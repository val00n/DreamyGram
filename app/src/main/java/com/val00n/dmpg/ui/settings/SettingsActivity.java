package com.val00n.dmpg.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;

import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.services.SettingsManager;
import com.val00n.dmpg.ui.SimpleTextWatcher;
import com.val00n.dmpg.ui.settings.instances.InstanceSettingsActivity;

public class SettingsActivity extends Activity {

    private Button instancesButton;
    private Button cacheButton;
    private EditText dialogsCountEditText;
    private EditText messagesCountEditText;
    private EditText dialogPollEditText;
    private EditText messagesPollEditText;
    private CheckBox loadAvatarsCheckBox;
    private CheckBox loadMediaCheckBox;
    private CheckBox dialogPollCheckBox;
    private CheckBox chatPollCheckBox;
    private Spinner chatStyleSpinner;
    private ArrayAdapter<String> chatStyleSpinnerAdapter;

    private SettingsManager settingsManager;
    private String[] chatStyles;
    private String[] chatStyleValues;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        settingsManager = App.getSettingsManager();

        instancesButton = (Button) findViewById(R.id.settingsInstancesButton);
        cacheButton = (Button) findViewById(R.id.settingsCacheButton);
        dialogsCountEditText = (EditText) findViewById(R.id.settingsDialogsCountEditText);
        messagesCountEditText = (EditText) findViewById(R.id.settingsMessagesCountEditText);
        dialogPollEditText = (EditText) findViewById(R.id.settingsDialogPollEditText);
        messagesPollEditText = (EditText) findViewById(R.id.settingsMessagesPollEditText);
        loadAvatarsCheckBox = (CheckBox) findViewById(R.id.settingsLoadAvatarsCheckBox);
        loadMediaCheckBox = (CheckBox) findViewById(R.id.settingsLoadMediaCheckBox);
        dialogPollCheckBox = (CheckBox) findViewById(R.id.settingsDialogPollCheckBox);
        chatPollCheckBox = (CheckBox) findViewById(R.id.settingsChatPollCheckBox);
        chatStyleSpinner = (Spinner) findViewById(R.id.settingsChatStyleSpinner);

        loadValues();

        dialogsCountEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(String s) {
                try { settingsManager.setDialogsCount(Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
            }
        });

        messagesCountEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(String s) {
                try { settingsManager.setMessagesCount(Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
            }
        });

        dialogPollEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(String s) {
                try { settingsManager.setDialogPollSecs(Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
            }
        });

        messagesPollEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(String s) {
                try { settingsManager.setMessagesPollSecs(Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
            }
        });

        loadAvatarsCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settingsManager.setLoadAvatars(isChecked);
            }
        });

        loadMediaCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settingsManager.setLoadMedia(isChecked);
            }
        });

        dialogPollCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settingsManager.setDialogPollEnabled(isChecked);
            }
        });

        chatPollCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settingsManager.setChatPollEnabled(isChecked);
            }
        });

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

        chatStyleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settingsManager.setChatStyle(chatStyleValues[position]);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadValues() {
        chatStyles = new String[]{"Classic", "Modern", "Compact"};
        chatStyleValues = new String[]{
                SettingsManager.CHAT_STYLE_DEFAULT,
                SettingsManager.CHAT_STYLE_MODERN,
                SettingsManager.CHAT_STYLE_COMPACT
        };

        chatStyleSpinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, chatStyles);
        chatStyleSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        chatStyleSpinner.setAdapter(chatStyleSpinnerAdapter);

        int chatStyle = 0;
        final String currentType = settingsManager.getChatStyle();
        for (int i = 0; i < chatStyleValues.length; i++) {
            if (chatStyleValues[i].equals(currentType)) {
                chatStyle = i;
                break;
            }
        }
        chatStyleSpinner.setSelection(chatStyle);

        dialogsCountEditText.setText(String.valueOf(settingsManager.getDialogsCount()));
        messagesCountEditText.setText(String.valueOf(settingsManager.getMessagesCount()));
        dialogPollEditText.setText(String.valueOf(settingsManager.getDialogPollMillis() / 1000));
        messagesPollEditText.setText(String.valueOf(settingsManager.getMessagesPollMillis() / 1000));
        loadAvatarsCheckBox.setChecked(settingsManager.getLoadAvatars());
        loadMediaCheckBox.setChecked(settingsManager.getLoadMedia());
        dialogPollCheckBox.setChecked(settingsManager.isDialogPollEnabled());
        chatPollCheckBox.setChecked(settingsManager.isChatPollEnabled());
    }

    private void navigateTo(Class<?> cls) {
        Intent intent = new Intent(this, cls);
        startActivity(intent);
    }
}
