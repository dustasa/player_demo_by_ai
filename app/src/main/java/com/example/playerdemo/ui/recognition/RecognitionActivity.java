package com.example.playerdemo.ui.recognition;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.playerdemo.data.model.WslConfig;
import com.example.playerdemo.data.repository.AutoCutManager;
import com.example.playerdemo.data.repository.ConfigManager;
import com.example.playerdemo.data.repository.SshManager;
import com.example.playerdemo.databinding.ActivityRecognitionBinding;
import com.example.playerdemo.ui.editor.MdEditorActivity;

public class RecognitionActivity extends AppCompatActivity {
    private ActivityRecognitionBinding binding;
    private SshManager sshManager;
    private AutoCutManager autoCutManager;
    private ConfigManager configManager;
    
    private String videoPath;
    private String videoName;
    private String mdOutputPath;
    private boolean isRecognizing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecognitionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sshManager = SshManager.getInstance();
        autoCutManager = AutoCutManager.getInstance();
        configManager = ConfigManager.getInstance(this);

        videoPath = getIntent().getStringExtra("video_path");
        videoName = getIntent().getStringExtra("video_name");

        setupViews();
        startRecognition();
    }

    private void setupViews() {
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        
        binding.toolbar.setTitle("识别字幕");
        
        binding.tvVideoName.setText(videoName);
        
        binding.btnCancel.setOnClickListener(v -> showCancelDialog());
        
        binding.btnRetry.setOnClickListener(v -> startRecognition());
    }

    private void startRecognition() {
        if (!sshManager.isConnected()) {
            showError("未连接到WSL，请先建立连接");
            return;
        }

        isRecognizing = true;
        updateUIState(true);
        
        WslConfig config = configManager.getWslConfig();
        String videoDir = config.getWslVideoPath();
        String baseName = videoName.substring(0, videoName.lastIndexOf('.'));
        mdOutputPath = videoDir + "/" + baseName + ".md";
        
        binding.tvLog.append("开始识别字幕...\n");
        binding.tvLog.append("视频路径: " + videoPath + "\n");
        binding.tvLog.append("输出路径: " + mdOutputPath + "\n");
        
        autoCutManager.recognizeSubtitles(videoPath, mdOutputPath, new AutoCutManager.RecognitionCallback() {
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
            public void onSuccess(String mdFilePath) {
                runOnUiThread(() -> {
                    isRecognizing = false;
                    binding.progressBar.setProgress(100);
                    binding.tvLog.append("\n识别完成！\n");
                    updateUIState(false);
                    
                    navigateToEditor(mdFilePath);
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    isRecognizing = false;
                    binding.tvLog.append("\n识别失败: " + error + "\n");
                    updateUIState(false);
                    showError(error);
                });
            }
        });
    }

    private void updateUIState(boolean recognizing) {
        if (recognizing) {
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
                .setTitle("取消识别")
                .setMessage("确定要取消字幕识别吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    autoCutManager.cancelOperation();
                    isRecognizing = false;
                    finish();
                })
                .setNegativeButton("继续识别", null)
                .show();
    }

    private void showError(String error) {
        new AlertDialog.Builder(this)
                .setTitle("错误")
                .setMessage(error)
                .setPositiveButton("确定", null)
                .show();
    }

    private void navigateToEditor(String mdFilePath) {
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, MdEditorActivity.class);
            intent.putExtra("video_path", videoPath);
            intent.putExtra("video_name", videoName);
            intent.putExtra("md_path", mdFilePath);
            startActivity(intent);
            finish();
        }, 1500);
    }

    @Override
    public void onBackPressed() {
        if (isRecognizing) {
            showCancelDialog();
        } else {
            super.onBackPressed();
        }
    }
}
