package com.val00n.dmpg.ui.chat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.Chat;
import com.val00n.dmpg.model.message.HistoryResponse;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.model.Update;
import com.val00n.dmpg.model.message.Message;
import com.val00n.dmpg.model.User;
import com.val00n.dmpg.model.message.MessageMedia;
import com.val00n.dmpg.model.message.MessageReply;
import com.val00n.dmpg.model.message.MessageRow;
import com.val00n.dmpg.model.message.PendingMessage;
import com.val00n.dmpg.services.FileHelper;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.services.MessageFileActions;
import com.val00n.dmpg.services.SettingsManager;
import com.val00n.dmpg.ui.AvatarViewBinder;
import com.val00n.dmpg.ui.ImageViewBinder;
import com.val00n.dmpg.ui.main.MainActivity;
import com.val00n.dmpg.ui.StartupActivity;
import com.val00n.dmpg.utils.BitmapUtils;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeMap;
import cc.nnproject.json.JSONObject;
import static android.content.ContentValues.TAG;

public class ChatActivity extends Activity {

    private ProgressBar progressBar;
    private TextView chatTitleText;
    private TextView chatStatusText;
    private ImageView chatAvatar;
    private AvatarViewBinder headerAvatarViewBinder;
    private boolean headerAvatarBound;
    private boolean headerAvatarHasPhoto;
    private String headerAvatarPeer;
    private EditText chatInputEditText;
    private ImageButton chatAttachButton;
    private ImageButton chatSendButton;
    private ImageButton chatOptionsButton;
    private MessagesAdapter messagesAdapter;
    private ListView chatMessages;
    private TextView headerText;
    private ProgressBar headerProgressBar;
    private Button headerButton;
    private View headerView;
    private LinearLayout tooltipLayout;
    private TextView tooltipText;
    private LinearLayout bottomContainerContent;
    private ImageButton tooltipClose;
    private ImageView tooltipPreview;
    private ImageButton backToBottomButton;
    private TextView networkStatusText;

    private MPGramService mpgService;
    private SettingsManager settingsManager;
    private HistoryResponse historyResponse;
    private Instance instance;
    private Handler chatUpdatesHandler;
    private Handler statusUpdateHandler;
    private Runnable statusUpdateTask;
    private Runnable chatUpdateTask;
    private String userToken;
    private String peerId;
    private String displayName;
    private String topic;
    private String draftMessage;
    private String firstMessageId = "0";
    private int readOut;
    private int loadedHistory;
    private boolean isLoading;
    private boolean endReached;
    private boolean initialLoaded;
    private boolean fetchError;
    private boolean atBottom = true;
    private boolean canType;
    private boolean isTyping;
    private boolean isSavedMessages;
    private int messagesPollMillis;
    private boolean chatPollEnabled;

    private volatile long pollToken = 0;
    private volatile boolean hasUpdateOffset = false;
    private volatile boolean initialSyncInProgress = false;
    private volatile boolean historyGap = false;
    private volatile boolean skipNextPauseHistoryGap = false;
    private volatile boolean historyRefreshInProgress = false;
    private volatile boolean historyRefreshScheduled = false;

    volatile private boolean isPolling;
    volatile private boolean isPaused;
    volatile private boolean isActivityDied;

    private MessageAction messageAction = MessageAction.NONE;
    private String fwdPeerId;
    private String fwdMessageId;
    private String targetMessageId;

    private String updateLimit = "200";
    private String updateOffset = "-1";
    private int updateFails = 0;

    private final ArrayList<PendingMessage> pendingMessages = new ArrayList<PendingMessage>();
    private int nextLocalMessageId = -1;

    private App.NetworkListener networkListener;
    private boolean hasCachedHistory = false;
    private volatile boolean pendingCheckScheduled = false;
    private volatile boolean pendingCheckInProgress = false;
    private File pendingAttachmentFile;
    private String pendingAttachmentName;
    private String pendingAttachmentMime;
    private boolean pendingAttachmentAsFile;
    private boolean pendingAttachmentIsImage;
    private boolean pendingAttachmentAsSpoiler;
    private int pendingAttachmentWidth;
    private int pendingAttachmentHeight;

    private static final int MENU_REFRESH = 1;

    private static final int OFFLINE_CHECK_MILLIS = 6000;
    private static final int UPDATE_TIMEOUT_SECS = 30;
    private static final int STATUS_UPDATE_MILLIS = 5000;
    private static final int PENDING_CHECK_DELAY_MILLIS = 3000;
    private static final int PENDING_CHECK_RETRY_MILLIS = 7000;
    private static final int PENDING_UNCERTAIN_TIMEOUT_SECS = 20;
    private static final int REQUEST_FORWARD = 1;
    private static final int REQUEST_PICK_IMAGE = 2;
    private static final int REQUEST_PICK_FILE = 3;

    private final Runnable pendingCheckTask = new Runnable() {
        @Override
        public void run() {
            pendingCheckScheduled = false;
            checkPendingAgainstServer();
        }
    };

    private final Runnable historyRefreshTask = new Runnable() {
        @Override
        public void run() {
            historyRefreshScheduled = false;
            refreshHistoryAfterGap();
        }
    };

    private enum MessageAction {
        NONE, EDIT, REPLY
    }

    private enum MessageOptions {
        COPY, DELETE, EDIT, REPLY, FORWARD, SHARE, SAVE, INFO
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat);

        mpgService = App.getMPGramService(); // TODO prioritize
        instance = App.getInstanceManager().getSelectedInstance();
        settingsManager = App.getSettingsManager();
        userToken = App.getSessionManager().loadSession(instance.baseURL);

        peerId = getIntent().getStringExtra("ID");
        displayName = getIntent().getStringExtra("NAME");
        readOut = getIntent().getIntExtra("READ_OUT", 0);

        int t = getIntent().getIntExtra("TOPIC", -1);
        topic = t > 0 ? String.valueOf(t) : null;

        if (App.selfId == null && userToken != null && instance != null) {
            final String reqBase = instance.baseURL;
            mpgService.getSelf(userToken, new MPGramService.ApiCallback<User>() {
                @Override
                public void onSuccess(final User result) {
                    if (isActivityDied) return;
                    if (result != null && reqBase.equals(App.getInstanceManager().getSelectedInstanceBaseUrl())) {
                        App.selfId = result.id;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (messagesAdapter != null) messagesAdapter.setSavedMessages(peerId.equals(result.id));
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

        isSavedMessages = App.selfId != null && App.selfId.equals(peerId);

        progressBar = (ProgressBar) findViewById(R.id.chatProgressBar);
        chatTitleText = (TextView) findViewById(R.id.chatTitle);
        chatStatusText = (TextView) findViewById(R.id.chatStatus);
        chatOptionsButton = (ImageButton) findViewById(R.id.chatOptionsButton);
        chatMessages = (ListView) findViewById(R.id.chatMessages);
        chatInputEditText = (EditText) findViewById(R.id.chatInputEditText);
        chatAttachButton = (ImageButton) findViewById(R.id.chatAttachButton);
        chatSendButton = (ImageButton) findViewById(R.id.chatSendButton);
        tooltipLayout = (LinearLayout) findViewById(R.id.chatTooltip);
        tooltipClose = (ImageButton) findViewById(R.id.chatTooltipCloseButton);
        tooltipText = (TextView) findViewById(R.id.chatTooltipText);
        tooltipPreview = (ImageView) findViewById(R.id.chatTooltipPreview);
        bottomContainerContent = (LinearLayout) findViewById(R.id.chatBottomContainerContent);
        backToBottomButton = (ImageButton) findViewById(R.id.chatBackToBottomButton);
        networkStatusText = (TextView) findViewById(R.id.chatNetworkStatusText);
        chatAvatar = (ImageView) findViewById(R.id.chatAvatar);

        headerAvatarViewBinder = new AvatarViewBinder(App.getCacheManager());

        chatUpdatesHandler = new Handler();
        statusUpdateHandler = new Handler();

        chatUpdateTask = new Runnable() {
            @Override
            public void run() {
                final long currentToken = pollToken;
                if (isActivityDied || isPolling || isPaused || !mpgService.isPollTokenActive(currentToken)) return;

                if (!App.isNetworkAvailable()) {
                    isPolling = false;
                    historyGap = true;
                    chatUpdatesHandler.postDelayed(this, OFFLINE_CHECK_MILLIS);
                    return;
                }

                isPolling = true;
                mpgService.getUpdates(peerId, null, updateLimit, updateOffset, firstMessageId, true, UPDATE_TIMEOUT_SECS, topic, userToken, new MPGramService.ApiCallback<ArrayList<Update>>() {
                    @Override
                    public void onSuccess(ArrayList<Update> result) {
                        isPolling = false;

                        if (isActivityDied) return;
                        if (!isPaused && !mpgService.isPollTokenActive(currentToken)) return;

                        updateFails = 0;
                        final ArrayList<Update> batch = result;

                        if (batch != null && batch.size() > 0) {
                            updateOffset = Integer.toString(batch.get(batch.size() - 1).update_id);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isActivityDied) update(batch);
                                }
                            });
                        }

                        if (!isPaused) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isActivityDied) hideNetworkStatus();
                                }
                            });
                        }

                        if (isPaused) return;
                        if (!mpgService.isPollTokenActive(currentToken) || !App.isNetworkAvailable()) return;

                        chatUpdatesHandler.removeCallbacks(chatUpdateTask);
                        chatUpdatesHandler.postDelayed(chatUpdateTask, messagesPollMillis);
                    }

