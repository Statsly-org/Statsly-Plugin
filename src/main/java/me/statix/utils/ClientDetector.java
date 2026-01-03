package me.statix.utils;

import org.bukkit.entity.Player;

public class ClientDetector {
    

    public static String parseClientBrand(String brand) {
        if (brand == null || brand.isEmpty()) {
            return "Vanilla Minecraft";
        }
        
        String lowerBrand = brand.toLowerCase();
        
        brand = brand.replace(" (Velocity)", "");
        
        if (lowerBrand.contains("fabric")) {
            if (lowerBrand.contains("feather")) {
                return "Feather Client (Fabric)";
            } else if (lowerBrand.contains("iris")) {
                return "Iris Shaders (Fabric)";
            } else if (lowerBrand.contains("sodium")) {
                return "Sodium (Fabric)";
            } else if (lowerBrand.contains("lithium")) {
                return "Lithium (Fabric)";
            } else if (lowerBrand.contains("phosphor")) {
                return "Phosphor (Fabric)";
            } else {
                return "Fabric Client";
            }
        }
        
        if (lowerBrand.contains("forge")) {
            return "Forge Client";
        }
        
        if (lowerBrand.contains("quilt")) {
            return "Quilt Client";
        }
        
        if (lowerBrand.contains("optifine")) {
            return "OptiFine";
        }
        
        if (lowerBrand.contains("lunar")) {
            return "Lunar Client";
        }
        
        if (lowerBrand.contains("badlion")) {
            return "Badlion Client";
        }
        
        if (lowerBrand.contains("labymod")) {
            return "LabyMod";
        }
        
        if (lowerBrand.equals("vanilla") || lowerBrand.equals("minecraft")) {
            return "Vanilla Minecraft";
        }
        
        return "Custom Client (" + brand + ")";
    }
    

    public static ClientInfo getClientInfo(Player player) {
        try {
            String clientBrand = null;
            try {
                java.lang.reflect.Method method = player.getClass().getMethod("getClientBrandName");
                Object result = method.invoke(player);
                if (result != null) {
                    clientBrand = result.toString();
                }
            } catch (NoSuchMethodException e) {
                clientBrand = null;
            } catch (Exception e) {
                clientBrand = null;
            }
            
            String parsedClient = parseClientBrand(clientBrand);
            
            String launcher = "Vanilla";
            String lowerParsed = parsedClient.toLowerCase();
            if (lowerParsed.contains("lunar")) {
                launcher = "Lunar Client";
            } else if (lowerParsed.contains("badlion")) {
                launcher = "Badlion Client";
            } else if (lowerParsed.contains("labymod")) {
                launcher = "LabyMod";
            } else if (lowerParsed.contains("feather")) {
                launcher = "Feather Client";
            } else if (parsedClient.contains("Custom Client")) {
                launcher = "Custom";
            } else if (lowerParsed.equals("vanilla minecraft")) {
                launcher = "Vanilla";
            } else {
                launcher = parsedClient;
            }
            
            String modLoader = "None";
            if (parsedClient.contains("Fabric")) {
                modLoader = "Fabric";
            } else if (parsedClient.contains("Forge")) {
                modLoader = "Forge";
            } else if (parsedClient.contains("Quilt")) {
                modLoader = "Quilt";
            } else if (parsedClient.contains("OptiFine")) {
                modLoader = "OptiFine";
            }
            
            return new ClientInfo(parsedClient, launcher, modLoader);
        } catch (Exception e) {
            return new ClientInfo("Vanilla Minecraft", "Vanilla", "None");
        }
    }
    

    public static class ClientInfo {
        private final String clientName;
        private final String launcher;
        private final String modLoader;
        
        public ClientInfo(String clientName, String launcher, String modLoader) {
            this.clientName = clientName;
            this.launcher = launcher;
            this.modLoader = modLoader;
        }
        
        public String getClientName() {
            return clientName;
        }
        
        public String getLauncher() {
            return launcher;
        }
        
        public String getModLoader() {
            return modLoader;
        }
    }
}

