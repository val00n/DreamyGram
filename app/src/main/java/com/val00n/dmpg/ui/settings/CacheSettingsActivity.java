package com.val00n.dmpg.ui.settings;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import com.val00n.dmpg.R;

public class CacheSettingsActivity extends Activity {

    private Button avatarClear;
    private Button mediaClear;
    private Button chatsClear;
    private EditText avatarEditText;
    private EditText mediaEditText;
    private EditText chatsEditText;
    private CheckBox avatarStoreCheckBox;
    private CheckBox mediaStoreCheckBox;
    private CheckBox chatsStoreCheckBox;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cache_settings);

        avatarClear = (Button) findViewById(R.id.cacheSettingsAvatarClear);
        mediaClear = (Button) findViewById(R.id.cacheSettingsMediaClear);
        chatsClear = (Button) findViewById(R.id.cacheSettingsChatsClear);
        avatarEditText = (EditText) findViewById(R.id.cacheSettingsAvatarEditText);
        mediaEditText = (EditText) findViewById(R.id.cacheSettingsMediaEditText);
        chatsEditText = (EditText) findViewById(R.id.cacheSettingsChatsEditText);
        avatarStoreCheckBox = (CheckBox) findViewById(R.id.cacheSettingsAvatarStoreCheckBox);
        mediaStoreCheckBox = (CheckBox) findViewById(R.id.cacheSettingsMediaStoreCheckBox);
        chatsStoreCheckBox = (CheckBox) findViewById(R.id.cacheSettingsChatsStoreCheckBox);
    }
}
