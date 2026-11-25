package com.val00n.dmpg.ui.chat;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.cache.CacheManager;
import com.val00n.dmpg.model.FileParams;
import com.val00n.dmpg.model.message.Message;
import com.val00n.dmpg.model.message.MessageMedia;
import com.val00n.dmpg.services.MessageFileActions;

public class ImageViewerActivity extends Activity {

    private ImageView imageView;
    private ProgressBar progressBar;

    private String peer;
    private Message msg;

    private static final int MENU_SAVE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_viewer);

        imageView = (ImageView) findViewById(R.id.imageViewerFullscreenImage);
        progressBar = (ProgressBar) findViewById(R.id.imageViewerProgressBar);

        peer = getIntent().getStringExtra("PEER");
        final String messageId = getIntent().getStringExtra("MESSAGE_ID");
        final String mime = getIntent().getStringExtra("MEDIA_MIME");
        final String name = getIntent().getStringExtra("MEDIA_NAME");
        final long size = getIntent().getLongExtra("MEDIA_SIZE", 0L);

        msg = new Message("", Integer.parseInt(messageId), peer);
        msg.media = new MessageMedia();
        msg.media.type = "photo";
        msg.media.mime = mime != null ? mime : "image/jpeg";
        msg.media.name = name;
        msg.media.size = size;

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        imageView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                MessageFileActions.showSaveMenu(ImageViewerActivity.this, peer, msg, false);
                return true;
            }
        });

        if (peer == null || messageId == null) {
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        App.getCacheManager().loadImage(peer, messageId, FileParams.preview(), true, new CacheManager.ImageCallback() {
            @Override
            public void onLoaded(Bitmap bitmap) {
                progressBar.setVisibility(View.GONE);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                } else {
                    Toast.makeText(ImageViewerActivity.this, R.string.image_viewer_failed_load_image, Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onError(Exception error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ImageViewerActivity.this, R.string.image_viewer_failed_load_image, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem deleteItem = menu.add(0, MENU_SAVE, 1, R.string.action_save);
        deleteItem.setIcon(android.R.drawable.ic_menu_save);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case MENU_SAVE:
                MessageFileActions.showSaveMenu(ImageViewerActivity.this, peer, msg, false);
                return true;
            default:
                return super.onOptionsItemSelected(menuItem);
        }
    }
}
