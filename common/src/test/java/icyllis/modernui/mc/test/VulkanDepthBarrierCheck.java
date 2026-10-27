package icyllis.modernui.mc.test;

import icyllis.modernui.mc.VulkanBarrierCompat;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import static org.lwjgl.vulkan.VK10.*;

/** Checks the production policy and optional actual VulkanMod release JAR injection points. */
public final class VulkanDepthBarrierCheck {
    public static void main(String[] args) throws Exception {
        int both = VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
        for (int aspect : new int[]{VK_IMAGE_ASPECT_DEPTH_BIT, VK_IMAGE_ASPECT_STENCIL_BIT,
                VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT}) {
            for (int access : new int[]{VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT,
                    VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT}) {
                expect(both, VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT, access, aspect);
                expect(both, VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT, access, aspect);
                expect(both | VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, access, aspect);
            }
        }
        for (int access : new int[]{0, VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT}) {
            expect(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    access, VK_IMAGE_ASPECT_DEPTH_BIT);
        }
        expect(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT, VK_IMAGE_ASPECT_COLOR_BIT);

        ClassNode mixin = new ClassNode();
        try (var stream = VulkanDepthBarrierCheck.class.getResourceAsStream(
                "/icyllis/modernui/mc/mixin/MixinVulkanImage.class")) {
            if (stream == null) throw new AssertionError("Missing production mixin");
            new ClassReader(stream).accept(mixin, 0);
        }
        MethodNode handler = mixin.methods.stream().filter(m -> m.name.startsWith("modernui$")).findFirst().orElseThrow();
        AnnotationNode redirect = handler.visibleAnnotations.stream()
                .filter(a -> a.desc.endsWith("/Redirect;")).findFirst().orElseThrow();
        var values = values(redirect);
        if (!Integer.valueOf(1).equals(values.get("require"))) throw new AssertionError("Injection must be required");
        String method = ((List<?>) values.get("method")).getFirst().toString();
        String target = values((AnnotationNode) values.get("at")).get("target").toString();
        int policyCalls = 0;
        for (AbstractInsnNode instruction : handler.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("icyllis/modernui/mc/VulkanBarrierCompat")
                    && call.name.equals("depthStencilStages")) policyCalls++;
        }
        if (policyCalls != 2) throw new AssertionError("Production redirect must cover source and destination independently");
        for (String jar : args) verifyJar(Path.of(jar), method, target);
        System.out.println("Depth/stencil barrier strategy passed; " + args.length
                + " actual VulkanMod release injection points verified");
    }

    private static void expect(int expected, int stage, int access, int aspect) {
        int actual = VulkanBarrierCompat.depthStencilStages(stage, access, aspect);
        if (actual != expected) throw new AssertionError("Stage mask: expected " + expected + ", got " + actual);
    }

    private static void verifyJar(Path path, String method, String target) throws Exception {
        ClassNode image = new ClassNode();
        try (JarFile jar = new JarFile(path.toFile())) {
            var entry = jar.getJarEntry("net/vulkanmod/vulkan/texture/VulkanImage.class");
            if (entry == null) throw new AssertionError("Missing VulkanImage: " + path);
            try (var input = jar.getInputStream(entry)) { new ClassReader(input).accept(image, 0); }
        }
        int split = method.indexOf('(');
        var matching = image.methods.stream().filter(m -> m.name.equals(method.substring(0, split))
                && m.desc.equals(method.substring(split))).toList();
        if (matching.size() != 1) throw new AssertionError("Unsupported transitionLayout descriptor: " + path);
        int calls = 0;
        for (AbstractInsnNode instruction : matching.getFirst().instructions) {
            if (instruction instanceof MethodInsnNode call
                    && target.equals("L" + call.owner + ";" + call.name + call.desc)) calls++;
        }
        if (calls != 1) throw new AssertionError("Expected exactly one barrier call, got " + calls + ": " + path);
    }

    private static Map<String, Object> values(AnnotationNode annotation) {
        var result = new HashMap<String, Object>();
        for (int i = 0; i < annotation.values.size(); i += 2) {
            result.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        }
        return result;
    }
}
