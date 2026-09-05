package com.val00n.dmpg.ui.main;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
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

import java.util.Collections;
import static android.content.ContentValues.TAG;

public class MainActivity extends Activity {

    private ProgressBar progressBar;
    private ListView dialogsListView;
    private DialogsAdapter dialogsAdapter;
    private ImageButton searchButton;
    private ImageButton settingsButton;
    private Button loadMoreButton;

    private MPGramService mpgService;
    private Instance instance;
    private String userToken;
    private DialogResponse dialogs;
    private int loadedDialogs;
    private boolean forwardMode;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mpgService = App.getMPGramService();
        instance = App.getInstanceManager().getSelectedInstance();
        userToken = App.getSessionManager().loadSession(instance.baseURL);

        progressBar = (ProgressBar) findViewById(R.id.mainProgressBar);
        dialogsListView = (ListView) findViewById(R.id.mainDialogsListView);
        searchButton = (ImageButton) findViewById(R.id.mainSearchButton);
        settingsButton = (ImageButton) findViewById(R.id.mainSettingsButton);

        forwardMode = getIntent().getBooleanExtra("FORWARD_MODE", false);
        if (forwardMode) {
            searchButton.setVisibility(View.GONE);
            settingsButton.setVisibility(View.GONE);
        }

        dialogsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (forwardMode) {
                    Intent intent = new Intent();
                    intent.putExtra("ID", dialogs.dialogs.get(position).id);
                    intent.putExtra("NAME", dialogs.dialogs.get(position).getDisplayName());
                    setResult(RESULT_OK, intent);
                    finish();
                } else {
                    navigateToChat(dialogs.dialogs.get(position).id, dialogs.dialogs.get(position).getDisplayName());
                }
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToSettings();
            }
        });
    }

    protected void onResume() {
        super.onResume();
        if (!instance.equals(App.getInstanceManager().getSelectedInstance())) {
            navigateToStartup();
        } else {
            addFooter();
            dialogsAdapter = null;
            loadDialogs(true, null, null, 0);
        }
    }

    private void loadDialogs(final boolean initial, final String offsetPeer, final String offsetId, final long offsetDate) {
        if (initial) {
            loadedDialogs = 0;
        } else {
            if (loadedDialogs >= dialogs.totalCount) {
                Toast.makeText(MainActivity.this, "it all was loaded", Toast.LENGTH_SHORT).show();
                return; // FIXME
            }

            loadMoreButton.setEnabled(false);
        }

        mpgService.getDialogs(offsetPeer, offsetId, offsetDate, userToken, new MPGramService.ApiCallback<DialogResponse>() {
            @Override
            public void onSuccess(final DialogResponse result) {
                loadedDialogs += result.dialogs.size();
                Collections.sort(result.dialogs, SortUtils.PINNED_FIRST);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (initial) {
                            dialogs = result;
                        } else {
                            dialogs.offsetPeer = result.offsetPeer;
                            dialogs.offsetId = result.offsetId;
                            dialogs.offsetDate = result.offsetDate;

                            for (Dialog dialogItem : result.dialogs) {
                                dialogs.dialogs.add(dialogItem);
                            }

                            loadMoreButton.setEnabled(true);
                        }

                        showDialogs();
                    }
                });
            }

            @Override
            public void onError(final Exception error) {
                Log.d(TAG, "MAIN ACT onError: " + error.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this, "Failed to load dialogs " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void addFooter() {
        if (loadMoreButton != null) return;

        View footer = getLayoutInflater().inflate(R.layout.dialogs_footer, dialogsListView, false);
        dialogsListView.addFooterView(footer);

        loadMoreButton = (Button) footer.findViewById(R.id.dialogsFooterLoadButton);
        loadMoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadDialogs(false, dialogs.offsetPeer, dialogs.offsetId, dialogs.offsetDate);
            }
        });

    }

    private void showDialogs() {
        if (dialogsAdapter == null) {
            dialogsAdapter = new DialogsAdapter(MainActivity.this, dialogs.dialogs, userToken);
            dialogsListView.setAdapter(dialogsAdapter);
        } else {
            dialogsAdapter.notifyDataSetChanged();
        }

        progressBar.setVisibility(View.GONE);
    }

    private void navigateToChat(String id, String name) {
        Intent intent = new Intent(this, ChatActivity.class);

        intent.putExtra("ID", id);
        intent.putExtra("NAME", name);

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
