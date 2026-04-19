#include "path_resolver.h"

#include <jni.h>
#include <android/log.h>
#include <dobby.h>
#include <xdl.h>
#include <string>
#include <cerrno>
#include <cstdarg>
#include <cstdio>
#include <dlfcn.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#define LOG_TAG "DuneBox-IO"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace dunebox;

// ============================================================
// Original function pointers (saved by Dobby before hooking)
// ============================================================
typedef int (*open_t)(const char*, int, ...);
typedef int (*openat_t)(int, const char*, int, ...);
typedef int (*stat_t)(const char*, struct stat*);
typedef int (*lstat_t)(const char*, struct stat*);
typedef int (*access_t)(const char*, int);
typedef int (*mkdir_t)(const char*, mode_t);
typedef int (*unlink_t)(const char*);
typedef int (*rename_t)(const char*, const char*);
typedef FILE* (*fopen_t)(const char*, const char*);

static open_t orig_open = nullptr;
static openat_t orig_openat = nullptr;
static stat_t orig_stat = nullptr;
static lstat_t orig_lstat = nullptr;
static access_t orig_access = nullptr;
static mkdir_t orig_mkdir = nullptr;
static unlink_t orig_unlink = nullptr;
static rename_t orig_rename = nullptr;
static fopen_t orig_fopen = nullptr;

// ============================================================
// Hooked functions
// ============================================================
static int fake_open(const char* path, int flags, ...) {
    auto result = PathResolver::getInstance().resolve(path);

    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
    }

    switch (result.action) {
        case IOAction::DENY:
            errno = EACCES;
            return -1;
        case IOAction::REDIRECT:
            return orig_open(result.redirectedPath.c_str(), flags, mode);
        default:
            return orig_open(path, flags, mode);
    }
}

static int fake_openat(int dirfd, const char* path, int flags, ...) {
    auto result = PathResolver::getInstance().resolve(path);

    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
    }

    switch (result.action) {
        case IOAction::DENY:
            errno = EACCES;
            return -1;
        case IOAction::REDIRECT:
            return orig_openat(dirfd, result.redirectedPath.c_str(), flags, mode);
        default:
            return orig_openat(dirfd, path, flags, mode);
    }
}

static int fake_stat(const char* path, struct stat* buf) {
    auto result = PathResolver::getInstance().resolve(path);
    switch (result.action) {
        case IOAction::DENY:
            errno = EACCES;
            return -1;
        case IOAction::REDIRECT:
            return orig_stat(result.redirectedPath.c_str(), buf);
        default:
            return orig_stat(path, buf);
    }
}

static int fake_lstat(const char* path, struct stat* buf) {
    auto result = PathResolver::getInstance().resolve(path);
    switch (result.action) {
        case IOAction::DENY:
            errno = EACCES;
            return -1;
        case IOAction::REDIRECT:
            return orig_lstat(result.redirectedPath.c_str(), buf);
        default:
            return orig_lstat(path, buf);
    }
}

static int fake_access(const char* path, int mode) {
    auto result = PathResolver::getInstance().resolve(path);
    switch (result.action) {
        case IOAction::DENY:
            errno = EACCES;
            return -1;
        case IOAction::REDIRECT:
            return orig_access(result.redirectedPath.c_str(), mode);
        default:
            return orig_access(path, mode);
    }
}

static int fake_mkdir(const char* path, mode_t mode) {
    auto result = PathResolver::getInstance().resolve(path);
    switch (result.action) {
        case IOAction::DENY:
            errno = EACCES;
            return -1;
        case IOAction::REDIRECT:
            return orig_mkdir(result.redirectedPath.c_str(), mode);
        default:
            return orig_mkdir(path, mode);
    }
}

static int fake_unlink(const char* path) {
    auto result = PathResolver::getInstance().resolve(path);
    switch (result.action) {
        case IOAction::DENY:
            errno = EACCES;
            return -1;
        case IOAction::REDIRECT:
            return orig_unlink(result.redirectedPath.c_str());
        default:
            return orig_unlink(path);
    }
}

static int fake_rename(const char* oldpath, const char* newpath) {
    auto oldResult = PathResolver::getInstance().resolve(oldpath);
    auto newResult = PathResolver::getInstance().resolve(newpath);

    const char* resolvedOld = (oldResult.action == IOAction::REDIRECT)
                              ? oldResult.redirectedPath.c_str() : oldpath;
    const char* resolvedNew = (newResult.action == IOAction::REDIRECT)
                              ? newResult.redirectedPath.c_str() : newpath;

    if (oldResult.action == IOAction::DENY || newResult.action == IOAction::DENY) {
        errno = EACCES;
        return -1;
    }

    return orig_rename(resolvedOld, resolvedNew);
}

