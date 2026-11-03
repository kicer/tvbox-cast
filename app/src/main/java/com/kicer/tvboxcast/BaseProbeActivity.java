package com.kicer.tvboxcast;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 探针基类 v0.2.0
 *
 * 背景结论（见 ../tvbox-cast/KEY_INTERCEPT.md）：
 *   「直播 / 回看 / 主页」在 system_server 的厂商输入策略里就被消费，应用层拦不住。
 *   所以这里加两个实验：
 *     实验 1（grab）：用 EVIOCGRAB 独占 /dev/input/event0，让 system_server 根本读不到事件
 *     实验 2（悬浮窗）：全屏 TYPE_SYSTEM_ALERT 悬浮窗，看能否盖住 IPTV
 *
 * 探针操作键（数字键）：
 *   0 → 回 IPTV 桌面          1 → 开始独占 event0        2 → 释放独占
 *   3 → 显示/隐藏悬浮窗        4 → 进直播                 7 → 切换 消费/放行
 *   8 → 重读 HOME             返回 ×3 → 退出
 *
 * 独占之后的按键不再走 dispatchKeyEvent，而是由 native reader 回调，
 * 日志里标成 raw，并同时打印 Linux 输入码。
 */
public abstract class BaseProbeActivity extends Activity implements NativeGrab.Listener {

    protected static final String TAG = "KICERPROBE";

    private static final int MAX_LOG_LINES = 16;

    private static final String IPTV_PKG = "com.ipanel.join.homed.stb";
    private static final String IPTV_HOME_CLS = "com.ipanel.join.homed.stb.HostActivity";
    private static final String IPTV_LIVE_CLS =
            "com.ipanel.join.topway.homed.live.LiveForHomeActivity";

    /** 可能被系统钩子截走的键（Android keyCode），按下后 1.5s 复查是否还在前台 */
    private static final int[] DANGER_KEYS = {3, 82, 130, 131, 132, 133, 84};

    private TextView tvKey;
    private TextView tvMeta;
    private TextView tvLog;
    private ScrollView logScroll;

    private final ArrayList<String> lines = new ArrayList<String>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private final Handler handler = new Handler();

    protected boolean consumeAll = true;
    private boolean resumed = false;
    private int resumeCount = 0;
    private int pauseCount = 0;
    private long lastBackAt = 0;
    private int backStreak = 0;
    private String homeInfo = "?";

    private String grabState = "未抓";
    private String deviceName = "?";

