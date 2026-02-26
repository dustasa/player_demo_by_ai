package com.example.playerdemo.ui.connection;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.playerdemo.data.model.WslConfig;
import com.example.playerdemo.data.repository.ConfigManager;
import com.example.playerdemo.data.repository.SshManager;
import com.example.playerdemo.databinding.ActivityConnectionSettingsBinding;

public class ConnectionSettingsActivity extends AppCompatActivity {
    private ActivityConnectionSettingsBinding binding;
    private ConfigManager configManager;
    private SshManager sshManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityConnectionSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        configManager = ConfigManager.getInstance(this);
        sshManager = SshManager.getInstance();

        setupViews();
        loadConfig();
    }

    private void setupViews() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.etWindowsHost.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateInput();
            }
        });

        binding.etSshPort.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateInput();
            }
        });

        binding.etUsername.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateInput();
            }
        });

        binding.etPassword.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateInput();
            }
        });

        binding.etAutocutPort.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateInput();
            }
        });

        binding.etWslVideoPath.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateInput();
            }
        });

        binding.switchAutoReconnect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveConfig();
        });

        binding.btnTestConnection.setOnClickListener(v -> testConnection());
        
        binding.btnSave.setOnClickListener(v -> {
            saveConfig();
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
            finish();
        });

        binding.btnHelp.setOnClickListener(v -> showHelpDialog());
    }

    private void loadConfig() {
        WslConfig config = configManager.getWslConfig();
        if (config != null) {
            binding.etWindowsHost.setText(config.getWindowsHost());
            binding.etSshPort.setText(String.valueOf(config.getSshPort()));
            binding.etUsername.setText(config.getUsername());
            binding.etPassword.setText(config.getPassword());
            binding.etAutocutPort.setText(String.valueOf(config.getAutocutPort()));
            binding.etWslVideoPath.setText(config.getWslVideoPath());
            binding.switchAutoReconnect.setChecked(config.isAutoReconnect());
        }
    }

    private void saveConfig() {
        WslConfig config = new WslConfig();
        config.setWindowsHost(binding.etWindowsHost.getText().toString().trim());
        try {
            config.setSshPort(Integer.parseInt(binding.etSshPort.getText().toString().trim()));
        } catch (NumberFormatException e) {
            config.setSshPort(22);
        }
        config.setUsername(binding.etUsername.getText().toString().trim());
        config.setPassword(binding.etPassword.getText().toString().trim());
        try {
            config.setAutocutPort(Integer.parseInt(binding.etAutocutPort.getText().toString().trim()));
        } catch (NumberFormatException e) {
            config.setAutocutPort(8080);
        }
        config.setWslVideoPath(binding.etWslVideoPath.getText().toString().trim());
        config.setAutoReconnect(binding.switchAutoReconnect.isChecked());
        
        configManager.saveWslConfig(config);
    }

    private void validateInput() {
        boolean isValid = true;
        
        String host = binding.etWindowsHost.getText().toString().trim();
        if (host.isEmpty()) {
            binding.tilWindowsHost.setError("请输入Windows主机IP");
            isValid = false;
        } else if (!isValidIp(host)) {
            binding.tilWindowsHost.setError("IP地址格式不正确");
            isValid = false;
        } else {
            binding.tilWindowsHost.setError(null);
        }
        
        String port = binding.etSshPort.getText().toString().trim();
        if (port.isEmpty()) {
            binding.tilSshPort.setError("请输入SSH端口");
            isValid = false;
        } else {
            try {
                int portNum = Integer.parseInt(port);
                if (portNum <= 0 || portNum > 65535) {
                    binding.tilSshPort.setError("端口范围1-65535");
                    isValid = false;
                } else {
                    binding.tilSshPort.setError(null);
                }
            } catch (NumberFormatException e) {
                binding.tilSshPort.setError("请输入有效端口");
                isValid = false;
            }
        }
        
        String username = binding.etUsername.getText().toString().trim();
        if (username.isEmpty()) {
            binding.tilUsername.setError("请输入用户名");
            isValid = false;
        } else {
            binding.tilUsername.setError(null);
        }
        
        String password = binding.etPassword.getText().toString();
        if (password.isEmpty()) {
            binding.tilPassword.setError("请输入密码");
            isValid = false;
        } else {
            binding.tilPassword.setError(null);
        }
        
        binding.btnTestConnection.setEnabled(isValid);
    }

    private boolean isValidIp(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private void testConnection() {
        saveConfig();
        
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnTestConnection.setEnabled(false);
        binding.tvStatus.setText("正在测试连接...");
        binding.tvStatus.setVisibility(View.VISIBLE);

        WslConfig config = configManager.getWslConfig();
        sshManager.testConnection(config, new SshManager.ConnectionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnTestConnection.setEnabled(true);
                    binding.tvStatus.setText("连接成功！");
                    binding.tvStatus.setTextColor(getColor(android.R.color.holo_green_dark));
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnTestConnection.setEnabled(true);
                    binding.tvStatus.setText("连接失败: " + error);
                    binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                });
            }
        });
    }

    private void showHelpDialog() {
        String helpText = "配置指引：\n\n" +
            "1. 在Windows端确保SSH服务已开启\n" +
            "2. 在Windows防火墙中允许SSH端口\n" +
            "3. 确保WSL2已安装并配置好Ubuntu-22.04\n" +
            "4. 在WSL中安装AutoCut工具\n\n" +
            "常用命令：\n" +
            "在PowerShell中运行：\n" +
            "启动SSH: net start sshd\n" +
            "检查WSL: wsl -l -v";
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("配置帮助")
            .setMessage(helpText)
            .setPositiveButton("确定", null)
            .show();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
