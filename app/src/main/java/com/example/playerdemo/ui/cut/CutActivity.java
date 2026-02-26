package com.example.playerdemo.ui.cut;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.playerdemo.data.repository.AutoCutManager;
import com.example.playerdemo.data.repository.ConfigManager;
import com.example.playerdemo.data.repository.SshManager;
import com.example.playerdemo.databinding.ActivityCutBinding;
import com.example.playerdemo.ui.export.ExportActivity;
import com.example.playerdemo.ui.player.VideoPlayerActivity;

public class CutActivity extends AppCompatActivity {
    private ActivityCutBinding binding;
    private SshManager sshManager;
    private AutoCutManager autoCutManager;
    private ConfigManager configManager;
    
    private String videoPath;
    private String videoName;
    private String mdPath;
    private String outputPath;
    private boolean isCutting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sshManager = SshManager.getInstance();
        autoCutManager = AutoCutManager.getInstance();
        configManager = ConfigManager.getInstance(this);

        videoPath = getIntent().getStringExtra("video_path");
        videoName = getIntent().getStringExtra("video_name");
        mdPath = getIntent().getStringExtra("md_path");

        setupViews();
        startCut();
    }

    private void setupViews() {
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        binding.toolbar.setTitle("视频剪辑");
        
        binding.tvVideoName.setText(videoName);
        
        binding.btnCancel.setOnClickListener(v -> showCancelDialog());
        
        binding.btnRetry.setOnClickListener(v -> startCut());
    }

    private void startCut() {
        if (!sshManager.isConnected()) {
            showError("未连接到WSL");
            return;
        }

        isCutting = true;
        updateUIState(true);
        
        String baseName = videoName.substring(0, videoName.lastIndexOf('.'));
        String outputDir = configManager.getWslConfig().getVideoPath();
        outputPath = outputDir + "/" + baseName + "_edited.mp4";
        
        binding.tvLog.append("开始视频剪辑...\n");
        binding.tvLog.append("视频路径: " + videoPath + "\n");
        binding.tvLog.append("字幕文件: " + mdPath + "\n");
        binding.tvLog.append("输出路径: " + outputPath + "\n");
        
        autoCutManager.cutVideo(videoPath, mdPath, outputPath, new AutoCutManager.CutCallback() {
            @Override
            public void onProgress(int progress, String message) {
                runOnUiThread(() -> {
                    if (progress > 0) {
                        binding.progressBar.setProgress(progress);
                    }
                    if (message != null && !message.isEmpty()) {
                        binding.tvLog.append(message + "\n");
                        scrollToBottom();
                    }
                });
            }

            @Override
            public void onSuccess(String outputPath) {
                runOnUiThread(() -> {
                    isCutting = false;
                    binding.progressBar.setProgress(100);
                    binding.tvLog.append("\n剪辑完成！\n");
                    updateUIState(false);
                    
                    navigateToExport(outputPath);
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    isCutting = false;
                    binding.tvLog.append("\n剪辑失败: " + error + "\n");
                    updateUIState(false);
                    showError(error);
                });
            }
        });
    }

    private void updateUIState(boolean cutting) {
        if (cutting) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnCancel.setVisibility(View.VISIBLE);
            binding.btnRetry.setVisibility(View.GONE);
            binding.cardLog.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnCancel.setVisibility(View.GONE);
            binding.btnRetry.setVisibility(View.VISIBLE);
        }
    }

    private void scrollToBottom() {
        int scrollAmount = binding.tvLog.getLayout().getLineTop(binding.tvLog.getLineCount()) - binding.tvLog.getHeight();
        if (scrollAmount > 0) {
            binding.tvLog.scrollTo(0, scrollAmount);
        } else {
            binding.tvLog.scrollTo(0, 0);
        }
    }

    private void showCancelDialog() {
        new AlertDialog.Builder(this)
                .setTitle("取消剪辑")
                .setMessage("确定要取消视频剪辑吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    autoCutManager.cancelOperation();
                    isCutting = false;
                    finish();
                })
                .setNegativeButton("继续", null)
                .show();
    }

    private void showError(String error) {
        new AlertDialog.Builder(this)
                .setTitle("错误")
                .setMessage(error)
                .setPositiveButton("确定", null)
                .show();
    }

    private void navigateToExport(String outputPath) {
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, ExportActivity.class);
            intent.putExtra("video_path", videoPath);
            intent.putExtra("video_name", videoName);
            intent.putExtra("output_path", outputPath);
            startActivity(intent);
            finish();
        }, 1500);
    }

    @Override
    public void onBackPressed() {
        if (isCutting) {
            showCancelDialog();
        } else {
            super.onBackPressed();
        }
    }
}