                    @Override
                    public void onError(Exception error) {
                        isPolling = false;
                        if (error instanceof MPGramService.PollCancelledException) return;
                        if (isActivityDied || isPaused || !mpgService.isPollTokenActive(currentToken)) return;

                        if (!App.isSessionDead(error.getMessage(), ChatActivity.this)) {
                            String errMsg = error.getMessage();
                            Log.d(TAG, "UPDATE FAILED: " + errMsg);

                            if (errMsg != null && errMsg.contains("Concurrent getUpdates")) {
                                if (!isPaused && mpgService.isPollTokenActive(currentToken)) {
                                    chatUpdatesHandler.removeCallbacks(chatUpdateTask);
                                    chatUpdatesHandler.postDelayed(chatUpdateTask, messagesPollMillis);
                                }
                                return;
                            }

                            historyGap = true;

                            if (updateFails > 10) {
                                updateFails = 0;
                                refreshHistoryAfterGap();
                            } else {
                                updateFails++;
                                scheduleHistoryRefreshRetry(messagesPollMillis * 3);
                            }

                            if (!isPaused && mpgService.isPollTokenActive(currentToken)) {
                                chatUpdatesHandler.removeCallbacks(chatUpdateTask);
                                chatUpdatesHandler.postDelayed(chatUpdateTask,
                                        Math.min(messagesPollMillis * Math.max(updateFails, 1), 20000));
                            }
                        }
                    }
                });
            }
        };

        statusUpdateTask = new Runnable() {
            @Override
            public void run() {
                if (isTyping) {
                    isTyping = false;
                    showStatus();
                }
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

        if (peerId == null) {
            Toast.makeText(ChatActivity.this, "Invalid peer ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (savedInstanceState != null) {
            fwdPeerId = savedInstanceState.getString("fwdPeerId");
            fwdMessageId = savedInstanceState.getString("fwdMessageId");
        }

        chatTitleText.setText(displayName);

        addHeader();
        loadHistory(true, null, 0);

        chatMessages.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                int adapterPos = position - 1;
                if (messagesAdapter == null || !messagesAdapter.isMessage(adapterPos)) {
                    return false;
                }
                Message msg = (Message) messagesAdapter.getItem(adapterPos);
                if (msg == null) {
                    return false;
                }

                showMessageActionMenu(msg);

                return true;
            }
        });

        chatMessages.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!chatSendButton.isEnabled()) return;

                int adapterPos = position - 1;
                if (messagesAdapter == null || !messagesAdapter.isMessage(adapterPos)) return;

                Message msg = (Message) messagesAdapter.getItem(adapterPos);
                if (msg == null || msg.messageState != Message.MessageState.MESSAGE_ERROR) return;

                PendingMessage entry = findPendingByMessage(msg);

                if (entry != null) {
                    historyResponse.messages.remove(entry.message);
                    historyResponse.messages.add(entry.message);

                    pendingMessages.remove(entry);
                    pendingMessages.add(entry);

                    chatSendButton.setEnabled(false);
                    retryPending(entry);
                    chatMessages.setSelection(chatMessages.getCount() - 1);
                } else {
                    historyResponse.messages.remove(msg);
                    sendMessage(msg.text);
                }
            }
        });

        chatMessages.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                // ?
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (firstVisibleItem == 0 && !isLoading && !fetchError && !endReached
                        && initialLoaded && totalItemCount > visibleItemCount) {
                    loadHistory(false, historyResponse.offsetId, historyResponse.offsetDate);
                }

                if (firstVisibleItem + visibleItemCount >= totalItemCount - 3) {
                    atBottom = true;
                    backToBottomButton.setVisibility(View.GONE);
                } else {
                    atBottom = false;
                    backToBottomButton.setVisibility(View.VISIBLE);
                }
            }
        });

        chatSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = chatInputEditText.getText().toString().trim();
                
                switch (messageAction) {
                    case EDIT:
                        if (text != null && text.length() > 0) {
                            applyEditedMessage(targetMessageId, text);
                        }
                        break;
                    default:
                        if (pendingAttachmentFile != null) {
                            sendMediaMessage(text);
                        } else if (text != null && text.length() > 0) {
                            sendMessage(text);
                        }
                        break;
                }

                restoreActionMode();
                chatInputEditText.setText("");
            }
        });

        chatAttachButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAttachMenu();
            }
        });

        tooltipPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pendingAttachmentFile != null) {
                    showMediaActionMenu();
                }
            }
        });

        backToBottomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chatMessages.setSelection(chatMessages.getCount() - 1);
                backToBottomButton.setVisibility(View.GONE);
            }
        });

        tooltipClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restoreActionMode();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        App.addNetworkListener(networkListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        skipNextPauseHistoryGap = false;
        Instance selected = App.getInstanceManager().getSelectedInstance();
        if (instance == null || !instance.equals(selected)) {
            navigateToStartup();
            return;
        }

        String currentToken = App.getSessionManager().loadSession(instance.baseURL);
        if (currentToken == null || currentToken.length() == 0
                || userToken == null || !currentToken.equals(userToken)) {
            navigateToStartup();
            return;
        }

        isPaused = false;
        isPolling = false;

        messagesPollMillis = settingsManager.getMessagesPollMillis();
        chatPollEnabled = settingsManager.isChatPollEnabled();

        pollToken = mpgService.beginPollSession(userToken);

        if (App.isNetworkAvailable()) {
            if (historyResponse == null && !isLoading) {
                loadHistory(true, null, 0);
            } else if (historyGap) {
                refreshHistoryAfterGap();
            } else {
                tryStartPolling();
            }
        } else {
            onNetworkLost();
        }

        if (messagesAdapter != null) {
            messagesAdapter.setChatStyle(settingsManager.getChatStyle());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPaused = true;
        isPolling = false;
        if (skipNextPauseHistoryGap) {
            skipNextPauseHistoryGap = false;
        } else {
            historyGap = true;
        }
        historyRefreshScheduled = false;
        chatUpdatesHandler.removeCallbacks(chatUpdateTask);
        chatUpdatesHandler.removeCallbacks(historyRefreshTask);
        chatUpdatesHandler.removeCallbacks(pendingCheckTask);
        statusUpdateHandler.removeCallbacks(statusUpdateTask);
        mpgService.endPollSession(pollToken, userToken);
        pendingCheckScheduled = false;
        pendingCheckInProgress = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        App.removeNetworkListener(networkListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDied = true;
        historyRefreshScheduled = false;

        chatUpdatesHandler.removeCallbacks(chatUpdateTask);
        chatUpdatesHandler.removeCallbacks(historyRefreshTask);
        statusUpdateHandler.removeCallbacks(statusUpdateTask);

        if (pendingAttachmentFile != null) {
            pendingAttachmentFile.delete();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem refresh = menu.add(0, MENU_REFRESH, 0, "Refresh");
        refresh.setIcon(android.R.drawable.ic_menu_rotate);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case MENU_REFRESH:
                loadHistory(true, null, 0);
            default:
                return super.onOptionsItemSelected(menuItem);
        }
    }

    private AlbumView.Listener albumListener = new AlbumView.Listener() {
        @Override
        public void onMediaClick(MessageRow item, int index) {
            if (item.isVideo) {
                openVideoViewer(item);
            } else {
                openImageViewer(item);
            }
        }

        @Override
        public boolean onMediaLongClick(MessageRow item, int index) {
            if (item.message != null) {
                showMessageActionMenu(item.message);
                return true;
            }
            return false;
        }
    };

    private final MessagesAdapter.MediaClickListener mediaClickListener = new MessagesAdapter.MediaClickListener() {
        @Override
        public void onImageClick(MessageRow item) {
            openImageViewer(item);
        }

        @Override
        public void onVideoClick(MessageRow item) {
            openVideoViewer(item);
        }
    };

    private void openImageViewer(MessageRow item) {
        if (item == null) return;

        String peer = item.chatPeer != null ? item.chatPeer : item.peer;

        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putExtra("PEER", peer);
        intent.putExtra("MESSAGE_ID", item.messageId);
        if (item.mediaMime != null) {
            intent.putExtra("MEDIA_MIME", item.mediaMime);
        }
        if (item.message != null && item.message.media != null) {
            intent.putExtra("MEDIA_NAME", item.message.media.name);
            intent.putExtra("MEDIA_SIZE", item.message.media.size);
        }

        skipNextPauseHistoryGap = true;
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            skipNextPauseHistoryGap = false;
            throw e;
        }
    }

    private void openVideoViewer(MessageRow item) {
        if (item == null || item.message == null || item.message.media == null) return;

        String peer = item.chatPeer != null ? item.chatPeer : item.peer;

        Intent intent = new Intent(this, VideoViewerActivity.class);
        intent.putExtra("PEER", peer);
        intent.putExtra("MESSAGE_ID", item.messageId);
        intent.putExtra("FILENAME", item.message.media.name);
        intent.putExtra("MEDIA_MIME", item.message.media.mime);

        skipNextPauseHistoryGap = true;
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            skipNextPauseHistoryGap = false;
            throw e;
        }
    }

    private void tryStartPolling() {
        chatUpdatesHandler.removeCallbacks(chatUpdateTask);
        if (!chatPollEnabled) return;
        if (isPaused || isPolling || initialSyncInProgress || historyResponse == null
                || !App.isNetworkAvailable() || !mpgService.isPollTokenActive(pollToken)) {
            return;
        }
        if (!hasUpdateOffset) {
            syncLastUpdateAndStartPolling(pollToken);
            return;
        }
        chatUpdatesHandler.postDelayed(chatUpdateTask, messagesPollMillis);
    }

    private void syncLastUpdateAndStartPolling(final long currentToken) {
        if (isActivityDied || isPaused || !mpgService.isPollTokenActive(currentToken) || !App.isNetworkAvailable()) return;
        chatUpdatesHandler.removeCallbacks(chatUpdateTask);
        initialSyncInProgress = true;
        mpgService.getLastUpdate(peerId, firstMessageId, userToken, UPDATE_TIMEOUT_SECS, new MPGramService.ApiCallback<Update>() {
            @Override
            public void onSuccess(final Update result) {
                initialSyncInProgress = false;
                if (isActivityDied || isPaused || !mpgService.isPollTokenActive(currentToken)) return;

                if (result == null || result.update_id < 0) {
                    updateOffset = "-1";
                } else {
                    int offset = result.update_id;

                    if (!result.exact) {
                        offset--;
                    }

                    if (offset < 0) offset = 0;
                    updateOffset = Integer.toString(offset);
                }

                Log.d(TAG, "sync offset: update_id=" + (result != null ? result.update_id : -1)
                        + " exact=" + (result != null && result.exact)
                        + " firstMessageId=" + firstMessageId
                        + " offset=" + updateOffset);

                hasUpdateOffset = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (chatPollEnabled && !isPaused && !isPolling && mpgService.isPollTokenActive(currentToken)) {
                            chatUpdatesHandler.removeCallbacks(chatUpdateTask);
                            chatUpdatesHandler.postDelayed(chatUpdateTask, messagesPollMillis);
                        }
                    }
                });
            }
            @Override
            public void onError(Exception error) {
                initialSyncInProgress = false;
                if (error instanceof MPGramService.PollCancelledException) return;
                if (isActivityDied || isPaused || !mpgService.isPollTokenActive(currentToken)) return;
                if (App.isSessionDead(error.getMessage(), ChatActivity.this)) return;
                hasUpdateOffset = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (chatPollEnabled && !isPaused && !isPolling && mpgService.isPollTokenActive(currentToken)) {
                            chatUpdatesHandler.removeCallbacks(chatUpdateTask);
                            chatUpdatesHandler.postDelayed(chatUpdateTask, messagesPollMillis);
                        }
                    }
                });
            }
        });
    }

    private void sendMessage(String text) {
        if (historyResponse == null || messagesAdapter == null) return;

        chatSendButton.setEnabled(false);

        final String replyId = messageAction == MessageAction.REPLY && targetMessageId != null ? targetMessageId : null;

        Message pending = new Message(text, nextLocalMessageId--, peerId);
        pending.out = true;
        pending.date = System.currentTimeMillis() / 1000;
        pending.from_id = App.selfId;

        if (replyId != null) {
            pending.reply = buildPendingReply(replyId);
        }

        int baseId = getMaxServerMessageId();
        PendingMessage entry = new PendingMessage(pending, replyId, baseId);
        pendingMessages.add(entry);
        historyResponse.messages.add(pending);

        sendPending(entry);
        chatMessages.post(new Runnable() {
            @Override
            public void run() {
                if (!isActivityDied) {
                    chatMessages.setSelection(chatMessages.getCount() - 1);
                }
            }
        });
    }

    private void sendMediaMessage(String caption) {
        if (historyResponse == null || messagesAdapter == null || pendingAttachmentFile == null) {
            return;
        }

        if (messageAction == MessageAction.EDIT) {
            Toast.makeText(this, "Finish editing first", Toast.LENGTH_SHORT).show();
            return;
        }

        chatSendButton.setEnabled(false);

        final String replyId = messageAction == MessageAction.REPLY && targetMessageId != null
                ? targetMessageId : null;

        Message pending = new Message(caption, nextLocalMessageId--, peerId);
        pending.out = true;
        pending.date = System.currentTimeMillis() / 1000;

        if (replyId != null) {
            pending.reply = buildPendingReply(replyId);
        }

        MessageMedia media = new MessageMedia();
        media.type = (pendingAttachmentMime != null && pendingAttachmentMime.startsWith("image/") && !pendingAttachmentAsFile)
                ? "photo" : "document";
        media.mime = pendingAttachmentMime;
        media.name = pendingAttachmentName;
        media.size = pendingAttachmentFile.length();
        media.width = pendingAttachmentWidth;
        media.height = pendingAttachmentHeight;
        pending.media = media;

        int baseId = getMaxServerMessageId();
        PendingMessage entry = new PendingMessage(pending, replyId, baseId);
        entry.attachmentFile = pendingAttachmentFile;
        entry.attachmentName = pendingAttachmentName;
        entry.attachmentMime = pendingAttachmentMime;
        entry.attachmentAsFile = pendingAttachmentAsFile;
        entry.attachmentAsSpoiler = pendingAttachmentAsSpoiler;

        pendingMessages.add(entry);
        historyResponse.messages.add(pending);

        pendingAttachmentFile = null;
        pendingAttachmentName = null;
        pendingAttachmentMime = null;
        pendingAttachmentAsFile = false;
        pendingAttachmentIsImage = false;
        pendingAttachmentAsSpoiler = false;

        sendPending(entry);
        chatMessages.post(new Runnable() {
           @Override
            public void run() {
               if (!isActivityDied) {
                   chatMessages.setSelection(chatMessages.getCount() - 1);
               }
           }
        });
    }

    private void showAttachMenu() {
        if (messageAction == MessageAction.EDIT) {
            Toast.makeText(this, "Finish editing first", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Attach")
                .setItems(new String[]{"Photo", "File"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            pickAttachment("image/*", REQUEST_PICK_IMAGE);
                        } else {
                            pickAttachment("*/*", REQUEST_PICK_FILE);
                        }
                    }
                })
                .show();
    }

    private void pickAttachment(String mime, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivityForResult(Intent.createChooser(intent, "Select file"), requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No file picker found", Toast.LENGTH_SHORT).show();
        }
    }

    private int getMaxServerMessageId() {
        int maxId = 0;
        try {
            maxId = Integer.parseInt(firstMessageId);
        } catch (NumberFormatException ignored) {}

        if (historyResponse != null && historyResponse.messages != null) {
            for (int i = 0; i < historyResponse.messages.size(); i++) {
                int id = historyResponse.messages.get(i).id;
                if (id > maxId) maxId = id;
            }
        }
        return maxId;
    }

    private void sendPending(final PendingMessage entry) {
        final Message pending = entry.message;

        entry.lastAttemptAt = System.currentTimeMillis() / 1000;
        entry.accepted = false;
        entry.uncertain = false;

        pending.date = entry.lastAttemptAt;
        pending.messageState = Message.MessageState.MESSAGE_SENDING;
        messagesAdapter.notifyDataSetChanged();

        MPGramService.ApiCallback<String> callback = new MPGramService.ApiCallback<String>() {
            @Override
            public void onSuccess(final String result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityDied) return;

                        if (pending.id > 0) {
                            chatSendButton.setEnabled(true);
                            return;
                        }

                        if ("1".equals(result)) {
                            entry.accepted = true;
                            pending.messageState = Message.MessageState.MESSAGE_SENT;
                            deleteEntryAttachment(entry);
                        } else {
                            entry.uncertain = true;
                            pending.messageState = Message.MessageState.MESSAGE_SENDING;
                        }

                        messagesAdapter.notifyDataSetChanged();
                        schedulePendingCheck(PENDING_CHECK_DELAY_MILLIS);
                        chatSendButton.setEnabled(true);
                    }
                });
            }

            @Override
            public void onError(final Exception error) {
                error.printStackTrace();

                if (isActivityDied) return;
                if (App.isSessionDead(error.getMessage(), ChatActivity.this)) return;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityDied) return;

                        if (pending.id > 0) {
                            chatSendButton.setEnabled(true);
                            return;
                        }

                        entry.uncertain = true;
                        pending.messageState = Message.MessageState.MESSAGE_SENDING;
                        messagesAdapter.notifyDataSetChanged();
                        schedulePendingCheck(PENDING_CHECK_DELAY_MILLIS);
                        chatSendButton.setEnabled(true);
                    }
                });
            }
        };

        if (entry.attachmentFile != null) {
            mpgService.sendMedia(
                    userToken,
                    peerId,
                    entry.attachmentFile,
                    entry.attachmentName,
                    entry.attachmentMime,
                    pending.text,
                    entry.replyId,
                    entry.attachmentAsFile,
                    entry.attachmentAsSpoiler,
                    false,
                    topic,
                    callback
            );
        } else {
            mpgService.sendMessage(userToken, peerId, pending.text, entry.replyId,
                    null, null, topic, callback);
        }
    }

    private void deleteEntryAttachment(PendingMessage entry) {
        if (entry.attachmentFile != null) {
            entry.attachmentFile.delete();
            entry.attachmentFile = null;
        }
    }

    private void update(ArrayList<Update> u) {
        if (u == null || historyResponse == null || historyResponse.messages == null) return;
        for (final Update upd : u) {
            if (upd == null || upd.type == null) continue;
            switch (upd.type) {
                case "updateNewChannelMessage":
                case "updateNewMessage":
                    if (upd.msg != null) {
                        Message serverMsg = upd.msg;
                        boolean isDupe = false;
                        if (serverMsg.id > 0) {
                            for (int i = 0; i < historyResponse.messages.size(); i++) {
                                if (serverMsg.id == historyResponse.messages.get(i).id) {
                                    isDupe = true;
                                    break;
                                }
                            }
                            try {
                                if (serverMsg.id > Integer.parseInt(firstMessageId)) {
                                    firstMessageId = String.valueOf(serverMsg.id);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                        if (isDupe) continue;

                        PendingMessage match = serverMsg.out ? findPendingMatch(serverMsg) : null;

                        if (match != null) {
                            replacePendingWithServer(match, serverMsg);
                            messagesAdapter.notifyDataSetChanged();
                        } else {
                            if (serverMsg.out) {
                                Log.w(TAG, "updateNewMessage: no pending match for out message id=" + serverMsg.id + " text=" + serverMsg.text);
                            }
                            historyResponse.messages.add(serverMsg);
                            messagesAdapter.notifyDataSetChanged();

                            if (atBottom) {
                                chatMessages.setSelection(chatMessages.getCount() - 1);
                            }
                        }
                    }

                    if (isTyping) {
                        statusUpdateHandler.removeCallbacks(statusUpdateTask);
                        statusUpdateTask.run();
                    }

                    break;
                case "updateEditChannelMessage":
                case "updateEditMessage":
                    if (upd.msg != null) {
                        for (int i = 0; i < historyResponse.messages.size(); i++) {
                            if (upd.msg.id == historyResponse.messages.get(i).id) {
                                historyResponse.messages.set(i, upd.msg);
                                messagesAdapter.notifyDataSetChanged();
                                break;
                            }
                        }
                    }
                    break;
                case "updateDeleteChannelMessages":
                case "updateDeleteMessages":
                    // deletions in DMs is not implemented on the server
                    if (upd.messages != null && peerId.equals(upd.channel_id)) {
                        clearDeleted(upd.messages);
                    }
                    break;
                case "updateUserTyping":
                    String typingAuthor = upd.user_id != null ? upd.user_id : upd.from_id;
                    if (typingAuthor != null && typingAuthor.equals(App.selfId)) break;

                    if (upd.action != null && upd.action.has("_") && "sendMessageCancelAction".equals(upd.action.getString("_"))) {
                        statusUpdateHandler.removeCallbacks(statusUpdateTask);
                        statusUpdateTask.run();
                    } else {
                        isTyping = true;
                        chatStatusText.setText("Typing...");
                        statusUpdateHandler.removeCallbacks(statusUpdateTask);
                        statusUpdateHandler.postDelayed(statusUpdateTask, STATUS_UPDATE_MILLIS);
                    }
                    break;
                case "updateChatUserTyping":
                case "updateChannelUserTyping":
                    String typingUser = upd.user_id != null ? upd.user_id : upd.from_id;
                    if (typingUser != null && typingUser.equals(App.selfId)) break;

                    if (upd.action != null && upd.action.has("_") && "sendMessageCancelAction".equals(upd.action.getString("_"))) {
                        statusUpdateHandler.removeCallbacks(statusUpdateTask);
                        statusUpdateTask.run();
                    } else {
                        isTyping = true;
                        if (upd.from_id != null && historyResponse.users != null) {
                            User user = historyResponse.users.get(upd.from_id);
                            if (user != null) {
                                chatStatusText.setText(user.getDisplayName() + " is typing...");
                            } else {
                                chatStatusText.setText("Typing...");
                            }
                        } else {
                            chatStatusText.setText("Typing...");
                        }
                        statusUpdateHandler.removeCallbacks(statusUpdateTask);
                        statusUpdateHandler.postDelayed(statusUpdateTask, STATUS_UPDATE_MILLIS);
                    }
                    break;
                case "updateUserStatus":
                    if (upd.status == null || historyResponse.users == null) {
                        break;
                    }

                    User user = historyResponse.users.get(upd.user_id);
                    if (user == null) {
                        mpgService.getUserPeer(userToken, upd.user_id, new MPGramService.ApiCallback<User>() {
                            @Override
                            public void onSuccess(User result) {
                                historyResponse.users.put(upd.user_id, result);
                            }

                            @Override
                            public void onError(Exception error) {
                                error.printStackTrace();
                            }
                        });
                    } else {
                        user.s = "userStatusOnline".equals(upd.status.getNullableString("_"));
                        if (!user.s) {
                            user.w = upd.status.getLong("was_online", user.w);
                        }
                    }

                    if (upd.user_id.equals(peerId) && !isTyping) {
                        showStatus();
                    }

                    break;
                case "updateReadHistoryOutbox":
                case "updateReadChannelOutbox":
                    if (peerId.equals(upd.peer) || peerId.equals(upd.channel_id)) {
                        markRead(upd.max_id);
                    }

                    messagesAdapter.notifyDataSetChanged();
                    break;
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (fwdMessageId != null) {
            outState.putString("fwdPeerId", fwdPeerId);
            outState.putString("fwdMessageId", fwdMessageId);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PICK_IMAGE || requestCode == REQUEST_PICK_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importPickedUri(data.getData(), requestCode == REQUEST_PICK_FILE);
            }
            return;
        }

        if (requestCode == REQUEST_FORWARD && resultCode == RESULT_OK && data != null
                && fwdMessageId != null && fwdPeerId != null) {
            String targetPeer = data.getStringExtra("ID");
            String targetName = data.getStringExtra("NAME");

            if (targetPeer != null) {
                sendForward(targetPeer, targetName);
                fwdPeerId = null;
                fwdMessageId = null;
            }
        }
    }

    private void importPickedUri(final Uri uri, final boolean asFile) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                File outFile = null;
                InputStream in = null;
                OutputStream out = null;

                try {
                    final String name = getDisplayName(uri);
                    String mime = getContentResolver().getType(uri);

                    if (mime == null || mime.length() == 0) {
                        mime = FileHelper.getMimeType(name);
                    }

                    final String finalMime = mime;

                    outFile = new File(getCacheDir(),
                            "upload_" + System.currentTimeMillis() + ".tmp");

                    in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new IOException("Cannot open stream");

                    out = new FileOutputStream(outFile);
                    byte[] buffer = new byte[8192];
                    int read;

                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }

                    out.flush();

                    final File finalFile = outFile;

                    int width = 0;
                    int height = 0;

                    if (!asFile && finalMime != null && finalMime.startsWith("image/")) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(outFile.getAbsolutePath(), options);

                        if (options.outWidth > 0 && options.outHeight > 0) {
                            width = options.outWidth;
                            height = options.outHeight;
                        }
                    }

                    final int imageWidth = width;
                    final int imageHeight = height;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isActivityDied) return;
                            showAttachment(finalFile, name, finalMime, asFile, imageWidth, imageHeight);
                        }
                    });
                } catch (final Exception e) {
                    if (outFile != null) outFile.delete();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isActivityDied) return;
                            Toast.makeText(ChatActivity.this,
                                    "Cannot read file: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                } finally {
                    closeQuietly(in);
                    closeQuietly(out);
                }
            }
        }).start();
    }

    private void showAttachment(File file, String name, String mime, boolean asFile, int width, int height) {
        clearPendingAttachment(true);

        pendingAttachmentFile = file;
        pendingAttachmentName = name;
        pendingAttachmentMime = mime;
        pendingAttachmentAsFile = asFile;
        pendingAttachmentIsImage = mime != null && mime.startsWith("image/");
        pendingAttachmentWidth = width;
        pendingAttachmentHeight = height;

        tooltipLayout.setVisibility(View.VISIBLE);
        if (messageAction == MessageAction.NONE) {
            tooltipText.setText("Attach: " + name);
        }

        tooltipPreview.setVisibility(View.VISIBLE);

        if (pendingAttachmentIsImage) {
            Bitmap bitmap = BitmapUtils.decodeSampledBitmapFromFile(
                    file.getAbsolutePath(), 96, 96, true);

            if (bitmap != null) {
                tooltipPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                tooltipPreview.setImageBitmap(bitmap);
            } else {
                tooltipPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                tooltipPreview.setImageResource(android.R.drawable.ic_menu_report_image);
            }
        } else {
            tooltipPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            tooltipPreview.setImageResource(android.R.drawable.ic_menu_save);
        }
    }

    private void clearPendingAttachment(boolean deleteFile) {
        if (deleteFile && pendingAttachmentFile != null) {
            pendingAttachmentFile.delete();
        }

        pendingAttachmentFile = null;
        pendingAttachmentName = null;
        pendingAttachmentMime = null;
        pendingAttachmentAsFile = false;
        pendingAttachmentIsImage = false;
        pendingAttachmentAsSpoiler = false;
        pendingAttachmentWidth = 0;
        pendingAttachmentHeight = 0;

        if (tooltipPreview != null) {
            tooltipPreview.setImageDrawable(null);
            tooltipPreview.setVisibility(View.GONE);
        }
    }

    private String getDisplayName(Uri uri) {
        String name = null;
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(uri,
                    new String[]{"_display_name"}, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (Exception ignored) {
            // a
        } finally {
            if (cursor != null) cursor.close();
        }

        if (name == null || name.length() == 0) {
            name = uri.getLastPathSegment();
        }

        name = FileHelper.sanitizeFileName(name);
        return name != null ? name : "file";
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) { }
        }
    }

    private void sendForward(String targetPeer, final String targetName) {
        mpgService.sendMessage(userToken, targetPeer, null, null, fwdPeerId, fwdMessageId, null, new MPGramService.ApiCallback<String>() {
            @Override
            public void onSuccess(final String result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result != null && result.equals("1")) {
                            Toast.makeText(ChatActivity.this, "Forwarded to " + targetName, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ChatActivity.this, "Failed to forward", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            @Override
            public void onError(final Exception error) {
                error.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ChatActivity.this, "Failed to forward: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void forwardMessage(Message msg) {
        fwdPeerId = msg.peer_id;
        fwdMessageId = String.valueOf(msg.id);

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("FORWARD_MODE", true);
        startActivityForResult(intent, REQUEST_FORWARD);
    }

    private void deleteMessage(final String id, boolean revoke) {
        mpgService.deleteMessage(userToken, peerId, id, revoke, new MPGramService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                if ("1".equals(result)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            clearDeleted(new ArrayList<String>(Arrays.asList(id)));
                            Toast.makeText(ChatActivity.this, "Successfully deleted", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    onError(new Exception("res: " + result));
                }
            }

            @Override
            public void onError(final Exception error) {
                error.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ChatActivity.this, "Unable to delete: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void clearDeleted(ArrayList<String> messages) {
        if (messages == null || messages.size() == 0) return;
        if (historyResponse == null || historyResponse.messages == null) return;

        boolean isChanged = false;

        for (int i = historyResponse.messages.size() - 1; i >= 0; i--) {
            int currentId = historyResponse.messages.get(i).id;

            if (messages.contains(String.valueOf(currentId))) {
                historyResponse.messages.remove(i);
                isChanged = true;
            }
        }

        if (isChanged) {
            messagesAdapter.notifyDataSetChanged();
        }
    }

    private void restoreActionMode() {
        tooltipLayout.setVisibility(View.GONE);
        clearPendingAttachment(true);

        if (messageAction == MessageAction.EDIT) {
            chatInputEditText.setText(draftMessage);
        }
        messageAction = MessageAction.NONE;
    }

    private void showMessageActionMenu(final Message msg) {
        ArrayList<String> options = new ArrayList<String>();
        final ArrayList<MessageOptions> actions = new ArrayList<MessageOptions>();

        if (msg.text != null && msg.text.length() > 0) {
            options.add("Copy");
            actions.add(MessageOptions.COPY);
        }

        options.add("Delete");
        actions.add(MessageOptions.DELETE);

        options.add("Forward");
        actions.add(MessageOptions.FORWARD);

        options.add("Reply");
        actions.add(MessageOptions.REPLY);

        if (msg.media != null) {
            options.add("Save");
            actions.add(MessageOptions.SAVE);
        }

        if (msg.text != null && msg.text.length() > 0) {
            options.add("Share");
            actions.add(MessageOptions.SHARE);
        }

        options.add("Info");
        actions.add(MessageOptions.INFO);

        if (msg.text != null && msg.text.length() > 0 && !isMessagePending(msg) && msg.id > 0) {
            options.add("Edit");
            actions.add(MessageOptions.EDIT);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose action");

        builder.setItems(options.toArray(new String[options.size()]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MessageOptions action = actions.get(which);

                switch (action) {
                    case DELETE:
                        if (isMessagePending(msg)) {
                            PendingMessage entry = findPendingByMessage(msg);

                            if (entry != null) {
                                deleteEntryAttachment(entry);
                                pendingMessages.remove(entry);
                            }

                            historyResponse.messages.remove(msg);
                            messagesAdapter.notifyDataSetChanged();
                        } else {
                            new AlertDialog.Builder(ChatActivity.this)
                                    .setTitle("Delete")
                                    .setMessage("Are you sure?")
                                    .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            deleteMessage(String.valueOf(msg.id), false);
                                        }
                                    })
                                    .setNegativeButton("Delete for everyone", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            deleteMessage(String.valueOf(msg.id), true);
                                        }
                                    })
                                    .setNeutralButton("Cancel", null)
                                    .show();
                        }
                        break;

                    case COPY:
                        copy(msg.text);
                        break;

                    case EDIT:
                        if (isMessagePending(msg)) {
                            Toast.makeText(ChatActivity.this, "Cannot edit pending message", Toast.LENGTH_SHORT).show();
                        } else if (msg.id <= 0) {
                            Toast.makeText(ChatActivity.this, "Cannot edit message without server ID", Toast.LENGTH_SHORT).show();
                        } else {
                            editMessage(msg);
                        }
                        break;

                    case FORWARD:
                        if (isMessagePending(msg)) {
                            Toast.makeText(ChatActivity.this, "Cannot forward pending message", Toast.LENGTH_SHORT).show();
                        } else {
                            forwardMessage(msg);
                        }
                        break;

                    case REPLY:
                        if (isMessagePending(msg)) {
                            Toast.makeText(ChatActivity.this, "Cannot reply to pending message", Toast.LENGTH_SHORT).show();
                        } else {
                            setReply(msg);
                        }
                        break;

                    case SAVE:
                        MessageFileActions.showSaveMenu(ChatActivity.this, peerId, msg, false);
                        break;

                    case SHARE:
                        if (msg.text != null) {
                            shareMessage(msg.text);
                        } else {
                            Toast.makeText(ChatActivity.this, "Message is empty", Toast.LENGTH_SHORT).show();
                        }
                        break;

                    case INFO:
                        showMessageInfo(msg);
                        break;
                }
            }
        });

        builder.create().show();
    }

    private void showMediaActionMenu() {
        ArrayList<String> options = new ArrayList<String>();
        final ArrayList<Integer> actions = new ArrayList<Integer>();

        options.add("Remove");
        actions.add(1);

        options.add(pendingAttachmentAsSpoiler ? "Send without spoiler" : "Mark as spoiler");
        actions.add(2);

        options.add(pendingAttachmentAsFile ? "Send as usual" : "Send as file");
        actions.add(3);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose action");

        builder.setItems(options.toArray(new String[options.size()]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int action = actions.get(which);

                switch (action) {
                    case 1:
                        clearPendingAttachment(true);
                        if (messageAction == MessageAction.REPLY) {
                            Message target = findMessageById(targetMessageId);
                            tooltipText.setText("Reply: " + (target != null && target.text != null ? target.text : ""));
                            tooltipLayout.setVisibility(View.VISIBLE);
                        } else {
                            tooltipLayout.setVisibility(View.GONE);
                        }
                        break;

                    case 2:
                        pendingAttachmentAsSpoiler = !pendingAttachmentAsSpoiler;
                        break;

                    case 3:
                        pendingAttachmentAsFile = !pendingAttachmentAsFile;
                        break;
                }
            }
        });

        builder.create().show();
    }

    private void editMessage(Message message) {
        if (message.text != null && message.text.length() > 0) {
            messageAction = MessageAction.EDIT;
            draftMessage = chatInputEditText.getText().toString();

            targetMessageId = String.valueOf(message.id);
            chatInputEditText.setText(message.text);

            tooltipLayout.setVisibility(View.VISIBLE);
            tooltipText.setText("Editing message:");
        } else {
            Toast.makeText(ChatActivity.this, "Message is empty", Toast.LENGTH_SHORT).show();
            return;
        }
    }

    private void setReply(Message message) {
        String tooltipReplyText;
        tooltipReplyText = "Reply: ";

        if (message.text != null) {
            tooltipReplyText += message.text;
        }

        messageAction = MessageAction.REPLY;
        targetMessageId = String.valueOf(message.id);

        tooltipLayout.setVisibility(View.VISIBLE);
        tooltipText.setText(tooltipReplyText);
    }

    private void applyEditedMessage(String id, String text) {
        mpgService.editMessage(userToken, peerId, id, text, new MPGramService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                if ("1".equals(result)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showMessages();
                        }
                    });
                } else {
                    onError(new Exception("res: " + result));
                }
            }

            @Override
            public void onError(final Exception error) {
                error.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ChatActivity.this, "Unable to edit: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void shareMessage(String text) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(sendIntent, "Send message"));
    }

    private void copy(String text) {
        if (text == null || text.length() <= 0) {
            Toast.makeText(ChatActivity.this, "Message is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        @SuppressWarnings("deprecation")
        android.text.ClipboardManager clipboard =
                (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setText(text);
        }
    }

    private void showMessageInfo(Message msg) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("ID: ").append(msg.id);
        stringBuilder.append("\nDATE: ").append(msg.date);
        stringBuilder.append("\nPEER_ID: ").append(msg.peer_id);
        stringBuilder.append("\nFROM_ID: ").append(msg.from_id);

        if (msg.media != null) { // FIXME
            stringBuilder.append("\n\nMEDIA_TYPE: ").append(msg.media.type);
            stringBuilder.append("\nMEDIA_MIME: ").append(msg.media.mime);
        }

        stringBuilder.append("\n\nTEXT: ").append(msg.text);

        new AlertDialog.Builder(ChatActivity.this)
                .setTitle("Message info")
                .setMessage(stringBuilder.toString())
                .setNeutralButton("Ok", null)
                .show();
    }

    private void showMessages() {
        if (messagesAdapter == null) {
            messagesAdapter = new MessagesAdapter(
                    historyResponse.messages,
                    historyResponse.users,
                    historyResponse.chats,
                    isSavedMessages,
                    peerId != null && peerId.startsWith("-"),
                    new AvatarViewBinder(App.getCacheManager()),
                    new ImageViewBinder(App.getCacheManager()));

            messagesAdapter.setChatStyle(settingsManager.getChatStyle());
            messagesAdapter.setTopic(topic);
            messagesAdapter.setAlbumListener(albumListener);
            messagesAdapter.setMediaClickListener(mediaClickListener);
            chatMessages.setAdapter(messagesAdapter);
        } else {
            messagesAdapter.setAlbumListener(albumListener);
            messagesAdapter.setMediaClickListener(mediaClickListener);

            int headerCount = chatMessages.getHeaderViewsCount();
            int firstPos = chatMessages.getFirstVisiblePosition();
            int anchorId = -1;
            int anchorTop = 0;

            for (int i = 0; i < chatMessages.getChildCount(); i++) {
                int adapterPos = (firstPos + i) - headerCount;
                if (adapterPos >= 0 && messagesAdapter.isMessage(adapterPos)) {
                    Message m = (Message) messagesAdapter.getItem(adapterPos);
                    if (m != null && m.id > 0) {
                        anchorId = m.id;
                        anchorTop = chatMessages.getChildAt(i).getTop();
                        break;
                    }
                }
            }

            int oldCount = messagesAdapter.getCount();
            messagesAdapter.notifyDataSetChanged();

            if (anchorId > 0) {
                int newAdapterPos = messagesAdapter.findRowPositionByMessageId(anchorId);
                if (newAdapterPos >= 0) {
                    chatMessages.setSelectionFromTop(newAdapterPos + headerCount, anchorTop);
                }
            } else {
                View v = chatMessages.getChildAt(0);
                int top = (v == null) ? 0 : v.getTop();
                int inserted = messagesAdapter.getCount() - oldCount;
                chatMessages.setSelectionFromTop(firstPos + inserted, top);
            }
        }

        progressBar.setVisibility(View.GONE);
        showStatus();
        updateChatAvatar();
    }

    private void showStatus() {
        if (historyResponse == null) return;
        String status = "";

        if (peerId.startsWith("-") && historyResponse.chats != null) {
            Chat chat = historyResponse.chats.get(peerId);
            if (chat != null && chat.name != null) status = "@" + chat.name;
        } else if (historyResponse.users != null) {
            User user = historyResponse.users.get(peerId);
            if (user != null) {
                status = user.getStatusText();

                if (status == null && user.name != null) {
                    status = "@" + user.name;
                }
            }
        }

        chatStatusText.setText(status);
    }

    private void markRead(int limit) {
        for (int i = historyResponse.messages.size() - 1; i >= 0; i--) {
            Message m = historyResponse.messages.get(i);
            if (m.out && m.id > 0 && m.id <= limit
                    && m.messageState != Message.MessageState.MESSAGE_ERROR
                    && m.messageState != Message.MessageState.MESSAGE_SENDING) {
                historyResponse.messages.get(i).messageState = Message.MessageState.MESSAGE_WAS_READ;
            }
        }
    }

    private void loadHistory(final boolean initial, final String offsetId, final long offsetDate) {
        if (initial) {
            initialSyncInProgress = true;
            loadedHistory = 0;
            hasCachedHistory = false;

            String cachedJsonString = App.getCacheManager().loadHistory(peerId, topic);

            if (cachedJsonString != null) {
                try {
                    JSONObject cachedJson = (JSONObject) cc.nnproject.json.JSON.get(cachedJsonString);
                    HistoryResponse hr = new HistoryResponse(cachedJson);
                    if (hr.messages != null && !hr.messages.isEmpty()) {
                        Collections.reverse(hr.messages);

                        historyResponse = hr;
                        initialLoaded = true;

                        if (!historyResponse.messages.isEmpty()) {
                            firstMessageId = String.valueOf(
                                    historyResponse.messages.get(historyResponse.messages.size() - 1).id);
                        }

                        canType = true;

                        if (peerId != null && peerId.startsWith("-")) {
                            canType = false;

                            if (historyResponse.chats != null) {
                                Chat chat = historyResponse.chats.get(peerId);
                                if (chat != null) canType = !chat.c && !chat.l;
                            }
                        }
                        if (canType) {
                            bottomContainerContent.setVisibility(View.VISIBLE);
                        }
                        showMessages();

                        chatMessages.post(new Runnable() {
                            @Override
                            public void run() {
                                chatMessages.setSelection(chatMessages.getCount() - 1);
                            }
                        });

                        hasCachedHistory = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to parse cached history", e);
                }
            }

            if (!App.isNetworkAvailable()) {
                isLoading = false;
                initialSyncInProgress = false;
                progressBar.setVisibility(View.GONE);

                if (hasCachedHistory) {
                    if (networkStatusText != null) {
                        networkStatusText.setVisibility(View.VISIBLE);
                        updateNetworkStatusPosition();
                    }
                } else {
                    Toast.makeText(ChatActivity.this, "No network or cached history", Toast.LENGTH_LONG).show();
                    finish();
                }

                return;
            }
        } else {
            if (!App.isNetworkAvailable()) {
                isLoading = false;

                headerView.setVisibility(View.VISIBLE);
                headerProgressBar.setVisibility(View.GONE);
                headerButton.setVisibility(View.VISIBLE);
                if (networkStatusText != null) {
                    networkStatusText.setVisibility(View.VISIBLE);
                    updateNetworkStatusPosition();
                }

                return;
            }

            headerText.setVisibility(View.VISIBLE);
            headerText.setText("Loading messages...");
            headerProgressBar.setVisibility(View.VISIBLE);
            headerButton.setVisibility(View.GONE);
        }

        isLoading = true;

        mpgService.getHistory(peerId, offsetId, offsetDate, userToken, initial, topic, new MPGramService.ApiCallback<HistoryResponse>() {
            @Override
            public void onSuccess(final HistoryResponse result) {
               runOnUiThread(new Runnable() {
                   @Override
                   public void run() {
                       loadedHistory += result.messages.size();
                       Collections.reverse(result.messages);

                       isLoading = false;
                       hideNetworkStatus();

                       if (initial) {
                           boolean shouldScrollToBottom = !hasCachedHistory || atBottom;

                           mergeInitialHistory(result);
                           historyGap = false;

                           markRead(readOut);
                           initialLoaded = true;

                           showMessages();

                           canType = true;
                           if (peerId != null && peerId.startsWith("-")) {
                               canType = false;
                               if (historyResponse.chats != null) {
                                   Chat chat = historyResponse.chats.get(peerId);
                                   if (chat != null) {
                                       canType = !chat.c && !chat.l;
                                   }
                               }
                           }

                           if (canType) {
                               bottomContainerContent.setVisibility(View.VISIBLE);
                           }

                           initialSyncInProgress = false;
                           if (!isPaused) syncLastUpdateAndStartPolling(pollToken);

                           if (shouldScrollToBottom) {
                               chatMessages.post(new Runnable() {
                                   @Override
                                   public void run() {
                                       chatMessages.setSelection(chatMessages.getCount() - 1);
                                   }
                               });
                           }
                       } else {
                           historyResponse.add(result);
                           markRead(readOut);

                           if (loadedHistory >= result.totalCount || result.messages.isEmpty()) {
                               endReached = true;

                               headerText.setVisibility(View.GONE);
                               headerProgressBar.setVisibility(View.GONE);
                               headerButton.setVisibility(View.GONE);
                           }

                           showMessages();
                       }
                   }
               });
            }

            @Override
            public void onError(final Exception error) {
                Log.d(TAG, "onError: " + error.getMessage());
                error.printStackTrace();

                if (!App.isSessionDead(error.getMessage(), ChatActivity.this)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isLoading = false;

                            if (initial) {
                                initialSyncInProgress = false;
                                progressBar.setVisibility(View.GONE);

                                if (!hasCachedHistory) {
                                    Toast.makeText(ChatActivity.this, "Failed to load history " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                } else {
                                    if (networkStatusText != null) {
                                        networkStatusText.setText("Server unavailable");
                                        networkStatusText.setVisibility(View.VISIBLE);
                                        updateNetworkStatusPosition();
                                    }
                                    tryStartPolling();
                                }
                            } else {
                                fetchError = true;
                                headerView.setVisibility(View.VISIBLE);
                                headerProgressBar.setVisibility(View.GONE);
                                headerButton.setVisibility(View.VISIBLE);
                                Toast.makeText(ChatActivity.this, "Failed to load: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        });
    }

    private void schedulePendingCheck(int delayMillis) {
        if (isActivityDied || isPaused || !hasUnresolvedPending()) return;
        if (pendingCheckScheduled) return;

        pendingCheckScheduled = true;
        chatUpdatesHandler.removeCallbacks(pendingCheckTask);
        chatUpdatesHandler.postDelayed(pendingCheckTask, delayMillis);
    }

    private boolean hasUnresolvedPending() {
        long now = System.currentTimeMillis() / 1000;
        for (int i = 0; i < pendingMessages.size(); i++) {
            PendingMessage entry = pendingMessages.get(i);

            if (entry.message.id > 0) continue;
            if (entry.message.messageState == Message.MessageState.MESSAGE_ERROR) continue;
            if (!entry.accepted && !entry.uncertain) continue;
            if (entry.accepted && now - entry.lastAttemptAt >= PENDING_UNCERTAIN_TIMEOUT_SECS) continue;

            return true;
        }
        return false;
    }

    private void checkPendingAgainstServer() {
        if (isActivityDied || isPaused || !hasUnresolvedPending()) return;

        if (pendingCheckInProgress || isLoading || initialSyncInProgress
                || historyRefreshInProgress || !App.isNetworkAvailable()
                || !mpgService.isPollTokenActive(pollToken)) {
            schedulePendingCheck(PENDING_CHECK_RETRY_MILLIS);
            return;
        }

        final long currentToken = pollToken;
        pendingCheckInProgress = true;

        mpgService.getRecentMessages(peerId, topic, userToken, new MPGramService.ApiCallback<HistoryResponse>() {
                    @Override
                    public void onSuccess(final HistoryResponse result) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pendingCheckInProgress = false;
                                if (isActivityDied
                                        || !mpgService.isPollTokenActive(currentToken)) return;

                                applyProbeResult(result);
                                evaluatePendingChecks();
                            }
                        });
                    }

                    @Override
                    public void onError(final Exception error) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pendingCheckInProgress = false;
                                if (isActivityDied) return;

                                evaluatePendingChecks();
                            }
                        });
                    }
                });
    }

    private boolean applyProbeResult(HistoryResponse result) {
        if (result == null || result.messages == null
                || historyResponse == null || historyResponse.messages == null) {
            return false;
        }

        boolean changed = false;

        for (int i = result.messages.size() - 1; i >= 0; i--) {
            Message server = result.messages.get(i);

            if (server.id <= 0 || !server.out) continue;

            PendingMessage match = findPendingMatch(server);
            if (match == null) continue;

            if (isMessageInHistory(server.id)) {
                historyResponse.messages.remove(match.message);
                pendingMessages.remove(match);
            } else {
                replacePendingWithServer(match, server);
            }
            changed = true;
        }

        if (changed) {
            recalculateFirstMessageId();
            messagesAdapter.notifyDataSetChanged();
        }

        return changed;
    }

    private boolean isMessageInHistory(int id) {
        for (int i = 0; i < historyResponse.messages.size(); i++) {
            if (historyResponse.messages.get(i).id == id) return true;
        }
        return false;
    }

    private void evaluatePendingChecks() {
        long now = System.currentTimeMillis() / 1000;
        boolean changed = false;
        boolean hasMore = false;

        for (int i = 0; i < pendingMessages.size(); i++) {
            PendingMessage entry = pendingMessages.get(i);
            Message local = entry.message;

            if (local.id > 0) continue;
            if (local.messageState == Message.MessageState.MESSAGE_ERROR) continue;

            entry.confirmAttempts++;

            if (entry.uncertain) {
                if (now - entry.lastAttemptAt >= PENDING_UNCERTAIN_TIMEOUT_SECS) {
                    local.messageState = Message.MessageState.MESSAGE_ERROR;
                    changed = true;
                    continue;
                }
                hasMore = true;
            } else if (entry.accepted) {
                if (now - entry.lastAttemptAt < PENDING_UNCERTAIN_TIMEOUT_SECS) {
                    hasMore = true;
                }
            }
        }

        if (changed) {
            messagesAdapter.notifyDataSetChanged();
        }

        if (hasMore) {
            schedulePendingCheck(PENDING_CHECK_RETRY_MILLIS);
        }
    }

    private void refreshHistoryAfterGap() {
        if (isActivityDied || isPaused || !historyGap) return;
        if (historyResponse == null || !initialLoaded) return;

        if (historyRefreshInProgress || isLoading || initialSyncInProgress
                || !App.isNetworkAvailable() || !mpgService.isPollTokenActive(pollToken)) {
            scheduleHistoryRefreshRetry(messagesPollMillis);
            return;
        }

        final long currentToken = pollToken;
        historyRefreshInProgress = true;
        historyRefreshScheduled = false;
        chatUpdatesHandler.removeCallbacks(historyRefreshTask);

        Log.d(TAG, "refreshing history after gap");

        mpgService.getHistory(peerId, null, 0, userToken, false, topic, new MPGramService.ApiCallback<HistoryResponse>() {
            @Override
            public void onSuccess(final HistoryResponse result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        historyRefreshInProgress = false;

                        if (isActivityDied || !mpgService.isPollTokenActive(currentToken)) return;

                        historyGap = false;

                        String keepOffsetId = historyResponse.offsetId;
                        long keepOffsetDate = historyResponse.offsetDate;

                        Collections.reverse(result.messages);
                        mergeInitialHistory(result);

                        historyResponse.offsetId = keepOffsetId;
                        historyResponse.offsetDate = keepOffsetDate;

                        markRead(readOut);
                        showMessages();

                        if (atBottom) {
                            chatMessages.setSelection(chatMessages.getCount() - 1);
                        }

                        hasUpdateOffset = false;
                        chatUpdatesHandler.removeCallbacks(chatUpdateTask);
                        syncLastUpdateAndStartPolling(currentToken);
                    }
                });
            }

            @Override
            public void onError(final Exception error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        historyRefreshInProgress = false;

                        if (isActivityDied || !mpgService.isPollTokenActive(currentToken)) return;

                        Log.d(TAG, "history refresh failed: " + error.getMessage());

                        if (isPaused || !App.isNetworkAvailable()) return;

                        scheduleHistoryRefreshRetry(messagesPollMillis * 3);

                        if (!isPolling) tryStartPolling();
                    }
                });
            }
        });
    }

    private void scheduleHistoryRefreshRetry(int delayMillis) {
        if (isActivityDied || isPaused || !historyGap || historyRefreshScheduled) return;

        historyRefreshScheduled = true;
        chatUpdatesHandler.removeCallbacks(historyRefreshTask);
        chatUpdatesHandler.postDelayed(historyRefreshTask, delayMillis);
    }

    private MessageReply buildPendingReply(String replyId) {
        Message target = findMessageById(replyId);

        if (target != null) {
            MessageReply reply = new MessageReply(target.text);
            reply.replyId = replyId;
            reply.peer = target.from_id != null ? target.from_id : target.peer_id;
            reply.msg = target;
            return reply;
        }

        MessageReply reply = new MessageReply("Loading...");
        reply.replyId = replyId;
        return reply;
    }

    private PendingMessage findPendingMatch(Message serverMsg) {
        if (serverMsg == null || serverMsg.id <= 0) return null;

        for (int i = 0; i < pendingMessages.size(); i++) {
            PendingMessage entry = pendingMessages.get(i);
            Message local = entry.message;

            if (local.id > 0) continue;
            if (local.messageState == Message.MessageState.MESSAGE_ERROR && !entry.uncertain) continue;
            if (serverMsg.id <= entry.minServerId) continue;
            if (!isSamePendingContent(local, serverMsg)) continue;

            return entry;
        }

        return null;
    }

    private boolean isSamePendingContent(Message local, Message server) {
        if (local.text != null && local.text.length() > 0) {
            return local.text.equals(server.text);
        }

        if (local.media != null && server.media != null) {
            return local.media.type != null && local.media.type.equals(server.media.type);
        }

        return false;
    }

    private void replacePendingWithServer(PendingMessage entry, Message server) {
        Message local = entry.message;

        if (server.reply == null) {
            server.reply = local.reply;
        } else if (local.reply != null) {
            if (server.reply.replyId == null) server.reply.replyId = local.reply.replyId;
            if (server.reply.peer == null) server.reply.peer = local.reply.peer;
            if (server.reply.quote == null) server.reply.quote = local.reply.quote;
            if (server.reply.msg == null) server.reply.msg = local.reply.msg;
        }

        if (historyResponse != null && historyResponse.messages != null) {
            int index = historyResponse.messages.indexOf(local);

            if (index >= 0) {
                historyResponse.messages.set(index, server);
            } else {
                historyResponse.messages.add(server);
            }

            if (server.id > 0) {
                for (int i = historyResponse.messages.size() - 1; i >= 0; i--) {
                    if (i != index && historyResponse.messages.get(i).id == server.id) {
                        historyResponse.messages.remove(i);
                        Log.d(TAG, "Removed duplicate server message id=" + server.id + " at index " + i);
                    }
                }
            }
        }

        pendingMessages.remove(entry);
    }

    private void retryPending(final PendingMessage entry) {
        if (entry.message.id > 0 || pendingCheckInProgress || isLoading) {
            chatSendButton.setEnabled(true);
            return;
        }

        chatSendButton.setEnabled(false);
        pendingCheckInProgress = true;

        mpgService.getRecentMessages(peerId, topic, userToken, new MPGramService.ApiCallback<HistoryResponse>() {
                    @Override
                    public void onSuccess(final HistoryResponse result) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pendingCheckInProgress = false;
                                applyProbeResult(result);

                                if (entry.message.id > 0 || !pendingMessages.contains(entry)) {
                                    chatSendButton.setEnabled(true);
                                    return;
                                }

                                historyResponse.messages.remove(entry.message);
                                historyResponse.messages.add(entry.message);
                                pendingMessages.remove(entry);
                                pendingMessages.add(entry);

                                entry.minServerId = getMaxServerMessageId();
                                sendPending(entry);
                            }
                        });
                    }

                    @Override
                    public void onError(final Exception error) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pendingCheckInProgress = false;
                                chatSendButton.setEnabled(true);
                                Toast.makeText(ChatActivity.this,
                                        "Cannot verify message status",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
    }

    private void mergeInitialHistory(HistoryResponse result) {
        if (result == null || result.messages == null) return;

        if (historyResponse == null || historyResponse.messages == null) {
            historyResponse = result;
            recalculateFirstMessageId();
            return;
        }

        ArrayList<Message> current = historyResponse.messages;
        ArrayList<Message> incoming = result.messages;

        for (int i = 0; i < pendingMessages.size(); i++) {
            Message pending = pendingMessages.get(i).message;
            if (!current.contains(pending)) {
                current.add(pending);
            }
        }

        if (!pendingMessages.isEmpty()) {
            for (int i = 0; i < incoming.size(); i++) {
                Message serverMsg = incoming.get(i);
                if (serverMsg.id <= 0 || !serverMsg.out) continue;

                PendingMessage match = findPendingMatch(serverMsg);
                if (match != null) {
                    if (isMessageInHistory(serverMsg.id)) {
                        current.remove(match.message);
                        pendingMessages.remove(match);
                    } else {
                        replacePendingWithServer(match, serverMsg);
                    }
                }
            }
        }

        TreeMap<Integer, Message> byId = new TreeMap<Integer, Message>();
        ArrayList<Message> localPending = new ArrayList<Message>();

        for (int i = 0; i < current.size(); i++) {
            Message m = current.get(i);
            if (m.id > 0) {
                byId.put(m.id, m);
            } else {
                localPending.add(m);
            }
        }

        for (int i = 0; i < incoming.size(); i++) {
            Message m = incoming.get(i);
            if (m.id <= 0) continue;

            Message existing = byId.get(m.id);
            if (existing != null) {
                if (existing != m) {
                    applyServerMessage(existing, m);
                }
            } else {
                byId.put(m.id, m);
            }
        }

        current.clear();
        current.addAll(byId.values());
        current.addAll(localPending);

        if (result.users != null) {
            if (historyResponse.users == null) {
                historyResponse.users = result.users;
                messagesAdapter = null;
            } else if (historyResponse.users != result.users) {
                historyResponse.users.putAll(result.users);
            }
        }

        if (result.chats != null) {
            if (historyResponse.chats == null) {
                historyResponse.chats = result.chats;
                messagesAdapter = null;
            } else if (historyResponse.chats != result.chats) {
                historyResponse.chats.putAll(result.chats);
            }
        }

        if (result.totalCount > historyResponse.totalCount) {
            historyResponse.totalCount = result.totalCount;
        }

        historyResponse.offsetId = result.offsetId;
        historyResponse.offsetDate = result.offsetDate;

        recalculateFirstMessageId();

        Log.d(TAG, "merged history: incoming=" + incoming.size()
                + " messages=" + current.size()
                + " pending=" + pendingMessages.size()
                + " firstMessageId=" + firstMessageId);
    }

    private void recalculateFirstMessageId() {
        int maxId = 0;

        if (historyResponse != null && historyResponse.messages != null) {
            for (int i = 0; i < historyResponse.messages.size(); i++) {
                int id = historyResponse.messages.get(i).id;
                if (id > maxId) maxId = id;
            }
        }

        firstMessageId = String.valueOf(maxId);
    }

    private void applyServerMessage(Message local, Message server) {
        local.id = server.id;
        local.date = server.date;
        local.out = server.out;

        if (server.text != null) local.text = server.text;
        if (server.peer_id != null) local.peer_id = server.peer_id;

        local.from_id = server.from_id;
        local.media = server.media;
        local.fwd = server.fwd;
        local.act = server.act;
        local.group = server.group;
        local.edit = server.edit;
        local.silent = server.silent;
        local.mention = server.mention;

        if (server.reply != null) {
            if (local.reply == null) {
                local.reply = server.reply;
            } else {
                if (server.reply.replyId == null) server.reply.replyId = local.reply.replyId;
                if (server.reply.peer == null) server.reply.peer = local.reply.peer;
                if (server.reply.quote == null) server.reply.quote = local.reply.quote;
                if (server.reply.msg == null) server.reply.msg = local.reply.msg;
                local.reply = server.reply;
            }
        }

        local.messageState = Message.MessageState.MESSAGE_SENT;
    }

    private void onNetworkLost() {
        historyGap = true;
        historyRefreshScheduled = false;
        chatUpdatesHandler.removeCallbacks(chatUpdateTask);
        chatUpdatesHandler.removeCallbacks(historyRefreshTask);
        statusUpdateHandler.removeCallbacks(statusUpdateTask);

        if (networkStatusText != null) {
            networkStatusText.setVisibility(View.VISIBLE);
            networkStatusText.setText("Network lost...");
            updateNetworkStatusPosition();
        }
    }

    private void onNetworkRestored() {
        if (networkStatusText != null) hideNetworkStatus();
        if (initialSyncInProgress || isLoading) {
            scheduleHistoryRefreshRetry(messagesPollMillis);
            return;
        }
        if (historyResponse == null || !initialLoaded) {
            loadHistory(true, null, 0);
        } else if (historyGap) {
            refreshHistoryAfterGap();
        } else {
            tryStartPolling();
        }

        schedulePendingCheck(0);
    }

    private void hideNetworkStatus() {
        if (networkStatusText == null) return;
        networkStatusText.setText("Network lost...");
        networkStatusText.setVisibility(View.GONE);
    }

    private boolean isMessagePending(Message msg) {
        return msg.id <= 0
                || msg.messageState == Message.MessageState.MESSAGE_SENDING
                || msg.messageState == Message.MessageState.MESSAGE_ERROR;
    }

    private boolean isMessageSent(Message msg) {
        return msg.messageState == Message.MessageState.MESSAGE_SENT
                || msg.messageState == Message.MessageState.MESSAGE_WAS_READ;
    }

    private Message findMessageById(String id) {
        if (id == null || historyResponse == null) return null;

        for (int i = 0; i < historyResponse.messages.size(); i++) {
            Message m = historyResponse.messages.get(i);
            if (String.valueOf(m.id).equals(id)) return m;
        }
        return null;
    }

    private PendingMessage findPendingByMessage(Message message) {
        for (int i = 0; i < pendingMessages.size(); i++) {
            PendingMessage entry = pendingMessages.get(i);
            if (entry.message == message) return entry;
        }
        return null;
    }

    private void updateChatAvatar() {
        if (chatAvatar == null || headerAvatarViewBinder == null || peerId == null) {
            return;
        }

        boolean modern = SettingsManager.CHAT_STYLE_MODERN.equals(settingsManager.getChatStyle());

        if (!modern) {
            chatAvatar.setVisibility(View.GONE);
            headerAvatarBound = false;
            return;
        }

        chatAvatar.setVisibility(View.VISIBLE);

        boolean hasAvatar = false;

        if (historyResponse != null) {
            if (peerId.startsWith("-")) {
                Chat chat = historyResponse.chats != null ? historyResponse.chats.get(peerId) : null;
                hasAvatar = chat != null && chat.p;
            } else {
                User user = historyResponse.users != null ? historyResponse.users.get(peerId) : null;
                hasAvatar = user != null && user.p;
            }
        }

        if (!headerAvatarBound || headerAvatarHasPhoto != hasAvatar || !peerId.equals(headerAvatarPeer)) {
            headerAvatarViewBinder.bind(chatAvatar, peerId, hasAvatar);
            headerAvatarBound = true;
            headerAvatarHasPhoto = hasAvatar;
            headerAvatarPeer = peerId;
        }
    }

    private void addHeader() {
        headerView = getLayoutInflater().inflate(R.layout.chat_header, chatMessages, false);
        chatMessages.addHeaderView(headerView);

        headerText = (TextView) headerView.findViewById(R.id.chatHeaderText);
        headerProgressBar = (ProgressBar) headerView.findViewById(R.id.chatHeaderProgressBar);
        headerButton = (Button) headerView.findViewById(R.id.chatHeaderButton);

        headerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchError = false;
                headerButton.setVisibility(View.GONE);
                headerProgressBar.setVisibility(View.VISIBLE);
                headerText.setText("Loading messages...");
                loadHistory(false, historyResponse.offsetId, historyResponse.offsetDate);
            }
        });
    }

    private void updateNetworkStatusPosition() { // FIXME: dumb
        if (networkStatusText == null) return;

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) networkStatusText.getLayoutParams();

        params.addRule(RelativeLayout.ABOVE, R.id.chatBottomContainer);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);

        networkStatusText.setLayoutParams(params);
    }

    private void navigateToStartup() {
        Intent intent = new Intent(this, StartupActivity.class);
        startActivity(intent);
        finish();
    }
}
