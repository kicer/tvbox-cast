package com.kicer.tvboxcast;

/**
 * 普通 Activity 模式（方案 A）：
 * 不抢系统 HOME，直接从 `am start` 或桌面上点进来。
 */
public class MainActivity extends BaseProbeActivity {

    @Override
    protected String probeTitle() {
        return "KICER PROBE / MAIN   com.kicer.tvboxcast";
    }
}
