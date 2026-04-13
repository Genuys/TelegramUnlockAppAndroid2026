package com.tgproxy.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WsPool {

    private static class Entry {
        RawWebSocket ws;
        long time;
        Entry(RawWebSocket ws, long time) {
            this.ws = ws;
            this.time = time;
        }
    }

    private final Map<String, List<Entry>> buckets = new HashMap<>();
    private final Set<String> filling = new HashSet<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private volatile boolean running = true;

    public void warmup() {
        for (int dc = 1; dc <= 5; dc++) {
            if (!TgConstants.DC_IPS.containsKey(dc)) continue;
            String tip = TgConstants.DC_IPS.get(dc);
            for (boolean m : new boolean[]{false, true}) {
                String key = dc + ":" + m;
                refill(key, tip, TgConstants.wsDomains(dc, m));
            }
        }

        Thread sweeper = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(30000);
                    long now = System.currentTimeMillis() / 1000;
                    synchronized (buckets) {
                        for (List<Entry> b : buckets.values()) {
                            b.removeIf(e -> {
                                boolean expired = (now - e.time) > TgConstants.POOL_AGE;
                                if (expired || !e.ws.isAlive()) {
                                    e.ws.close();
                                    return true;
                                }
                                return false;
                            });
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        });
        sweeper.setDaemon(true);
        sweeper.start();
    }

    public void stop() {
        running = false;
        executor.shutdownNow();
        synchronized (buckets) {
            for (List<Entry> b : buckets.values()) {
                for (Entry e : b) {
                    e.ws.close();
                }
            }
            buckets.clear();
        }
    }

    public RawWebSocket get(int dc, boolean m, String tip, String[] doms) {
        String key = dc + ":" + m;
        long now = System.currentTimeMillis() / 1000;

        synchronized (buckets) {
            List<Entry> bucket = buckets.get(key);
            if (bucket != null) {
                while (!bucket.isEmpty()) {
                    Entry e = bucket.remove(0);
                    if ((now - e.time) > TgConstants.POOL_AGE || !e.ws.isAlive()) {
                        e.ws.close();
                        continue;
                    }
                    refill(key, tip, doms);
                    return e.ws;
                }
            }
        }
        refill(key, tip, doms);
        return null;
    }

    private void refill(String key, String tip, String[] doms) {
        synchronized (filling) {
            if (filling.contains(key)) return;
            List<Entry> b = buckets.get(key);
            if (b != null && b.size() >= TgConstants.POOL_SIZE) return;
            filling.add(key);
        }

        executor.submit(() -> {
            try {
                while (running) {
                    int size;
                    synchronized (buckets) {
                        List<Entry> b = buckets.get(key);
                        size = b == null ? 0 : b.size();
                    }
                    int need = TgConstants.POOL_SIZE - size;
                    if (need <= 0) break;

                    RawWebSocket ws = null;
                    for (String domain : doms) {
                        try {
                            ws = RawWebSocket.connect(tip, domain, 8000);
                            break;
                        } catch (RawWebSocket.WsRedirectException e) {
                            continue;
                        } catch (Exception e) {
                            break;
                        }
                    }

                    if (ws != null && ws.isAlive()) {
                        synchronized (buckets) {
                            bracketsOf(key).add(new Entry(ws, System.currentTimeMillis() / 1000));
                        }
                    } else {
                        break;
                    }
                }
            } finally {
                synchronized (filling) {
                    filling.remove(key);
                }
            }
        });
    }

    private List<Entry> bracketsOf(String key) {
        List<Entry> b = buckets.get(key);
        if (b == null) {
            b = new ArrayList<>();
            buckets.put(key, b);
        }
        return b;
    }
    
    // Compatibility stub
    public void setMobileMode(boolean m) {}
}
