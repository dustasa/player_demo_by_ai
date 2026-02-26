package com.example.playerdemo.data.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubtitleEntry {
    private String id;
    private String startTime;
    private String endTime;
    private String content;
    private boolean kept;
    private int index;

    private static final Pattern TIME_PATTERN = Pattern.compile(
        "(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})"
    );

    public SubtitleEntry(String id, String startTime, String endTime, String content, boolean kept, int index) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.content = content;
        this.kept = kept;
        this.index = index;
    }

    public static SubtitleEntry parseFromMd(String mdContent, int index) {
        String[] lines = mdContent.split("\n");
        String id = String.valueOf(index + 1);
        String startTime = "";
        String endTime = "";
        String content = "";

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher matcher = TIME_PATTERN.matcher(line);
            if (matcher.find()) {
                startTime = matcher.group(1);
                endTime = matcher.group(2);
            } else if (!line.contains("-->")) {
                content = line;
            }
        }

        return new SubtitleEntry(id, startTime, endTime, content, true, index);
    }

    public String toMdFormat() {
        return startTime + " --> " + endTime + "\n" + content + "\n";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isKept() { return kept; }
    public void setKept(boolean kept) { this.kept = kept; }
    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
}
