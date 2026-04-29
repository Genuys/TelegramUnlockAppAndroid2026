package com.tgproxy.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class AwgLibManager {

    private static final String TAG = "AwgLibManager";

    private static final String LIB_URL =
        "https://gitflic.ru/project/mandremanovich/soshkidlyaplaginov/blob/raw" +
        "?file=libawg_proxy.so&inline=false" +
        "&commit=2abb7c2bc355721c404a3b4a2ceb9dd691a54e3f";

    private static final String LIB_FILENAME = "libawg_proxy.so";
    private static final long   MIN_SIZE_BYTES = 100_000L;

    public interface LoadCallback {
        void onProgress(String msg);
        void onLoaded();
        void onError(String msg);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean loading = false;

    
    public void ensureLoaded(Context ctx, LoadCallback cb) {
        if (AwgLib.loadBridge() && AwgLib.nativeIsLoaded()) {
            mainHandler.post(cb::onLoaded);
            return;
        }
        if (loading) {
            mainHandler.post(() -> cb.onProgress("⏳ Уже идёт загрузка..."));
            return;
        }
        loading = true;
        new Thread(() -> {
            try {
                if (!AwgLib.loadBridge()) {
                    fail(cb, "❌ awg_bridge.so не загружен — пересоберите APK с NDK");
                    return;
                }

                File libFile = new File(ctx.getFilesDir(), LIB_FILENAME);

                if (!libFile.exists() || libFile.length() < MIN_SIZE_BYTES) {
                    mainHandler.post(() -> cb.onProgress("⏬ Скачиваем libawg_proxy.so..."));
                    downloadLib(libFile, cb);
                } else {
                    Log.i(TAG, "lib already on disk: " + libFile.length() + " bytes");
                }

                mainHandler.post(() -> cb.onProgress("🔗 Загружаем нативную библиотеку..."));
                boolean ok = AwgLib.nativeLoad(libFile.getAbsolutePath());
                loading = false;
                if (ok) {
                    Log.i(TAG, "nativeLoad OK");
                    mainHandler.post(cb::onLoaded);
                } else {
                    if (AwgLib.nativeIsLoaded()) {
                        mainHandler.post(cb::onLoaded);
                    } else {
                        libFile.delete();
                        fail(cb, "❌ dlopen не удался — неверная архитектура или файл повреждён");
                    }
                }
            } catch (Exception e) {
                loading = false;
                fail(cb, "❌ " + e.getMessage());
            }
        }, "awg-loader").start();
    }

    private void downloadLib(File dest, LoadCallback cb) throws Exception {
        File tmp = new File(dest.getParent(), LIB_FILENAME + ".tmp");
        try {
            URL url = new URL(LIB_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(60_000);
            conn.setRequestProperty("User-Agent", "TgProxy/1.5");
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code + " при скачивании SO");

            long total   = conn.getContentLengthLong();
            long written = 0;

            try (InputStream is  = conn.getInputStream();
                 FileOutputStream os = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) != -1) {
                    os.write(buf, 0, n);
                    written += n;
                    if (total > 0) {
                        final int pct = (int)(written * 100 / total);
                        mainHandler.post(() -> cb.onProgress("⏬ Скачиваем " + pct + "%..."));
                    }
                }
            }
            conn.disconnect();

            if (written < MIN_SIZE_BYTES)
                throw new Exception("Файл слишком маленький (" + written + " байт)");

            if (dest.exists()) dest.delete();
            if (!tmp.renameTo(dest))
                throw new Exception("Не удалось переименовать .tmp → .so");

            Log.i(TAG, "downloaded: " + dest.length() + " bytes");

        } catch (Exception e) {
            if (tmp.exists()) tmp.delete();
            throw e;
        }
    }

    private void fail(LoadCallback cb, String msg) {
        loading = false;
        Log.e(TAG, msg);
        mainHandler.post(() -> cb.onError(msg));
    }
}
