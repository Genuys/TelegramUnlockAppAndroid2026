package com.tgproxy.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
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

    private static ProxyService instance;

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
        return "127.0.0.1";
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TGProxy::ProxyWake");
        wakeLock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (engine != null) {
            engine.stop();
            engine = null;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean dynamicPort = prefs.getBoolean("dynamic_port", false);
        if (dynamicPort) {
            port = 10000 + new Random().nextInt(50000);
        } else {
            port = 1080;
        }

        int mode = prefs.getInt("proxy_mode", ProxyEngine.MODE_ORIGINAL);
        boolean rotateIp = prefs.getBoolean("rotate_ip", false);
        String vlessUri = prefs.getString("vless_uri", "");

        startForeground(NOTIF_ID, buildNotification());
        startTime = System.currentTimeMillis();

        engine = new ProxyEngine();
        engine.setMode(mode);
        engine.setVlessUri(vlessUri);
        engine.setRotateIp(rotateIp);

        new Thread(() -> {
            try {
                engine.start(port);
            } catch (Exception e) {
                handler.post(() -> {
                    stopSelf();
                });
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
                .setContentText("127.0.0.1:" + port)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    public void updateNotification(String text) {
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
    }
}
