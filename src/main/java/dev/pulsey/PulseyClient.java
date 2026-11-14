package dev.pulsey;

import com.mojang.blaze3d.platform.InputConstants;
import dev.pulsey.config.Config;
import dev.pulsey.gui.ClickGui;
import dev.pulsey.module.ModuleManager;
import dev.pulsey.render.EspRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class PulseyClient implements ClientModInitializer {
    public static final String ID = "pulsey";

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        Config.load();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
        KeyMapping openGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.pulsey.open_gui", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, category));

        KeyMapping freecamKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.pulsey.freecam", InputConstants.Type.KEYSYM, InputConstants.KEY_V, category));

        ClickGui[] gui = new ClickGui[1]; // created lazily, once Minecraft is fully started

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openGui.consumeClick()) {
                if (mc.screen == null) {
                    if (gui[0] == null) gui[0] = new ClickGui();
                    mc.setScreen(gui[0]);
                }
            }
            while (freecamKey.consumeClick()) {
                if (mc.screen == null && mc.player != null) ModuleManager.freecam.toggle();
            }
            ModuleManager.tick(mc);
        });

        WorldRenderEvents.BEFORE_TRANSLUCENT.register(EspRenderer::render);

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            Config.save();
            EspRenderer.close();
        });
    }
}
c
