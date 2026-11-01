package com.val00n.dmpg.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import com.val00n.dmpg.R;
import com.val00n.dmpg.ui.settings.instances.InstanceSettingsActivity;

public class SettingsActivity extends Activity {

    private Button instancesButton;
    private Button cacheButton;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        instancesButton = (Button) findViewById(R.id.settingsInstancesButton);
        cacheButton = (Button) findViewById(R.id.settingsCacheButton);

        instancesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateTo(InstanceSettingsActivity.class);
            }
        });

        cacheButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateTo(CacheSettingsActivity.class);
            }
        });
    }

    private void navigateTo(Class<?> cls) {
        Intent intent = new Intent(this, cls);
        startActivity(intent);
    }
}
