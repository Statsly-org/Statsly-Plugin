package me.statix.commands;

import me.Statix;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StatixCommand implements CommandExecutor, TabCompleter {

    private final Statix plugin;
    private String lastCheckedCode = null;

    public StatixCommand(Statix plugin) {
        this.plugin = plugin;
    }
    
    private String getApiUrl() {
        return "https://api.statsly.org";
    }
    
    private String getValidateCodeUrl() {
        return getApiUrl() + "/api/servers/validate-code";
    }
    
    private String getUuidCheckUrl() {
        return getApiUrl() + "/api/servers/code";
    }
    
    private String getSecretUrl() {
        return getApiUrl() + "/api/servers/get-secret";
    }
    
    /**
     * Retrieves the API secret from the backend.
     * 
     * WARNING: 
     * - NEVER save the API secret to disk (config.yml, files, etc.) - this will result in a PERMANENT BAN
     * - The secret MUST only be stored in memory
     * - Never log or expose the API secret in any form
     */
    private void retrieveApiSecret(String serverUuid, CommandSender sender, BukkitRunnable runnable) {
        try {
            HttpURLConnection conn = createConnection(getSecretUrl());
            
            String jsonPayload = String.format(
                "{\"serverUuid\":\"%s\"}",
                serverUuid
            );

            sendRequest(conn, jsonPayload);

            int responseCode = conn.getResponseCode();
            String responseBody = readResponse(conn, responseCode == 200);
            
            getServer().getScheduler().runTask(getPlugin(), () -> {
                if (responseCode == 200) {
                    String apiSecret = extractApiSecret(responseBody);
                    if (apiSecret != null && !apiSecret.isEmpty()) {
                        plugin.setApiSecret(apiSecret);
                        sender.sendMessage("§7[§6Statsly§7] §aAPI secret retrieved and stored securely!");
                        sender.sendMessage("§7[§6Statsly§7] §7Secret is stored in memory (not in config.yml for security)");
                        if (runnable != null) {
                            runnable.cancel();
                        }
                    } else {
                        sender.sendMessage("§7[§6Statsly§7] §cFailed to extract API secret from response");
                    }
                } else {
                    String errorMessage = parseErrorMessage(responseBody);
                    sender.sendMessage("§7[§6Statsly§7] §cFailed to retrieve API secret: " + errorMessage);
                }
            });
        } catch (Exception e) {
            getServer().getScheduler().runTask(getPlugin(), () -> {
                sender.sendMessage("§7[§6Statsly§7] §cFailed to retrieve API secret!");
                sender.sendMessage("§7[§6Statsly§7] §7Error: " + e.getMessage());
            });
            plugin.getLogger().severe("Failed to retrieve API secret: " + e.getMessage());
        }
    }
    
    /**
     * Extracts the API secret from the JSON response.
     * 
     * WARNING: 
     * - NEVER save this secret to disk - will result in PERMANENT BAN
     * - Secret must only exist in memory
     * - Never log, print, or expose the extracted secret
     */
    private String extractApiSecret(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }
        
        if (responseBody.contains("\"apiSecret\"")) {
            int secretStart = responseBody.indexOf("\"apiSecret\":\"") + 14;
            int secretEnd = responseBody.indexOf("\"", secretStart);
            if (secretEnd > secretStart) {
                return responseBody.substring(secretStart, secretEnd);
            }
        }
        
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("setup")) {
            sender.sendMessage("§7[§6Statsly§7] §eUsage: /statsly setup <code>");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§7[§6Statsly§7] §cPlease provide a setup code!");
            sender.sendMessage("§7[§6Statsly§7] §eUsage: /statsly setup <code>");
            return true;
        }
        
        handleSetup(sender, args[1]);
        return true;
    }

    private void handleSetup(CommandSender sender, String code) {
        final String minecraftUser = (sender instanceof Player) 
            ? ((Player) sender).getName() 
            : "CONSOLE";

        final String serverIp = getServerIp(sender);
        final int serverPort = getServerPort(sender);

        sender.sendMessage("§7[§6Statsly§7] §eValidating code...");

        CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection conn = createConnection(getValidateCodeUrl());
                
                String jsonPayload = String.format(
                    "{\"code\":\"%s\",\"minecraftUser\":\"%s\",\"serverIp\":\"%s\",\"serverPort\":%d}",
                    code, minecraftUser, serverIp, serverPort
                );

                sendRequest(conn, jsonPayload);

                int responseCode = conn.getResponseCode();
                String responseBody = readResponse(conn, responseCode == 200);
                
                final String finalCode = code;
                getServer().getScheduler().runTask(getPlugin(), () -> {
                    if (responseCode == 200) {
                        sender.sendMessage("§7[§6Statsly§7] §aCode validated successfully!");
                        sender.sendMessage("§7[§6Statsly§7] §eWaiting for confirmation on the dashboard...");
                        sender.sendMessage("§7[§6Statsly§7] §7Please check your Statsly dashboard to confirm the connection.");
                        
                        lastCheckedCode = finalCode;
                        startCheckingForUuid(finalCode, sender);
                    } else {
                        String errorMessage = parseErrorMessage(responseBody);
                        sender.sendMessage("§7[§6Statsly§7] §cError: " + errorMessage);
                    }
                });
            } catch (Exception e) {
                getServer().getScheduler().runTask(getPlugin(), () -> {
                    sender.sendMessage("§7[§6Statsly§7] §cFailed to connect to Statsly server!");
                    sender.sendMessage("§7[§6Statsly§7] §7Error: " + e.getMessage());
                });
                plugin.getLogger().severe("Failed to validate code: " + e.getMessage());
            }
        });
    }

    /**
     * Creates and configures an HTTP connection to the API.
     * Sets up POST method, JSON content type, and timeout values.
     * 
     * @param url The API endpoint URL
     * @return Configured HttpURLConnection ready for use
     * @throws Exception If connection setup fails
     */
    private HttpURLConnection createConnection(String url) throws Exception {
        URI uri = new URI(url);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        return conn;
    }

    /**
     * Sends a JSON payload to the HTTP connection.
     * Writes the payload bytes to the connection's output stream.
     * 
     * @param conn The HTTP connection to send data to
     * @param jsonPayload The JSON string to send
     * @throws Exception If writing to the stream fails
     */
    private void sendRequest(HttpURLConnection conn, String jsonPayload) throws Exception {
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
    }

    /**
     * Reads the response from an HTTP connection.
     * Reads from either the input stream (success) or error stream (failure).
     * 
     * @param conn The HTTP connection to read from
     * @param success Whether to read from input stream (true) or error stream (false)
     * @return The response body as a string
     * @throws Exception If reading the response fails
     */
    private String readResponse(HttpURLConnection conn, boolean success) throws Exception {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                    success ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
            return response.toString();
        }
    }

    /**
     * Parses an error message from a JSON response body.
     * Looks for "error" or "message" fields in the JSON.
     * 
     * @param responseBody The JSON response string
     * @return The extracted error message, or "Unknown error" if not found
     */
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
        
        return "Unknown error";
    }

    private String getServerIp(CommandSender sender) {
        String ip = sender.getServer().getIp();
        return (ip == null || ip.isEmpty()) ? "localhost" : ip;
    }

    private int getServerPort(CommandSender sender) {
        return sender.getServer().getPort();
    }

    private org.bukkit.Server getServer() {
        return org.bukkit.Bukkit.getServer();
    }

    private org.bukkit.plugin.Plugin getPlugin() {
        return plugin;
    }
    
    /**
     * Polls the API to check if the server UUID has been assigned after code validation.
     * 
     * WARNING: 
     * - Do NOT modify the polling interval (100 ticks = 5 seconds) - will result in PERMANENT BAN
     * - Do NOT increase maxAttempts (60) - will result in PERMANENT BAN
     * - Changing intervals or request frequencies violates API terms and leads to immediate banning
     */
    private void startCheckingForUuid(String code, CommandSender sender) {
        class RunnableHolder {
            BukkitRunnable runnable;
        }
        final RunnableHolder holder = new RunnableHolder();
        
        holder.runnable = new BukkitRunnable() {
            private int attempts = 0;
            /**
             * WARNING: Do NOT modify this value - will result in PERMANENT BAN
             */
            private final int maxAttempts = 60;
            
            @Override
            public void run() {
                if (attempts >= maxAttempts) {
                    sender.sendMessage("§7[§6Statsly§7] §cConnection timeout. Please try again.");
                    cancel();
                    return;
                }
                
                attempts++;
                
                String existingUuid = plugin.getServerUuid();
                if (existingUuid != null && !existingUuid.isEmpty()) {
                    sender.sendMessage("§7[§6Statsly§7] §aServer already connected! UUID: " + existingUuid);
                    cancel();
                    return;
                }
                
                CompletableFuture.runAsync(() -> {
                    try {
                        String normalizedCode = code.replace("-", "").toUpperCase();
                        URI uri = new URI(getUuidCheckUrl() + "/" + normalizedCode);
                        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(20000);
                        
                        int responseCode = conn.getResponseCode();
                        
                        if (responseCode == 200) {
                            try (BufferedReader br = new BufferedReader(
                                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                                StringBuilder response = new StringBuilder();
                                String line;
                                while ((line = br.readLine()) != null) {
                                    response.append(line.trim());
                                }
                                
                                String responseStr = response.toString();
                                if (responseStr.contains("\"connected\":true")) {
                                    int uuidStart = responseStr.indexOf("\"serverUuid\":\"") + 15;
                                    int uuidEnd = responseStr.indexOf("\"", uuidStart);
                                    if (uuidEnd > uuidStart) {
                                        String serverUuid = responseStr.substring(uuidStart, uuidEnd);
                                        
                                        getServer().getScheduler().runTask(getPlugin(), () -> {
                                            plugin.saveServerUuid(serverUuid);
                                            sender.sendMessage("§7[§6Statsly§7] §aServer connected successfully!");
                                            sender.sendMessage("§7[§6Statsly§7] §7Server UUID saved to config.yml");
                                            sender.sendMessage("§7[§6Statsly§7] §eRetrieving API secret...");
                                            
                                            CompletableFuture.runAsync(() -> {
                                                retrieveApiSecret(serverUuid, sender, holder.runnable);
                                            });
                                        });
                                        return;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore errors, will retry
                    }
                });
            }
        };
        /**
         * WARNING: Do NOT modify the polling interval (100L ticks = 5 seconds) - will result in PERMANENT BAN
         */
        holder.runnable.runTaskTimer(getPlugin(), 100L, 100L);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "setup".startsWith(args[0].toLowerCase())) {
            List<String> list = new ArrayList<>();
            list.add("setup");
            return list;
        }
        return new ArrayList<>();
    }
}

