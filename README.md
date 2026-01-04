# Statsly - Minecraft Server Statistics Plugin

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-green.svg)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)

Statsly is a comprehensive statistics tracking plugin for Minecraft servers that automatically collects and reports server data to your Statsly dashboard.

## Features

- 📊 **Automatic Statistics Collection** - Tracks player counts, joins, leaves, and more
- 🎮 **Version Detection** - Detects player Minecraft versions using ViaVersion
- 🚀 **Launcher Detection** - Identifies Lunar Client, Badlion, LabyMod, and more
- 🔧 **Mod Loader Detection** - Detects Fabric, Forge, Quilt, and OptiFine
- 🔒 **Secure Authentication** - HMAC-SHA256 signature for all API requests
- ⚡ **Lightweight** - Minimal performance impact on your server
- 🔄 **Auto-Sync** - Automatically sends statistics every 30 minutes

## Supported Server Software

- ✅ **Spigot** - All versions 1.21+
- ✅ **Paper** - All versions 1.21+
- ✅ **PurpurMC** - All versions 1.21+
- ✅ **CraftBukkit** - All versions 1.21+

> **Note:** For Folia/CanvasMC servers, use [Statsly-Folia](https://github.com/Statsly-org/Statsly-Folia) instead.

## Requirements

- **Minecraft Server**: 1.21 or higher
- **Java**: 21 or higher


## Installation

1. Download the latest `Statsly-1.0.jar` from [https://statsly.org/versions](https://statsly.org/versions)
2. Place the JAR file in your server's `plugins/` folder
3. Start your server
4. Run `/statsly setup <code>` with your setup code from the dashboard
5. Confirm the connection in your Statsly dashboard

## Usage

### Setup Command

```
/statsly setup <code>
```

Replace `<code>` with the setup code from your Statsly dashboard.

### Command Aliases

- `/statsly` - Main command
- `/stx` - Short alias

## Configuration

The plugin automatically creates a `config.yml` file in the `plugins/Statsly/` directory:

```yaml
serverUuid: ""  # Automatically set during setup
```

## Statistics Collected

- Current player count
- Player joins/leaves since last report
- Minecraft version distribution (with ViaVersion)
- Client launcher distribution
- Mod loader distribution
- Server software type
- Server uptime

## API Integration

The plugin communicates with the Statsly backend API:

- `POST /api/servers/validate-code` - Validates setup code
- `GET /api/servers/code/:code` - Checks connection status
- `POST /api/servers/get-secret` - Retrieves API secret
- `POST /api/servers/stats` - Sends statistics (every 30 minutes)

All requests are authenticated using HMAC-SHA256 signatures.

## Building from Source

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## Dependencies

- **Spigot API** 1.21-R0.1-SNAPSHOT
- **ViaVersion** (optional, for multi-version support)

## Troubleshooting

### Plugin doesn't send statistics

1. Verify the server is connected: Check if `serverUuid` is set in `config.yml`
2. Check API connection: Ensure the Statsly backend is running on `https://api.statsly.org`
3. Verify IP/Port: Make sure your server IP and port match the registered values (Open Support Ticket for that)
4. Check logs: Look for error messages in `logs/latest.log`

### Authentication failed

- The server IP doesn't match the registered IP
- Verify your server's IP and port in the dashboard
- Re-run `/statsly setup <code>` if needed

## License

This project is proprietary software. All rights reserved.

## Support

If you encounter any bugs or issues with the plugin, please:
- Open a ticket on [statsly.org](https://statsly.org) (recommended)
- Or contact us on our [Discord server](https://discord.gg/pheByusq)

> **Note:** I'm relatively new to plugin development, so your patience and detailed bug reports are greatly appreciated!

## Contributing

We welcome contributions! We're especially looking for help with:
- **Plugin Updates**: Keeping plugins compatible with newer Minecraft/server versions
- **Legacy Support**: Creating plugin versions for older Minecraft versions

If you'd like to help, please:
1. Open a ticket on [statsly.org](https://statsly.org) describing what you'd like to contribute
2. We'll discuss the details and coordinate the work

Your contributions help make Statsly better for everyone!

---

Made with ❤️ by the Statsly Team
