package cn.itv.mobile.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage("com.ais.mimo.aisplay.tv");
            if (i == null) i = getPackageManager().getLaunchIntentForPackage("com.ais.mimo.aisplay.tv");
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(i);
            }
        } catch (Exception ignored) {}
        finish();
    }
}
