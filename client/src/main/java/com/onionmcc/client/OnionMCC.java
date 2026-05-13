package com.onionmcc.client;

import com.onionmcc.client.config.ConfigManager;
import com.onionmcc.client.event.EventBus;
import com.onionmcc.client.event.events.TickEvent;
import com.onionmcc.client.ipc.IPCServer;
import com.onionmcc.client.mapping.MappingManager;
import com.onionmcc.client.minecraft.MinecraftAccessor;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.ModuleManager;
import com.onionmcc.client.render.OverlayRenderer;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;

/**
 * Main OnionMCC client singleton. Bootstrapped by the agent inside the
 * Minecraft JVM.
 */
public class OnionMCC {

    public static final String NAME = "OnionMCC";
    public static final String VERSION = "1.0.0";

    private static final OnionMCC INSTANCE = new OnionMCC();

    private Instrumentation instrumentation;
    private EventBus eventBus;
    private MappingManager mappingManager;
    private MinecraftAccessor minecraftAccessor;
    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private IPCServer ipcServer;
    private int ipcPort = 47891;

    private volatile boolean running = false;
    private Thread tickThread;

    private OnionMCC() {
    }

    public static OnionMCC getInstance() {
        return INSTANCE;
    }

    /**
     * Called by the agent after injection. Initializes all subsystems.
     */
    public void start() {
        if (running)
            return;
        running = true;

        System.out.println("[OnionMCC] Starting " + NAME + " v" + VERSION + "...");
        logToFile("Starting " + NAME + " v" + VERSION);

        // Initialize event bus
        eventBus = new EventBus();

        // Start IPC as early as possible so the launcher can connect even while the client
        // is still initializing.
        try {
            ipcServer = new IPCServer(ipcPort);
            ipcServer.start();
            logToFile("IPC server start requested on port " + ipcPort);
        } catch (Throwable t) {
            logToFile("Failed to start IPC server: " + stackTrace(t));
        }

        // Wait for Minecraft to be fully loaded before accessing it
        Thread initThread = new Thread(() -> {
            try {
                // Initialize mapping system
                mappingManager = new MappingManager();
                mappingManager.init();

                // Initialize Minecraft accessor
                minecraftAccessor = new MinecraftAccessor();

                System.out.println("[OnionMCC] Waiting for Minecraft to fully load...");
                if (!waitForMinecraft()) {
                    System.err.println("[OnionMCC] Minecraft classes were not detected in time. Continuing best-effort initialization.");
                    logToFile("Minecraft class wait timed out; continuing best-effort initialization.");
                }

                minecraftAccessor.init();
                logToFile("MinecraftAccessor initialized.");

                // Initialize modules
                moduleManager = new ModuleManager();
                moduleManager.init();
                logToFile("ModuleManager initialized with " + moduleManager.getModules().size() + " modules.");

                // Initialize config
                configManager = new ConfigManager();
                configManager.init();
                logToFile("ConfigManager initialized.");

                // Push a fresh snapshot once modules/config are live.
                if (ipcServer != null) {
                    ipcServer.broadcastFullState();
                }

                // Start the tick loop
                startTickLoop();
                logToFile("Tick loop started.");

                System.out.println("[OnionMCC] ========================================");
                System.out.println("[OnionMCC]   " + NAME + " v" + VERSION + " initialized!");
                System.out.println("[OnionMCC]   Modules: " + (moduleManager != null ? moduleManager.getModules().size() : 0));
                System.out.println("[OnionMCC]   Version: " + (mappingManager != null ? mappingManager.getDetectedVersion() : "?"));
                System.out.println("[OnionMCC]   PID: " + getProcessId());
                System.out.println("[OnionMCC]   IPC: Active on port " + ipcPort);
                System.out.println("[OnionMCC] ========================================");
            } catch (Throwable t) {
                running = false;
                System.err.println("[OnionMCC] Fatal initialization error: " + t.getMessage());
                t.printStackTrace();
                logToFile("Fatal initialization error: " + stackTrace(t));
            }
        }, "OnionMCC-Init");
        initThread.setDaemon(true);
        initThread.start();
    }

