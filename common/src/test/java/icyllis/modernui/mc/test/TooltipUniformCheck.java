package icyllis.modernui.mc.test;

import net.minecraft.client.renderer.DynamicUniforms;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

/** Checks the shader layout against Minecraft's actual uniform serializer, without a GPU. */
public final class TooltipUniformCheck {
    public static void main(String[] args) throws Exception {
        var matrix = new Matrix4f().translation(120, 80, 0);
        var colors = new Matrix4f()
                .setColumn(0, new Vector4f(0.1f, 0.2f, 0.3f, 0.4f))
                .setColumn(1, new Vector4f(0.5f, 0.6f, 0.7f, 0.8f))
                .setColumn(2, new Vector4f(0.9f, 0.8f, 0.7f, 0.6f))
                .setColumn(3, new Vector4f(0.5f, 0.4f, 0.3f, -1.25f));
        var size = new Vector4f(60, 12, 4, 0.75f);
        var shadow = new Vector3f(0.3f, 0.2f, 0.85f);
        var data = ByteBuffer.allocateDirect(DynamicUniforms.TRANSFORM_UBO_SIZE)
                .order(ByteOrder.nativeOrder());
        // 1.21.9/10 append lineWidth; 1.21.11 removes it. Exercise the actual
        // serializer of the version on the runtime classpath in either case.
        DynamicUniforms.Transform transform;
        boolean hasLineWidth;
        try {
            var constructor = DynamicUniforms.Transform.class.getConstructor(
                    org.joml.Matrix4fc.class, org.joml.Vector4fc.class,
                    org.joml.Vector3fc.class, org.joml.Matrix4fc.class);
            transform = constructor.newInstance(matrix, size, shadow, colors);
            hasLineWidth = false;
        } catch (NoSuchMethodException olderVersion) {
            var constructor = DynamicUniforms.Transform.class.getConstructor(
                    org.joml.Matrix4fc.class, org.joml.Vector4fc.class,
                    org.joml.Vector3fc.class, org.joml.Matrix4fc.class, float.class);
            transform = constructor.newInstance(matrix, size, shadow, colors, 2.75f);
            hasLineWidth = true;
        }
        transform.write(data);
        if (DynamicUniforms.TRANSFORM_UBO_SIZE != (hasLineWidth ? 164 : 160)) {
            throw new AssertionError("Unexpected Minecraft transform buffer size");
        }
        if (hasLineWidth && data.getFloat(160) != 2.75f) {
            throw new AssertionError("Minecraft lineWidth serialization changed");
        }

        Map<String, float[]> expected = Map.of(
                "u_LocalMat", matrix.get(new float[16]),
                "u_PushData0", new float[]{size.x, size.y, size.z, size.w},
                "u_PushData1", new float[]{shadow.x, shadow.y, shadow.z},
                "u_PushData2", new float[]{0.1f, 0.2f, 0.3f, 0.4f},
                "u_PushData3", new float[]{0.5f, 0.6f, 0.7f, 0.8f},
                "u_PushData4", new float[]{0.9f, 0.8f, 0.7f, 0.6f},
                "u_PushData5", new float[]{0.5f, 0.4f, 0.3f, -1.25f});
        for (String stage : new String[]{"vsh", "fsh"}) {
            checkShader(stage, data, expected);
        }
        System.out.println("Tooltip shader layout matches Minecraft's actual "
                + DynamicUniforms.TRANSFORM_UBO_SIZE + "-byte uniform serializer");
    }

    private static void checkShader(String stage, ByteBuffer data, Map<String, float[]> expected) throws Exception {
        var block = Pattern.compile("uniform\\s+ModernTooltip\\s*\\{([^}]+)}", Pattern.DOTALL)
                .matcher(resource("core/rendertype_modern_tooltip." + stage));
        if (!block.find()) throw new AssertionError("Missing ModernTooltip block in " + stage);
        var fields = Pattern.compile("(mat4|vec[34])\\s+(u_\\w+)\\s*;")
                .matcher(block.group(1));
        int offset = 0;
        int count = 0;
        while (fields.find()) {
            offset = (offset + 15) & ~15; // std140 matrix/vector base alignment
            float[] values = expected.get(fields.group(2));
            if (values == null) throw new AssertionError("Unexpected uniform " + fields.group(2));
            for (int i = 0; i < values.length; i++) {
                float actual = data.getFloat(offset + i * Float.BYTES);
                if (actual != values[i]) {
                    throw new AssertionError(fields.group(2) + "[" + i + "]: expected "
                            + values[i] + ", got " + actual);
                }
            }
            offset += switch (fields.group(1)) {
                case "mat4" -> 64;
                case "vec4" -> 16;
                default -> 12;
            };
            count++;
        }
        if (count != expected.size() || offset != 160) {
            throw new AssertionError("Incomplete tooltip uniform layout");
        }
    }

    private static String resource(String path) throws Exception {
        try (var stream = TooltipUniformCheck.class.getResourceAsStream(
                "/assets/modernui/shaders/" + path)) {
            if (stream == null) throw new AssertionError("Missing shader " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
