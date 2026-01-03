# Statsly Plugins - Übersicht

## Verfügbare Plugins

### 1. Statsly Standard Plugin (`Statix-MC-Pl/`)
- **Ziel**: Standard-Bukkit/Spigot/Paper Server
- **Datei**: `Statsly-1.0.jar`
- **Kompatibilität**: Spigot, Paper, PurpurMC, CraftBukkit
- **Minecraft**: 1.21+

### 2. Statsly Folia/CanvasMC Plugin (`Statix-MC-Pl-Folia/`)
- **Ziel**: Folia und CanvasMC Server
- **Datei**: `Statsly-Folia-1.0.jar`
- **Kompatibilität**: Folia, CanvasMC
- **Minecraft**: 1.21+
- **Besonderheit**: Region-basiertes Threading

### 3. Statsly Velocity Plugin (`Statix-MC-Pl-Velocity/`)
- **Ziel**: Velocity Proxy Server
- **Datei**: `Statsly-Velocity-1.0.jar`
- **Kompatibilität**: Velocity 3.0.0+
- **Besonderheit**: Proxy-spezifische Statistiken

### 4. Statsly BungeeCord Plugin (`Statix-MC-Pl-BungeeCord/`)
- **Ziel**: BungeeCord Proxy Server
- **Datei**: `Statsly-BungeeCord-1.0.jar`
- **Kompatibilität**: BungeeCord 1.21+
- **Besonderheit**: Proxy-spezifische Statistiken

### 5. Statsly Waterfall Plugin (`Statix-MC-Pl-Waterfall/`)
- **Ziel**: Waterfall Proxy Server (BungeeCord Fork)
- **Datei**: `Statsly-Waterfall-1.0.jar`
- **Kompatibilität**: Waterfall 1.21+
- **Besonderheit**: Proxy-spezifische Statistiken, BungeeCord-kompatibel

## Welches Plugin sollte ich verwenden?

### Für Game-Server:
- **Standard-Bukkit/Spigot/Paper**: Verwende `Statsly-1.0.jar`
- **Folia/CanvasMC**: Verwende `Statsly-Folia-1.0.jar`

### Für Proxy-Server:
- **Velocity**: Verwende `Statsly-Velocity-1.0.jar`
- **BungeeCord**: Verwende `Statsly-BungeeCord-1.0.jar`
- **Waterfall**: Verwende `Statsly-Waterfall-1.0.jar`

## Gemeinsame Features

Alle Plugins bieten:
- ✅ Server-Statistiken sammeln
- ✅ Player-Joins/Leaves tracken
- ✅ HMAC-SHA256 Authentifizierung
- ✅ Automatische Statistiken-Übermittlung
- ✅ `/statix setup <code>` Command

## Unterschiede

| Feature | Standard | Folia | Proxy (Velocity/BungeeCord/Waterfall) |
|---------|----------|-------|--------------------------------------|
| **Threading** | Standard-Bukkit | Region-basiert | Async |
| **Server-Typ** | Game-Server | Game-Server | Proxy-Server |
| **Player-Count** | Einzelner Server | Einzelner Server | Alle verbundenen Server |
| **Version-Erkennung** | ✅ | ✅ | ❌ (Proxy) |
| **Launcher-Erkennung** | ✅ | ✅ | ❌ (Proxy) |

## Build-Anleitung

Jedes Plugin kann einzeln gebaut werden:

```bash
# Standard Plugin
cd Statix-MC-Pl
./gradlew build

# Folia Plugin
cd Statix-MC-Pl-Folia
./gradlew build

# Velocity Plugin
cd Statix-MC-Pl-Velocity
./gradlew build

# BungeeCord Plugin
cd Statix-MC-Pl-BungeeCord
./gradlew build

# Waterfall Plugin
cd Statix-MC-Pl-Waterfall
./gradlew build
```

Die JAR-Dateien werden jeweils in `build/libs/` erstellt.

