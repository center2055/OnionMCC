# OnionMCC

**Injectable External UI Client for Minecraft 1.8.x**

## Core Architecture

- **Java Attach API Injection**: Zero native code footprint. Injects directly into the target JVM (Vanilla, Forge, or OptiFine).
- **Dynamic Obfuscation Mapping**: Resolves SRG and Notch names on the fly, allowing seamless execution regardless of the target's classloader environment.
- **Asynchronous Execution**: Bypasses the standard 50ms Minecraft tick quantization. Combat modules execute in isolated daemon threads with mathematical Gaussian delays to synthesize raw, human-like standard deviations.
- **Hardware-Level Input Polling**: Utilizes JNA to poll Windows Virtual Key Codes (`GetAsyncKeyState`), allowing keybinds and mouse buttons to trigger instantaneously without relying on the game's internal focus or input handling.

## Module Roster

### Combat
* **KillAura**: Asynchronous, highly erratic Gaussian timing bypasses stdDev and sync checks. Advanced GCD patching mimics physical mouse movement.
* **SilentKiller**: Attacks out-of-FOV entities silently with strict swing-before-attack packet ordering.
* **SilentAim**: Magnetizes hits outside the crosshair without visual snapping.
* **TriggerBot**: Threaded, unquantized automated attacking.
* **Reach**: Extends attack vectors.
* **Velocity**: Jump-resets and modifies incoming damage vectors.
* **AutoClicker**: Threaded click simulation with randomized CPS and jitter.
* **WTap**: Precise sprint-reset combo optimization.
* **AimAssist**: Smooth, multi-stage easing curve rotation with overshoot recovery.

### Movement
* **Sprint**: Automated sprinting logic.

### Render
* **ESP**: Renders bounding boxes around targets via an external, transparent click-through JWindow overlay.
* **Tracers**: Projects lines to targets.
* **ArrayList**: Displays active modules.
* **Fullbright**: Modifies internal gamma settings.

### Player & Utility
* **AutoArmor**: Mathematically scores and shift-clicks the highest protection tier armor automatically.
* **ChestStealer**: Dynamically identifies container sizes to rip items into inventory.
* **LegitScaffold**: Sneak-places blocks over voids by directly polling the world memory for Air blocks.
* **Clutch**: Automated block placement to prevent void falls.
* **Teams & Friends**: Whitelisting systems.

## Building

```bash
# Build the client and launcher
./gradlew build -x test

# Compile the executable
./gradlew :launcher:createExe -x test
```

The compiled launcher will be output to `launcher/build/launch4j/OnionMCC.exe`.

## Usage

1. Launch Minecraft (1.8.9, supports Forge/OptiFine).
2. Run `OnionMCC.exe`.
3. The launcher will automatically detect the Minecraft process and inject the agent.
4. Manage modules, configure settings, and bind keys directly from the external UI.

## Project Structure

* `agent/`: Bootstrap loader for JVM injection.
* `client/`: Core payload containing modules, events, reflection accessors, and the IPC server.
* `launcher/`: External JavaFX UI, auto-injector, and IPC client.
* `mappings/`: Version-specific JSON dictionaries for class and field resolution.

## Disclaimer

This software is provided for educational and research purposes. Use responsibly.
