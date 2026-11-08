package dev.pulsey.module;

import dev.pulsey.render.EspRenderer;
import dev.pulsey.setting.Setting;
import dev.pulsey.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shared logic for "highlight these block entities" modules. */
public abstract class BlockEspModule extends Module {

    /** A category of block entity the module can highlight (own toggle + own colour). */
    public static final class Kind {
        public final String name;
        public final Setting.Bool enabled;
        public final Setting.Color color;
        Kind(String name, Setting.Bool enabled, Setting.Color color) {
            this.name = name; this.enabled = enabled; this.color = color;
        }
    }

    protected record Target(BlockPos pos, AABB box, Kind kind) {}

    private List<Target> targets = new ArrayList<>();
    private ClientLevel lastLevel;
    private int timer;

    // common settings (created in initCommon)
    protected Setting.Mode renderMode, outlineStyle, colorMode;
    protected Setting.Num cornerLength, lineWidth, outlineOpacity, fillOpacity, rainbowSpeed,
            range, expand, pulseSpeed, scanInterval;
    protected Setting.Color staticColor, nearColor, farColor;
    protected Setting.Bool distanceScaling, distanceFade;

    protected BlockEspModule(String name, String description) {
        super(name, description, "Render");
    }

    /** Adds a toggle + colour pair for one block-entity category. Call before {@link #initCommon}. */
    protected Kind kind(String name, int rgb, boolean defaultOn) {
        Setting.Bool on = add(new Setting.Bool(name, defaultOn));
        Setting.Color col = add(new Setting.Color(name + " Color", rgb),
                () -> on.value && colorMode.is("By Type"));
        return new Kind(name, on, col);
    }

    protected void initCommon(int defaultColor, double defaultPulse) {
        renderMode = add(new Setting.Mode("Render Mode", "Both", "Outline", "Fill", "Both"));
        outlineStyle = add(new Setting.Mode("Outline Style", "Full", "Full", "Corners"), this::isOutline);
        cornerLength = add(new Setting.Num("Corner Length", 0.3, 0.1, 0.5, 0.05, ""),
                () -> isOutline() && outlineStyle.is("Corners"));
        lineWidth = add(new Setting.Num("Line Width", 3, 1, 10, 0.5, ""), this::isOutline);
        distanceScaling = add(new Setting.Bool("Distance Scaling", true), this::isOutline);
        outlineOpacity = add(new Setting.Num("Outline Opacity", 100, 0, 100, 1, "%"), this::isOutline);
        fillOpacity = add(new Setting.Num("Fill Opacity", 25, 0, 100, 1, "%"), this::isFill);

        colorMode = add(new Setting.Mode("Color Mode", "By Type", "Static", "By Type", "Rainbow", "Distance"));
        staticColor = add(new Setting.Color("Color", defaultColor), () -> colorMode.is("Static"));
        rainbowSpeed = add(new Setting.Num("Rainbow Speed", 1.0, 0.1, 5.0, 0.1, "x"), () -> colorMode.is("Rainbow"));
        nearColor = add(new Setting.Color("Near Color", 0xFF4D4D), () -> colorMode.is("Distance"));
        farColor = add(new Setting.Color("Far Color", 0x4DA6FF), () -> colorMode.is("Distance"));

        range = add(new Setting.Num("Range", 128, 16, 256, 8, " blocks"));
        expand = add(new Setting.Num("Expand", 0.005, 0, 0.25, 0.005, ""));
        distanceFade = add(new Setting.Bool("Distance Fade", false));
        pulseSpeed = add(new Setting.Num("Pulse Speed", defaultPulse, 0, 5, 0.1, "x"));
        scanInterval = add(new Setting.Num("Scan Interval", 10, 1, 40, 1, " ticks"));
    }

    protected boolean isOutline() { return !renderMode.is("Fill"); }
    protected boolean isFill() { return !renderMode.is("Outline"); }

