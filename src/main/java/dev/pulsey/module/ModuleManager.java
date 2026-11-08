package dev.pulsey.module;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();
    public static ClickGuiModule gui;
    public static Freecam freecam;

    private ModuleManager() {}

    public static void init() {
        gui = new ClickGuiModule();
        MODULES.add(new StorageFinder());
        MODULES.add(new SpawnFinder());
        freecam = new Freecam();
        MODULES.add(freecam);
        MODULES.add(gui);
    }

    public static List<Module> all() { return MODULES; }

    public static List<String> categories() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (Module m : MODULES) set.add(m.category);
        return new ArrayList<>(set);
    }

    public static List<Module> inCategory(String c) {
        List<Module> out = new ArrayList<>();
        for (Module m : MODULES) if (m.category.equals(c)) out.add(m);
        return out;
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        for (Module m : MODULES) if (m.enabled) m.onTick(mc);
    }
}
