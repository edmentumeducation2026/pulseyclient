package dev.pulsey.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.pulsey.util.Colors;

import java.util.Locale;
import java.util.function.BooleanSupplier;

public abstract class Setting {
    public final String name;
    public BooleanSupplier visible = () -> true;

    protected Setting(String name) { this.name = name; }

    public boolean isVisible() { return visible.getAsBoolean(); }
    public abstract JsonElement save();
    public abstract void load(JsonElement e);

    public static class Bool extends Setting {
        public boolean value;
        public Bool(String name, boolean def) { super(name); this.value = def; }
        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) { value = e.getAsBoolean(); }
    }

    public static class Num extends Setting {
        public double value;
        public final double min, max, step;
        public final String suffix;

        public Num(String name, double def, double min, double max, double step, String suffix) {
            super(name);
            this.value = def; this.min = min; this.max = max; this.step = step; this.suffix = suffix;
        }
        public float f() { return (float) value; }
        public int i() { return (int) Math.round(value); }
        public double fraction() { return (value - min) / (max - min); }
        public void setFraction(double f) {
            double v = min + Math.max(0, Math.min(1, f)) * (max - min);
            v = Math.round(v / step) * step;
            value = Math.max(min, Math.min(max, v));
        }
        public String display() {
            int dec = step >= 1 ? 0 : step >= 0.1 ? 1 : step >= 0.01 ? 2 : 3;
            return String.format(Locale.ROOT, "%." + dec + "f", value) + suffix;
        }
        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) { value = Math.max(min, Math.min(max, e.getAsDouble())); }
    }

    public static class Mode extends Setting {
        public final String[] options;
        public int index;

        public Mode(String name, String def, String... options) {
            super(name);
            this.options = options;
            for (int i = 0; i < options.length; i++) if (options[i].equals(def)) index = i;
        }
        public String value() { return options[index]; }
        public boolean is(String s) { return options[index].equals(s); }
        public void next() { index = (index + 1) % options.length; }
        public void prev() { index = (index - 1 + options.length) % options.length; }
        @Override public JsonElement save() { return new JsonPrimitive(value()); }
        @Override public void load(JsonElement e) {
            for (int i = 0; i < options.length; i++) if (options[i].equals(e.getAsString())) index = i;
        }
    }

    public static class Color extends Setting {
        public float h, s, b;
        public Color(String name, int rgb) { super(name); set(rgb); }
        public void set(int rgb) { float[] hsb = Colors.rgbToHsb(rgb); h = hsb[0]; s = hsb[1]; b = hsb[2]; }
        public int rgb() { return Colors.hsbToRgb(h, s, b) & 0xFFFFFF; }
        @Override public JsonElement save() { return new JsonPrimitive(rgb()); }
        @Override public void load(JsonElement e) { set(e.getAsInt()); }
    }
}
