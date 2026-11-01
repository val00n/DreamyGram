package com.val00n.dmpg.ui.chat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.Chat;
import com.val00n.dmpg.model.HistoryResponse;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.model.Update;
import com.val00n.dmpg.model.message.Message;
import com.val00n.dmpg.model.User;
import com.val00n.dmpg.model.message.MessageReply;
import com.val00n.dmpg.model.message.PendingMessage;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.ui.AvatarViewBinder;
import com.val00n.dmpg.ui.main.MainActivity;
import com.val00n.dmpg.ui.StartupActivity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import static android.content.ContentValues.TAG;

public class ChatActivity extends Activity {

    private ProgressBar progressBar;
    private TextView chatTitleText;
    private TextView chatStatusText;
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
    private ImageButton backToBottomButton;

    private MPGramService mpgService;
    private HistoryResponse historyResponse;
    private Instance instance;
    private Handler chatUpdatesHandler;
    private Handler statusUpdateHandler;
    private Runnable statusUpdateTask;
    private Runnable chatUpdateTask;
    private String userToken;
    private String peerId;
    private String displayName;
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

    volatile private boolean isPolling;
    volatile private boolean isPaused;
    volatile private boolean isActivityDied;

    private MessageAction messageAction = MessageAction.NONE;
    private String fwdPeerId;
    private String fwdMessageId;
    private String targetMessageId;

    private ArrayList<Update> updates;
    private String updateLimit = "200";
    private String updateOffset = "-1";
    private int updateFails = 0;

    private final ArrayList<PendingMessage> pendingMessages = new ArrayList<PendingMessage>();
    private int nextLocalMessageId = -1;

    private static final long PENDING_MATCH_WINDOW_SECS = 300;
    private static final int UPDATE_TIMEOUT_SECS = 30;
    private static final int UPDATE_POLL_MILLIS = 3000;
    private static final int STATUS_UPDATE_MILLIS = 5000;
    private static final int REQUEST_FORWARD = 1;

    private enum MessageAction {
        NONE, EDIT, REPLY
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat);

        mpgService = App.getMPGramService(); // TODO prioritize
        instance = App.getInstanceManager().getSelectedInstance();
        userToken = App.getSessionManager().loadSession(instance.baseURL);

        peerId = getIntent().getStringExtra("ID");
        displayName = getIntent().getStringExtra("NAME");
        readOut = getIntent().getIntExtra("READ_OUT", 0);

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
        bottomContainerContent = (LinearLayout) findViewById(R.id.chatBottomContainerContent);
        backToBottomButton = (ImageButton) findViewById(R.id.chatBackToBottomButton);

        chatUpdatesHandler = new Handler();
        statusUpdateHandler = new Handler();

