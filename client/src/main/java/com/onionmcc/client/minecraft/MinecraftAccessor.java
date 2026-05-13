package com.onionmcc.client.minecraft;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.mapping.ClassMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.awt.Robot;
import java.awt.event.InputEvent;

/**
 * Reflection-based accessor for the Minecraft singleton and game state.
 * All access goes through the mapping system for version independence.
 */
public class MinecraftAccessor {

    private Object minecraftInstance;
    private ClassMapping mcMapping;
    private ClassMapping entityMapping;
    private ClassMapping entityLivingMapping;
    private ClassMapping entityPlayerMapping;
    private ClassMapping playerControllerMapping;
    private ClassMapping gameSettingsMapping;
    private Method displayIsActiveMethod;
    private Method mouseIsButtonDownMethod;
    private final java.util.Set<String> loggedFieldErrors = new java.util.HashSet<>();
    private Robot robot;

    public void init() {
        try {
            robot = new Robot();
        } catch (Exception e) {
            System.err.println("[OnionMCC] Failed to initialize AWT Robot: " + e.getMessage());
        }
        mcMapping = OnionMCC.getInstance().getMappingManager().getMapping("net.minecraft.client.Minecraft");
        entityMapping = OnionMCC.getInstance().getMappingManager().getMapping("net.minecraft.entity.Entity");
        entityLivingMapping = OnionMCC.getInstance().getMappingManager()
                .getMapping("net.minecraft.entity.EntityLivingBase");
        entityPlayerMapping = OnionMCC.getInstance().getMappingManager()
                .getMapping("net.minecraft.entity.player.EntityPlayer");
        playerControllerMapping = OnionMCC.getInstance().getMappingManager()
                .getMapping("net.minecraft.client.multiplayer.PlayerControllerMP");
        gameSettingsMapping = OnionMCC.getInstance().getMappingManager()
                .getMapping("net.minecraft.client.settings.GameSettings");
    }

    // ── Minecraft fields ─────────────────────────────────────────────

