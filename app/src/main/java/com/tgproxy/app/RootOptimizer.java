package com.tgproxy.app;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class RootOptimizer {

    public interface ResultCallback {
        void onResult(List<String> log);
    }

    public static boolean isRootAvailable() {
        try {
            Process p = Runtime.getRuntime().exec("su -c id");
            p.waitFor();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String out = br.readLine();
            return out != null && out.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Applies all network + CPU optimizations and returns a log of results.
     * Runs on the calling thread — use from a background thread.
     */
    public static List<String> applyAll(Context ctx) {
        List<String> log = new ArrayList<>();
        String pkg = ctx.getPackageName();

        applyCmd(log, "TCP буферы",
                "sysctl -w net.core.rmem_max=16777216 net.core.wmem_max=16777216 "
                        + "net.ipv4.tcp_rmem='4096 87380 16777216' "
                        + "net.ipv4.tcp_wmem='4096 65536 16777216'");

        applyCmd(log, "TCP BBR congestion control",
                "sysctl -w net.ipv4.tcp_congestion_control=bbr 2>/dev/null "
                        + "|| sysctl -w net.ipv4.tcp_congestion_control=cubic");

        applyCmd(log, "TCP Fast Open",
                "sysctl -w net.ipv4.tcp_fastopen=3");

        applyCmd(log, "Disable IPv6 privacy extensions (стабильность адреса)",
                "sysctl -w net.ipv6.conf.all.use_tempaddr=0 "
                        + "net.ipv6.conf.default.use_tempaddr=0");

        applyCmd(log, "IRQ balance (фиксируем сетевые прерывания на большое ядро)",
                "for irq in $(cat /proc/interrupts | grep -i 'eth\\|wlan\\|rmnet' | "
                        + "awk -F: '{print $1}' | tr -d ' '); do "
                        + "echo 4 > /proc/irq/$irq/smp_affinity 2>/dev/null; done");

        applyCmd(log, "CPU governor → performance для сетевых потоков",
                "for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do "
                        + "echo performance > $f 2>/dev/null; done");

        applyCmd(log, "Отключить Doze для приложения",
                "dumpsys deviceidle whitelist +" + pkg);

        applyCmd(log, "Приоритет процесса приложения",
                "pid=$(pidof " + pkg + " 2>/dev/null); "
                        + "[ -n \"$pid\" ] && renice -10 $pid 2>/dev/null");

        applyCmd(log, "WLAN power save → off",
                "iwconfig wlan0 power off 2>/dev/null || iw dev wlan0 set power_save off 2>/dev/null");

        applyCmd(log, "Увеличить лимит файловых дескрипторов",
                "ulimit -n 65536 2>/dev/null");

        return log;
    }

    public static List<String> resetAll(Context ctx) {
        List<String> log = new ArrayList<>();

        applyCmd(log, "TCP буферы → default",
                "sysctl -w net.core.rmem_max=212992 net.core.wmem_max=212992");

        applyCmd(log, "CPU governor → schedutil",
                "for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do "
                        + "echo schedutil > $f 2>/dev/null || echo ondemand > $f 2>/dev/null; done");

        applyCmd(log, "WLAN power save → on",
                "iw dev wlan0 set power_save on 2>/dev/null");

        return log;
    }

    public static List<String> getNetworkInfo() {
        List<String> info = new ArrayList<>();
        runRead(info, "TCP congestion",    "sysctl net.ipv4.tcp_congestion_control");
        runRead(info, "rmem_max",          "sysctl net.core.rmem_max");
        runRead(info, "wmem_max",          "sysctl net.core.wmem_max");
        runRead(info, "TCP Fast Open",     "sysctl net.ipv4.tcp_fastopen");
        runRead(info, "CPU governors",
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null");
        runRead(info, "BBR available",
                "ls /sys/module/tcp_bbr 2>/dev/null && echo 'yes' || echo 'no'");
        return info;
    }

    private static void applyCmd(List<String> log, String label, String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            int exit = p.waitFor();
            String err = readStream(p.getErrorStream());
            if (exit == 0) {
                log.add("✅ " + label);
            } else {
                log.add("⚠️ " + label + (err.isEmpty() ? "" : ": " + err.trim()));
            }
        } catch (Exception e) {
            log.add("❌ " + label + ": " + e.getMessage());
        }
    }

    private static void runRead(List<String> out, String label, String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            String result = readStream(p.getInputStream()).trim();
            p.waitFor();
            out.add(label + ": " + result);
        } catch (Exception e) {
            out.add(label + ": N/A");
        }
    }

    private static String readStream(java.io.InputStream is) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
}
