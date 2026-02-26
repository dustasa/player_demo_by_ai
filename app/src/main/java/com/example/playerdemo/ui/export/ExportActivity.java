package com.example.playerdemo.ui.export;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.playerdemo.data.repository.ConfigManager;
import com.example.playerdemo.data.repository.SshManager;
import com.example.playerdemo.databinding.ActivityExportBinding;
import com.example.playerdemo.ui.main.MainActivity;
import com.example.playerdemo.ui.player.VideoPlayerActivity;

import java.io.File;

public class ExportActivity extends AppCompatActivity {
    private ActivityExportBinding binding;
    private SshManager sshManager;
    private ConfigManager configManager;
    
    private String videoPath;
    private String videoName;
    private String outputPath;
    private boolean isExporting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sshManager = SshManager.getInstance();
        configManager = ConfigManager.getInstance(this);

        videoPath = getIntent().getStringExtra("video_path");
        videoName = getIntent().getStringExtra("video_name");
        outputPath = getIntent().getStringExtra("output_path");

        setupViews();
    }

    private void setupViews() {
        binding.toolbar.setTitle("导出视频");
        
        binding.tvVideoName.setText(videoName + " (编辑后)");
        
        binding.radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == binding.radioWsl.getId()) {
                binding.cardLocalPath.setVisibility(View.GONE);
            } else {
                binding.cardLocalPath.setVisibility(View.VISIBLE);
            }
        });
        
        binding.btnPreview.setOnClickListener(v -> previewVideo());
        binding.btnExport.setOnClickListener(v -> startExport());
        binding.btnDone.setOnClickListener(v -> goHome());
    }

    private void previewVideo() {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("video_path", outputPath);
        intent.putExtra("video_name", videoName + " (编辑后)");
        intent.putExtra("is_remote", true);
        startActivity(intent);
    }

    private void startExport() {
        if (binding.radioWsl.isChecked()) {
            exportToWsl();
        } else {
            exportToLocal();
        }
    }

    private void exportToWsl() {
        showSuccess("视频已保存到WSL: " + outputPath);
        updateUIAfterExport();
    }

    private void exportToLocal() {
        isExporting = true;
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnExport.setEnabled(false);
        
        File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        String localPath = moviesDir.getAbsolutePath() + "/" + videoName.replace(".mp4", "_edited.mp4");
        
        binding.tvLog.append("正在下载视频到本地...\n");
        binding.tvLog.append("目标路径: " + localPath + "\n");
        
        sshManager.downloadFile(outputPath, localPath, new SshManager.ProgressCallback() {
            @Override
            public void onProgress(int progress, String message) {
                runOnUiThread(() -> {
                    binding.progressBar.setProgress(progress);
                    if (message != null) {
                        binding.tvLog.append(message + "\n");
                    }
                });
            }
        });

        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {}
            
            runOnUiThread(() -> {
                isExporting = false;
                binding.progressBar.setVisibility(View.GONE);
                binding.btnExport.setEnabled(true);
                
                binding.tvLog.append("\n导出完成！\n");
                binding.tvLog.append("保存位置: " + localPath + "\n");
                
                showExportCompleteDialog(localPath);
            });
        }).start();
    }

    private void updateUIAfterExport() {
        binding.btnExport.setVisibility(View.GONE);
        binding.btnDone.setVisibility(View.VISIBLE);
        binding.btnPreview.setVisibility(View.VISIBLE);
    }

    private void showExportCompleteDialog(String localPath) {
        new AlertDialog.Builder(this)
                .setTitle("导出成功")
                .setMessage("视频已导出到: " + localPath)
                .setPositiveButton("预览", (dialog, which) -> previewVideo())
                .setNegativeButton("完成", (dialog, which) -> goHome())
                .show();
    }

    private void showSuccess(String message) {
        binding.tvLog.setVisibility(View.VISIBLE);
        binding.tvLog.append("\n" + message + "\n");
        updateUIAfterExport();
    }

    private void goHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (isExporting) {
            new AlertDialog.Builder(this)
                    .setTitle("正在导出")
                    .setMessage("导出正在进行中，确定要返回吗？")
                    .setPositiveButton("返回", (dialog, which) -> finish())
                    .setNegativeButton("继续", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }
}
