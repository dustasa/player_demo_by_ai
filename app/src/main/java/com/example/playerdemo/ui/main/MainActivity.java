package com.example.playerdemo.ui.main;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.playerdemo.R;
import com.example.playerdemo.data.model.VideoFile;
import com.example.playerdemo.data.repository.AutoCutManager;
import com.example.playerdemo.data.repository.ConfigManager;
import com.example.playerdemo.data.repository.SshManager;
import com.example.playerdemo.data.repository.VideoManager;
import com.example.playerdemo.databinding.ActivityMainBinding;
import com.example.playerdemo.ui.connection.ConnectionSettingsActivity;
import com.example.playerdemo.ui.editor.MdEditorActivity;
import com.example.playerdemo.ui.player.VideoPlayerActivity;
import com.example.playerdemo.ui.recognition.RecognitionActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private ConfigManager configManager;
    private SshManager sshManager;
    private VideoManager videoManager;
    private VideoListAdapter adapter;
    private boolean showingLocalVideos = false;
    private boolean isConnecting = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    loadLocalVideos();
                } else {
                    showPermissionDeniedDialog();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        configManager = ConfigManager.getInstance(this);
        sshManager = SshManager.getInstance();
        videoManager = VideoManager.getInstance();

        setupViews();
        checkAutoReconnect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateConnectionStatus();
        if (isConnecting) {
            return;
        }
        if (sshManager.isConnected() && !showingLocalVideos) {
            loadRemoteVideos();
        } else if (!sshManager.isConnected() && !showingLocalVideos) {
            showNotConnectedTip();
        }
    }

    private void setupViews() {
        adapter = new VideoListAdapter(new VideoListAdapter.OnVideoClickListener() {
            @Override
            public void onPlayClick(VideoFile video) {
                playVideo(video);
            }

            @Override
            public void onRecognizeClick(VideoFile video) {
                startRecognition(video);
            }
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        binding.chipRemote.setOnClickListener(v -> {
            showingLocalVideos = false;
            binding.chipRemote.setChecked(true);
            binding.chipLocal.setChecked(false);
            if (sshManager.isConnected()) {
                loadRemoteVideos();
            } else {
                showNotConnectedTip();
            }
        });

        binding.chipLocal.setOnClickListener(v -> {
            showingLocalVideos = true;
            binding.chipLocal.setChecked(true);
            binding.chipRemote.setChecked(false);
            checkStoragePermissionAndLoad();
        });

        binding.fabConnect.setOnClickListener(v -> {
            if (sshManager.isConnected()) {
                showDisconnectDialog();
            } else {
                openConnectionSettings();
            }
        });

        binding.btnConnectionSettings.setOnClickListener(v -> openConnectionSettings());

        binding.swipeRefresh.setOnRefreshListener(() -> {
            if (showingLocalVideos) {
                loadLocalVideos();
            } else {
                if (sshManager.isConnected()) {
                    loadRemoteVideos();
                } else {
                    showNotConnectedTip();
                    binding.swipeRefresh.setRefreshing(false);
                }
            }
        });

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                openConnectionSettings();
                return true;
            }
            return false;
        });
    }

    private void checkAutoReconnect() {
        if (configManager.getWslConfig().isAutoReconnect() && configManager.getWslConfig().isValid()) {
            connectToWsl();
        }
    }

    private void connectToWsl() {
        isConnecting = true;
        showConnecting();

        sshManager.connect(configManager.getWslConfig(), new SshManager.ConnectionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    isConnecting = false;
                    updateConnectionStatus();
                    if (!showingLocalVideos) {
                        loadRemoteVideos();
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    isConnecting = false;
                    updateConnectionStatus();
                    showConnectionError(error);
                });
            }
        });
    }

    private void updateConnectionStatus() {
        if (sshManager.isConnected()) {
            binding.cardConnection.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.connection_connected));
            binding.tvConnectionStatus.setText("已连接");
            binding.fabConnect.setImageResource(R.drawable.ic_disconnect);
            binding.btnConnectionSettings.setVisibility(View.GONE);
        } else {
            binding.cardConnection.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.connection_disconnected));
            binding.tvConnectionStatus.setText("未连接");
            binding.fabConnect.setImageResource(R.drawable.ic_connect);
            binding.btnConnectionSettings.setVisibility(View.VISIBLE);
        }
    }

    private void loadRemoteVideos() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvEmpty.setVisibility(View.GONE);

        String videoPath = configManager.getWslConfig().getVideoPath();
        videoManager.getRemoteVideos(videoPath, new VideoManager.VideoListCallback() {
            @Override
            public void onSuccess(List<VideoFile> videos) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.swipeRefresh.setRefreshing(false);
                    
                    if (videos.isEmpty()) {
                        binding.tvEmpty.setText("未找到视频，请检查WSL路径");
                        binding.tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        binding.tvEmpty.setVisibility(View.GONE);
                    }
                    
                    adapter.setVideos(videos);
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.swipeRefresh.setRefreshing(false);
//                    binding.tvEmpty.setText(error);
                    binding.tvEmpty.setText("连接失败，请检查连接");
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void checkStoragePermissionAndLoad() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            if (ContextCompat.checkSelfPermission(this, 
                    android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, 
                    android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED) {
                loadLocalVideos();
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else {
            showStorageNotAvailable();
        }
    }

    private void loadLocalVideos() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvEmpty.setVisibility(View.GONE);

        new Thread(() -> {
            List<VideoFile> localVideos = new ArrayList<>();
            File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            
            scanDirectory(dcim, localVideos);
            scanDirectory(downloads, localVideos);

            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);
                
                if (localVideos.isEmpty()) {
                    binding.tvEmpty.setText("未找到本地视频");
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    binding.tvEmpty.setVisibility(View.GONE);
                }
                
                adapter.setVideos(localVideos);
            });
        }).start();
    }

    private void scanDirectory(File dir, List<VideoFile> videos) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && isVideoFile(file.getName())) {
                        videos.add(new VideoFile(
                                file.getName(),
                                file.getAbsolutePath(),
                                file.length(),
                                "",
                                false
                        ));
                    }
                }
            }
        }
    }

    private boolean isVideoFile(String filename) {
        String[] extensions = {"mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp"};
        String lower = filename.toLowerCase();
        for (String ext : extensions) {
            if (lower.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    private void playVideo(VideoFile video) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("video_path", video.getPath());
        intent.putExtra("video_name", video.getName());
        intent.putExtra("is_remote", video.isRemote());
        startActivity(intent);
    }

    private void startRecognition(VideoFile video) {
        Intent intent = new Intent(this, RecognitionActivity.class);
        intent.putExtra("video_path", video.getPath());
        intent.putExtra("video_name", video.getName());
        startActivity(intent);
    }

    private void showConnecting() {
        binding.cardConnection.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.connection_connecting));
        binding.tvConnectionStatus.setText("连接中...");
    }

    private void showConnectionError(String error) {
        binding.cardConnection.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.connection_error));
        binding.tvConnectionStatus.setText("连接失败");
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }

    private void showNotConnectedTip() {
        binding.tvEmpty.setText("请先连接WSL");
        binding.tvEmpty.setVisibility(View.VISIBLE);
    }

    private void showDisconnectDialog() {
        new AlertDialog.Builder(this)
                .setTitle("断开连接")
                .setMessage("确定要断开WSL连接吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    sshManager.disconnect();
                    updateConnectionStatus();
                    adapter.clearVideos();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openConnectionSettings() {
        startActivity(new Intent(this, ConnectionSettingsActivity.class));
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("权限被拒绝")
                .setMessage("需要存储权限才能访问本地视频，是否前往设置？")
                .setPositiveButton("前往", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showStorageNotAvailable() {
        binding.tvEmpty.setText("存储不可用");
        binding.tvEmpty.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
