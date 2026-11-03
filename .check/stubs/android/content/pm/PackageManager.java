package android.content.pm;

import android.content.Intent;
import java.util.List;

public class PackageManager {
    public static final int MATCH_DEFAULT_ONLY = 0x00010000;
    public ResolveInfo resolveActivity(Intent i, int flags) { return null; }
    public List<ResolveInfo> queryIntentActivities(Intent i, int flags) { return null; }
}
