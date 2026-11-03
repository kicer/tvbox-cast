package com.kicer.tvboxcast;

import android.content.Intent;

/**
 * HOME 模式（方案 B）：
 * 申请了 CATEGORY_HOME，被设为默认桌面后，主页键会回到我们这里，
 * 天然拦死「按主页逃逸」。默认 android:enabled="false"，
 * 测试时用 `pm enable com.kicer.tvboxcast/.HomeProbeActivity` 打开。
 */
public class HomeProbeActivity extends BaseProbeActivity {

    @Override
    protected String probeTitle() {
        return "KICER PROBE / HOME   (本 Activity = 桌面时，主页键被拦死)";
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // 已经在前台时再按主页，系统会把我们重新拉到前台 → 走到这里
        log(">>> onNewIntent：系统又把我们叫到前台（可能是主页键）");
    }
}
