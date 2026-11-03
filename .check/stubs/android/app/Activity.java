package android.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;

public class Activity extends android.content.Context {
    protected void onCreate(Bundle b) {}
    protected void onResume() {}
    protected void onPause() {}
    protected void onStart() {}
    protected void onStop() {}
    protected void onDestroy() {}
    protected void onNewIntent(Intent i) {}
    public void onWindowFocusChanged(boolean hasFocus) {}
    public boolean dispatchKeyEvent(KeyEvent e) { return false; }
    public boolean hasWindowFocus() { return true; }
    public Window getWindow() { return null; }
    public android.content.pm.PackageManager getPackageManager() { return null; }
    public String getPackageName() { return ""; }
    public void startActivity(Intent i) {}
    public void finish() {}
    public void setContentView(View v) {}
    public void setIntent(Intent i) {}
    public Intent getIntent() { return null; }
    public void setRequestedOrientation(int o) {}
}
