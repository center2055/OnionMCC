package com.onionmcc.client.config;

import com.google.gson.*;
import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.module.Module;
import com.onionmcc.client.module.Setting;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigManager {

    private File configFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public void init() {
        File dir = new File(System.getProperty("user.home"), ".onionmcc");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        configFile = new File(dir, "default.json");
        load();
    }

    public void save() {
        if (OnionMCC.getInstance().getModuleManager() == null) return;
        
        JsonObject root = new JsonObject();
        for (Module module : OnionMCC.getInstance().getModuleManager().getModules()) {
            JsonObject modObj = new JsonObject();
            modObj.addProperty("enabled", module.isEnabled());
            modObj.addProperty("keybind", module.getKeyBind());
            
            JsonObject settingsObj = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                if (setting.getType() == Setting.SettingType.NUMBER) {
                    settingsObj.addProperty(setting.getName(), (Number) setting.getValue());
                } else if (setting.getValue() instanceof Boolean) {
                    settingsObj.addProperty(setting.getName(), (Boolean) setting.getValue());
                } else {
                    settingsObj.addProperty(setting.getName(), setting.getValue().toString());
                }
            }
            modObj.add("settings", settingsObj);
            root.add(module.getName(), modObj);
        }

        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(root, writer);
            System.out.println("[OnionMCC] Saved config to " + configFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        if (!configFile.exists() || OnionMCC.getInstance().getModuleManager() == null) return;
        
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            
            for (Module module : OnionMCC.getInstance().getModuleManager().getModules()) {
                if (root.has(module.getName())) {
                    JsonObject modObj = root.getAsJsonObject(module.getName());
                    
                    if (modObj.has("enabled")) {
                        module.setEnabled(modObj.get("enabled").getAsBoolean());
                    }
                    if (modObj.has("keybind")) {
                        module.setKeyBind(modObj.get("keybind").getAsInt());
                    }
                    
                    if (modObj.has("settings")) {
                        JsonObject settingsObj = modObj.getAsJsonObject("settings");
                        for (Setting<?> setting : module.getSettings()) {
                            if (settingsObj.has(setting.getName())) {
                                JsonElement el = settingsObj.get(setting.getName());
                                // Only parse the primitive since Setting.fromJson supports NUMBER parsing from Object or Primitive
                                if (setting.getType() == Setting.SettingType.NUMBER) {
                                    setting.fromJson(el);
                                } else {
                                    setting.fromJson(el);
                                }
                            }
                        }
                    }
                }
            }
            System.out.println("[OnionMCC] Loaded config from " + configFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
