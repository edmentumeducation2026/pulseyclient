package dev.pulsey.module;

import dev.pulsey.setting.Setting;

public class ClickGuiModule extends Module {
    public final Setting.Color accent = add(new Setting.Color("Accent", 0x38E8C6));
    public final Setting.Bool rainbow = add(new Setting.Bool("Rainbow Accent", false));
    public final Setting.Num rainbowSpeed = add(new Setting.Num("Rainbow Speed", 1.0, 0.1, 5.0, 0.1, "x"), () -> rainbow.value);
    public final Setting.Num dim = add(new Setting.Num("Background Dim", 35, 0, 80, 1, "%"));

    public ClickGuiModule() {
        super("ClickGUI", "Appearance of this menu.", "Client");
        toggleable = false;
    }
}
