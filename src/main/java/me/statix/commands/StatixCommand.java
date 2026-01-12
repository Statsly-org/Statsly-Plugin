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
    
    private String tsIsApiUri() {
        return "https://api.statsly.org";
    }
    
    private String get_code_url() {
        return tsIsApiUri() + "/api/servers/validate-code";
    }
    
    private String get_uuid_uri() {
        return tsIsApiUri() + "/api/servers/code";
    }
    
    private String get_very_secret_url() {
        return tsIsApiUri() + "/api/servers/get-secret";
    }
    
    private void retrieveApiSecret(String serverUuid, CommandSender sender, BukkitRunnable runnable) {
        try {
            URI uri = new URI(get_very_secret_url());
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            
            String jsonPayload = String.format("{\"serverUuid\":\"%s\"}", serverUuid);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            String responseBody = "";
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                        responseCode == 200 ? conn.getInputStream() : conn.getErrorStream(),
                        StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
                responseBody = response.toString();
            } catch (Exception readErr) {
            }
            
            String finalResponseBody = responseBody;
            org.bukkit.Bukkit.getServer().getScheduler().runTask(plugin, () -> {
                if (responseCode == 200) {
                    String apiSecret = extractApiSecret(finalResponseBody);
                    if (apiSecret != null && !apiSecret.isEmpty()) {
                        plugin.setApiSecret(apiSecret);
                        sender.sendMessage("§7[§6Statsly§7] §aSecret retrieved!");
                        if (runnable != null) {
                            runnable.cancel();
                        }
                    } else {
                        sender.sendMessage("§7[§6Statsly§7] §cCouldn't get secret");
                    }
                } else {
                    String err = "Unknown error";
                    if (finalResponseBody != null && finalResponseBody.contains("\"error\"")) {
                        int start = finalResponseBody.indexOf("\"error\"") + 9;
                        int end = finalResponseBody.indexOf("\"", start);
                        if (end > start) {
                            err = finalResponseBody.substring(start, end);
                        }
                    }
                    sender.sendMessage("§7[§6Statsly§7] §cError: " + err);
                }
            });
        } catch (Exception e) {
            // TODO: better error handling
            org.bukkit.Bukkit.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§7[§6Statsly§7] §cFailed!");
            });
        }
    }
    
    private String extractApiSecret(String resp) {
        if (resp == null || resp.isEmpty()) {
            return null;
        }
        
        if (resp.contains("\"apiSecret\"")) {
            int start = resp.indexOf("\"apiSecret\":\"") + 14;
            int end = resp.indexOf("\"", start);
            if (end > start) {
                return resp.substring(start, end);
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
            return true;
        }
        
        handleSetup(sender, args[1]);
        return true;
    }

    private void handleSetup(CommandSender sender, String code) {
        String minecraftUser = (sender instanceof Player) 
            ? ((Player) sender).getName() 
            : "CONSOLE";

        String serverIp = sender.getServer().getIp();
        if (serverIp == null || serverIp.isEmpty()) {
            serverIp = "localhost";
        }
        int serverPort = sender.getServer().getPort();

        sender.sendMessage("§7[§6Statsly§7] §eValidating code...");

        CompletableFuture.runAsync(() -> {
            try {
                URI uri = new URI(get_code_url());
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                
                String jsonPayload = String.format(
                    "{\"code\":\"%s\",\"minecraftUser\":\"%s\",\"serverIp\":\"%s\",\"serverPort\":%d}",
                    code, minecraftUser, serverIp, serverPort
                );

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                String responseBody = "";
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(
                            responseCode == 200 ? conn.getInputStream() : conn.getErrorStream(),
                            StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                    responseBody = response.toString();
                } catch (Exception readErr) {
                }
                
                String finalCode = code;
                String finalResponseBody = responseBody;
                org.bukkit.Bukkit.getServer().getScheduler().runTask(plugin, () -> {
                    if (responseCode == 200) {
                        sender.sendMessage("§7[§6Statsly§7] §aCode valid!");
                        sender.sendMessage("§7[§6Statsly§7] §eWaiting for confirmation...");
                        
                        lastCheckedCode = finalCode;
                        startCheckingForUuid(finalCode, sender);
                    } else {
                        String err = "Unknown error";
                        if (finalResponseBody != null && finalResponseBody.contains("\"error\"")) {
                            int start = finalResponseBody.indexOf("\"error\"") + 9;
                            int end = finalResponseBody.indexOf("\"", start);
                            if (end > start) {
                                err = finalResponseBody.substring(start, end);
                            }
                        }
                        sender.sendMessage("§7[§6Statsly§7] §cError: " + err);
                    }
                });
            } catch (Exception e) {
                org.bukkit.Bukkit.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§7[§6Statsly§7] §cConnection failed!");
                });
                e.printStackTrace();
            }
        });
    }

    // TODO: maybe refactor this later (idk yet lol)
    
    private void startCheckingForUuid(String code, CommandSender sender) {
        class RunnableHolder {
            BukkitRunnable runnable;
        }
        final RunnableHolder holder = new RunnableHolder();
        
        holder.runnable = new BukkitRunnable() {
            private int attempts = 0;
            private int maxAttempts = 60;
            
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
                    sender.sendMessage("§7[§6Statsly§7] §aAlready connected: " + existingUuid);
                    cancel();
                    return;
                }
                
                CompletableFuture.runAsync(() -> {
                    try {
                        String normalizedCode = code.replace("-", "").toUpperCase();
                        URI uri = new URI(get_uuid_uri() + "/" + normalizedCode);
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
                                        
                                        org.bukkit.Bukkit.getServer().getScheduler().runTask(plugin, () -> {
                                            plugin.saveServerUuid(serverUuid);
                                            sender.sendMessage("§7[§6Statsly§7] §aConnected!");
                                            sender.sendMessage("§7[§6Statsly§7] §eGetting secret...");
                                            
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
                        // ignore
                    }
                });
            }
        };
        holder.runnable.runTaskTimer(plugin, 100L, 100L);
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

