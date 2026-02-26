package com.example.playerdemo.data.repository;

import com.example.playerdemo.data.model.WslConfig;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SshManager {
    private static SshManager instance;
    private Session session;
    private ChannelSftp sftpChannel;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private WslConfig currentConfig;

    private SshManager() {}

    public static synchronized SshManager getInstance() {
        if (instance == null) {
            instance = new SshManager();
        }
        return instance;
    }

    public interface ConnectionCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public interface CommandCallback {
        void onOutput(String line);
        void onComplete(int exitCode);
        void onError(String error);
    }

    public interface ProgressCallback {
        void onProgress(int progress, String message);
    }

    public void connect(WslConfig config, ConnectionCallback callback) {
        executor.execute(() -> {
            try {
                disconnect();

                JSch jsch = new JSch();
                session = jsch.getSession(config.getUsername(), config.getWindowsHost(), config.getSshPort());
                session.setPassword(config.getPassword());
                
                Properties properties = new Properties();
                properties.put("StrictHostKeyChecking", "no");
                properties.put("KeepAliveInterval", "30");
                properties.put("ConnectionTimeout", "5000");
                session.setConfig(properties);
                
                session.connect(5000);

                ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect(5000);
                sftpChannel = channel;

                currentConfig = config;
                isConnected.set(true);
                
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (JSchException e) {
                isConnected.set(false);
                if (callback != null) {
                    callback.onFailure(parseConnectionError(e.getMessage()));
                }
            }
        });
    }

    public void testConnection(WslConfig config, ConnectionCallback callback) {
        executor.execute(() -> {
            Session testSession = null;
            try {
                JSch jsch = new JSch();
                testSession = jsch.getSession(config.getUsername(), config.getWindowsHost(), config.getSshPort());
                testSession.setPassword(config.getPassword());
                
                Properties properties = new Properties();
                properties.put("StrictHostKeyChecking", "no");
                properties.put("ConnectionTimeout", "5000");
                testSession.setConfig(properties);
                
                testSession.connect(5000);

                if (testSession.isConnected()) {
                    testSession.disconnect();
                    callback.onSuccess();
                } else {
                    callback.onFailure("连接失败");
                }
            } catch (JSchException e) {
                if (testSession != null) {
                    testSession.disconnect();
                }
                callback.onFailure(parseConnectionError(e.getMessage()));
            }
        });
    }

    public void executeCommand(String command, CommandCallback callback) {
        if (!isConnected.get() || session == null || !session.isConnected()) {
            if (callback != null) {
                callback.onError("未连接到服务器");
            }
            return;
        }

        executor.execute(() -> {
            Channel channel = null;
            try {
                channel = session.openChannel("exec");
                ((ChannelExec) channel).setCommand(command);
                
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
                
                channel.setInputStream(null);
                ((ChannelExec) channel).setErrStream(errorStream);
                
                channel.connect(30000);

                InputStream inputStream = channel.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (callback != null) {
                        callback.onOutput(line);
                    }
                }

                channel.disconnect();

                if (callback != null) {
                    callback.onComplete(((ChannelExec) channel).getExitStatus());
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
                if (channel != null) {
                    channel.disconnect();
                }
            }
        });
    }

    public void executeCommandWithWsl(String command, CommandCallback callback) {
        String wslCommand = "wsl -d Ubuntu-22.04 " + command;
        executeCommand(wslCommand, callback);
    }

    public void downloadFile(String remotePath, String localPath, ProgressCallback callback) {
        if (!isConnected.get() || sftpChannel == null) {
            if (callback != null) {callback.onProgress(0, "未连接到服务器");
            }
            return;
        }

        executor.execute(() -> {
            try {
                // 使用匿名内部类代替 Lambda
                sftpChannel.get(remotePath, localPath, new com.jcraft.jsch.SftpProgressMonitor() {
                    private long max = 0;
                    private long count = 0;

                    @Override
                    public void init(int op, String src, String dest, long max) {
                        this.max = max;
                    }

                    @Override
                    public boolean count(long count) {
                        this.count += count;
                        if (callback != null && max > 0) {
                            int percent = (int) ((this.count * 100) / max);
                            callback.onProgress(percent, "下载中: " + percent + "%");
                        }
                        return true; // 返回 true 继续传输，返回 false 终止传输
                    }

                    @Override
                    public void end() {
                        if (callback != null) {
                            callback.onProgress(100, "下载完成");
                        }
                    }
                });
            } catch (SftpException e) {
                if (callback != null) {
                    callback.onProgress(0, "下载失败: " + e.getMessage());
                }
            }
        });
    }

    public void uploadFile(String localPath, String remotePath, ProgressCallback callback) {
        if (!isConnected.get() || sftpChannel == null) {
            if (callback != null) {
                callback.onProgress(0, "未连接到服务器");
            }
            return;
        }

        executor.execute(() -> {
            try {
                sftpChannel.put(localPath, remotePath, new com.jcraft.jsch.SftpProgressMonitor() {
                    private long max = 0;
                    private long count = 0;

                    @Override
                    public void init(int op, String src, String dest, long max) {
                        this.max = max;
                    }

                    @Override
                    public boolean count(long count) {
                        this.count += count;
                        if (callback != null && max > 0) {
                            int percent = (int) ((this.count * 100) / max);
                            callback.onProgress(percent, "上传中: " + percent + "%");
                        }
                        return true;
                    }

                    @Override
                    public void end() {}
                });
            } catch (SftpException e) {
                if (callback != null) {
                    callback.onProgress(0, "上传失败: " + e.getMessage());
                }
            }
        });
    }

    public void uploadContent(String content, String remotePath, ProgressCallback callback) {
        if (!isConnected.get() || sftpChannel == null) {
            if (callback != null) {
                callback.onProgress(0, "未连接到服务器");
            }
            return;
        }

        executor.execute(() -> {
            try {
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

                // 将 Lambda 改为匿名内部类实现 SftpProgressMonitor
                sftpChannel.put(inputStream, remotePath, new com.jcraft.jsch.SftpProgressMonitor() {
                    private long max = 0;
                    private long count = 0;

                    @Override
                    public void init(int op, String src, String dest, long max) {
                        // 对于 InputStream 上传，max 可能是 -1（如果 JSch 无法预知流大小）
                        // 但由于我们是 byte[]，我们可以手动设置 max
                        this.max = bytes.length;
                    }

                    @Override
                    public boolean count(long count) {
                        this.count += count;
                        if (callback != null && max > 0) {
                            int percent = (int) ((this.count * 100) / max);
                            callback.onProgress(percent, "上传中: " + percent + "%");
                        }
                        return true;
                    }

                    @Override
                    public void end() {
                        if (callback != null) {
                            callback.onProgress(100, "上传完成");
                        }
                    }
                });
            } catch (SftpException e) {
                if (callback != null) {
                    callback.onProgress(0, "上传失败: " + e.getMessage());
                }
            }
        });
    }

    public String readRemoteFile(String remotePath) {
        if (!isConnected.get() || sftpChannel == null) {
            return null;
        }

        // 使用 try-with-resources 可以自动关闭流，更安全
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            sftpChannel.get(remotePath, outputStream);

            // 这里的 toString() 会抛出 UnsupportedEncodingException
            return outputStream.toString("UTF-8");

        } catch (SftpException | java.io.UnsupportedEncodingException e) {
            // 同时捕获 SFTP 异常和编码异常
            // 可以选择记录日志 e.printStackTrace();
            return null;
        } catch (Exception e) {
            // 捕获其他所有可能的异常，增加代码健壮性
            return null;
        }
    }


    public Vector<ChannelSftp.LsEntry> listFiles(String remotePath) {
        if (!isConnected.get() || sftpChannel == null) {
            return null;
        }

        try {
            return sftpChannel.ls(remotePath);
        } catch (SftpException e) {
            return null;
        }
    }

    public void disconnect() {
        try {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        } catch (Exception e) {
            // Ignore
        }
        isConnected.set(false);
    }

    public boolean isConnected() {
        return isConnected.get() && session != null && session.isConnected();
    }

    public WslConfig getCurrentConfig() {
        return currentConfig;
    }

    private String parseConnectionError(String message) {
        if (message == null) return "连接失败";
        if (message.contains("Connection refused")) return "SSH端口未开放，请检查SSH服务是否启动";
        if (message.contains("Connection timeout")) return "连接超时，请检查IP地址是否正确";
        if (message.contains("Auth fail")) return "用户名或密码错误";
        if (message.contains("Unknown host")) return "主机名无法解析，请检查IP地址";
        return message;
    }
}
