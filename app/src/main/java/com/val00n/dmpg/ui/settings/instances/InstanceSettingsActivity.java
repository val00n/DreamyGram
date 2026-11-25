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
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.model.AuthResponse;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.services.SessionManager;
import com.val00n.dmpg.ui.StartupActivity;
import java.util.ArrayList;
import static android.content.ContentValues.TAG;

public class InstanceSettingsActivity extends Activity {

    private ListView instancesList;
    private InstancesAdapter instancesAdapter;
    private Button checkButton;
    private Button addButton;
    private Button logoutButton;
    private ArrayList<Instance> instances;

    private MPGramService mpgService;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.instance_settings);

        mpgService = App.getMPGramService();

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
                        .setTitle(R.string.instance_settings_remove_title)
                        .setMessage(R.string.instance_settings_remove_message)
                        .setPositiveButton(R.string.action_delete, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                App.getInstanceManager().deleteInstance(position);
                                getInstances();
                            }
                        })
                        .setNegativeButton(R.string.action_cancel, null)
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
                        .setTitle(R.string.instance_settings_logout_title)
                        .setMessage(R.string.instance_settings_logout_message)
                        .setPositiveButton(R.string.instance_settings_every_instance, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int i) {
                                new AlertDialog.Builder(InstanceSettingsActivity.this)
                                        .setTitle(R.string.instance_settings_logout_everywhere_title)
                                        .setMessage(R.string.instance_settings_logout_everywhere_message)
                                        .setPositiveButton(R.string.instance_settings_logout_button, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                            logoutEverywhere();
                                            }
                                        })
                                        .setNegativeButton(R.string.action_cancel, null)
                                        .show();
                            }
                        })
                        .setNeutralButton(R.string.instance_settings_current_instance, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int i) {
                                new AlertDialog.Builder(InstanceSettingsActivity.this)
                                        .setTitle(R.string.instance_settings_remove_message)
                                        .setMessage(R.string.instance_settings_logout_current_message)
                                        .setPositiveButton(R.string.instance_settings_logout_button, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                            logout();
                                            }
                                        })
                                        .setNegativeButton(R.string.action_cancel, null)
                                        .show();
                            }
                        })
                        .setNegativeButton(R.string.action_cancel, null)
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
            final Instance instance = instances.get(i);
            final int index = i;
            final String token;

            String savedSession = App.getSessionManager().loadSession(instance.baseURL);
            if (savedSession != null) {
                token = savedSession;
            } else {
                token = "session_test"; // it is stupid lol
            }

            mpgService.checkAuth(instance, token, new MPGramService.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(final Boolean response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mpgService.pingInstance(instance, new MPGramService.ApiCallback<Integer>() {
                                @Override
                                public void onSuccess(final Integer result) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (response) {
                                                instancesAdapter.updateItem(index, result, 1);
                                            } else if (token.equals("session_test")) {
                                                instancesAdapter.updateItem(index, result, 2);
                                            } else {
                                                instancesAdapter.updateItem(index, result, -1);
                                            }
                                        }
                                    });
                                }

                                @Override
                                public void onError(Exception error) {
                                    error.printStackTrace();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            instancesAdapter.updateItem(index, -1, -1);
                                        }
                                    });
                                }
                            });
                        }
                    });
                }

                @Override
                public void onError(final Exception error) {
                    error.printStackTrace();
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
        Instance instance = App.getInstanceManager().getSelectedInstance();
        if (instance == null) return;

        String token = App.getSessionManager().loadSession(instance.baseURL);

        if (token != null && token.length() > 0) {
            mpgService.logout(instance, token, null);
        }

        App.getSessionManager().clearSession(instance.baseURL);

        wipeAndNavigate(getString(R.string.instance_settings_logged_out));
    }

    private void logoutEverywhere() {
        SessionManager sm = App.getSessionManager();
        ArrayList<Instance> instances = App.getInstanceManager().getInstances();

        if (instances == null) return;

        ArrayList<Instance> toLogout = new ArrayList<Instance>();
        ArrayList<String> tokens = new ArrayList<String>();

        for (Instance instance : instances) {
            String token = sm.loadSession(instance.baseURL);
            if (token != null && token.length() > 0) {
                toLogout.add(instance);
                tokens.add(token);
            }
        }

        if (toLogout.isEmpty()) {
            Toast.makeText(this, R.string.instance_settings_no_active_sessions, Toast.LENGTH_SHORT).show();
            App.getCacheManager().wipeCache(null);
            return;
        }

        sm.clearAllSessions();

        for (int i = 0; i < toLogout.size(); i++) {
            mpgService.logout(toLogout.get(i), tokens.get(i), null);
        }

        wipeAndNavigate(getString(R.string.instance_settings_logged_out_everywhere));
    }

    private void wipeAndNavigate(final String message) {
        App.getCacheManager().wipeCache(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;

                Toast.makeText(InstanceSettingsActivity.this, message, Toast.LENGTH_LONG).show();
                navigateToStartup();
            }
        });
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
