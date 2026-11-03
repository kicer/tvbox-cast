package com.kicer.tvboxcast;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
 * 探针基类：全屏黑底 + 把收到的每个按键画在屏幕上。
 *
 * 目的（一次性回答 5 个问题）：
 *   1. 我们能不能拿到 菜单/直播/回看 的 keyCode
 *   2. return true 能不能吃掉它们（画面留在我们这）
 *   3. 吃掉之后 IPTV 还会不会跳到前台（看 onPause / windowFocus）
 *   4. 从我们这里能不能用 am start 等价的 Intent 切回 IPTV
 *   5. 我们全屏时直播音频是否静音
 *
 * 屏幕操作键（探针自身用，不进 logcat 之外的业务逻辑）：
 *   0 → 回 IPTV 桌面 (HostActivity)
 *   9 → 直接进直播 (LiveForHomeActivity)
 *   7 → 切换「全部消费 / 放行给系统」
 *   8 → 重新解析当前 HOME 是哪个 Activity
 *   返回 ×3 → 退出探针
 *
 * 注意：0/9/7/8 是数字键，正式答题 UI 里要换掉。
 */
public abstract class BaseProbeActivity extends Activity {

    protected static final String TAG = "KICERPROBE";

    private static final int MAX_LOG_LINES = 14;

    private static final String IPTV_PKG = "com.ipanel.join.homed.stb";
    private static final String IPTV_HOME_CLS = "com.ipanel.join.homed.stb.HostActivity";
    private static final String IPTV_LIVE_CLS =
            "com.ipanel.join.topway.homed.live.LiveForHomeActivity";

    /** 可能被系统级钩子截走的键，按下后 1.5s 检查我们是否还在前台 */
    private static final int[] DANGER_KEYS = {3, 82, 130, 131, 132, 133, 84};

    private TextView tvKey;
    private TextView tvMeta;
    private TextView tvLog;
    private ScrollView logScroll;

    private final ArrayList<String> lines = new ArrayList<String>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private final Handler handler = new Handler();

    /** true = 消费全部按键并 return true；false = 放行给系统（对照组） */
    protected boolean consumeAll = true;
    private boolean resumed = false;
    private int resumeCount = 0;
    private int pauseCount = 0;
    private long lastBackAt = 0;
    private int backStreak = 0;
    /** 本次按下被我们吃掉的键，抬起也要一起吃掉，避免半截事件漏给系统 */
    private int consumedDownCode = -1;
    private String homeInfo = "?";

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

    /** 子类给个标题 */
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

        log("=== " + probeTitle() + " 启动 ===");
        log("HOME → " + homeInfo);
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
            logKey(event);

            if (tvKey != null) {
                tvKey.setText(KeyNames.name(code) + "   (" + code + ")");
            }

            if (handleLocalKey(code)) {
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

        // 探针核心：整个按下/抬起序列都吃掉，或者整段放给系统
        return consumeAll || super.dispatchKeyEvent(event);
    }

    private void logKey(KeyEvent e) {
        String line = KeyNames.name(e.getKeyCode())
                + "  code=" + e.getKeyCode()
                + "  scan=" + e.getScanCode()
                + "  src=0x" + Integer.toHexString(e.getSource())
                + "  rc=" + e.getRepeatCount();
        log(line);
    }

    private boolean isDangerKey(int code) {
        for (int k : DANGER_KEYS) {
            if (k == code) {
                return true;
            }
        }
        return false;
    }

    /** 探针自身的操作键。返回 true = 已处理 */
    private boolean handleLocalKey(int code) {
        switch (code) {
            case 7: // 0
                launchIptv(IPTV_HOME_CLS);
                return true;
            case 16: // 9
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

    /** 按下危险键 1.5 秒后，看我们还在不在前台 */
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

    // ------------------------------------------------------------------ actions

    /**
     * 等价于 adb shell am start -n pkg/.Cls
     * （不含 CATEGORY_LAUNCHER，与实测有效的命令一致）
     */
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
        root.setPadding(56, 36, 56, 36);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(probeTitle());
        tvTitle.setTextColor(0xFF00E5FF);
        tvTitle.setTextSize(24f);
        root.addView(tvTitle);

        tvKey = new TextView(this);
        tvKey.setText("— 等待按键 —");
        tvKey.setTextColor(Color.WHITE);
        tvKey.setTextSize(64f);
        tvKey.setTypeface(Typeface.DEFAULT_BOLD);
        tvKey.setPadding(0, 16, 0, 16);
        root.addView(tvKey);

        tvMeta = new TextView(this);
        tvMeta.setTextColor(0xFFAAAAAA);
        tvMeta.setTextSize(20f);
        tvMeta.setTypeface(Typeface.MONOSPACE);
        root.addView(tvMeta);

        TextView hint = new TextView(this);
        hint.setText("0=回IPTV桌面   9=进直播   7=切换消费/放行   8=重读HOME   返回x3=退出");
        hint.setTextColor(0xFF666666);
        hint.setTextSize(18f);
        hint.setPadding(0, 10, 0, 18);
        root.addView(hint);

        logScroll = new ScrollView(this);
        logScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        logScroll.setBackgroundColor(0xFF050505);

        tvLog = new TextView(this);
        tvLog.setTextColor(0xFF88FF88);
        tvLog.setTextSize(19f);
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
                + "    前台: " + (resumed ? "是" : "否")
                + "    windowFocus: " + (hasWindowFocus() ? "有" : "无")
                + "    resume=" + resumeCount + " pause=" + pauseCount
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
