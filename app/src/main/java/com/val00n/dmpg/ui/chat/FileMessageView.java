package com.val00n.dmpg.ui.chat;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.cache.CacheManager;
import com.val00n.dmpg.model.FileParams;
import com.val00n.dmpg.model.message.Message;
import com.val00n.dmpg.services.FileHelper;
import com.val00n.dmpg.services.MessageFileActions;

public class FileMessageView extends LinearLayout {
    private ImageView icon;
    private TextView name;
    private TextView meta;

    private Context context;
    private Message message;
    private String peer;

    private String filename;
    private String mime;

    public FileMessageView(Context context) {
        super(context);
        init(context);
    }

    public FileMessageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.message_attachment, this, true);
        icon = (ImageView) findViewById(R.id.fileIcon);
        name = (TextView) findViewById(R.id.fileName);
        meta = (TextView) findViewById(R.id.fileSize);
        this.context = context;
    }

    public void bind(final Message msg, final String peer) {
        if (msg == null || msg.media == null) {
            return;
        }

        if (msg.media.mime == null || msg.media.name == null) {
            return;
        }

        icon.setImageDrawable(null);

        this.message = msg;
        this.peer = peer;

        filename = msg.media.name;
        mime = msg.media.mime;

        /*String displayName = msg.media.name; // TODO
        if (displayName == null || displayName.isEmpty()) {
            displayName = msg.media.title != null ? msg.media.title : "file";
        }
        name.setText(displayName);
        */
        //if (msg.media.time > 0) { // TODO
        //if (msg.media.artist != null) { // TODO

        name.setText(filename);

        String metaText = "";
        if (msg.media.size > 0) metaText = FileHelper.formatSize(msg.media.size);

        meta.setText(metaText);

        if (msg.media.thumb) {
            App.getCacheManager().loadImage(peer, String.valueOf(msg.id), FileParams.fileThumb(), new CacheManager.ImageCallback() {
                @Override
                public void onLoaded(Bitmap bitmap) {
                    icon.setImageBitmap(bitmap);
                }
                @Override
                public void onError(Exception error) {
                    icon.setVisibility(View.GONE);
                }
            });
        } else {
            icon.setImageResource(android.R.drawable.ic_menu_save);
        }

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (message == null) {
                    return;
                }

                if (MessageFileActions.openIfSaved(context, peer, message, false)) {
                    return;
                }

                if (context instanceof Activity) {
                    MessageFileActions.showSaveMenu((Activity) context, peer, message, true);
                }
            }
        });
    }
}
