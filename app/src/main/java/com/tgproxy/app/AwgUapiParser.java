package com.tgproxy.app;

import android.util.Base64;

public class AwgUapiParser {

    public static class Result {
        public final String uapi;
        public final String localIp;
        public final String dnsIp;

        Result(String uapi, String localIp, String dnsIp) {
            this.uapi    = uapi;
            this.localIp = localIp;
            this.dnsIp   = dnsIp;
        }
    }

    public static Result parse(String confText) {
        StringBuilder uapi = new StringBuilder();
        String localIp = "10.0.0.2";
        String dnsIp   = "1.1.1.1";
        String section = null; // "interface" or "peer"

        for (String rawLine : confText.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith("[")) {
                section = line.toLowerCase().replaceAll("[\\[\\]]", "").trim();
                if ("peer".equals(section)) uapi.append("public_key=\n");
                continue;
            }

            if (!line.contains("=")) continue;
            int eq = line.indexOf('=');
            String key = line.substring(0, eq).trim().toLowerCase();
            String val = line.substring(eq + 1).trim();

            if ("interface".equals(section)) {
                switch (key) {
                    case "privatekey":
                        uapi.append("private_key=").append(b64ToHex(val)).append("\n"); break;
                    case "address":
                        String[] addrs = val.split(",");
                        for (String a : addrs) {
                            a = a.trim().split("/")[0];
                            if (a.contains(".") && !a.contains(":")) {
                                localIp = a; break;
                            }
                        }
                        break;
                    case "dns":
                        String firstDns = val.split(",")[0].trim();
                        if (firstDns.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                            dnsIp = firstDns;
                        } else {
                            dnsIp = "1.1.1.1";
                        }
                        break;
                    case "mtu":
                        uapi.append("mtu=").append(val).append("\n"); break;
                    case "listenport":
                        uapi.append("listen_port=").append(val).append("\n"); break;
                    case "jc": case "jmin": case "jmax":
                    case "s1": case "s2":
                    case "h1": case "h2": case "h3": case "h4":
                        uapi.append(key).append("=").append(val).append("\n"); break;
                }
            } else if ("peer".equals(section)) {
                switch (key) {
                    case "publickey":
                        String placeholder = "public_key=\n";
                        int pi = uapi.lastIndexOf(placeholder);
                        if (pi >= 0)
                            uapi.replace(pi, pi + placeholder.length(),
                                "public_key=" + b64ToHex(val) + "\n");
                        else
                            uapi.append("public_key=").append(b64ToHex(val)).append("\n");
                        break;
                    case "presharedkey":
                        uapi.append("preshared_key=").append(b64ToHex(val)).append("\n"); break;
                    case "endpoint":
                        uapi.append("endpoint=").append(val).append("\n"); break;
                    case "allowedips":
                        for (String ip : val.split(","))
                            uapi.append("allowed_ip=").append(ip.trim()).append("\n");
                        break;
                    case "persistentkeepalive":
                        uapi.append("persistent_keepalive_interval=").append(val).append("\n"); break;
                }
            }
        }

        return new Result(uapi.toString(), localIp, dnsIp);
    }

    private static String b64ToHex(String b64) {
        try {
            byte[] bytes = Base64.decode(b64.trim(), Base64.DEFAULT);
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
            return sb.toString();
        } catch (Exception e) {
            return b64;
        }
    }
}
