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
    
    /**
     * WARNING: Do NOT modify this value - will result in PERMANENT BAN
     * The interval of 1800 seconds (30 minutes) is enforced by the API.
     * Changing this violates API terms and leads to immediate server banning.
     */
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
    
    /**
     * Collects server statistics and sends them to the API.
     * This method gathers:
     * - Current player count
     * - Version distribution (via ViaVersion if available)
     * - Launcher distribution (Lunar, Badlion, etc.)
     * - Mod loader distribution (Fabric, Forge, Quilt, etc.)
     * - Server software type (Paper, Spigot, Folia, etc.)
     * - Server uptime
     * 
     * The data is sent asynchronously with HMAC-SHA256 signature for authentication.
     * 
     * @param serverUuid The unique server identifier
     */
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
                // Skip players where detection fails (silent)
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
                
                /**
                 * WARNING: 
                 * - NEVER save the API secret to disk (config.yml, files, etc.) - will result in PERMANENT BAN
                 * - The secret MUST only be stored in memory
                 * - Never log, print, or expose the API secret in any form
                 */
                String apiSecret = plugin.getApiSecret();
                if (apiSecret == null || apiSecret.isEmpty()) {
                    plugin.getLogger().severe("API secret not available! Cannot send stats with HMAC signature.");
                    plugin.getLogger().severe("Please run /statsly setup <code> again to retrieve the API secret.");
                    return;
                }
                
                /**
                 * WARNING: Do NOT modify the signature generation algorithm - will result in PERMANENT BAN
                 */
                String hmacSignature = generateHmacSignature(jsonPayload, apiSecret);
                
                conn.setRequestProperty("X-Statsly-Signature", hmacSignature);
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                
                String responseBody = null;
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(
                            responseCode == 200 ? conn.getInputStream() : conn.getErrorStream(),
                            StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    responseBody = response.toString();
                } catch (Exception readErr) {
                    // Ignore read errors
                }
                
                if (responseCode == 200) {
                    // Stats sent successfully (silent)
                } else {
                    String errorMessage = parseErrorMessage(responseBody);
                    
                    if (errorMessage.contains("Unauthorized") || errorMessage.contains("IP")) {
                        plugin.getLogger().severe("Failed to send stats: Authentication failed!");
                        plugin.getLogger().severe("This usually means the server IP doesn't match the registered IP.");
                        plugin.getLogger().severe("Please verify your server IP and port match the registered values.");
                    } else if (errorMessage.contains("Server not found") || errorMessage.contains("serverUuid")) {
                        plugin.getLogger().severe("Failed to send stats: Server UUID not found in database!");
                        plugin.getLogger().severe("Server UUID used: " + serverUuid);
                        plugin.getLogger().severe("Please verify that the server is properly connected. Run /statsly setup <code> again if needed.");
                    } else {
                        plugin.getLogger().severe("Failed to send stats: HTTP " + responseCode);
                        if (errorMessage != null && !errorMessage.isEmpty()) {
                            plugin.getLogger().severe("Error: " + errorMessage);
                        }
                    }
                }
                
                joinsSinceLastReport = 0;
                leavesSinceLastReport = 0;
                
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to send stats: " + e.getMessage());
            }
        });
    }
    
    private String parseErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "Unknown error";
        }
        
        if (responseBody.contains("\"error\"")) {
            int errorStart = responseBody.indexOf("\"error\"") + 9;
            int errorEnd = responseBody.indexOf("\"", errorStart);
            if (errorEnd > errorStart) {
                return responseBody.substring(errorStart, errorEnd);
            }
        }
        
        if (responseBody.contains("\"message\"")) {
            int messageStart = responseBody.indexOf("\"message\"") + 11;
            int messageEnd = responseBody.indexOf("\"", messageStart);
            if (messageEnd > messageStart) {
                return responseBody.substring(messageStart, messageEnd);
            }
        }
        
        return responseBody;
    }
    
    /**
     * Detects the server software type by checking for specific classes.
     * Uses reflection to identify the server software without requiring dependencies.
     * Checks in order: Folia, Canvas, LeafMC, PurpurMC, Paper, Spigot, CraftBukkit.
     * 
     * This is necessary because different server software types may have different
     * capabilities or require different handling.
     * 
     * @return The detected server software name (e.g., "Paper", "Spigot", "Folia")
     */
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
                        // Not found via class check, will check version string
                    }
                }
            }
            
            String version = Bukkit.getVersion();
            
            if (isPaper || version.contains("Paper")) {
                return "Paper";
            }
            
            try {
                Class.forName("org.spigotmc.SpigotConfig");
                if (version.contains("Spigot")) {
                    return "Spigot";
                }
            } catch (ClassNotFoundException e) {
                // Not Spigot
            }
            
            if (version.contains("CraftBukkit")) {
                return "CraftBukkit";
            }
            
            if (version.contains("git-Paper")) {
                return "Paper";
            } else if (version.contains("git-Spigot")) {
                return "Spigot";
            } else if (version.contains("git-Bukkit")) {
                return "CraftBukkit";
            }
            
            return "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * Recursively converts a Map to a JSON string.
     * Handles nested maps, numbers, booleans, and strings.
     * Used to serialize metadata before sending to the API.
     * 
     * @param map The map to convert to JSON
     * @return A JSON string representation of the map
     */
    @SuppressWarnings("unchecked")
    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");
            
            if (entry.getValue() instanceof Map) {
                json.append(mapToJson((Map<String, Object>) entry.getValue()));
            } else if (entry.getValue() instanceof Number) {
                json.append(entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                json.append(entry.getValue());
            } else {
                json.append("\"").append(entry.getValue()).append("\"");
            }
            first = false;
        }
        json.append("}");
        return json.toString();
    }
    

    /**
     * Generates HMAC-SHA256 signature for API request authentication.
     * 
     * WARNING: Do NOT modify this function - will result in PERMANENT BAN
     * - Changing the algorithm (HmacSHA256) violates API terms
     * - Never log or expose the secret parameter
     * 
     * @param payload JSON payload to sign
     * @param secret API secret (never log this value)
     * @return Hexadecimal HMAC signature string
     */
    private String generateHmacSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to generate HMAC signature: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }
}

