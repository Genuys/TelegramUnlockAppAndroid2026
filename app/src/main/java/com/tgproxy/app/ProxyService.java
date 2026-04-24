package com.tgproxy.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.preference.PreferenceManager;

import java.util.Random;

public class ProxyService extends Service {

    private static final String CHANNEL_ID = "proxy_channel";
    private static final int    NOTIF_ID   = 1;

    private ProxyEngine engine;
    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private int     port;
    private String  boundIp = "127.0.0.1";
    private boolean isMobile = false;

    private static ProxyService instance;

    private ScreenStateReceiver screenReceiver;
    private boolean smartSleepEnabled = true;
    private volatile boolean paused = false;
    private SharedPreferences prefs;

    private ConnectivityManager.NetworkCallback networkCallback;

    // Debounce: only one pending reconnect at a time.
    // Without this, rapid onAvailable callbacks (WiFi→Mobile) fired a
    // reconnectPool() every 1 second, killing active connections in a loop.
    private Runnable pendingReconnect = null;
    private static final long RECONNECT_DEBOUNCE_MS = 2500;

    public static ProxyService getInstance() { return instance; }
    public ProxyEngine getEngine()            { return engine; }
    public int         getPort()              { return port; }
    public boolean     isPaused()             { return paused; }
    public boolean     isMobileNetwork()      { return isMobile; }

    private long startTime;

    public long getUptime() { return System.currentTimeMillis() - startTime; }

    public String getIp() {
        return (engine != null && engine.boundIp != null) ? engine.boundIp : boundIp;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        handler  = new Handler(Looper.getMainLooper());
        prefs    = PreferenceManager.getDefaultSharedPreferences(this);
        createNotificationChannel();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TGProxy::ProxyWake");

        isMobile = NetworkUtils.isMobileNetwork(this);
        registerNetworkCallback();
        registerScreenReceiver();
    }

    private void registerScreenReceiver() {
        smartSleepEnabled = prefs.getBoolean("smart_sleep", true);
        if (!smartSleepEnabled) return;

        screenReceiver = new ScreenStateReceiver();
        screenReceiver.setCallback(new ScreenStateReceiver.Callback() {
            @Override public void onScreenOn() {
                if (paused && engine != null) resumeEngine();
            }
            @Override public void onScreenOff() {
                if (!paused && engine != null && smartSleepEnabled) {
                    handler.postDelayed(() -> {
                        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                        if (pm != null && !pm.isInteractive()) pauseEngine();
                    }, 10_000);
                }
            }
        });

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, f);
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    boolean wasMobile = isMobile;
                    isMobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                    if (engine != null && wasMobile != isMobile) {
                        scheduleReconnect();
                    }
                }

                @Override
                public void onLost(Network network) {
                    cancelPendingReconnect();
                }

                @Override
                public void onAvailable(Network network) {
                    if (paused) {
                        handler.post(() -> resumeEngine());
                    } else {
                        scheduleReconnect();
                    }
                }
            };

            NetworkRequest req = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            cm.registerNetworkCallback(req, networkCallback);
        } catch (Exception ignored) {}
    }

    private void scheduleReconnect() {
        cancelPendingReconnect();
        pendingReconnect = () -> {
            pendingReconnect = null;
            if (engine != null && engine.isRunning() && !paused) {
                engine.reconnectPool();
            }
        };
        handler.postDelayed(pendingReconnect, RECONNECT_DEBOUNCE_MS);
    }

    private void cancelPendingReconnect() {
        if (pendingReconnect != null) {
            handler.removeCallbacks(pendingReconnect);
            pendingReconnect = null;
        }
    }

    private void pauseEngine() {
        paused = true;
        cancelPendingReconnect();
        if (engine != null) engine.pause();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        updateNotification("⏸ Приостановлен (экран выключен)");
    }

    private void resumeEngine() {
        paused = false;
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
        if (engine != null) {
            new Thread(() -> {
                try { engine.resume(port); } catch (Exception ignored) {}
            }).start();
        }
        updateNotification(boundIp + ":" + port + " | Активен");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (engine != null) { engine.stop(); engine = null; }

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean dynamicPort = prefs.getBoolean("dynamic_port", false);
        if (dynamicPort) {
            port = 10000 + new Random().nextInt(50000);
        } else {
            port = prefs.getInt("custom_port", 1080);
            if (port < 1 || port > 65535) port = 1080;
        }

        boundIp = prefs.getString("custom_ip", "127.0.0.1");
        if (boundIp == null || boundIp.trim().isEmpty()) boundIp = "127.0.0.1";

        int    mode     = prefs.getInt("proxy_mode", ProxyEngine.MODE_ORIGINAL);
        String vlessUri = prefs.getString("vless_uri", "");
        smartSleepEnabled = prefs.getBoolean("smart_sleep", true);

        String socks5User = prefs.getString("socks5_user", "");
        String socks5Pass = prefs.getString("socks5_pass", "");

        startForeground(NOTIF_ID, buildNotification());
        startTime = System.currentTimeMillis();

        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();

        isMobile = NetworkUtils.isMobileNetwork(this);

        engine = new ProxyEngine();
        engine.setMode(mode);
        engine.setVlessUri(vlessUri);
        engine.setBoundIp(boundIp);
        engine.setMobileMode(isMobile);

        if (!socks5User.isEmpty() && !socks5Pass.isEmpty()) {
            engine.setCredentials(socks5User, Socks5Auth.hashPassword(socks5Pass));
        }

        new Thread(() -> {
            try { engine.start(port); }
            catch (Exception e) { handler.post(this::stopSelf); }
        }).start();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        cancelPendingReconnect();
        if (engine != null) { engine.stop(); engine = null; }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        }
        if (networkCallback != null) {
            try {
                ConnectivityManager cm =
                        (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) cm.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "TG Proxy", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("SOCKS5 Proxy");
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent ni = new Intent(this, MainActivity.class);
        ni.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, ni, piFlags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("TG Proxy")
                    .setContentText(boundIp + ":" + port)
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setContentIntent(pi).setOngoing(true).build();
        } else {
            return new Notification.Builder(this)
                    .setContentTitle("TG Proxy")
                    .setContentText(boundIp + ":" + port)
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setContentIntent(pi).setOngoing(true).build();
        }
    }

    public void updateNotification(String text) {
        try {
            Intent ni = new Intent(this, MainActivity.class);
            ni.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int piFlags = Build.VERSION.SDK_INT >= 23
                    ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getActivity(this, 0, ni, piFlags);

            Notification n;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                n = new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("TG Proxy").setContentText(text)
                        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                        .setContentIntent(pi).setOngoing(true).build();
            } else {
                n = new Notification.Builder(this)
                        .setContentTitle("TG Proxy").setContentText(text)
                        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                        .setContentIntent(pi).setOngoing(true).build();
            }
            getSystemService(NotificationManager.class).notify(NOTIF_ID, n);
        } catch (Exception ignored) {}
    }
}
