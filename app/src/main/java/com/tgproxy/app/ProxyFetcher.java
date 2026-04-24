package com.tgproxy.app;

import android.os.Handler;
import android.os.Looper;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxyFetcher {

    public interface Listener {
        void onProxiesUpdated(List<ProxyEntry> proxies);
    }

    public static class ProxyEntry {
        public final String server;
        public final int    port;
        public final String secret;
        public final String fullLink;
        public volatile int ping = -1;

        public ProxyEntry(String server, int port, String secret, String fullLink) {
            this.server   = server;
            this.port     = port;
            this.secret   = secret;
            this.fullLink = fullLink;
        }
    }

    private static final String[] HARDCODED_PROXIES = {
            // WiFi primary
            "tg://proxy?server=" + TgConstants.PROXY_WIFI_SERVER
                    + "&port=" + TgConstants.PROXY_WIFI_PORT
                    + "&secret=" + TgConstants.PROXY_WIFI_SECRET,
            // Mobile / whitelist bypass #1
            "tg://proxy?server=" + TgConstants.PROXY_MOB1_SERVER
                    + "&port=" + TgConstants.PROXY_MOB1_PORT
                    + "&secret=" + TgConstants.PROXY_MOB1_SECRET,
            // Mobile / whitelist bypass #2
            "tg://proxy?server=" + TgConstants.PROXY_MOB2_SERVER
                    + "&port=" + TgConstants.PROXY_MOB2_PORT
                    + "&secret=" + TgConstants.PROXY_MOB2_SECRET,
            // Additional community servers
            "tg://proxy?server=ads1.mtproxygram.lol&port=443"
                    + "&secret=eee74a2d4c670e40f3d98ce2f2a7b7c8d762726f777365722e79616e6465782e636f6d",
            "tg://proxy?server=116.202.247.247&port=443"
                    + "&secret=ee1603010200010001fc030386e24c3add"
                    + "733130312e646976617263646e2e636f6d"
                    + "16030102000100010000000000000000000000000000000000004000000000"
                    + "000000000000000000000000000000",
            "tg://proxy?server=s11.dimasssss.space&port=41528"
                    + "&secret=eebe3007e927acd147dde12bee8b1a7c93726164696f7265636f72642e7275",
    };

    private static final Pattern TG_PROXY_PATTERN = Pattern.compile(
            "(?:tg://proxy|https?://t\\.me/proxy)\\?server=([^&]+)&port=(\\d+)&secret=([^& \\n\\r]+)"
    );

    private final CopyOnWriteArrayList<ProxyEntry> proxies = new CopyOnWriteArrayList<>();
    private final ExecutorService fetchPool  = Executors.newFixedThreadPool(1);
    private final ExecutorService pingPool   = Executors.newFixedThreadPool(6);
    private Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean isFetching = false;

    public void setListener(Listener l) { this.listener = l; }
    public List<ProxyEntry> getProxies() { return new ArrayList<>(proxies); }
    public void start()     { fetchNow(); }
    public void stop()      { fetchPool.shutdownNow(); pingPool.shutdownNow(); }

    public void fetchNow() {
        if (isFetching) return;
        fetchPool.submit(this::fetchAll);
    }

    private void fetchAll() {
        isFetching = true;
        List<ProxyEntry> all = new ArrayList<>();

        for (String src : HARDCODED_PROXIES) {
            Matcher m = TG_PROXY_PATTERN.matcher(src);
            if (m.find()) {
                all.add(new ProxyEntry(
                        m.group(1),
                        Integer.parseInt(m.group(2)),
                        m.group(3),
                        m.group(0)));
            }
        }

        List<ProxyEntry> unique = dedup(all);
        proxies.clear();
        proxies.addAll(unique);
        isFetching = false;

        if (listener != null)
            handler.post(() -> listener.onProxiesUpdated(new ArrayList<>(unique)));

        measurePings(unique);
    }

    private void measurePings(List<ProxyEntry> list) {
        AtomicInteger remaining = new AtomicInteger(list.size());
        for (ProxyEntry entry : list) {
            pingPool.submit(() -> {
                entry.ping = tcpPing(entry.server, entry.port, 4000);
                if (remaining.decrementAndGet() == 0 && listener != null) {
                    List<ProxyEntry> sorted = new ArrayList<>(proxies);
                    sorted.sort((a, b) -> {
                        if (a.ping < 0 && b.ping < 0) return 0;
                        if (a.ping < 0) return 1;
                        if (b.ping < 0) return -1;
                        return Integer.compare(a.ping, b.ping);
                    });
                    handler.post(() -> listener.onProxiesUpdated(sorted));
                }
            });
        }
    }

    private static int tcpPing(String host, int port, int timeout) {
        long start = System.currentTimeMillis();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeout);
            return (int)(System.currentTimeMillis() - start);
        } catch (Exception e) {
            return -1;
        }
    }

    private List<ProxyEntry> dedup(List<ProxyEntry> list) {
        List<ProxyEntry> unique = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ProxyEntry e : list) {
            if (seen.add(e.server + ":" + e.port)) unique.add(e);
        }
        return unique;
    }
}
