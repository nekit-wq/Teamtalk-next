#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#include <dlfcn.h>
#include <android/log.h>

#define TAG "NativePatch"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef const char* (*TT_GetVersion_t)(void);

JNIEXPORT jboolean JNICALL
Java_org_nekit_ttproplus_backend_NativePatch_setClientVersion(JNIEnv *env, jclass clazz, jstring versionStr) {
    if (!versionStr) return JNI_FALSE;
    const char *ver = (*env)->GetStringUTFChars(env, versionStr, NULL);
    if (!ver || strlen(ver) == 0) {
        if (ver) (*env)->ReleaseStringUTFChars(env, versionStr, ver);
        return JNI_FALSE;
    }

    void *handle = dlopen("libTeamTalk5-jni.so", RTLD_NOLOAD);
    if (!handle) {
        handle = dlopen("libTeamTalk5-jni.so", RTLD_NOW);
    }
    if (!handle) {
        handle = RTLD_DEFAULT;
    }

    TT_GetVersion_t getVersion = (TT_GetVersion_t)dlsym(handle, "TT_GetVersion");
    if (!getVersion) {
        LOGE("Could not resolve TT_GetVersion");
        (*env)->ReleaseStringUTFChars(env, versionStr, ver);
        return JNI_FALSE;
    }

    const char *currentVer = getVersion();
    if (!currentVer) {
        LOGE("TT_GetVersion returned NULL");
        (*env)->ReleaseStringUTFChars(env, versionStr, ver);
        return JNI_FALSE;
    }

    LOGI("Current TT_GetVersion: %s at %p", currentVer, currentVer);

    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) page_size = 4096;
    uintptr_t page_start = ((uintptr_t)currentVer) & ~(page_size - 1);

    if (mprotect((void *)page_start, page_size, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        LOGE("mprotect failed");
        (*env)->ReleaseStringUTFChars(env, versionStr, ver);
        return JNI_FALSE;
    }

    char buf[16] = {0};
    strncpy(buf, ver, 15);
    memcpy((void *)currentVer, buf, strlen(buf) + 1);

    LOGI("Successfully updated TT_GetVersion to: %s", currentVer);

    (*env)->ReleaseStringUTFChars(env, versionStr, ver);
    return JNI_TRUE;
}