    /** Returns the kind this block entity belongs to, or null to ignore it. */
    protected abstract Kind classify(BlockEntity be);

    /** Box to draw for this block entity; null to skip it. */
    protected AABB boxFor(BlockEntity be, Kind kind) {
        return new AABB(be.getBlockPos());
    }

    @Override
    protected void onEnable() {
        targets = new ArrayList<>();
        timer = 999;
    }

    @Override
    protected void onDisable() {
        targets = new ArrayList<>();
    }

    // ------------------------------------------------------------ scanning
    @Override
    public void onTick(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            targets = new ArrayList<>();
                    timer = 999;
        }
        if (++timer < scanInterval.i()) return;
        timer = 0;
        scan(mc);
    }

    private void scan(Minecraft mc) {
        ClientLevel level = mc.level;
        Vec3 pv = mc.player.position();
        BlockPos pp = mc.player.blockPosition();
        double r = range.value, r2 = r * r;
        int chunkRadius = ((int) r >> 4) + 1;
        int pcx = pp.getX() >> 4, pcz = pp.getZ() >> 4;

        List<Target> out = new ArrayList<>();
        for (int cx = pcx - chunkRadius; cx <= pcx + chunkRadius; cx++) {
            for (int cz = pcz - chunkRadius; cz <= pcz + chunkRadius; cz++) {
                ChunkAccess access = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (!(access instanceof LevelChunk chunk)) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (pos.getCenter().distanceToSqr(pv) > r2) continue;
                    Kind k = classify(be);
                    if (k == null || !k.enabled.value) continue;
                    AABB box = boxFor(be, k);
                    if (box == null) continue;
                    out.add(new Target(pos, box, k));
                }
            }
        }

        targets = out;
    }

    // ------------------------------------------------------------ rendering
    @Override
    public void onWorldRender() {
        if (targets.isEmpty()) return;

        double time = System.nanoTime() / 1e9;
        float pulse = pulseSpeed.value > 0
                ? 1f - 0.5f * (0.5f + 0.5f * (float) Math.sin(time * pulseSpeed.value * Math.PI))
                : 1f;

        boolean ol = isOutline(), fl = isFill();
        boolean corners = outlineStyle.is("Corners");
        float baseThick = lineWidth.f() * 0.0035f;
        double maxRange = range.value;

        for (Target t : targets) {
            Vec3 c = t.box().getCenter();
            double dx = c.x - EspRenderer.camX, dy = c.y - EspRenderer.camY, dz = c.z - EspRenderer.camZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > maxRange + 8) continue;

            float fade = distanceFade.value ? (float) Math.max(0.15, 1.0 - dist / maxRange * 0.85) : 1f;
            int rgb = colorFor(t, dist, time);
            AABB bb = expand.value > 0 ? t.box().inflate(expand.value) : t.box();

            if (fl) {
                EspRenderer.fillBox(bb, rgb, fillOpacity.f() / 100f * pulse * fade);
            }
            if (ol) {
                float thick = distanceScaling.value ? baseThick * (1f + (float) dist * 0.015f) : baseThick;
                EspRenderer.edgeBox(bb, rgb, outlineOpacity.f() / 100f * fade * (fl ? 1f : pulse),
                        thick, corners, cornerLength.f());
            }
        }
    }

    private int colorFor(Target t, double dist, double time) {
        if (colorMode.is("Static")) return staticColor.rgb();
        if (colorMode.is("Rainbow")) {
            float hue = (float) ((time * rainbowSpeed.value * 0.1 + dist * 0.004) % 1.0);
            return Colors.hsbToRgb(hue, 0.75f, 1f);
        }
        if (colorMode.is("Distance")) {
            return Colors.lerp(nearColor.rgb(), farColor.rgb(), (float) (dist / range.value)) & 0xFFFFFF;
        }
        return t.kind().color.rgb();
    }
}
