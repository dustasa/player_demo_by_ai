package com.example.playerdemo.data.model;

public class VideoFile {
    private String name;
    private String path;
    private long size;
    private String duration;
    private boolean isRemote;
    private boolean isSelected;

    public VideoFile(String name, String path, long size, String duration, boolean isRemote) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.duration = duration;
        this.isRemote = isRemote;
        this.isSelected = false;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public boolean isRemote() { return isRemote; }
    public void setRemote(boolean remote) { isRemote = remote; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }

    public String getFormattedSize() {
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        else if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        else return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }
}
