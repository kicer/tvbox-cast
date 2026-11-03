package android.content;

public class Intent {
    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    public static final String CATEGORY_HOME = "android.intent.category.HOME";
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public static final int FLAG_ACTIVITY_RESET_TASK_IF_NEEDED = 0x00200000;

    public Intent() {}
    public Intent(String action) {}
    public Intent setClassName(String pkg, String cls) { return this; }
    public Intent addFlags(int flags) { return this; }
    public Intent addCategory(String c) { return this; }
}
