package dev.pulsey.config;

import com.google.gson.*;
import dev.pulsey.module.Module;
import dev.pulsey.module.ModuleManager;
import dev.pulsey.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private static final Logger LOG = LoggerFactory.getLogger("Pulsey");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("pulsey.json");

    private Config() {}

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (Module m : ModuleManager.all()) {
                if (!root.has(m.name)) continue;
                JsonObject mo = root.getAsJsonObject(m.name);
                if (m.toggleable && m.persistEnabled && mo.has("enabled")) m.setEnabled(mo.get("enabled").getAsBoolean());
                if (!mo.has("settings")) continue;
                JsonObject so = mo.getAsJsonObject("settings");
                for (Setting s : m.settings) {
                    if (!so.has(s.name)) continue;
                    try { s.load(so.get(s.name)); } catch (RuntimeException ignored) {}
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not read config", e);
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        for (Module m : ModuleManager.all()) {
            JsonObject mo = new JsonObject();
            mo.addProperty("enabled", m.persistEnabled && m.enabled);
            JsonObject so = new JsonObject();
            for (Setting s : m.settings) so.add(s.name, s.save());
            mo.add("settings", so);
            root.add(m.name, mo);
        }
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(root, w);
        } catch (Exception e) {
            LOG.warn("Could not write config", e);
        }
    }
}
