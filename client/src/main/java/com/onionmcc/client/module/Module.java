package com.onionmcc.client.module;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.event.EventHandler;
import com.onionmcc.client.event.events.TickEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for all modules. Extend this to create new client
 * features.
 */
public abstract class Module {

    private final String name;
    private final String description;
    private final ModuleCategory category;
    private int keyBind;
    private boolean enabled = false;
    protected final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description, ModuleCategory category, int keyBind) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keyBind = keyBind;
    }

    // ── Lifecycle ────────────────────────────────────────────────────

    /**
     * Called when the module is enabled.
     */
    protected abstract void onEnable();

    /**
     * Called when the module is disabled.
     */
    protected abstract void onDisable();

    /**
     * Called every game tick while the module is enabled.
     */
    public abstract void onTick();

    // ── State Management ─────────────────────────────────────────────

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled)
            return;
        this.enabled = enabled;

        if (enabled) {
            OnionMCC.getInstance().getEventBus().register(this);
            onEnable();
        } else {
            onDisable();
            OnionMCC.getInstance().getEventBus().unregister(this);
        }

        // Notify IPC about state change
        OnionMCC.getInstance().notifyModuleStateChanged(this);
    }

    // ── Settings Helpers ─────────────────────────────────────────────

    protected <T> Setting<T> addSetting(Setting<T> setting) {
        settings.add(setting);
        return setting;
    }

    // ── Getters ──────────────────────────────────────────────────────

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ModuleCategory getCategory() {
        return category;
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    // ── Serialization ────────────────────────────────────────────────

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("enabled", enabled);
        obj.addProperty("keyBind", keyBind);

        JsonObject settingsObj = new JsonObject();
        for (Setting<?> setting : settings) {
            settingsObj.add(setting.getName(), setting.toJson());
        }
        obj.add("settings", settingsObj);

        return obj;
    }

    public void fromJson(JsonObject obj) {
        if (obj.has("enabled") && obj.get("enabled").getAsBoolean()) {
            setEnabled(true);
        }
        if (obj.has("keyBind")) {
            keyBind = obj.get("keyBind").getAsInt();
        }
        if (obj.has("settings")) {
            JsonObject settingsObj = obj.getAsJsonObject("settings");
            for (Setting<?> setting : settings) {
                if (settingsObj.has(setting.getName())) {
                    setting.fromJson(settingsObj.get(setting.getName()));
                }
            }
        }
    }

    @Override
    public String toString() {
        return name + " [" + (enabled ? "ON" : "OFF") + "]";
    }
}
