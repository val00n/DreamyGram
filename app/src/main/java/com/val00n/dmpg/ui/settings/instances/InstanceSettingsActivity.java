package com.val00n.dmpg.ui.settings.instances;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.AuthResult;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.model.LoginResponse;
import com.val00n.dmpg.network.RestClient;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.services.SessionManager;
import com.val00n.dmpg.ui.main.MainActivity;

import java.util.ArrayList;

import static android.content.ContentValues.TAG;

public class InstanceSettingsActivity extends Activity {

    // UI
    private ListView instancesList;
    private InstancesAdapter instancesAdapter;
    private Button checkButton;
    private Button addButton;
    private ArrayList<Instance> instances;
    // Others
    private MPGramService mpgService;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.instance_settings);

        instancesList = (ListView) findViewById(R.id.instanceSettingsInstancesList);
        addButton = (Button) findViewById(R.id.instanceSettingsAddButton);
        checkButton = (Button) findViewById(R.id.instanceSettingsCheckButton);

        instancesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                App.getInstanceManager().selectInstance(position);
                getInstances();
            }
        });

        instancesList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                new AlertDialog.Builder(InstanceSettingsActivity.this)
                        .setTitle("Remove instance?")
                        .setMessage("Are you sure?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                App.getInstanceManager().deleteInstance(position);
                                getInstances();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            }
        });

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addInstance();
            }
        });

        checkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkInstances();
            }
        });

        getInstances();
    }

    protected void onResume() {
        super.onResume();
        getInstances();
    }

    private void checkInstances() {
        mpgService = new MPGramService();
        instancesAdapter.resetStatus();

        for (int i = 0; i < instances.size(); i++) {
            Instance instance = instances.get(i);
            final int index = i;
            final String token;

            String savedSession = App.getSessionManager().loadSession(instance.baseURL);
            if (savedSession != null) {
                token = savedSession;
            } else {
                token = "session_test"; // it is stupid lol
            }

            mpgService.checkAuth(instance, token, new MPGramService.ApiCallback<AuthResult>() {
                @Override
                public void onSuccess(final AuthResult response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response.isLogged) {
                                instancesAdapter.updateItem(index, response.ping, 1);
                            } else if (token.equals("test")) {
                                instancesAdapter.updateItem(index, response.ping, 2);
                            } else {
                                instancesAdapter.updateItem(index, response.ping, -1);
                            }
                        }
                    });
                }

                @Override
                public void onError(final Exception error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            instancesAdapter.updateItem(index, -1, -2);
                        }
                    });
                }
            });
        }
    }

    private void getInstances() {
        instances = App.getInstanceManager().getInstances();
        showInstances(instances);
    }

    private void addInstance() {
        Intent intent = new Intent(this, InstanceEditActivity.class);
        startActivity(intent);
    }

    private void showInstances(final ArrayList<Instance> instances) {
        if (instancesAdapter == null) {
            instancesAdapter = new InstancesAdapter(this, instances);
            instancesList.setAdapter(instancesAdapter);
        } else if (instances.size() != instancesAdapter.getCount()) {
            instancesAdapter = new InstancesAdapter(this, instances);
            instancesList.setAdapter(instancesAdapter);
        } else {
            instancesAdapter.notifyDataSetChanged();
        }
    }
}
