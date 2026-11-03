package android.view;

import android.content.Context;
import android.view.ViewGroup;

public class WindowManager {
    public void addView(View v, ViewGroup.LayoutParams p) {}
    public void removeView(View v) {}

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public static final int FLAG_FULLSCREEN = 0x00000400;
        public static final int FLAG_KEEP_SCREEN_ON = 0x00000080;
        public static final int FLAG_NOT_FOCUSABLE = 0x00000008;
        public static final int FLAG_LAYOUT_IN_SCREEN = 0x00000100;
        public static final int TYPE_SYSTEM_ALERT = 2003;

        public LayoutParams(int w, int h, int type, int flags, int format) {
            super(w, h);
        }
    }
}
