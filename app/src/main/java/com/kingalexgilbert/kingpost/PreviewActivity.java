package com.kingalexgilbert.kingpost;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

public final class PreviewActivity extends Activity {

    private VideoView videoView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        videoView = findViewById(R.id.previewVideoView);
        progressBar = findViewById(R.id.previewProgress);
        findViewById(R.id.closePreviewButton).setOnClickListener(view -> finish());

        Uri videoUri = getIntent() == null ? null : getIntent().getData();
        if (videoUri == null) {
            showFailureAndClose();
            return;
        }

        try {
            MediaController controller = new MediaController(this);
            controller.setAnchorView(videoView);
            videoView.setMediaController(controller);
            videoView.setVideoURI(videoUri);
            videoView.setOnPreparedListener(this::onPrepared);
            videoView.setOnErrorListener((player, what, extra) -> {
                showFailureAndClose();
                return true;
            });
            videoView.requestFocus();
            videoView.start();
        } catch (RuntimeException exception) {
            showFailureAndClose();
        }
    }

    private void onPrepared(MediaPlayer player) {
        progressBar.setVisibility(View.GONE);
        player.setLooping(true);
        try {
            videoView.start();
        } catch (RuntimeException exception) {
            showFailureAndClose();
        }
    }

    private void showFailureAndClose() {
        Toast.makeText(
                this,
                "This video could not be played in the preview, but it can still be shared.",
                Toast.LENGTH_LONG
        ).show();
        finish();
    }

    @Override
    protected void onPause() {
        if (videoView != null) {
            try {
                videoView.pause();
            } catch (RuntimeException ignored) {
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (videoView != null) {
            try {
                videoView.stopPlayback();
            } catch (RuntimeException ignored) {
            }
        }
        super.onDestroy();
    }
}
