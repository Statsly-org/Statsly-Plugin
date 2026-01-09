package me;

import me.statix.commands.StatixCommand;
import me.statix.stats.StatsCollector;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class Statix extends JavaPlugin implements Listener {
    
    private File configFile;
    private FileConfiguration config;
    private StatsCollector statsCollector;
    private String apiSecret;
    
    public String getPluginVersion() {
        return getDescription().getVersion();
    }
    
    /**
     * Retrieves the unique Build ID from the plugin's build.properties file.
     * This ID is generated at build time and uniquely identifies each plugin build.
     * Used for security validation to ensure the plugin is an official build.
     * 
     * @return The 32-character hex Build ID, or a dev identifier if build.properties is not available
     */
    public String getBuildId() {
        try {
            java.io.InputStream is = getResource("build.properties");
            if (is != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                String buildId = props.getProperty("build.id");
                if (buildId != null && !buildId.isEmpty()) {
                    return buildId;
                }
            }
        } catch (Exception e) {
            getLogger().warning("Failed to read build.properties: " + e.getMessage());
        }
        return "dev-" + System.currentTimeMillis();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configFile = new File(getDataFolder(), "config.yml");
        config = YamlConfiguration.loadConfiguration(configFile);
        
        StatixCommand cmd = new StatixCommand(this);
        if (getCommand("statsly") != null) {
            getCommand("statsly").setExecutor(cmd);
            getCommand("statsly").setTabCompleter(cmd);
        }
        
        getServer().getPluginManager().registerEvents(this, this);
        
        statsCollector = new StatsCollector(this);
        String serverUuid = getServerUuid();
        if (serverUuid != null && !serverUuid.isEmpty()) {
            if (apiSecret == null || apiSecret.isEmpty()) {
                retrieveApiSecretOnStartup(serverUuid);
            } else {
                statsCollector.start();
            }
        }
    }
    
    @Override
    public void onDisable() {
        if (statsCollector != null) {
            statsCollector.stop();
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (statsCollector != null) {
            statsCollector.onPlayerJoin();
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (statsCollector != null) {
            statsCollector.onPlayerQuit();
        }
    }
    
    public void saveServerUuid(String serverUuid) {
        config.set("serverUuid", serverUuid);
        try {
            config.save(configFile);
            if (statsCollector != null && !statsCollector.isRunning()) {
                statsCollector.start();
            }
        } catch (IOException e) {
            getLogger().severe("Failed to save server UUID to config.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public String getServerUuid() {
        return config.getString("serverUuid", "");
    }
    
    public String getServerIp() {
        String ip = getServer().getIp();
        return (ip == null || ip.isEmpty()) ? "localhost" : ip;
    }
    
    public int getServerPort() {
        return getServer().getPort();
    }
    
    /**
     * Returns the API secret.
     * WARNING: 
     * - NEVER save this to disk (config.yml, files, etc.) - will result in PERMANENT BAN
     * - Secret must only exist in memory
     * - Never log, print, or expose this value
     */
    public String getApiSecret() {
        return apiSecret;
    }
    
    /**
     * Sets the API secret in memory.
     * WARNING: 
     * - NEVER save the secret to config.yml or any file - will result in PERMANENT BAN
     * - Secret must only exist in memory
     * - Never log or expose the secret parameter
     */
    public void setApiSecret(String secret) {
        this.apiSecret = secret;
        if (statsCollector != null && !statsCollector.isRunning()) {
            String serverUuid = getServerUuid();
            if (serverUuid != null && !serverUuid.isEmpty()) {
                statsCollector.start();
            }
        }
    }
    
    /**
     * Retrieves the API secret from the backend on plugin startup.
     * 
     * WARNING: 
     * - NEVER save the API secret to disk (config.yml, files, etc.) - will result in PERMANENT BAN
     * - The secret MUST only be stored in memory
     * - Never log the API secret in any form
     * - Do NOT modify request intervals - will result in PERMANENT BAN
     */
    private void retrieveApiSecretOnStartup(String serverUuid) {
        new Thread(() -> {
            try {
                java.net.URI uri = new java.net.URI("https://api.statsly.org/api/servers/get-secret");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                
                String jsonPayload = String.format("{\"serverUuid\":\"%s\"}", serverUuid);
                
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200) {
                    try (java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line.trim());
                        }
                        
                        String responseStr = response.toString();
                        if (responseStr.contains("\"apiSecret\"")) {
                            int secretStart = responseStr.indexOf("\"apiSecret\":\"") + 14;
                            int secretEnd = responseStr.indexOf("\"", secretStart);
                            if (secretEnd > secretStart) {
                                String secret = responseStr.substring(secretStart, secretEnd);
                                setApiSecret(secret);
                                return;
                            }
                        }
                    }
                }
                
                getLogger().warning("Failed to retrieve API secret on startup: HTTP " + responseCode);
            } catch (Exception e) {
                getLogger().severe("Failed to retrieve API secret on startup: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
}
