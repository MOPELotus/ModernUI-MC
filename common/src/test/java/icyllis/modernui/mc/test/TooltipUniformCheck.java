package icyllis.modernui.mc.test;

import net.minecraft.client.renderer.DynamicGpuData;
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
        var data = ByteBuffer.allocateDirect(DynamicGpuData.TRANSFORM_UBO_SIZE)
                .order(ByteOrder.nativeOrder());
        new DynamicGpuData.Transform(matrix, size, shadow, colors).write(data);

        Map<String, float[]> expected = Map.of(
                "u_LocalMat", matrix.get(new float[16]),
                "u_PushData0", new float[]{size.x, size.y, size.z, size.w},
                "u_PushData1", new float[]{shadow.x, shadow.y, shadow.z},
                "u_PushData2", new float[]{0.1f, 0.2f, 0.3f, 0.4f},
                "u_PushData3", new float[]{0.5f, 0.6f, 0.7f, 0.8f},
                "u_PushData4", new float[]{0.9f, 0.8f, 0.7f, 0.6f},
                "u_PushData5", new float[]{0.5f, 0.4f, 0.3f, -1.25f});
        String include = "#include <modernui:tooltip_uniforms.glsl>";
        for (String stage : new String[]{"vsh", "fsh"}) {
            if (!resource("core/rendertype_modern_tooltip." + stage).contains(include)) {
                throw new AssertionError(stage + " must use the shared tooltip uniform layout");
            }
        }
        var fields = Pattern.compile("(mat4|vec[34])\\s+(u_\\w+)\\s*;")
                .matcher(resource("include/tooltip_uniforms.glsl"));
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
        if (count != expected.size() || offset != DynamicGpuData.TRANSFORM_UBO_SIZE) {
            throw new AssertionError("Incomplete tooltip uniform layout");
        }
        System.out.println("Tooltip shader layout matches Minecraft's uniform serializer");
    }

    private static String resource(String path) throws Exception {
        try (var stream = TooltipUniformCheck.class.getResourceAsStream(
                "/assets/modernui/shaders/" + path)) {
            if (stream == null) throw new AssertionError("Missing shader " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
