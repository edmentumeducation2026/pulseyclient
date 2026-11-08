package dev.pulsey.module;

import dev.pulsey.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public abstract class Module {
    public final String name, description, category;
    public final List<Setting> settings = new ArrayList<>();
    public boolean enabled;
    public boolean toggleable = true;
    public boolean persistEnabled = true; // false = always starts disabled (e.g. Freecam)
    public boolean guiOpen; // settings expanded in the ClickGUI

    protected Module(String name, String description, String category) {
        this.name = name; this.description = description; this.category = category;
    }

    protected <T extends Setting> T add(T s) { settings.add(s); return s; }
    protected <T extends Setting> T add(T s, BooleanSupplier visible) { s.visible = visible; settings.add(s); return s; }

    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean on) {
        if (enabled == on) return;
        enabled = on;
        if (on) onEnable(); else onDisable();
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick(Minecraft mc) {}
    public void onWorldRender() {}
}
