package com.removereels;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private Switch blockSwitch;
    private TextView toggleStatus;
    private View setupCard;
    private View serviceOk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        blockSwitch = findViewById(R.id.block_switch);
        toggleStatus = findViewById(R.id.toggle_status);
        setupCard = findViewById(R.id.setup_card);
        serviceOk = findViewById(R.id.service_ok);

        blockSwitch.setOnCheckedChangeListener((button, checked) -> {
            Prefs.setBlocking(this, checked);
            refresh();
        });
        findViewById(R.id.toggle_card).setOnClickListener(v -> blockSwitch.toggle());

        findViewById(R.id.open_accessibility).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.open_app_info).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null))));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean blocking = Prefs.isBlocking(this);
        boolean serviceOn = isServiceEnabled();

        if (blockSwitch.isChecked() != blocking) blockSwitch.setChecked(blocking);

        if (!serviceOn) {
            toggleStatus.setText("Finish setup below first");
        } else if (blocking) {
            toggleStatus.setText("On: Reels tab is hidden");
        } else {
            toggleStatus.setText("Off: Instagram is normal");
        }
        setupCard.setVisibility(serviceOn ? View.GONE : View.VISIBLE);
        serviceOk.setVisibility(serviceOn ? View.VISIBLE : View.GONE);
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName me = new ComponentName(this, ReelsBlockerService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        for (String entry : splitter) {
            ComponentName cn = ComponentName.unflattenFromString(entry);
            if (me.equals(cn)) return true;
        }
        return false;
    }
}
