package icyllis.modernui.mc.text;

import org.joml.Matrix3x2fc;

/** Chooses filtering for a rasterized outline-font run, without changing its layout. */
public final class TextSampling {
    private TextSampling() {
    }

    public static boolean needsLinear(Matrix3x2fc pose, float x, float y,
                                      float density, float guiScale) {
        // NEAREST is equivalent to LINEAR only for a one-texel-to-one-pixel,
        // axis-aligned placement. In particular, testing translation in GUI
        // units rather than physical pixels is incorrect at GUI scales > 1.
        if (!near(pose.m00() * guiScale / density, 1) ||
                !near(pose.m11() * guiScale / density, 1) ||
                pose.m01() != 0 || pose.m10() != 0) {
            return true;
        }
        float originX = (pose.m00() * x + pose.m20()) * guiScale;
        float originY = (pose.m11() * y + pose.m21()) * guiScale;
        return !near(originX, Math.rint(originX)) || !near(originY, Math.rint(originY));
    }

    private static boolean near(float value, double target) {
        return Math.abs(value - target) <= 0.0001;
    }
}
