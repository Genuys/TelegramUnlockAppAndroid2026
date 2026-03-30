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

import java.net.InetSocketAddress;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private Button btnStart, btnStop, btnQpEast, btnQpRu;
    private TextView tvStatus, tvAddress, tvPort, tvTgLink, tvPing, tvTraffic, tvUptime;
    private RadioGroup rgMode;
    private RadioButton rbOriginal, rbPython, rbVless;
    private EditText etVless, etCustomPort, etCustomIp, etTgIp;
    private LinearLayout llVless;
    private CheckBox cbDynamicPort, cbAutostart;
    private Handler handler;
    private Runnable statsUpdater;
    private SharedPreferences prefs;
    private volatile boolean pingRunning = false;

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
        cbAutostart = findViewById(R.id.cb_autostart);
        etCustomPort = findViewById(R.id.et_custom_port);
        etCustomIp = findViewById(R.id.et_custom_ip);
        etTgIp = findViewById(R.id.et_tg_ip);
        btnQpEast = findViewById(R.id.btn_qp_east);
        btnQpRu = findViewById(R.id.btn_qp_ru);

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
        cbAutostart.setChecked(prefs.getBoolean("autostart_open", false));

        int savedPort = prefs.getInt("custom_port", 1080);
        etCustomPort.setText(String.valueOf(savedPort));

        String savedIp = prefs.getString("custom_ip", "127.0.0.1");
        etCustomIp.setText(savedIp);

        String savedTgIp = prefs.getString("tg_ping_ip", "149.154.167.220");
        etTgIp.setText(savedTgIp);

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
        cbAutostart.setOnCheckedChangeListener((v, c) ->
                prefs.edit().putBoolean("autostart_open", c).apply());

        btnStart.setOnClickListener(v -> startProxy());
        btnStop.setOnClickListener(v -> stopProxy());
        btnQpEast.setOnClickListener(v -> openQuickProxy(
                "tg://proxy?server=peyk.acharbashi.info&port=4515" +
                "&secret=7umk8jsddowEqNfzkSDKW25iaXNjb3R0aS55ZWt0YW5ldC5jb20"));
        btnQpRu.setOnClickListener(v -> openQuickProxy(
                "tg://proxy?server=wiseprox.sbrf-cdn342.ru&port=443" +
                "&secret=000102030405060708090a0b0c0d0e0f"));

        setupCopyOnTap(tvAddress);
        setupCopyOnTap(tvPort);
        setupTgLinkTap(tvTgLink);
        setupCopyOnTap(tvPing);
        setupCopyOnTap(tvTraffic);

        requestPermissions();
        requestBatteryOptimization();

        statsUpdater = new Runnable() {
            @Override
            public void run() {
                updateStats();
                handler.postDelayed(this, 2000);
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

    private void openQuickProxy(String tgUrl) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(tgUrl));
            startActivity(i);
        } catch (Exception e) {
            try {
                String web = tgUrl
                        .replace("tg://proxy?", "https://t.me/proxy?")
                        .replace("tg://proxy?", "https://t.me/proxy?");
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(web)));
            } catch (Exception ignored) {
            }
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

        String portStr = etCustomPort.getText().toString().trim();
        int port = 1080;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) port = 1080;
        } catch (NumberFormatException ignored) {
        }
        e.putInt("custom_port", port);

        String ip = etCustomIp.getText().toString().trim();
        if (ip.isEmpty()) ip = "127.0.0.1";
        e.putString("custom_ip", ip);

        String tgIp = etTgIp.getText().toString().trim();
        if (tgIp.isEmpty()) tgIp = "149.154.167.220";
        e.putString("tg_ping_ip", tgIp);

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
            tvStatus.setText("\u2705 \u0410\u043a\u0442\u0438\u0432\u0435\u043d");
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
            tvStatus.setText("\u274C \u041e\u0441\u0442\u0430\u043d\u043e\u0432\u043b\u0435\u043d");
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

        svc.updateNotification(svc.getIp() + ":" + svc.getPort() +
                " | \u2191" + TgConstants.humanBytes(up) +
                " \u2193" + TgConstants.humanBytes(down));

        if (!pingRunning) {
            pingRunning = true;
            new Thread(() -> {
                try {
                    String tgIp = prefs.getString("tg_ping_ip", "149.154.167.220");
                    int proxyPort = svc.getPort();
                    String proxyIp = svc.getIp();
                    long start = System.currentTimeMillis();
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(proxyIp, proxyPort), 3000);
                    s.setSoTimeout(3000);
                    s.setTcpNoDelay(true);

                    java.io.OutputStream out = s.getOutputStream();
                    java.io.InputStream in = s.getInputStream();

                    byte[] tgIpBytes;
                    try {
                        tgIpBytes = java.net.InetAddress.getByName(tgIp).getAddress();
                    } catch (Exception ex) {
                        tgIpBytes = new byte[]{(byte)149, (byte)154, (byte)167, (byte)220};
                    }

                    out.write(new byte[]{0x05, 0x01, 0x00});
                    out.flush();
                    byte[] authResp = new byte[2];
                    int ar = 0;
                    while (ar < 2) {
                        int r = in.read(authResp, ar, 2 - ar);
                        if (r == -1) throw new java.io.IOException("EOF");
                        ar += r;
                    }

                    byte[] req = new byte[10];
                    req[0] = 0x05;
                    req[1] = 0x01;
                    req[2] = 0x00;
                    req[3] = 0x01;
                    req[4] = tgIpBytes[0];
                    req[5] = tgIpBytes[1];
                    req[6] = tgIpBytes[2];
                    req[7] = tgIpBytes[3];
                    req[8] = (byte)(443 >> 8);
                    req[9] = (byte)(443 & 0xFF);
                    out.write(req);
                    out.flush();

                    byte[] resp = new byte[10];
                    int rr = 0;
                    while (rr < 10) {
                        int r = in.read(resp, rr, 10 - rr);
                        if (r == -1) throw new java.io.IOException("EOF");
                        rr += r;
                    }

                    long elapsed = System.currentTimeMillis() - start;
                    s.close();

                    if (resp[1] == 0x00) {
                        handler.post(() -> tvPing.setText(elapsed + " ms"));
                    } else {
                        handler.post(() -> tvPing.setText("err"));
                    }
                } catch (Exception e) {
                    handler.post(() -> tvPing.setText("err"));
                } finally {
                    pingRunning = false;
                }
            }).start();
        }
    }

    private void setupTgLinkTap(TextView tv) {
        tv.setOnClickListener(v -> {
            String text = tv.getText().toString();
            if (text.equals("-")) return;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(text)));
            } catch (Exception e) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("proxy", text));
                Toast.makeText(this, "\u0421\u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043d\u043e", Toast.LENGTH_SHORT).show();
            }
        });
        tv.setOnLongClickListener(v -> {
            String text = tv.getText().toString();
            if (text.equals("-")) return false;
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("proxy", text));
            Toast.makeText(this, "\u0421\u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043d\u043e", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void setupCopyOnTap(TextView tv) {
        tv.setOnClickListener(v -> {
            String text = tv.getText().toString();
            if (text.equals("-")) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("proxy", text));
            Toast.makeText(this, "\u0421\u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043d\u043e", Toast.LENGTH_SHORT).show();
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
