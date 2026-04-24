package com.tgproxy.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class NetworkUtils {

    private static final String[] ALT_DNS = {
            "1.1.1.1", "8.8.8.8", "9.9.9.9", "208.67.222.222", "76.76.2.0"
    };
    private static final ExecutorService DNS_POOL = Executors.newFixedThreadPool(3);

    // Use the trusted system factory everywhere — the previous trustAll instance
    // in NetworkUtils disabled certificate verification for TLS-fragment connections.
    private static final SSLSocketFactory TRUSTED_FACTORY =
            (SSLSocketFactory) SSLSocketFactory.getDefault();

    public static boolean isMobileNetwork(Context ctx) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            if (caps == null) return false;
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        } catch (Exception e) {
            return false;
        }
    }

    public static InetAddress resolveMultiDns(String host) {
        AtomicReference<InetAddress> result = new AtomicReference<>(null);
        CountDownLatch latch = new CountDownLatch(1);

        for (String ignored : ALT_DNS) {
            DNS_POOL.submit(() -> {
                try {
                    if (result.get() != null) return;
                    InetAddress addr = InetAddress.getByName(host);
                    if (result.compareAndSet(null, addr)) latch.countDown();
                } catch (Exception e2) {}
            });
        }

        try {
            InetAddress direct = InetAddress.getByName(host);
            if (result.compareAndSet(null, direct)) latch.countDown();
        } catch (Exception ignored) {}

        try { latch.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        InetAddress r = result.get();
        if (r != null) return r;
        try { return InetAddress.getByName(host); } catch (Exception e) { return null; }
    }

    public static Socket connectWithHappyEyeballs(String host, int port, int timeout)
            throws IOException {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            InetAddress[] all = InetAddress.getAllByName(host);
            List<InetAddress> v4 = new ArrayList<>(), v6 = new ArrayList<>();
            for (InetAddress a : all) {
                if (a instanceof java.net.Inet6Address) v6.add(a); else v4.add(a);
            }
            for (int i = 0; i < Math.max(v6.size(), v4.size()); i++) {
                if (i < v6.size()) addresses.add(v6.get(i));
                if (i < v4.size()) addresses.add(v4.get(i));
            }
        } catch (Exception e) {
            throw new IOException("DNS resolution failed: " + host);
        }
        if (addresses.isEmpty()) throw new IOException("No addresses for: " + host);

        IOException lastErr = null;
        for (InetAddress addr : addresses) {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(addr, port), timeout);
                return s;
            } catch (IOException e) {
                lastErr = e;
            }
        }
        throw lastErr != null ? lastErr : new IOException("Connection failed");
    }

    public static Socket connectWithTlsFragment(String ip, String sni, int port, int timeout)
            throws Exception {
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(ip, port), timeout);
        raw.setTcpNoDelay(true);
        raw.setSoTimeout(timeout);
        raw.setReceiveBufferSize(262144);
        raw.setSendBufferSize(262144);

        SSLSocket ssl = (SSLSocket) TRUSTED_FACTORY.createSocket(raw, sni, port, true);
        ssl.setUseClientMode(true);

        SSLParameters params = ssl.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        ssl.setSSLParameters(params);

        String[] protos = ssl.getEnabledProtocols();
        List<String> filtered = new ArrayList<>();
        for (String p : protos) { if (!p.contains("SSLv")) filtered.add(p); }
        if (!filtered.isEmpty())
            ssl.setEnabledProtocols(filtered.toArray(new String[0]));

        ssl.startHandshake();
        return ssl;
    }

    public static Socket smartConnect(String host, int port, int timeout, boolean isMobile)
            throws IOException {
        int realTimeout = isMobile ? Math.min(timeout * 2, 20000) : timeout;
        try {
            return connectWithHappyEyeballs(host, port, realTimeout);
        } catch (IOException e1) {
            Socket s = new Socket();
            try {
                InetAddress alt = resolveMultiDns(host);
                if (alt != null) {
                    s.connect(new InetSocketAddress(alt, port), realTimeout);
                    return s;
                }
            } catch (Exception ignored) {}
            s.connect(new InetSocketAddress(host, port), realTimeout);
            return s;
        }
    }

    public static int adaptiveBufferSize(boolean isMobile) { return isMobile ? 65536 : 131072; }
    public static int adaptivePoolSize(boolean isMobile)   { return isMobile ? 4 : 8; }

    private NetworkUtils() {}
}
