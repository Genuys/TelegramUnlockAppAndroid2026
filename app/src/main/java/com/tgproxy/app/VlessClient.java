package com.tgproxy.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class VlessClient {

    private final String uuid;
    private final String host;
    private final int port;
    private final String security;
    private final String sni;
    private final String fingerprint;
    private final String type;
    private final String wsPath;
    private final String wsHost;

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private boolean isWs = false;
    private static final SecureRandom rng = new SecureRandom();

    public VlessClient(String vlessUri) {
        String clean = vlessUri.trim();
        if (!clean.startsWith("vless://")) {
            throw new IllegalArgumentException("Invalid VLESS URI");
        }

        String withoutScheme = clean.substring(8);
        int atIdx = withoutScheme.indexOf('@');
        uuid = withoutScheme.substring(0, atIdx);

        String rest = withoutScheme.substring(atIdx + 1);
        int qIdx = rest.indexOf('?');
        String hostPort;
        String queryAndFragment;
        if (qIdx >= 0) {
            hostPort = rest.substring(0, qIdx);
            queryAndFragment = rest.substring(qIdx + 1);
        } else {
            hostPort = rest;
            queryAndFragment = "";
        }

        int hashIdx = queryAndFragment.indexOf('#');
        String query = hashIdx >= 0 ? queryAndFragment.substring(0, hashIdx) : queryAndFragment;

        int colonIdx = hostPort.lastIndexOf(':');
        host = hostPort.substring(0, colonIdx);
        port = Integer.parseInt(hostPort.substring(colonIdx + 1));

        Map<String, String> params = parseQuery(query);
        security = params.getOrDefault("security", "none");
        sni = params.getOrDefault("sni", host);
        fingerprint = params.getOrDefault("fp", "");
        type = params.getOrDefault("type", "tcp");
        wsPath = params.getOrDefault("path", "/");
        wsHost = params.getOrDefault("host", sni);
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = pair.substring(0, eq);
                String val = pair.substring(eq + 1);
                try {
                    val = java.net.URLDecoder.decode(val, "UTF-8");
                } catch (Exception ignored) {
                }
                map.put(key, val);
            }
        }
        return map;
    }

    public void connect(String destAddr, int destPort) throws Exception {
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(host, port), 10000);
        raw.setReceiveBufferSize(262144);
        raw.setSendBufferSize(262144);
        raw.setTcpNoDelay(true);
        raw.setSoTimeout(30000);
        if ("tls".equalsIgnoreCase(security) || "reality".equalsIgnoreCase(security)) {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }

                        public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {
                        }

                        public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {
                        }
                    }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            SSLSocketFactory factory = ctx.getSocketFactory();
            SSLSocket ssl = (SSLSocket) factory.createSocket(raw, sni, port, true);
            ssl.setUseClientMode(true);
            ssl.startHandshake();
            socket = ssl;
        } else {
            socket = raw;
        }

        in = new java.io.BufferedInputStream(socket.getInputStream(), TgConstants.BUF);
        out = new java.io.BufferedOutputStream(socket.getOutputStream(), TgConstants.BUF);

        if ("ws".equalsIgnoreCase(type)) {
            isWs = true;
            doWsHandshake();
        }

        sendVlessRequest(destAddr, destPort);
        readVlessResponse();
    }

    private void doWsHandshake() throws IOException {
        byte[] keyBytes = new byte[16];
        rng.nextBytes(keyBytes);
        String wsKey = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP);

        String req = "GET " + wsPath + " HTTP/1.1\r\n" +
                "Host: " + wsHost + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + wsKey + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n";

        out.write(req.getBytes("UTF-8"));
        out.flush();

        String statusLine = readLine(in);
        if (statusLine == null || !statusLine.contains("101")) {
            throw new IOException("WebSocket handshake failed: " + statusLine);
        }

        while (true) {
            String line = readLine(in);
            if (line == null || line.isEmpty()) break;
        }
    }

    private void sendVlessRequest(String destAddr, int destPort) throws IOException {
        byte[] uuidBytes = uuidToBytes(uuid);
        byte[] addrBytes;
        int addrType;

        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(destAddr);
            byte[] raw = addr.getAddress();
            if (raw.length == 4) {
                addrType = 1;
                addrBytes = raw;
            } else {
                addrType = 3;
                addrBytes = raw;
            }
        } catch (Exception e) {
            addrType = 2;
            byte[] domainBytes = destAddr.getBytes("UTF-8");
            addrBytes = new byte[1 + domainBytes.length];
            addrBytes[0] = (byte) domainBytes.length;
            System.arraycopy(domainBytes, 0, addrBytes, 1, domainBytes.length);
        }

        int totalLen = 1 + 16 + 1 + 1 + 2 + 1 + addrBytes.length;
        byte[] request = new byte[totalLen];
        int pos = 0;

        request[pos++] = 0;

        System.arraycopy(uuidBytes, 0, request, pos, 16);
        pos += 16;

        request[pos++] = 0;

        request[pos++] = 1;

        request[pos++] = (byte) ((destPort >> 8) & 0xFF);
        request[pos++] = (byte) (destPort & 0xFF);

        request[pos++] = (byte) addrType;

        System.arraycopy(addrBytes, 0, request, pos, addrBytes.length);

        if (isWs) {
            writeWsFrame(request);
        } else {
            out.write(request);
            out.flush();
        }
    }

    private byte[] vlessLeftover = null;

    private void readVlessResponse() throws IOException {
        if (isWs) {
            byte[] frame = readWsFrame();
            if (frame == null || frame.length < 2) {
                throw new IOException("Invalid VLESS response");
            }
            int addLen = frame[1] & 0xFF;
            int offset = 2 + addLen;
            if (frame.length > offset) {
                vlessLeftover = new byte[frame.length - offset];
                System.arraycopy(frame, offset, vlessLeftover, 0, vlessLeftover.length);
            }
        } else {
            byte[] hdr = readExactly(2);
            int addLen = hdr[1] & 0xFF;
            if (addLen > 0) {
                readExactly(addLen);
            }
        }
    }

    public void sendData(byte[] data) throws IOException {
        if (isWs) {
            writeWsFrame(data);
        } else {
            out.write(data);
            out.flush();
        }
    }

    public byte[] receiveData() throws IOException {
        if (vlessLeftover != null) {
            byte[] ret = vlessLeftover;
            vlessLeftover = null;
            return ret;
        }
        if (isWs) {
            return readWsFrame();
        } else {
            byte[] buf = new byte[TgConstants.BUF];
            int n = in.read(buf);
            if (n <= 0) return null;
            byte[] result = new byte[n];
            System.arraycopy(buf, 0, result, 0, n);
            return result;
        }
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    private void writeWsFrame(byte[] data) throws IOException {
        int len = data.length;
        byte[] header;
        if (len < 126) {
            header = new byte[6];
            header[0] = (byte) 0x82;
            header[1] = (byte) (0x80 | len);
        } else if (len < 65536) {
            header = new byte[8];
            header[0] = (byte) 0x82;
            header[1] = (byte) (0x80 | 126);
            header[2] = (byte) ((len >> 8) & 0xFF);
            header[3] = (byte) (len & 0xFF);
        } else {
            header = new byte[14];
            header[0] = (byte) 0x82;
            header[1] = (byte) (0x80 | 127);
            ByteBuffer bb = ByteBuffer.allocate(8);
            bb.putLong(len);
            byte[] lb = bb.array();
            System.arraycopy(lb, 0, header, 2, 8);
        }

        byte[] mask = new byte[4];
        rng.nextBytes(mask);
        int maskOffset = header.length - 4;
        System.arraycopy(mask, 0, header, maskOffset, 4);

        byte[] masked = new byte[len];
        for (int i = 0; i < len; i++) {
            masked[i] = (byte) (data[i] ^ mask[i % 4]);
        }

        synchronized (out) {
            out.write(header);
            out.write(masked);
            out.flush();
        }
    }

    private byte[] readWsFrame() throws IOException {
        byte[] h = readExactly(2);
        boolean isMasked = (h[1] & 0x80) != 0;
        int length = h[1] & 0x7F;

        if (length == 126) {
            byte[] ext = readExactly(2);
            length = ((ext[0] & 0xFF) << 8) | (ext[1] & 0xFF);
        } else if (length == 127) {
            byte[] ext = readExactly(8);
            length = (int) ByteBuffer.wrap(ext).getLong();
        }

        byte[] mask = null;
        if (isMasked) {
            mask = readExactly(4);
        }

        byte[] payload = readExactly(length);
        if (isMasked && mask != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }

        int opcode = h[0] & 0x0F;
        if (opcode == 0x8) return null;
        if (opcode == 0x9) {
            synchronized (out) {
                byte[] pong = new byte[]{(byte) 0x8A, 0};
                out.write(pong);
                out.flush();
            }
            return readWsFrame();
        }
        if (opcode == 0xA) return readWsFrame();

        return payload;
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

    private static byte[] uuidToBytes(String uuidStr) {
        UUID u = UUID.fromString(uuidStr);
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(u.getMostSignificantBits());
        bb.putLong(u.getLeastSignificantBits());
        return bb.array();
    }
}
