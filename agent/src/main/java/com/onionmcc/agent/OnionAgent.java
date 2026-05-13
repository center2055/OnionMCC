package com.onionmcc.agent;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

/**
 * OnionMCC Java Agent — entry point for both premain (static) and agentmain
 * (dynamic) attachment.
 * When injected into a running Minecraft JVM, this agent bootstraps the
 * OnionMCC client.
 */
public class OnionAgent {

    private static Instrumentation instrumentation;
    private static boolean initialized = false;
    private static final int DEFAULT_IPC_PORT = 47891;
    private static String logPath;

    /**
     * Called when the agent is loaded at JVM startup via -javaagent.
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
        initialize(agentArgs);
    }

    /**
     * Called when the agent is loaded at runtime via the Attach API.
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
        initialize(agentArgs);
    }

    private static ClassLoader clientClassLoader;
    private static Object clientInstance;

    private static synchronized void initialize(String agentArgs) {
        System.out.println("[OnionMCC] ========================================");
        System.out.println("[OnionMCC]   OnionMCC Ghost Client v1.0.0");
        System.out.println("[OnionMCC]   Initializing agent...");
        System.out.println("[OnionMCC] ========================================");
        parseLogPath(agentArgs);
        logToFile("Agent initialize called. args=" + agentArgs);

        if (initialized) {
            System.out.println("[OnionMCC] Agent already running. Shutting down old client instance for hot-reload...");
            logToFile("Shutting down old client for hot-reload.");
            if (clientInstance != null && clientClassLoader != null) {
                try {
                    Class<?> oldClientClass = clientClassLoader.loadClass("com.onionmcc.client.OnionMCC");
                    Method stopMethod = oldClientClass.getMethod("stop");
                    stopMethod.invoke(clientInstance);
                } catch (Exception e) {
                    logToFile("Error stopping old client: " + e.getMessage());
                }
            }
            clientInstance = null;
            clientClassLoader = null;
            System.gc(); // Suggest GC to clean up old classloader
        }
        initialized = true;

        try {
            String clientPath = parseClientPath(agentArgs);
            if (clientPath == null || clientPath.isEmpty()) {
                throw new Exception("Client path not provided in agent arguments.");
            }
            
            System.out.println("[OnionMCC] Loading client from: " + clientPath);
            URL jarUrl = new File(clientPath).toURI().toURL();
            
            // Create a child-first classloader to prioritize classes in the client JAR over any cached system classes
            clientClassLoader = new URLClassLoader(new URL[]{jarUrl}, null) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> c = findLoadedClass(name);
                        if (c == null) {
                            if (name.startsWith("com.onionmcc.client.")) {
                                try {
                                    c = findClass(name);
                                } catch (ClassNotFoundException e) {
                                    // Ignore, fallback to parent
                                }
                            }
                            if (c == null) {
                                c = ClassLoader.getSystemClassLoader().loadClass(name);
                            }
                        }
                        if (resolve) {
                            resolveClass(c);
                        }
                        return c;
                    }
                }
            };

            Class<?> clientClass = Class.forName("com.onionmcc.client.OnionMCC", true, clientClassLoader);
            clientInstance = clientClass.getMethod("getInstance").invoke(null);

            // Pass instrumentation reference
            Method setInstrumentation = clientClass.getMethod("setInstrumentation", Instrumentation.class);
            setInstrumentation.invoke(clientInstance, instrumentation);

            int ipcPort = parseIpcPort(agentArgs);
            try {
                Method setIpcPort = clientClass.getMethod("setIpcPort", int.class);
                setIpcPort.invoke(clientInstance, ipcPort);
                System.out.println("[OnionMCC] IPC port configured: " + ipcPort);
                logToFile("IPC port configured: " + ipcPort);
            } catch (NoSuchMethodException ignored) {
                System.out.println("[OnionMCC] Client does not expose setIpcPort, using default.");
                logToFile("Client does not expose setIpcPort, using default.");
            }

            // Start the client on a new thread to avoid blocking MC
            Thread clientThread = new Thread(() -> {
                try {
                    // Set context class loader for the thread so dependencies (like Gson) can be found if needed
                    Thread.currentThread().setContextClassLoader(clientClassLoader);
                    Method startMethod = clientClass.getMethod("start");
                    startMethod.invoke(clientInstance);
                    System.out.println("[OnionMCC] Client started successfully!");
                    logToFile("Client start invoked successfully.");
                } catch (Exception e) {
                    System.err.println("[OnionMCC] Failed to start client: " + e.getMessage());
                    e.printStackTrace();
                    logToFile("Failed to start client: " + stackTrace(e));
                }
            }, "OnionMCC-Main");
            clientThread.setDaemon(true);
            clientThread.start();

        } catch (Exception e) {
            System.err.println("[OnionMCC] Failed to initialize agent: " + e.getMessage());
            e.printStackTrace();
            logToFile("Failed to initialize agent: " + stackTrace(e));
        }
    }

    private static int parseIpcPort(String agentArgs) {
        if (agentArgs == null || agentArgs.isEmpty()) {
            return DEFAULT_IPC_PORT;
        }
        try {
            for (String part : agentArgs.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 && "port".equalsIgnoreCase(kv[0].trim())) {
                    return Integer.parseInt(kv[1].trim());
                }
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_IPC_PORT;
    }

    private static void parseLogPath(String agentArgs) {
        if (agentArgs == null || agentArgs.isEmpty()) {
            return;
        }
        try {
            for (String part : agentArgs.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 && "log".equalsIgnoreCase(kv[0].trim())) {
                    logPath = kv[1].trim();
                    System.setProperty("onionmcc.log.file", logPath);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void logToFile(String message) {
        if (logPath == null || logPath.isEmpty()) {
            return;
        }
        try (FileWriter fw = new FileWriter(logPath, true)) {
            fw.write("[" + LocalDateTime.now() + "] [Agent] " + message + System.lineSeparator());
        } catch (Exception ignored) {
        }
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    private static boolean isClientRunning() {
        try {
            Class<?> clientClass = Class.forName("com.onionmcc.client.OnionMCC");
            Object instance = clientClass.getMethod("getInstance").invoke(null);
            Object running = clientClass.getMethod("isRunning").invoke(instance);
            return running instanceof Boolean && (Boolean) running;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String parseClientPath(String args) {
        if (args == null || args.isEmpty()) return null;
        String[] parts = args.split(",");
        for (String part : parts) {
            if (part.startsWith("client=")) {
                return part.substring(7);
            }
        }
        return null;
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
}
