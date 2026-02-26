package com.example.playerdemo.ui.player;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.playerdemo.data.repository.ConfigManager;
import com.example.playerdemo.data.repository.SshManager;
import com.example.playerdemo.databinding.ActivityVideoPlayerBinding;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

public class VideoPlayerActivity extends AppCompatActivity {
    private ActivityVideoPlayerBinding binding;
    private ExoPlayer player;
    
    private String videoPath;
    private String videoName;
    private boolean isRemote;
    private String startTime;
    private float currentPlaybackSpeed = 1.0f;
    private boolean isFullScreen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVideoPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        videoPath = getIntent().getStringExtra("video_path");
        videoName = getIntent().getStringExtra("video_name");
        isRemote = getIntent().getBooleanExtra("is_remote", false);
        startTime = getIntent().getStringExtra("start_time");

        setupViews();
        initializePlayer();
    }

    private void setupViews() {
        binding.toolbar.setTitle(videoName);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        
        binding.playerView.setOnClickListener(v -> toggleControls());
        
        binding.btnSpeed.setOnClickListener(v -> showSpeedDialog());
        binding.btnFullscreen.setOnClickListener(v -> toggleFullScreen());
        
        binding.btnBack.setOnClickListener(v -> {
            if (isFullScreen) {
                toggleFullScreen();
            } else {
                finish();
            }
        });
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        binding.playerView.setPlayer(player);
        
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    binding.progressBar.setVisibility(View.VISIBLE);
                } else if (playbackState == Player.STATE_READY) {
                    binding.progressBar.setVisibility(View.GONE);
                } else if (playbackState == Player.STATE_ENDED) {
                    showPlaybackEndedDialog();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    binding.btnSpeed.setVisibility(View.VISIBLE);
                    binding.btnFullscreen.setVisibility(View.VISIBLE);
                }
            }
        });

        if (isRemote) {
            loadRemoteVideo();
        } else {
            loadLocalVideo();
        }
    }

    private void loadRemoteVideo() {
        SshManager sshManager = SshManager.getInstance();
        
        String videoStreamUrl = "http://" + 
                sshManager.getCurrentConfig().getWindowsHost() + 
                ":8080" + videoPath.replace("/mnt/c/", "/").replace(" ", "%20");
        
        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(10000);
        
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this, httpDataSourceFactory);

        ProgressiveMediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(videoStreamUrl))); // Fix: Use MediaItem.fromUri()
        
        player.setMediaSource(mediaSource);
        player.prepare();
        
        if (startTime != null && !startTime.isEmpty()) {
            seekToTime(startTime);
        }
        
        player.play();
    }

    private void loadLocalVideo() {
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoPath));
        player.setMediaItem(mediaItem);
        player.prepare();
        
        if (startTime != null && !startTime.isEmpty()) {
            seekToTime(startTime);
        }
        
        player.play();
    }

    private void seekToTime(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            String secPart = parts[2].split("\\.")[0];
            int seconds = Integer.parseInt(secPart);
            
            long positionMs = (hours * 3600 + minutes * 60 + seconds) * 1000L;
            player.seekTo(positionMs);
        } catch (Exception e) {
            // Ignore seek error
        }
    }

    private void toggleControls() {
        if (binding.controlsContainer.getVisibility() == View.VISIBLE) {
            binding.controlsContainer.setVisibility(View.GONE);
            binding.toolbar.setVisibility(View.GONE);
        } else {
            binding.controlsContainer.setVisibility(View.VISIBLE);
            binding.toolbar.setVisibility(View.VISIBLE);
        }
    }

    private void showSpeedDialog() {
        String[] speeds = {"0.5x", "0.75x", "1.0x (正常)", "1.25x", "1.5x", "2.0x"};
        float[] speedValues = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        
        new AlertDialog.Builder(this)
                .setTitle("播放速度")
                .setItems(speeds, (dialog, which) -> {
                    currentPlaybackSpeed = speedValues[which];
                    player.setPlaybackParameters(new PlaybackParameters(currentPlaybackSpeed));
                    Toast.makeText(this, "播放速度: " + speeds[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void toggleFullScreen() {
        isFullScreen = !isFullScreen;
        
        if (isFullScreen) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            binding.toolbar.setVisibility(View.GONE);
            binding.controlsContainer.setVisibility(View.GONE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            binding.toolbar.setVisibility(View.VISIBLE);
            binding.controlsContainer.setVisibility(View.VISIBLE);
        }
    }

    private void showPlaybackEndedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("播放结束")
                .setMessage("视频已播放完毕")
                .setPositiveButton("重新播放", (dialog, which) -> {
                    player.seekTo(0);
                    player.play();
                })
                .setNegativeButton("返回", (dialog, which) -> finish())
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            isFullScreen = true;
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            isFullScreen = false;
        }
    }

    @Override
    public void onBackPressed() {
        if (isFullScreen) {
            toggleFullScreen();
        } else {
            super.onBackPressed();
        }
    }
}
