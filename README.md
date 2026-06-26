# The-Better-Folia

A high-performance Folia-compatible Bukkit/Paper plugin providing AppleSkin, Litematica EasyPlace, and ServUX protocol support, along with an item cleanup system.

## Features

### Protocol Support
- **AppleSkin** — Syncs player saturation and exhaustion data with the AppleSkin mod client
- **Litematica EasyPlace** — ProtocolLib-based schematic block state correction for Litematica's EasyPlace mode
- **ServUX / MiniHUD Integration**
  - Entity data preview (players, mobs, block entities)
  - HUD data (TPS, MSPT, world time)
  - Container preview (inventories, ender chests)
  - Structure bounding box display
  - Litematica schematic paste

### Item Cleanup
- Automatic scheduled item (dropped item) cleanup
- Configurable warning broadcasts before cleanup
- Public recycle bin GUI (`/rubish`) with pagination
- Player voting system (`/clean`) to trigger manual cleanup
- Configurable auto-clear for bin overflow

## Requirements

- Java 21+
- Paper 1.21.x or Folia
- ProtocolLib (for EasyPlace feature)

## Build

```bash
./gradlew build -Pv=<version>
```

## Installation

1. Place the JAR in `plugins/`
2. Ensure ProtocolLib is installed
3. Restart or reload the server
4. Edit `plugins/The-Better-Folia/config.yml` as desired

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/rubish` | `thebetterfolia.rubish` | Open the public recycle bin |
| `/clean` | `thebetterfolia.clean` | Start a cleanup vote |
| `/clean yes` | `thebetterfolia.clean` | Vote yes on an active cleanup |

## Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `thebetterfolia.admin` | `op` | Access to all admin commands |
| `thebetterfolia.rubish` | `true` | Use the recycle bin |
| `thebetterfolia.clean` | `true` | Initiate and vote on cleanup |
| `thebetterfolia.protocol.*` | `true` | All protocol features |
| `thebetterfolia.protocol.appleskin` | `true` | AppleSkin protocol |
| `thebetterfolia.protocol.litematica` | `true` | Litematica EasyPlace protocol |
| `thebetterfolia.protocol.servux` | `true` | ServUX protocols |

## Configuration

See `config.yml` for the full configuration. Key sections:

- **`cleanup`** — Interval, warnings, recycle bin, voting settings
- **`servux`** — Entity sync, HUD sync, structures, container preview toggles
- **`schematica`** — Litematics and EasyPlace toggles
- **`appleskin`** — AppleSkin protocol toggle

## Server Compatibility

| Environment | Status |
|-------------|--------|
| Folia | Full support |
| Paper | Full support |
| Spigot | Limited (protocol features unavailable) |
| Bukkit | Limited (protocol features unavailable) |

## License

MIT
