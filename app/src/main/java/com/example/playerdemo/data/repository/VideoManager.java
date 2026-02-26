package com.example.playerdemo.data.repository;

import com.example.playerdemo.data.model.VideoFile;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

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
                            String fullPath = remotePath.endsWith("/") 
                                ? remotePath + filename 
                                : remotePath + "/" + filename;
                            VideoFile video = new VideoFile(
                                filename,
                                fullPath,
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

    public static String convertToWslPath(String windowsPath) {
        if (windowsPath == null) return "";

        // 1. 先把输入的 string 第一个 "/" 去掉（如果存在）
        String path = windowsPath;
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // 2. 将反斜杠换成正斜杠
        path = path.replace("\\", "/");

        // 3. 处理盘符 (例如 F:/ 变 /mnt/f/)
        if (path.length() >= 2 && path.charAt(1) == ':') {
            String drive = String.valueOf(path.charAt(0)).toLowerCase();
            path = "/mnt/" + drive + path.substring(2);
        }

        return path;
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
}