    private WindowManager wm;
    private View overlayView;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshMeta();
            handler.postDelayed(this, 500);
        }
    };

    private final Runnable scrollToBottom = new Runnable() {
        @Override
        public void run() {
            if (logScroll != null) {
                logScroll.fullScroll(View.FOCUS_DOWN);
            }
        }
    };

    protected abstract String probeTitle();

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        buildUi();
        homeInfo = resolveHome();
        NativeGrab.setListener(this);

        log("=== " + probeTitle() + " 启动 ===");
        log("HOME → " + homeInfo);
        log("libevgrab: " + (NativeGrab.available() ? "已加载" : "未加载!"));
        if (NativeGrab.available()) {
            deviceName = NativeGrab.deviceName(NativeGrab.EVENT0);
            log("event0 设备名: " + deviceName);
        }
        Log.i(TAG, "started " + getClass().getName());
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        resumeCount++;
        log("onResume");
        refreshMeta();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        resumed = false;
        pauseCount++;
        log("onPause  <<< 我们失去前台");
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        NativeGrab.stop();
        removeOverlay();
        NativeGrab.setListener(null);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        log("windowFocus=" + hasFocus);
        refreshMeta();
    }

    // ------------------------------------------------------------------ key I/O

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int code = event.getKeyCode();

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            log(KeyNames.name(code) + "  code=" + code
                    + "  scan=" + event.getScanCode()
                    + "  src=0x" + Integer.toHexString(event.getSource())
                    + "  [act]");

            if (tvKey != null) {
                tvKey.setText(KeyNames.name(code) + "   (" + code + ")");
            }

            if (handleAction(code)) {
                consumedDownCode = code;
                return true;
            }
            if (isDangerKey(code)) {
                scheduleForegroundCheck(code);
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP && code == consumedDownCode) {
            consumedDownCode = -1;
            return true;
        }

        return consumeAll || super.dispatchKeyEvent(event);
    }

    /**
     * 独占 event0 之后按键从这里来（native 线程），value: 1=按下 0=抬起 2=重复。
     */
    @Override
    public void onGrabKey(final int linuxCode, final int value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int androidCode = RawKeys.toAndroid(linuxCode);
                if (value == 1) {
                    log(RawKeys.name(linuxCode) + "  linux=" + linuxCode
                            + "  -> android=" + androidCode + "  [raw]");
                    if (tvKey != null) {
                        tvKey.setText(RawKeys.name(linuxCode) + "   (linux " + linuxCode + ")");
                    }
                    if (handleAction(androidCode)) {
                        return;
                    }
                    if (isDangerKey(androidCode)) {
                        scheduleForegroundCheck(androidCode);
                    }
                }
            }
        });
    }

    private int consumedDownCode = -1;

    private boolean isDangerKey(int code) {
        for (int k : DANGER_KEYS) {
            if (k == code) {
                return true;
            }
        }
        return false;
    }

    /** 探针自身的操作键（Android keyCode）。返回 true = 已处理 */
    private boolean handleAction(int code) {
        switch (code) {
            case 7: // 0
                launchIptv(IPTV_HOME_CLS);
                return true;
            case 8: // 1
                startGrab();
                return true;
            case 9: // 2
                stopGrab();
                return true;
            case 10: // 3
                toggleOverlay();
                return true;
            case 11: // 4
                launchIptv(IPTV_LIVE_CLS);
                return true;
            case 14: // 7
                consumeAll = !consumeAll;
                log(">>> 模式切换: " + (consumeAll ? "全部消费" : "放行给系统"));
                refreshMeta();
                return true;
            case 15: // 8
                homeInfo = resolveHome();
                log(">>> 重新解析 HOME: " + homeInfo);
                refreshMeta();
                return true;
            case 4: // BACK
                onBackProbe();
                return true;
            default:
                return false;
        }
    }

    private void onBackProbe() {
        long now = System.currentTimeMillis();
        if (now - lastBackAt < 2500) {
            backStreak++;
        } else {
            backStreak = 1;
        }
        lastBackAt = now;
        log("返回 " + backStreak + "/3");
        if (backStreak >= 3) {
            log(">>> 退出探针");
            finish();
        }
    }

    private void scheduleForegroundCheck(final int code) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean ok = resumed && hasWindowFocus();
                log((ok ? "✓ 仍在我们的界面" : "✗ 焦点已被抢走")
                        + "  (after " + KeyNames.name(code) + ")");
                refreshMeta();
            }
        }, 1500);
    }

    // -------------------------------------------------------------- 实验 1: grab

    private void startGrab() {
        if (!NativeGrab.available()) {
            log(">>> grab 失败: so 未加载");
            return;
        }
        String err = NativeGrab.start(NativeGrab.EVENT0);
        if (err == null || err.length() == 0) {
            grabState = "已独占 event0";
            log(">>> grab 成功，现在按遥控器应该只出现在这里（标记 raw）");
        } else {
            grabState = "失败: " + err;
            log(">>> grab 失败: " + err);
        }
        refreshMeta();
    }

    private void stopGrab() {
        NativeGrab.stop();
        grabState = "未抓";
        log(">>> grab 已释放（按键回到系统）");
        refreshMeta();
    }

    // ---------------------------------------------------------- 实验 2: 悬浮窗

    private void toggleOverlay() {
        if (overlayView != null) {
            removeOverlay();
            log(">>> 悬浮窗已移除");
            return;
        }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        TextView v = new OverlayView(this);
        v.setText("OVERLAY 占屏测试\n\n（全屏悬浮窗，压在所有 Activity 之上）");
        v.setBackgroundColor(0xFFFF00FF); // 洋红：方便用截屏像素判断有没有被盖住
        v.setTextColor(0xFF101010);
        v.setTextSize(44f);
        v.setGravity(Gravity.CENTER);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);

        try {
            wm.addView(v, lp);
            overlayView = v;
            log(">>> 悬浮窗已显示（当前 Activity 可能失去输入焦点）");
        } catch (Exception e) {
            log(">>> 悬浮窗失败: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
        refreshMeta();
    }

    private void removeOverlay() {
        if (overlayView == null) {
            return;
        }
        try {
            wm.removeView(overlayView);
        } catch (Exception ignored) {
        }
        overlayView = null;
    }

    /** 悬浮窗自己也记日志，用来判断它有没有拿到按键 */
    private class OverlayView extends TextView {
        OverlayView(Activity ctx) {
            super(ctx);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                final int code = event.getKeyCode();
                log("OVERLAY 收到 " + KeyNames.name(code) + " code=" + code + "  [ovl]");
                // post 出去再处理：避免在 dispatch 过程中移除自己这个窗口
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        handleAction(code);
                    }
                });
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ actions

    private void launchIptv(String cls) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setClassName(IPTV_PKG, cls);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            log(">>> 启动 " + cls);
        } catch (Exception e) {
            log(">>> 启动失败 " + cls + " : " + e.getClass().getSimpleName());
        }
    }

    private String resolveHome() {
        PackageManager pm = getPackageManager();
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);

        String def;
        try {
            ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null) {
                def = "null";
            } else {
                def = ri.activityInfo.packageName + "/" + ri.activityInfo.name;
            }
        } catch (Exception e) {
            def = "err:" + e.getClass().getSimpleName();
        }

        int count = 0;
        try {
            List<ResolveInfo> all = pm.queryIntentActivities(home, 0);
            count = all == null ? 0 : all.size();
        } catch (Exception ignored) {
        }

        String mine = def.startsWith(getPackageName()) ? "  [我们=HOME]" : "";
        return def + "  (候选 " + count + ")" + mine;
    }

    // ---------------------------------------------------------------------- ui

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(56, 30, 56, 30);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(probeTitle());
        tvTitle.setTextColor(0xFF00E5FF);
        tvTitle.setTextSize(24f);
        root.addView(tvTitle);

        tvKey = new TextView(this);
        tvKey.setText("— 等待按键 —");
        tvKey.setTextColor(Color.WHITE);
        tvKey.setTextSize(56f);
        tvKey.setTypeface(Typeface.DEFAULT_BOLD);
        tvKey.setPadding(0, 10, 0, 10);
        root.addView(tvKey);

        tvMeta = new TextView(this);
        tvMeta.setTextColor(0xFFAAAAAA);
        tvMeta.setTextSize(19f);
        tvMeta.setTypeface(Typeface.MONOSPACE);
        root.addView(tvMeta);

        TextView hint = new TextView(this);
        hint.setText("0=回桌面  1=Grab  2=Release  3=悬浮窗  4=直播  7=消费/放行  8=重读HOME  返回x3=退出");
        hint.setTextColor(0xFF666666);
        hint.setTextSize(17f);
        hint.setPadding(0, 8, 0, 12);
        root.addView(hint);

        logScroll = new ScrollView(this);
        logScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        logScroll.setBackgroundColor(0xFF050505);

        tvLog = new TextView(this);
        tvLog.setTextColor(0xFF88FF88);
        tvLog.setTextSize(18f);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setPadding(12, 12, 12, 12);
        logScroll.addView(tvLog);

        root.addView(logScroll);
        setContentView(root);
    }

    private void refreshMeta() {
        if (tvMeta == null) {
            return;
        }
        String s = "模式: " + (consumeAll ? "全部消费" : "放行")
                + "   前台: " + (resumed ? "是" : "否")
                + "   focus: " + (hasWindowFocus() ? "有" : "无")
                + "   res/pause=" + resumeCount + "/" + pauseCount
                + "\nGrab: " + grabState
                + "   悬浮窗: " + (overlayView != null ? "显示中" : "关")
                + "   event0=" + deviceName
                + "\nHOME → " + homeInfo;
        tvMeta.setText(s);
    }

    protected void log(String s) {
        String line = "[" + fmt.format(new Date()) + "] " + s;
        Log.i(TAG, line);

        lines.add(line);
        while (lines.size() > MAX_LOG_LINES) {
            lines.remove(0);
        }

        if (tvLog != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                sb.append(lines.get(i)).append('\n');
            }
            tvLog.setText(sb.toString());
            logScroll.post(scrollToBottom);
        }
    }
}
