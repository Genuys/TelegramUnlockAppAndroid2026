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
    private static final int NOTIF_ID = 1;

    private ProxyEngine engine;
    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private int port;
    private String boundIp = "127.0.0.1";
    private boolean isMobile = false;

    private static ProxyService instance;

    private ScreenStateReceiver screenReceiver;
    private boolean smartSleepEnabled = true;
    private volatile boolean paused = false;
    private SharedPreferences prefs;

    private ConnectivityManager.NetworkCallback networkCallback;

    public static ProxyService getInstance() {
        return instance;
    }

    public ProxyEngine getEngine() {
        return engine;
    }

    public int getPort() {
        return port;
    }

    private long startTime;

    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    public String getIp() {
        if (engine != null && engine.boundIp != null) {
            return engine.boundIp;
        }
        return boundIp;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isMobileNetwork() {
        return isMobile;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        handler = new Handler(Looper.getMainLooper());
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
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
            @Override
            public void onScreenOn() {
                if (paused && engine != null) {
                    resumeEngine();
                }
            }

            @Override
            public void onScreenOff() {
                if (!paused && engine != null && smartSleepEnabled) {
                    handler.postDelayed(() -> {
                        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                        if (pm != null && !pm.isInteractive()) {
                            pauseEngine();
                        }
                    }, 10000);
                }
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;

            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    boolean wasMobile = isMobile;
                    isMobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                    if (engine != null && wasMobile != isMobile) {
                        engine.setMobileMode(isMobile);
                    }
                }

                @Override
                public void onLost(Network network) {
                    if (engine != null && !paused) {
                        handler.postDelayed(() -> {
                            if (engine != null && engine.isRunning()) {
                                engine.reconnectPool();
                            }
                        }, 2000);
                    }
                }

                @Override
                public void onAvailable(Network network) {
                    if (paused) {
                        resumeEngine();
                    } else if (engine != null) {
                        handler.postDelayed(() -> {
                            if (engine != null && engine.isRunning()) {
                                engine.reconnectPool();
                            }
                        }, 1000);
                    }
                }
            };

            NetworkRequest req = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            cm.registerNetworkCallback(req, networkCallback);
        } catch (Exception ignored) {}
    }

    private void pauseEngine() {
        paused = true;
        if (engine != null) {
            engine.pause();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        updateNotification("⏸ Приостановлен (экран выключен)");
    }

    private void resumeEngine() {
        paused = false;
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
        if (engine != null) {
            new Thread(() -> {
                try {
                    engine.resume(port);
                } catch (Exception ignored) {}
            }).start();
        }
        updateNotification(boundIp + ":" + port + " | Активен");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (engine != null) {
            engine.stop();
            engine = null;
        }

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

        int mode = prefs.getInt("proxy_mode", ProxyEngine.MODE_ORIGINAL);
        String vlessUri = prefs.getString("vless_uri", "");
        smartSleepEnabled = prefs.getBoolean("smart_sleep", true);

        startForeground(NOTIF_ID, buildNotification());
        startTime = System.currentTimeMillis();

        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }

        isMobile = NetworkUtils.isMobileNetwork(this);

        engine = new ProxyEngine();
        engine.setMode(mode);
        engine.setVlessUri(vlessUri);
        engine.setBoundIp(boundIp);
        engine.setMobileMode(isMobile);

        new Thread(() -> {
            try {
                engine.start(port);
            } catch (Exception e) {
                handler.post(this::stopSelf);
            }
        }).start();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        }
        if (networkCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) cm.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "TG Proxy",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("SOCKS5 Proxy");
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent ni = new Intent(this, MainActivity.class);
        ni.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, ni,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }

        return b.setContentTitle("TG Proxy")
                .setContentText(boundIp + ":" + port)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    public void updateNotification(String text) {
        try {
            Intent ni = new Intent(this, MainActivity.class);
            ni.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, ni,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                b = new Notification.Builder(this, CHANNEL_ID);
            } else {
                b = new Notification.Builder(this);
            }

            Notification n = b.setContentTitle("TG Proxy")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build();

            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.notify(NOTIF_ID, n);
        } catch (Exception ignored) {}
    }
}
