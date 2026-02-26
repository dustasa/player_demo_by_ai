package com.example.playerdemo.data.repository;

import android.annotation.SuppressLint;

import com.example.playerdemo.data.model.VideoFile;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.example.playerdemo.data.model.WslConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoManager {
    private static VideoManager instance;
    private final SshManager sshManager;
    private static final String[] VIDEO_EXTENSIONS = {
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp"
    };

    private VideoManager() {
        this.sshManager = SshManager.getInstance();
    }

    public static synchronized VideoManager getInstance() {
        if (instance == null) {
            instance = new VideoManager();
        }
        return instance;
    }

    public interface VideoListCallback {
        void onSuccess(List<VideoFile> videos);
        void onFailure(String error);
    }

    public void getRemoteVideos(String remotePath, VideoListCallback callback) {
        new Thread(() -> {
            try {
                Vector<ChannelSftp.LsEntry> files = sshManager.listFiles(remotePath);
                List<VideoFile> videos = new ArrayList<>();

                if (files != null) {
                    for (ChannelSftp.LsEntry entry : files) {
                        String filename = entry.getFilename();
                        if (!filename.startsWith(".") && isVideoFile(filename)) {
                            long size = entry.getAttrs().getSize();
                            VideoFile video = new VideoFile(
                                filename,
                                remotePath + "/" + filename,
                                size,
                                "",
                                true
                            );
                            videos.add(video);
                        }
                    }
                }

                if (callback != null) {
                    callback.onSuccess(videos);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onFailure("获取视频列表失败: " + e.getMessage());
                }
            }
        }).start();
    }

    @SuppressLint("DefaultLocale")
    public String getVideoStreamUrl(VideoFile video) {
        SshManager sshManager = SshManager.getInstance();
        WslConfig config = sshManager.getCurrentConfig();
        if (config == null) return null;

        return String.format("smb://%s:%d%s",
            config.getWindowsHost(),
            config.getSshPort(),
            video.getPath().replace("/mnt/c/", "/")
        );
    }

    private boolean isVideoFile(String filename) {
        String lower = filename.toLowerCase();
        for (String ext : VIDEO_EXTENSIONS) {
            if (lower.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    public String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
}
