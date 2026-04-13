package com.tgproxy.app;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxyFetcher {

    public interface Listener {
        void onProxiesUpdated(List<ProxyEntry> proxies);
    }

    public static class ProxyEntry {
        public final String server;
        public final int port;
        public final String secret;
        public final String fullLink;
        public volatile int ping = -1;

        public ProxyEntry(String server, int port, String secret, String fullLink) {
            this.server = server;
            this.port = port;
            this.secret = secret;
            this.fullLink = fullLink;
        }
    }

    private static final String[] HARDCODED_PROXIES = {
            "tg://proxy?server=knoll0422-c522b2fb.proxytg.ink&port=443&secret=28ed0c814a97362df6fed7339922e795",
            "https://t.me/proxy?server=quackton.life&port=443&secret=ee65fc7553a1f5ca8b50b71c015b38722479616e6465782e7275",
            "tg://proxy?server=ads1.mtproxygram.lol&port=443&secret=eee74a2d4c670e40f3d98ce2f2a7b7c8d762726f777365722e79616e6465782e636f6d",
            "tg://proxy?server=116.202.247.247&port=443&secret=ee1603010200010001fc030386e24c3add733130312e646976617263646e2e636f6d16030102000100010000000000000000000000000000000000004000000000000000000000000000000000000000",
            "tg://proxy?server=s11.dimasssss.space&port=41528&secret=eebe3007e927acd147dde12bee8b1a7c93726164696f7265636f72642e7275",
            "tg://proxy?server=116.203.245.195&port=443&secret=1603010200010001fc030386e24c3add",
            "tg://proxy?server=116.203.235.40&port=443&secret=1603010200010001fc030386e24c3add",
            "tg://proxy?server=135.125.134.120&port=443&secret=1603010200010001fc030386e24c3add"
    };

    private static final Pattern TG_PROXY_PATTERN = Pattern.compile(
            "(?:tg://proxy|https?://t\\.me/proxy)\\?server=([^&]+)&port=(\\d+)&secret=([^& \n\r]+)"
    );

    private final CopyOnWriteArrayList<ProxyEntry> proxies = new CopyOnWriteArrayList<>();
    private final ExecutorService fetchPool = Executors.newFixedThreadPool(1);
    private Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean isFetching = false;

    public void setListener(Listener l) {
        this.listener = l;
    }

    public List<ProxyEntry> getProxies() {
        return new ArrayList<>(proxies);
    }

    public void start() {
        fetchNow();
    }

    public void stop() {
    }

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
                all.add(new ProxyEntry(m.group(1), Integer.parseInt(m.group(2)), m.group(3), m.group(0)));
            }
        }

        List<ProxyEntry> unique = dedup(all);

        proxies.clear();
        proxies.addAll(unique);

        isFetching = false;
        if (listener != null) {
            handler.post(() -> listener.onProxiesUpdated(unique));
        }
    }


    private List<ProxyEntry> dedup(List<ProxyEntry> list) {
        List<ProxyEntry> unique = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ProxyEntry e : list) {
            String key = e.server + ":" + e.port;
            if (seen.add(key)) unique.add(e);
        }
        return unique;
    }
}