        chatUpdateTask = new Runnable() {
            @Override
            public void run() {
                isPolling = true;
                mpgService.getUpdates(peerId, null, updateLimit, updateOffset, firstMessageId, true, UPDATE_TIMEOUT_SECS, userToken, new MPGramService.ApiCallback<ArrayList<Update>>() {
                    @Override
                    public void onSuccess(ArrayList<Update> result) {
                        if (isActivityDied) return;

                        updates = result;
                        if (result != null && result.size() > 0) {
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
                        if (!isPaused) chatUpdatesHandler.postDelayed(chatUpdateTask, UPDATE_POLL_MILLIS);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isActivityDied) return;
                        Log.d(TAG, "UPDATE FAILED: " + error.getMessage());
                        isPolling = false;

                        if (!App.isSessionDead(error.getMessage(), ChatActivity.this)) {
                            if (updateFails > 10) {
                                mpgService.getLastUpdate(peerId, userToken, UPDATE_TIMEOUT_SECS, new MPGramService.ApiCallback<Update>() {
                                    @Override
                                    public void onSuccess(Update result) {
                                        if (result != null) {
                                            updateOffset = Integer.toString(result.update_id);
                                        } else {
                                            updateOffset = "-1";
                                        }

                                        if (!isPaused) chatUpdatesHandler.postDelayed(chatUpdateTask, UPDATE_POLL_MILLIS);
                                    }

                                    @Override
                                    public void onError(Exception error) {
                                        error.printStackTrace();
                                        if (!App.isSessionDead(error.getMessage(), ChatActivity.this)) {
                                            if (!isPaused) chatUpdatesHandler.postDelayed(chatUpdateTask, UPDATE_POLL_MILLIS);
                                        }
                                    }
                                });

                                updateFails = 0;
                            } else {
                                updateFails++;
                                if (!isPaused) chatUpdatesHandler.postDelayed(chatUpdateTask, UPDATE_POLL_MILLIS * updateFails);
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
                    sendPending(entry);
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
                        if (text != null && text.length() > 0) {
                            sendMessage(text);
                        }
                        break;
                }

                restoreActionMode();
                chatInputEditText.setText("");
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

    protected void onResume() {
        super.onResume();
        Instance selected = App.getInstanceManager().getSelectedInstance();
        if (instance == null || !instance.equals(selected)) {
            navigateToStartup();
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        isActivityDied = true;
        chatUpdatesHandler.removeCallbacks(chatUpdateTask);
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
        chatUpdatesHandler.removeCallbacks(chatUpdateTask);
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
    protected void onStart() {
        super.onStart();
        isPaused = false;
        if (!isPolling) {
            if (historyResponse != null) {
                chatUpdatesHandler.postDelayed(chatUpdateTask, UPDATE_POLL_MILLIS);
                statusUpdateHandler.postDelayed(statusUpdateTask, STATUS_UPDATE_MILLIS);
            } else {
                if (!isLoading) {
                    loadHistory(true, null, 0);
                }
            }
        }
    }

    private void sendMessage(String text) {
        if (historyResponse == null || messagesAdapter == null) return;

        chatSendButton.setEnabled(false);

        final String replyId = messageAction == MessageAction.REPLY && targetMessageId != null ? targetMessageId : null;

        Message pending = new Message(text, nextLocalMessageId--, peerId);
        pending.out = true;
        pending.date = System.currentTimeMillis() / 1000;

        if (replyId != null) {
            pending.reply = buildPendingReply(replyId);
        }

        PendingMessage entry = new PendingMessage(pending, replyId);
        pendingMessages.add(entry);
        historyResponse.messages.add(pending);

        sendPending(entry);
        chatMessages.setSelection(chatMessages.getCount() - 1);
    }

    private void sendPending(final PendingMessage entry) {
        final Message pending = entry.message;

        pending.messageState = Message.MessageState.MESSAGE_SENDING;
        pending.date = System.currentTimeMillis() / 1000;
        messagesAdapter.notifyDataSetChanged();

        mpgService.sendMessage(userToken, peerId, pending.text, entry.replyId, null, null, null,
                new MPGramService.ApiCallback<String>() {
                    @Override
                    public void onSuccess(final String result) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isActivityDied) return;

                                if (pending.id <= 0) {
                                    if ("1".equals(result)) {
                                        pending.messageState = Message.MessageState.MESSAGE_SENT;
                                    } else {
                                        pending.messageState = Message.MessageState.MESSAGE_ERROR;
                                        Toast.makeText(ChatActivity.this,
                                                "Failed to send message: res: " + result,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                    messagesAdapter.notifyDataSetChanged();
                                }

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

                                if (pending.id <= 0) {
                                    pending.messageState = Message.MessageState.MESSAGE_ERROR;
                                    messagesAdapter.notifyDataSetChanged();
                                    Toast.makeText(ChatActivity.this,
                                            "Failed to send message: " + error.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }

                                chatSendButton.setEnabled(true);
                            }
                        });
                    }
                });
    }

    private void update(ArrayList<Update> u) {
        for (final Update upd : u) {
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
                        }
                        if (isDupe) continue;

                        PendingMessage match = serverMsg.out ? findPendingMatch(serverMsg) : null;

                        if (match != null) {
                            applyServerMessage(match.message, serverMsg);
                            pendingMessages.remove(match);
                            messagesAdapter.notifyDataSetChanged();
                        } else {
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
        if (messages == null || messages.isEmpty()) return;
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
        if (messageAction == MessageAction.EDIT) {
            chatInputEditText.setText(draftMessage);
        }
        messageAction = MessageAction.NONE;
    }

    private void showMessageActionMenu(final Message msg) {
        final String[] options = {"Delete", "Copy", "Edit", "Forward", "Reply", "Share", "Info"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose action");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        if (isMessagePending(msg)) {
                            PendingMessage entry = findPendingByMessage(msg);
                            if (entry != null) {
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
                    case 1:
                        copy(msg.text);
                        break;
                    case 2:
                        if (isMessagePending(msg)) {
                            Toast.makeText(ChatActivity.this, "Cannot edit pending message", Toast.LENGTH_SHORT).show();
                        } else if (msg.id <= 0) {
                            Toast.makeText(ChatActivity.this, "Cannot edit message without server ID", Toast.LENGTH_SHORT).show();
                        } else {
                            editMessage(msg);
                        }
                        break;
                    case 3:
                        if (isMessagePending(msg)) {
                            Toast.makeText(ChatActivity.this, "Cannot forward pending message", Toast.LENGTH_SHORT).show();
                        } else {
                            forwardMessage(msg);
                        }
                        break;
                    case 4:
                        if (isMessagePending(msg)) {
                            Toast.makeText(ChatActivity.this, "Cannot reply to pending message", Toast.LENGTH_SHORT).show();
                        } else {
                            setReply(msg);
                        }
                        break;
                    case 5:
                        if (msg.text != null) {
                            shareMessage(msg.text);
                        } else {
                            Toast.makeText(ChatActivity.this, "Message is empty", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case 6:
                        showMessageInfo(msg);
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
            messagesAdapter = new MessagesAdapter(historyResponse.messages, historyResponse.users, historyResponse.chats, isSavedMessages, new AvatarViewBinder(App.getCacheManager()));
            chatMessages.setAdapter(messagesAdapter);
        } else {
            int pos = chatMessages.getFirstVisiblePosition();
            View v = chatMessages.getChildAt(0);
            int top = (v == null) ? 0 : v.getTop();

            int oldCount = messagesAdapter.getCount();
            messagesAdapter.notifyDataSetChanged();
            int inserted = messagesAdapter.getCount() - oldCount;

            chatMessages.setSelectionFromTop(pos + inserted, top);
        }

        progressBar.setVisibility(View.GONE);
        showStatus();
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
            if (m.out && (m.id > 0 || isMessageSent(m)) && m.id <= limit
                    && m.messageState != Message.MessageState.MESSAGE_ERROR
                    && m.messageState != Message.MessageState.MESSAGE_SENDING) {
                historyResponse.messages.get(i).messageState = Message.MessageState.MESSAGE_WAS_READ;
            }
        }
    }

    private void loadHistory(final boolean initial, final String offsetId, final long offsetDate) {
        if (initial) {
            loadedHistory = 0;
        } else {
            headerText.setVisibility(View.VISIBLE);
            headerText.setText("Loading messages...");
            headerProgressBar.setVisibility(View.VISIBLE);
            headerButton.setVisibility(View.GONE);
        }

        isLoading = true;

        mpgService.getHistory(peerId, offsetId, offsetDate, userToken, new MPGramService.ApiCallback<HistoryResponse>() {
            @Override
            public void onSuccess(final HistoryResponse result) {
               runOnUiThread(new Runnable() {
                   @Override
                   public void run() {
                       loadedHistory += result.messages.size();
                       Collections.reverse(result.messages);

                       isLoading = false;

                       if (initial) {
                           historyResponse = result;

                           markRead(readOut);

                           initialLoaded = true;
                           showMessages();

                           if (!historyResponse.messages.isEmpty()) {
                               firstMessageId = String.valueOf(
                                       historyResponse.messages.get(historyResponse.messages.size() - 1).id
                               );
                           }

                           canType = true;
                           if (peerId != null && peerId.startsWith("-")) {
                               canType = false;
                               if (historyResponse.chats != null) {
                                   Chat chat = historyResponse.chats.get(peerId);
                                   if (chat != null) {
                                       canType = !chat.c && !chat.l;
                                   }
                               }
                           } else {
                               canType = true;
                           }

                           if (canType) {
                               bottomContainerContent.setVisibility(View.VISIBLE);
                           }

                           mpgService.getLastUpdate(peerId, userToken, UPDATE_TIMEOUT_SECS, new MPGramService.ApiCallback<Update>() {
                               @Override
                               public void onSuccess(Update result) {
                                   if (result != null) {
                                       updateOffset = Integer.toString(result.update_id);
                                   }
                                   updates = new ArrayList<Update>();

                                   if (!isPaused) chatUpdatesHandler.postDelayed(chatUpdateTask, UPDATE_POLL_MILLIS);
                               }

                               @Override
                               public void onError(Exception error) {
                                   error.printStackTrace();
                                   if (!isPaused) chatUpdatesHandler.postDelayed(chatUpdateTask, UPDATE_POLL_MILLIS);
                               }
                           });
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
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(ChatActivity.this, "Failed to load history " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            } else {
                                fetchError = true;
                                headerView.setVisibility(View.VISIBLE);
                                headerProgressBar.setVisibility(View.GONE);
                                headerButton.setVisibility(View.VISIBLE);
                                headerText.setText("Failed to load: " + error.getMessage());
                            }
                        }
                    });
                }
            }
        });
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
        PendingMessage errorFallback = null;

        for (int i = 0; i < pendingMessages.size(); i++) {
            PendingMessage entry = pendingMessages.get(i);
            Message local = entry.message;

            if (local.id > 0) continue;
            if (local.text == null || !local.text.equals(serverMsg.text)) continue;

            if (local.peer_id != null && serverMsg.peer_id != null
                    && !local.peer_id.equals(serverMsg.peer_id)) continue;

            long delta = serverMsg.date - local.date;
            if (delta < -PENDING_MATCH_WINDOW_SECS || delta > PENDING_MATCH_WINDOW_SECS) {
                continue;
            }

            if (local.messageState == Message.MessageState.MESSAGE_ERROR) {
                if (errorFallback == null) errorFallback = entry;
                continue;
            }

            return entry;
        }

        return errorFallback;
    }

    private void applyServerMessage(Message local, Message server) {
        local.id = server.id;
        local.date = server.date;
        local.out = server.out;

        if (server.text != null) local.text = server.text;
        if (server.peer_id != null) local.peer_id = server.peer_id;

        local.from_id = server.from_id;
        local.reply = server.reply != null ? server.reply : local.reply;
        local.media = server.media;
        local.fwd = server.fwd;
        local.act = server.act;
        local.group = server.group;
        local.edit = server.edit;
        local.silent = server.silent;
        local.mention = server.mention;

        local.messageState = Message.MessageState.MESSAGE_SENT;
    }

    private boolean isMessagePending(Message msg) {
        return msg.messageState == Message.MessageState.MESSAGE_SENDING
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

    private void navigateToStartup() {
        Intent intent = new Intent(this, StartupActivity.class);
        startActivity(intent);
        finish();
    }
}
