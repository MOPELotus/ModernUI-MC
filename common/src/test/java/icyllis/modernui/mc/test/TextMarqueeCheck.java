package icyllis.modernui.mc.test;

import com.mojang.blaze3d.vertex.VertexConsumer;
import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.SamplerDesc;
import icyllis.arc3d.granite.SubRunContainer;
import icyllis.arc3d.granite.TextBlobCache;
import icyllis.arc3d.sketch.Font;
import icyllis.arc3d.sketch.GlyphRun;
import icyllis.arc3d.sketch.GlyphRunList;
import icyllis.arc3d.sketch.Matrix;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.StrikeDesc;
import icyllis.arc3d.sketch.j2d.Typeface_JDK;
import icyllis.modernui.mc.CanvasTextOrigin;
import icyllis.modernui.mc.text.CharacterStyle;
import icyllis.modernui.mc.text.ModernBakedGlyph;
import icyllis.modernui.mc.text.TextRunRenderState;
import icyllis.modernui.mc.text.TextSampling;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.joml.Matrix3x2f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** CPU checks of actual emitted vertices and Arc3D cache/transform code; not visual acceptance. */
public final class TextMarqueeCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        checkVertices();
        checkSampling();
        checkCanvas();
        checkCanvasInjection();
        System.out.println("Text marquee: " + checks + " geometry, sampling, cache and injection checks passed");
    }

    private static void checkVertices() {
        var a = glyph(-1, -6, 4, 9);
        var b = glyph(0, -5, 3, 7);
        BakedGlyph[] glyphs = {a, b};
        float[] positions = {0.18f, 0, 4.72f, 0};
        int[] flags = {CharacterStyle.IMPLICIT_COLOR_MASK, CharacterStyle.IMPLICIT_COLOR_MASK};
        // Exercise both pose translation (GuiGraphicsExtractor) and a fractional
        // draw origin (including the old xAdj/yAdj integration API).
        for (int guiScale : new int[]{1, 2, 3, 4}) {
            for (float scale : new float[]{0.375f, 0.7f, 1, 1.25f}) {
                float density = guiScale * scale;
                for (boolean origin : new boolean[]{false, true}) {
                    for (boolean shadow : new boolean[]{false, true}) {
                        Capture first = null;
                        for (int frame = 0; frame <= 320; frame++) {
                            float dx = -2 + frame / 80f;
                            float dy = 0.37f * dx;
                            var pose = new Matrix3x2f().scaling(scale);
                            if (!origin) pose.setTranslation(scale * dx, scale * dy);
                            var capture = new Capture();
                            new TextRunRenderState(pose, null, null, null,
                                    origin ? dx : 0, origin ? dy : 0, -1, shadow,
                                    glyphs, positions, flags, 0, 2, false, true, density, 1)
                                    .buildVertices(capture);
                            if (first == null) first = capture;
                            check(capture.vertices.size() == (shadow ? 16 : 8), "glyph/shadow count");
                            for (int v = 0; v < capture.vertices.size(); v++) {
                                var actual = capture.vertices.get(v);
                                var start = first.vertices.get(v);
                                close((actual[0] - start[0]) * guiScale,
                                        (dx + 2) * scale * guiScale, "continuous horizontal motion");
                                close((actual[1] - start[1]) * guiScale,
                                        (dy + 0.74f) * scale * guiScale, "continuous vertical motion");
                                close(actual[2], start[2], "stable atlas U");
                                close(actual[3], start[3], "stable atlas V");
                            }
                            // The quad includes one transparent texel on each edge.
                            close(capture.vertices.get(3)[0] - capture.vertices.get(0)[0],
                                    (a.width + 2) / density * scale, "filter border geometry");
                            close(capture.vertices.get(0)[2], a.u1 - (a.u2 - a.u1) / a.width,
                                    "filter border UV");
                        }
                    }
                }
            }
        }
        // Bitmap replacements and emoji keep their original atlas rectangles.
        for (boolean emoji : new boolean[]{false, true}) {
            int bits = CharacterStyle.ANY_BITMAP_REPLACEMENT;
            var capture = new Capture();
            new TextRunRenderState(new Matrix3x2f().translation(0.25f, -0.3f), null, null, null,
                    0, 0, -1, false, new BakedGlyph[]{a}, new float[]{0, 0}, new int[]{bits},
                    0, 1, emoji, true, 2, 1).buildVertices(capture);
            close(capture.vertices.get(0)[2], a.u1, "bitmap/emoji UV left");
            close(capture.vertices.get(2)[2], a.u2, "bitmap/emoji UV right");
        }
        // This phase sweep would have failed with per-glyph draw-origin rounding.
        float oldMin = Float.POSITIVE_INFINITY, oldMax = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 100; i++) {
            float x = i / 100f;
            float gap = Math.round(positions[2] + x) - Math.round(positions[0] + x);
            oldMin = Math.min(oldMin, gap);
            oldMax = Math.max(oldMax, gap);
        }
        check(oldMax - oldMin == 1, "fixture reproduces the old one-pixel spacing wobble");
    }

    private static void checkSampling() {
        for (int guiScale : new int[]{1, 2, 3, 4}) {
            for (float scale : new float[]{0.5f, 0.75f, 1, 1.25f}) {
                for (int phase = -32; phase <= 32; phase++) {
                    float deviceOffset = phase / 16f;
                    var pose = new Matrix3x2f().translation(deviceOffset / guiScale, 0).scale(scale);
                    check(TextSampling.needsLinear(pose, 0, 0, guiScale * scale, guiScale)
                            == (phase % 16 != 0), "sampling follows physical-pixel phase");
                }
            }
        }
        check(TextSampling.needsLinear(new Matrix3x2f(), 0.125f, 0, 2, 2), "fractional draw origin");
        check(TextSampling.needsLinear(new Matrix3x2f().scaling(0.5f), 0, 0, 2, 2), "minification");
        check(TextSampling.needsLinear(new Matrix3x2f().rotate(0.1f), 0, 0, 2, 2), "rotation");
    }

    private static void checkCanvas() {
        var font = new Font();
        font.setTypeface(new Typeface_JDK(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12)));
        var run = new GlyphRun();
        run.set(new int[]{1, 2}, 0, new float[]{0, 0, 5.4f, 0}, 0, 2, font);
        var runs = new GlyphRunList();
        runs.set(new GlyphRun[]{run}, 1, null, new Rect2f(0, -9, 12, 3), 3.25f, -1.5f);
        try (var paint = new Paint()) {
            var initial = new Matrix();
            initial.setScale(0.75f, 0.75f);
            var baseline = new TextBlobCache.FeatureKey();
            baseline.update(runs, paint, initial);
            var subRun = new SubRunContainer.DirectMaskSubRun(
                    StrikeDesc.makeMask(font, paint, initial), initial, new Rect2f(0, 0, 12, 9),
                    Engine.MASK_FORMAT_A8, new int[]{1, 2}, 0, new float[]{0, 0, 5, 0}, 0, 2, 1, 1);
            for (int i = -128; i <= 128; i++) {
                var actual = new Matrix(initial);
                actual.setTranslateX(i / 32f);
                actual.setTranslateY(i / 64f);
                var baking = new Matrix(actual);
                baking.preTranslate(runs.mOriginX, runs.mOriginY);
                CanvasTextOrigin.forRasterization(baking);
                var key = new TextBlobCache.FeatureKey();
                key.update(runs, paint, baking);
                check(key.equals(baseline) && key.hashCode() == baseline.hashCode(), "reuse Canvas cache while scrolling");
                var toLocal = new Matrix();
                var toDevice = new Matrix();
                int filter = subRun.getMatrixAndFilter(actual, runs.mOriginX, runs.mOriginY, toLocal, toDevice);
                float x = actual.getTranslateX() + 0.75f * runs.mOriginX;
                float y = actual.getTranslateY() + 0.75f * runs.mOriginY;
                close(toDevice.getTranslateX(), x, "Canvas restores actual X and run origin");
                close(toDevice.getTranslateY(), y, "Canvas restores actual Y and run origin");
                check(filter == ((x == Math.floor(x) && y == Math.floor(y))
                        ? SamplerDesc.FILTER_NEAREST : SamplerDesc.FILTER_LINEAR), "Canvas fractional filtering");
            }
            var resized = new Matrix();
            resized.setScale(1, 1);
            var resizedKey = new TextBlobCache.FeatureKey();
            resizedKey.update(runs, paint, resized);
            check(!resizedKey.equals(baseline), "font scale still invalidates Canvas cache");
        }
        var perspective = new Matrix();
        perspective.m14(0.000001f);
        perspective.setTranslateX(13.25f);
        var expected = new Matrix(perspective);
        CanvasTextOrigin.forRasterization(perspective);
        check(perspective.equals(expected), "perspective rendering remains position-dependent");
    }

    private static void checkCanvasInjection() throws Exception {
        // Check the pinned dependency's bytecode, not only our mixin annotation.
        var node = new ClassNode();
        try (var in = TextMarqueeCheck.class.getResourceAsStream("/icyllis/arc3d/granite/GraniteDevice.class")) {
            if (in == null) throw new AssertionError("Missing GraniteDevice");
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        int sites = 0;
        for (var method : node.methods) {
            if (!method.name.equals("onDrawGlyphRunList")) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals("icyllis/arc3d/sketch/Matrix")
                        && call.name.equals("preTranslate") && call.desc.equals("(FF)V")) sites++;
            }
        }
        check(sites == 1, "exactly one Canvas translation injection site");
        var root = Path.of(System.getProperty("modernui.projectRoot"));
        for (String loader : new String[]{"fabric", "neoforge"}) {
            check(Files.readString(root.resolve(loader + "/src/main/resources/mixins.modernui-" + loader + ".json"))
                    .contains("\"MixinGraniteText\""), "Canvas mixin registered for " + loader);
        }
    }

    private static ModernBakedGlyph glyph(int x, int y, int width, int height) {
        var glyph = new ModernBakedGlyph();
        glyph.x = x;
        glyph.y = y;
        glyph.width = (short) width;
        glyph.height = (short) height;
        glyph.u1 = 0.25f;
        glyph.v1 = 0.25f;
        glyph.u2 = glyph.u1 + width / 256f;
        glyph.v2 = glyph.v1 + height / 256f;
        return glyph;
    }

    private static void close(float actual, float expected, String what) {
        check(Math.abs(actual - expected) < 0.0001f, what + ": " + actual + " != " + expected);
    }

    private static void check(boolean value, String what) {
        if (!value) throw new AssertionError(what);
        checks++;
    }

    private static final class Capture implements VertexConsumer {
        final ArrayList<float[]> vertices = new ArrayList<>();
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices.add(new float[]{x, y, 0, 0});
            return this;
        }
        public VertexConsumer setUv(float u, float v) {
            var vertex = vertices.getLast();
            vertex[2] = u;
            vertex[3] = v;
            return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setColor(int color) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setUv3(float u, float v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
        public VertexConsumer setLineWidth(float width) { return this; }
    }
}
