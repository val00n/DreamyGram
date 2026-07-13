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
import com.val00n.dmpg.model.DialogItem;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.ui.settings.SettingsActivity;
import com.val00n.dmpg.ui.startup.StartupActivity;

import java.util.ArrayList;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity {

    private ProgressBar progressBar;
    private ListView dialogsListView;
    private DialogsAdapter dialogsAdapter;
    private ImageButton searchButton;
    private ImageButton settingsButton;

    private MPGramService mpgService;
    private Instance instance;
    private String userToken;
    private ArrayList<DialogItem> dialogItems;

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
                Toast.makeText(MainActivity.this, "id: " + dialogItems.get(position).id, Toast.LENGTH_SHORT).show();
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToSettings();
            }
        });

        getDialogs();
    }

    protected void onResume() {
        super.onResume();
        if (!instance.equals(App.getInstanceManager().getSelectedInstance())) {
            navigateToStartup();
        }
    }

    private void getDialogs() {
        mpgService.getDialogs(userToken, new MPGramService.ApiCallback<ArrayList<DialogItem>>() {
            @Override
            public void onSuccess(final ArrayList<DialogItem> result) {
                showDialogs(result);
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

    private void navigateToSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void navigateToStartup() {
        Intent intent = new Intent(this, StartupActivity.class);
        //intent.putExtra("CHECK", true);
        startActivity(intent);
        finish();
    }

    private void showDialogs(final ArrayList<DialogItem> dialogItems) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.dialogItems = dialogItems;
                dialogsAdapter = new DialogsAdapter(MainActivity.this, dialogItems, userToken);
                dialogsListView.setAdapter(dialogsAdapter);
                progressBar.setVisibility(View.GONE);
            }
        });
    }
}