    /**
     * Wait until Minecraft's main class is accessible (game has loaded).
     */
    private boolean waitForMinecraft() {
        int attempts = 0;
        while (running && attempts < 300) { // Max ~60 seconds
            if (isClassAvailable("net.minecraft.client.Minecraft")) {
                System.out.println("[OnionMCC] Minecraft class found (deobfuscated).");
                return true;
            }

            if (isClassAvailable("ave")) {
                System.out.println("[OnionMCC] Minecraft class found (obfuscated 1.8.x).");
                return true;
            }

            if (isClassAvailable("net.minecraft.client.MinecraftClient")) {
                System.out.println("[OnionMCC] Minecraft class found (Fabric).");
                return true;
            }

            attempts++;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                return false;
            }
        }
        System.err.println("[OnionMCC] Warning: Could not find Minecraft class after " + attempts + " attempts.");
        return false;
    }

    private boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable ignored) {
        }

        if (instrumentation != null) {
            try {
                for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
                    if (loadedClass.getName().equals(className)) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private boolean[] previousKeyStates = new boolean[256];

    /**
     * Main tick loop — runs at ~20 TPS to match Minecraft's tick rate.
     */
    private void startTickLoop() {
        tickThread = new Thread(() -> {
            System.out.println("[OnionMCC] Tick loop started.");
            TickEvent tickEvent = new TickEvent();
            long lastLog = 0;

            while (running) {
                try {
                    boolean inGame = minecraftAccessor.isInGame();
                    long now = System.currentTimeMillis();
                    if (now - lastLog > 5000) {
                        lastLog = now;
                        logToFile("Tick debug: isInGame=" + inGame + 
                            ", mc=" + minecraftAccessor.getMinecraft() + 
                            ", player=" + minecraftAccessor.getPlayer() + 
                            ", world=" + minecraftAccessor.getWorld());
                    }

                    if (inGame && minecraftAccessor.getCurrentScreen() == null && minecraftAccessor.isMinecraftWindowActive()) {
                        for (int i = 1; i < 256; i++) {
                            boolean isDown = minecraftAccessor.isKeyDown(i);
                            if (isDown && !previousKeyStates[i]) {
                                moduleManager.onKeyPress(i);
                            }
                            previousKeyStates[i] = isDown;
                        }
                    }

                    if (inGame) {
                        eventBus.post(tickEvent);
                        moduleManager.onTick();
                    }
                    Thread.sleep(7); // ~144 TPS
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable e) {
                    System.err.println("[OnionMCC] Tick error: " + e.getMessage());
                }
            }
        }, "OnionMCC-Tick");
        tickThread.setDaemon(true);
        tickThread.start();
    }

    public void stop() {
        System.out.println("[OnionMCC] Shutting down...");
        running = false;

        if (configManager != null)
            configManager.save();
        if (ipcServer != null)
            ipcServer.stop();
        if (tickThread != null)
            tickThread.interrupt();
        OverlayRenderer.getInstance().shutdown();

        // Disable all modules
        if (moduleManager != null) {
            for (Module module : moduleManager.getModules()) {
                if (module.isEnabled())
                    module.setEnabled(false);
            }
        }

        System.out.println("[OnionMCC] Shutdown complete.");
    }

    /**
     * Notify IPC clients about a module state change.
     */
    public void notifyModuleStateChanged(Module module) {
        if (ipcServer != null) {
            ipcServer.broadcastModuleState(module);
        }
    }

    // ── Getters ──────────────────────────────────────────────────────

    public void setInstrumentation(Instrumentation inst) {
        this.instrumentation = inst;
    }

    public Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public void setIpcPort(int ipcPort) {
        if (ipcPort >= 1024 && ipcPort <= 65535) {
            this.ipcPort = ipcPort;
        }
    }

    public int getIpcPort() {
        return ipcPort;
    }

    public String getProcessId() {
        try {
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            int idx = runtimeName.indexOf('@');
            if (idx > 0) {
                return runtimeName.substring(0, idx);
            }
            return runtimeName;
        } catch (Exception ignored) {
            return "?";
        }
    }

    public void logToFile(String message) {
        String path = System.getProperty("onionmcc.log.file");
        if (path == null || path.isEmpty()) {
            return;
        }
        try (FileWriter fw = new FileWriter(path, true)) {
            fw.write("[" + LocalDateTime.now() + "] [Client] " + message + System.lineSeparator());
        } catch (Exception ignored) {
        }
    }

    public void logError(String context, Throwable t) {
        logToFile(context + ": " + stackTrace(t));
    }

    private String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public MappingManager getMappingManager() {
        return mappingManager;
    }

    public MinecraftAccessor getMinecraft() {
        return minecraftAccessor;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public boolean isRunning() {
        return running;
    }
}
