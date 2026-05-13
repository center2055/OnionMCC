package com.onionmcc.client.mapping;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages version-specific obfuscation mappings. Loads JSON mapping files
 * and provides ClassMapping objects for reflecting into Minecraft internals.
 */
public class MappingManager {

    private final Map<String, ClassMapping> classMappings = new HashMap<>(); // deobf name -> mapping
    private String detectedVersion = "unknown";

    /**
     * Detect the Minecraft version and load appropriate mappings.
     */
    public void init() {
        detectedVersion = detectMinecraftVersion();
        System.out.println("[OnionMCC] Detected Minecraft version: " + detectedVersion);
        com.onionmcc.client.OnionMCC.getInstance().logToFile("Detected Minecraft version: " + detectedVersion);

        loadMappings(detectedVersion);
    }

    /**
     * Attempt to detect the running Minecraft version by checking for known
     * classes/fields.
     */
    private String detectMinecraftVersion() {
        // First check via loaded classes to avoid triggering classloading/security exceptions
        try {
            java.lang.instrument.Instrumentation inst = com.onionmcc.client.OnionMCC.getInstance().getInstrumentation();
            if (inst != null) {
                for (Class<?> c : inst.getAllLoadedClasses()) {
                    if (c.getName().equals("net.minecraft.SharedConstants")) return "1.20.4";
                    if (c.getName().equals("net.minecraft.client.Minecraft")) return "1.8.9";
                    if (c.getName().equals("ave")) return "1.8.9";
                }
            }
        } catch (Throwable ignored) {}

        // Fallback to Class.forName
        try {
            Class.forName("net.minecraft.SharedConstants");
            return "1.20.4";
        } catch (Throwable ignored) {}

        try {
            Class.forName("net.minecraft.client.Minecraft");
            return "1.8.9";
        } catch (Throwable ignored) {}

        try {
            Class.forName("ave");
            return "1.8.9";
        } catch (Throwable ignored) {}

        return "1.8.9"; // Default fallback
    }

    /**
     * Load mappings from a JSON file for the given version.
     */
    private void loadMappings(String version) {
        String resourcePath = "/mappings/" + version + ".json";

        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("[OnionMCC] No mapping file found for version " + version + ", using defaults");
                com.onionmcc.client.OnionMCC.getInstance().logToFile("No mapping file found for version " + version + ", using defaults");
                loadDefaultMappings();
                return;
            }

            String json;
            try (java.util.Scanner scanner = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A")) {
                json = scanner.hasNext() ? scanner.next() : "";
            }
            parseMappings(json);
            
