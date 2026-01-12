package me.statix.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;


public class ViaVersionDetector {
    
    private static Boolean viaVersionAvailable = null;

    public static void resetCache() {
        viaVersionAvailable = null;
    }
    
    public static boolean isViaVersionAvailable() {
        if (viaVersionAvailable != null) {
            return viaVersionAvailable;
        }
        
        Plugin viaVersion = Bukkit.getPluginManager().getPlugin("ViaVersion");
        if (viaVersion == null || !viaVersion.isEnabled()) {
            viaVersionAvailable = false;
            return false;
        }
        
        String[] possibleClasses = {
            "com.viaversion.viaversion.api.Via",
            "us.myles.ViaVersion.api.ViaVersionAPI",
            "com.viaversion.viaversion.api.ViaAPI"
        };
        
        for (String className : possibleClasses) {
            try {
                Class<?> viaClass = Class.forName(className);
                java.lang.reflect.Method getAPIMethod = viaClass.getMethod("getAPI");
                Object api = getAPIMethod.invoke(null);
                if (api != null) {
                    viaVersionAvailable = true;
                    return true;
                }
            } catch (Exception e) {
                // try next class
            }
        }
        
        viaVersionAvailable = false;
        return false;
    }
    
    public static int getPlayerProtocolVersion(Player player) {
        if (!isViaVersionAvailable()) {
            return -1;
        }
        
        try {
            try {
                Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
                java.lang.reflect.Method getAPIMethod = viaClass.getMethod("getAPI");
                Object viaAPI = getAPIMethod.invoke(null);
                
                if (viaAPI == null) {
                    throw new ClassNotFoundException("Via.getAPI() returned null");
                }
                
                java.lang.reflect.Method getPlayerVersionMethod = null;
                Object versionObj = null;
                
                try {
                    getPlayerVersionMethod = viaAPI.getClass().getMethod("getPlayerVersion", Player.class);
                    versionObj = getPlayerVersionMethod.invoke(viaAPI, player);
                } catch (NoSuchMethodException e) {
                    try {
                        getPlayerVersionMethod = viaAPI.getClass().getMethod("getPlayerVersion", java.util.UUID.class);
                        versionObj = getPlayerVersionMethod.invoke(viaAPI, player.getUniqueId());
                    } catch (NoSuchMethodException e2) {
                        try {
                            getPlayerVersionMethod = viaAPI.getClass().getMethod("getProtocolVersion", Player.class);
                            versionObj = getPlayerVersionMethod.invoke(viaAPI, player);
                        } catch (NoSuchMethodException e3) {
                            throw new NoSuchMethodException("Could not find getPlayerVersion method");
                        }
                    }
                }
                
                if (versionObj instanceof Integer) {
                    return (Integer) versionObj;
                } else if (versionObj instanceof Number) {
                    return ((Number) versionObj).intValue();
                }
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                try {
                    Class<?> viaVersionClass = Class.forName("us.myles.ViaVersion.api.ViaVersionAPI");
                    java.lang.reflect.Method getAPIMethod = viaVersionClass.getMethod("getAPI");
                    Object viaAPI = getAPIMethod.invoke(null);
                    
                    if (viaAPI == null) {
                        return -1;
                    }
                    
                    java.lang.reflect.Method getPlayerVersionMethod = viaAPI.getClass().getMethod("getPlayerVersion", Player.class);
                    Object versionObj = getPlayerVersionMethod.invoke(viaAPI, player);
                    
                    if (versionObj instanceof Integer) {
                        return (Integer) versionObj;
                    } else if (versionObj instanceof Number) {
                        return ((Number) versionObj).intValue();
                    }
                } catch (ClassNotFoundException | NoSuchMethodException e2) {
                }
            }
        } catch (Exception e) {
            // ignore
        }
        
        return -1;
    }
    
    public static String protocolToVersion(int protocolVersion) {
        Map<Integer, String> versionMap = new HashMap<>();
        
        versionMap.put(768, "1.21.5");
        versionMap.put(767, "1.21.4");
        versionMap.put(766, "1.21.3");
        versionMap.put(765, "1.21.2");
        versionMap.put(764, "1.21.1");
        versionMap.put(763, "1.21");
        
        versionMap.put(770, "1.20.6");
        versionMap.put(769, "1.20.5");
        versionMap.put(762, "1.20");
        versionMap.put(761, "1.19.4");
        versionMap.put(760, "1.19.3");
        versionMap.put(759, "1.19.2");
        versionMap.put(758, "1.19");
        versionMap.put(757, "1.18.2");
        versionMap.put(756, "1.18.1");
        versionMap.put(755, "1.18");
        versionMap.put(754, "1.17.1");
        versionMap.put(753, "1.17");
        versionMap.put(751, "1.16.5");
        versionMap.put(736, "1.16.2");
        versionMap.put(735, "1.16.1");
        versionMap.put(734, "1.16");
        versionMap.put(578, "1.15.2");
        versionMap.put(575, "1.15.1");
        versionMap.put(573, "1.15");
        versionMap.put(498, "1.14.4");
        versionMap.put(490, "1.14.3");
        versionMap.put(485, "1.14.2");
        versionMap.put(480, "1.14.1");
        versionMap.put(477, "1.14");
        versionMap.put(404, "1.13.2");
        versionMap.put(401, "1.13.1");
        versionMap.put(393, "1.13");
        versionMap.put(340, "1.12.2");
        versionMap.put(338, "1.12.1");
        versionMap.put(335, "1.12");
        versionMap.put(316, "1.11.2");
        versionMap.put(315, "1.11.1");
        versionMap.put(210, "1.10.2");
        versionMap.put(110, "1.9.4");
        versionMap.put(109, "1.9.2");
        versionMap.put(107, "1.9");
        versionMap.put(47, "1.8.9");
        versionMap.put(5, "1.7.10");
        
        String version = versionMap.get(protocolVersion);
        if (version != null) {
            return version;
        }
        
        return "Protocol " + protocolVersion;
    }
    
    public static Map<String, Integer> collectVersionDistribution() {
        if (!isViaVersionAvailable()) {
            return null;
        }
        
        Map<String, Integer> versionDistribution = new HashMap<>();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            int protocolVersion = getPlayerProtocolVersion(player);
            if (protocolVersion > 0) {
                String version = protocolToVersion(protocolVersion);
                versionDistribution.put(version, versionDistribution.getOrDefault(version, 0) + 1);
            }
        }
        
        return versionDistribution;
    }
}

