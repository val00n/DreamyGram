package com.val00n.dmpg.ui.chat;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.val00n.dmpg.R;

public class ChatActivity extends Activity {
    // UI
    private TextView chatTitleText;
    private TextView chatStatusText;
    private EditText chatInputEditText;
    private ImageButton chatAttachButton;
    private ImageButton chatSendButton;
    private ImageButton chatOptionsButton;
    private ListView chatMessages;
    // dialog
    private String id;
    private String displayName;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat);

        id = getIntent().getStringExtra("ID");
        displayName = getIntent().getStringExtra("NAME");

        chatTitleText = (TextView) findViewById(R.id.chatTitle);
        chatStatusText = (TextView) findViewById(R.id.chatStatus);
        chatOptionsButton = (ImageButton) findViewById(R.id.chatOptionsButton);
        chatMessages = (ListView) findViewById(R.id.chatMessages);
        chatInputEditText = (EditText) findViewById(R.id.chatInputEditText);
        chatAttachButton = (ImageButton) findViewById(R.id.chatAttachButton);
        chatSendButton = (ImageButton) findViewById(R.id.chatSendButton);

        chatTitleText.setText(displayName);
    }
}
