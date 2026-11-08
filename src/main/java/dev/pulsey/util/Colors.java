package dev.pulsey.util;

public final class Colors {
    private Colors() {}

    public static float clamp01(float v) { return v < 0 ? 0 : Math.min(v, 1); }

    public static int hsbToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float r, g, b;
        int i = (int) Math.floor(h * 6f);
        float f = h * 6f - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255 + .5f) << 16) | ((int) (g * 255 + .5f) << 8) | (int) (b * 255 + .5f);
    }

    public static float[] rgbToHsb(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min, h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    /** Per-channel ARGB lerp. */
    public static int lerp(int a, int b, float t) {
        t = clamp01(t);
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 255, cb = (b >>> shift) & 255;
            out |= ((int) (ca + (cb - ca) * t) & 255) << shift;
        }
        return out;
    }

    public static int withAlpha(int rgb, float a) {
        return ((int) (clamp01(a) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
