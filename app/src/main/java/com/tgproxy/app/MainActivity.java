package com.tgproxy.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.net.InetAddress;

public class MainActivity extends AppCompatActivity {

    private Button btnStart, btnStop;
    private TextView tvStatus, tvAddress, tvPort, tvTgLink, tvPing, tvTraffic, tvUptime;
    private RadioGroup rgMode;
    private RadioButton rbOriginal, rbPython, rbVless;
    private EditText etVless;
    private LinearLayout llVless;
    private CheckBox cbDynamicPort, cbRotateIp, cbAutostart;
    private Handler handler;
    private Runnable statsUpdater;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        handler = new Handler(Looper.getMainLooper());

        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        tvStatus = findViewById(R.id.tv_status);
        tvAddress = findViewById(R.id.tv_address);
        tvPort = findViewById(R.id.tv_port);
        tvTgLink = findViewById(R.id.tv_tg_link);
        tvPing = findViewById(R.id.tv_ping);
        tvTraffic = findViewById(R.id.tv_traffic);
        tvUptime = findViewById(R.id.tv_uptime);
        rgMode = findViewById(R.id.rg_mode);
        rbOriginal = findViewById(R.id.rb_original);
        rbPython = findViewById(R.id.rb_python);
        rbVless = findViewById(R.id.rb_vless);
        etVless = findViewById(R.id.et_vless);
        llVless = findViewById(R.id.ll_vless);
        cbDynamicPort = findViewById(R.id.cb_dynamic_port);
        cbRotateIp = findViewById(R.id.cb_rotate_ip);
        cbAutostart = findViewById(R.id.cb_autostart);

        int savedMode = prefs.getInt("proxy_mode", ProxyEngine.MODE_ORIGINAL);
        switch (savedMode) {
            case ProxyEngine.MODE_PYTHON:
                rbPython.setChecked(true);
                break;
            case ProxyEngine.MODE_VLESS:
                rbVless.setChecked(true);
                llVless.setVisibility(View.VISIBLE);
                break;
            default:
                rbOriginal.setChecked(true);
                break;
        }

