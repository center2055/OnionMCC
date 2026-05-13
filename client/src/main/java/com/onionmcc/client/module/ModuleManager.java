package com.onionmcc.client.module;

import com.onionmcc.client.OnionMCC;
import com.onionmcc.client.module.modules.combat.*;
import com.onionmcc.client.module.modules.movement.*;
import com.onionmcc.client.module.modules.render.*;
import com.onionmcc.client.module.modules.player.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages all module instances. Provides registration, lookup, and category
 * filtering.
 */
public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();
    private final Map<String, Module> moduleMap = new HashMap<>();

    public void init() {
        // ── Combat ──────────────────────────────────
        register(new KillAura());
        register(new TriggerBot());
        register(new Reach());
        register(new HitBox());
        register(new AutoClicker());
        register(new Velocity());
        register(new WTap());
        register(new AimAssist());
        register(new com.onionmcc.client.module.modules.combat.SilentKiller());
        register(new com.onionmcc.client.module.modules.combat.SilentAim());

        // ── Movement ────────────────────────────────
        register(new Sprint());

        // ── Render ──────────────────────────────────
        register(new Fullbright());
        register(new com.onionmcc.client.module.modules.render.ArrayListMod());
        register(new com.onionmcc.client.module.modules.render.ESP());
        register(new com.onionmcc.client.module.modules.render.Tracers());

        // ── Player ──────────────────────────────────
        register(new Teams());
        register(new Friends());
        register(new com.onionmcc.client.module.modules.player.LegitScaffold());
        register(new com.onionmcc.client.module.modules.player.ChestStealer());
        register(new com.onionmcc.client.module.modules.player.AutoArmor());
        register(new com.onionmcc.client.module.modules.player.Clutch());

        System.out.println("[OnionMCC] Registered " + modules.size() + " modules.");
    }

    private void register(Module module) {
        modules.add(module);
        moduleMap.put(module.getName().toLowerCase(), module);
    }

    public Module getModule(String name) {
        return moduleMap.get(name.toLowerCase());
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> getModulesByCategory(ModuleCategory category) {
        return modules.stream()
                .filter(m -> m.getCategory() == category)
                .collect(Collectors.toList());
    }

    public List<Module> getEnabledModules() {
        return modules.stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());
    }

    private long lastLog = 0;

    public void onTick() {
        long now = System.currentTimeMillis();
        boolean shouldLog = now - lastLog > 1000;
        if (shouldLog) {
            lastLog = now;
        }
        
        int enabledCount = 0;
        for (Module module : modules) {
            if (module.isEnabled()) {
                enabledCount++;
                try {
                    module.onTick();
                } catch (Throwable e) {
                    System.err.println("[OnionMCC] Error in module " + module.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    OnionMCC.getInstance().logError("Module tick failure in " + module.getName(), e);
                }
            }
        }
        
        if (shouldLog && enabledCount > 0) {
            OnionMCC.getInstance().logToFile("ModuleManager onTick called. " + enabledCount + " modules enabled.");
        }
    }

    /**
     * Handle a key press — toggle any module bound to this key.
     */
    public void onKeyPress(int keyCode) {
        for (Module module : modules) {
            if (module.getKeyBind() == keyCode) {
                module.toggle();
                System.out.println(
                        "[OnionMCC] " + module.getName() + " " + (module.isEnabled() ? "enabled" : "disabled"));
            }
        }
    }
}
