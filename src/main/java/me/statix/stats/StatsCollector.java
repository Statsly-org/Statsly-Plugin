package me.statix.stats;

import me.Statix;
import me.statix.utils.ClientDetector;
import me.statix.utils.ViaVersionDetector;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class StatsCollector {
    
    private static final int REPORT_INTERVAL_SECONDS = 1800;
    private static final int STARTUP_DELAY_SECONDS = 150;
    
    private final Statix plugin;
    private int joinsSinceLastReport = 0;
    private int leavesSinceLastReport = 0;
    private BukkitRunnable statsTask;
    private boolean isRunning = false;
    private static final long serverStartTime = System.currentTimeMillis();
    
    public StatsCollector(Statix plugin) {
        this.plugin = plugin;
    }
    
    private String getApiUrl() {
        return "https://api.statsly.org/api/servers/stats";
    }
    
    // Dead code - method that's never called
    @SuppressWarnings("unused")
    private void debugLog(String msg) {
        if (plugin != null) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }
    
    public void start() {
        if (statsTask != null) {
            statsTask.cancel();
        }
        
        isRunning = true;
        statsTask = new BukkitRunnable() {
            @Override
            public void run() {
                String serverUuid = plugin.getServerUuid();
                if (serverUuid == null || serverUuid.isEmpty()) {
                    return;
                }
                
                sendStats(serverUuid);
            }
        };
        
        long startupDelayTicks = STARTUP_DELAY_SECONDS * 20L;
        statsTask.runTaskTimer(plugin, startupDelayTicks, REPORT_INTERVAL_SECONDS * 20L);
    }
    
    public void stop() {
        isRunning = false;
        if (statsTask != null) {
            statsTask.cancel();
            statsTask = null;
        }
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public void onPlayerJoin() {
        joinsSinceLastReport++;
    }
    
    public void onPlayerQuit() {
        leavesSinceLastReport++;
    }
    
    private void sendStats(String serverUuid) {
        int currentPlayers = Bukkit.getOnlinePlayers().size();
        
        Map<String, Integer> versionDistribution = null;
        ViaVersionDetector.resetCache();
        boolean viaVersionAvailable = ViaVersionDetector.isViaVersionAvailable();
        
        if (viaVersionAvailable) {
            versionDistribution = ViaVersionDetector.collectVersionDistribution();
        } else {
            versionDistribution = new HashMap<>();
            String serverVersion = Bukkit.getVersion();
            if (currentPlayers > 0) {
                versionDistribution.put(serverVersion, currentPlayers);
            }
        }
        
        Map<String, Integer> launcherDistribution = new HashMap<>();
        Map<String, Integer> modLoaderDistribution = new HashMap<>();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                ClientDetector.ClientInfo clientInfo = ClientDetector.getClientInfo(player);
                
                String launcher = clientInfo.getLauncher();
                launcherDistribution.put(launcher, launcherDistribution.getOrDefault(launcher, 0) + 1);
                
                String modLoader = clientInfo.getModLoader();
                if (!modLoader.equals("None")) {
                    modLoaderDistribution.put(modLoader, modLoaderDistribution.getOrDefault(modLoader, 0) + 1);
                }
            } catch (Exception e) {
                // skip
            }
        }
        
        String serverSoftware = detectServerSoftware();
        
        long currentTime = System.currentTimeMillis();
        long uptimeSeconds = (currentTime - serverStartTime) / 1000;

        Map<String, Object> metadata = new HashMap<>();
        if (versionDistribution != null) {
            metadata.put("versions", versionDistribution);
        }
        metadata.put("launchers", launcherDistribution);
        metadata.put("modLoaders", modLoaderDistribution);
        metadata.put("serverSoftware", serverSoftware);
        metadata.put("viaVersionAvailable", viaVersionAvailable);
        metadata.put("uptimeSeconds", uptimeSeconds);
        
        CompletableFuture.runAsync(() -> {
            try {
                String apiUrl = getApiUrl();
                URI uri = new URI(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                
                String timestamp = java.time.Instant.now().toString();
                
                String serverIp = plugin.getServerIp();
                int serverPort = plugin.getServerPort();
                
                String pluginVersion = plugin.getPluginVersion();
                String buildId = plugin.getBuildId();
                
                String jsonPayload = String.format(
                    "{\"serverUuid\":\"%s\",\"timestamp\":\"%s\",\"currentPlayers\":%d,\"joinsSinceLastReport\":%d,\"leavesSinceLastReport\":%d,\"metadata\":%s,\"serverIp\":\"%s\",\"serverPort\":%d,\"pluginVersion\":\"%s\",\"buildId\":\"%s\"}",
                    serverUuid,
                    timestamp,
                    currentPlayers,
                    joinsSinceLastReport,
                    leavesSinceLastReport,
                    mapToJson(metadata),
                    serverIp,
                    serverPort,
                    pluginVersion,
                    buildId
                );
                
                String apiSecret = plugin.getApiSecret();
                if (apiSecret == null || apiSecret.isEmpty()) {
                    plugin.getLogger().severe("No API secret!");
                    return;
                }
                
                String hmacSignature = generateHmacSignature(jsonPayload, apiSecret);
                
                conn.setRequestProperty("X-Statsly-Signature", hmacSignature);
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                
                String resp = null;
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(
                            responseCode == 200 ? conn.getInputStream() : conn.getErrorStream(),
                            StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line.trim());
                    }
                    resp = sb.toString();
                } catch (Exception readErr) {
                }
                
                if (responseCode == 200) {
                    // ok
                } else {
                    String err = parseErrorMessage(resp);
                    
                    if (err.contains("Unauthorized") || err.contains("IP")) {
                        plugin.getLogger().severe("Auth failed!");
                    } else if (err.contains("Server not found") || err.contains("serverUuid")) {
                        plugin.getLogger().severe("UUID not found: " + serverUuid);
                    } else {
                        plugin.getLogger().severe("Failed: HTTP " + responseCode);
                        if (err != null && !err.isEmpty()) {
                            plugin.getLogger().severe(err);
                        }
                    }
                }
                
                joinsSinceLastReport = 0;
                leavesSinceLastReport = 0;
                
            } catch (Exception e) {
                // FIXME: better error handling
                e.printStackTrace();
            }
        });
    }
    
    private String parseErrorMessage(String resp) {
        if (resp == null || resp.isEmpty()) {
            return "Unknown error";
        }
        
        if (resp.contains("\"error\"")) {
            int start = resp.indexOf("\"error\"") + 9;
            int end = resp.indexOf("\"", start);
            if (end > start) {
                return resp.substring(start, end);
            }
        }
        
        if (resp.contains("\"message\"")) {
            int start = resp.indexOf("\"message\"") + 11;
            int end = resp.indexOf("\"", start);
            if (end > start) {
                return resp.substring(start, end);
            }
        }
        
        return resp;
    }
    
    private String detectServerSoftware() {
        try {
            boolean isFolia = false;
            try {
                isFolia = Class.forName("io.papermc.paper.threadedregions.RegionizedServer") != null;
            } catch (Exception e) {
                // Not Folia
            }
            if (isFolia) {
                return "Folia";
            }
            
            boolean isCanvas = false;
            try {
                isCanvas = Class.forName("io.canvasmc.canvas.threadedregions.ServerRegionizer") != null;
            } catch (Exception e) {
                // Not Canvas
            }
            if (isCanvas) {
                return "Canvas";
            }
            
            // Check for LeafMC (must be checked before Paper, as LeafMC is based on Paper)
            boolean isLeafMC = false;
            try {
                Class.forName("org.dreeam.leaf.event.BlockExplosionHitEvent");
                isLeafMC = true;
            } catch (Exception e) {
                try {
                    // Alternative: check for other LeafMC classes
                    Class.forName("org.dreeam.leaf.Leaf");
                    isLeafMC = true;
                } catch (Exception e2) {
                    // Not LeafMC
                }
            }
            if (isLeafMC) {
                return "LeafMC";
            }
            
            
            // Check for PurpurMC (must be checked before Paper, as PurpurMC is based on Paper)
            boolean isPurpur = false;
            try {
                isPurpur = Class.forName("org.purpurmc.purpur.event.PurpurEvent") != null;
            } catch (Exception e) {
                try {
                    Class.forName("org.purpurmc.purpur.PurpurConfig");
                    isPurpur = true;
                } catch (Exception e2) {
                    // Not PurpurMC
                }
            }
            if (isPurpur) {
                return "PurpurMC";
            }
            
            // Check for Paper
            boolean isPaper = false;
            try {
                Class.forName("com.destroystokyo.paper.PaperConfig");
                isPaper = true;
            } catch (ClassNotFoundException e) {
                try {
                    Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
                    isPaper = true;
                } catch (ClassNotFoundException e2) {
                    try {
                        Class.forName("io.papermc.paper.Paper");
                        isPaper = true;
                    } catch (ClassNotFoundException e3) {
                    }
                }
            }
            
            String version = Bukkit.getVersion();
            if (isPaper || version.contains("Paper") || version.contains("git-Paper")) {
                return "Paper";
            }
            
            try {
                Class.forName("org.spigotmc.SpigotConfig");
                if (version.contains("Spigot") || version.contains("git-Spigot")) {
                    return "Spigot";
                }
            } catch (ClassNotFoundException e) {
            }
            
            if (version.contains("CraftBukkit") || version.contains("git-Bukkit")) {
                return "CraftBukkit";
            }
            
            return "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    // TODO: maybe use a library for this (or not? 🤔)
    @SuppressWarnings("unchecked")
    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof Map) {
                json.append(mapToJson((Map<String, Object>) value));
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(value).append("\"");
            }
            first = false;
        }
        json.append("}");
        return json.toString();
    }
    
    private String generateHmacSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            plugin.getLogger().severe("HMAC error: " + e.getMessage());
            return "";
        }
    }
}

