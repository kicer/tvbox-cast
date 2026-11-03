/*
 * evgrab.c —— 独占 /dev/input/event0 的按键抓取器
 *
 * 背景：盒子（Hi3798MV310 / Android 4.4.2）的系统输入策略会在事件分发给应用
 * 之前吃掉「直播 / 回看 / 主页」，并拉起 IPTV。那是 system_server 里厂商定制的
 * 行为，应用层 return true 拦不住（详见 tvbox-cast/KEY_INTERCEPT.md）。
 *
 * 唯一在应用层可行的做法：在驱动层把设备独占（EVIOCGRAB）。
 * 抓住之后 system_server / remote_control / tvspeech 全部读不到事件，
 * 三个键就和普通按键一样，只到我们这里。
 *
 * 安全：进程退出时内核自动释放 grab，不会把遥控器永久锁死。
 */
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/select.h>
#include <sys/time.h>
#include <linux/input.h>
#include <android/log.h>

#define TAG "KICERGRAB"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static JavaVM *g_vm = NULL;
static jclass g_cls = NULL;
static jmethodID g_on_key = NULL;

static int g_fd = -1;
static volatile int g_run = 0;
static volatile int g_grabbed = 0;
static pthread_t g_thread;
static int g_thread_started = 0;

static void *reader_thread(void *arg) {
    (void) arg;

    JNIEnv *env = NULL;
    if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != JNI_OK) {
        LOGI("AttachCurrentThread 失败");
        return NULL;
    }

    struct input_event ev;
    memset(&ev, 0, sizeof(ev));

    while (g_run) {
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(g_fd, &rfds);
        struct timeval tv;
        tv.tv_sec = 0;
        tv.tv_usec = 200000; /* 200ms，用来定期检查 g_run */

        int r = select(g_fd + 1, &rfds, NULL, NULL, &tv);
        if (r < 0) {
            if (errno == EINTR) {
                continue;
            }
            LOGI("select 出错 errno=%d", errno);
            break;
        }
        if (r == 0) {
            continue; /* 超时，回去看 g_run */
        }

        /* 把这次可读的事件全部取干净（非阻塞，取空就回 select） */
        while (g_run) {
            ssize_t n = read(g_fd, &ev, sizeof(ev));
            if (n == (ssize_t) sizeof(ev)) {
                if (ev.type == EV_KEY) {
                    (*env)->CallStaticVoidMethod(env, g_cls, g_on_key,
                                                 (jint) ev.code, (jint) ev.value);
                    if ((*env)->ExceptionCheck(env)) {
                        (*env)->ExceptionClear(env);
                    }
                }
            } else if (n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
                break; /* 读空了 */
            } else if (n < 0 && errno == EINTR) {
                continue;
            } else {
                break;
            }
        }
    }

    LOGI("reader 线程退出");
    (*g_vm)->DetachCurrentThread(g_vm);
    return NULL;
}

static jstring err_str(JNIEnv *env, const char *msg) {
    char buf[256];
    snprintf(buf, sizeof(buf), "%s (errno=%d: %s)", msg, errno, strerror(errno));
    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jboolean JNICALL
Java_com_kicer_tvboxcast_NativeGrab_isGrabbedNative(JNIEnv *env, jclass cls) {
    (void) env;
    (void) cls;
    return g_grabbed ? JNI_TRUE : JNI_FALSE;
}

/*
 * 返回空串表示成功；非空串是错误信息（会显示在屏幕上）。
 */
JNIEXPORT jstring JNICALL
Java_com_kicer_tvboxcast_NativeGrab_startNative(JNIEnv *env, jclass cls, jstring jpath) {
    (void) cls;
    if (g_run) {
        return (*env)->NewStringUTF(env, "已经在抓");
    }

    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    /* O_NONBLOCK：否则 stop() 会阻塞在下一次 read() 上，把 UI 线程卡死 */
    int fd = open(path, O_RDONLY | O_NONBLOCK);
    if (fd < 0) {
        jstring e = err_str(env, "open 失败");
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return e;
    }

    if (ioctl(fd, EVIOCGRAB, 1) != 0) {
        jstring e = err_str(env, "EVIOCGRAB 失败");
        close(fd);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return e;
    }

    g_fd = fd;
    g_grabbed = 1;

    /* 保存 JavaVM / 类 / 回调方法 */
    if (g_vm == NULL) {
        (*env)->GetJavaVM(env, &g_vm);
    }
    if (g_cls == NULL) {
        jclass local = (*env)->FindClass(env, "com/kicer/tvboxcast/NativeGrab");
        if (local == NULL) {
            ioctl(fd, EVIOCGRAB, 0);
            close(fd);
            g_fd = -1;
            g_grabbed = 0;
            (*env)->ReleaseStringUTFChars(env, jpath, path);
            return (*env)->NewStringUTF(env, "找不到 NativeGrab 类");
        }
        g_cls = (jclass) (*env)->NewGlobalRef(env, local);
        (*env)->DeleteLocalRef(env, local);
    }
    if (g_on_key == NULL) {
        g_on_key = (*env)->GetStaticMethodID(env, g_cls, "onKey", "(II)V");
        if (g_on_key == NULL) {
            ioctl(fd, EVIOCGRAB, 0);
            close(fd);
            g_fd = -1;
            g_grabbed = 0;
            (*env)->ReleaseStringUTFChars(env, jpath, path);
            return (*env)->NewStringUTF(env, "找不到 onKey 回调");
        }
    }

    (*env)->ReleaseStringUTFChars(env, jpath, path);

    g_run = 1;
    if (pthread_create(&g_thread, NULL, reader_thread, NULL) != 0) {
        g_run = 0;
        ioctl(g_fd, EVIOCGRAB, 0);
        close(g_fd);
        g_fd = -1;
        g_grabbed = 0;
        return err_str(env, "pthread_create 失败");
    }
    g_thread_started = 1;

    LOGI("已独占 %s (fd=%d)", "/dev/input/event0", g_fd);
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT void JNICALL
Java_com_kicer_tvboxcast_NativeGrab_stopNative(JNIEnv *env, jclass cls) {
    (void) env;
    (void) cls;

    if (!g_run && !g_grabbed) {
        return;
    }

    g_run = 0;

    if (g_thread_started) {
        pthread_join(g_thread, NULL);
        g_thread_started = 0;
    }

    if (g_fd >= 0) {
        if (g_grabbed) {
            ioctl(g_fd, EVIOCGRAB, 0);
            LOGI("已释放设备独占");
        }
        close(g_fd);
        g_fd = -1;
    }
    g_grabbed = 0;
}

/* 设备名，便于日志确认抓对了设备 */
JNIEXPORT jstring JNICALL
Java_com_kicer_tvboxcast_NativeGrab_deviceNameNative(JNIEnv *env, jclass cls, jstring jpath) {
    (void) cls;
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    char name[128];
    memset(name, 0, sizeof(name));

    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return err_str(env, "open 失败");
    }
    if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) < 0) {
        close(fd);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return err_str(env, "EVIOCGNAME 失败");
    }
    close(fd);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return (*env)->NewStringUTF(env, name);
}
