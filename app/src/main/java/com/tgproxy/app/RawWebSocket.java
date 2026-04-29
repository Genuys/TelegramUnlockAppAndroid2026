package com.tgproxy.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class RawWebSocket {

    private static final int OP_BINARY = 0x2;
    private static final int OP_CLOSE  = 0x8;
    private static final int OP_PING   = 0x9;
    private static final int OP_PONG   = 0xA;

    private static final int RECV_TIMEOUT_MS = 90_000;

    private final InputStream  in;
    private final OutputStream out;
    private final Socket socket;
    private volatile boolean closed = false;
    private static final SecureRandom rng = new SecureRandom();

    private static final SSLSocketFactory sslFactory =
            (SSLSocketFactory) SSLSocketFactory.getDefault();

    public RawWebSocket(Socket socket) throws IOException {
        this.socket = socket;
        this.in  = new java.io.BufferedInputStream(socket.getInputStream(),  TgConstants.BUF);
        this.out = new java.io.BufferedOutputStream(socket.getOutputStream(), TgConstants.BUF);
    }

    public static RawWebSocket connect(String ip, String domain, int timeout) throws Exception {
        Socket raw = new Socket();
        raw.connect(new java.net.InetSocketAddress(ip, 443), timeout);
        raw.setSoTimeout(timeout);
        raw.setTcpNoDelay(true);
        raw.setReceiveBufferSize(262144);
        raw.setSendBufferSize(262144);

        SSLSocket ssl = (SSLSocket) sslFactory.createSocket(raw, domain, 443, true);
        ssl.setUseClientMode(true);

        SSLParameters params = ssl.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        ssl.setSSLParameters(params);
        ssl.startHandshake();

        RawWebSocket ws = new RawWebSocket(ssl);

        byte[] keyBytes = new byte[16];
        rng.nextBytes(keyBytes);
        String wsKey = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP);

        String req = "GET /apiws HTTP/1.1\r\n" +
                "Host: " + domain + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + wsKey + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "Sec-WebSocket-Protocol: binary\r\n" +
                "Origin: https://web.telegram.org\r\n" +
                "User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.6778.204 Mobile Safari/537.36\r\n" +
                "\r\n";

        ws.out.write(req.getBytes("UTF-8"));
        ws.out.flush();

        int statusCode = 0;
        boolean firstLine = true;
        while (true) {
            String line = readLine(ws.in);
            if (line == null || line.isEmpty()) break;
            if (firstLine) {
                String[] parts = line.split(" ", 3);
                if (parts.length >= 2) {
                    try { statusCode = Integer.parseInt(parts[1]); }
                    catch (NumberFormatException ignored) {}
                }
                firstLine = false;
            }
        }

        if (statusCode == 101) {
            ssl.setSoTimeout(RECV_TIMEOUT_MS);
            return ws;
        }

        ws.closeQuiet();
        if (statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308) {
            throw new WsRedirectException(statusCode);
        }
        throw new IOException("WS handshake failed: " + statusCode);
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\r') {
                    sb.setLength(sb.length() - 1);
                }
                return sb.toString();
            }
            sb.append((char) c);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    public void send(byte[] data) throws IOException {
        if (closed) throw new IOException("closed");
        byte[] frame = buildFrame(OP_BINARY, data, true);
        synchronized (out) {
            out.write(frame);
            out.flush();
        }
    }

    public void sendBatch(java.util.List<byte[]> parts) throws IOException {
        if (closed) throw new IOException("closed");
        synchronized (out) {
            for (byte[] p : parts) out.write(buildFrame(OP_BINARY, p, true));
            out.flush();
        }
    }

    /**
     * Sends WS CLOSE frame WITHOUT closing the socket.
     * Used by upThread to signal server that uploads are done while
     * downThread continues draining the server's remaining response data.
     *
     * Root cause of download asymmetry bug: the previous ws.close() call
     * from upThread set closed=true and called socket.close(), killing the
     * socket while downThread was still reading incoming media/sticker data.
     */
    public void initiateClose() {
        if (closed) return;
        try {
            synchronized (out) {
                out.write(buildFrame(OP_CLOSE, new byte[0], true));
                out.flush();
            }
        } catch (Exception ignored) {}
    }

    public byte[] recv() throws IOException {
        while (!closed) {
            int[] hdr = readFrameHeader();
            int opcode = hdr[0];
            int length  = hdr[1];
            boolean masked = hdr[2] == 1;

            byte[] mask = masked ? readExactly(4) : null;
            byte[] payload = readExactly(length);
            if (masked && mask != null) payload = xorMask(payload, mask);

            if (opcode == OP_CLOSE) {
                closed = true;
                try {
                    byte[] closeData = payload.length >= 2
                            ? Arrays.copyOf(payload, 2) : new byte[0];
                    synchronized (out) {
                        out.write(buildFrame(OP_CLOSE, closeData, true));
                        out.flush();
                    }
                } catch (Exception ignored) {}
                return null;
            }

            if (opcode == OP_PING) {
                try {
                    synchronized (out) {
                        out.write(buildFrame(OP_PONG, payload, true));
                        out.flush();
                    }
                } catch (Exception ignored) {}
                continue;
            }

            if (opcode == OP_PONG) continue;

            if (opcode == 0x1 || opcode == 0x2) return payload;
        }
        return null;
    }

    public void close() {
        if (closed) return;
        closed = true;
        try {
            synchronized (out) {
                out.write(buildFrame(OP_CLOSE, new byte[0], true));
                out.flush();
            }
        } catch (Exception ignored) {}
        closeQuiet();
    }

    public boolean isAlive() {
        return !closed && socket != null && !socket.isClosed() && socket.isConnected();
    }

    private void closeQuiet() {
        try { socket.close(); } catch (Exception ignored) {}
    }

    private int[] readFrameHeader() throws IOException {
        byte[] h = readExactly(2);
        int opcode = h[0] & 0x0F;
        boolean masked = (h[1] & 0x80) != 0;
        int length = h[1] & 0x7F;
        if (length == 126) {
            byte[] ext = readExactly(2);
            length = ((ext[0] & 0xFF) << 8) | (ext[1] & 0xFF);
        } else if (length == 127) {
            byte[] ext = readExactly(8);
            length = (int) ByteBuffer.wrap(ext).getLong();
        }
        return new int[]{opcode, length, masked ? 1 : 0};
    }

    private byte[] readExactly(int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r == -1) throw new IOException("EOF");
            off += r;
        }
        return buf;
    }

    private static byte[] xorMask(byte[] data, byte[] mask) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) result[i] = (byte) (data[i] ^ mask[i % 4]);
        return result;
    }

    private static byte[] buildFrame(int opcode, byte[] data, boolean mask) {
        int headerLen = 2;
        int len = data.length;
        if (len >= 126 && len < 65536) headerLen += 2;
        else if (len >= 65536) headerLen += 8;
        if (mask) headerLen += 4;

        byte[] frame = new byte[headerLen + len];
        int pos = 0;
        frame[pos++] = (byte) (0x80 | opcode);

        int maskBit = mask ? 0x80 : 0;
        if (len < 126) {
            frame[pos++] = (byte) (maskBit | len);
        } else if (len < 65536) {
            frame[pos++] = (byte) (maskBit | 126);
            frame[pos++] = (byte) ((len >> 8) & 0xFF);
            frame[pos++] = (byte) (len & 0xFF);
        } else {
            frame[pos++] = (byte) (maskBit | 127);
            ByteBuffer bb = ByteBuffer.allocate(8);
            bb.putLong(len);
            byte[] lb = bb.array();
            System.arraycopy(lb, 0, frame, pos, 8);
            pos += 8;
        }

        if (mask) {
            byte[] mk = new byte[4];
            rng.nextBytes(mk);
            System.arraycopy(mk, 0, frame, pos, 4);
            pos += 4;
            for (int i = 0; i < len; i++) frame[pos + i] = (byte) (data[i] ^ mk[i % 4]);
        } else {
            System.arraycopy(data, 0, frame, pos, len);
        }
        return frame;
    }

    public static class WsRedirectException extends IOException {
        public final int statusCode;
        public WsRedirectException(int code) {
            super("Redirect " + code);
            this.statusCode = code;
        }
    }
}
