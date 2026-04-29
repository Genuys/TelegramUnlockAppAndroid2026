package com.tgproxy.app;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public final class TgConstants {

    public static final int    BUF       = 131072;
    public static final int    POOL_SIZE = 8;
    public static final double POOL_AGE  = 120.0;
    public static final double COOLDOWN  = 60.0;

    public static final String PROXY_1_SERVER = "quackton.life";
    public static final int    PROXY_1_PORT   = 443;
    public static final String PROXY_1_SECRET = "ee65fc7553a1f5ca8b50b71c015b38722479616e6465782e7275";

    public static final String PROXY_2_SERVER = "peyk.acharbashi.info";
    public static final int    PROXY_2_PORT   = 4515;
    public static final String PROXY_2_SECRET = "eee9a4f23b1d768c04a8d7f39120ca5b6e626973636f7474692e79656b74616e65742e636f6d";

    public static final String PROXY_3_SERVER = "test.alotaxi.info";
    public static final int    PROXY_3_PORT   = 4515;
    public static final String PROXY_3_SECRET = "eee9a4f23b1d768c04a8d7f39120ca5b6e626973636f7474692e79656b74616e65742e636f6d";

    public static final String PROXY_4_SERVER = "jtproxy.life";
    public static final int    PROXY_4_PORT   = 443;
    public static final String PROXY_4_SECRET = "eef0050d30441bab41f60acae779df0c40766473696e612e7275";

    public static final String PROXY_5_SERVER = "alo.acharbashi.info";
    public static final int    PROXY_5_PORT   = 4515;
    public static final String PROXY_5_SECRET = "eee9a4f23b1d768c04a8d7f39120ca5b6e626973636f7474692e79656b74616e65742e636f6d";

    public static final String PROXY_6_SERVER = "proxy.nuv.cx";
    public static final int    PROXY_6_PORT   = 443;
    public static final String PROXY_6_SECRET = "ee470cb2b8b29aeadfbdf8a2f7bee5ca3b62726f777365722e79616e6465782e636f6d";

    public static final String PROXY_WIFI_SERVER = PROXY_6_SERVER;
    public static final int    PROXY_WIFI_PORT   = PROXY_6_PORT;
    public static final String PROXY_WIFI_SECRET = PROXY_6_SECRET;
    public static final String PROXY_MOB1_SERVER = PROXY_4_SERVER;
    public static final int    PROXY_MOB1_PORT   = PROXY_4_PORT;
    public static final String PROXY_MOB1_SECRET = PROXY_4_SECRET;
    public static final String PROXY_MOB2_SERVER = PROXY_1_SERVER;
    public static final int    PROXY_MOB2_PORT   = PROXY_1_PORT;
    public static final String PROXY_MOB2_SECRET = PROXY_1_SECRET;

    public static final long[][] TG_RANGES = {
            {ipToLong("185.76.151.0"),  ipToLong("185.76.151.255")},
            {ipToLong("149.154.160.0"), ipToLong("149.154.175.255")},
            {ipToLong("91.105.192.0"),  ipToLong("91.105.193.255")},
            {ipToLong("91.108.0.0"),    ipToLong("91.108.255.255")},
            {ipToLong("95.161.76.0"),   ipToLong("95.161.76.255")},
    };

    public static final Map<String, int[]> IP_TO_DC = new HashMap<>();
    static {
        IP_TO_DC.put("149.154.175.50",  new int[]{1, 0});
        IP_TO_DC.put("149.154.175.51",  new int[]{1, 0});
        IP_TO_DC.put("149.154.175.53",  new int[]{1, 0});
        IP_TO_DC.put("149.154.175.54",  new int[]{1, 0});
        IP_TO_DC.put("149.154.175.52",  new int[]{1, 1});
        IP_TO_DC.put("149.154.167.41",  new int[]{2, 0});
        IP_TO_DC.put("149.154.167.50",  new int[]{2, 0});
        IP_TO_DC.put("149.154.167.51",  new int[]{2, 0});
        IP_TO_DC.put("149.154.167.220", new int[]{2, 0});
        IP_TO_DC.put("95.161.76.100",   new int[]{2, 0});
        IP_TO_DC.put("149.154.167.151", new int[]{2, 1});
        IP_TO_DC.put("149.154.167.222", new int[]{2, 1});
        IP_TO_DC.put("149.154.167.223", new int[]{2, 1});
        IP_TO_DC.put("149.154.162.123", new int[]{2, 1});
        IP_TO_DC.put("149.154.175.100", new int[]{3, 0});
        IP_TO_DC.put("149.154.175.101", new int[]{3, 0});
        IP_TO_DC.put("149.154.175.102", new int[]{3, 1});
        IP_TO_DC.put("149.154.167.91",  new int[]{4, 0});
        IP_TO_DC.put("149.154.167.92",  new int[]{4, 0});
        IP_TO_DC.put("149.154.164.250", new int[]{4, 1});
        IP_TO_DC.put("149.154.166.120", new int[]{4, 1});
        IP_TO_DC.put("149.154.166.121", new int[]{4, 1});
        IP_TO_DC.put("149.154.167.118", new int[]{4, 1});
        IP_TO_DC.put("149.154.165.111", new int[]{4, 1});
        IP_TO_DC.put("91.108.56.100",   new int[]{5, 0});
        IP_TO_DC.put("91.108.56.101",   new int[]{5, 0});
        IP_TO_DC.put("91.108.56.116",   new int[]{5, 0});
        IP_TO_DC.put("91.108.56.126",   new int[]{5, 0});
        IP_TO_DC.put("149.154.171.5",   new int[]{5, 0});
        IP_TO_DC.put("91.108.56.102",   new int[]{5, 1});
        IP_TO_DC.put("91.108.56.128",   new int[]{5, 1});
        IP_TO_DC.put("91.108.56.151",   new int[]{5, 1});
    }

    public static final Map<Integer, String> DC_IPS = new HashMap<>();
    static {
        DC_IPS.put(1, "149.154.175.53");
        DC_IPS.put(2, "149.154.167.220");
        DC_IPS.put(3, "149.154.175.100");
        DC_IPS.put(4, "149.154.167.220");
        DC_IPS.put(5, "91.108.56.116");
    }

    public static long ipToLong(String ip) {
        try {
            byte[] b = InetAddress.getByName(ip).getAddress();
            return ((long)(b[0]&0xFF)<<24)|((long)(b[1]&0xFF)<<16)
                  |((long)(b[2]&0xFF)<<8) |(long)(b[3]&0xFF);
        } catch (Exception e) { return 0; }
    }

    public static boolean isTelegramIp(String ip) {
        try {
            long v = ipToLong(ip);
            for (long[] r : TG_RANGES) if (v >= r[0] && v <= r[1]) return true;
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean isHttp(byte[] d) {
        if (d.length < 4) return false;
        if (d[0]=='G'&&d[1]=='E'&&d[2]=='T'&&d[3]==' ') return true;
        if (d.length>=5){
            if (d[0]=='P'&&d[1]=='O'&&d[2]=='S'&&d[3]=='T'&&d[4]==' ') return true;
            if (d[0]=='H'&&d[1]=='E'&&d[2]=='A'&&d[3]=='D'&&d[4]==' ') return true;
        }
        if (d.length>=8&&d[0]=='O'&&d[1]=='P'&&d[2]=='T'&&d[3]=='I'
                       &&d[4]=='O'&&d[5]=='N'&&d[6]=='S'&&d[7]==' ') return true;
        return false;
    }

    public static String humanBytes(long n) {
        double v = n;
        String[] u = {"B","KB","MB","GB","TB"};
        for (int i = 0; i < u.length-1; i++){
            if (Math.abs(v) < 1024) return String.format("%.1f%s", v, u[i]);
            v /= 1024;
        }
        return String.format("%.1f%s", v, u[u.length-1]);
    }

    public static String[] wsDomains(int dc, boolean isMedia) {
        String primary  = "kws" + dc + (isMedia ? "-1" : "") + ".web.telegram.org";
        String fallback = "kws" + dc + (isMedia ? ""  : "-1") + ".web.telegram.org";
        return new String[]{primary, fallback};
    }

    public static String buildProxyLink(String server, int port, String secret) {
        return "tg://proxy?server=" + server + "&port=" + port + "&secret=" + secret;
    }

    private TgConstants() {}
}
