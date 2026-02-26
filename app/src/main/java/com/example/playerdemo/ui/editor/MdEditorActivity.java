package com.example.playerdemo.ui.editor;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.playerdemo.data.model.SubtitleEntry;
import com.example.playerdemo.data.repository.AutoCutManager;
import com.example.playerdemo.data.repository.ConfigManager;
import com.example.playerdemo.data.repository.SshManager;
import com.example.playerdemo.databinding.ActivityMdEditorBinding;
import com.example.playerdemo.ui.cut.CutActivity;

import java.util.ArrayList;
import java.util.List;

public class MdEditorActivity extends AppCompatActivity {
    private ActivityMdEditorBinding binding;
    private SshManager sshManager;
    private AutoCutManager autoCutManager;
    private ConfigManager configManager;
    private SubtitleAdapter adapter;
    private List<SubtitleEntry> subtitleEntries = new ArrayList<>();
    
    private String videoPath;
    private String videoName;
    private String mdPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMdEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sshManager = SshManager.getInstance();
        autoCutManager = AutoCutManager.getInstance();
        configManager = ConfigManager.getInstance(this);

        videoPath = getIntent().getStringExtra("video_path");
        videoName = getIntent().getStringExtra("video_name");
        mdPath = getIntent().getStringExtra("md_path");

        setupViews();
        loadMdFile();
    }

    private void setupViews() {
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        binding.toolbar.setTitle("编辑字幕");
        
        adapter = new SubtitleAdapter(new SubtitleAdapter.OnSubtitleActionListener() {
            @Override
            public void onToggleKeep(SubtitleEntry entry, boolean kept) {
                entry.setKept(kept);
                adapter.notifyDataSetChanged();
                updateStats();
            }

            @Override
            public void onEdit(SubtitleEntry entry) {
                showEditDialog(entry);
            }

            @Override
            public void onPreview(SubtitleEntry entry) {
                previewSubtitle(entry);
            }
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        binding.btnSelectAll.setOnClickListener(v -> selectAll(true));
        binding.btnDeselectAll.setOnClickListener(v -> selectAll(false));
        binding.btnReverse.setOnClickListener(v -> reverseSelection());
        
        binding.btnSave.setOnClickListener(v -> saveAndContinue());
    }

    private void loadMdFile() {
        binding.progressBar.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            String mdContent = sshManager.readRemoteFile(mdPath);
            
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                
                if (mdContent != null && !mdContent.isEmpty()) {
                    subtitleEntries = autoCutManager.parseMdFile(mdContent);
                    adapter.setSubtitles(subtitleEntries);
                    updateStats();
                } else {
                    Toast.makeText(this, "无法读取md文件", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        }).start();
    }

    private void updateStats() {
        int total = subtitleEntries.size();
        int kept = 0;
        for (SubtitleEntry entry : subtitleEntries) {
            if (entry.isKept()) kept++;
        }
        binding.tvStats.setText(String.format("共 %d 条，保留 %d 条", total, kept));
    }

    private void selectAll(boolean select) {
        for (SubtitleEntry entry : subtitleEntries) {
            entry.setKept(select);
        }
        adapter.notifyDataSetChanged();
        updateStats();
    }

    private void reverseSelection() {
        for (SubtitleEntry entry : subtitleEntries) {
            entry.setKept(!entry.isKept());
        }
        adapter.notifyDataSetChanged();
        updateStats();
    }

    private void showEditDialog(SubtitleEntry entry) {
        View dialogView = getLayoutInflater().inflate(
                com.example.playerdemo.R.layout.dialog_edit_subtitle, null);
        
        com.google.android.material.textfield.TextInputEditText etContent = 
                dialogView.findViewById(com.example.playerdemo.R.id.et_subtitle_content);
        etContent.setText(entry.getContent());

        new AlertDialog.Builder(this)
                .setTitle("编辑字幕")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newContent = etContent.getText().toString().trim();
                    if (newContent.isEmpty()) {
                        Toast.makeText(this, "字幕内容不能为空", Toast.LENGTH_SHORT).show();
                    } else {
                        entry.setContent(newContent);
                        adapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void previewSubtitle(SubtitleEntry entry) {
        Intent intent = new Intent(this, com.example.playerdemo.ui.player.VideoPlayerActivity.class);
        intent.putExtra("video_path", videoPath);
        intent.putExtra("video_name", videoName);
        intent.putExtra("start_time", entry.getStartTime());
        intent.putExtra("is_remote", true);
        startActivity(intent);
    }

    private void saveAndContinue() {
        boolean hasKeptEntries = false;
        for (SubtitleEntry entry : subtitleEntries) {
            if (entry.isKept()) {
                hasKeptEntries = true;
                break;
            }
        }

        if (!hasKeptEntries) {
            new AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("所有字幕都被删除，视频将为空。确定要继续吗？")
                    .setPositiveButton("确定", (dialog, which) -> performSave())
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            performSave();
        }
    }

    private void performSave() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSave.setEnabled(false);
        
        String mdContent = autoCutManager.generateMdFile(subtitleEntries);
        
        sshManager.uploadContent(mdContent, mdPath, new SshManager.ProgressCallback() {
            @Override
            public void onProgress(int progress, String message) {
                runOnUiThread(() -> {
                    binding.progressBar.setProgress(progress);
                });
            }
        });

        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
            
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnSave.setEnabled(true);
                
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                navigateToCut();
            });
        }).start();
    }

    private void navigateToCut() {
        Intent intent = new Intent(this, CutActivity.class);
        intent.putExtra("video_path", videoPath);
        intent.putExtra("video_name", videoName);
        intent.putExtra("md_path", mdPath);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("返回")
                .setMessage("编辑尚未保存，确定要返回吗？")
                .setPositiveButton("返回", (dialog, which) -> finish())
                .setNegativeButton("取消", null)
                .show();
    }
}
