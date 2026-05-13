package com.onionmcc.client.module;

/**
 * Categories for organizing modules in the UI.
 */
public enum ModuleCategory {
    COMBAT("Combat", "⚔"),
    MOVEMENT("Movement", "🏃"),
    RENDER("Render", "👁"),
    PLAYER("Player", "🎮"),
    UTILITY("Utility", "🔧");

    private final String displayName;
    private final String icon;

    ModuleCategory(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }
}
