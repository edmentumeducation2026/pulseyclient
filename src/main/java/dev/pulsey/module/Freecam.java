package dev.pulsey.module;

import dev.pulsey.setting.Setting;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side only free camera.
 *
 * How it stays invisible to the server:
 *  - The real LocalPlayer is never moved or rotated: its input is swapped for an empty ClientInput
 *    and mouse-look (Entity#turn) is redirected to the camera by EntityMixin.
 *  - Only the rendered Camera is overridden (CameraMixin), so the movement packets the server
 *    receives keep describing the real player standing where you enabled Freecam.
 */
public class Freecam extends Module {
    private static Freecam instance;
    public static Freecam get() { return instance; }

    public final Setting.Num speed = add(new Setting.Num("Speed", 0.9, 0.1, 5.0, 0.1, " b/t"));
    public final Setting.Num sprintBoost = add(new Setting.Num("Sprint Boost", 2.0, 1.0, 5.0, 0.25, "x"));
    public final Setting.Num smoothing = add(new Setting.Num("Smoothing", 40, 0, 90, 5, "%"));
    public final Setting.Bool blockInteractions = add(new Setting.Bool("Block Interactions", true));
    public final Setting.Bool disableOnDamage = add(new Setting.Bool("Disable On Damage", true));

    private Vec3 pos = Vec3.ZERO, prev = Vec3.ZERO, vel = Vec3.ZERO;
    private float yaw, pitch;
    private LocalPlayer owner;
    private ClientInput savedInput;

    public Freecam() {
        super("Freecam", "Fly a detached camera in singleplayer. Your real position never changes.", "Player");
        persistEnabled = false;
        instance = this;

        // Stop your (stationary) real player from hitting / placing / using things the camera can't "see".
        UseBlockCallback.EVENT.register((p, w, h, hit) -> guard(p));
        AttackBlockCallback.EVENT.register((p, w, h, pos, dir) -> guard(p));
        UseItemCallback.EVENT.register((p, w, h) -> guard(p));
        AttackEntityCallback.EVENT.register((p, w, h, e, hit) -> guard(p));
        UseEntityCallback.EVENT.register((p, w, h, e, hit) -> guard(p));
    }

    private InteractionResult guard(Player p) {
        return isActive() && blockInteractions.value && p == owner ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    // ---------------------------------------------------------------- state used by the mixins
    public boolean isActive() {
        return enabled && owner != null && Minecraft.getInstance().player == owner;
    }

    public Vec3 renderPos(float partialTick) {
        return new Vec3(
                prev.x + (pos.x - prev.x) * partialTick,
                prev.y + (pos.y - prev.y) * partialTick,
                prev.z + (pos.z - prev.z) * partialTick);
    }

    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    /** Mouse look; arguments are the raw deltas Entity#turn receives. */
    public void turn(double yawDelta, double pitchDelta) {
        yaw += (float) (yawDelta * 0.15);
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) (pitchDelta * 0.15)));
    }

    // ---------------------------------------------------------------- lifecycle
    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { enabled = false; return; }
        if (mc.getSingleplayerServer() == null) {
            p.displayClientMessage(Component.literal("Freecam is only available in singleplayer."), true);
            enabled = false;
            return;
        }

        owner = p;
        pos = prev = p.getEyePosition();
        vel = Vec3.ZERO;
        yaw = p.getYRot();
        pitch = p.getXRot();

        savedInput = p.input;
        p.input = new ClientInput(); // real player now receives no movement input
        p.displayClientMessage(Component.literal("Freecam enabled"), true);
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = owner;
        // If the player object changed (respawn / dimension change) the game already installed fresh input.
        if (p != null && mc.player == p && savedInput != null) {
            p.input = savedInput;
            p.displayClientMessage(Component.literal("Freecam disabled"), true);
        }
        owner = null;
        savedInput = null;
        vel = Vec3.ZERO;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player != owner || mc.getSingleplayerServer() == null) { setEnabled(false); return; }
        if (disableOnDamage.value && owner.hurtTime > 0) { setEnabled(false); return; }

        float f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        float s = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        float v = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);

        double len = Math.sqrt(f * f + s * s);
        if (len > 1) { f /= len; s /= len; }

        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad), cos = Math.cos(rad);
        double mult = speed.value * (mc.options.keySprint.isDown() ? sprintBoost.value : 1.0);

        // Minecraft yaw 0 faces +Z; forward = (-sin, cos), left = (cos, sin)
        Vec3 target = new Vec3(
                (-sin * f + cos * s) * mult,
                v * mult,
                (cos * f + sin * s) * mult);

        double k = 1.0 - smoothing.value / 100.0; // 1 = instant, lower = floatier
        vel = new Vec3(
                vel.x + (target.x - vel.x) * k,
                vel.y + (target.y - vel.y) * k,
                vel.z + (target.z - vel.z) * k);

        prev = pos;
        pos = pos.add(vel);
    }
}
