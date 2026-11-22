package com.val00n.dmpg.ui.main;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.val00n.dmpg.App;
import com.val00n.dmpg.BuildConfig;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.ForumTopic;
import com.val00n.dmpg.model.ForumTopicResponse;
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

    private LinearLayout dialogsRoot;
    private LinearLayout topicsRoot;
    private ProgressBar progressBar;
    private ListView dialogsListView;
    private ListView topicListView;
    private ArrayAdapter<String> topicsAdapter;
    private DialogsAdapter dialogsAdapter;
    private ImageButton searchButton;
    private ImageButton settingsButton;
    private ImageButton topicBackButton;
    private Button loadMoreButton;
    private View footerView;
    private TextView networkStatusText;
    private TextView topicTitleText;
    private ImageView topicChatAvatar;

    private MPGramService mpgService;
    private DialogResponse dialogResponse;
    private ForumTopicResponse forumTopicResponse;
    private Instance instance;
    private String userToken;
    private Handler dialogsUpdatesHandler;
    private Handler statusUpdateHandler;
    private Runnable statusUpdateTask;
    private Runnable dialogsUpdateTask;
    private AvatarViewBinder avatarViewBinder;
    private String[] updatesFilter;
    private int loadedDialogs;
    private final ArrayList<String> dialogsRefreshPeers = new ArrayList<String>();
    private boolean dialogsRefreshPending = false;
    private boolean forwardMode;
    private String displayedTopicsPeer;

    volatile private boolean isLoading;
    volatile private boolean isPaused;
    volatile private boolean isPolling;
    volatile private boolean isActivityDied;

    volatile private long pollToken = 0;
    volatile private boolean hasUpdateOffset = false;
    volatile private boolean initialSyncInProgress = false;

    private static final int MENU_REFRESH = 1;
    private static final int MENU_ABOUT = 2;

    private ArrayList<Update> updates;
    private String updateLimit = "200";
    private String updateOffset = "-1";
    private int updateFails = 0;

    private App.NetworkListener networkListener;
    private boolean hasCachedDialogs = false;

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
        mpgService.refreshInstance();

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

        dialogsRoot = (LinearLayout) findViewById(R.id.mainDialogsRoot);
        topicsRoot = (LinearLayout) findViewById(R.id.mainTopicsRoot);
        progressBar = (ProgressBar) findViewById(R.id.mainProgressBar);
        dialogsListView = (ListView) findViewById(R.id.mainDialogsListView);
        topicListView = (ListView) findViewById(R.id.mainTopicsListView);
        searchButton = (ImageButton) findViewById(R.id.mainSearchButton);
        settingsButton = (ImageButton) findViewById(R.id.mainSettingsButton);
        topicBackButton = (ImageButton) findViewById(R.id.mainTopicBackButton);
        networkStatusText = (TextView) findViewById(R.id.mainNetworkStatusText);
        topicTitleText = (TextView) findViewById(R.id.mainTopicTitleText);
        topicChatAvatar = (ImageView) findViewById(R.id.mainTopicChatAvatar);

        avatarViewBinder = new AvatarViewBinder(App.getCacheManager());

        if (getIntent().getBooleanExtra("SERVER_UNAVAILABLE", false)) {
            showServerUnavailable();
        }

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

        topicListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ForumTopic topic = forumTopicResponse.topics.get(position);
                if (topic != null) {
                    navigateToChat(forumTopicResponse.peer, topic.title, forumTopicResponse.readOut, topic.id);
                }
            }
        });

        dialogsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);

                if (!(item instanceof DialogRow)) {
                    return;
                }

                Dialog dialog = ((DialogRow) item).dialog;

                if (dialog.chat != null && dialog.chat.f) {
                    dialogsRoot.setVisibility(View.GONE);
                    topicsRoot.setVisibility(View.VISIBLE);

                    topicTitleText.setText(dialog.getDisplayName(App.selfId));

                    getTopics(dialog.peerId);

                    avatarViewBinder.bind(topicChatAvatar, dialog.peerId, dialog.hasAvatar());
                    return;
                }

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

        topicBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialogsRoot.setVisibility(View.VISIBLE);
                topicsRoot.setVisibility(View.GONE);
            }
        });

        dialogsUpdateTask = new Runnable() {
            @Override
            public void run() {
                final long currentToken = pollToken;
                if (isActivityDied || isPolling || isPaused || !mpgService.isPollTokenActive(currentToken)) return;

                if (!App.isNetworkAvailable()) {
                    isPolling = false;
                    dialogsUpdatesHandler.postDelayed(this, OFFLINE_CHECK_MILLIS);
                    return;
                }

                isPolling = true;
                mpgService.getUpdates(updatesFilter, updateLimit, updateOffset, null, false, UPDATE_TIMEOUT_SECS, userToken, new MPGramService.ApiCallback<ArrayList<Update>>() {
                    @Override
                    public void onSuccess(ArrayList<Update> result) {
                        isPolling = false;
                        if (isActivityDied || isPaused || !mpgService.isPollTokenActive(currentToken)) return;

                        updates = result;
                        if (updates != null && updates.size() > 0) {
                            updateOffset = Integer.toString(updates.get(updates.size() - 1).update_id);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isActivityDied && !isPaused) update(updates);
                                }
                            });
                        }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!isActivityDied && !isPaused) hideNetworkStatus();
                            }
                        });

                        updateFails = 0;
                        if (!isPaused && mpgService.isPollTokenActive(currentToken)) {
                            dialogsUpdatesHandler.removeCallbacks(dialogsUpdateTask);
                            dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, UPDATE_POLL_MILLIS);
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        isPolling = false;
                        if (error instanceof MPGramService.PollCancelledException) return;
                        if (isActivityDied || isPaused || !mpgService.isPollTokenActive(currentToken)) return;
                        Log.d(TAG, "UPDATE FAILED: " + error.getMessage());

                        if (!App.isSessionDead(error.getMessage(), MainActivity.this)) {
                            if (updateFails > 10) {
                                updateFails = 0;
                                syncLastUpdateAndStartPolling(currentToken);
                            } else {
                                updateFails++;
                                if (!isPaused && mpgService.isPollTokenActive(currentToken)) {
                                    dialogsUpdatesHandler.removeCallbacks(dialogsUpdateTask);
                                    dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, Math.min(UPDATE_POLL_MILLIS * updateFails, 20000));
                                }
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
            return;
        }

        String currentToken = App.getSessionManager().loadSession(instance.baseURL);
        if (currentToken == null || currentToken.length() == 0
                || userToken == null || !currentToken.equals(userToken)) {
            App.selfId = null;
            navigateToStartup();
            return;
        }

        isPaused = false;
        isPolling = false;
        pollToken = mpgService.beginPollSession(userToken);

        dialogsRefreshPeers.clear();
        dialogsRefreshPending = false;

        if (App.isNetworkAvailable()) {
            if ((dialogResponse == null || dialogResponse.dialogs == null || dialogResponse.dialogs.isEmpty()) && !isLoading) {
                loadDialogs(true, GET_DIALOG_LIMIT, null, null, 0);
            } else {
                tryStartPolling();
            }
        } else {
            onNetworkLost();
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        isActivityDied = true;
        dialogsUpdatesHandler.removeCallbacks(dialogsUpdateTask);
        statusUpdateHandler.removeCallbacks(statusUpdateTask);
    }

    @Override
    protected void onStop() {
        super.onStop();
        App.removeNetworkListener(networkListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        App.addNetworkListener(networkListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPaused = true;
        isPolling = false;
        dialogsUpdatesHandler.removeCallbacks(dialogsUpdateTask);
        statusUpdateHandler.removeCallbacks(statusUpdateTask);
        mpgService.endPollSession(pollToken, userToken);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem refresh = menu.add(0, MENU_REFRESH, 0, "Refresh");
        refresh.setIcon(android.R.drawable.ic_menu_rotate);
        MenuItem about = menu.add(0, MENU_ABOUT, 1, "About");
        about.setIcon(android.R.drawable.ic_menu_info_details);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case MENU_REFRESH:
                refreshDialogs(loadedDialogs);
                break;
            case MENU_ABOUT:
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("About")
                        .setMessage("DreamyGram " + BuildConfig.VERSION_NAME + "\n\nAndroid 1.0+ lightweight Telegram client. Powered by MPGram WEB API.\n\nval00n ;3")
                        .setNeutralButton("Ok", null)
                        .show();
                return true;
            default:
                return super.onOptionsItemSelected(menuItem);
        }

        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (topicsRoot.getVisibility() == View.VISIBLE) {
                topicsRoot.setVisibility(View.GONE);
                dialogsRoot.setVisibility(View.VISIBLE);
            }

            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private void tryStartPolling() {
        dialogsUpdatesHandler.removeCallbacks(dialogsUpdateTask);
        if (isPaused || isPolling || initialSyncInProgress || dialogResponse == null
                || !App.isNetworkAvailable() || !mpgService.isPollTokenActive(pollToken)) {
            return;
        }
        if (!hasUpdateOffset) {
            syncLastUpdateAndStartPolling(pollToken);
            return;
        }
        dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, UPDATE_POLL_MILLIS);
    }

    private void syncLastUpdateAndStartPolling(final long currentToken) {
        if (isActivityDied || isPaused || !mpgService.isPollTokenActive(currentToken) || !App.isNetworkAvailable()) return;
        initialSyncInProgress = true;
        mpgService.getLastUpdate(userToken, UPDATE_TIMEOUT_SECS, new MPGramService.ApiCallback<Update>() {
            @Override
            public void onSuccess(final Update result) {
                initialSyncInProgress = false;
                if (isActivityDied || isPaused || !mpgService.isPollTokenActive(currentToken)) return;
                updateOffset = result != null ? Integer.toString(result.update_id) : "-1";
                hasUpdateOffset = true;
                updates = new ArrayList<Update>();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isPaused && !isPolling && mpgService.isPollTokenActive(currentToken)) {
                            dialogsUpdatesHandler.removeCallbacks(dialogsUpdateTask);
                            dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, UPDATE_POLL_MILLIS);
                        }
                    }
                });
            }
            @Override
            public void onError(Exception error) {
                initialSyncInProgress = false;
                if (error instanceof MPGramService.PollCancelledException) return;
                if (isActivityDied || isPaused || !mpgService.isPollTokenActive(currentToken)) return;
                if (App.isSessionDead(error.getMessage(), MainActivity.this)) return;
                hasUpdateOffset = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isPaused && !isPolling && mpgService.isPollTokenActive(currentToken)) {
                            dialogsUpdatesHandler.removeCallbacks(dialogsUpdateTask);
                            dialogsUpdatesHandler.postDelayed(dialogsUpdateTask, UPDATE_POLL_MILLIS);
                        }
                    }
                });
            }
        });
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
                        String peerId = getDialogPeerId(upd);
                        if (peerId == null) break;
                        if (dialogResponse == null || dialogResponse.dialogs == null || dialogsAdapter == null) break;

                        boolean found = false;
                        boolean isEdit = upd.type.contains("Edit");

                        for (int i = 0; i < dialogResponse.dialogs.size(); i++) {
                            Dialog d = dialogResponse.dialogs.get(i);
                            if (d != null && peerId.equals(d.peerId)) {
                                dialogsRefreshPeers.remove(peerId);

                                if (!isEdit && d.msg != null && d.msg.id == upd.msg.id) {
                                    d.isTyping = false;
                                    found = true;
                                    break;
                                }

                                if (isEdit && (d.msg == null || d.msg.id != upd.msg.id)) {
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

                        if (!found && !isEdit) {
                            if (!dialogsRefreshPeers.contains(peerId)) {
                                dialogsRefreshPeers.add(peerId);
                                needRefresh = true;
                            }
                        }
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
                            if (d.msg != null && d.msg.out && (upd.max_id == 0 || d.msg.id <= upd.max_id)) {
                                d.msg.messageState = Message.MessageState.MESSAGE_WAS_READ;
                            }
                            break;
                        }
                    }
                    break;
            }
        }

        if (needRefresh) {
            dialogsRefreshPending = true;
        }

        if (dialogsAdapter != null) dialogsAdapter.notifyDataSetChanged();

        maybeRefreshDialogs();
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
                if (result == null || result.dialogs == null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;
                            updateDialogsFooter();
                            maybeRefreshDialogs();
                        }
                    });
                    return;
                }

                Collections.sort(result.dialogs, SortUtils.PINNED_FIRST);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideNetworkStatus();

                        applyDialogResponse(result);
                        loadedDialogs = dialogResponse.dialogs.size();

                        removeMissingRefreshPeers();

                        isLoading = false;
                        updateDialogsFooter();
                        showDialogs();

                        maybeRefreshDialogs();
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
                            Log.d(TAG, "refreshDialogs: Failed to load dialogResponse" + error.getMessage());

                            updateDialogsFooter();
                            maybeRefreshDialogs();
                        }
                    });
                }
            }
        });
    }

    private void loadDialogs(final boolean initial, final int limit, final String offsetPeer, final String offsetId, final long offsetDate) {
        isLoading = true;

        if (initial) {
            initialSyncInProgress = true;
            loadedDialogs = 0;
            hasCachedDialogs = false;
            dialogsRefreshPeers.clear();
            dialogsRefreshPending = false;

            String cachedJsonString = App.getCacheManager().loadDialogs();
            if (cachedJsonString != null) {
                try {
                    JSONObject cachedJson = (JSONObject) cc.nnproject.json.JSON.get(cachedJsonString);
                    DialogResponse dr = new DialogResponse(cachedJson);

                    if (dr != null && dr.dialogs != null) {
                        Collections.sort(dr.dialogs, SortUtils.PINNED_FIRST);
                        applyDialogResponse(dr);
                        loadedDialogs = dialogResponse.dialogs.size();
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
                initialSyncInProgress = false;
                progressBar.setVisibility(View.GONE);
                updateDialogsFooter();

                if (!hasCachedDialogs) {
                    Toast.makeText(MainActivity.this, "No network or cached dialogs", Toast.LENGTH_LONG).show();
                }

                return;
            }
        } else {
            loadMoreButton.setEnabled(false);

            if (!App.isNetworkAvailable()) {
                isLoading = false;
                updateDialogsFooter();

                Toast.makeText(MainActivity.this, "No network", Toast.LENGTH_SHORT).show();

                return;
            }
        }

        mpgService.getDialogs(limit, offsetPeer, offsetId, offsetDate, userToken, initial, new MPGramService.ApiCallback<DialogResponse>() {
            @Override
            public void onSuccess(final DialogResponse result) {
                if (result == null || result.dialogs == null) {
                    isLoading = false;
                    if (initial) initialSyncInProgress = false;
                    return;
                }

                Collections.sort(result.dialogs, SortUtils.PINNED_FIRST);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (initial) {
                            hideNetworkStatus();
                            applyDialogResponse(result);
                            hasCachedDialogs = true;

                            initialSyncInProgress = false;
                            if (!isPaused) syncLastUpdateAndStartPolling(pollToken);
                        } else {
                            dialogResponse.offsetPeer = result.offsetPeer;
                            dialogResponse.offsetId = result.offsetId;
                            dialogResponse.offsetDate = result.offsetDate;

                            appendDialogs(result.dialogs);
                            Collections.sort(dialogResponse.dialogs, SortUtils.PINNED_FIRST);
                        }

                        loadedDialogs = dialogResponse.dialogs.size();

                        boolean allLoaded = result.dialogs.isEmpty()
                                || (dialogResponse.totalCount > 0
                                && loadedDialogs >= dialogResponse.totalCount);

                        if (allLoaded && !initial) {
                            Toast.makeText(MainActivity.this, "Everything is loaded", Toast.LENGTH_SHORT).show();
                        }

                        isLoading = false;
                        updateDialogsFooter();
                        showDialogs();

                        maybeRefreshDialogs();
                    }
                });
            }

            @Override
            public void onError(final Exception error) {
                Log.d(TAG, "MAIN ACT onError: " + error.getMessage());

                isLoading = false;
                if (initial) initialSyncInProgress = false;

                if (!App.isSessionDead(error.getMessage(), MainActivity.this)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);

                            if (initial && !hasCachedDialogs) {
                                Toast.makeText(MainActivity.this, "Failed to load dialogResponse " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            } else if (initial && hasCachedDialogs) {
                                showServerUnavailable();
                                tryStartPolling();
                            } else if (!initial) {
                                loadMoreButton.setEnabled(true);
                                Toast.makeText(MainActivity.this, "Failed to load dialogs " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }

                            maybeRefreshDialogs();
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

    private void maybeRefreshDialogs() {
        if (!dialogsRefreshPending) return;
        if (isActivityDied || isPaused || isLoading || initialSyncInProgress) return;
        if (!App.isNetworkAvailable()) return;

        dialogsRefreshPending = false;

        int limit = loadedDialogs > 0 ? loadedDialogs : GET_DIALOG_LIMIT;

        refreshDialogs(limit);
    }

    private void getTopics(final String peer) {
        if (forumTopicResponse != null && peer.equals(forumTopicResponse.peer)) {
            setTopics(peer);
            return;
        }

        if (topicsAdapter != null && !peer.equals(displayedTopicsPeer)) {
            topicsAdapter.clear();
            topicsAdapter.notifyDataSetChanged();
        }

        mpgService.getForumTopics(peer, userToken, new MPGramService.ApiCallback<ForumTopicResponse>() {
            @Override
            public void onSuccess(final ForumTopicResponse result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityDied) return;
                        forumTopicResponse = result;
                        setTopics(peer);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                error.printStackTrace();
            }
        });
    }

    private void setTopics(String peer) {
        if (topicsAdapter == null) {
            topicsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
            topicListView.setAdapter(topicsAdapter);
        } else if (peer.equals(displayedTopicsPeer)) {
            return;
        } else {
            topicsAdapter.clear();
        }

        for (int i = 0; i < forumTopicResponse.topics.size(); i++) {
            ForumTopic t = forumTopicResponse.topics.get(i);
            if (t != null) {
                if (t.title != null && t.title.length() > 0) {
                    topicsAdapter.add(t.title);
                } else {
                    topicsAdapter.add(String.valueOf(t.id));
                }
            }
        }

        displayedTopicsPeer = peer;
        topicsAdapter.notifyDataSetChanged();
    }

    private void removeMissingRefreshPeers() {
        if (dialogsRefreshPeers.isEmpty()) return;

        if (dialogResponse == null || dialogResponse.dialogs == null) {
            dialogsRefreshPeers.clear();
            return;
        }

        for (int i = dialogsRefreshPeers.size() - 1; i >= 0; i--) {
            String peer = dialogsRefreshPeers.get(i);
            boolean present = false;

            for (int j = 0; j < dialogResponse.dialogs.size(); j++) {
                Dialog d = dialogResponse.dialogs.get(j);
                if (d != null && peer.equals(d.peerId)) {
                    present = true;
                    break;
                }
            }

            if (present) {
                dialogsRefreshPeers.remove(i);
            } else {
                Log.d(TAG, "dialog still missing after refresh: " + peer);
            }
        }
    }

    private void appendDialogs(ArrayList<Dialog> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        if (dialogResponse == null || dialogResponse.dialogs == null) return;

        for (int i = 0; i < incoming.size(); i++) {
            Dialog d = incoming.get(i);
            if (d == null || d.peerId == null) continue;

            boolean exists = false;
            for (int j = 0; j < dialogResponse.dialogs.size(); j++) {
                Dialog existing = dialogResponse.dialogs.get(j);
                if (existing != null && d.peerId.equals(existing.peerId)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                dialogResponse.dialogs.add(d);
            }
        }
    }

    private String getDialogPeerId(Update upd) {
        if (upd == null) return null;
        if (upd.msg == null) return upd.getPeerId();

        String peerId = upd.msg.peer_id;
        if (peerId == null) {
            peerId = upd.getPeerId();
        }
        if (peerId == null) return null;

        String fromId = upd.msg.from_id;

        if (!upd.msg.out
                && fromId != null
                && !peerId.equals(fromId)
                && !peerId.startsWith("-")
                && !fromId.startsWith("-")
                && (App.selfId == null || peerId.equals(App.selfId))) {
            Log.d(TAG, "fix update peer: " + peerId + " -> " + fromId + ", msg=" + upd.msg.id);
            return fromId;
        }

        return peerId;
    }

    private void updateDialogsFooter() {
        if (footerView == null || loadMoreButton == null) return;

        if (dialogResponse == null || dialogResponse.dialogs == null || dialogResponse.dialogs.isEmpty()) {
            footerView.setVisibility(View.GONE);
            return;
        }

        boolean allLoaded = dialogResponse.totalCount > 0
                && dialogResponse.dialogs.size() >= dialogResponse.totalCount;

        footerView.setVisibility(allLoaded ? View.GONE : View.VISIBLE);
        loadMoreButton.setEnabled(!isLoading);
    }
    private void navigateToChat(String id, String name, int readOut) {
        navigateToChat(id, name, readOut, -1);
    }

    private void navigateToChat(String id, String name, int readOut, int topic) {
        Intent intent = new Intent(this, ChatActivity.class);

        intent.putExtra("ID", id);
        intent.putExtra("NAME", name);
        intent.putExtra("READ_OUT", readOut);
        intent.putExtra("TOPIC", topic);

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
            networkStatusText.setText("Network lost...");
            networkStatusText.setVisibility(View.VISIBLE);
        }
    }

    private void onNetworkRestored() {
        hideNetworkStatus();
        if (initialSyncInProgress || isLoading) return;
        if (dialogResponse == null || dialogResponse.dialogs == null || dialogResponse.dialogs.isEmpty()) {
            loadDialogs(true, GET_DIALOG_LIMIT, null, null, 0);
        } else {
            refreshDialogs(loadedDialogs > 0 ? loadedDialogs : GET_DIALOG_LIMIT);
            tryStartPolling();
        }
    }

    private void showServerUnavailable() {
        if (networkStatusText == null) return;
        networkStatusText.setText("Server unavailable, showing cached data");
        networkStatusText.setVisibility(View.VISIBLE);
    }

    private void hideNetworkStatus() {
        if (networkStatusText == null) return;
        networkStatusText.setText("Network lost...");
        networkStatusText.setVisibility(View.GONE);
    }
}