            if (classMappings.isEmpty()) {
                System.out.println("[OnionMCC] JSON mapping was empty. Using defaults.");
                com.onionmcc.client.OnionMCC.getInstance().logToFile("JSON mapping was empty. Using defaults.");
                loadDefaultMappings();
            } else {
                System.out.println("[OnionMCC] Loaded " + classMappings.size() + " class mappings for " + version);
                com.onionmcc.client.OnionMCC.getInstance().logToFile("Loaded " + classMappings.size() + " class mappings for " + version);
            }

        } catch (Exception e) {
            System.err.println("[OnionMCC] Failed to load mappings: " + e.getMessage());
            com.onionmcc.client.OnionMCC.getInstance().logToFile("Failed to load mappings: " + e.getMessage());
            loadDefaultMappings();
        }
    }

    private void parseMappings(String json) {
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        Gson gson = new Gson();
        JsonObject root = gson.fromJson(json, JsonObject.class);
        if (root == null) {
            return;
        }
        JsonArray classes = root.getAsJsonArray("classes");

        if (classes == null)
            return;

        for (JsonElement elem : classes) {
            JsonObject classObj = elem.getAsJsonObject();
            String obfName = classObj.get("obf").getAsString();
            String deobfName = classObj.get("deobf").getAsString();

            ClassMapping mapping = new ClassMapping(obfName, deobfName);

            // Parse field mappings
            if (classObj.has("fields")) {
                JsonObject fields = classObj.getAsJsonObject("fields");
                for (Map.Entry<String, JsonElement> entry : fields.entrySet()) {
                    mapping.addFieldMapping(entry.getKey(), entry.getValue().getAsString());
                }
            }

            // Parse method mappings
            if (classObj.has("methods")) {
                JsonObject methods = classObj.getAsJsonObject("methods");
                for (Map.Entry<String, JsonElement> entry : methods.entrySet()) {
                    mapping.addMethodMapping(entry.getKey(), entry.getValue().getAsString());
                }
            }

            classMappings.put(deobfName, mapping);
        }
    }

    /**
     * Fallback mappings for 1.8.9 when no file is available.
     */
    private void loadDefaultMappings() {
        // 1.8.9 MCP mappings (commonly used names) + SRG fallbacks for Forge/Optifine
        ClassMapping minecraft = new ClassMapping("ave", "net.minecraft.client.Minecraft");
        minecraft.addFieldMapping("thePlayer", "h,field_71439_g");
        minecraft.addFieldMapping("theWorld", "f,field_71441_e");
        minecraft.addFieldMapping("playerController", "c,field_71442_b");
        minecraft.addFieldMapping("gameSettings", "t,field_71474_y");
        minecraft.addFieldMapping("timer", "Y,field_71428_T");
        minecraft.addFieldMapping("leftClickCounter", "W,field_71429_W");
        minecraft.addFieldMapping("objectMouseOver", "s,field_71476_x");
        minecraft.addFieldMapping("currentScreen", "m,field_71462_r");
        minecraft.addMethodMapping("clickMouse", "aw,func_147116_af");
        minecraft.addMethodMapping("rightClickMouse", "ax,func_147121_ag");
        classMappings.put("net.minecraft.client.Minecraft", minecraft);

        ClassMapping entity = new ClassMapping("pk", "net.minecraft.entity.Entity");
        entity.addFieldMapping("posX", "s,field_70165_t");
        entity.addFieldMapping("posY", "t,field_70163_u");
        entity.addFieldMapping("posZ", "u,field_70161_v");
        entity.addFieldMapping("motionX", "v,field_70159_w");
        entity.addFieldMapping("motionY", "w,field_70181_x");
        entity.addFieldMapping("motionZ", "x,field_70179_y");
        entity.addFieldMapping("rotationYaw", "y,field_70177_z");
        entity.addFieldMapping("rotationPitch", "z,field_70125_A");
        entity.addFieldMapping("onGround", "C,field_70122_E");
        entity.addFieldMapping("isDead", "I,field_70128_L");
        classMappings.put("net.minecraft.entity.Entity", entity);

        ClassMapping entityLiving = new ClassMapping("pr", "net.minecraft.entity.EntityLivingBase");
        entityLiving.addFieldMapping("health", "bn,field_70725_aQ");
        entityLiving.addMethodMapping("getMaxHealth", "by,func_110138_aP");
        entityLiving.addMethodMapping("swingItem", "bw,func_71038_i");
        entityLiving.addFieldMapping("hurtTime", "at,field_70737_aN");
        classMappings.put("net.minecraft.entity.EntityLivingBase", entityLiving);

        ClassMapping entityPlayer = new ClassMapping("wn", "net.minecraft.entity.player.EntityPlayer");
        entityPlayer.addFieldMapping("inventory", "bi,field_71071_by");
        entityPlayer.addFieldMapping("capabilities", "bz,field_71075_bZ");
        entityPlayer.addFieldMapping("openContainer", "bk,field_71070_bA");
        entityPlayer.addFieldMapping("inventoryContainer", "bj,field_71069_bz");
        classMappings.put("net.minecraft.entity.player.EntityPlayer", entityPlayer);

        ClassMapping playerController = new ClassMapping("bda", "net.minecraft.client.multiplayer.PlayerControllerMP");
        playerController.addMethodMapping("attackEntity", "a,func_78764_a");
        playerController.addFieldMapping("blockReachDistance", "d,field_78781_c");
        classMappings.put("net.minecraft.client.multiplayer.PlayerControllerMP", playerController);

        ClassMapping gameSettings = new ClassMapping("avh", "net.minecraft.client.settings.GameSettings");
        gameSettings.addFieldMapping("gammaSetting", "aJ,field_74333_Y");
        gameSettings.addFieldMapping("fovSetting", "aI,field_74334_X");
        gameSettings.addFieldMapping("keyBindSprint", "ai,field_151444_V");
        gameSettings.addFieldMapping("keyBindForward", "Y,field_74351_w");
        gameSettings.addFieldMapping("keyBindBack", "aa,field_74368_y");
        classMappings.put("net.minecraft.client.settings.GameSettings", gameSettings);

        ClassMapping timer = new ClassMapping("avl", "net.minecraft.util.Timer");
        timer.addFieldMapping("ticksPerSecond", "a,field_74282_a");
        timer.addFieldMapping("elapsedTicks", "b,field_74280_b");
        timer.addFieldMapping("renderPartialTicks", "c,field_74281_c");
        timer.addFieldMapping("timerSpeed", "d,field_74278_d");
        timer.addFieldMapping("elapsedPartialTicks", "e,field_74279_e");
        classMappings.put("net.minecraft.util.Timer", timer);

        ClassMapping networkManager = new ClassMapping("ej", "net.minecraft.network.NetworkManager");
        networkManager.addMethodMapping("sendPacket", "a,func_179290_a");
        classMappings.put("net.minecraft.network.NetworkManager", networkManager);

        ClassMapping blockPos = new ClassMapping("cj", "net.minecraft.util.BlockPos");
        classMappings.put("net.minecraft.util.BlockPos", blockPos);

        ClassMapping container = new ClassMapping("xi", "net.minecraft.inventory.Container");
        classMappings.put("net.minecraft.inventory.Container", container);

        System.out.println("[OnionMCC] Loaded " + classMappings.size() + " default mappings for 1.8.9");
    }

    public ClassMapping getMapping(String deobfName) {
        return classMappings.get(deobfName);
    }

    public String getDetectedVersion() {
        return detectedVersion;
    }
}
