package com.tgproxy.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenStateReceiver extends BroadcastReceiver {

    public interface Callback {
        void onScreenOn();
        void onScreenOff();
    }

    private Callback callback;

    public void setCallback(Callback cb) {
        this.callback = cb;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        switch (intent.getAction()) {
            case Intent.ACTION_SCREEN_ON:
                if (callback != null) callback.onScreenOn();
                break;
            case Intent.ACTION_SCREEN_OFF:
                if (callback != null) callback.onScreenOff();
                break;
        }
    }
}
