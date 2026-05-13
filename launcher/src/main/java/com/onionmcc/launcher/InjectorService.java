package com.onionmcc.launcher;

import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Handles finding the Minecraft JVM process and injecting the agent.
 */
public class InjectorService {

    private VirtualMachine vm;
    private boolean injected = false;
    private volatile String lastError = "";
    private volatile String lastTargetInfo = "";
    private volatile String lastAgentLogPath = "";
    private static final List<String> MINECRAFT_PROCESS_MARKERS = Arrays.asList(
            "minecraft",
            "net.minecraft",
            "launchwrapper",
            "gradlestart",
            "knot",
            "fabricmc",
            "fabric-loader",
            "cpw.mods",
            "modlauncher",
            "lunar",
            "badlion",
            "feather",
            "multimc",
            "prism",
            "optifine",
            "lwjgl");
    private static final List<String> NON_TARGET_PROCESS_MARKERS = Arrays.asList(
            "org.gradle",
            "gradle daemon",
            "com.onionmcc.launcher",
            "onionmcc.launcher.main",
            "idea_rt",
            "jetbrains",
            "eclipse");

    /**
     * Find running Minecraft Java processes.
     */
    public List<VirtualMachineDescriptor> findMinecraftProcesses() {
        return VirtualMachine.list().stream()
                .filter(this::looksLikeMinecraftProcess)
                .toList();
    }

    /**
     * Find any Java process (fallback).
     */
    public List<VirtualMachineDescriptor> findAllJavaProcesses() {
        return VirtualMachine.list();
    }

