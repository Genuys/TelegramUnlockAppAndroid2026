package com.tgproxy.app;

public final class AwgLib {

    private static boolean bridgeLoaded = false;

    
    public static synchronized boolean loadBridge() {
        if (bridgeLoaded) return true;
        try {
            System.loadLibrary("awg_bridge");
            bridgeLoaded = true;
            return true;
        } catch (UnsatisfiedLinkError e) {
            android.util.Log.e("AwgLib", "loadLibrary(awg_bridge) failed: " + e.getMessage());
            return false;
        }
    }

    
    public static native boolean nativeLoad(String absolutePath);

    
    public static native boolean nativeIsLoaded();

    
    public static native int nativeStart(String uapi, String localIp, String dnsIp, int port);

    
    public static native int nativeStop();
}
