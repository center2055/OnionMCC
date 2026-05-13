package com.onionmcc.client.ipc;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.Setting;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TCP-based IPC server running inside the Minecraft process.
 * Allows the external launcher GUI to communicate with the injected client.
 *
 * Protocol: JSON messages, one per line, terminated by newline.
 */
public class IPCServer {

    private static final Gson GSON = new Gson();
    private final int port;

    private ServerSocket serverSocket;
    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;

    public IPCServer(int port) {
        this.port = port;
    }

    public void start() {
        running = true;
        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("[OnionMCC] IPC Server started on port " + port);

                while (running) {
                    Socket socket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(socket);
                    clients.add(handler);
                    new Thread(handler, "OnionMCC-IPC-Client").start();
                    System.out.println("[OnionMCC] IPC client connected: " + socket.getRemoteSocketAddress());

                    // Send full state to new client
                    sendFullState(handler);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[OnionMCC] IPC Server error: " + e.getMessage());
                }
            }
        }, "OnionMCC-IPC-Server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null)
                serverSocket.close();
            for (ClientHandler client : clients)
                client.close();
        } catch (IOException e) {
            System.err.println("[OnionMCC] Error stopping IPC server: " + e.getMessage());
        }
    }

    /**
     * Broadcast a module state change to all connected clients.
     */
    public void broadcastModuleState(Module module) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "module_state");
        msg.addProperty("name", module.getName());
        msg.addProperty("enabled", module.isEnabled());
        msg.add("settings", module.toJson().getAsJsonObject("settings"));
        broadcast(msg);
    }

    /**
     * Broadcast the current full state to all connected launcher clients.
     */
    public void broadcastFullState() {
        for (ClientHandler client : clients) {
            sendFullState(client);
        }
    }

    /**
     * Send the complete client state to a single client.
     */
    private void sendFullState(ClientHandler handler) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "full_state");
        OnionMCC client = OnionMCC.getInstance();
        String version = "?";
        if (client.getMappingManager() != null) {
            version = client.getMappingManager().getDetectedVersion();
        }
        msg.addProperty("version", version);
        msg.addProperty("pid", client.getProcessId());
        msg.addProperty("ipcPort", client.getIpcPort());
        msg.addProperty("ready", client.getModuleManager() != null);

        JsonObject modules = new JsonObject();
        if (client.getModuleManager() != null) {
            for (Module module : client.getModuleManager().getModules()) {
                JsonObject moduleObj = module.toJson();
                moduleObj.addProperty("category", module.getCategory().name());
                moduleObj.addProperty("description", module.getDescription());
                modules.add(module.getName(), moduleObj);
            }
        }
        msg.add("modules", modules);
        handler.send(msg);
    }

    private void broadcast(JsonObject message) {
        String json = GSON.toJson(message);
        for (ClientHandler client : clients) {
            client.send(json);
        }
    }

    /**
     * Handles a single connected IPC client (the launcher GUI).
     */
    private class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private volatile boolean connected = true;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String line;
                while (connected && (line = in.readLine()) != null) {
                    handleMessage(line);
                }
            } catch (IOException e) {
                if (connected) {
                    System.err.println("[OnionMCC] IPC client disconnected: " + e.getMessage());
                }
            } finally {
                close();
                clients.remove(this);
                System.out.println("[OnionMCC] IPC connection closed. Active clients: " + clients.size());
                if (clients.isEmpty()) {
                    System.out.println("[OnionMCC] No IPC clients remaining. Initiating self-destruct sequence to disable modules.");
                    OnionMCC.getInstance().stop();
                }
            }
        }

        private void handleMessage(String json) {
            try {
                OnionMCC.getInstance().logToFile("IPC received: " + json);
                JsonObject msg = GSON.fromJson(json, JsonObject.class);
                String type = msg.get("type").getAsString();

                switch (type) {
                    case "toggle_module":
                        String name = msg.get("name").getAsString();
                        if (OnionMCC.getInstance().getModuleManager() == null)
                            break;
                        Module module = OnionMCC.getInstance().getModuleManager().getModule(name);
                        OnionMCC.getInstance().logToFile("toggle_module: '" + name + "' -> resolved: " + (module != null));
                        if (module != null)
                            module.toggle();
                        break;
                    case "set_enabled":
                        String nameObj = msg.get("name").getAsString();
                        boolean enabled = msg.get("enabled").getAsBoolean();
                        if (OnionMCC.getInstance().getModuleManager() == null)
                            break;
                        Module modEnabled = OnionMCC.getInstance().getModuleManager().getModule(nameObj);
                        OnionMCC.getInstance().logToFile("set_enabled: '" + nameObj + "' -> resolved: " + (modEnabled != null));
                        if (modEnabled != null)
                            modEnabled.setEnabled(enabled);
                        break;
                    case "update_bind":
                        String bindName = msg.get("name").getAsString();
                        int keyCode = msg.get("keyBind").getAsInt();
                        if (OnionMCC.getInstance().getModuleManager() == null)
                            break;
                        Module modBind = OnionMCC.getInstance().getModuleManager().getModule(bindName);
                        if (modBind != null) {
                            modBind.setKeyBind(keyCode);
                        }
                        break;
                    case "update_setting":
                        String moduleName = msg.get("module").getAsString();
                        String settingName = msg.get("setting").getAsString();
                        if (OnionMCC.getInstance().getModuleManager() == null)
                            break;
                        Module modSetting = OnionMCC.getInstance().getModuleManager().getModule(moduleName);
                        if (modSetting != null) {
                            for (Setting<?> setting : modSetting.getSettings()) {
                                if (setting.getName().equals(settingName)) {
                                    setting.fromJson(msg.get("value"));
                                    break;
                                }
                            }
                        }
                        break;
                    case "save_config":
                        if (OnionMCC.getInstance().getConfigManager() != null) {
                            OnionMCC.getInstance().getConfigManager().save();
                        }
                        break;
                    case "load_config":
                        if (OnionMCC.getInstance().getConfigManager() != null) {
                            OnionMCC.getInstance().getConfigManager().load();
                        }
                        break;
                    case "request_state":
                        sendFullState(this);
                        break;
                }
            } catch (Exception e) {
                System.err.println("[OnionMCC] Error handling IPC message: " + e.getMessage());
            }
        }

        void send(JsonObject message) {
            send(GSON.toJson(message));
        }

        void send(String json) {
            if (out != null && connected) {
                out.println(json);
            }
        }

        void close() {
            connected = false;
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
