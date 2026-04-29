package com.tgproxy.app;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
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
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LinearLayout tabProxy, tabWarp, tabProxyList, tabDns;
    private LinearLayout contentProxy, contentWarp, contentProxyList, contentDns;
    private View         currentContent;
    private View         currentTab;

    private Button      btnStart, btnStop;
    private TextView    tvStatus, tvAddress, tvPort, tvTgLink, tvPing, tvTraffic, tvUptime;
    private RadioGroup  rgMode;
    private RadioButton rbOriginal, rbPython, rbVless;
    private EditText    etVless, etCustomPort, etCustomIp, etTgIp, etSocks5User, etSocks5Pass;
    private LinearLayout llVless;
    private CheckBox    cbDynamicPort, cbAutostart, cbSmartSleep;
    private ObjectAnimator pulseAnim;

    private WarpManager warpManager;
    private String      currentWarpConfig = "";
    private TextView    tvWarpStatus, tvWarpConfig, tvWarpProxyStatus, tvWarpLibProgress;
    private Button      btnWarpVpgram, btnWarpCopy, btnWarpOpen;
    private Button      btnWarpProxyStart, btnWarpProxyStop;
    private EditText    etWarpUser, etWarpPass;

    private ProxyFetcher proxyFetcher;
    private LinearLayout llProxyItems;
    private TextView     tvProxyListStatus;

    private Handler  handler;
    private Runnable statsUpdater;
    private SharedPreferences prefs;

    private FrameLayout    overlaySettings;
    private TextView       btnSettingsOpen;
    private boolean        isEnglish;
    private boolean        isLightTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs   = PreferenceManager.getDefaultSharedPreferences(this);
        handler = new Handler(Looper.getMainLooper());

        isEnglish    = prefs.getBoolean("lang_en", false);
        isLightTheme = prefs.getBoolean("theme_light", false);

        initTabs();
        initProxyTab();
        initWarpTab();
        initProxyListTab();
        initDnsTab();
        initSettings();
        applyTheme();
        applyLanguage();

        requestPermissions();
        requestBatteryOptimization();

        statsUpdater = new Runnable() {
            @Override public void run() {
                updateStats();
                handler.postDelayed(this, 2000);
            }
        };

        if (ProxyService.getInstance() != null) updateRunningState(true);
        if (prefs.getBoolean("autostart_open", false) && ProxyService.getInstance() == null)
            startProxy();

        proxyFetcher = new ProxyFetcher();
        proxyFetcher.setListener(this::refreshProxyList);
        proxyFetcher.start();

        warpManager = new WarpManager();
        warpManager.setContext(this);
    }

    private void initTabs() {
        tabProxy     = findViewById(R.id.tab_proxy);
        tabWarp      = findViewById(R.id.tab_warp);
        tabProxyList = findViewById(R.id.tab_proxy_list);
        tabDns       = findViewById(R.id.tab_dns);

        contentProxy     = findViewById(R.id.content_proxy);
        contentWarp      = findViewById(R.id.content_warp);
        contentProxyList = findViewById(R.id.content_proxy_list);
        contentDns       = findViewById(R.id.content_dns);

        tabProxy.setOnClickListener(v     -> switchTab(tabProxy,     contentProxy));
        tabWarp.setOnClickListener(v      -> switchTab(tabWarp,      contentWarp));
        tabProxyList.setOnClickListener(v -> switchTab(tabProxyList, contentProxyList));
        tabDns.setOnClickListener(v       -> switchTab(tabDns,       contentDns));

        currentContent = contentProxy;
        currentTab     = tabProxy;
        contentProxy.setVisibility(View.VISIBLE);
        contentWarp.setVisibility(View.GONE);
        contentProxyList.setVisibility(View.GONE);
        contentDns.setVisibility(View.GONE);
        highlightTab(tabProxy);
    }

    private void switchTab(View tab, View content) {
        if (content == currentContent) return;

        if (currentContent != null) {
            currentContent.startAnimation(
                AnimationUtils.loadAnimation(this, R.anim.tab_out));
            currentContent.setVisibility(View.GONE);
        }
        unhighlightTab(currentTab);

        content.setVisibility(View.VISIBLE);
        content.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.tab_in));

        currentContent = content;
        currentTab     = tab;
        highlightTab(tab);
    }

    private void highlightTab(View tab) {
        if (tab == null) return;
        tab.setAlpha(1.0f);
        tab.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
    }

    private void unhighlightTab(View tab) {
        if (tab == null) return;
        tab.setAlpha(0.45f);
        tab.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
    }

    private void animBtn(View v) {
        v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80)
            .withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
            .start();
    }

    private void initProxyTab() {
        btnStart      = findViewById(R.id.btn_start);
        btnStop       = findViewById(R.id.btn_stop);
        tvStatus      = findViewById(R.id.tv_status);
        tvAddress     = findViewById(R.id.tv_address);
        tvPort        = findViewById(R.id.tv_port);
        tvTgLink      = findViewById(R.id.tv_tg_link);
        tvPing        = findViewById(R.id.tv_ping);
        tvTraffic     = findViewById(R.id.tv_traffic);
        tvUptime      = findViewById(R.id.tv_uptime);
        rgMode        = findViewById(R.id.rg_mode);
        rbOriginal    = findViewById(R.id.rb_original);
        rbPython      = findViewById(R.id.rb_python);
        rbVless       = findViewById(R.id.rb_vless);
        etVless       = findViewById(R.id.et_vless);
        llVless       = findViewById(R.id.ll_vless);
        cbDynamicPort = findViewById(R.id.cb_dynamic_port);
        cbAutostart   = findViewById(R.id.cb_autostart);
        cbSmartSleep  = findViewById(R.id.cb_smart_sleep);
        etCustomPort  = findViewById(R.id.et_custom_port);
        etCustomIp    = findViewById(R.id.et_custom_ip);
        etTgIp        = findViewById(R.id.et_tg_ip);
        etSocks5User  = findViewById(R.id.et_socks5_user);
        etSocks5Pass  = findViewById(R.id.et_socks5_pass);

        int savedMode = prefs.getInt("proxy_mode", ProxyEngine.MODE_ORIGINAL);
        switch (savedMode) {
            case ProxyEngine.MODE_PYTHON:
                rbPython.setChecked(true); break;
            case ProxyEngine.MODE_VLESS:
                rbVless.setChecked(true);
                llVless.setVisibility(View.VISIBLE); break;
            default:
                rbOriginal.setChecked(true); break;
        }

        etVless.setText(prefs.getString("vless_uri", ""));
        cbDynamicPort.setChecked(prefs.getBoolean("dynamic_port", false));
        cbAutostart.setChecked(prefs.getBoolean("autostart_open", false));
        cbSmartSleep.setChecked(prefs.getBoolean("smart_sleep", true));
        etCustomPort.setText(String.valueOf(prefs.getInt("custom_port", 1080)));
        etCustomIp.setText(prefs.getString("custom_ip", "127.0.0.1"));
        etTgIp.setText(prefs.getString("tg_ping_ip", "149.154.167.220"));
        etSocks5User.setText(prefs.getString("socks5_user", ""));
        etSocks5Pass.setText(prefs.getString("socks5_pass", ""));

        updateRunningState(prefs.getBoolean("proxy_enabled", false));

        rgMode.setOnCheckedChangeListener((g, id) -> {
            if (id == R.id.rb_vless) {
                llVless.setVisibility(View.VISIBLE);
                saveMode(ProxyEngine.MODE_VLESS);
            } else if (id == R.id.rb_python) {
                llVless.setVisibility(View.GONE);
                saveMode(ProxyEngine.MODE_PYTHON);
            } else {
                llVless.setVisibility(View.GONE);
                saveMode(ProxyEngine.MODE_ORIGINAL);
            }
        });

        cbDynamicPort.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean("dynamic_port", c).apply());
        cbAutostart.setOnCheckedChangeListener((v, c)   -> prefs.edit().putBoolean("autostart_open", c).apply());
        cbSmartSleep.setOnCheckedChangeListener((v, c)  -> prefs.edit().putBoolean("smart_sleep", c).apply());

        btnStart.setOnClickListener(v -> { animBtn(v); startProxy(); });
        btnStop.setOnClickListener(v  -> { animBtn(v); stopProxy();  });

        setupCopyOnTap(tvAddress);
        setupCopyOnTap(tvPort);
        setupTgLinkTap(tvTgLink);
        setupCopyOnTap(tvPing);
        setupCopyOnTap(tvTraffic);

        TextView tvTgChannel = findViewById(R.id.tv_tg_channel);
        TextView tvGithub    = findViewById(R.id.tv_github);
        if (tvTgChannel != null) tvTgChannel.setOnClickListener(v -> openLink("https://t.me/TgUnlock2026"));
        if (tvGithub    != null) tvGithub.setOnClickListener(v    -> openLink("https://github.com/Genuys/TelegramUnlockAppAndroid2026"));

        Button btnPingNow = findViewById(R.id.btn_ping_now);
        if (btnPingNow != null) btnPingNow.setOnClickListener(v -> { animBtn(v); measurePing(); });
    }

    private void initWarpTab() {
        tvWarpStatus     = findViewById(R.id.tv_warp_status);
        tvWarpConfig     = findViewById(R.id.tv_warp_config);
        tvWarpProxyStatus = findViewById(R.id.tv_warp_proxy_status);
        tvWarpLibProgress = findViewById(R.id.tv_warp_lib_progress);
        etWarpUser       = findViewById(R.id.et_warp_user);
        etWarpPass       = findViewById(R.id.et_warp_pass);
        btnWarpVpgram    = findViewById(R.id.btn_warp_vpgram);
        btnWarpCopy      = findViewById(R.id.btn_warp_copy);
        btnWarpOpen      = findViewById(R.id.btn_warp_open);
        btnWarpProxyStart = findViewById(R.id.btn_warp_proxy_start);
        btnWarpProxyStop  = findViewById(R.id.btn_warp_proxy_stop);

        btnWarpVpgram.setOnClickListener(v -> {
            animBtn(v);
            btnWarpVpgram.setEnabled(false);
            tvWarpStatus.setText(isEnglish ? "⏳ Loading..." : "⏳ Загружаем...");
            tvWarpStatus.setTextColor(0xFFFFAB00);
            warpManager.fetchVpgramConfig(new WarpManager.WarpCallback() {
                @Override public void onConfig(String conf, String label) {
                    btnWarpVpgram.setEnabled(true);
                    currentWarpConfig = conf;
                    tvWarpStatus.setText("✅ " + label);
                    tvWarpStatus.setTextColor(0xFF4CAF50);
                    tvWarpConfig.setText(conf);
                    btnWarpCopy.setEnabled(true);
                    btnWarpOpen.setEnabled(true);
                    if (!warpManager.isProxyRunning()) {
                        btnWarpProxyStart.setEnabled(true);
                        tvWarpProxyStatus.setText(isEnglish ? "● Ready to start" : "● Готово к запуску");
                        tvWarpProxyStatus.setTextColor(0xFFFFAB00);
                    }
                    tvWarpConfig.setAlpha(0f);
                    tvWarpConfig.animate().alpha(1f).setDuration(350).start();
                }
                @Override public void onError(String msg) {
                    btnWarpVpgram.setEnabled(true);
                    tvWarpStatus.setText(msg);
                    tvWarpStatus.setTextColor(0xFFF44336);
                }
                @Override public void onProgress(String msg) {
                    tvWarpStatus.setText(msg);
                    tvWarpStatus.setTextColor(0xFFFFAB00);
                }
            });
        });

        btnWarpCopy.setOnClickListener(v -> {
            animBtn(v);
            if (!currentWarpConfig.isEmpty()) {
                copyToClipboard(currentWarpConfig);
                Toast.makeText(this, isEnglish ? "✅ Copied" : "✅ Скопировано", Toast.LENGTH_SHORT).show();
            }
        });

        btnWarpOpen.setOnClickListener(v -> { animBtn(v); shareConfig(); });

        btnWarpProxyStart.setOnClickListener(v -> {
            animBtn(v);
            if (currentWarpConfig.isEmpty()) {
                Toast.makeText(this, isEnglish ? "⚠️ Get config first" : "⚠️ Сначала получите конфиг", Toast.LENGTH_SHORT).show();
                return;
            }
            btnWarpProxyStart.setEnabled(false);
            tvWarpLibProgress.setVisibility(View.VISIBLE);
            tvWarpProxyStatus.setText(isEnglish ? "⏳ Initializing..." : "⏳ Инициализация...");
            tvWarpProxyStatus.setTextColor(0xFFFFAB00);

            String user = etWarpUser != null ? etWarpUser.getText().toString().trim() : "";
            String pass = etWarpPass != null ? etWarpPass.getText().toString() : "";

            warpManager.startLocalProxy(currentWarpConfig, user, pass,
                new WarpManager.ProxyStateCallback() {
                    @Override public void onProgress(String msg) {
                        tvWarpLibProgress.setText(msg);
                        tvWarpLibProgress.setVisibility(View.VISIBLE);
                    }
                    @Override public void onStarted(int port) {
                        tvWarpLibProgress.setVisibility(View.GONE);
                        tvWarpProxyStatus.setText((isEnglish ? "✅ Active: 127.0.0.1:" : "✅ Активен: 127.0.0.1:") + port);
                        tvWarpProxyStatus.setTextColor(0xFF4CAF50);
                        btnWarpProxyStop.setEnabled(true);
                        btnWarpProxyStop.setBackgroundColor(0xFFF44336);
                        Toast.makeText(MainActivity.this,
                            (isEnglish ? "✅ Proxy started on :" : "✅ Прокси запущен на :") + port, Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onStopped() {
                        tvWarpLibProgress.setVisibility(View.GONE);
                        tvWarpProxyStatus.setText(isEnglish ? "● Stopped" : "● Остановлен");
                        tvWarpProxyStatus.setTextColor(0xFF888888);
                        if (!currentWarpConfig.isEmpty()) btnWarpProxyStart.setEnabled(true);
                        btnWarpProxyStop.setEnabled(false);
                        btnWarpProxyStop.setBackgroundColor(0xFF444444);
                    }
                    @Override public void onError(String msg) {
                        tvWarpLibProgress.setVisibility(View.GONE);
                        tvWarpProxyStatus.setText(msg);
                        tvWarpProxyStatus.setTextColor(0xFFF44336);
                        btnWarpProxyStart.setEnabled(!currentWarpConfig.isEmpty());
                        btnWarpProxyStop.setEnabled(false);
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
        });

        btnWarpProxyStop.setOnClickListener(v -> {
            animBtn(v);
            btnWarpProxyStop.setEnabled(false);
            warpManager.stopLocalProxy(new WarpManager.ProxyStateCallback() {
                @Override public void onStarted(int p) {}
                @Override public void onStopped() {
                    tvWarpLibProgress.setVisibility(View.GONE);
                    tvWarpProxyStatus.setText(isEnglish ? "● Stopped" : "● Остановлен");
                    tvWarpProxyStatus.setTextColor(0xFF888888);
                    if (!currentWarpConfig.isEmpty()) btnWarpProxyStart.setEnabled(true);
                    btnWarpProxyStop.setEnabled(false);
                    btnWarpProxyStop.setBackgroundColor(0xFF444444);
                    Toast.makeText(MainActivity.this, isEnglish ? "Tunnel stopped" : "Туннель остановлен", Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String msg) {
                    btnWarpProxyStop.setEnabled(true);
                }
            });
        });

        btnWarpCopy.setEnabled(false);
        btnWarpOpen.setEnabled(false);
        btnWarpProxyStart.setEnabled(false);
        btnWarpProxyStop.setEnabled(false);

        Button btnAmneziaStore = findViewById(R.id.btn_amnezia_playstore);
        if (btnAmneziaStore != null)
            btnAmneziaStore.setOnClickListener(v -> {
                animBtn(v);
                openLink("https://play.google.com/store/apps/details?id=org.amnezia.awg");
            });
    }

    private void shareConfig() {
        if (currentWarpConfig.isEmpty()) return;
        try {
            java.io.File f = new java.io.File(getCacheDir(), "warp_export.conf");
            java.io.FileWriter fw = new java.io.FileWriter(f);
            fw.write(currentWarpConfig);
            fw.close();
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".provider", f);
            Intent si = new Intent(Intent.ACTION_SEND);
            si.setType("text/plain");
            si.putExtra(Intent.EXTRA_STREAM, uri);
            si.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(si, isEnglish ? "Share config" : "Поделиться конфигом"));
        } catch (Exception e) {
            copyToClipboard(currentWarpConfig);
            Toast.makeText(this, isEnglish ? "Config copied to clipboard" : "Конфиг скопирован в буфер", Toast.LENGTH_LONG).show();
        }
    }

    private void initProxyListTab() {
        llProxyItems      = findViewById(R.id.ll_proxy_items);
        tvProxyListStatus = findViewById(R.id.tv_proxy_list_status);
        Button btnRefresh = findViewById(R.id.btn_proxy_refresh);
        if (btnRefresh != null)
            btnRefresh.setOnClickListener(v -> {
                animBtn(v);
                tvProxyListStatus.setText(isEnglish ? "⏳ Loading..." : "⏳ Загрузка...");
                tvProxyListStatus.setTextColor(0xFFFFAB00);
                proxyFetcher.fetchNow();
            });
    }

    private void refreshProxyList(List<ProxyFetcher.ProxyEntry> proxies) {
        llProxyItems.removeAllViews();
        if (proxies.isEmpty()) {
            tvProxyListStatus.setText(isEnglish ? "❌ No proxies found" : "❌ Прокси не найдены");
            tvProxyListStatus.setTextColor(0xFFF44336);
            return;
        }
        tvProxyListStatus.setText((isEnglish ? "✅ Found: " : "✅ Найдено: ") + proxies.size());
        tvProxyListStatus.setTextColor(0xFF4CAF50);
        int n = 1;
        for (ProxyFetcher.ProxyEntry entry : proxies) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 10, 0, 10);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            LinearLayout vl = new LinearLayout(this);
            vl.setOrientation(LinearLayout.VERTICAL);
            vl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(this);
            tvName.setTextColor(0xFF3390EC);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setText((isEnglish ? "Server #" : "Сервер #") + n++);
            TextView tvInfo = new TextView(this);
            tvInfo.setTextColor(0xFF666666);
            tvInfo.setTextSize(11);
            tvInfo.setText(entry.server + ":" + entry.port);
            vl.addView(tvName);
            vl.addView(tvInfo);

            String pingStr = entry.ping >= 0 ? entry.ping + " ms" : "…";
            int pingColor = entry.ping < 0 ? 0xFF666666
                : entry.ping < 200 ? 0xFF4CAF50
                : entry.ping < 500 ? 0xFFFFAB00 : 0xFFF44336;
            TextView tvPingItem = new TextView(this);
            tvPingItem.setTextColor(pingColor);
            tvPingItem.setTextSize(12);
            tvPingItem.setText(pingStr);
            tvPingItem.setPadding(14, 0, 14, 0);

            Button btnConnect = new Button(this);
            btnConnect.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 76));
            btnConnect.setText("➤");
            btnConnect.setTextColor(0xFFFFFFFF);
            btnConnect.setTextSize(14);
            android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
            btnBg.setColor(0xFF3390EC);
            btnBg.setCornerRadius(36);
            btnConnect.setBackground(btnBg);
            btnConnect.setPadding(20, 0, 20, 0);
            btnConnect.setOnClickListener(v -> {
                animBtn(v);
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(entry.fullLink)));
                } catch (Exception e) {
                    copyToClipboard(entry.fullLink);
                    Toast.makeText(this, isEnglish ? "Copied" : "Скопировано", Toast.LENGTH_SHORT).show();
                }
            });

            row.addView(vl);
            row.addView(tvPingItem);
            row.addView(btnConnect);
            llProxyItems.addView(row);

            View div = new View(this);
            div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            div.setBackgroundColor(0xFF2A2A2A);
            llProxyItems.addView(div);
        }
    }

    private void initSettings() {
        overlaySettings = findViewById(R.id.overlay_settings);
        btnSettingsOpen = findViewById(R.id.btn_settings);

        TextView btnClose    = findViewById(R.id.btn_settings_close);
        TextView btnLangRu   = findViewById(R.id.btn_lang_ru);
        TextView btnLangEn   = findViewById(R.id.btn_lang_en);
        TextView btnThemeDark  = findViewById(R.id.btn_theme_dark);
        TextView btnThemeLight = findViewById(R.id.btn_theme_light);

        updateSettingsHighlight(btnLangRu, btnLangEn, btnThemeDark, btnThemeLight);

        if (btnSettingsOpen != null)
            btnSettingsOpen.setOnClickListener(v -> {
                animBtn(v);
                if (overlaySettings != null) overlaySettings.setVisibility(View.VISIBLE);
            });

        if (overlaySettings != null)
            overlaySettings.setOnClickListener(v -> overlaySettings.setVisibility(View.GONE));

        if (btnClose != null)
            btnClose.setOnClickListener(v -> {
                if (overlaySettings != null) overlaySettings.setVisibility(View.GONE);
            });

        if (btnLangRu != null)
            btnLangRu.setOnClickListener(v -> {
                prefs.edit().putBoolean("lang_en", false).apply();
                isEnglish = false;
                updateSettingsHighlight(btnLangRu, btnLangEn, btnThemeDark, btnThemeLight);
                applyLanguage();
                if (overlaySettings != null) overlaySettings.setVisibility(View.GONE);
            });

        if (btnLangEn != null)
            btnLangEn.setOnClickListener(v -> {
                prefs.edit().putBoolean("lang_en", true).apply();
                isEnglish = true;
                updateSettingsHighlight(btnLangRu, btnLangEn, btnThemeDark, btnThemeLight);
                applyLanguage();
                if (overlaySettings != null) overlaySettings.setVisibility(View.GONE);
            });

        if (btnThemeDark != null)
            btnThemeDark.setOnClickListener(v -> {
                prefs.edit().putBoolean("theme_light", false).apply();
                isLightTheme = false;
                updateSettingsHighlight(btnLangRu, btnLangEn, btnThemeDark, btnThemeLight);
                applyTheme();
                if (overlaySettings != null) overlaySettings.setVisibility(View.GONE);
            });

        if (btnThemeLight != null)
            btnThemeLight.setOnClickListener(v -> {
                prefs.edit().putBoolean("theme_light", true).apply();
                isLightTheme = true;
                updateSettingsHighlight(btnLangRu, btnLangEn, btnThemeDark, btnThemeLight);
                applyTheme();
                if (overlaySettings != null) overlaySettings.setVisibility(View.GONE);
            });
    }

    private void updateSettingsHighlight(TextView ru, TextView en, TextView dark, TextView light) {
        if (ru    != null) { ru.setTextColor(isEnglish    ? 0xFF555555 : 0xFFE0E0E0); ru.setTypeface(null, isEnglish    ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD); }
        if (en    != null) { en.setTextColor(isEnglish    ? 0xFFE0E0E0 : 0xFF555555); en.setTypeface(null, isEnglish    ? android.graphics.Typeface.BOLD   : android.graphics.Typeface.NORMAL); }
        if (dark  != null) { dark.setTextColor(isLightTheme  ? 0xFF555555 : 0xFFE0E0E0); dark.setTypeface(null, isLightTheme  ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD); }
        if (light != null) { light.setTextColor(isLightTheme ? 0xFFE0E0E0 : 0xFF555555); light.setTypeface(null, isLightTheme ? android.graphics.Typeface.BOLD   : android.graphics.Typeface.NORMAL); }
    }

    private void applyTheme() {
        LinearLayout root = findViewById(R.id.main_root);
        View frame = (View) root.getParent();
        if (isLightTheme) {
            frame.setBackgroundColor(0xFFF0F4F8);
            root.setBackgroundColor(0xFFF0F4F8);
        } else {
            frame.setBackgroundColor(0xFF0F1721);
            root.setBackgroundColor(0xFF0F1721);
        }
        applyThemeToGroup(root);
    }

    private void applyThemeToGroup(android.view.ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            Object tag = v.getTag();
            if ("card".equals(tag)) {
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setColor(isLightTheme ? 0xFFFFFFFF : 0xFF1E1E1E);
                gd.setCornerRadius(56);
                gd.setStroke(1, isLightTheme ? 0xFFDDDDDD : 0xFF2A2A2A);
                v.setBackground(gd);
            }
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                int c = tv.getCurrentTextColor();
                if (c == 0xFFE0E0E0 || c == -2105376 || c == 0xFF1A1A1A) {
                    tv.setTextColor(isLightTheme ? 0xFF1A1A1A : 0xFFE0E0E0);
                } else if (c == 0xFF888888 || c == -8355712 || c == 0xFF666666) {
                    tv.setTextColor(isLightTheme ? 0xFF666666 : 0xFF888888);
                }
            }
            if (v instanceof android.view.ViewGroup) {
                applyThemeToGroup((android.view.ViewGroup) v);
            }
        }
    }

    private void applyLanguage() {
        LinearLayout tabProxyL     = tabProxy;
        LinearLayout tabProxyListL = tabProxyList;
        if (tabProxyL != null && tabProxyL.getChildCount() > 0 && tabProxyL.getChildAt(0) instanceof TextView)
            ((TextView) tabProxyL.getChildAt(0)).setText(isEnglish ? "⚡ Proxy" : "⚡ Прокси");
        if (tabProxyListL != null && tabProxyListL.getChildCount() > 0 && tabProxyListL.getChildAt(0) instanceof TextView)
            ((TextView) tabProxyListL.getChildAt(0)).setText(isEnglish ? "📋 List" : "📋 Список");

        if (btnStart != null) btnStart.setText(isEnglish ? "Start"  : "Запустить");
        if (btnStop  != null) btnStop.setText(isEnglish  ? "Stop"   : "Остановить");

        Button btnPing = findViewById(R.id.btn_ping_now);
        if (btnPing != null) btnPing.setText(isEnglish ? "🔍 Measure ping" : "🔍 Измерить пинг");

        setTvText(R.id.tv_info_title,     isEnglish ? "Information"        : "Информация");
        setTvText(R.id.tv_label_addr,     isEnglish ? "Address"            : "Адрес");
        setTvText(R.id.tv_label_port,     isEnglish ? "Port"               : "Порт");
        setTvText(R.id.tv_label_tglink,   isEnglish ? "Telegram link"      : "Ссылка для Telegram");
        setTvText(R.id.tv_label_ping,     isEnglish ? "Ping"               : "Пинг");
        setTvText(R.id.tv_label_traffic,  isEnglish ? "Traffic"            : "Трафик");
        setTvText(R.id.tv_label_uptime,   isEnglish ? "Uptime"             : "Аптайм");
        setTvText(R.id.tv_mode_title,     isEnglish ? "Mode"               : "Режим работы");

        if (rbOriginal != null) rbOriginal.setText(isEnglish ? "Original"      : "Оригинал");
        if (rbPython   != null) rbPython.setText(isEnglish   ? "Python bypass" : "Python-обходник");

        setTvText(R.id.tv_settings_card_title, isEnglish ? "Settings"                                                       : "Настройки");
        setTvText(R.id.tv_label_proxy_port,    isEnglish ? "Proxy port (1–65535)"                                           : "Порт прокси (1–65535)");
        setTvText(R.id.tv_label_proxy_ip,      isEnglish ? "Local proxy IP"                                                 : "IP локального прокси");
        setTvText(R.id.tv_label_tg_dc,         isEnglish ? "Telegram DC IP (for ping)"                                      : "IP Telegram DC (для пинга)");
        setTvText(R.id.tv_socks5_title,        isEnglish ? "🔒 SOCKS5 protection (optional)"                                : "🔒 Защита SOCKS5 (опционально)");
        setTvText(R.id.tv_socks5_desc,         isEnglish ? "Login and password for proxy access. Leave empty — auth off."   : "Логин и пароль для доступа к прокси. Оставьте пустым — авторизация отключена.");
        setTvText(R.id.tv_label_login,         isEnglish ? "Login"                                                          : "Логин");
        setTvText(R.id.tv_label_password,      isEnglish ? "Password"                                                       : "Пароль");
        setTvText(R.id.tv_about_title,         isEnglish ? "About developer"                                                : "О разработчике");

        CheckBox cbDynPort = findViewById(R.id.cb_dynamic_port);
        CheckBox cbAutostart = findViewById(R.id.cb_autostart);
        CheckBox cbSmartSleep = findViewById(R.id.cb_smart_sleep);
        if (cbDynPort   != null) cbDynPort.setText(isEnglish   ? "Dynamic port"              : "Динамический порт");
        if (cbAutostart != null) cbAutostart.setText(isEnglish ? "Auto-start on open"         : "Автозапуск при открытии");
        if (cbSmartSleep!= null) cbSmartSleep.setText(isEnglish? "Smart sleep (save battery)" : "Умный сон (экономия батареи)");

        setTvText(R.id.tv_warp_title,        isEnglish ? "🔑 WARP Tunnel"               : "🔑 WARP-туннель");
        setTvText(R.id.tv_warp_config_title, isEnglish ? "📄 Config"                    : "📄 Конфиг");
        setTvText(R.id.tv_warp_auth_title,   isEnglish ? "🔐 Authorization (optional)"  : "🔐 Авторизация (необязательно)");
        setTvText(R.id.tv_warp_proxy_title,  isEnglish ? "🚀 Local proxy"               : "🚀 Локальный прокси");

        Button btnWarpGet   = findViewById(R.id.btn_warp_vpgram);
        Button btnWarpCopy  = findViewById(R.id.btn_warp_copy);
        Button btnWarpOpen  = findViewById(R.id.btn_warp_open);
        Button btnWarpStart = findViewById(R.id.btn_warp_proxy_start);
        Button btnWarpStop  = findViewById(R.id.btn_warp_proxy_stop);
        Button btnAmnezia   = findViewById(R.id.btn_amnezia_playstore);
        if (btnWarpGet   != null) btnWarpGet.setText(isEnglish   ? "⬇️ Get config"           : "⬇️ Получить конфиг");
        if (btnWarpCopy  != null) btnWarpCopy.setText(isEnglish  ? "📋 Copy"                  : "📋 Копировать");
        if (btnWarpOpen  != null) btnWarpOpen.setText(isEnglish  ? "📤 Share"                 : "📤 Поделиться");
        if (btnWarpStart != null) btnWarpStart.setText(isEnglish ? "▶ Start"                  : "▶ Запустить");
        if (btnWarpStop  != null) btnWarpStop.setText(isEnglish  ? "■ Stop"                   : "■ Остановить");
        if (btnAmnezia   != null) btnAmnezia.setText(isEnglish   ? "📥 Install AmneziaWG"     : "📥 Установить AmneziaWG");

        android.widget.EditText etWarpUser = findViewById(R.id.et_warp_user);
        android.widget.EditText etWarpPass = findViewById(R.id.et_warp_pass);
        if (etWarpUser != null) etWarpUser.setHint(isEnglish ? "Login"    : "Логин");
        if (etWarpPass != null) etWarpPass.setHint(isEnglish ? "Password" : "Пароль");

        setTvText(R.id.tv_proxy_list_title, isEnglish ? "📋 High-speed servers" : "📋 Высокоскоростные сервера");

        Button btnRefresh = findViewById(R.id.btn_proxy_refresh);
        if (btnRefresh != null) btnRefresh.setText(isEnglish ? "🔄 Refresh proxies" : "🔄 Обновить прокси");

        setTvText(R.id.tv_dns_title, isEnglish ? "🌐 DNS unlock AI & games" : "🌐 DNS unlock ИИ и игр");
        setTvText(R.id.tv_dns_info,
            isEnglish
            ? "GeoHide DNS — a special DNS server that unblocks:\n\n🤖 ChatGPT, Claude, Gemini and other AI\n🎮 Gaming services, Discord\n📱 Social networks\n🌍 Blocked websites\n\nTo activate, set Private DNS in Android settings.\n\nAddress: dns.geohide.ru"
            : "GeoHide DNS — специальный DNS-сервер который разблокирует:\n\n🤖 ChatGPT, Claude, Gemini и другие ИИ\n🎮 Игровые сервисы, Discord\n📱 Социальные сети\n🌍 Заблокированные сайты\n\nДля активации установите Private DNS в настройках Android.\n\nАдрес: dns.geohide.ru");

        Button btnDnsActivate = findViewById(R.id.btn_dns_activate);
        Button btnDnsCopy     = findViewById(R.id.btn_dns_copy);
        if (btnDnsActivate != null) btnDnsActivate.setText(isEnglish ? "⚙️ Open DNS settings"         : "⚙️ Открыть настройки DNS");
        if (btnDnsCopy     != null) btnDnsCopy.setText(isEnglish     ? "📋 Copy dns.geohide.ru"        : "📋 Скопировать dns.geohide.ru");

        setTvText(R.id.tv_dns_instruction,
            isEnglish
            ? "Instructions:\n1. Tap «Open DNS settings»\n2. Go to Private DNS section\n3. Select «Private DNS provider hostname»\n4. Enter: dns.geohide.ru\n5. Save"
            : "Инструкция:\n1. Нажмите «Открыть настройки DNS»\n2. Перейдите в раздел Private DNS\n3. Выберите «Имя хоста провайдера»\n4. Введите: dns.geohide.ru\n5. Сохраните");

        TextView tvSettingsTitle = findViewById(R.id.tv_settings_title);
        TextView tvLabelLang     = findViewById(R.id.tv_label_lang);
        TextView tvLabelTheme    = findViewById(R.id.tv_label_theme);
        if (tvSettingsTitle != null) tvSettingsTitle.setText(isEnglish ? "⚙️ Settings" : "⚙️ Настройки");
        if (tvLabelLang     != null) tvLabelLang.setText(isEnglish     ? "🌍 Language" : "🌍 Язык");
        if (tvLabelTheme    != null) tvLabelTheme.setText(isEnglish    ? "🎨 Theme"    : "🎨 Тема");

        TextView btnLangRu    = findViewById(R.id.btn_lang_ru);
        TextView btnLangEn    = findViewById(R.id.btn_lang_en);
        TextView btnThemeDark  = findViewById(R.id.btn_theme_dark);
        TextView btnThemeLight = findViewById(R.id.btn_theme_light);
        if (btnLangRu    != null) btnLangRu.setText(isEnglish    ? "🇷🇺 Russian" : "🇷🇺 Русский");
        if (btnLangEn    != null) btnLangEn.setText(isEnglish    ? "🇬🇧 English" : "🇬🇧 English");
        if (btnThemeDark  != null) btnThemeDark.setText(isEnglish  ? "🌑 Dark"   : "🌑 Тёмная");
        if (btnThemeLight != null) btnThemeLight.setText(isEnglish ? "☀️ Light"  : "☀️ Светлая");
    }

    private void setTvText(int id, String text) {
        TextView tv = findViewById(id);
        if (tv != null) tv.setText(text);
    }

    private void initDnsTab() {
        Button btnDnsActivate = findViewById(R.id.btn_dns_activate);
        Button btnDnsCopy     = findViewById(R.id.btn_dns_copy);
        if (btnDnsActivate != null)
            btnDnsActivate.setOnClickListener(v -> {
                animBtn(v);
                copyToClipboard("dns.geohide.ru");
                Toast.makeText(this, isEnglish ? "✅ dns.geohide.ru copied" : "✅ dns.geohide.ru скопирован", Toast.LENGTH_LONG).show();
                try { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
                catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                    catch (Exception ignored) {}
                }
            });
        if (btnDnsCopy != null)
            btnDnsCopy.setOnClickListener(v -> {
                animBtn(v);
                copyToClipboard("dns.geohide.ru");
                Toast.makeText(this, isEnglish ? "Copied" : "Скопировано", Toast.LENGTH_SHORT).show();
            });
    }

    private void startProxy() {
        saveSettings();
        Intent si = new Intent(this, ProxyService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(si);
        else startService(si);
        handler.postDelayed(() -> updateRunningState(true), 500);
    }

    private void stopProxy() {
        stopService(new Intent(this, ProxyService.class));
        updateRunningState(false);
    }

    private void updateRunningState(boolean running) {
        if (running) {
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            ProxyService svc    = ProxyService.getInstance();
            boolean      paused = svc != null && svc.isPaused();
            if (paused) {
                tvStatus.setText(isEnglish ? "⏸ Sleep mode" : "⏸ Спящий режим");
                tvStatus.setTextColor(0xFFFFAB00);
                if (pulseAnim != null) pulseAnim.cancel();
            } else {
                tvStatus.setText(isEnglish ? "✅ Active" : "✅ Активен");
                tvStatus.setTextColor(0xFF4CAF50);
                if (pulseAnim == null) {
                    pulseAnim = ObjectAnimator.ofPropertyValuesHolder(tvStatus,
                        PropertyValuesHolder.ofFloat("scaleX", 1f, 1.07f),
                        PropertyValuesHolder.ofFloat("scaleY", 1f, 1.07f));
                    pulseAnim.setDuration(700);
                    pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
                    pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
                }
                if (!pulseAnim.isRunning()) pulseAnim.start();
            }
            int    p  = svc != null ? svc.getPort() : 1080;
            String ip = svc != null ? svc.getIp()   : "127.0.0.1";
            tvAddress.setText(ip + ":" + p);
            tvPort.setText(String.valueOf(p));
            tvTgLink.setText("tg://socks?server=" + ip + "&port=" + p);
        } else {
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            if (pulseAnim != null) { pulseAnim.cancel(); tvStatus.setScaleX(1f); tvStatus.setScaleY(1f); }
            tvStatus.setText(isEnglish ? "❌ Stopped" : "❌ Остановлен");
            tvStatus.setTextColor(0xFFF44336);
            tvAddress.setText("-"); tvPort.setText("-"); tvTgLink.setText("-");
            tvPing.setText("-");    tvTraffic.setText("-");
            if (tvUptime != null) tvUptime.setText("-");
        }
    }

    private void updateStats() {
        ProxyService svc = ProxyService.getInstance();
        if (svc == null || svc.getEngine() == null) return;
        if (svc.isPaused()) {
            tvStatus.setText(isEnglish ? "⏸ Sleep mode" : "⏸ Спящий режим");
            tvStatus.setTextColor(0xFFFFAB00);
            if (pulseAnim != null) { pulseAnim.cancel(); tvStatus.setScaleX(1f); tvStatus.setScaleY(1f); }
            return;
        }
        tvStatus.setText(isEnglish ? "✅ Active" : "✅ Активен");
        tvStatus.setTextColor(0xFF4CAF50);
        int    p  = svc.getPort();
        String ip = svc.getIp();
        tvAddress.setText(ip + ":" + p);
        tvPort.setText(String.valueOf(p));
        tvTgLink.setText("tg://socks?server=" + ip + "&port=" + p);
        ProxyEngine eng = svc.getEngine();
        tvTraffic.setText("↑ " + TgConstants.humanBytes(eng.bytesUp.get())
                + "  ↓ " + TgConstants.humanBytes(eng.bytesDown.get()));
        if (tvUptime != null) {
            long s = svc.getUptime() / 1000;
            tvUptime.setText(String.format(java.util.Locale.US,
                "%02d:%02d:%02d", s/3600, (s%3600)/60, s%60));
        }
        svc.updateNotification(ip + ":" + p
            + " | ↑" + TgConstants.humanBytes(eng.bytesUp.get())
            + " ↓"  + TgConstants.humanBytes(eng.bytesDown.get()));
    }

    private void measurePing() {
        String ip = etTgIp != null ? etTgIp.getText().toString().trim() : "";
        if (ip.isEmpty()) ip = "149.154.167.220";
        final String target = ip;
        if (tvPing != null) tvPing.setText("…");
        new Thread(() -> {
            long start = System.currentTimeMillis();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(target, 443), 5000);
                int ms = (int)(System.currentTimeMillis() - start);
                int col = ms < 100 ? 0xFF4CAF50 : ms < 300 ? 0xFFFFAB00 : 0xFFF44336;
                handler.post(() -> { if (tvPing != null) { tvPing.setText(ms + " ms"); tvPing.setTextColor(col); }});
            } catch (Exception e) {
                handler.post(() -> { if (tvPing != null) { tvPing.setText("N/A"); tvPing.setTextColor(0xFF666666); }});
            }
        }).start();
    }

    private void saveSettings() {
        SharedPreferences.Editor e = prefs.edit();
        if (rbOriginal.isChecked())    e.putInt("proxy_mode", ProxyEngine.MODE_ORIGINAL);
        else if (rbPython.isChecked()) e.putInt("proxy_mode", ProxyEngine.MODE_PYTHON);
        else if (rbVless.isChecked())  e.putInt("proxy_mode", ProxyEngine.MODE_VLESS);
        e.putString("vless_uri", etVless.getText().toString().trim());
        try {
            int p = Integer.parseInt(etCustomPort.getText().toString().trim());
            e.putInt("custom_port", (p >= 1 && p <= 65535) ? p : 1080);
        } catch (NumberFormatException ignored) { e.putInt("custom_port", 1080); }
        String ip = etCustomIp.getText().toString().trim();
        e.putString("custom_ip", ip.isEmpty() ? "127.0.0.1" : ip);
        String tgIp = etTgIp.getText().toString().trim();
        e.putString("tg_ping_ip", tgIp.isEmpty() ? "149.154.167.220" : tgIp);
        e.putString("socks5_user", etSocks5User.getText().toString().trim());
        e.putString("socks5_pass", etSocks5Pass.getText().toString());
        e.apply();
    }

    private void saveMode(int mode) { prefs.edit().putInt("proxy_mode", mode).apply(); }

    private void openLink(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception ignored) {}
    }

    private void copyToClipboard(String text) {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
            .setPrimaryClip(ClipData.newPlainText("tgproxy", text));
    }

    private void setupCopyOnTap(TextView tv) {
        if (tv == null) return;
        tv.setOnClickListener(v -> {
            String t = tv.getText().toString();
            if ("-".equals(t) || "…".equals(t)) return;
            copyToClipboard(t);
            Toast.makeText(this, isEnglish ? "Copied" : "Скопировано", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupTgLinkTap(TextView tv) {
        if (tv == null) return;
        tv.setOnClickListener(v -> {
            String t = tv.getText().toString();
            if ("-".equals(t)) return;
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(t))); }
            catch (Exception e) {
                copyToClipboard(t);
                Toast.makeText(this, isEnglish ? "Copied" : "Скопировано", Toast.LENGTH_SHORT).show();
            }
        });
        tv.setOnLongClickListener(v -> {
            String t = tv.getText().toString();
            if ("-".equals(t)) return false;
            copyToClipboard(t);
            Toast.makeText(this, isEnglish ? "Copied" : "Скопировано", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                try { startActivity(i); } catch (Exception ignored) {}
            }
        }
    }

    @Override protected void onResume()  { super.onResume();  handler.post(statsUpdater); }
    @Override protected void onPause()   { super.onPause();   handler.removeCallbacks(statsUpdater); }
    @Override protected void onDestroy() {
        super.onDestroy();
        if (proxyFetcher != null) proxyFetcher.stop();
        if (warpManager != null && warpManager.isProxyRunning())
            warpManager.stopLocalProxy(new WarpManager.ProxyStateCallback() {
                @Override public void onStarted(int p) {}
                @Override public void onStopped() {}
                @Override public void onError(String m) {}
            });
    }
}
