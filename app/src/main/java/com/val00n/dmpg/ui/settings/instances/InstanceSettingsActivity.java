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
import android.widget.Toast;

import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.AuthResult;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.model.LoginResponse;
import com.val00n.dmpg.network.RestClient;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.services.SessionManager;
import com.val00n.dmpg.ui.main.MainActivity;
import com.val00n.dmpg.ui.startup.StartupActivity;

import java.util.ArrayList;

import static android.content.ContentValues.TAG;

public class InstanceSettingsActivity extends Activity {

    // UI
    private ListView instancesList;
    private InstancesAdapter instancesAdapter;
    private Button checkButton;
    private Button addButton;
    private Button logoutButton;
    private ArrayList<Instance> instances;
    // Others
    private MPGramService mpgService;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.instance_settings);

        mpgService = new MPGramService();

        instancesList = (ListView) findViewById(R.id.instanceSettingsInstancesList);
        addButton = (Button) findViewById(R.id.instanceSettingsAddButton);
        checkButton = (Button) findViewById(R.id.instanceSettingsCheckButton);
        logoutButton = (Button) findViewById(R.id.instanceSettingsLogoutButton);

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

        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(InstanceSettingsActivity.this)
                        .setTitle("Logout")
                        .setMessage("Please choose")
                        .setPositiveButton("Every instance", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int i) {
                                new AlertDialog.Builder(InstanceSettingsActivity.this)
                                        .setTitle("ARE YOU SURE?")
                                        .setMessage("Logout from every instance?")
                                        .setPositiveButton("Logout", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                            logoutEverywhere();
                                            }
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            }
                        })
                        .setNeutralButton("Current instance", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int i) {
                                new AlertDialog.Builder(InstanceSettingsActivity.this)
                                        .setTitle("Are you sure?")
                                        .setMessage("Logout from current instance?")
                                        .setPositiveButton("Logout", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                            logout();
                                            }
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        getInstances();
    }

    protected void onResume() {
        super.onResume();
        getInstances();
    }

    private void checkInstances() {
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
                            } else if (token.equals("session_test")) {
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

    private void logout() {
        mpgService.logout(App.getSessionManager().loadSession(App.getInstanceManager().getSelectedInstanceBaseUrl()), new MPGramService.ApiCallback<LoginResponse>() {
            @Override
            public void onSuccess(LoginResponse result) {
                if (result.getResponse().equals("1")) {
                    App.getSessionManager().clearSession(App.getInstanceManager().getSelectedInstanceBaseUrl());

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(InstanceSettingsActivity.this, "Successfully logged out", Toast.LENGTH_LONG).show();
                            navigateToStartup();
                        }
                    });
                } else {
                    onError(new Exception("Invalid res: " + result.getResponse()));
                }
            }

            @Override
            public void onError(final Exception error) {
                Log.d(TAG, "onError: " + error.getMessage());

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(InstanceSettingsActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void logoutEverywhere() {
        SessionManager sm = App.getSessionManager();
        ArrayList<Instance> instances = App.getInstanceManager().getInstances();
        final int[] remaining = {0};

        for (final Instance instance : instances) {
            String instanceUserToken = sm.loadSession(instance.baseURL);
            if (instanceUserToken != null && instanceUserToken.length() > 0) {
                remaining[0]++;
                mpgService.logout(instance, instanceUserToken, new MPGramService.ApiCallback<LoginResponse>() {
                    @Override
                    public void onSuccess(LoginResponse result) {
                        if (result.getResponse().equals("1")) {
                            App.getSessionManager().clearSession(instance.baseURL);
                        }
                        done();
                    }

                    @Override
                    public void onError(Exception error) {
                        Log.d(TAG, "onError: " + error.getMessage());
                        done();
                    }

                    private void done() {
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(InstanceSettingsActivity.this, "Successfully logged out", Toast.LENGTH_LONG).show();
                                    navigateToStartup();
                                }
                            });
                        }
                    }
                });
            }
        }

        if (remaining[0] == 0) {
            Toast.makeText(this, "No active sessions", Toast.LENGTH_SHORT).show();
            return;
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

    private void navigateToStartup() {
        Intent intent = new Intent(this, StartupActivity.class);
        intent.putExtra("SKIP", true);
        startActivity(intent);
        finish();
    }
}
