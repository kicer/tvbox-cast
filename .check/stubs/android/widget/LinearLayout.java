package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public class LinearLayout extends ViewGroup {
    public static final int VERTICAL = 1;
    public static final int HORIZONTAL = 0;

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int w, int h, float weight) { super(w, h, weight); }
    }

    public LinearLayout(Context c) {}
    public void setOrientation(int o) {}
}
