#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#include <android/log.h>

#define TAG "NativePatch"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

JNIEXPORT jboolean JNICALL
Java_org_nekit_ttproplus_backend_NativePatch_setClientVersion(JNIEnv *env, jclass clazz, jstring versionStr) {
    if (!versionStr) return JNI_FALSE;
    const char *ver = (*env)->GetStringUTFChars(env, versionStr, NULL);
    if (!ver || strlen(ver) == 0) {
        if (ver) (*env)->ReleaseStringUTFChars(env, versionStr, ver);
        return JNI_FALSE;
    }

    FILE *f = fopen("/proc/self/maps", "r");
    if (!f) {
        LOGE("Failed to open /proc/self/maps");
        (*env)->ReleaseStringUTFChars(env, versionStr, ver);
        return JNI_FALSE;
    }

    char line[512];
    long page_size = sysconf(_SC_PAGESIZE);
    char buf[16] = {0};
    strncpy(buf, ver, 15);

    int patched = 0;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, "libTeamTalk5-jni.so")) {
            uintptr_t start = 0, end = 0;
            char perms[16] = {0};
            if (sscanf(line, "%lx-%lx %s", &start, &end, perms) >= 2) {
                // Scan this mapped region for the original version or previous patch
                for (uintptr_t ptr = start; ptr + 8 <= end; ptr += 1) {
                    if (memcmp((const void *)ptr, "5.22.0.5198", 11) == 0 ||
                        memcmp((const void *)ptr, "5.26.0", 6) == 0 ||
                        memcmp((const void *)ptr, "5.27.0", 6) == 0 ||
                        memcmp((const void *)ptr, "5.28.0", 6) == 0 ||
                        memcmp((const void *)ptr, "5.15.0", 6) == 0 ||
                        memcmp((const void *)ptr, "5.33.0", 6) == 0) {
                        
                        uintptr_t page_start = ptr & ~(page_size - 1);
                        mprotect((void *)page_start, page_size * 2, PROT_READ | PROT_WRITE | PROT_EXEC);
                        
                        size_t len = strlen(buf);
                        memcpy((void *)ptr, buf, len + 1);
                        LOGI("Successfully patched client version at %p to %s", (void *)ptr, buf);
                        patched = 1;
                    }
                }
            }
        }
    }
    fclose(f);

    (*env)->ReleaseStringUTFChars(env, versionStr, ver);
    return patched ? JNI_TRUE : JNI_FALSE;
}
