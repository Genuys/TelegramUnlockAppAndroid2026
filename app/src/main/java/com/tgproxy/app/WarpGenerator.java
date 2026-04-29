package com.tgproxy.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

public class WarpGenerator {

    private static final String TAG = "WarpGenerator";

    private static final String[] API_ENDPOINTS = {
        "https://api.cloudflareclient.com/v0a2158/reg",
        "https://api.cloudflareclient.com/v0a4005/reg",
        "https://api.cloudflareclient.com/v0a977/reg",
    };

    public interface Callback {
        void onSuccess(String config);
        void onError(String error);
    }

    public static void generate(String dnsPref, Callback callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                X25519KeyPairGenerator kpg = new X25519KeyPairGenerator();
                kpg.init(new X25519KeyGenerationParameters(new SecureRandom()));
                AsymmetricCipherKeyPair kp = kpg.generateKeyPair();

                byte[] privRaw = ((X25519PrivateKeyParameters) kp.getPrivate()).getEncoded();
                byte[] pubRaw  = ((X25519PublicKeyParameters)  kp.getPublic()).getEncoded();

                String privB64 = Base64.encodeToString(privRaw, Base64.NO_WRAP);
                String pubB64  = Base64.encodeToString(pubRaw,  Base64.NO_WRAP);

                String installId = UUID.randomUUID().toString();
                String fcmToken  = installId + ":APA91b"
                        + UUID.randomUUID().toString().replace("-", "");
                String tos = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date());

                String body = "{"
                        + "\"install_id\":\"" + installId + "\","
                        + "\"fcm_token\":\""  + fcmToken  + "\","
                        + "\"tos\":\""         + tos       + "\","
                        + "\"key\":\""         + pubB64    + "\","
                        + "\"type\":\"Android\","
                        + "\"model\":\"Samsung Galaxy\","
                        + "\"locale\":\"ru_RU\""
                        + "}";

                String json = null;
                Exception lastEx = null;

                for (String endpoint : API_ENDPOINTS) {
                    try {
                        json = postJson(endpoint, body);
                        if (json != null && json.contains("public_key")) break;
                        json = null;
                    } catch (Exception ex) {
                        lastEx = ex;
                        Log.w(TAG, "Endpoint failed: " + endpoint + " — " + ex.getMessage());
                    }
                }

                if (json == null) {
                    String errMsg = lastEx != null ? lastEx.getMessage() : "Все эндпоинты недоступны";
                    handler.post(() -> callback.onError(errMsg));
                    return;
                }

                String v4      = extractJson(json, "\"v4\":\"", "\"");
                String v6      = extractJson(json, "\"v6\":\"", "\"");
                String peerPub = extractJson(json, "\"public_key\":\"", "\"");
                String peerIp  = extractJson(json, "\"endpoint\":\"", "\"");

                if (v4.isEmpty())      v4      = "172.16.0.2";
                if (v6.isEmpty())      v6      = "2606:4700:110:8f80::1";
                if (peerPub.isEmpty()) peerPub = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=";
                if (peerIp.isEmpty())  peerIp  = "162.159.192.1:2408";

                String dns;
                switch (dnsPref == null ? "" : dnsPref) {
                    case "geohide": dns = "dns.geohide.ru, 185.172.128.53"; break;
                    case "google":  dns = "8.8.8.8, 8.8.4.4";               break;
                    case "comss":   dns = "93.115.24.205, 93.115.24.204";   break;
                    default:        dns = "1.1.1.1, 1.0.0.1, "
                                       + "2606:4700:4700::1111, "
                                       + "2606:4700:4700::1001";            break;
                }

                String config = "[Interface]\n"
                        + "PrivateKey = " + privB64 + "\n"
                        + "Address = "    + v4 + "/32, " + v6 + "/128\n"
                        + "DNS = "        + dns + "\n"
                        + "MTU = 1280\n\n"
                        + "[Peer]\n"
                        + "PublicKey = "  + peerPub + "\n"
                        + "AllowedIPs = 0.0.0.0/0, ::/0\n"
                        + "Endpoint = "   + peerIp  + "\n";

                handler.post(() -> callback.onSuccess(config));

            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Неизвестная ошибка";
                handler.post(() -> callback.onError(msg));
            }
        }, "warp-gen").start();
    }

    private static String postJson(String endpoint, String body) throws Exception {
        URL url = new URL(endpoint);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(12_000);
            conn.setReadTimeout(18_000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("CF-Client-Version", "a-6.11-2158");
            conn.setRequestProperty("User-Agent",        "okhttp/3.12.1");
            conn.setRequestProperty("Accept-Encoding",   "identity");

            byte[] bodyBytes = body.getBytes("UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "POST " + endpoint + " → HTTP " + code);

            InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream() : conn.getErrorStream();

            if (is == null) throw new Exception("HTTP " + code + ", пустое тело");

            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            if (code >= 300) throw new Exception("HTTP " + code + ": " + sb);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static String extractJson(String json, String startStr, String endStr) {
        int s = json.indexOf(startStr);
        if (s < 0) return "";
        s += startStr.length();
        int e = json.indexOf(endStr, s);
        if (e < 0) return "";
        return json.substring(s, e);
    }
}
