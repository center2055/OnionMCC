package com.onionmcc.launcher.ipc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * IPC client for the launcher — connects to the injected client's IPC server.
 */
public class IPCClient {

    private static final int DEFAULT_PORT = 47891;
    private static final Gson GSON = new Gson();

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean connected = false;
    private volatile int port = DEFAULT_PORT;
    private Thread readThread;
    
    private Runnable onDisconnect;
    private final CopyOnWriteArrayList<Consumer<JsonObject>> messageListeners = new CopyOnWriteArrayList<>();

    public void setOnDisconnect(Runnable onDisconnect) {
        this.onDisconnect = onDisconnect;
    }

    public boolean connect() {
        return connect(DEFAULT_PORT);
    }

    public boolean connect(int port) {
        try {
            this.port = port;
            socket = new Socket("127.0.0.1", port);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connected = true;

            // Start reading messages
            readThread = new Thread(() -> {
                try {
                    String line;
                    while (connected && (line = in.readLine()) != null) {
                        JsonObject msg = GSON.fromJson(line, JsonObject.class);
                        for (Consumer<JsonObject> listener : messageListeners) {
                            listener.accept(msg);
                        }
                    }
                } catch (IOException e) {
                    if (connected) {
                        System.err.println("[OnionMCC Launcher] IPC connection lost: " + e.getMessage());
                        connected = false;
                        if (onDisconnect != null) onDisconnect.run();
                    }
                }
            }, "OnionMCC-IPC-Read");
            readThread.setDaemon(true);
            readThread.start();

            System.out.println("[OnionMCC Launcher] Connected to client IPC on port " + port);
            return true;
        } catch (IOException e) {
            System.err.println("[OnionMCC Launcher] Failed to connect to IPC port " + port + ": " + e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        if (connected) {
            connected = false;
            if (onDisconnect != null) onDisconnect.run();
        }
        try {
            if (socket != null)
                socket.close();
        } catch (IOException ignored) {
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public void send(JsonObject message) {
        if (out != null && connected) {
            out.println(GSON.toJson(message));
        }
    }

    public void toggleModule(String name) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "toggle_module");
        msg.addProperty("name", name);
        send(msg);
    }

    public void setModuleEnabled(String name, boolean enabled) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "set_enabled");
        msg.addProperty("name", name);
        msg.addProperty("enabled", enabled);
        send(msg);
    }

    public void updateSetting(String moduleName, String settingName, Object value) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "update_setting");
        msg.addProperty("module", moduleName);
        msg.addProperty("setting", settingName);
        if (value instanceof Boolean)
            msg.addProperty("value", (Boolean) value);
        else if (value instanceof Number)
            msg.addProperty("value", (Number) value);
        else
            msg.addProperty("value", value.toString());
        send(msg);
    }

    public void requestState() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "request_state");
        send(msg);
    }

    public void saveConfig() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "save_config");
        send(msg);
    }

    public void addMessageListener(Consumer<JsonObject> listener) {
        messageListeners.add(listener);
    }
}
