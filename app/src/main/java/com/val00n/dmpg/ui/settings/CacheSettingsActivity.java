package com.val00n.dmpg.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
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

    private static final long APPLY_DEBOUNCE_MS = 600L;
    private static final long MAX_LIMIT_MB = 1048576L;
    private static final long MAX_TTL_DAYS = 36500L;

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

    private boolean isUpdatingUi = false;
    private boolean hasPendingRefresh = false;
    private final Handler uiHandler = new Handler();

    private final Runnable refreshCacheRunnable = new Runnable() {
        public void run() {
            hasPendingRefresh = false;
            App.getCacheManager().refreshSettings(new Runnable() {
                public void run() {
                    if (!isFinishing()) {
                        loadSizes();
                        updateSummaryTexts();
                    }
                }
            });
        }
    };

    @Override
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
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isUpdatingUi) return;
                storeAvatars = isChecked;
                sharedPreferences.edit().putBoolean("storeAvatars", isChecked).commit();
                applyStoreFlagImmediately();
            }
        });

        mediaStoreCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isUpdatingUi) return;
                storeMedia = isChecked;
                sharedPreferences.edit().putBoolean("storeMedia", isChecked).commit();
                applyStoreFlagImmediately();
            }
        });

        chatsStoreCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isUpdatingUi) return;
                storeChats = isChecked;
                sharedPreferences.edit().putBoolean("storeChats", isChecked).commit();
                applyStoreFlagImmediately();
            }
        });

        avatarClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(CacheSettingsActivity.this)
                        .setTitle(getString(R.string.cache_settings_are_you_sure))
                        .setMessage(getString(R.string.cache_settings_wipe_avatar_cache))
                        .setNegativeButton(R.string.action_wipe, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                final ProgressDialog pd = new ProgressDialog(CacheSettingsActivity.this);
                                pd.setTitle(getString(R.string.cache_settings_clearing));
                                pd.setMessage(getString(R.string.cache_settings_wiping_avatar_cache));
                                pd.setCancelable(false);
                                pd.show();

                                App.getCacheManager().wipeFilesCache(new Runnable() {
                                    public void run() {
                                        if (!isFinishing()) {
                                            if (pd.isShowing()) {
                                                pd.dismiss();
                                            }
                                            Toast.makeText(CacheSettingsActivity.this, R.string.cache_settings_avatar_cleared, Toast.LENGTH_SHORT).show();
                                            loadSizes();
                                            updateSummaryTexts();
                                        }
                                    }
                                }, true);
                            }
                        })
                        .setPositiveButton(R.string.action_cancel, null)
                        .show();
            }
        });

        chatsClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(CacheSettingsActivity.this)
                        .setTitle(getString(R.string.cache_settings_are_you_sure))
                        .setMessage(getString(R.string.cache_settings_wipe_chats_cache))
                        .setNegativeButton(R.string.action_wipe, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                final ProgressDialog pd = new ProgressDialog(CacheSettingsActivity.this);
                                pd.setTitle(getString(R.string.cache_settings_clearing));
                                pd.setMessage(getString(R.string.cache_settings_wiping_chats_cache));
                                pd.setCancelable(false);
                                pd.show();

                                App.getCacheManager().wipeChatsCache(new Runnable() {
                                    public void run() {
                                        if (!isFinishing()) {
                                            if (pd.isShowing()) {
                                                pd.dismiss();
                                            }
                                            Toast.makeText(CacheSettingsActivity.this, R.string.cache_settings_chats_cleared, Toast.LENGTH_SHORT).show();
                                            loadSizes();
                                            updateSummaryTexts();
                                        }
                                    }
                                });
                            }
                        })
                        .setPositiveButton(R.string.action_cancel, null)
                        .show();
            }
        });

        mediaClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(CacheSettingsActivity.this)
                        .setTitle(getString(R.string.cache_settings_are_you_sure))
                        .setMessage(getString(R.string.cache_settings_wipe_media_cache))
                        .setNegativeButton(R.string.action_wipe, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                final ProgressDialog pd = new ProgressDialog(CacheSettingsActivity.this);
                                pd.setTitle(getString(R.string.cache_settings_clearing));
                                pd.setMessage(getString(R.string.cache_settings_wiping_media_cache));
                                pd.setCancelable(false);
                                pd.show();

                                App.getCacheManager().wipeFilesCache(new Runnable() {
                                    public void run() {
                                        if (!isFinishing()) {
                                            if (pd.isShowing()) {
                                                pd.dismiss();
                                            }
                                            Toast.makeText(CacheSettingsActivity.this, R.string.cache_settings_media_cleared, Toast.LENGTH_SHORT).show();
                                            loadSizes();
                                            updateSummaryTexts();
                                        }
                                    }
                                }, false);
                            }
                        })
                        .setPositiveButton(R.string.action_cancel, null)
                        .show();
            }
        });

        storageClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(CacheSettingsActivity.this)
                        .setTitle(getString(R.string.cache_settings_are_you_sure))
                        .setMessage(getString(R.string.cache_settings_wipe_storage_cache))
                        .setNegativeButton(R.string.action_wipe, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                FileHelper.deleteDirectory(false);
                                Toast.makeText(CacheSettingsActivity.this, R.string.cache_settings_storage_cleared, Toast.LENGTH_SHORT).show();
                                loadSizes();
                                updateSummaryTexts();
                            }
                        })
                        .setPositiveButton(R.string.action_cancel, null)
                        .show();
            }
        });

        avatarEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String s) {
                if (isUpdatingUi) return;
                try {
                    long val = clamp(Long.parseLong(s), 0L, MAX_LIMIT_MB);
                    avatarsLimitMb = val;
                    avatarsLimit = val * 1024L * 1024L;
                    sharedPreferences.edit().putLong("avatarsLimit", val).commit();
                    updateSummaryTexts();
                    scheduleCacheRefresh();
                } catch (NumberFormatException ignored) {}
            }
        });

        mediaEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String s) {
                if (isUpdatingUi) return;
                try {
                    long val = clamp(Long.parseLong(s), 0L, MAX_LIMIT_MB);
                    mediaLimitMb = val;
                    mediaLimit = val * 1024L * 1024L;
                    sharedPreferences.edit().putLong("mediaLimit", val).commit();
                    updateSummaryTexts();
                    scheduleCacheRefresh();
                } catch (NumberFormatException ignored) {}
            }
        });

        chatsEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String s) {
                if (isUpdatingUi) return;
                try {
                    long val = clamp(Long.parseLong(s), 0L, MAX_LIMIT_MB);
                    chatsLimitMb = val;
                    chatsLimit = val * 1024L * 1024L;
                    sharedPreferences.edit().putLong("chatsLimit", val).commit();
                    updateSummaryTexts();
                    scheduleCacheRefresh();
                } catch (NumberFormatException ignored) {}
            }
        });

        avatarTTLEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String s) {
                if (isUpdatingUi) return;
                try {
                    long days = clamp(Long.parseLong(s), 1L, MAX_TTL_DAYS);
                    avatarsTTL = days * 24L * 60L * 60L * 1000L;
                    sharedPreferences.edit().putLong("avatarsTTL", days).commit();
                    scheduleCacheRefresh();
                } catch (NumberFormatException ignored) {}
            }
        });

        mediaTTLEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String s) {
                if (isUpdatingUi) return;
                try {
                    long days = clamp(Long.parseLong(s), 1L, MAX_TTL_DAYS);
                    mediaTTL = days * 24L * 60L * 60L * 1000L;
                    sharedPreferences.edit().putLong("mediaTTL", days).commit();
                    scheduleCacheRefresh();
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        flushPendingCacheRefresh();
    }

    private void applyStoreFlagImmediately() {
        uiHandler.removeCallbacks(refreshCacheRunnable);
        hasPendingRefresh = false;
        App.getCacheManager().refreshSettings(new Runnable() {
            public void run() {
                if (!isFinishing()) {
                    loadSizes();
                    updateSummaryTexts();
                }
            }
        });
    }

    private void scheduleCacheRefresh() {
        hasPendingRefresh = true;
        uiHandler.removeCallbacks(refreshCacheRunnable);
        uiHandler.postDelayed(refreshCacheRunnable, APPLY_DEBOUNCE_MS);
    }

    private void flushPendingCacheRefresh() {
        if (hasPendingRefresh) {
            uiHandler.removeCallbacks(refreshCacheRunnable);
            hasPendingRefresh = false;
            App.getCacheManager().refreshSettings();
        }
    }

    private static long clamp(long val, long min, long max) {
        if (val < min) return min;
        if (val > max) return max;
        return val;
    }

    private void updateSummaryTexts() {
        globalSize = avatarsSize + mediaSize + chatsSize;
        globalLimit = avatarsLimit + mediaLimit + chatsLimit;

        globalText.setText(getString(R.string.cache_settings_global, FileHelper.formatSize(globalSize), FileHelper.formatSize(globalLimit)));
        avatarsText.setText(getString(R.string.cache_settings_avatars, FileHelper.formatSize(avatarsSize), FileHelper.formatSize(avatarsLimit)));
        mediaText.setText(getString(R.string.cache_settings_media, FileHelper.formatSize(mediaSize), FileHelper.formatSize(mediaLimit)));
        chatsText.setText(getString(R.string.cache_settings_chats, FileHelper.formatSize(chatsSize), FileHelper.formatSize(chatsLimit)));
        storageText.setText(getString(R.string.cache_settings_storage, FileHelper.formatSize(storageSize)));
    }

    private void showValues() {
        isUpdatingUi = true;
        try {
            updateSummaryTexts();

            avatarEditText.setText(String.valueOf(avatarsLimitMb));
            mediaEditText.setText(String.valueOf(mediaLimitMb));
            chatsEditText.setText(String.valueOf(chatsLimitMb));
            avatarTTLEditText.setText(String.valueOf(avatarsTTL / (24L * 60L * 60L * 1000L)));
            mediaTTLEditText.setText(String.valueOf(mediaTTL / (24L * 60L * 60L * 1000L)));

            avatarStoreCheckBox.setChecked(storeAvatars);
            mediaStoreCheckBox.setChecked(storeMedia);
            chatsStoreCheckBox.setChecked(storeChats);
        } finally {
            isUpdatingUi = false;
        }
    }

    private void loadSizes() {
        CacheManager cm = App.getCacheManager();
        avatarsSize = cm.getAvatarsSize();
        mediaSize = cm.getMediaSize();
        chatsSize = cm.getChatsSize();
        storageSize = cm.getStorageSavedSize();
    }

    private void loadValues() {
        avatarsLimitMb = clamp(sharedPreferences.getLong("avatarsLimit", 16), 0L, MAX_LIMIT_MB);
        mediaLimitMb = clamp(sharedPreferences.getLong("mediaLimit", 32), 0L, MAX_LIMIT_MB);
        chatsLimitMb = clamp(sharedPreferences.getLong("chatsLimit", 16), 0L, MAX_LIMIT_MB);

        avatarsLimit = avatarsLimitMb * 1024L * 1024L;
        mediaLimit = mediaLimitMb * 1024L * 1024L;
        chatsLimit = chatsLimitMb * 1024L * 1024L;

        avatarsTTL = clamp(sharedPreferences.getLong("avatarsTTL", 10), 1L, MAX_TTL_DAYS) * 24L * 60L * 60L * 1000L;
        mediaTTL = clamp(sharedPreferences.getLong("mediaTTL", 30), 1L, MAX_TTL_DAYS) * 24L * 60L * 60L * 1000L;

        storeAvatars = sharedPreferences.getBoolean("storeAvatars", true);
        storeChats = sharedPreferences.getBoolean("storeChats", true);
        storeMedia = sharedPreferences.getBoolean("storeMedia", true);

        loadSizes();
    }
}