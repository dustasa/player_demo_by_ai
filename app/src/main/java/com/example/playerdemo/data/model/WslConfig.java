package com.example.playerdemo.data.model;

public class WslConfig {
    private String windowsHost;
    private int sshPort;
    private String username;
    private String password;
    private String videoPath;
    private boolean autoReconnect;

    public WslConfig() {
        this.windowsHost = "";
        this.sshPort = 22;
        this.username = "";
        this.password = "";
        this.videoPath = "";
        this.autoReconnect = false;
    }

    public String getWindowsHost() { return windowsHost; }
    public void setWindowsHost(String windowsHost) { this.windowsHost = windowsHost; }
    public int getSshPort() { return sshPort; }
    public void setSshPort(int sshPort) { this.sshPort = sshPort; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getVideoPath() { return videoPath; }
    public void setVideoPath(String videoPath) { this.videoPath = videoPath; }
    public boolean isAutoReconnect() { return autoReconnect; }
    public void setAutoReconnect(boolean autoReconnect) { this.autoReconnect = autoReconnect; }

    public boolean isValid() {
        return windowsHost != null && !windowsHost.isEmpty() &&
               sshPort > 0 && sshPort <= 65535 &&
               username != null && !username.isEmpty() &&
               password != null && !password.isEmpty();
    }
}
