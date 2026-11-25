package com.val00n.dmpg.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.cache.CacheManager;
import com.val00n.dmpg.services.FileHelper;
import com.val00n.dmpg.ui.SimpleTextWatcher;

public class CacheSettingsActivity extends Activity {

    private TextView globalText;
    private TextView avatarsText;
    private TextView mediaText;
    private TextView chatsText;
    private TextView storageText;
    private Button avatarClear;
    private Button mediaClear;
    private Button chatsClear;
    private Button storageClear;
    private EditText avatarEditText;
    private EditText mediaEditText;
    private EditText chatsEditText;
    private EditText avatarTTLEditText;
    private EditText mediaTTLEditText;
    private CheckBox avatarStoreCheckBox;
    private CheckBox mediaStoreCheckBox;
    private CheckBox chatsStoreCheckBox;

    private long avatarsSize;
    private long mediaSize;
    private long chatsSize;
    private long storageSize;
    private long avatarsLimit;
    private long mediaLimit;
    private long chatsLimit;
    private long avatarsTTL;
    private long mediaTTL;
    private boolean storeAvatars;
    private boolean storeChats;
    private boolean storeMedia;

    private SharedPreferences sharedPreferences;
    private long globalSize;
    private long globalLimit;
    private long avatarsLimitMb;
    private long mediaLimitMb;
    private long chatsLimitMb;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cache_settings);

        globalText = (TextView) findViewById(R.id.cacheSettingsGlobalText);
        avatarsText = (TextView) findViewById(R.id.cacheSettingsAvatarsText);
        mediaText = (TextView) findViewById(R.id.cacheSettingsMediaText);
        chatsText = (TextView) findViewById(R.id.cacheSettingsChatsText);
        storageText = (TextView) findViewById(R.id.cacheSettingsStorageText);
        avatarClear = (Button) findViewById(R.id.cacheSettingsAvatarClear);
        mediaClear = (Button) findViewById(R.id.cacheSettingsMediaClear);
        chatsClear = (Button) findViewById(R.id.cacheSettingsChatsClear);
        storageClear = (Button) findViewById(R.id.cacheSettingsStorageSavedClear);
        avatarEditText = (EditText) findViewById(R.id.cacheSettingsAvatarEditText);
        mediaEditText = (EditText) findViewById(R.id.cacheSettingsMediaEditText);
        chatsEditText = (EditText) findViewById(R.id.cacheSettingsChatsEditText);
        avatarTTLEditText = (EditText) findViewById(R.id.cacheSettingsAvatarTTLEditText);
        mediaTTLEditText = (EditText) findViewById(R.id.cacheSettingsMediaTTLEditText);
        avatarStoreCheckBox = (CheckBox) findViewById(R.id.cacheSettingsAvatarStoreCheckBox);
        mediaStoreCheckBox = (CheckBox) findViewById(R.id.cacheSettingsMediaStoreCheckBox);
        chatsStoreCheckBox = (CheckBox) findViewById(R.id.cacheSettingsChatsStoreCheckBox);

        sharedPreferences = this.getSharedPreferences("cache", MODE_PRIVATE);

        loadValues();
        showValues();

        avatarStoreCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sharedPreferences.edit().putBoolean("storeAvatars", isChecked).commit();
            }
        });

        mediaStoreCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sharedPreferences.edit().putBoolean("storeMedia", isChecked).commit();
            }
        });

        chatsStoreCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sharedPreferences.edit().putBoolean("storeChats", isChecked).commit();
            }
        });

        avatarClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(CacheSettingsActivity.this)
                        .setTitle(getString(R.string.cache_settings_are_you_sure))
                        .setMessage(getString(R.string.cache_settings_wipe_avatar_cache))
                        .setNegativeButton(R.string.action_wipe, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final ProgressDialog pd = new ProgressDialog(CacheSettingsActivity.this);
                                pd.setTitle(getString(R.string.cache_settings_clearing));
                                pd.setMessage(getString(R.string.cache_settings_wiping_avatar_cache));
                                pd.setCancelable(false);
                                pd.show();

                                App.getCacheManager().wipeFilesCache(new Runnable() {
                                    @Override
                                    public void run() {
                                        pd.dismiss();
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(CacheSettingsActivity.this, R.string.cache_settings_avatar_cleared, Toast.LENGTH_SHORT).show();
                                                loadValues();
                                                showValues();
                                            }
                                        });
                                    }
                                }, true);
                            }
                        })
                        .setPositiveButton(R.string.action_cancel, null)
                        .show();
            }
        });

        chatsClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(CacheSettingsActivity.this)
                        .setTitle(getString(R.string.cache_settings_are_you_sure))
                        .setMessage(getString(R.string.cache_settings_wipe_chats_cache))
                        .setNegativeButton(R.string.action_wipe, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final ProgressDialog pd = new ProgressDialog(CacheSettingsActivity.this);
                                pd.setTitle(getString(R.string.cache_settings_clearing));
                                pd.setMessage(getString(R.string.cache_settings_wiping_chats_cache));
                                pd.setCancelable(false);
                                pd.show();

                                App.getCacheManager().wipeChatsCache(new Runnable() {
                                    @Override
                                    public void run() {
                                        pd.dismiss();
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(CacheSettingsActivity.this, R.string.cache_settings_chats_cleared, Toast.LENGTH_SHORT).show();
                                                loadValues();
                                                showValues();
                                            }
                                        });
                                    }
                                });
                            }
                        })
                        .setPositiveButton(R.string.action_cancel, null)
                        .show();
            }
        });

        mediaClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(CacheSettingsActivity.this)
                        .setTitle(getString(R.string.cache_settings_are_you_sure))
                        .setMessage(getString(R.string.cache_settings_wipe_media_cache))
                        .setNegativeButton(R.string.action_wipe, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                final ProgressDialog pd = new ProgressDialog(CacheSettingsActivity.this);
                                pd.setTitle(getString(R.string.cache_settings_clearing));
                                pd.setMessage(getString(R.string.cache_settings_wiping_media_cache));
                                pd.setCancelable(false);
                                pd.show();

                                App.getCacheManager().wipeFilesCache(new Runnable() {
                                    @Override
                                    public void run() {
                                        pd.dismiss();
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(CacheSettingsActivity.this, R.string.cache_settings_media_cleared, Toast.LENGTH_SHORT).show();
                                                loadValues();
                                                showValues();
                                            }
                                        });
                                    }
                                }, false);
                            }
                        })
                        .setPositiveButton(R.string.action_cancel, null)
                        .show();
            }
        });

        storageClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(CacheSettingsActivity.this)
                        .setTitle(getString(R.string.cache_settings_are_you_sure))
                        .setMessage(getString(R.string.cache_settings_wipe_storage_cache))
                        .setNegativeButton(R.string.action_wipe, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                FileHelper.deleteDirectory(false);
                                Toast.makeText(CacheSettingsActivity.this, R.string.cache_settings_storage_cleared, Toast.LENGTH_SHORT).show();
                                loadValues();
                                showValues();
                            }
                        })
                        .setPositiveButton(R.string.action_cancel, null)
                        .show();
            }
        });

        avatarEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String s) {
                try {
                    long val = Long.parseLong(s);
                    avatarsLimitMb = val;
                    avatarsLimit = val * 1024 * 1024;
                    sharedPreferences.edit().putLong("avatarsLimit", val).commit();
                    App.getCacheManager().refreshSettings();
                } catch (NumberFormatException ignored) {}
            }
        });

        mediaEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String s) {
                try {
                    long val = Long.parseLong(s);
                    mediaLimitMb = val;
                    mediaLimit = val * 1024 * 1024;
                    sharedPreferences.edit().putLong("mediaLimit", val).commit();
                    App.getCacheManager().refreshSettings();
                } catch (NumberFormatException ignored) {}
            }
        });

        chatsEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String s) {
                try {
                    long val = Long.parseLong(s);
                    chatsLimitMb = val;
                    chatsLimit = val * 1024 * 1024;
                    sharedPreferences.edit().putLong("chatsLimit", val).commit();
                    App.getCacheManager().refreshSettings();
                } catch (NumberFormatException ignored) {}
            }
        });

        avatarTTLEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String s) {
                try {
                    long days = Long.parseLong(s);
                    avatarsTTL = days * 24 * 60 * 60 * 1000;
                    sharedPreferences.edit().putLong("avatarsTTL", days).commit();
                    App.getCacheManager().refreshSettings();
                } catch (NumberFormatException ignored) {}
            }
        });

        mediaTTLEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(String s) {
                try {
                    long days = Long.parseLong(s);
                    mediaTTL = days * 24 * 60 * 60 * 1000;
                    sharedPreferences.edit().putLong("mediaTTL", days).commit();
                    App.getCacheManager().refreshSettings();
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    private void showValues() {
        globalSize = avatarsSize + mediaSize + chatsSize;
        globalLimit = avatarsLimit + mediaLimit + chatsLimit;

        globalText.setText(getString(R.string.cache_settings_global, FileHelper.formatSize(globalSize), FileHelper.formatSize(globalLimit)));
        avatarsText.setText(getString(R.string.cache_settings_avatars, FileHelper.formatSize(avatarsSize), FileHelper.formatSize(avatarsLimit)));
        mediaText.setText(getString(R.string.cache_settings_media, FileHelper.formatSize(mediaSize), FileHelper.formatSize(mediaLimit)));
        chatsText.setText(getString(R.string.cache_settings_chats, FileHelper.formatSize(chatsSize), FileHelper.formatSize(chatsLimit)));
        storageText.setText(getString(R.string.cache_settings_storage, FileHelper.formatSize(storageSize)));

        avatarEditText.setText(String.valueOf(avatarsLimitMb));
        mediaEditText.setText(String.valueOf(mediaLimitMb));
        chatsEditText.setText(String.valueOf(chatsLimitMb));
        avatarTTLEditText.setText(String.valueOf(avatarsTTL / (24 * 60 * 60 * 1000)));
        mediaTTLEditText.setText(String.valueOf(mediaTTL / (24 * 60 * 60 * 1000)));

        avatarStoreCheckBox.setChecked(storeAvatars);
        mediaStoreCheckBox.setChecked(storeMedia);
        chatsStoreCheckBox.setChecked(storeChats);
    }

    private void loadValues() {
        avatarsLimitMb = sharedPreferences.getLong("avatarsLimit", 16);
        mediaLimitMb = sharedPreferences.getLong("mediaLimit", 32);
        chatsLimitMb = sharedPreferences.getLong("chatsLimit", 16);

        avatarsLimit = avatarsLimitMb * 1024 * 1024;
        mediaLimit = mediaLimitMb * 1024 * 1024;
        chatsLimit = chatsLimitMb * 1024 * 1024;

        avatarsTTL = sharedPreferences.getLong("avatarsTTL", 10) * 24 * 60 * 60 * 1000;
        mediaTTL = sharedPreferences.getLong("mediaTTL", 30) * 24 * 60 * 60 * 1000;

        storeAvatars = sharedPreferences.getBoolean("storeAvatars", true);
        storeChats = sharedPreferences.getBoolean("storeChats", true);
        storeMedia = sharedPreferences.getBoolean("storeMedia", true);

        CacheManager cm = App.getCacheManager();
        avatarsSize = cm.getAvatarsSize();
        mediaSize = cm.getMediaSize();
        chatsSize = cm.getChatsSize();
        storageSize = cm.getStorageSavedSize();
    }
}