    public boolean looksLikeMinecraftProcess(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return false;
        }
        String lowered = displayName.toLowerCase();
        boolean looksLikeMinecraft = MINECRAFT_PROCESS_MARKERS.stream().anyMatch(lowered::contains);
        boolean blocked = NON_TARGET_PROCESS_MARKERS.stream().anyMatch(lowered::contains);
        if (lowered.contains("gradlestart")) {
            blocked = false;
        }
        return looksLikeMinecraft && !blocked;
    }

    public boolean looksLikeMinecraftProcess(VirtualMachineDescriptor descriptor) {
        return descriptor != null && looksLikeMinecraftProcess(descriptor.displayName());
    }

    /**
     * Inject the agent JAR into the target Minecraft process.
     */
    public boolean inject(String pid, String agentJarPath) {
        return inject(pid, agentJarPath, 47891);
    }

    public boolean inject(String pid, String agentJarPath, int ipcPort) {
        try {
            lastError = "";
            lastTargetInfo = "";
            System.out.println("[OnionMCC] Attaching to PID: " + pid);
            vm = VirtualMachine.attach(pid);
            populateTargetInfo();

            File agentJar = new File(agentJarPath);
            if (!agentJar.exists()) {
                lastError = "Agent JAR not found: " + agentJarPath;
                System.err.println("[OnionMCC] " + lastError);
                return false;
            }
            
            String clientJarPath = findClientJar().orElseThrow(() -> new RuntimeException("Client JAR not found"));

            File logFile = new File(System.getProperty("java.io.tmpdir"), "onionmcc-agent-" + pid + ".log");
            lastAgentLogPath = logFile.getAbsolutePath();
            String safeLogPath = lastAgentLogPath.replace('\\', '/');
            String safeClientPath = clientJarPath.replace('\\', '/');
            String agentArgs = "port=" + ipcPort + ",log=" + safeLogPath + ",client=" + safeClientPath;
            loadAgentWithFallback(agentJar, agentArgs);

            injected = true;
            System.out.println("[OnionMCC] Agent injected successfully!");
            return true;

        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "(no message)" : e.getMessage());
            if (lastTargetInfo != null && !lastTargetInfo.isBlank()) {
                lastError += " | " + lastTargetInfo;
            }
            System.err.println("[OnionMCC] Injection failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void populateTargetInfo() {
        try {
            Properties props = vm.getSystemProperties();
            String version = props.getProperty("java.version", "?");
            String vmName = props.getProperty("java.vm.name", "?");
            String vendor = props.getProperty("java.vendor", "?");
            String home = props.getProperty("java.home", "?");
            String arch = props.getProperty("os.arch", "?");
            lastTargetInfo = "Target JVM " + version + " | " + vmName + " | " + vendor + " | " + arch + " | " + home;
            System.out.println("[OnionMCC] " + lastTargetInfo);
        } catch (Exception e) {
            lastTargetInfo = "Target JVM info unavailable: " + e.getClass().getSimpleName();
        }
    }

    private void loadAgentWithFallback(File agentJar, String agentArgs) throws Exception {
        String canonicalPath = agentJar.getCanonicalPath();
        System.out.println("[OnionMCC] Loading agent: " + canonicalPath + " (args: " + agentArgs + ")");

        try {
            vm.loadAgent(canonicalPath, agentArgs);
        } catch (AgentLoadException firstError) {
            if (isLegacySuccessCode(firstError)) {
                System.out.println("[OnionMCC] Agent reported legacy success code 0; continuing.");
                return;
            }
            System.err.println("[OnionMCC] Agent load failed at primary path: " + firstError.getMessage());

            File tempCopy = createTempAgentCopy(agentJar);
            String tempPath = tempCopy.getCanonicalPath();
            System.out.println("[OnionMCC] Retrying agent load from temp path: " + tempPath);
            try {
                vm.loadAgent(tempPath, agentArgs);
            } catch (AgentLoadException retryError) {
                if (isLegacySuccessCode(retryError)) {
                    System.out.println("[OnionMCC] Agent retry returned legacy success code 0; continuing.");
                    return;
                }
                throw retryError;
            }
        }
    }

    private boolean isLegacySuccessCode(AgentLoadException exception) {
        if (exception == null) {
            return false;
        }
        String message = exception.getMessage();
        return message != null && "0".equals(message.trim());
    }

    private File createTempAgentCopy(File agentJar) throws IOException {
        File tempJar = File.createTempFile("onionmcc-agent-", ".jar");
        tempJar.deleteOnExit();
        Files.copy(agentJar.toPath(), tempJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return tempJar;
    }

    /**
     * Detach from the target JVM.
     */
    public void detach() {
        if (vm != null) {
            try {
                vm.detach();
                injected = false;
                System.out.println("[OnionMCC] Detached from target JVM.");
            } catch (Exception e) {
                System.err.println("[OnionMCC] Error detaching: " + e.getMessage());
            }
        }
    }

    public boolean isInjected() {
        return injected;
    }

    public String getLastError() {
        return lastError == null ? "" : lastError;
    }

    public String getLastTargetInfo() {
        return lastTargetInfo == null ? "" : lastTargetInfo;
    }

    public String getLastAgentLogPath() {
        return lastAgentLogPath == null ? "" : lastAgentLogPath;
    }

    public static int computeIpcPort(String pid) {
        try {
            int value = Integer.parseInt(pid.trim());
            int base = 47000 + Math.floorMod(value, 1000);
            for (int port = base; port < Math.min(base + 100, 65535); port++) {
                if (isPortAvailable(port)) {
                    return port;
                }
            }
            if (isPortAvailable(47891)) {
                return 47891;
            }
            for (int port = 48000; port < 49000; port++) {
                if (isPortAvailable(port)) {
                    return port;
                }
            }
        } catch (Exception ignored) {
        }
        return 47891;
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Find the agent JAR by looking in common locations.
     */
    public Optional<String> findAgentJar() {
        String baseName = "onionmcc-agent-1.0.0-SNAPSHOT.jar";
        String[] searchPaths = {
                "agent/build/libs/" + baseName,
                "../agent/build/libs/" + baseName,
                "../../agent/build/libs/" + baseName,
                "../../../agent/build/libs/" + baseName,
                "../../../../agent/build/libs/" + baseName,
                baseName,
                "onionmcc-agent.jar",
                "build/libs/onionmcc-agent.jar"
        };

        for (String path : searchPaths) {
            File f = new File(path);
            if (f.exists())
                return Optional.of(f.getAbsolutePath());
        }

        return Optional.empty();
    }

    public Optional<String> findClientJar() {
        String baseName = "onionmcc-client-1.0.0-SNAPSHOT.jar";
        String[] searchPaths = {
                "client/build/libs/" + baseName,
                "../client/build/libs/" + baseName,
                "../../client/build/libs/" + baseName,
                "../../../client/build/libs/" + baseName,
                "../../../../client/build/libs/" + baseName,
                baseName,
                "onionmcc-client.jar",
                "build/libs/onionmcc-client.jar"
        };

        for (String path : searchPaths) {
            File f = new File(path);
            if (f.exists())
                return Optional.of(f.getAbsolutePath());
        }

        return Optional.empty();
    }
}
