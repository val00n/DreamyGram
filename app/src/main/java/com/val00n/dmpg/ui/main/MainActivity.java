package com.val00n.dmpg.ui.main;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.DialogItem;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.services.MPGramService;

import java.util.ArrayList;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity {

    private ProgressBar progressBar;
    private ListView dialogsListView;
    private DialogsAdapter dialogsAdapter;

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

        dialogsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Toast.makeText(MainActivity.this, "id: " + dialogItems.get(position).id, Toast.LENGTH_SHORT).show();
            }
        });

        getDialogs();
    }

    private void getDialogs() {
        mpgService.getDialogs(userToken, new MPGramService.ApiCallback<ArrayList<DialogItem>>() {
            @Override
            public void onSuccess(final ArrayList<DialogItem> result) {
                showDialogs(result);
            }

            @Override
            public void onError(Exception error) {
                Log.d(TAG, "MAIN ACT onError: " + error.getMessage());
            }
        });
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
