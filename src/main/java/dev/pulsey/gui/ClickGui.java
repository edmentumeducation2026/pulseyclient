package dev.pulsey.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.pulsey.config.Config;
import dev.pulsey.module.Module;
import dev.pulsey.module.ModuleManager;
import dev.pulsey.setting.Setting;
import dev.pulsey.util.Colors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClickGui extends Screen {
    private static final int PW = 150, HEADER = 24, ROW = 20, PICK_H = 70;

    private interface Click { void on(double mx, double my, int button); }
    private interface Drag { void on(double mx, double my); }

    private static final class Hit {
        int x, y, w, h;
        Click click;
        Drag drag;
    }

    private static final class Panel {
        final String title;
        int x, y;
        boolean open = true;
        double offX, offY;
        Panel(String title, int x, int y) { this.title = title; this.x = x; this.y = y; }
    }

    private final List<Panel> panels = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<Object, Float> anims = new HashMap<>();
    private Hit active;
    private Module hovered;
    private float scroll, scrollTarget, maxScroll;
    private long lastNs = System.nanoTime();

    public ClickGui() {
        super(Component.literal("Pulsey Client"));
        int x = 28;
        for (String cat : ModuleManager.categories()) {
            panels.add(new Panel(cat, x, 28));
            x += PW + 14;
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Config.save();
        super.onClose();
    }

    // ------------------------------------------------------------------ animation helpers
    private float anim(Object key, float target, float dt, float speed) {
        Float cur = anims.get(key);
        if (cur == null) cur = target;
        cur += (target - cur) * Math.min(1f, dt * speed);
        if (Math.abs(target - cur) < 0.002f) cur = target;
        anims.put(key, cur);
        return cur;
    }

    private float peek(Object key) {
        Float v = anims.get(key);
        return v == null ? 0f : v;
    }

    /** Updates animations that influence layout so heights are consistent within a frame. */
    private void updateLayoutAnims(float dt) {
        for (Module m : ModuleManager.all()) {
            anim(m, m.guiOpen && !m.settings.isEmpty() ? 1f : 0f, dt, 12f);
            for (Setting s : m.settings) {
                if (s instanceof Setting.Color c) anim(c, openPickers.contains(c) ? 1f : 0f, dt, 14f);
            }
        }
    }

    private final java.util.Set<Setting.Color> openPickers = new java.util.HashSet<>();

    // ------------------------------------------------------------------ layout maths
    private int settingHeight(Setting s) {
        if (s instanceof Setting.Num) return 24;
        if (s instanceof Setting.Color c) return 18 + (int) (PICK_H * peek(c));
        return 18;
    }

    private int settingsHeight(Module m) {
        int h = 6;
        for (Setting s : m.settings) if (s.isVisible()) h += settingHeight(s);
        return h;
    }

    private int moduleHeight(Module m) {
        return ROW + (int) (settingsHeight(m) * peek(m));
    }

    // ------------------------------------------------------------------ screen overrides
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        int a = (int) (ModuleManager.gui.dim.value / 100.0 * 255);
        g.fill(0, 0, this.width, this.height, a << 24);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;

        hits.clear();
        hovered = null;
        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 14f);
        updateLayoutAnims(dt);

        int accent = Theme.accent();
        float bottom = 0;
        for (Panel p : panels) bottom = Math.max(bottom, drawPanel(g, p, mx, my, dt, accent));
        maxScroll = Math.max(0, bottom - this.height + 36);

        if (hovered != null) drawTooltip(g, hovered.description);
        g.drawString(this.font, "Pulsey Client  |  Right-click a module for settings", 8, this.height - 12, Theme.MUTED, false);
    }

    /** Draws one panel; returns its unscrolled bottom edge. */
    private float drawPanel(GuiGraphics g, Panel p, int mx, int my, float dt, int accent) {
        int x = p.x, y = p.y + (int) scroll;
        List<Module> mods = ModuleManager.inCategory(p.title);

        int body = 0;
        if (p.open) for (Module m : mods) body += moduleHeight(m);
        int total = HEADER + body;

        Draw.rrect(g, x - 2, y - 2, PW + 4, total + 4, 8, 0x30000000);
        Draw.rrect(g, x, y, PW, total, 6, Theme.PANEL_BG);
        Draw.rrect(g, x, y, PW, HEADER, 6, Theme.HEADER_BG);
        if (body > 0) g.fill(x, y + HEADER - 6, x + PW, y + HEADER, Theme.HEADER_BG);
        g.fill(x + 8, y + HEADER - 1, x + PW - 8, y + HEADER, accent);

        g.drawString(this.font, Component.literal(p.title.toUpperCase()).withStyle(ChatFormatting.BOLD),
                x + 10, y + 8, Theme.TEXT, false);
        g.drawString(this.font, p.open ? "-" : "+", x + PW - 14, y + 8, Theme.MUTED, false);

        hit(x, y, PW, HEADER, y, y + HEADER,
                (cx, cy, btn) -> {
                    if (btn == 0) { p.offX = cx - p.x; p.offY = cy - (p.y + scroll); }
                    else if (btn == 1) p.open = !p.open;
                },
                (cx, cy) -> { p.x = (int) (cx - p.offX); p.y = (int) (cy - p.offY - scroll); });

        if (p.open) {
            int cy = y + HEADER;
            for (Module m : mods) {
                int h = moduleHeight(m);
                drawModule(g, m, x, cy, mx, my, dt, accent);
                cy += h;
            }
        }
        return p.y + total;
    }

    private void drawModule(GuiGraphics g, Module m, int x, int cy, int mx, int my, float dt, int accent) {
        boolean over = mx >= x && mx < x + PW && my >= cy && my < cy + ROW;
        if (over) hovered = m;

        float en = anim(m.name + "#en", m.enabled || !m.toggleable ? 1f : 0f, dt, 14f);
        if (over) g.fill(x, cy, x + PW, cy + ROW, 0x14FFFFFF);
        if (en > 0.01f && m.toggleable) {
            g.fill(x, cy, x + PW, cy + ROW, Colors.withAlpha(accent, en * 0.16f));
            g.fill(x, cy + 2, x + 2, cy + ROW - 2, Colors.withAlpha(accent, en));
        }
        int textColor = m.toggleable ? Colors.lerp(Theme.MUTED, Theme.TEXT, en) : Theme.TEXT;
        g.drawString(this.font, m.name, x + 10, cy + 6, textColor, false);
        if (!m.settings.isEmpty()) {
            g.drawString(this.font, peek(m) > 0.5f ? "v" : ">", x + PW - 12, cy + 6, Theme.MUTED, false);
        }

        hit(x, cy, PW, ROW, cy, cy + ROW, (cx, cy2, btn) -> {
            if (btn == 0 && m.toggleable) m.toggle();
            else if (btn == 1 || (btn == 0 && !m.toggleable)) m.guiOpen = !m.guiOpen;
        }, null);

        int sh = (int) (settingsHeight(m) * peek(m));
        if (sh > 1) {
            int top = cy + ROW, bot = top + sh;
            g.enableScissor(x, top, x + PW, bot);
            g.fill(x, top, x + PW, bot, Theme.SETTINGS_BG);
            int sy = top + 3;
            for (Setting s : m.settings) {
                if (!s.isVisible()) continue;
                sy += drawSetting(g, s, x, sy, top, bot, mx, my, dt, accent);
            }
            g.disableScissor();
        }
    }

    // ------------------------------------------------------------------ settings widgets
    private int drawSetting(GuiGraphics g, Setting s, int x, int y, int clipT, int clipB,
                            int mx, int my, float dt, int accent) {
        int w = PW;
        if (s instanceof Setting.Bool b) {
            float a = anim(b, b.value ? 1f : 0f, dt, 16f);
            g.drawString(this.font, b.name, x + 8, y + 5, Theme.TEXT_DIM, false);
            int pw = 24, ph = 11, px = x + w - 8 - pw, py = y + 3;
            Draw.rrect(g, px, py, pw, ph, 5, Colors.lerp(Theme.TRACK, accent, a));
            Draw.rrect(g, px + 1 + (int) ((pw - ph) * a), py + 1, ph - 2, ph - 2, 4, 0xFFFFFFFF);
            hit(x, y, w, 18, clipT, clipB, (cx, cy, btn) -> b.value = !b.value, null);
            return 18;
        }
        if (s instanceof Setting.Num n) {
            float frac = anim(n, (float) n.fraction(), dt, 22f);
            g.drawString(this.font, n.name, x + 8, y + 3, Theme.TEXT_DIM, false);
            String val = n.display();
            g.drawString(this.font, val, x + w - 8 - this.font.width(val), y + 3, accent, false);
            int tx = x + 8, tw = w - 16, ty = y + 16;
            Draw.rrect(g, tx, ty, tw, 3, 1, Theme.TRACK);
            int fw = (int) (tw * frac);
            if (fw > 0) Draw.rrect(g, tx, ty, Math.max(fw, 2), 3, 1, accent);
            Draw.rrect(g, tx + fw - 3, ty - 2, 7, 7, 3, 0xFFFFFFFF);
            Drag setter = (cx, cy) -> n.setFraction((cx - tx) / (double) tw);
            hit(tx - 3, y + 10, tw + 6, 14, clipT, clipB, (cx, cy, btn) -> setter.on(cx, cy), setter);
            return 24;
        }
        if (s instanceof Setting.Mode m) {
            g.drawString(this.font, m.name, x + 8, y + 5, Theme.TEXT_DIM, false);
            String v = m.value();
            int cw = this.font.width(v) + 10, cx0 = x + w - 8 - cw;
            Draw.rrect(g, cx0, y + 2, cw, 14, 4, Theme.CHIP);
            g.drawString(this.font, v, cx0 + 5, y + 5, accent, false);
            hit(x, y, w, 18, clipT, clipB, (cx, cy, btn) -> { if (btn == 1) m.prev(); else m.next(); }, null);
            return 18;
        }
        if (s instanceof Setting.Color c) {
            g.drawString(this.font, c.name, x + 8, y + 5, Theme.TEXT_DIM, false);
            int sx0 = x + w - 8 - 24;
            Draw.rrect(g, sx0 - 1, y + 2, 26, 14, 5, 0xFF343954);
            Draw.rrect(g, sx0, y + 3, 24, 12, 4, 0xFF000000 | c.rgb());
            hit(x, y, w, 18, clipT, clipB, (cx, cy, btn) -> {
                if (!openPickers.remove(c)) openPickers.add(c);
            }, null);

            float o = peek(c);
            int ph = (int) (PICK_H * o);
            if (ph > 2) {
                int py = y + 18;
                g.enableScissor(x, py, x + w, py + ph);
                drawPicker(g, c, x, py, w, clipT, clipB);
                g.disableScissor();
            }
            return 18 + ph;
        }
        return 0;
    }

    private void drawPicker(GuiGraphics g, Setting.Color c, int x, int py, int w, int clipT, int clipB) {
        int sx = x + 8, sy = py + 4, sw = w - 16 - 14, sh = PICK_H - 12;
        int hx = sx + sw + 4, hw = 10;

        for (int i = 0; i < sw; i += 3) {
            for (int j = 0; j < sh; j += 3) {
                int col = Colors.hsbToRgb(c.h, i / (float) sw, 1f - j / (float) sh);
                g.fill(sx + i, sy + j, Math.min(sx + i + 3, sx + sw), Math.min(sy + j + 3, sy + sh), 0xFF000000 | col);
            }
        }
        for (int j = 0; j < sh; j += 2) {
            g.fill(hx, sy + j, hx + hw, Math.min(sy + j + 2, sy + sh), 0xFF000000 | Colors.hsbToRgb(j / (float) sh, 1f, 1f));
        }

        int mxk = sx + (int) (c.s * sw), myk = sy + (int) ((1f - c.b) * sh);
        Draw.rrect(g, mxk - 3, myk - 3, 7, 7, 3, 0xFF000000);
        Draw.rrect(g, mxk - 2, myk - 2, 5, 5, 2, 0xFFFFFFFF);
        int hy = sy + (int) (c.h * sh);
        g.fill(hx - 1, hy - 1, hx + hw + 1, hy + 2, 0xFFFFFFFF);

        Drag svDrag = (cx, cy) -> {
            c.s = Colors.clamp01((float) ((cx - sx) / sw));
            c.b = 1f - Colors.clamp01((float) ((cy - sy) / sh));
        };
        Drag hueDrag = (cx, cy) -> c.h = Math.min(0.9999f, Colors.clamp01((float) ((cy - sy) / sh)));
        hit(sx, sy, sw, sh, clipT, clipB, (cx, cy, b) -> svDrag.on(cx, cy), svDrag);
        hit(hx, sy, hw, sh, clipT, clipB, (cx, cy, b) -> hueDrag.on(cx, cy), hueDrag);
    }

    private void drawTooltip(GuiGraphics g, String s) {
        int w = this.font.width(s) + 16, x = (this.width - w) / 2, y = this.height - 32;
        Draw.rrect(g, x, y, w, 18, 5, 0xE6101218);
        g.drawString(this.font, s, x + 8, y + 5, Theme.TEXT_DIM, false);
    }

    private void hit(int x, int y, int w, int h, int clipT, int clipB, Click click, Drag drag) {
        int y1 = Math.max(y, clipT), y2 = Math.min(y + h, clipB);
        if (y2 <= y1) return;
        Hit ht = new Hit();
        ht.x = x; ht.y = y1; ht.w = w; ht.h = y2 - y1;
        ht.click = click; ht.drag = drag;
        hits.add(ht);
    }

    // ------------------------------------------------------------------ input (1.21.9+ event-style API)
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int btn = event.button();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
                if (h.click != null) h.click.on(mx, my, btn);
                if (h.drag != null && btn == 0) active = h;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (active != null && active.drag != null) {
            active.drag.on(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        active = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scrollTarget = (float) Math.max(-maxScroll, Math.min(0, scrollTarget + scrollY * 28));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RSHIFT) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
