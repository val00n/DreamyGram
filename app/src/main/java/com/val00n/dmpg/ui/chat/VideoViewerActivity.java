package com.val00n.dmpg.ui.chat;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.cache.CacheManager;
import com.val00n.dmpg.model.message.Message;
import com.val00n.dmpg.model.message.MessageMedia;
import com.val00n.dmpg.services.MessageFileActions;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import static android.content.ContentValues.TAG;

public class VideoViewerActivity extends Activity implements SurfaceHolder.Callback, MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener {

    private FrameLayout videoContainer;
    private SurfaceView surfaceView;
    private ProgressBar progressBar;
    private MediaController mediaController;
    private MediaPlayer mediaPlayer;
    private FileInputStream videoFis;

    private String peer;
    private String messageId;
    private String filename;
    private Message msg;

    private File videoFile;
    private SurfaceHolder currentHolder;
    private boolean isSurfaceReady = false;

    private static final int MENU_SAVE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_viewer);

        peer = getIntent().getStringExtra("PEER");
        messageId = getIntent().getStringExtra("MESSAGE_ID");
        final String mime = getIntent().getStringExtra("MEDIA_MIME");
        filename = getIntent().getStringExtra("FILENAME");

        msg = new Message("", Integer.parseInt(messageId), peer);
        msg.media = new MessageMedia();
        msg.media.type = "video";
        msg.media.mime = mime != null ? mime : "video/mp4";

        if (filename == null || filename.trim().length() == 0) {
            filename = MessageFileActions.getFileName(peer, msg, true);
        }
        if (filename == null || filename.trim().length() == 0) {
            filename = "video_" + messageId + ".mp4";
        }
        msg.media.name = filename;

        Log.d(TAG, "onCreate: PEER: " + peer + ", MSG: " + messageId + ", FILE: " + filename);

        videoContainer = (FrameLayout) findViewById(R.id.videoContainer);
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        surfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceView.getHolder().addCallback(this);

        mediaController = new MediaController(this);

        videoContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaController != null) {
                    mediaController.show(3000);
                }
            }
        });
        videoContainer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);


        startVideoPlayback();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem deleteItem = menu.add(0, MENU_SAVE, 1, "Save");
        deleteItem.setIcon(android.R.drawable.ic_menu_save);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case MENU_SAVE:
                MessageFileActions.showSaveMenu(VideoViewerActivity.this, peer, msg, false);
                return true;
            default:
                return super.onOptionsItemSelected(menuItem);
        }
    }

        private void startVideoPlayback() {
        if (filename == null || filename.isEmpty()) {
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        App.getCacheManager().loadVideo(peer, messageId, filename, new CacheManager.VideoCallback() {
            @Override
            public void onLoaded(final File file) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (file == null || !file.exists()) {
                            Log.e(TAG, "onLoaded: File is null or does not exist!");
                            Toast.makeText(VideoViewerActivity.this, "File not found in cache", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }

                        videoFile = file;
                        Log.d(TAG, "Video file ready, size: " + file.length() + " bytes. Path: " + file.getAbsolutePath());

                        tryInitPlayer();
                    }
                });
            }

            @Override
            public void onError(final Exception error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.e(TAG, "CacheManager loadVideo error", error);
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(VideoViewerActivity.this,
                                "Failed to load video: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }
        });
    }

    private void tryInitPlayer() {
        boolean hasFile = (videoFile != null && videoFile.exists());
        boolean noPlayer = (mediaPlayer == null);


        if (isSurfaceReady && hasFile && noPlayer) {
            initMediaPlayer(currentHolder);
        }
    }

    private void initMediaPlayer(SurfaceHolder holder) {
        if (videoFile == null || !videoFile.exists()) {
            return;
        }
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnErrorListener(this);

            mediaPlayer.setDisplay(holder);

            videoFis = new FileInputStream(videoFile);
            mediaPlayer.setDataSource(videoFis.getFD());

            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            Log.e(TAG, "Failed to set data source", e);
            Toast.makeText(this, "Failed to open video file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated");
        this.currentHolder = holder;
        this.isSurfaceReady = true;

        tryInitPlayer();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed");
        this.isSurfaceReady = false;
        this.currentHolder = null;
        if (mediaPlayer != null) {
            mediaPlayer.setDisplay(null);
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        Log.d(TAG, "onPrepared: SUCCESS!");
        progressBar.setVisibility(View.GONE);

        int videoWidth = mp.getVideoWidth();
        int videoHeight = mp.getVideoHeight();

        if (videoWidth > 0 && videoHeight > 0) {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;

            float scaleWidth = (float) screenWidth / videoWidth;
            float scaleHeight = (float) screenHeight / videoHeight;

            float scale = Math.min(scaleWidth, scaleHeight);

            int targetWidth = (int) (videoWidth * scale);
            int targetHeight = (int) (videoHeight * scale);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(targetWidth, targetHeight);
            params.gravity = Gravity.CENTER;
            surfaceView.setLayoutParams(params);
        }

        mediaController.setMediaPlayer(new MediaPlayerControlWrapper(mp));
        mediaController.setAnchorView(surfaceView);
        mediaController.show();
        mp.start();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "MediaPlayer Error: what=" + what + ", extra=" + extra);
        Toast.makeText(this, "Video playback error (" + what + ", " + extra + ")", Toast.LENGTH_SHORT).show();
        finish();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer();
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (videoFis != null) {
            try {
                videoFis.close();
                Log.d(TAG, "FileInputStream closed safely");
            } catch (IOException e) {
                e.printStackTrace();
            }
            videoFis = null;
        }
    }

    private static class MediaPlayerControlWrapper implements MediaController.MediaPlayerControl {
        private final MediaPlayer mp;

        public MediaPlayerControlWrapper(MediaPlayer mp) {
            this.mp = mp;
        }

        @Override public void start() { mp.start(); }
        @Override public void pause() { mp.pause(); }
        @Override public int getDuration() { return mp.getDuration(); }
        @Override public int getCurrentPosition() { return mp.getCurrentPosition(); }
        @Override public void seekTo(int pos) { mp.seekTo(pos); }
        @Override public boolean isPlaying() { return mp.isPlaying(); }
        @Override public int getBufferPercentage() { return 0; }
        @Override public boolean canPause() { return true; }
        @Override public boolean canSeekBackward() { return true; }
        @Override public boolean canSeekForward() { return true; }
        @Override public int getAudioSessionId() { return mp.getAudioSessionId(); }
    }
}