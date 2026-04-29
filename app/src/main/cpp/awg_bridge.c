#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <string.h>

#define TAG "AwgBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef int (*fn_StartAWGProxy)(const char* uapi,
                                const char* local_ip,
                                const char* dns_ip,
                                int         port);
typedef int (*fn_StopAWGProxy)(void);

static void*            g_handle    = NULL;
static fn_StartAWGProxy g_start     = NULL;
static fn_StopAWGProxy  g_stop      = NULL;

JNIEXPORT jboolean JNICALL
Java_com_tgproxy_app_AwgLib_nativeLoad(JNIEnv *env, jclass cls, jstring jpath) {
    if (g_handle) {
        LOGI("lib already loaded");
        return JNI_TRUE;
    }
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    LOGI("dlopen: %s", path);

    /* RTLD_NOW | RTLD_GLOBAL so the lib can resolve its own symbols */
    g_handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    (*env)->ReleaseStringUTFChars(env, jpath, path);

    if (!g_handle) {
        LOGE("dlopen failed: %s", dlerror());
        return JNI_FALSE;
    }
    g_start = (fn_StartAWGProxy) dlsym(g_handle, "StartAWGProxy");
    g_stop  = (fn_StopAWGProxy)  dlsym(g_handle, "StopAWGProxy");

    if (!g_start || !g_stop) {
        LOGE("dlsym failed: start=%p stop=%p err=%s", (void*)g_start, (void*)g_stop, dlerror());
        dlclose(g_handle);
        g_handle = NULL;
        return JNI_FALSE;
    }
    LOGI("loaded OK — StartAWGProxy=%p StopAWGProxy=%p", (void*)g_start, (void*)g_stop);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_tgproxy_app_AwgLib_nativeIsLoaded(JNIEnv *env, jclass cls) {
    return (g_handle && g_start && g_stop) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_tgproxy_app_AwgLib_nativeStart(JNIEnv *env, jclass cls,
                                         jstring juapi, jstring jlocalIp,
                                         jstring jdnsIp, jint port) {
    if (!g_start) { LOGE("nativeStart: lib not loaded"); return -1; }

    const char *uapi    = (*env)->GetStringUTFChars(env, juapi,    NULL);
    const char *localIp = (*env)->GetStringUTFChars(env, jlocalIp, NULL);
    const char *dnsIp   = (*env)->GetStringUTFChars(env, jdnsIp,   NULL);

    LOGI("StartAWGProxy(local=%s dns=%s port=%d)", localIp, dnsIp, (int)port);
    int result = g_start(uapi, localIp, dnsIp, (int)port);
    LOGI("StartAWGProxy returned %d", result);

    (*env)->ReleaseStringUTFChars(env, juapi,    uapi);
    (*env)->ReleaseStringUTFChars(env, jlocalIp, localIp);
    (*env)->ReleaseStringUTFChars(env, jdnsIp,   dnsIp);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_tgproxy_app_AwgLib_nativeStop(JNIEnv *env, jclass cls) {
    if (!g_stop) { LOGE("nativeStop: lib not loaded"); return -1; }
    int r = g_stop();
    LOGI("StopAWGProxy returned %d", r);
    return r;
}
