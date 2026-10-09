package com.val00n.dmpg.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.Dialog;
import com.val00n.dmpg.model.DialogResponse;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.ui.chat.ChatActivity;
import com.val00n.dmpg.ui.settings.SettingsActivity;
import com.val00n.dmpg.ui.startup.StartupActivity;
import com.val00n.dmpg.utils.SortUtils;

import java.util.ArrayList;
import java.util.Collections;
import static android.content.ContentValues.TAG;

public class MainActivity extends Activity {

    private ProgressBar progressBar;
    private ListView dialogsListView;
    private DialogsAdapter dialogsAdapter;
    private ImageButton searchButton;
    private ImageButton settingsButton;
    private Button loadMoreButton;
    private View footerView;

    private MPGramService mpgService;
    private DialogResponse dialogResponse;
    private Instance instance;
    private Handler dialogsUpdatesHandler;
    private Runnable dialogsUpdateTask;
    private String userToken;
    private int loadedDialogs;
    private boolean forwardMode;

    private static final int GET_DIALOG_LIMIT = 25; // TODO change this in settings
    private static final int UPDATE_POLL_MILLIS = 45000; // TODO change this in settings

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mpgService = App.getMPGramService();
        instance = App.getInstanceManager().getSelectedInstance();
        if (instance.baseURL != null) {
            userToken = App.getSessionManager().loadSession(instance.baseURL);
        }

        progressBar = (ProgressBar) findViewById(R.id.mainProgressBar);
        dialogsListView = (ListView) findViewById(R.id.mainDialogsListView);
        searchButton = (ImageButton) findViewById(R.id.mainSearchButton);
        settingsButton = (ImageButton) findViewById(R.id.mainSettingsButton);

        dialogsUpdatesHandler = new Handler();

        dialogsUpdateTask = new Runnable() {
            @Override
            public void run() {
                //refreshDialogs(loadedDialogs);
            }
        };

        forwardMode = getIntent().getBooleanExtra("FORWARD_MODE", false); //TODO: meh
        if (forwardMode) {
            searchButton.setVisibility(View.GONE);
            settingsButton.setVisibility(View.GONE);
        }

        dialogsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);

                if (!(item instanceof Dialog)) {
                    return;
                }

                Dialog dialog = (Dialog) item;

                if (forwardMode) {
                    Intent intent = new Intent();
                    intent.putExtra("ID", dialog.id);
                    intent.putExtra("NAME", dialog.getDisplayName());
                    setResult(RESULT_OK, intent);
                    finish();
                } else {
                    navigateToChat(dialog.id, dialog.getDisplayName(), dialog.readOut);
                }
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToSettings();
            }
        });

        addFooter();
        loadDialogs(true, GET_DIALOG_LIMIT, null, null, 0);
    }

    protected void onResume() {
        super.onResume();
        if (instance == null || !instance.equals(App.getInstanceManager().getSelectedInstance())) {
            navigateToStartup();
        }

        if (loadedDialogs == 0) return;
        refreshDialogs(loadedDialogs);
    }

    protected void onDestroy() {
        super.onDestroy();
        dialogsUpdatesHandler.removeCallbacks(dialogsUpdateTask);
    }

    protected void onPause() {
        super.onPause();
        dialogsUpdatesHandler.removeCallbacks(dialogsUpdateTask);
    }

    private void refreshDialogs(final int limit) {
        mpgService.getDialogs(limit, null, null, 0, userToken, new MPGramService.ApiCallback<DialogResponse>() {
            @Override
            public void onSuccess(final DialogResponse result) {
                Collections.sort(result.dialogs, SortUtils.PINNED_FIRST);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        applyDialogResponse(result);

                        loadMoreButton.setEnabled(true);
                        showDialogs();

                        dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, UPDATE_POLL_MILLIS);
                    }
                });
            }

            @Override
            public void onError(final Exception error) {
                Log.d(TAG, "MAIN ACT onError: " + error.getMessage());
                if (!App.isSessionDead(error.getMessage(), MainActivity.this)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            Log.d(TAG, "refreshDialogs: Failed to load dialogResponse" + error.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void loadDialogs(final boolean initial, final int limit, final String offsetPeer, final String offsetId, final long offsetDate) {
        if (initial) {
            loadedDialogs = 0;
        } else {
            loadMoreButton.setEnabled(false);
        }

        mpgService.getDialogs(limit, offsetPeer, offsetId, offsetDate, userToken, new MPGramService.ApiCallback<DialogResponse>() {
            @Override
            public void onSuccess(final DialogResponse result) {
                loadedDialogs += result.dialogs.size();
                Collections.sort(result.dialogs, SortUtils.PINNED_FIRST);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (initial) {
                            applyDialogResponse(result);
                            dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, UPDATE_POLL_MILLIS);
                        } else {
                            dialogResponse.offsetPeer = result.offsetPeer;
                            dialogResponse.offsetId = result.offsetId;
                            dialogResponse.offsetDate = result.offsetDate;

                            dialogResponse.dialogs.addAll(result.dialogs);

                            loadMoreButton.setEnabled(true);
                        }

                        if (result.dialogs.size() >= dialogResponse.totalCount || result.dialogs.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Everything is loaded", Toast.LENGTH_SHORT).show();
                            footerView.setVisibility(View.GONE);
                        } else {
                            footerView.setVisibility(View.VISIBLE);
                        }

                        showDialogs();
                    }
                });
            }

            @Override
            public void onError(final Exception error) {
                Log.d(TAG, "MAIN ACT onError: " + error.getMessage());
                if (!App.isSessionDead(error.getMessage(), MainActivity.this)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this, "Failed to load dialogResponse " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void applyDialogResponse(DialogResponse result) {
        if (result == null) {
            return;
        }

        if (dialogResponse == null || dialogResponse.dialogs == null) {
            dialogResponse = result;
            return;
        }

        if (result.dialogs != null && !result.dialogs.equals(dialogResponse.dialogs)) {
            dialogResponse.dialogs.clear();
            dialogResponse.dialogs.addAll(result.dialogs);
        }

        dialogResponse.offsetPeer = result.offsetPeer;
        dialogResponse.offsetId = result.offsetId;
        dialogResponse.offsetDate = result.offsetDate;
        dialogResponse.totalCount = result.totalCount;
    }

    private void addFooter() {
        if (loadMoreButton != null) return;

        footerView = getLayoutInflater().inflate(R.layout.dialogs_footer, dialogsListView, false);
        dialogsListView.addFooterView(footerView);

        loadMoreButton = (Button) footerView.findViewById(R.id.dialogsFooterLoadButton);
        loadMoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadDialogs(false, GET_DIALOG_LIMIT, dialogResponse.offsetPeer, dialogResponse.offsetId, dialogResponse.offsetDate);
            }
        });
    }

    private void showDialogs() {
        if (dialogsAdapter == null) {
            dialogsAdapter = new DialogsAdapter(getApplicationContext(), dialogResponse.dialogs, userToken);
            dialogsListView.setAdapter(dialogsAdapter);
        }

        dialogsAdapter.notifyDataSetChanged();

        progressBar.setVisibility(View.GONE);
    }

    private void navigateToChat(String id, String name, int readOut) {
        Intent intent = new Intent(this, ChatActivity.class);

        intent.putExtra("ID", id);
        intent.putExtra("NAME", name);
        intent.putExtra("READ_OUT", readOut);

        startActivity(intent);
    }

    private void navigateToSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void navigateToStartup() {
        Intent intent = new Intent(this, StartupActivity.class);
        startActivity(intent);
        finish();
    }
}
