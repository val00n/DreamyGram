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
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.ui.main.MainActivity;
import com.val00n.dmpg.ui.startup.StartupActivity;
import com.val00n.dmpg.utils.DateUtils;
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
    private Message pendingMessage;
    private boolean isTyping;
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
                public void onSuccess(User result) {
                    if (isActivityDied) return;
                    if (result != null && reqBase.equals(App.getInstanceManager().getSelectedInstanceBaseUrl())) {
                        App.selfId = result.id;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (messagesAdapter != null) messagesAdapter.notifyDataSetChanged();
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
                if (adapterPos < 0 || adapterPos >= historyResponse.messages.size()) {
                    return false;
                }

                if (historyResponse.messages.get(adapterPos).dateSeparator || historyResponse.messages.get(adapterPos).act != null) {
                    return false;
                }

                if (historyResponse.messages.get(adapterPos).messageState == Message.MessageState.MESSAGE_ERROR) {
                    return false;
                }

                showMessageActionMenu(adapterPos);
                return false;
            }
        });

        chatMessages.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!chatSendButton.isEnabled()) {
                    return;
                }

                int adapterPos = position - 1;
                if (adapterPos < 0 || adapterPos >= historyResponse.messages.size()) {
                    return;
                }

                if (historyResponse.messages.get(adapterPos).messageState == Message.MessageState.MESSAGE_ERROR) {
                    String pendingText = historyResponse.messages.get(adapterPos).text;
                    pendingMessage = null;
                    historyResponse.messages.remove(adapterPos);
                    sendMessage(pendingText);
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
                String text = chatInputEditText.getText().toString();
                
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
        if (!instance.equals(App.getInstanceManager().getSelectedInstance())) {
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
        chatSendButton.setEnabled(false);
        String replyId = messageAction == MessageAction.REPLY && targetMessageId != null ? targetMessageId : null;

        pendingMessage = new Message(text, 0, peerId);
        pendingMessage.out = true;
        pendingMessage.date = System.currentTimeMillis() / 1000;
        if (replyId != null) {
            pendingMessage.reply = new MessageReply("Loading...");
        }
        historyResponse.messages.add(pendingMessage);
        messagesAdapter.notifyDataSetChanged();
        chatMessages.setSelection(chatMessages.getCount() - 1);

        mpgService.sendMessage(userToken, peerId, text, replyId, null, null, null, new MPGramService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                if (pendingMessage != null) {
                    if ("1".equals(result)) {
                        pendingMessage.messageState = Message.MessageState.MESSAGE_SENT;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                messagesAdapter.notifyDataSetChanged();
                            }
                        });
                    } else {
                        pendingMessage.messageState = Message.MessageState.MESSAGE_ERROR;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                messagesAdapter.notifyDataSetChanged();
                            }
                        });
                        onError(new Exception("res: " + result));
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        chatSendButton.setEnabled(true);
                    }
                });
            }

            @Override
            public void onError(final Exception error) {
                error.printStackTrace();
                if (!App.isSessionDead(error.getMessage(), ChatActivity.this)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (pendingMessage != null) {
                                pendingMessage.messageState = Message.MessageState.MESSAGE_ERROR;
                                messagesAdapter.notifyDataSetChanged();
                            }
                            chatSendButton.setEnabled(true);
                            Toast.makeText(ChatActivity.this, "Failed to send message: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void update(ArrayList<Update> u) {
        for (final Update upd : u) {
            switch (upd.type) {
                case "updateNewChannelMessage":
                case "updateNewMessage":
                    if (upd.msg != null) {
                        boolean isDupe = false;
                        for (int i = 0; i < historyResponse.messages.size(); i++) {
                            if (upd.msg.id == historyResponse.messages.get(i).id) {
                                isDupe = true;
                                break;
                            }
                        }
                        if (isDupe) continue;

                        if (upd.msg.out == true && pendingMessage != null) {
                            pendingMessage.id = upd.msg.id;
                            pendingMessage.date = upd.msg.date;
                            pendingMessage.reply = upd.msg.reply;
                            pendingMessage.from_id = upd.msg.from_id;
                            pendingMessage.media = upd.msg.media; // is this will work? i dunno =/
                            pendingMessage.messageState = Message.MessageState.MESSAGE_SENT;

                            messagesAdapter.notifyDataSetChanged();
                            pendingMessage = null;
                        } else {
                            historyResponse.messages.add(upd.msg);
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
            if (historyResponse.messages.isEmpty()) {
                messagesAdapter.notifyDataSetChanged();
                return;
            }

            if (historyResponse.messages.get(historyResponse.messages.size() - 1).dateSeparator) {
                historyResponse.messages.remove(historyResponse.messages.size() - 1);
            }

            if (historyResponse.messages.isEmpty()) {
                messagesAdapter.notifyDataSetChanged();
                return;
            }

            for (int i = historyResponse.messages.size() - 2; i >= 0; i--) {
                if (historyResponse.messages.get(i).dateSeparator && (historyResponse.messages.get(i+1).dateSeparator)) {
                    historyResponse.messages.remove(i);
                }
            }

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

    private void showMessageActionMenu(final int position) {
        final String[] options = {"Delete", "Copy", "Edit", "Forward", "Reply", "Share", "Info"};
        final Message msg = historyResponse.messages.get(position);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose action");

        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
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
                        break;
                    case 1:
                        copy(msg.text);
                        break;
                    case 2:
                        editMessage(msg);
                        break;
                    case 3:
                        forwardMessage(msg);
                        break;
                    case 4:
                        setReply(msg);
                        break;
                    case 5:
                        if (msg.text != null) {
                            shareMessage(msg.text);
                        } else {
                            Toast.makeText(ChatActivity.this, "Message is empty", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case 6:
                        showMessageInfo(position);
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
                            showMessages(0);
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

    private void showMessageInfo(int position) {
        Message msg = historyResponse.messages.get(position);
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

    private void showMessages(final int inserted) {
        if (messagesAdapter == null) {
            messagesAdapter = new MessagesAdapter(ChatActivity.this, historyResponse.messages, historyResponse.users, historyResponse.chats);
            messagesAdapter.setPeerId(peerId);
            chatMessages.setAdapter(messagesAdapter);
        } else {
            int pos = chatMessages.getFirstVisiblePosition();
            View v = chatMessages.getChildAt(0);
            int top = (v == null) ? 0 : v.getTop();

            messagesAdapter.notifyDataSetChanged();

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
            if (m.out && m.id <= limit
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
                       result.messages = insertDateSeparators(result.messages);

                       isLoading = false;

                       if (initial) {
                           historyResponse = result;

                           markRead(readOut);

                           initialLoaded = true;
                           showMessages(0);

                           if (historyResponse.messages.size() > 0 && !historyResponse.messages.isEmpty()) {
                                firstMessageId = String.valueOf(historyResponse.messages.get(historyResponse.messages.size() - 1).id); // date separator shouldn't be last
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
                           int sizeBefore = historyResponse.messages.size();
                           historyResponse.add(result);
                           final int inserted = historyResponse.messages.size() - sizeBefore;

                           if (loadedHistory >= result.totalCount || result.messages.isEmpty()) {
                               endReached = true;

                               headerText.setVisibility(View.GONE);
                               headerProgressBar.setVisibility(View.GONE);
                               headerButton.setVisibility(View.GONE);
                           }

                           showMessages(inserted);
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

    private ArrayList<Message> insertDateSeparators(ArrayList<Message> messages) {
        ArrayList<Message> result = new ArrayList<>(messages.size() * 2);
        String prevDay = null;

        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);

            if (m.act == null) {
                String day = DateUtils.dayKey(m.date);

                if (!day.equals(prevDay)) {
                    result.add(Message.dateSeparator(m.date));
                }

                prevDay = day;
            }

            result.add(m);
        }

        return result;
    }

    private void navigateToStartup() {
        Intent intent = new Intent(this, StartupActivity.class);
        startActivity(intent);
        finish();
    }
}