    public Object getMinecraft() {
        if (minecraftInstance == null) {
            try {
                Class<?> mcClass = mcMapping.resolveClass();
                // Try common singleton accessor methods
                for (String methodName : new String[] { "getMinecraft", "A", "func_71410_x" }) {
                    try {
                        Method getter = mcClass.getDeclaredMethod(methodName);
                        getter.setAccessible(true);
                        minecraftInstance = getter.invoke(null);
                        if (minecraftInstance != null)
                            break;
                    } catch (NoSuchMethodException ignored) {
                    }
                }

                // If method didn't work, try static field
                if (minecraftInstance == null) {
                    for (Field f : mcClass.getDeclaredFields()) {
                        if (f.getType() == mcClass && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                            f.setAccessible(true);
                            minecraftInstance = f.get(null);
                            if (minecraftInstance != null)
                                break;
                        }
                    }
                }
                
                // Fallback: look for it in all threads if we STILL don't have it
                if (minecraftInstance == null) {
                    try {
                        for (Thread t : Thread.getAllStackTraces().keySet()) {
                            if (t.getName().equals("Client thread")) {
                                // Sometimes the game instance is stored in a thread local or similar,
                                // but if we can't find the static instance, something is deeply wrong.
                                // Let's try iterating all static fields of ALL loaded classes (heavy fallback)
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                
                if (minecraftInstance != null) {
                    System.out.println("[OnionMCC] Minecraft instance lazily acquired: " + minecraftInstance.getClass().getName());
                    OnionMCC.getInstance().logToFile("Minecraft instance lazily acquired.");
                } else {
                    OnionMCC.getInstance().logToFile("CRITICAL: Could not resolve Minecraft instance!");
                }
            } catch (Exception e) {
                // Ignore, we will try again next time
            }
        }
        return minecraftInstance;
    }

    public Object getPlayer() {
        return getField(getMinecraft(), mcMapping, "thePlayer");
    }

    public Object getWorld() {
        return getField(getMinecraft(), mcMapping, "theWorld");
    }

    public Object getPlayerController() {
        return getField(getMinecraft(), mcMapping, "playerController");
    }

    public Object getGameSettings() {
        return getField(getMinecraft(), mcMapping, "gameSettings");
    }

    public Object getCurrentScreen() {
        return getField(getMinecraft(), mcMapping, "currentScreen");
    }

    public Object getObjectMouseOver() {
        return getField(getMinecraft(), mcMapping, "objectMouseOver");
    }

    public Object getObjectMouseOverEntity() {
        Object mop = getObjectMouseOver();
        if (mop == null) return null;
        try {
            for (Field f : mop.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(mop);
                if (val != null) {
                    if (isPlayerEntity(val) || isLivingEntity(val)) {
                        return val;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public boolean isLookingAtBlock() {
        Object mop = getObjectMouseOver();
        if (mop == null) return false;
        try {
            for (Field f : mop.getClass().getDeclaredFields()) {
                if (f.getType().isEnum()) {
                    f.setAccessible(true);
                    Object val = f.get(mop);
                    if (val != null && "BLOCK".equals(val.toString())) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean isInGame() {
        return getPlayer() != null && getWorld() != null;
    }

    public boolean isInGui() {
        return getCurrentScreen() != null;
    }

    public void setLeftClickCounter(int value) {
        setField(getMinecraft(), mcMapping, "leftClickCounter", value);
    }

    public int getLeftClickCounter() {
        Object val = getField(getMinecraft(), mcMapping, "leftClickCounter");
        return val instanceof Number ? ((Number) val).intValue() : 0;
    }

    // ── Entity fields ────────────────────────────────────────────────

    public Object getEntityBoundingBox(Object entity) {
        Object val = getField(entity, entityMapping, "boundingBox");
        if (val == null) val = getField(entity, entityMapping, "field_70159_w"); // Fallback
        return val;
    }

    public double getEntityPosX(Object entity) {
        Object val = getField(entity, entityMapping, "posX");
        return val instanceof Number ? ((Number) val).doubleValue() : 0;
    }

    public double getEntityPosY(Object entity) {
        Object val = getField(entity, entityMapping, "posY");
        return val instanceof Number ? ((Number) val).doubleValue() : 0;
    }

    public double getEntityPosZ(Object entity) {
        Object val = getField(entity, entityMapping, "posZ");
        return val instanceof Number ? ((Number) val).doubleValue() : 0;
    }

    public void setEntityMotionX(Object entity, double value) {
        setField(entity, entityMapping, "motionX", value);
    }

    public void setEntityMotionY(Object entity, double value) {
        setField(entity, entityMapping, "motionY", value);
    }

    public void setEntityMotionZ(Object entity, double value) {
        setField(entity, entityMapping, "motionZ", value);
    }

    public double getEntityMotionX(Object entity) {
        Object val = getField(entity, entityMapping, "motionX");
        return val instanceof Number ? ((Number) val).doubleValue() : 0;
    }

    public double getEntityMotionY(Object entity) {
        Object val = getField(entity, entityMapping, "motionY");
        return val instanceof Number ? ((Number) val).doubleValue() : 0;
    }

    public double getEntityMotionZ(Object entity) {
        Object val = getField(entity, entityMapping, "motionZ");
        return val instanceof Number ? ((Number) val).doubleValue() : 0;
    }

    public float getEntityYaw(Object entity) {
        Object val = getField(entity, entityMapping, "rotationYaw");
        return val instanceof Number ? ((Number) val).floatValue() : 0;
    }

    public float getEntityPitch(Object entity) {
        Object val = getField(entity, entityMapping, "rotationPitch");
        return val instanceof Number ? ((Number) val).floatValue() : 0;
    }

    public void setEntityYaw(Object entity, float yaw) {
        setField(entity, entityMapping, "rotationYaw", yaw);
    }

    public void setEntityPitch(Object entity, float pitch) {
        setField(entity, entityMapping, "rotationPitch", pitch);
    }

    public boolean isEntityOnGround(Object entity) {
        Object val = getField(entity, entityMapping, "onGround");
        return val instanceof Boolean && (Boolean) val;
    }

    public void setEntityOnGround(Object entity, boolean onGround) {
        setField(entity, entityMapping, "onGround", onGround);
    }

    public boolean isEntityDead(Object entity) {
        Object val = getField(entity, entityMapping, "isDead");
        return val instanceof Boolean && (Boolean) val;
    }

    public boolean isInvisible(Object entity) {
        if (entity == null) return false;
        try {
            for (Method method : entity.getClass().getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == boolean.class) {
                    String name = method.getName();
                    // "isInvisible" in MCP, "ax" / "ay" in obf
                    if (name.equals("isInvisible") || name.equals("ax") || name.equals("ay") || name.equals("func_82150_aj")) {
                        Object result = method.invoke(entity);
                        if (result instanceof Boolean && (Boolean) result) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    // ── EntityLivingBase fields ──────────────────────────────────────

    public float getEntityHealth(Object entity) {
        if (entity == null || entityLivingMapping == null)
            return 0.0f;
        try {
            Method method = entityLivingMapping.getMethod("getHealth");
            Object value = method.invoke(entity);
            return value instanceof Number ? ((Number) value).floatValue() : 20.0f;
        } catch (Exception ignored) {
            // Fallback for method search
            try {
                for (Method method : entity.getClass().getMethods()) {
                    if (method.getParameterCount() == 0 && method.getReturnType() == float.class) {
                        String name = method.getName();
                        if (name.equals("getHealth") || name.equals("bx") || name.equals("func_110143_aJ")) {
                            return ((Number) method.invoke(entity)).floatValue();
                        }
                    }
                }
            } catch (Throwable t) {}
            return 20.0f; // Return a default positive health if we can't determine it, so aura doesn't break
        }
    }

    public int getEntityHurtTime(Object entity) {
        Object val = getField(entity, entityLivingMapping, "hurtTime");
        return val instanceof Number ? ((Number) val).intValue() : 0;
    }

    public float getEntityMaxHealth(Object entity) {
        if (entity == null || entityLivingMapping == null)
            return 20.0f;
        try {
            Method method = entityLivingMapping.getMethod("getMaxHealth");
            Object value = method.invoke(entity);
            return value instanceof Number ? ((Number) value).floatValue() : 20.0f;
        } catch (Exception ignored) {
            return 20.0f;
        }
    }

    // ── GameSettings ─────────────────────────────────────────────────

    public void setKeyBindSneakPressed(boolean pressed) {
        Object gs = getGameSettings();
        if (gs == null) return;
        try {
            Field sneakField = null;
            for (Field f : gs.getClass().getDeclaredFields()) {
                if (f.getName().equals("keyBindSneak") || f.getName().equals("af") || f.getName().equals("field_74311_E")) {
                    sneakField = f;
                    break;
                }
            }
            if (sneakField != null) {
                sneakField.setAccessible(true);
                Object keyBinding = sneakField.get(gs);
                if (keyBinding != null) {
                    for (Field f : keyBinding.getClass().getDeclaredFields()) {
                        if (f.getType() == boolean.class) {
                            f.setAccessible(true);
                            f.setBoolean(keyBinding, pressed);
                            return;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public void setKeyBindJumpPressed(boolean pressed) {
        Object gs = getGameSettings();
        if (gs == null) return;
        try {
            Field jumpField = null;
            for (Field f : gs.getClass().getDeclaredFields()) {
                // keyBindJump in MCP, 'Y' in some obf, 'field_74314_A' in others
                if (f.getName().equals("keyBindJump") || f.getName().equals("Y") || f.getName().equals("field_74314_A")) {
                    jumpField = f;
                    break;
                }
            }
            if (jumpField != null) {
                jumpField.setAccessible(true);
                Object keyBinding = jumpField.get(gs);
                if (keyBinding != null) {
                    for (Field f : keyBinding.getClass().getDeclaredFields()) {
                        if (f.getType() == boolean.class) {
                            f.setAccessible(true);
                            f.setBoolean(keyBinding, pressed);
                            return;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public boolean isKeyBindSneakPressed() {
        Object gs = getGameSettings();
        if (gs == null) return false;
        try {
            for (Field f : gs.getClass().getDeclaredFields()) {
                if (f.getName().equals("keyBindSneak") || f.getName().equals("af") || f.getName().equals("field_74311_E")) {
                    f.setAccessible(true);
                    Object keyBinding = f.get(gs);
                    if (keyBinding != null) {
                        for (Field f2 : keyBinding.getClass().getDeclaredFields()) {
                            if (f2.getType() == boolean.class) {
                                f2.setAccessible(true);
                                return f2.getBoolean(keyBinding);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public Object getOpenContainer() {
        Object player = getPlayer();
        if (player == null) return null;
        
        Object val = getField(player, entityPlayerMapping, "openContainer");
        if (val != null) return val;
        
        // Fallback: The openContainer field is usually the second field of type Container (xi) in EntityPlayer
        // inventoryContainer is the first one.
        try {
            ClassMapping containerMapping = OnionMCC.getInstance().getMappingManager().getMapping("net.minecraft.inventory.Container");
            Class<?> containerClass = containerMapping != null ? containerMapping.resolveClass() : null;
            
            if (containerClass != null) {
                int count = 0;
                for (Field f : player.getClass().getFields()) {
                    if (containerClass.isAssignableFrom(f.getType())) {
                        count++;
                        if (count == 2) { // The second container field is openContainer
                            f.setAccessible(true);
                            return f.get(player);
                        }
                    }
                }
                // If we only found 1 (some obfuscations might reorder), return the last one found
                for (Field f : player.getClass().getFields()) {
                    if (containerClass.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object container = f.get(player);
                        // inventoryContainer id is usually 0, openContainer is > 0
                        if (getContainerWindowId(container) != 0) {
                            return container;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        
        return null;
    }

    public int getContainerWindowId(Object container) {
        if (container == null) return 0;
        try {
            for (Class<?> current = container.getClass(); current != null; current = current.getSuperclass()) {
                for (Field f : current.getDeclaredFields()) {
                    if (f.getType() == int.class) {
                        String name = f.getName();
                        if (name.equals("windowId") || name.equals("d") || name.equals("field_75152_c")) {
                            f.setAccessible(true);
                            return f.getInt(container);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    @SuppressWarnings("unchecked")
    public List<Object> getContainerSlots(Object container) {
        if (container == null) return Collections.emptyList();
        try {
            for (Field f : container.getClass().getFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    String name = f.getName();
                    if (name.equals("inventorySlots") || name.equals("c")) {
                        return (List<Object>) f.get(container);
                    }
                }
            }
        } catch (Exception ignored) {}
        return Collections.emptyList();
    }

    public Object getSlotStack(Object slot) {
        if (slot == null) return null;
        try {
            for (Method m : slot.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() != void.class && m.getReturnType() != boolean.class && m.getReturnType() != int.class) {
                    String name = m.getName();
                    if (name.equals("getStack") || name.equals("d") || name.equals("func_75211_c")) {
                        return m.invoke(slot);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public String getItemName(Object itemStack) {
        if (itemStack == null) return "";
        try {
            for (Method m : itemStack.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == String.class) {
                    String name = m.getName();
                    if (name.equals("getDisplayName") || name.equals("q")) {
                        return (String) m.invoke(itemStack);
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    public void setGamma(float gamma) {
        Object settings = getGameSettings();
        if (settings != null) {
            setField(settings, gameSettingsMapping, "gammaSetting", gamma);
        }
    }

    // ── Hardware Input ───────────────────────────────────────────────

    public float getMouseSensitivity() {
        Object settings = getGameSettings();
        if (settings == null || gameSettingsMapping == null) {
            return 0.5f;
        }
        try {
            for (Field f : settings.getClass().getFields()) {
                if (f.getType() == float.class) {
                    String name = f.getName();
                    if (name.equals("mouseSensitivity") || name.equals("a")) {
                        return f.getFloat(settings);
                    }
                }
            }
            return 0.5f;
        } catch (Exception ignored) {
            return 0.5f;
        }
    }

    public float getFovSetting() {
        Object settings = getGameSettings();
        if (settings == null || gameSettingsMapping == null) {
            return 70.0f;
        }
        try {
            Object value = gameSettingsMapping.getField("fovSetting").get(settings);
            return value instanceof Number ? ((Number) value).floatValue() : 70.0f;
        } catch (Exception ignored) {
            return 70.0f;
        }
    }

    public String getEntityName(Object entity) {
        if (entity == null || entityMapping == null) {
            return "";
        }
        try {
            Method method = entityMapping.getMethod("getName");
            Object value = method.invoke(entity);
            return value != null ? value.toString() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    public void setSneaking(Object entity, boolean sneaking) {
        if (entity == null || entityMapping == null)
            return;
        try {
            Method mapped = entityMapping.getMethod("setSneaking", boolean.class);
            mapped.invoke(entity, sneaking);
            return;
        } catch (Exception ignored) {
        }
        
        try {
            Method method = findBooleanSetter(entity.getClass(), "setSneaking", "e");
            if (method != null) {
                method.invoke(entity, sneaking);
            }
        } catch (Exception e) {
            if (!loggedFieldErrors.contains("setSneakingInvoke")) {
                System.err.println("[OnionMCC] Error setting sneaking: " + e.getMessage());
                loggedFieldErrors.add("setSneakingInvoke");
            }
        }
    }

    public void windowClick(int windowId, int slotId, int mouseButtonClicked, int mode) {
        try {
            Object playerController = getPlayerController();
            Object player = getPlayer();
            if (playerController != null && player != null) {
                Method windowClickMethod = null;
                for (Method m : playerController.getClass().getMethods()) {
                    if (m.getParameterCount() == 5 && m.getParameterTypes()[0] == int.class && m.getParameterTypes()[1] == int.class && m.getParameterTypes()[2] == int.class && m.getParameterTypes()[3] == int.class) {
                        String name = m.getName();
                        if (name.equals("windowClick") || name.equals("e") || name.equals("func_78753_a")) {
                            windowClickMethod = m;
                            break;
                        }
                    }
                }
                if (windowClickMethod != null) {
                    windowClickMethod.setAccessible(true);
                    windowClickMethod.invoke(playerController, windowId, slotId, mouseButtonClicked, mode, player);
                } else {
                    if (!loggedFieldErrors.contains("windowClickMethod")) {
                        System.err.println("[OnionMCC] Could not resolve windowClick method.");
                        loggedFieldErrors.add("windowClickMethod");
                    }
                }
            }
        } catch (Exception e) {
            if (!loggedFieldErrors.contains("windowClickInvoke")) {
                System.err.println("[OnionMCC] Error invoking windowClick: " + e.getMessage());
                loggedFieldErrors.add("windowClickInvoke");
            }
        }
    }

    public void closeScreen() {
        Object player = getPlayer();
        if (player == null) return;
        try {
            for (Method m : player.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == void.class) {
                    String name = m.getName();
                    if (name.equals("closeScreen") || name.equals("p") || name.equals("func_71053_j")) {
                        m.setAccessible(true);
                        m.invoke(player);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    // ── Input Simulation ───────────────────────────────────────────────

    public void setKeyBindState(String bindingName, String obf1, String obf2, boolean pressed) {
        Object gs = getGameSettings();
        if (gs == null) return;
        try {
            Field keyField = null;
            for (Field f : gs.getClass().getDeclaredFields()) {
                if (f.getName().equals(bindingName) || f.getName().equals(obf1) || f.getName().equals(obf2)) {
                    keyField = f;
                    break;
                }
            }
            if (keyField != null) {
                keyField.setAccessible(true);
                Object keyBinding = keyField.get(gs);
                if (keyBinding != null) {
                    int keyCode = 0;
                    for (Field f : keyBinding.getClass().getDeclaredFields()) {
                        if (f.getType() == int.class) {
                            f.setAccessible(true);
                            keyCode = f.getInt(keyBinding);
                            break;
                        }
                    }
                    for (Method m : keyBinding.getClass().getDeclaredMethods()) {
                        if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) 
                            && m.getParameterCount() == 2 
                            && m.getParameterTypes()[0] == int.class 
                            && m.getParameterTypes()[1] == boolean.class) {
                            m.setAccessible(true);
                            m.invoke(null, keyCode, pressed);
                            return;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public void setSprinting(Object entity, boolean sprinting) {
        if (entity == null || entityMapping == null)
            return;
        try {
            Method mapped = entityMapping.getMethod("setSprinting", boolean.class);
            mapped.invoke(entity, sprinting);
            return;
        } catch (Exception ignored) {
            // Ignore mapping lookup failure, fallback next
        }

        try {
            Method method = findBooleanSetter(entity.getClass(), "setSprinting", "d");
            if (method != null) {
                method.invoke(entity, sprinting);
            } else {
                if (!loggedFieldErrors.contains("setSprintingMethod")) {
                    System.err.println("[OnionMCC] Could not find setSprinting method.");
                    OnionMCC.getInstance().logToFile("Could not find setSprinting method for " + entity.getClass().getName());
                    loggedFieldErrors.add("setSprintingMethod");
                }
            }
        } catch (Exception e) {
            if (!loggedFieldErrors.contains("setSprintingInvoke")) {
                System.err.println("[OnionMCC] Error setting sprinting: " + e.getMessage());
                OnionMCC.getInstance().logError("setSprinting invoke failure", e);
                loggedFieldErrors.add("setSprintingInvoke");
            }
        }
    }

    public void swingItem(Object player) {
        if (player == null) return;
        try {
            if (entityLivingMapping != null) {
                Method method = entityLivingMapping.getMethod("swingItem");
                method.invoke(player);
                return;
            }
        } catch (Exception ignored) {}
        
        try {
            for (Class<?> current = player.getClass(); current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.getParameterCount() == 0 && method.getReturnType() == void.class) {
                        String name = method.getName();
                        if (name.equals("swingItem") || name.equals("bw") || name.equals("func_71038_i")) {
                            method.setAccessible(true);
                            method.invoke(player);
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (!loggedFieldErrors.contains("swingItemInvoke")) {
                System.err.println("[OnionMCC] Error swinging item: " + e.getMessage());
                loggedFieldErrors.add("swingItemInvoke");
            }
        }
    }

    public float getCooledAttackStrength(Object player) {
        if (player == null) return 1.0f;
        try {
            if (entityPlayerMapping != null) {
                Method method = entityPlayerMapping.getMethod("getCooledAttackStrength", float.class);
                if (method != null) {
                    Object value = method.invoke(player, 0.0f);
                    return value instanceof Number ? ((Number) value).floatValue() : 1.0f;
                }
            }
        } catch (Throwable ignored) {}
        
        // Fallback for modern intermediary / srg mappings
        try {
            for (Method method : player.getClass().getMethods()) {
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == float.class && method.getReturnType() == float.class) {
                    String name = method.getName();
                    if (name.equals("getCooledAttackStrength") || name.equals("func_184825_o") || name.equals("method_7261")) {
                        method.setAccessible(true);
                        return ((Number) method.invoke(player, 0.0f)).floatValue();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return 1.0f;
    }

    public boolean isMinecraftWindowActive() {
        try {
            if (displayIsActiveMethod == null) {
                Class<?> displayClass = findSystemClass("org.lwjgl.opengl.Display");
                if (displayClass == null)
                    return true;
                displayIsActiveMethod = displayClass.getMethod("isActive");
            }
            Object result = displayIsActiveMethod.invoke(null);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return true;
        }
    }

    private Class<?> findSystemClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            java.lang.instrument.Instrumentation inst = OnionMCC.getInstance().getInstrumentation();
            if (inst != null) {
                for (Class<?> c : inst.getAllLoadedClasses()) {
                    if (name.equals(c.getName()))
                        return c;
                }
            }
        }
        return null;
    }

    public boolean isKeyDown(int keyCode) {
        try {
            short state = com.sun.jna.platform.win32.User32.INSTANCE.GetAsyncKeyState(keyCode);
            return (state & 0x8000) != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean isMouseButtonDown(int button) {
        try {
            if (mouseIsButtonDownMethod == null) {
                Class<?> mouseClass = findSystemClass("org.lwjgl.input.Mouse");
                if (mouseClass == null)
                    return false;
                mouseIsButtonDownMethod = mouseClass.getMethod("isButtonDown", int.class);
            }
            Object result = mouseIsButtonDownMethod.invoke(null, button);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void simulateClick(boolean left) {
        try {
            if (left) {
                invokeMinecraftMethod("clickMouse", "aw");
            } else {
                invokeMinecraftMethod("rightClickMouse", "ax");
            }
        } catch (Exception e) {
            if (!loggedFieldErrors.contains("simulateClick")) {
                System.err.println("[OnionMCC] Error simulating click: " + e.getMessage());
                loggedFieldErrors.add("simulateClick");
            }
        }
    }

    public void attackEntity(Object target) {
        try {
            Object playerController = getPlayerController();
            Object player = getPlayer();
            if (playerController != null && player != null) {
                Method attackMethod = resolveAttackMethod(playerController.getClass(), player.getClass(), target.getClass());
                if (attackMethod == null) {
                    if (!loggedFieldErrors.contains("attackEntityMethod")) {
                        System.err.println("[OnionMCC] Could not resolve attackEntity method.");
                        OnionMCC.getInstance().logToFile("Could not resolve attackEntity method for "
                                + playerController.getClass().getName() + " -> player:" + player.getClass().getName() + " target:" + target.getClass().getName());
                        loggedFieldErrors.add("attackEntityMethod");
                    }
                    return;
                }
                attackMethod.invoke(playerController, player, target);
                
                // Notify WTap module
                com.onionmcc.client.module.Module wtap = OnionMCC.getInstance().getModuleManager().getModule("WTap");
                if (wtap != null && wtap.isEnabled()) {
                    ((com.onionmcc.client.module.modules.combat.WTap) wtap).onAttack();
                }
            }
        } catch (Exception e) {
            if (!loggedFieldErrors.contains("attackEntityInvoke")) {
                System.err.println("[OnionMCC] Error attacking entity: " + e.getMessage());
                OnionMCC.getInstance().logError("attackEntity failure", e);
                loggedFieldErrors.add("attackEntityInvoke");
            }
        }
    }

    public boolean isOnSameTeam(Object entity1, Object entity2) {
        if (entity1 == null || entity2 == null) return false;
        try {
            // Find method on Entity1 that takes Entity2 and returns boolean
            for (Method method : entity1.getClass().getMethods()) {
                if (method.getParameterCount() == 1 && method.getReturnType() == boolean.class) {
                    Class<?> paramType = method.getParameterTypes()[0];
                    if (paramType.isInstance(entity2)) {
                        String name = method.getName();
                        // "isOnSameTeam" in MCP, "c" in some obf, just test all boolean methods that take an entity
                        // This is slightly risky but usually isOnSameTeam is the only common boolean(Entity) method that returns true for teammates.
                        // We will filter by common names.
                        if (name.equals("isOnSameTeam") || name.equals("isTeammate") || name.equals("c") || name.startsWith("func_142014_c")) {
                            Object result = method.invoke(entity1, entity2);
                            if (result instanceof Boolean && (Boolean) result) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private Method resolveAttackMethod(Class<?> controllerClass, Class<?> playerClass, Class<?> targetClass) {
        try {
            if (playerControllerMapping != null && entityPlayerMapping != null && entityMapping != null) {
                Method mapped = playerControllerMapping.getMethod("attackEntity",
                        entityPlayerMapping.resolveClass(), entityMapping.resolveClass());
                mapped.setAccessible(true);
                return mapped;
            }
        } catch (Exception ignored) {
            // Fallback to loop search
        }

        // We need to look for a method taking exactly two parameters: (EntityPlayer, Entity)
        Class<?> entityPlayerBase = entityPlayerMapping != null ? getResolvedClassSafely(entityPlayerMapping) : playerClass;
        Class<?> entityBase = entityMapping != null ? getResolvedClassSafely(entityMapping) : targetClass;

        if (entityPlayerBase == null || entityBase == null) return null;

        for (Class<?> current = controllerClass; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() != 2) {
                    continue;
                }
                Class<?> first = method.getParameterTypes()[0];
                Class<?> second = method.getParameterTypes()[1];
                
                // Match exact parameter types, or ensure they are assignable
                if (first.isAssignableFrom(entityPlayerBase) && second.isAssignableFrom(entityBase)) {
                    String name = method.getName();
                    if ("attackEntity".equals(name) || "a".equals(name) || name.startsWith("func_")) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
        }

        return null;
    }
    
    private Class<?> getResolvedClassSafely(ClassMapping mapping) {
        try {
            return mapping.resolveClass();
        } catch (Exception e) {
            return null;
        }
    }

    // ── Distance ─────────────────────────────────────────────────────

    public double distanceTo(Object entity) {
        Object player = getPlayer();
        if (player == null || entity == null)
            return Double.MAX_VALUE;

        double dx = getEntityPosX(player) - getEntityPosX(entity);
        double dy = getEntityPosY(player) - getEntityPosY(entity);
        double dz = getEntityPosZ(player) - getEntityPosZ(entity);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Get all loaded entities in the world (player list or entity list).
     */
    @SuppressWarnings("unchecked")
    public List<Object> getWorldPlayers() {
        Object world = getWorld();
        if (world == null)
            return Collections.emptyList();

        try {
            // Try playerEntities field (1.8.x)
            for (Field f : world.getClass().getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    List<Object> list = (List<Object>) f.get(world);
                    if (list != null && !list.isEmpty()) {
                        // Check if first element is a player entity
                        Object first = list.get(0);
                        if (entityPlayerMapping != null) {
                            try {
                                Class<?> playerClass = entityPlayerMapping.resolveClass();
                                if (playerClass.isInstance(first)) {
                                    return list;
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[OnionMCC] Error getting world players: " + e.getMessage());
        }
        List<Object> entities = getLoadedEntities();
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> players = new ArrayList<>();
        for (Object entity : entities) {
            if (isPlayerEntity(entity)) {
                players.add(entity);
            }
        }
        return players;
    }

    // ── Utility ──────────────────────────────────────────────────────

    public void runOnMainThread(Runnable task) {
        Object mc = getMinecraft();
        if (mc == null) return;
        try {
            for (Method m : mc.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Runnable.class) {
                    String name = m.getName();
                    if (name.equals("addScheduledTask") || name.equals("a") || name.equals("func_152344_a")) {
                        m.setAccessible(true);
                        m.invoke(mc, task);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    public List<Object> getLoadedEntities() {
        Object world = getWorld();
        if (world == null)
            return Collections.emptyList();

        try {
            Class<?> entityClass = entityMapping != null ? entityMapping.resolveClass() : null;
            if (entityClass == null) return Collections.emptyList();

            // Try to find any Iterable field that contains entities (works for Lists, Sets, etc.)
            for (Class<?> current = world.getClass(); current != null; current = current.getSuperclass()) {
                for (Field f : current.getDeclaredFields()) {
                    if (!Iterable.class.isAssignableFrom(f.getType())) {
                        continue;
                    }
                    f.setAccessible(true);
                    Object obj = f.get(world);
                    if (!(obj instanceof Iterable)) {
                        continue;
                    }
                    
                    Iterable<?> iterable = (Iterable<?>) obj;
                    
                    // Peek at first item to see if it's an entity list
                    Object first = null;
                    for (Object item : iterable) {
                        if (item != null) {
                            first = item;
                            break;
                        }
                    }
                    
                    if (first == null) {
                        continue;
                    }
                    
                    if (entityClass.isInstance(first)) {
                        List<Object> result = new ArrayList<>();
                        for (Object item : iterable) {
                            result.add(item);
                        }
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[OnionMCC] Error getting loaded entities: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    public boolean isLivingEntity(Object entity) {
        if (entity == null || entityLivingMapping == null)
            return false;
        try {
            return entityLivingMapping.resolveClass().isInstance(entity);
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean isPlayerEntity(Object entity) {
        if (entity == null || entityPlayerMapping == null)
            return false;
        try {
            return entityPlayerMapping.resolveClass().isInstance(entity);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void invokeMinecraftMethod(String mappedName, String fallbackName) {
        if (minecraftInstance == null || mcMapping == null)
            return;
        try {
            Method method = mcMapping.getMethod(mappedName);
            method.setAccessible(true);
            method.invoke(minecraftInstance);
            return;
        } catch (Exception ignored) {
        }

        try {
            for (Method method : minecraftInstance.getClass().getDeclaredMethods()) {
                if (method.getParameterCount() != 0) {
                    continue;
                }
                String name = method.getName();
                if (mappedName.equals(name) || fallbackName.equals(name)) {
                    method.setAccessible(true);
                    method.invoke(minecraftInstance);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("[OnionMCC] Error invoking Minecraft method " + mappedName + ": " + e.getMessage());
        }
    }

    private Method findBooleanSetter(Class<?> clazz, String... preferredNames) {
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            for (String preferredName : preferredNames) {
                try {
                    Method method = current.getDeclaredMethod(preferredName, boolean.class);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == boolean.class
                        && method.getReturnType() == void.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private Object firstNonNull(List<Object> list) {
        for (Object value : list) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object getField(Object instance, ClassMapping mapping, String name) {
        if (instance == null || mapping == null)
            return null;
        try {
            return mapping.getField(name).get(instance);
        } catch (Exception e) {
            // Only log once per field to avoid spamming the log
            String key = mapping.getDeobfuscatedName() + "." + name;
            if (!loggedFieldErrors.contains(key)) {
                System.err.println("[OnionMCC] Error getting field " + name + ": " + e.getMessage());
                OnionMCC.getInstance().logError("getField failure for " + name, e);
                loggedFieldErrors.add(key);
            }
            return null;
        }
    }

    private void setField(Object instance, ClassMapping mapping, String name, Object value) {
        if (instance == null || mapping == null)
            return;
        try {
            mapping.getField(name).set(instance, value);
        } catch (Exception e) {
            System.err.println("[OnionMCC] Error setting field " + name + ": " + e.getMessage());
        }
    }

    public boolean isAirBlock(double x, double y, double z) {
        Object world = getWorld();
        if (world == null) return true;
        try {
            ClassMapping blockPosMapping = OnionMCC.getInstance().getMappingManager().getMapping("net.minecraft.util.BlockPos");
            Class<?> blockPosClass = blockPosMapping != null ? blockPosMapping.resolveClass() : null;
            
            if (blockPosClass != null) {
                Object blockPos = blockPosClass.getConstructor(int.class, int.class, int.class).newInstance((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
                
                for (Method m : world.getClass().getMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == blockPosClass && m.getReturnType() == boolean.class) {
                        String name = m.getName();
                        if (name.equals("isAirBlock") || name.equals("d")) {
                            return (Boolean) m.invoke(world, blockPos);
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return false;
    }
}