static FILE* fake_fopen(const char* path, const char* mode) {
    auto result = PathResolver::getInstance().resolve(path);
    switch (result.action) {
        case IOAction::DENY:
            errno = EACCES;
            return nullptr;
        case IOAction::REDIRECT:
            return orig_fopen(result.redirectedPath.c_str(), mode);
        default:
            return orig_fopen(path, mode);
    }
}

// ============================================================
// Resolve libc symbols via xDL (bypasses NDK fortify wrappers)
// ============================================================
static void* resolveLibcSymbol(const char* symbol) {
    // Use xDL to find the real symbol in libc.so
    void* handle = xdl_open("libc.so", XDL_DEFAULT);
    if (!handle) {
        LOGE("Failed to open libc.so via xdl");
        // Fallback to dlsym
        return dlsym(RTLD_DEFAULT, symbol);
    }
    void* addr = xdl_dsym(handle, symbol, nullptr);
    if (!addr) {
        // Try xdl_sym as fallback
        addr = xdl_sym(handle, symbol, nullptr);
    }
    xdl_close(handle);

    if (!addr) {
        LOGE("Failed to resolve symbol: %s", symbol);
    }
    return addr;
}

// ============================================================
// Hook installation
// ============================================================
static bool installHooks() {
    LOGD("Installing IO hooks via Dobby...");

    int success = 0;
    int total = 0;

    #define HOOK_SYM(sym_name, fake_func, orig_ptr) do { \
        total++; \
        void* sym_addr = resolveLibcSymbol(sym_name); \
        if (sym_addr) { \
            if (DobbyHook(sym_addr, (void*)(fake_func), (void**)&(orig_ptr)) == 0) { \
                success++; \
                LOGD("Hooked %s successfully", sym_name); \
            } else { \
                LOGE("DobbyHook failed for %s", sym_name); \
            } \
        } else { \
            LOGE("Symbol not found: %s", sym_name); \
        } \
    } while(0)

    HOOK_SYM("open",   fake_open,   orig_open);
    HOOK_SYM("openat", fake_openat, orig_openat);
    HOOK_SYM("stat",   fake_stat,   orig_stat);
    HOOK_SYM("lstat",  fake_lstat,  orig_lstat);
    HOOK_SYM("access", fake_access, orig_access);
    HOOK_SYM("mkdir",  fake_mkdir,  orig_mkdir);
    HOOK_SYM("unlink", fake_unlink, orig_unlink);
    HOOK_SYM("rename", fake_rename, orig_rename);
    HOOK_SYM("fopen",  fake_fopen,  orig_fopen);

    #undef HOOK_SYM

    LOGD("IO hooks installed: %d/%d succeeded", success, total);
    return success > 0;
}

// ============================================================
// JNI Functions
// ============================================================
extern "C" {

JNIEXPORT void JNICALL
Java_app_pwhs_dunebox_native_1engine_NativeEngine_nativeInit(JNIEnv* env, jobject /* this */) {
    LOGD("DuneBox Native Engine initializing...");
    LOGD("DuneBox Native Engine initialized");
}

JNIEXPORT void JNICALL
Java_app_pwhs_dunebox_native_1engine_NativeEngine_nativeAddRedirectRule(
        JNIEnv* env, jobject /* this */, jstring fromPath, jstring toPath) {
    const char* from = env->GetStringUTFChars(fromPath, nullptr);
    const char* to = env->GetStringUTFChars(toPath, nullptr);

    PathResolver::getInstance().addRedirectRule(from, to);

    env->ReleaseStringUTFChars(fromPath, from);
    env->ReleaseStringUTFChars(toPath, to);
}

JNIEXPORT void JNICALL
Java_app_pwhs_dunebox_native_1engine_NativeEngine_nativeAddDenyRule(
        JNIEnv* env, jobject /* this */, jstring path) {
    const char* pathStr = env->GetStringUTFChars(path, nullptr);

    PathResolver::getInstance().addDenyRule(pathStr);

    env->ReleaseStringUTFChars(path, pathStr);
}

JNIEXPORT jboolean JNICALL
Java_app_pwhs_dunebox_native_1engine_NativeEngine_nativeStartRedirect(
        JNIEnv* env, jobject /* this */) {
    if (PathResolver::getInstance().isActive()) {
        LOGD("IO redirect already active");
        return JNI_TRUE;
    }

    bool result = installHooks();
    if (result) {
        PathResolver::getInstance().setActive(true);
        LOGD("IO redirect started");
    } else {
        LOGE("Failed to start IO redirect");
    }
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_app_pwhs_dunebox_native_1engine_NativeEngine_nativeStopRedirect(
        JNIEnv* env, jobject /* this */) {
    PathResolver::getInstance().clearRules();
    LOGD("IO redirect stopped (rules cleared, hooks remain in place)");
}

JNIEXPORT void JNICALL
Java_app_pwhs_dunebox_native_1engine_NativeEngine_nativeClearRules(
        JNIEnv* env, jobject /* this */) {
    PathResolver::getInstance().clearRules();
}

} // extern "C"
