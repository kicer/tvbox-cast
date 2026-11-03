package com.kicer.tvboxcast;

/**
 * 独占 /dev/input/event0 的 JNI 封装（见 cpp/evgrab.c）。
 *
 * 用途：绕开 system_server 里厂商定制的按键策略。
 * 盒子系统的优先级是「直播 / 回看 / 主页」三个键在 framework 层就被消费，
 * 应用层 return true 无效；只有把设备在驱动层独占才能拿到。
 *
 * 安全性：进程退出时内核自动释放独占，不会把遥控器锁死。
 */
public final class NativeGrab {

    public static final String EVENT0 = "/dev/input/event0";

    /** code = Linux 输入码（ev.code），value = 1 按下 / 0 抬起 / 2 长按重复 */
    public interface Listener {
        void onGrabKey(int code, int value);
    }

    private static boolean sLoaded = false;
    private static volatile Listener sListener;

    static {
        try {
            System.loadLibrary("evgrab");
            sLoaded = true;
        } catch (Throwable t) {
            sLoaded = false;
        }
    }

    private NativeGrab() {
    }

    /** so 是否加载成功（加载失败时下面的 native 方法会 UnsatisfiedLinkError） */
    public static boolean available() {
        return sLoaded;
    }

    public static void setListener(Listener l) {
        sListener = l;
    }

    /** 返回空串 = 成功，否则是错误信息 */
    public static String start(String path) {
        if (!sLoaded) {
            return "libevgrab.so 未加载";
        }
        try {
            return startNative(path);
        } catch (Throwable t) {
            return "start 异常: " + t;
        }
    }

    public static void stop() {
        if (!sLoaded) {
            return;
        }
        try {
            stopNative();
        } catch (Throwable ignored) {
        }
    }

    public static boolean isGrabbed() {
        if (!sLoaded) {
            return false;
        }
        try {
            return isGrabbedNative();
        } catch (Throwable t) {
            return false;
        }
    }

    public static String deviceName(String path) {
        if (!sLoaded) {
            return "(no so)";
        }
        try {
            return deviceNameNative(path);
        } catch (Throwable t) {
            return "(err " + t + ")";
        }
    }

    /** native 回调，运行在 native reader 线程上 */
    private static void onKey(int code, int value) {
        Listener l = sListener;
        if (l != null) {
            l.onGrabKey(code, value);
        }
    }

    private static native String startNative(String path);

    private static native void stopNative();

    private static native boolean isGrabbedNative();

    private static native String deviceNameNative(String path);
}
