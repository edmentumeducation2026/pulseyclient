package dev.pulsey.gui;

import dev.pulsey.module.ClickGuiModule;
import dev.pulsey.module.ModuleManager;
import dev.pulsey.util.Colors;

public final class Theme {
    private Theme() {}

    public static final int PANEL_BG = 0xF0101218;
    public static final int HEADER_BG = 0xFF171A25;
    public static final int SETTINGS_BG = 0x38000000;
    public static final int TRACK = 0xFF272B3C;
    public static final int CHIP = 0xFF222638;
    public static final int TEXT = 0xFFE8EAF2;
    public static final int TEXT_DIM = 0xFFC0C5D6;
    public static final int MUTED = 0xFF7F869C;

    public static int accent() {
        ClickGuiModule g = ModuleManager.gui;
        if (g.rainbow.value) {
            float hue = (float) ((System.nanoTime() / 1e9 * 0.08 * g.rainbowSpeed.value) % 1.0);
            return 0xFF000000 | Colors.hsbToRgb(hue, 0.6f, 1f);
        }
        return 0xFF000000 | g.accent.rgb();
    }
}
