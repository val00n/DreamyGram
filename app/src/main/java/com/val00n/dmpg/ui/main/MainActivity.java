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

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mpgService = new MPGramService();
        instance = App.getInstanceManager().getSelectedInstance();
        userToken = App.getSessionManager().loadSession(instance.baseURL);

        progressBar = (ProgressBar) findViewById(R.id.mainProgressBar);
        dialogsListView = (ListView) findViewById(R.id.mainDialogsListView);
        searchButton = (ImageButton) findViewById(R.id.mainSearchButton);
        settingsButton = (ImageButton) findViewById(R.id.mainSettingsButton);

        dialogsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(MainActivity.this, "id: " + dialogs.dialogs.get(position).id, Toast.LENGTH_SHORT).show();
                navigateToChat(dialogs.dialogs.get(position).id, dialogs.dialogs.get(position).getDisplayName());
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToSettings();
            }
        });

        getDialogs();
        addFooter();
    }

    protected void onResume() {
        super.onResume();
        if (!instance.equals(App.getInstanceManager().getSelectedInstance())) {
            navigateToStartup();
        }
    }

    private void getDialogs() {
        loadedDialogs = 0;
        mpgService.getDialogs(userToken, new MPGramService.ApiCallback<DialogResponse>() {
            @Override
            public void onSuccess(final DialogResponse result) {
                dialogs = result;
                loadedDialogs += result.dialogs.size();
                showDialogs();
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

    private void addDialogs() {
        if (loadedDialogs >= dialogs.count) {
            Toast.makeText(MainActivity.this, "it all was loaded", Toast.LENGTH_SHORT).show();
            return; // FIXME
        }

        loadMoreButton.setEnabled(false);
        mpgService.getDialogs(dialogs.offsetPeer, dialogs.offsetId, dialogs.offsetDate, userToken, new MPGramService.ApiCallback<DialogResponse>() {
            @Override
            public void onSuccess(final DialogResponse result) {
                loadedDialogs += result.dialogs.size();

                dialogs.offsetPeer = result.offsetPeer;
                dialogs.offsetId = result.offsetId;
                dialogs.offsetDate = result.offsetDate;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        for (Dialog dialogItem : result.dialogs) {
                            dialogs.dialogs.add(dialogItem);
                        }

                        showDialogs();
                        Toast.makeText(MainActivity.this, "Successfully loaded", Toast.LENGTH_SHORT).show();
                        loadMoreButton.setEnabled(true);
                    }
                });
            }

            @Override
            public void onError(final Exception error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadMoreButton.setEnabled(true);
                        Toast.makeText(MainActivity.this, "failed load: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

                Log.d(TAG, "onError: " + error.getMessage());
            }
        });
    }

    private void addFooter() {
        View footer = getLayoutInflater().inflate(R.layout.dialogs_footer, dialogsListView, false);
        dialogsListView.addFooterView(footer);

        loadMoreButton = (Button) findViewById(R.id.dialogsFooterLoadButton);
        loadMoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addDialogs();
            }
        });
    }

    private void showDialogs() {
        Collections.sort(dialogs.dialogs, Dialog.PINNED_FIRST);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (dialogsAdapter == null) {
                    dialogsAdapter = new DialogsAdapter(MainActivity.this, dialogs.dialogs, userToken);
                    dialogsListView.setAdapter(dialogsAdapter);
                } else {
                    dialogsAdapter.notifyDataSetChanged();
                }

                progressBar.setVisibility(View.GONE);
            }
        });
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
