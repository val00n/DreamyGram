package com.val00n.dmpg.ui.chat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.Chat;
import com.val00n.dmpg.model.HistoryResponse;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.model.Message;
import com.val00n.dmpg.model.User;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.ui.startup.StartupActivity;
import com.val00n.dmpg.utils.DateUtils;

import java.util.ArrayList;
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

    private HistoryResponse historyResponse;
    private String peerId;
    private String displayName;
    private MPGramService mpgService;
    private Instance instance;
    private String userToken;
    private int loadedHistory;
    private boolean isLoading;
    private boolean endReached;
    private boolean initialLoaded;
    private boolean fetchError;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat);

        mpgService = App.getMPGramService();
        instance = App.getInstanceManager().getSelectedInstance();
        userToken = App.getSessionManager().loadSession(instance.baseURL);

        peerId = getIntent().getStringExtra("ID");
        displayName = getIntent().getStringExtra("NAME");

        if (peerId == null) {
            Toast.makeText(ChatActivity.this, "Invalid peer ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progressBar = (ProgressBar) findViewById(R.id.chatProgressBar);
        chatTitleText = (TextView) findViewById(R.id.chatTitle);
        chatStatusText = (TextView) findViewById(R.id.chatStatus);
        chatOptionsButton = (ImageButton) findViewById(R.id.chatOptionsButton);
        chatMessages = (ListView) findViewById(R.id.chatMessages);
        chatInputEditText = (EditText) findViewById(R.id.chatInputEditText);
        chatAttachButton = (ImageButton) findViewById(R.id.chatAttachButton);
        chatSendButton = (ImageButton) findViewById(R.id.chatSendButton);

        chatTitleText.setText(displayName);

        addHeader();
        loadHistory(true, null, 0);

        chatMessages.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {

            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (firstVisibleItem == 0 && !isLoading && !fetchError && !endReached
                        && initialLoaded && totalItemCount > visibleItemCount) {
                    loadHistory(false, historyResponse.offsetId, historyResponse.offsetDate);
                }
            }
        });
    }

    protected void onResume() {
        super.onResume();
        if (!instance.equals(App.getInstanceManager().getSelectedInstance())) {
            navigateToStartup();
        }
    }

    private void showMessages(final int inserted) {
        if (messagesAdapter == null) {
            messagesAdapter = new MessagesAdapter(ChatActivity.this, historyResponse.messages, historyResponse.users, historyResponse.chats);
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
        String status = null;

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

        if (status != null) {
            chatStatusText.setText(status);
        } else {
            chatStatusText.setVisibility(View.GONE);
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
                       insertDateSeparators(result.messages);

                       isLoading = false;

                       if (initial) {
                           historyResponse = result;
                           initialLoaded = true;
                           showMessages(0);
                       } else {
                           int sizeBefore = historyResponse.messages.size();
                           historyResponse.add(result);
                           final int inserted = historyResponse.messages.size() - sizeBefore;

                           if (loadedHistory >= result.count || result.messages.isEmpty()) {
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

    private void insertDateSeparators(ArrayList<Message> messages) {
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

        messages.clear();
        messages.addAll(result);
    }

    private void navigateToStartup() {
        Intent intent = new Intent(this, StartupActivity.class);
        startActivity(intent);
        finish();
    }
}
