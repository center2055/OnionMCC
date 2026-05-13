package com.onionmcc.client.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Generic module setting with type-safe value management.
 */
public class Setting<T> {

    private final String name;
    private final String description;
    private T value;
    private final T defaultValue;
    private final SettingType type;

    // For number settings
    private double min;
    private double max;
    private double step;

    // For mode settings
    private List<String> modes;

    // Change listener
    private Consumer<T> onChange;

    public enum SettingType {
        BOOLEAN, NUMBER, MODE, TEXT
    }

    private Setting(String name, String description, T value, SettingType type) {
        this.name = name;
        this.description = description;
        this.value = value;
        this.defaultValue = value;
        this.type = type;
    }

    // ── Factory Methods ──────────────────────────────────────────────

    public static Setting<Boolean> ofBoolean(String name, String description, boolean defaultValue) {
        return new Setting<>(name, description, defaultValue, SettingType.BOOLEAN);
    }

    public static Setting<Double> ofNumber(String name, String description, double defaultValue,
            double min, double max, double step) {
        Setting<Double> setting = new Setting<>(name, description, defaultValue, SettingType.NUMBER);
        setting.min = min;
        setting.max = max;
        setting.step = step;
        return setting;
    }

    public static Setting<String> ofMode(String name, String description, String defaultValue, String... modes) {
        Setting<String> setting = new Setting<>(name, description, defaultValue, SettingType.MODE);
        setting.modes = Arrays.asList(modes);
        return setting;
    }

    public static Setting<String> ofText(String name, String description, String defaultValue) {
        return new Setting<>(name, description, defaultValue, SettingType.TEXT);
    }

    // ── Getters & Setters ────────────────────────────────────────────

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public T getValue() {
        return value;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public SettingType getType() {
        return type;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    public List<String> getModes() {
        return modes;
    }

    public void setValue(T value) {
        this.value = value;
        if (onChange != null)
            onChange.accept(value);
    }

    public Setting<T> onChange(Consumer<T> listener) {
        this.onChange = listener;
        return this;
    }

    // ── Serialization ────────────────────────────────────────────────

    public JsonElement toJson() {
        if (type == SettingType.NUMBER) {
            JsonObject obj = new JsonObject();
            obj.addProperty("value", (Number) value);
            obj.addProperty("min", min);
            obj.addProperty("max", max);
            obj.addProperty("step", step);
            return obj;
        }
        if (type == SettingType.MODE) {
            JsonObject obj = new JsonObject();
            obj.addProperty("value", (String) value);
            JsonArray arr = new JsonArray();
            for (String m : modes) {
                arr.add(new JsonPrimitive(m));
            }
            obj.add("modes", arr);
            return obj;
        }
        if (value instanceof Boolean)
            return new JsonPrimitive((Boolean) value);
        if (value instanceof Number)
            return new JsonPrimitive((Number) value);
        if (value instanceof String)
            return new JsonPrimitive((String) value);
        return new JsonPrimitive(value.toString());
    }

    @SuppressWarnings("unchecked")
    public void fromJson(JsonElement element) {
        try {
            switch (type) {
                case BOOLEAN:
                    setValue((T) Boolean.valueOf(element.getAsBoolean()));
                    break;
                case NUMBER:
                    if (element.isJsonObject()) {
                        setValue((T) Double.valueOf(element.getAsJsonObject().get("value").getAsDouble()));
                    } else {
                        setValue((T) Double.valueOf(element.getAsDouble()));
                    }
                    break;
                case MODE:
                    if (element.isJsonObject() && element.getAsJsonObject().has("value")) {
                        setValue((T) element.getAsJsonObject().get("value").getAsString());
                    } else {
                        setValue((T) element.getAsString());
                    }
                    break;
                case TEXT:
                    setValue((T) element.getAsString());
                    break;
            }
        } catch (Exception e) {
            System.err.println("[OnionMCC] Failed to deserialize setting " + name + ": " + e.getMessage());
        }
    }
}
