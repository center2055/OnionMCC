# OnionMCC — Minecraft Ghost Client

**Open-source injectable ghost client for Minecraft 1.8–1.21.x**

Designed for anarchy servers and environments where modifications are permitted.

## Features

### 🏗️ Architecture
- **Java Attach API injection** — no native code needed
- **Reflection-based** Minecraft access via version-specific JSON mappings
- **External JavaFX GUI** with modern dark theme
- **TCP IPC** for real-time module state sync between GUI and injected client
- **Profile-based config** with JSON persistence

### ⚔️ Combat (8 modules)
| Module | Description |
|--------|-------------|
| KillAura | Auto-attacks nearby entities with configurable range, CPS, FOV, priority |
| TriggerBot | Attacks crosshair target with randomized timing |
| Reach | Extends attack distance (3.0–6.0 blocks) |
| Velocity | Reduces/cancels knockback (Simple, Cancel, Reverse modes) |
| AutoClicker | Randomized CPS with jitter simulation |
| Criticals | Forces critical hits (Packet, Jump, MiniJump modes) |
| WTap | Re-sprint combo optimization |
| AimAssist | Smooth rotation toward targets within FOV |

### 🏃 Movement (6 modules)
Sprint, Speed (Vanilla/BHop/Strafe), Flight (Vanilla/Glide/Jetpack), NoFall, Step, NoSlowdown

### 👁 Render (6 modules)
ESP, Tracers, Nametags, Fullbright, NoHurtCam, ChestESP

### 🎮 Player (2 modules)
AutoArmor, InventoryManager

### 🔧 Utility (2 modules)
FastPlace, Timer

## Building

```bash
# Build everything
./gradlew build

# Run the launcher GUI
./gradlew :launcher:run
```

## Usage

1. **Launch Minecraft** (any version 1.8.9+)
2. **Run the OnionMCC Launcher** (`./gradlew :launcher:run`)
3. **Click Refresh** to detect Minecraft processes
4. **Select your MC process** from the dropdown
5. **Click Inject** — the agent loads into the MC JVM
6. **Manage modules** via the external GUI — toggle, configure settings
7. **Save config** to persist your setup

## Project Structure

```
OnionMCC/
├── agent/          # Java Agent (premain/agentmain injection bootstrap)
├── client/         # Core client library (modules, events, mappings, IPC server)
├── launcher/       # External JavaFX GUI (injector, IPC client, module management)
└── mappings/       # Version-specific obfuscation mappings (JSON)
```

## Requirements
- Java 17+
- Gradle 8.5+

## License
MIT — Use responsibly. Only on servers where modifications are allowed.

## ⚠️ Disclaimer
This client is designed for use on anarchy servers and practice environments where modifications are permitted. The developers are not responsible for any bans or consequences resulting from misuse.
