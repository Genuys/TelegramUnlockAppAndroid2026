package com.tgproxy.app;

import android.os.Handler;
import android.os.Looper;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class WarpGenerator {

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
                byte[] pubRaw = ((X25519PublicKeyParameters) kp.getPublic()).getEncoded();

                String privB64 = android.util.Base64.encodeToString(privRaw, android.util.Base64.NO_WRAP);
                String pubB64 = android.util.Base64.encodeToString(pubRaw, android.util.Base64.NO_WRAP);

                String installId = UUID.randomUUID().toString();
                String fcmToken = installId + ":APA91b" + UUID.randomUUID().toString().replace("-", "");
                
                String tos = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date());

                String body = "{\"install_id\":\"" + installId + "\","
                        + "\"fcm_token\":\"" + fcmToken + "\","
                        + "\"tos\":\"" + tos + "\","
                        + "\"key\":\"" + pubB64 + "\","
                        + "\"type\":\"Android\","
                        + "\"model\":\"Samsung Galaxy\","
                        + "\"locale\":\"ru_RU\"}";

                java.net.Socket raw = new java.net.Socket();
                raw.connect(new java.net.InetSocketAddress("162.159.192.1", 443), 10000);
                raw.setSoTimeout(15000);
                
                javax.net.ssl.SSLSocket ssl = (javax.net.ssl.SSLSocket) ((javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault())
                    .createSocket(raw, "api.cloudflareclient.com", 443, true);
                ssl.setUseClientMode(true);
                try {
                    javax.net.ssl.SSLParameters sslParams = ssl.getSSLParameters();
                    sslParams.setServerNames(java.util.Collections.singletonList(new javax.net.ssl.SNIHostName("api.cloudflareclient.com")));
                    ssl.setSSLParameters(sslParams);
                } catch (Exception ignored) {}
                
                ssl.startHandshake();

                OutputStream os = ssl.getOutputStream();
                String req = "POST /v0a2158/reg HTTP/1.1\r\n"
                        + "Host: api.cloudflareclient.com\r\n"
                        + "CF-Client-Version: a-6.11-2158\r\n"
                        + "Content-Type: application/json\r\n"
                        + "Content-Length: " + body.getBytes("UTF-8").length + "\r\n"
                        + "User-Agent: okhttp/3.12.1\r\n"
                        + "Connection: close\r\n\r\n"
                        + body;
                os.write(req.getBytes("UTF-8"));
                os.flush();

                BufferedReader br = new BufferedReader(new InputStreamReader(ssl.getInputStream()));
                String line = br.readLine();
                if (line == null) {
                    throw new Exception("Пустой ответ от сервера API");
                }
                
                int code = 500;
                if (line.startsWith("HTTP/")) {
                    String[] parts = line.split(" ");
                    if (parts.length > 1) code = Integer.parseInt(parts[1]);
                }
                
                while ((line = br.readLine()) != null && !line.isEmpty()) {}

                StringBuilder sb = new StringBuilder();
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                ssl.close();

                if (code >= 300) {
                    throw new Exception("Ошибка API: " + code + " " + sb.toString());
                }

                String json = sb.toString();

                String v4 = extractJson(json, "\"v4\":\"", "\"");
                String v6 = extractJson(json, "\"v6\":\"", "\"");
                String peerPub = extractJson(json, "\"public_key\":\"", "\"");
                String peerIp = extractJson(json, "\"endpoint\":\"", "\"");

                String dns = "1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001";
                if ("geohide".equals(dnsPref)) {
                    dns = "dns.geohide.ru, 185.172.128.53";
                } else if ("google".equals(dnsPref)) {
                    dns = "8.8.8.8, 8.8.4.4";
                } else if ("comss".equals(dnsPref)) {
                    dns = "93.115.24.205, 93.115.24.204";
                }

                String config = "[Interface]\n"
                        + "PrivateKey = " + privB64 + "\n"
                        + "Address = " + v4 + "/32, " + v6 + "/128\n"
                        + "DNS = " + dns + "\n"
                        + "MTU = 1200\n\n"
                        + "[Peer]\n"
                        + "PublicKey = " + peerPub + "\n"
                        + "AllowedIPs = 0.0.0.0/0, ::/0\n"
                        + "Endpoint = " + peerIp + "\n";

                handler.post(() -> callback.onSuccess(config));
            } catch (Exception e) {
                handler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Неизвестная ошибка"));
            }
        }).start();
    }

    private static String extractJson(String json, String startStr, String endStr) {
        int start = json.indexOf(startStr);
        if (start < 0) return "";
        start += startStr.length();
        int end = json.indexOf(endStr, start);
        if (end < 0) return "";
        return json.substring(start, end);
    }
}