        etVless.setText(prefs.getString("vless_uri", ""));
        cbDynamicPort.setChecked(prefs.getBoolean("dynamic_port", false));
        cbRotateIp.setChecked(prefs.getBoolean("rotate_ip", false));
        cbAutostart.setChecked(prefs.getBoolean("autostart_open", false));

        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_vless) {
                llVless.setVisibility(View.VISIBLE);
                saveMode(ProxyEngine.MODE_VLESS);
            } else if (checkedId == R.id.rb_python) {
                llVless.setVisibility(View.GONE);
                saveMode(ProxyEngine.MODE_PYTHON);
            } else {
                llVless.setVisibility(View.GONE);
                saveMode(ProxyEngine.MODE_ORIGINAL);
            }
        });

        cbDynamicPort.setOnCheckedChangeListener((v, c) ->
                prefs.edit().putBoolean("dynamic_port", c).apply());
        cbRotateIp.setOnCheckedChangeListener((v, c) ->
                prefs.edit().putBoolean("rotate_ip", c).apply());
        cbAutostart.setOnCheckedChangeListener((v, c) ->
                prefs.edit().putBoolean("autostart_open", c).apply());

        btnStart.setOnClickListener(v -> startProxy());
        btnStop.setOnClickListener(v -> stopProxy());

        setupCopyOnTap(tvAddress);
        setupCopyOnTap(tvPort);
        setupCopyOnTap(tvTgLink);
        setupCopyOnTap(tvPing);
        setupCopyOnTap(tvTraffic);

        requestPermissions();
        requestBatteryOptimization();

        statsUpdater = new Runnable() {
            @Override
            public void run() {
                updateStats();
                handler.postDelayed(this, 1000);
            }
        };

        if (ProxyService.getInstance() != null) {
            updateRunningState(true);
        }

        boolean autoOpen = prefs.getBoolean("autostart_open", false);
        if (autoOpen && ProxyService.getInstance() == null) {
            startProxy();
        }

        TextView tvTgChannel = findViewById(R.id.tv_tg_channel);
        TextView tvGithub = findViewById(R.id.tv_github);
        tvTgChannel.setOnClickListener(v -> openLink("https://t.me/TgUnlock2026"));
        tvGithub.setOnClickListener(v -> openLink("https://github.com/Genuys/TelegramUnlockAppAndroid2026"));
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(statsUpdater);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(statsUpdater);
    }

    private void startProxy() {
        saveSettings();

        Intent si = new Intent(this, ProxyService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(si);
        } else {
            startService(si);
        }

        handler.postDelayed(() -> updateRunningState(true), 500);
    }

    private void stopProxy() {
        stopService(new Intent(this, ProxyService.class));
        updateRunningState(false);
    }

    private void saveSettings() {
        SharedPreferences.Editor e = prefs.edit();
        if (rbOriginal.isChecked()) e.putInt("proxy_mode", ProxyEngine.MODE_ORIGINAL);
        else if (rbPython.isChecked()) e.putInt("proxy_mode", ProxyEngine.MODE_PYTHON);
        else if (rbVless.isChecked()) e.putInt("proxy_mode", ProxyEngine.MODE_VLESS);
        e.putString("vless_uri", etVless.getText().toString().trim());
        e.apply();
    }

    private void saveMode(int mode) {
        prefs.edit().putInt("proxy_mode", mode).apply();
    }

    private android.animation.ObjectAnimator pulseAnim;

    private void updateRunningState(boolean running) {
        if (running) {
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            tvStatus.setText("\u2705 \u0410\u043A\u0442\u0438\u0432\u0435\u043D");
            tvStatus.setTextColor(0xFF4CAF50);

            if (pulseAnim == null) {
                android.animation.PropertyValuesHolder sx = android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.1f);
                android.animation.PropertyValuesHolder sy = android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.1f);
                pulseAnim = android.animation.ObjectAnimator.ofPropertyValuesHolder(tvStatus, sx, sy);
                pulseAnim.setDuration(700);
                pulseAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                pulseAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            }
            pulseAnim.start();

            ProxyService svc = ProxyService.getInstance();
            int p = svc != null ? svc.getPort() : 1080;
            String ip = svc != null ? svc.getIp() : "127.0.0.1";
            tvAddress.setText(ip + ":" + p);
            tvPort.setText(String.valueOf(p));
            tvTgLink.setText("tg://socks?server=" + ip + "&port=" + p);
        } else {
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            if (pulseAnim != null) pulseAnim.cancel();
            tvStatus.setScaleX(1f);
            tvStatus.setScaleY(1f);
            tvStatus.setAlpha(1f);
            tvStatus.setText("\u274C \u041E\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D");
            tvStatus.setTextColor(0xFFF44336);
            tvAddress.setText("-");
            tvPort.setText("-");
            tvTgLink.setText("-");
            tvPing.setText("-");
            tvTraffic.setText("-");
            if (tvUptime != null) tvUptime.setText("-");
        }
    }

    private void updateStats() {
        ProxyService svc = ProxyService.getInstance();
        if (svc == null || svc.getEngine() == null) return;

        int p = svc.getPort();
        String ip = svc.getIp();
        if (!tvPort.getText().toString().equals("-") && !tvAddress.getText().toString().equals(ip + ":" + p)) {
            tvAddress.setText(ip + ":" + p);
            tvPort.setText(String.valueOf(p));
            tvTgLink.setText("tg://socks?server=" + ip + "&port=" + p);
        }

        ProxyEngine eng = svc.getEngine();
        long up = eng.bytesUp.get();
        long down = eng.bytesDown.get();
        tvTraffic.setText("\u2191 " + TgConstants.humanBytes(up) +
                "  \u2193 " + TgConstants.humanBytes(down));

        if (tvUptime != null) {
            long secs = svc.getUptime() / 1000;
            String timeStr = String.format(java.util.Locale.US, "%02d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60);
            tvUptime.setText(timeStr);
        }

        new Thread(() -> {
            try {
                long start = System.currentTimeMillis();
                java.net.Socket s = new java.net.Socket();
                s.connect(new java.net.InetSocketAddress("venus.web.telegram.org", 443), 2000);
                long elapsed = System.currentTimeMillis() - start;
                s.close();
                handler.post(() -> tvPing.setText(elapsed + " ms"));
            } catch (Exception e) {
                handler.post(() -> tvPing.setText("err"));
            }
        }).start();

        svc.updateNotification(svc.getIp() + ":" + svc.getPort() +
                " | \u2191" + TgConstants.humanBytes(up) +
                " \u2193" + TgConstants.humanBytes(down));
    }

    private void setupCopyOnTap(TextView tv) {
        tv.setOnClickListener(v -> {
            String text = tv.getText().toString();
            if (text.equals("-")) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("proxy", text));
            Toast.makeText(this, "\u0421\u043A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u043D\u043E", Toast.LENGTH_SHORT).show();
        });
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(intent);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
