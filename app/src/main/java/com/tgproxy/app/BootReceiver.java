package com.tgproxy.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.preference.PreferenceManager;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean autostart = prefs.getBoolean("autostart_boot", true);
            if (autostart) {
                Intent si = new Intent(context, ProxyService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(si);
                } else {
                    context.startService(si);
                }
            }
        }
    }
}
