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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Button btnStart, btnStop;
    private TextView tvStatus, tvAddress, tvPort, tvTgLink, tvPing, tvTraffic, tvUptime;
    private RadioGroup rgMode;
    private RadioButton rbOriginal, rbPython, rbVless;
    private EditText etVless, etCustomPort, etCustomIp, etTgIp;
    private LinearLayout llVless;
    private CheckBox cbDynamicPort, cbAutostart, cbSmartSleep;
    private Handler handler;
    private Runnable statsUpdater;
    private SharedPreferences prefs;
    private volatile boolean pingRunning = false;

    private LinearLayout tabProxy, tabWarp, tabProxyList, tabDns;
    private LinearLayout contentProxy, contentWarp, contentProxyList, contentDns;
    private View activeTab;

    private ProxyFetcher proxyFetcher;
    private LinearLayout llProxyItems;
    private TextView tvProxyListStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        handler = new Handler(Looper.getMainLooper());

        initTabs();
        initProxyTab();
        initWarpTab();
        initProxyListTab();
        initDnsTab();

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

        proxyFetcher = new ProxyFetcher();
        proxyFetcher.setListener(proxies -> refreshProxyList(proxies));
        proxyFetcher.start();
    }

    private void initTabs() {
        tabProxy = findViewById(R.id.tab_proxy);
        tabWarp = findViewById(R.id.tab_warp);
        tabProxyList = findViewById(R.id.tab_proxy_list);
        tabDns = findViewById(R.id.tab_dns);

        contentProxy = findViewById(R.id.content_proxy);
        contentWarp = findViewById(R.id.content_warp);
        contentProxyList = findViewById(R.id.content_proxy_list);
        contentDns = findViewById(R.id.content_dns);

        activeTab = tabProxy;

        tabProxy.setOnClickListener(v -> switchTab(tabProxy, contentProxy));
        tabWarp.setOnClickListener(v -> switchTab(tabWarp, contentWarp));
        tabProxyList.setOnClickListener(v -> switchTab(tabProxyList, contentProxyList));
        tabDns.setOnClickListener(v -> switchTab(tabDns, contentDns));

        switchTab(tabProxy, contentProxy);
    }

    private void switchTab(View tab, View content) {
        contentProxy.setVisibility(View.GONE);
        contentWarp.setVisibility(View.GONE);
        contentProxyList.setVisibility(View.GONE);
        contentDns.setVisibility(View.GONE);

        tabProxy.setAlpha(0.5f);
        tabWarp.setAlpha(0.5f);
        tabProxyList.setAlpha(0.5f);
        tabDns.setAlpha(0.5f);

        content.setVisibility(View.VISIBLE);
        tab.setAlpha(1.0f);
        activeTab = tab;
    }

    private void initProxyTab() {
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
        cbSmartSleep = findViewById(R.id.cb_smart_sleep);
        etCustomPort = findViewById(R.id.et_custom_port);
        etCustomIp = findViewById(R.id.et_custom_ip);
        etTgIp = findViewById(R.id.et_tg_ip);

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

        // Create Animated Premium Background - More Vibrant for 'Premium' feel
        View rootLayout = findViewById(R.id.main_root);
        if (rootLayout != null) {
            // Brighter shades for visibility
            android.animation.ValueAnimator colorAnim = android.animation.ObjectAnimator.ofArgb(rootLayout, "backgroundColor", 
                0xFF0F1721, 0xFF1E2F44, 0xFF142436, 0xFF0F1721);
            colorAnim.setDuration(9000); // Faster duration
            colorAnim.setEvaluator(new android.animation.ArgbEvaluator());
            colorAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            colorAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            colorAnim.start();
        }

        updateRunningState(prefs.getBoolean("proxy_enabled", false));
        cbSmartSleep.setChecked(prefs.getBoolean("smart_sleep", true));

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
        cbSmartSleep.setOnCheckedChangeListener((v, c) ->
                prefs.edit().putBoolean("smart_sleep", c).apply());

        btnStart.setOnClickListener(v -> startProxy());
        btnStop.setOnClickListener(v -> stopProxy());

        setupCopyOnTap(tvAddress);
        setupCopyOnTap(tvPort);
        setupTgLinkTap(tvTgLink);
        setupCopyOnTap(tvPing);
        setupCopyOnTap(tvTraffic);

        TextView tvTgChannel = findViewById(R.id.tv_tg_channel);
        TextView tvGithub = findViewById(R.id.tv_github);
        tvTgChannel.setOnClickListener(v -> openLink("https://t.me/TgUnlock2026"));
        tvGithub.setOnClickListener(v -> openLink("https://github.com/Genuys/TelegramUnlockAppAndroid2026"));
    }

    private void initWarpTab() {
        Button btnAmneziaPlay = findViewById(R.id.btn_amnezia_playstore);
        Button btnAmneziaSite = findViewById(R.id.btn_amnezia_site);
        Button btnInstruction = findViewById(R.id.btn_warp_instruction);

        btnAmneziaPlay.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=org.amnezia.awg")));
            } catch (Exception ignored) {}
        });

        btnAmneziaSite.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://warp-generator.github.io/")));
            } catch (Exception ignored) {}
        });

        btnInstruction.setOnClickListener(v -> {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
            builder.setTitle("📖 Инструкция AmneziaWG");
            builder.setMessage("1. Скачайте приложение AmneziaWG по кнопке выше.\n\n2. Откройте сайт-инструкцию для получения бесплатного или приватного WG-конфига.\n\n3. В приложении Amnezia нажмите [+] внизу экрана, выберите «Импорт из файла» или скопируйте текст в «Пользовательский туннель».\n\n4. Включите туннель — теперь ваш Телеграм работает без ограничений!");
            builder.setPositiveButton("Понятно", null);
            androidx.appcompat.app.AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    private void initProxyListTab() {
        llProxyItems = findViewById(R.id.ll_proxy_items);
        tvProxyListStatus = findViewById(R.id.tv_proxy_list_status);
        Button btnRefresh = findViewById(R.id.btn_proxy_refresh);

        btnRefresh.setOnClickListener(v -> {
            tvProxyListStatus.setText("⏳ Загрузка прокси...");
            tvProxyListStatus.setTextColor(0xFFFFAB00);
            proxyFetcher.fetchNow();
        });
    }

    private void refreshProxyList(List<ProxyFetcher.ProxyEntry> proxies) {
        llProxyItems.removeAllViews();

        if (proxies.isEmpty()) {
            tvProxyListStatus.setText("❌ Прокси не найдены");
            tvProxyListStatus.setTextColor(0xFFF44336);
            return;
        }

        tvProxyListStatus.setText("✅ Найдено: " + proxies.size());
        tvProxyListStatus.setTextColor(0xFF4CAF50);

        int count = 1;
        for (ProxyFetcher.ProxyEntry entry : proxies) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            LinearLayout vLayout = new LinearLayout(this);
            vLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams vParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            vLayout.setLayoutParams(vParams);

            TextView tvName = new TextView(this);
            tvName.setTextColor(0xFF3390EC);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setText("Сервер #" + (count++));

            TextView tvInfo = new TextView(this);
            tvInfo.setTextColor(0xFF888888);
            tvInfo.setTextSize(11);
            tvInfo.setText(entry.server + ":" + entry.port);

            vLayout.addView(tvName);
            vLayout.addView(tvInfo);

            String pingStr = entry.ping >= 0 ? entry.ping + "ms" : "N/A";
            int pingColor = entry.ping < 0 ? 0xFF888888 : entry.ping < 200 ? 0xFF4CAF50 : entry.ping < 500 ? 0xFFFFAB00 : 0xFFF44336;

            TextView tvPingItem = new TextView(this);
            tvPingItem.setTextColor(pingColor);
            tvPingItem.setTextSize(13);
            tvPingItem.setText(pingStr);
            tvPingItem.setPadding(16, 0, 16, 0);

            Button btnConnect = new Button(this);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 80);
            btnConnect.setLayoutParams(btnParams);
            btnConnect.setText("➤");
            btnConnect.setTextColor(0xFFFFFFFF);
            btnConnect.setTextSize(14);
            btnConnect.setBackgroundColor(0xFF3390EC);
            btnConnect.setPadding(24, 0, 24, 0);
            btnConnect.setOnClickListener(v -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(entry.fullLink));
                    startActivity(i);
                } catch (Exception e) {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("proxy", entry.fullLink));
                    Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
                }
            });

            row.addView(vLayout);
            row.addView(tvPingItem);
            row.addView(btnConnect);
            llProxyItems.addView(row);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFF333333);
            llProxyItems.addView(divider);
        }
    }

    private void initDnsTab() {
        Button btnDnsActivate = findViewById(R.id.btn_dns_activate);
        TextView tvDnsInfo = findViewById(R.id.tv_dns_info);

        btnDnsActivate.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("dns", "dns.geohide.ru"));
            Toast.makeText(this, "✅ Скопировано dns.geohide.ru!\nВставьте в поле 'Частный DNS' (Private DNS)", Toast.LENGTH_LONG).show();

            try {
                Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_SETTINGS);
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        });

        Button btnDnsCopy = findViewById(R.id.btn_dns_copy);
        btnDnsCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("dns", "dns.geohide.ru"));
            Toast.makeText(this, "dns.geohide.ru скопировано!", Toast.LENGTH_SHORT).show();
        });
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (proxyFetcher != null) proxyFetcher.stop();
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
        } catch (NumberFormatException ignored) {}
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
            ProxyService svc = ProxyService.getInstance();
            boolean paused = svc != null && svc.isPaused();
            if (paused) {
                tvStatus.setText("⏸ Спящий режим");
                tvStatus.setTextColor(0xFFFFAB00);
            } else {
                tvStatus.setText("✅ Активен");
                tvStatus.setTextColor(0xFF4CAF50);
            }

            if (pulseAnim == null) {
                android.animation.PropertyValuesHolder sx = android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.1f);
                android.animation.PropertyValuesHolder sy = android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.1f);
                pulseAnim = android.animation.ObjectAnimator.ofPropertyValuesHolder(tvStatus, sx, sy);
                pulseAnim.setDuration(700);
                pulseAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                pulseAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            }
            if (!paused) pulseAnim.start();

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
            tvStatus.setText("❌ Остановлен");
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

        if (svc.isPaused()) {
            tvStatus.setText("⏸ Спящий режим");
            tvStatus.setTextColor(0xFFFFAB00);
            if (pulseAnim != null) pulseAnim.cancel();
            tvStatus.setScaleX(1f);
            tvStatus.setScaleY(1f);
            return;
        } else {
            tvStatus.setText("✅ Активен");
            tvStatus.setTextColor(0xFF4CAF50);
        }

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
        tvTraffic.setText("↑ " + TgConstants.humanBytes(up) +
                "  ↓ " + TgConstants.humanBytes(down));

        if (tvUptime != null) {
            long secs = svc.getUptime() / 1000;
            String timeStr = String.format(java.util.Locale.US, "%02d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60);
            tvUptime.setText(timeStr);
        }

        String netType = svc.isMobileNetwork() ? " 📱" : " 📶";
        svc.updateNotification(svc.getIp() + ":" + svc.getPort() +
                " | ↑" + TgConstants.humanBytes(up) +
                " ↓" + TgConstants.humanBytes(down) + netType);
        
        tvPing.setText("-");
    }

    private void setupTgLinkTap(TextView tv) {
        tv.setOnClickListener(v -> {
            String text = tv.getText().toString();
            if (text.equals("-")) return;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(text)));
            } catch (Exception e) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("proxy", text));
                Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
            }
        });
        tv.setOnLongClickListener(v -> {
            String text = tv.getText().toString();
            if (text.equals("-")) return false;
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("proxy", text));
            Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void setupCopyOnTap(TextView tv) {
        tv.setOnClickListener(v -> {
            String text = tv.getText().toString();
            if (text.equals("-")) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("proxy", text));
            Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
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
                } catch (Exception ignored) {}
            }
        }
    }
}
