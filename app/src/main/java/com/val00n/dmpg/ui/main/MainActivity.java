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
import android.widget.TextView;
import android.widget.Toast;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.dialog.Dialog;
import com.val00n.dmpg.model.dialog.DialogResponse;
import com.val00n.dmpg.model.dialog.DialogRow;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.model.Update;
import com.val00n.dmpg.model.User;
import com.val00n.dmpg.model.message.Message;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.ui.chat.ChatActivity;
import com.val00n.dmpg.ui.settings.SettingsActivity;
import com.val00n.dmpg.ui.StartupActivity;
import com.val00n.dmpg.ui.AvatarViewBinder;
import com.val00n.dmpg.utils.SortUtils;
import java.util.ArrayList;
import java.util.Collections;

import cc.nnproject.json.JSONObject;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity {

    private ProgressBar progressBar;
    private ListView dialogsListView;
    private DialogsAdapter dialogsAdapter;
    private ImageButton searchButton;
    private ImageButton settingsButton;
    private Button loadMoreButton;
    private View footerView;
    private TextView networkStatusText;

    private MPGramService mpgService;
    private DialogResponse dialogResponse;
    private Instance instance;
    private String userToken;
    private Handler dialogsUpdatesHandler;
    private Handler statusUpdateHandler;
    private Runnable statusUpdateTask;
    private Runnable dialogsUpdateTask;
    private String[] updatesFilter;
    private int loadedDialogs;
    private boolean forwardMode;
    volatile private boolean isLoading;
    volatile private boolean isPaused;
    volatile private boolean isPolling;
    volatile private boolean isActivityDied;
    private volatile boolean cancelInProgress = false;

    private ArrayList<Update> updates;
    private String updateLimit = "200";
    private String updateOffset = "-1";
    private int updateFails = 0;

    private App.NetworkListener networkListener;
    private boolean hasCachedDialogs = false;
    private boolean startedOnce = false;

    private static final int OFFLINE_CHECK_MILLIS = 5000;
    private static final int GET_DIALOG_LIMIT = 25; // TODO change this in settings
    private static final int UPDATE_TIMEOUT_SECS = 30;
    private static final int UPDATE_POLL_MILLIS = 3000; // TODO change this in settings
    private static final int STATUS_UPDATE_MILLIS = 5000;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mpgService = App.getMPGramService();
        instance = App.getInstanceManager().getSelectedInstance();
        if (instance.baseURL != null) {
            userToken = App.getSessionManager().loadSession(instance.baseURL);

            if (userToken != null) {
                final String reqBase = instance.baseURL;
                mpgService.getSelf(userToken, new MPGramService.ApiCallback<User>() {
                    @Override
                    public void onSuccess(User result) {
                        if (result != null && reqBase.equals(App.getInstanceManager().getSelectedInstanceBaseUrl())) {
                            App.selfId = result.id;

                            final String selfId = result.id;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (dialogsAdapter != null) {
                                        dialogsAdapter.setSelfId(selfId);
                                        dialogsAdapter.notifyDataSetChanged();
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        error.printStackTrace();
                    }
                });
            }
        }

        progressBar = (ProgressBar) findViewById(R.id.mainProgressBar);
        dialogsListView = (ListView) findViewById(R.id.mainDialogsListView);
        searchButton = (ImageButton) findViewById(R.id.mainSearchButton);
        settingsButton = (ImageButton) findViewById(R.id.mainSettingsButton);
        networkStatusText = (TextView) findViewById(R.id.mainNetworkStatusText);

        forwardMode = getIntent().getBooleanExtra("FORWARD_MODE", false); //TODO: meh
        if (forwardMode) {
            searchButton.setVisibility(View.GONE);
            settingsButton.setVisibility(View.GONE);
        }

        dialogsUpdatesHandler = new Handler();
        statusUpdateHandler = new Handler();

        updatesFilter = new String[] { "updateNewChannelMessage",  "updateNewMessage", "updateEditChannelMessage",
                                       "updateEditMessage", "updateDeleteChannelMessages", "updateDeleteMessages",
                                       "updateUserTyping", "updateChatUserTyping", "updateChannelUserTyping",
                                       "updateReadHistoryOutbox", "updateReadChannelOutbox"};

        dialogsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);

                if (!(item instanceof DialogRow)) {
                    return;
                }

                Dialog dialog = ((DialogRow) item).dialog;

                if (forwardMode) {
                    Intent intent = new Intent();
                    intent.putExtra("ID", dialog.peerId);
                    intent.putExtra("NAME", dialog.getDisplayName(App.selfId));
                    setResult(RESULT_OK, intent);
                    finish();
                } else {
                    if (dialogsAdapter != null) {
                        dialog.unread = 0;
                        dialog.mentions = 0;
                        dialogsAdapter.notifyDataSetChanged();
                    }
                    navigateToChat(dialog.peerId, dialog.getDisplayName(App.selfId), dialog.readOut);
                }
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToSettings();
            }
        });

        dialogsUpdateTask = new Runnable() {
            @Override
            public void run() {
                if (isActivityDied || isPolling || isPaused) return;

                if (!App.isNetworkAvailable()) {
                    isPolling = false;
                    dialogsUpdatesHandler.postDelayed(this, OFFLINE_CHECK_MILLIS);
                    return;
                }

                isPolling = true;
                mpgService.getUpdates(updatesFilter, updateLimit, updateOffset, null, false, UPDATE_TIMEOUT_SECS, userToken, new MPGramService.ApiCallback<ArrayList<Update>>() {
                    @Override
                    public void onSuccess(ArrayList<Update> result) {
                        if (isActivityDied) return;

                        updates = result;
                        if (updates.size() > 0) {
                            updateOffset = Integer.toString(updates.get(updates.size() - 1).update_id);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    update(updates);
                                }
                            });
                        }

                        updateFails = 0;
                        isPolling = false;
                        if (!isPaused) dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, UPDATE_POLL_MILLIS);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isActivityDied) return;
                        Log.d(TAG, "UPDATE FAILED: " + error.getMessage());
                        isPolling = false;

                        if (!App.isSessionDead(error.getMessage(), MainActivity.this)) {
                            if (updateFails > 10) {
                                mpgService.getLastUpdate(userToken, UPDATE_TIMEOUT_SECS, new MPGramService.ApiCallback<Update>() {
                                    @Override
                                    public void onSuccess(Update result) {
                                        if (result != null) {
                                            updateOffset = Integer.toString(result.update_id);
                                        } else {
                                            updateOffset = "-1";
                                        }

                                        if (!isPaused) dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, UPDATE_POLL_MILLIS);
                                    }

                                    @Override
                                    public void onError(Exception error) {
                                        error.printStackTrace();
                                        if (!App.isSessionDead(error.getMessage(), MainActivity.this)) {
                                            if (!isPaused) dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, UPDATE_POLL_MILLIS);
                                        }
                                    }
                                });

                                updateFails = 0;
                            } else {
                                updateFails++;
                                if (!isPaused) dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, UPDATE_POLL_MILLIS * updateFails);
                            }
                        }
                    }
                });
            }
        };

        statusUpdateTask = new Runnable() {
            @Override
            public void run() {
                if (dialogResponse != null && dialogResponse.dialogs != null) {
                    for (int i = 0; i < dialogResponse.dialogs.size(); i++) {
                        Dialog d = dialogResponse.dialogs.get(i);
                        if (d != null && d.isTyping) {
                            d.isTyping = false;
                        }
                    }
                }

                if (dialogsAdapter != null) dialogsAdapter.notifyDataSetChanged();
            }
        };

        networkListener = new App.NetworkListener() {
            @Override
            public void onNetworkChanged(final boolean available) {
                if (isActivityDied) return;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityDied) return;

                        if (available) {
                            onNetworkRestored();
                        } else {
                            onNetworkLost();
                        }
                    }
                });
            }
        };

        addFooter();
        loadDialogs(true, GET_DIALOG_LIMIT, null, null, 0);
    }

    protected void onResume() {
        super.onResume();
        if (instance == null || !instance.equals(App.getInstanceManager().getSelectedInstance())) {
            App.selfId = null;
            navigateToStartup();
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        isActivityDied = true;
        dialogsUpdatesHandler.removeCallbacks(dialogsUpdateTask);
        statusUpdateHandler.removeCallbacks(statusUpdateTask);

        mpgService.cancelUpdates(userToken, new MPGramService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                Log.d(TAG, "onSuccess: canceled updates: " + result);
            }

            @Override
            public void onError(Exception error) {
                error.printStackTrace();
                Log.d(TAG, "onError: failed to cancel updates: " + error.getMessage());
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();

        isPaused = true;

        App.removeNetworkListener(networkListener);

        dialogsUpdatesHandler.removeCallbacks(dialogsUpdateTask);
        statusUpdateHandler.removeCallbacks(statusUpdateTask);

        if (!App.isNetworkAvailable()) {
            return;
        }

        if (!cancelInProgress) {
            cancelInProgress = true;

            mpgService.cancelUpdates(userToken, new MPGramService.ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    cancelInProgress = false;
                }

                @Override
                public void onError(Exception error) {
                    cancelInProgress = false;
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        isPaused = false;

        App.addNetworkListener(networkListener);

        if (App.isNetworkAvailable()) {
            if (startedOnce
                    && (dialogResponse == null
                    || dialogResponse.dialogs == null
                    || dialogResponse.dialogs.isEmpty())
                    && !isLoading) {
                loadDialogs(true, GET_DIALOG_LIMIT, null, null, 0);
            }

            tryStartPolling();
        } else {
            onNetworkLost();
        }

        startedOnce = true;
    }

    private void tryStartPolling() {
        dialogsUpdatesHandler.removeCallbacks(dialogsUpdateTask);

        if (isPaused || cancelInProgress || isPolling || dialogResponse == null || !App.isNetworkAvailable()) {
            return;
        }

        dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, UPDATE_POLL_MILLIS);
    }

    private void update(ArrayList<Update> u) {
        if (u == null) return;
        boolean needRefresh = false;
        for (final Update upd : u) {
            if (upd == null || upd.type == null) continue;
            switch (upd.type) {
                case "updateEditChannelMessage":
                case "updateEditMessage":
                case "updateNewChannelMessage":
                case "updateNewMessage":
                    if (upd.msg != null) {
                        String peerId = upd.getPeerId();
                        if (peerId == null) break;
                        if (dialogResponse == null || dialogResponse.dialogs == null || dialogsAdapter == null) break;

                        boolean found = false;
                        boolean isEdit = upd.type.contains("Edit");
                        for (int i = 0; i < dialogResponse.dialogs.size(); i++) {
                            Dialog d = dialogResponse.dialogs.get(i);
                            if (d != null && peerId.equals(d.peerId)) {
                                if (d.msg != null && d.msg.id == upd.msg.id) {
                                    d.isTyping = false;
                                    found = true;
                                    break;
                                }
                                d.msg = upd.msg;
                                if (!isEdit && !upd.msg.out) d.unread++;
                                d.isTyping = false;
                                if (i != 0 && !isEdit) {
                                    dialogResponse.dialogs.remove(i);
                                    dialogResponse.dialogs.add(0, d);
                                    Collections.sort(dialogResponse.dialogs, SortUtils.PINNED_FIRST);
                                }
                                found = true;
                                break;
                            }
                        }

                        if (!found && !isEdit) needRefresh = true;
                    }
                    break;
                case "updateDeleteChannelMessages":
                case "updateDeleteMessages":
                    String delPeer = upd.getPeerId();
                    if (delPeer == null) break;
                    if (dialogResponse == null || dialogResponse.dialogs == null) break;
                    for (int i = 0; i < dialogResponse.dialogs.size(); i++) {
                        Dialog d = dialogResponse.dialogs.get(i);
                        if (d != null && delPeer.equals(d.peerId)) {
                            if (d.msg != null && upd.messages != null
                                    && upd.messages.contains(String.valueOf(d.msg.id))) {
                                needRefresh = true;
                            } else if (d.msg == null || upd.messages == null) {
                                needRefresh = true;
                            }
                            break;
                        }
                    }
                    break;
                case "updateUserTyping":
                case "updateChatUserTyping":
                case "updateChannelUserTyping":
                    String typingPeer = upd.getPeerId();
                    if (typingPeer == null) break;
                    if (dialogResponse == null || dialogResponse.dialogs == null || dialogsAdapter == null) break;

                    String typingAuthor = upd.user_id != null ? upd.user_id : upd.from_id;
                    if (typingAuthor != null && typingAuthor.equals(App.selfId)) break;

                    boolean isCancel = upd.action != null && upd.action.has("_") && "sendMessageCancelAction".equals(upd.action.getString("_"));

                    if (isCancel) {
                        for (int i = 0; i < dialogResponse.dialogs.size(); i++) {
                            Dialog d = dialogResponse.dialogs.get(i);
                            if (d != null && typingPeer.equals(d.peerId) && d.isTyping) {
                                d.isTyping = false;
                                break;
                            }
                        }
                        break;
                    } else {
                        for (int i = 0; i < dialogResponse.dialogs.size(); i++) {
                            Dialog d = dialogResponse.dialogs.get(i);
                            if (d != null && typingPeer.equals(d.peerId)) {
                                d.isTyping = true;
                                break;
                            }
                        }
                        statusUpdateHandler.removeCallbacks(statusUpdateTask);
                        statusUpdateHandler.postDelayed(statusUpdateTask, STATUS_UPDATE_MILLIS);
                    }

                    break;
                case "updateReadHistoryOutbox":
                case "updateReadChannelOutbox":
                    String readPeer = upd.getPeerId();
                    if (readPeer == null) break;
                    if (dialogResponse == null || dialogResponse.dialogs == null || dialogsAdapter == null) break;
                    for (int i = 0; i < dialogResponse.dialogs.size(); i++) {
                        Dialog d = dialogResponse.dialogs.get(i);
                        if (d != null && readPeer.equals(d.peerId)) {
                            d.readOut = Math.max(d.readOut, upd.max_id);
                            if (d.msg != null && d.msg.out && (upd.max_id == 0 || d.msg.id <= upd.max_id))
                                d.msg.messageState = Message.MessageState.MESSAGE_WAS_READ;
                            break;
                        }
                    }

                    break;
            }
        }

        if (dialogsAdapter != null) dialogsAdapter.notifyDataSetChanged();

        if (needRefresh && !isLoading) {
            refreshDialogs(loadedDialogs > 0 ? loadedDialogs : GET_DIALOG_LIMIT);
        }
    }

    private void refreshDialogs(final int limit) {
        if (!App.isNetworkAvailable()) {
            isLoading = false;
            return;
        }

        isLoading = true;
        mpgService.getDialogs(limit, null, null, 0, userToken, false, new MPGramService.ApiCallback<DialogResponse>() {
            @Override
            public void onSuccess(final DialogResponse result) {
                Collections.sort(result.dialogs, SortUtils.PINNED_FIRST);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        applyDialogResponse(result);

                        loadedDialogs = dialogResponse.dialogs.size();

                        loadMoreButton.setEnabled(true);
                        showDialogs();
                        isLoading = false;
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
                            isLoading = false;
                        }
                    });
                }
            }
        });
    }

    private void loadDialogs(final boolean initial, final int limit, final String offsetPeer, final String offsetId, final long offsetDate) {
        isLoading = true;

        if (initial) {
            loadedDialogs = 0;
            hasCachedDialogs = false;

            String cachedJsonString = App.getCacheManager().loadDialogs();
            if (cachedJsonString != null) {
                try {
                    JSONObject cachedJson = (JSONObject) cc.nnproject.json.JSON.get(cachedJsonString);
                    DialogResponse dr = new DialogResponse(cachedJson);

                    if (dr != null && dr.dialogs != null) {
                        Collections.sort(dr.dialogs, SortUtils.PINNED_FIRST);
                        applyDialogResponse(dr);
                        showDialogs();
                        hasCachedDialogs = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to parse cached dialogs", e);
                }
            }

            if (!App.isNetworkAvailable()) {
                isLoading = false;
                progressBar.setVisibility(View.GONE);

                if (!hasCachedDialogs) {
                    Toast.makeText(MainActivity.this, "No network or cached dialogs", Toast.LENGTH_LONG).show();
                }

                return;
            }
        } else {
            loadMoreButton.setEnabled(false);

            if (!App.isNetworkAvailable()) {
                isLoading = false;
                loadMoreButton.setEnabled(true);

                Toast.makeText(MainActivity.this, "No network", Toast.LENGTH_SHORT).show();

                return;
            }
        }

        mpgService.getDialogs(limit, offsetPeer, offsetId, offsetDate, userToken, initial, new MPGramService.ApiCallback<DialogResponse>() {
            @Override
            public void onSuccess(final DialogResponse result) {
                loadedDialogs += result.dialogs.size();
                Collections.sort(result.dialogs, SortUtils.PINNED_FIRST);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (initial) {
                            applyDialogResponse(result);
                            hasCachedDialogs = true;

                            mpgService.getLastUpdate(userToken, UPDATE_TIMEOUT_SECS, new MPGramService.ApiCallback<Update>() {
                                @Override
                                public void onSuccess(Update result) {
                                    if (result != null) {
                                        updateOffset = Integer.toString(result.update_id);
                                    }
                                    updates = new ArrayList<Update>();

                                    if (!isPaused) dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, UPDATE_POLL_MILLIS);
                                }

                                @Override
                                public void onError(Exception error) {
                                    error.printStackTrace();
                                    if (!isPaused) dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, UPDATE_POLL_MILLIS);
                                }
                            });
                        } else {
                            dialogResponse.offsetPeer = result.offsetPeer;
                            dialogResponse.offsetId = result.offsetId;
                            dialogResponse.offsetDate = result.offsetDate;

                            dialogResponse.dialogs.addAll(result.dialogs);
                            Collections.sort(dialogResponse.dialogs, SortUtils.PINNED_FIRST);

                            loadMoreButton.setEnabled(true);
                        }

                        if (loadedDialogs >= dialogResponse.totalCount || result.dialogs.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Everything is loaded", Toast.LENGTH_SHORT).show();
                            footerView.setVisibility(View.GONE);
                        } else {
                            footerView.setVisibility(View.VISIBLE);
                        }

                        isLoading = false;
                        showDialogs();
                    }
                });
            }

            @Override
            public void onError(final Exception error) {
                Log.d(TAG, "MAIN ACT onError: " + error.getMessage());
                isLoading = false;
                if (!App.isSessionDead(error.getMessage(), MainActivity.this)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);

                            if (initial && !hasCachedDialogs) {
                                Toast.makeText(MainActivity.this, "Failed to load dialogResponse " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            } else if (!initial) {
                                loadMoreButton.setEnabled(true);

                                Toast.makeText(MainActivity.this, "Failed to load dialogs " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        });
    }

    private void applyReadStates() {
        if (dialogResponse == null || dialogResponse.dialogs == null) return;
        for (int i = 0; i < dialogResponse.dialogs.size(); i++) {
            Dialog d = dialogResponse.dialogs.get(i);
            if (d != null && d.msg != null && d.msg.out) {
                d.msg.messageState = (d.msg.id <= d.readOut)
                        ? Message.MessageState.MESSAGE_WAS_READ
                        : Message.MessageState.MESSAGE_SENT;
            }
        }
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
        applyReadStates();

        if (dialogsAdapter == null) {
            dialogsAdapter = new DialogsAdapter(dialogResponse.dialogs, App.selfId, new AvatarViewBinder(App.getCacheManager()));
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

    private void onNetworkLost() {
        dialogsUpdatesHandler.removeCallbacks(dialogsUpdateTask);
        statusUpdateHandler.removeCallbacks(statusUpdateTask);

        if (networkStatusText != null) {
            networkStatusText.setVisibility(View.VISIBLE);
        }
    }

    private void onNetworkRestored() {
        if (networkStatusText != null) {
            networkStatusText.setVisibility(View.GONE);
        }

        if (dialogResponse == null
                || dialogResponse.dialogs == null
                || dialogResponse.dialogs.isEmpty()) {
            if (!isLoading) {
                loadDialogs(true, GET_DIALOG_LIMIT, null, null, 0);
            }
        } else {
            refreshDialogs(loadedDialogs > 0 ? loadedDialogs : GET_DIALOG_LIMIT);
        }

        tryStartPolling();
    }
}
