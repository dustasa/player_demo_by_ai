package com.example.playerdemo.data.repository;

import com.example.playerdemo.data.model.SubtitleEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoCutManager {
    private static AutoCutManager instance;
    private final SshManager sshManager;
    private static final Pattern PROGRESS_PATTERN = Pattern.compile("(\\d+)%");
    private static final Pattern FRAME_PATTERN = Pattern.compile("frame=(\\d+)");
    private static final Pattern TIME_PATTERN = Pattern.compile("time=(\\d{2}):(\\d{2}):(\\d{2})");

    private AutoCutManager() {
        this.sshManager = SshManager.getInstance();
    }

    public static synchronized AutoCutManager getInstance() {
        if (instance == null) {
            instance = new AutoCutManager();
        }
        return instance;
    }

    public interface RecognitionCallback {
        void onProgress(int progress, String message);
        void onSuccess(String mdFilePath);
        void onFailure(String error);
    }

    public interface CutCallback {
        void onProgress(int progress, String message);
        void onSuccess(String outputPath);
        void onFailure(String error);
    }

    public void recognizeSubtitles(String videoPath, String outputPath, RecognitionCallback callback) {
        String wslVideoPath = VideoManager.convertToWslPath(videoPath);
        
        String command = String.format(
            "wsl -d Ubuntu2204 autocut -t %s --device cuda --whisper-model large-v3-turbo",
            wslVideoPath
        );

        final int[] lastProgress = {0};
        final boolean[] success = {false};
        
        sshManager.executeCommand(command, new SshManager.CommandCallback() {
            @Override
            public void onOutput(String line) {
                if (line != null && (line.contains("Saved texts to") || line.contains("Transcribed"))) {
                    success[0] = true;
                }
                
                int progress = parseProgress(line, lastProgress[0]);
                if (progress > 0) {
                    lastProgress[0] = progress;
                } else if (line != null && line.contains("Done transcription")) {
                    lastProgress[0] = 90;
                }
                
                if (callback != null) {
                    callback.onProgress(lastProgress[0], line);
                }
            }

            @Override
            public void onComplete(int exitCode) {
                String mdPath = VideoManager.convertToWslPath(getMdPathFromVideoPath(videoPath));
                
                if (success[0] || exitCode == 0) {
                    if (callback != null) {
                        callback.onSuccess(mdPath);
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure("字幕识别失败，退出码: " + exitCode);
                    }
                }
            }

            @Override
            public void onError(String error) {
                if (callback != null) {
                    callback.onFailure("字幕识别出错: " + error);
                }
            }
        });
    }
    
    private String getMdPathFromVideoPath(String videoPath) {
        if (videoPath == null || videoPath.isEmpty()) {
            return "";
        }
        if (videoPath.startsWith("/")) {
            videoPath = videoPath.substring(1);
        }

        int lastDot = videoPath.lastIndexOf('.');
        if (lastDot > 0) {
            return videoPath.substring(0, lastDot) + ".md";
        }
        return videoPath + ".md";
    }

    public void cutVideo(String videoPath, String mdPath, String outputPath, CutCallback callback) {
        String wslVideoPath = VideoManager.convertToWslPath(videoPath);
        String wslMdPath = VideoManager.convertToWslPath(mdPath);
        String wslOutputPath = VideoManager.convertToWslPath(outputPath);
        
        String command = String.format(
            "wsl -d Ubuntu2204 autocut -c \"%s\" -s \"%s\" -o \"%s\"",
            wslVideoPath,
            wslMdPath,
            wslOutputPath
        );

        final int[] lastProgress = {0};
        
        sshManager.executeCommand(command, new SshManager.CommandCallback() {
            @Override
            public void onOutput(String line) {
                int progress = parseCutProgress(line, lastProgress[0]);
                if (progress > 0) {
                    lastProgress[0] = progress;
                }
                
                if (callback != null) {
                    callback.onProgress(progress, line);
                }
            }

            @Override
            public void onComplete(int exitCode) {
                if (exitCode == 0) {
                    if (callback != null) {
                        callback.onSuccess(outputPath);
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure("视频剪辑失败，退出码: " + exitCode);
                    }
                }
            }

            @Override
            public void onError(String error) {
                if (callback != null) {
                    callback.onFailure("视频剪辑出错: " + error);
                }
            }
        });
    }

    public void cancelOperation() {
        sshManager.executeCommand("pkill -f autocut", new SshManager.CommandCallback() {
            @Override
            public void onOutput(String line) {}

            @Override
            public void onComplete(int exitCode) {}

            @Override
            public void onError(String error) {}
        });
    }

    public List<SubtitleEntry> parseMdFile(String mdContent) {
        List<SubtitleEntry> entries = new ArrayList<>();
        if (mdContent == null || mdContent.isEmpty()) {
            return entries;
        }

        String[] blocks = mdContent.split("\n\n");
        Pattern timePattern = Pattern.compile(
            "(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})"
        );

        int index = 0;
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) continue;

            Matcher matcher = timePattern.matcher(trimmed);
            if (matcher.find()) {
                String startTime = matcher.group(1);
                String endTime = matcher.group(2);
                String content = trimmed.replaceAll(timePattern.pattern(), "").trim();

                entries.add(new SubtitleEntry(
                    String.valueOf(index + 1),
                    startTime,
                    endTime,
                    content,
                    true,
                    index
                ));
                index++;
            }
        }

        return entries;
    }

    public String generateMdFile(List<SubtitleEntry> entries) {
        StringBuilder sb = new StringBuilder();
        
        for (SubtitleEntry entry : entries) {
            if (entry.isKept()) {
                sb.append(entry.getStartTime()).append(" --> ").append(entry.getEndTime()).append("\n");
                sb.append(entry.getContent()).append("\n\n");
            }
        }
        
        return sb.toString();
    }

    private int parseProgress(String line, int lastProgress) {
        if (line == null) return lastProgress;
        
        if (line.contains("Init model")) {
            return 5;
        }
        if (line.contains("Transcribing") || line.contains("voice activity detection")) {
            return Math.min(lastProgress + 10, 50);
        }
        if (line.contains("transcription")) {
            return Math.min(lastProgress + 30, 80);
        }
        
        Matcher progressMatcher = PROGRESS_PATTERN.matcher(line);
        if (progressMatcher.find()) {
            return Integer.parseInt(progressMatcher.group(1));
        }
        
        if (line.contains("%")) {
            String[] parts = line.split("%");
            for (String part : parts) {
                if (part.contains("|")) {
                    String numStr = part.replaceAll("[^0-9]", "").trim();
                    if (!numStr.isEmpty()) {
                        try {
                            return Integer.parseInt(numStr);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        
        return lastProgress;
    }

    private int parseCutProgress(String line, int lastProgress) {
        if (line == null) return lastProgress;
        
        Matcher frameMatcher = FRAME_PATTERN.matcher(line);
        if (frameMatcher.find()) {
            return Math.min(lastProgress + 3, 95);
        }
        
        Matcher timeMatcher = TIME_PATTERN.matcher(line);
        if (timeMatcher.find()) {
            return Math.min(lastProgress + 2, 95);
        }
        
        if (line.contains("Encoding") || line.contains("编码")) {
            return Math.min(lastProgress + 5, 90);
        }
        
        return lastProgress;
    }
}
