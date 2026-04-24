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
        final RawWebSocket ws;
        final long time;
        Entry(RawWebSocket ws, long time) { this.ws = ws; this.time = time; }
    }

    private final Map<String, List<Entry>> buckets = new HashMap<>();
    private final Set<String> filling = new HashSet<>();
    private ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running = false;

    public void warmup() {
        running = true;
        if (executor.isShutdown()) executor = Executors.newCachedThreadPool();

        for (int dc = 1; dc <= 5; dc++) {
            if (!TgConstants.DC_IPS.containsKey(dc)) continue;
            String tip = TgConstants.DC_IPS.get(dc);
            for (boolean m : new boolean[]{false, true}) {
                refill(dc + ":" + m, tip, TgConstants.wsDomains(dc, m));
            }
        }

        Thread sweeper = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(30_000);
                    long now = System.currentTimeMillis() / 1000;
                    synchronized (buckets) {
                        for (List<Entry> b : buckets.values()) {
                            b.removeIf(e -> {
                                if ((now - e.time) > TgConstants.POOL_AGE || !e.ws.isAlive()) {
                                    e.ws.close();
                                    return true;
                                }
                                return false;
                            });
                        }
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (Exception ignored) {}
            }
        });
        sweeper.setDaemon(true);
        sweeper.start();
    }

    public void stop() {
        running = false;
        executor.shutdownNow();
        synchronized (buckets) {
            for (List<Entry> b : buckets.values()) b.forEach(e -> e.ws.close());
            buckets.clear();
        }
        synchronized (filling) { filling.clear(); }
    }

    /**
     * Called by ProxyEngine.reconnectPool() on network change.
     * Replaces stop()+warmup() which failed because executor was shut down
     * but running was never reset to true before the next warmup().
     */
    public synchronized void restart() {
        stop();
        executor = Executors.newCachedThreadPool();
        warmup();
    }

    public RawWebSocket get(int dc, boolean m, String tip, String[] doms) {
        String key = dc + ":" + m;
        long   now = System.currentTimeMillis() / 1000;

        synchronized (buckets) {
            List<Entry> bucket = buckets.get(key);
            if (bucket != null) {
                while (!bucket.isEmpty()) {
                    Entry e = bucket.remove(0);
                    if ((now - e.time) > TgConstants.POOL_AGE || !e.ws.isAlive()) {
                        e.ws.close(); continue;
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
            synchronized (buckets) {
                List<Entry> b = buckets.get(key);
                if (b != null && b.size() >= TgConstants.POOL_SIZE) return;
            }
            filling.add(key);
        }

        if (executor.isShutdown()) return;

        executor.submit(() -> {
            try {
                while (running) {
                    int size;
                    synchronized (buckets) {
                        List<Entry> b = buckets.get(key);
                        size = b == null ? 0 : b.size();
                    }
                    if (size >= TgConstants.POOL_SIZE) break;

                    RawWebSocket ws = null;
                    for (String domain : doms) {
                        try {
                            ws = RawWebSocket.connect(tip, domain, 8000);
                            break;
                        } catch (RawWebSocket.WsRedirectException e) {
                            // domain redirects — try next
                        } catch (Exception e) {
                            break;
                        }
                    }

                    if (ws != null && ws.isAlive()) {
                        synchronized (buckets) {
                            bucketOf(key).add(new Entry(ws, System.currentTimeMillis() / 1000));
                        }
                    } else {
                        break;
                    }
                }
            } finally {
                synchronized (filling) { filling.remove(key); }
            }
        });
    }

    private List<Entry> bucketOf(String key) {
        List<Entry> b = buckets.get(key);
        if (b == null) { b = new ArrayList<>(); buckets.put(key, b); }
        return b;
    }

    public void setMobileMode(boolean m) {}
}
