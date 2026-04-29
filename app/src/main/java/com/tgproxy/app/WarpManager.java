package com.tgproxy.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;

public class WarpManager {

    private static final String TAG = "WarpManager";

    public static final int    LOCAL_PORT = 2337;
    public static final String LOCAL_IP   = "127.0.0.1";

    public interface WarpCallback {
        void onConfig(String conf, String label);
        void onError(String msg);
        void onProgress(String msg);
    }

    public interface ProxyStateCallback {
        void onStarted(int port);
        void onStopped();
        void onError(String msg);
        default void onProgress(String msg) {}
    }

    private final Handler       mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean proxyOn     = new AtomicBoolean(false);
    private final AwgLibManager libManager  = new AwgLibManager();
    private Context             appContext;

    private ServerSocket       javaServer;
    private ExecutorService    javaPool;
    private boolean            usingJavaFallback = false;

    public void setContext(Context ctx) {
        appContext = ctx.getApplicationContext();
    }

    public boolean isProxyRunning() { return proxyOn.get(); }

    public void fetchVpgramConfig(WarpCallback cb) {
        mainHandler.post(() -> cb.onProgress("⏳ Загружаем конфиг..."));
        new Thread(() -> {
            String devId = randomId(16);
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    URL url = new URL(
                        "https://web-api.vpgram.click/client-api/v1/download-anonymous-key");
                    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("X-Device-Id", devId);
                    conn.setConnectTimeout(12_000);
                    conn.setReadTimeout(20_000);
                    int code = conn.getResponseCode();
                    if (code == 200) {
                        String fname = "warp.conf";
                        String cd = conn.getHeaderField("Content-Disposition");
                        if (cd != null && cd.contains("filename=")) {
                            String ex = cd.split("filename=")[1].trim().replaceAll("[\"' ]", "");
                            if (!ex.isEmpty()) fname = ex;
                        }
                        BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line).append("\n");
                        br.close();
                        conn.disconnect();
                        final String conf = sb.toString(), label = fname;
                        mainHandler.post(() -> cb.onConfig(conf, label));
                        return;
                    }
                    conn.disconnect();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    if (attempt == 3) {
                        final String msg = "❌ " + (e.getMessage() != null ? e.getMessage()
                                                                            : e.getClass().getSimpleName());
                        mainHandler.post(() -> cb.onError(msg));
                    } else {
                        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    }
                }
            }
        }, "warp-fetch").start();
    }

    public void startLocalProxy(String warpConf, String username, String password,
                                ProxyStateCallback cb) {
        if (proxyOn.get()) {
            mainHandler.post(() -> cb.onError("⚠️ Уже запущен на :" + LOCAL_PORT));
            return;
        }
        if (warpConf == null || warpConf.trim().isEmpty()) {
            mainHandler.post(() -> cb.onError("❌ Сначала получите конфиг"));
            return;
        }
        mainHandler.post(() -> cb.onProgress("⏳ Инициализация..."));

        AwgUapiParser.Result parsed = AwgUapiParser.parse(warpConf);

        boolean useAuth  = username != null && !username.isEmpty()
                        && password != null && !password.isEmpty();
        String  authUser = useAuth ? username : null;
        String  authPass = useAuth ? password : null;

        if (appContext != null) {
            libManager.ensureLoaded(appContext, new AwgLibManager.LoadCallback() {
                @Override public void onProgress(String msg) {
                    mainHandler.post(() -> cb.onProgress(msg));
                }
                @Override public void onLoaded() {
                    mainHandler.post(() -> cb.onProgress("⏳ Запуск туннеля..."));
                    new Thread(() -> {
                        try {
                            int result = AwgLib.nativeStart(
                                    parsed.uapi, parsed.localIp, parsed.dnsIp, LOCAL_PORT);
                            if (result == 0) {
                                proxyOn.set(true);
                                usingJavaFallback = false;
                                mainHandler.post(() -> cb.onStarted(LOCAL_PORT));
                            } else {
                                Log.w(TAG, "nativeStart returned " + result + ", using Java fallback");
                                startJavaProxy(authUser, authPass, cb);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "nativeStart exception: " + e.getMessage());
                            startJavaProxy(authUser, authPass, cb);
                        }
                    }, "awg-start").start();
                }
                @Override public void onError(String msg) {
                    Log.w(TAG, "lib load error: " + msg + ", using Java fallback");
                    startJavaProxy(authUser, authPass, cb);
                }
            });
        } else {
            startJavaProxy(authUser, authPass, cb);
        }
    }

    private void startJavaProxy(String username, String password, ProxyStateCallback cb) {
        mainHandler.post(() -> cb.onProgress("⏳ Запуск SOCKS5..."));
        javaPool = Executors.newCachedThreadPool();
        javaPool.submit(() -> {
            try {
                javaServer = new ServerSocket();
                javaServer.setReuseAddress(true);
                javaServer.bind(new InetSocketAddress(
                        InetAddress.getByName(LOCAL_IP), LOCAL_PORT));
                proxyOn.set(true);
                usingJavaFallback = true;
                mainHandler.post(() -> cb.onStarted(LOCAL_PORT));

                while (proxyOn.get() && !javaServer.isClosed()) {
                    try {
                        Socket client = javaServer.accept();
                        client.setSoTimeout(20_000);
                        javaPool.submit(() -> handleSocks5(client, username, password));
                    } catch (Exception ignored) { break; }
                }
            } catch (Exception e) {
                proxyOn.set(false);
                final String msg = "❌ " + (e.getMessage() != null ? e.getMessage()
                                                                    : e.getClass().getSimpleName());
                mainHandler.post(() -> cb.onError(msg));
            }
        });
    }

    private void handleSocks5(Socket client, String username, String password) {
        boolean hasAuth = username != null && !username.isEmpty()
                       && password != null && !password.isEmpty();
        try {
            InputStream  in  = client.getInputStream();
            OutputStream out = client.getOutputStream();

            int ver = in.read();
            if (ver != 5) { client.close(); return; }
            int nm = in.read();
            byte[] methods = readN(in, nm);

            if (hasAuth) {
                boolean clientSupportsAuth = false;
                for (byte m : methods) if (m == 2) { clientSupportsAuth = true; break; }
                if (!clientSupportsAuth) {
                    out.write(new byte[]{5, (byte)0xFF});
                    client.close(); return;
                }
                out.write(new byte[]{5, 2});
                if (!doAuth(in, out, username, password)) { client.close(); return; }
            } else {
                out.write(new byte[]{5, 0});
            }

            int v2 = in.read();
            if (v2 != 5) { client.close(); return; }
            int cmd  = in.read();
            in.read(); // RSV
            int atyp = in.read();

            if (cmd != 1) {
                out.write(new byte[]{5, 7, 0, 1, 0,0,0,0, 0,0});
                client.close(); return;
            }

            String dstHost;
            if (atyp == 1) {
                byte[] a = readN(in, 4);
                dstHost = (a[0]&0xFF)+"."+(a[1]&0xFF)+"."+(a[2]&0xFF)+"."+(a[3]&0xFF);
            } else if (atyp == 3) {
                int len = in.read();
                dstHost = new String(readN(in, len), "UTF-8");
            } else if (atyp == 4) {
                byte[] a = readN(in, 16);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 16; i += 2) {
                    if (i > 0) sb.append(':');
                    sb.append(String.format("%x", ((a[i]&0xFF)<<8)|(a[i+1]&0xFF)));
                }
                dstHost = sb.toString();
            } else {
                out.write(new byte[]{5, 8, 0, 1, 0,0,0,0, 0,0});
                client.close(); return;
            }
            int dstPort = ((in.read()&0xFF)<<8) | (in.read()&0xFF);

            Socket upstream;
            try {
                upstream = new Socket();
                upstream.connect(new InetSocketAddress(dstHost, dstPort), 10_000);
                upstream.setSoTimeout(60_000);
            } catch (Exception ce) {
                out.write(new byte[]{5, 4, 0, 1, 0,0,0,0, 0,0});
                client.close(); return;
            }

            out.write(new byte[]{5, 0, 0, 1, 0,0,0,0,
                    (byte)(LOCAL_PORT>>8), (byte)(LOCAL_PORT&0xFF)});

            AtomicBoolean done = new AtomicBoolean(false);
            Thread t1 = new Thread(() -> { try { pipe(in,  upstream.getOutputStream(), done); } catch (IOException e) { done.set(true); } }, "s5a");
            Thread t2 = new Thread(() -> { try { pipe(upstream.getInputStream(), out,  done); } catch (IOException e) { done.set(true); } }, "s5b");
            t1.setDaemon(true); t2.setDaemon(true);
            t1.start(); t2.start();
            try { t1.join(); t2.join(); } catch (InterruptedException ignored) {}
            upstream.close();
        } catch (Exception e) {
            Log.d(TAG, "socks5 client: " + e.getMessage());
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private boolean doAuth(InputStream in, OutputStream out,
                           String user, String pass) throws IOException {
        int subver = in.read();
        if (subver != 1) { out.write(new byte[]{1, 1}); return false; }
        int ulen = in.read();
        String uname = new String(readN(in, ulen), "UTF-8");
        int plen = in.read();
        String passwd = new String(readN(in, plen), "UTF-8");
        if (user.equals(uname) && pass.equals(passwd)) {
            out.write(new byte[]{1, 0});
            return true;
        } else {
            out.write(new byte[]{1, 1});
            return false;
        }
    }

    private void pipe(InputStream in, OutputStream out, AtomicBoolean done) {
        byte[] buf = new byte[16384];
        try {
            int n;
            while (!done.get() && (n = in.read(buf)) != -1) out.write(buf, 0, n);
        } catch (Exception ignored) {
        } finally { done.set(true); }
    }

    private byte[] readN(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r < 0) throw new IOException("EOF");
            off += r;
        }
        return buf;
    }

    public void stopLocalProxy(ProxyStateCallback cb) {
        proxyOn.set(false);
        new Thread(() -> {
            if (!usingJavaFallback && AwgLib.loadBridge() && AwgLib.nativeIsLoaded()) {
                try { AwgLib.nativeStop(); } catch (Exception ignored) {}
            }
            if (javaServer != null) {
                try { javaServer.close(); } catch (Exception ignored) {}
                javaServer = null;
            }
            if (javaPool != null) {
                javaPool.shutdownNow();
                javaPool = null;
            }
            mainHandler.post(cb::onStopped);
        }, "awg-stop").start();
    }

    private String randomId(int len) {
        String ch = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(ch.charAt(rnd.nextInt(ch.length())));
        return sb.toString();
    }
}
