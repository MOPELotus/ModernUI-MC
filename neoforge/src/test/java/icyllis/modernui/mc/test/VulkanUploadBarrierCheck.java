package icyllis.modernui.mc.test;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.lwjgl.vulkan.VK10.*;

/** Checks the real Reforged injection site and the production handler's native barrier arguments. */
public final class VulkanUploadBarrierCheck {
    private static boolean captured;

    public static void capture(VkCommandBuffer commandBuffer, int srcStages, int dstStages, int flags,
                               VkMemoryBarrier.Buffer memory, VkBufferMemoryBarrier.Buffer buffers,
                               VkImageMemoryBarrier.Buffer images) {
        if (srcStages != VK_PIPELINE_STAGE_TRANSFER_BIT || dstStages != VK_PIPELINE_STAGE_TRANSFER_BIT
                || flags != 0 || memory != null || images != null || buffers.position() != 1 || buffers.remaining() != 1) {
            throw new AssertionError("Production handler changed unrelated barrier arguments");
        }
        var barrier = buffers.get(1);
        if (barrier.srcQueueFamilyIndex() != VK_QUEUE_FAMILY_IGNORED
                || barrier.dstQueueFamilyIndex() != VK_QUEUE_FAMILY_IGNORED
                || barrier.buffer() != 123 || barrier.offset() != 16 || barrier.size() != 64
                || barrier.srcAccessMask() != VK_ACCESS_TRANSFER_WRITE_BIT
                || barrier.dstAccessMask() != VK_ACCESS_TRANSFER_READ_BIT) {
            throw new AssertionError("Native barrier does not preserve the access dependency with ignored ownership");
        }
        captured = true;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new AssertionError("Actual Reforged JAR required");
        var mixin = new ClassNode();
        try (var input = VulkanUploadBarrierCheck.class.getResourceAsStream(
                "/icyllis/modernui/mc/mixin/MixinVulkanUploadManager.class")) {
            if (input == null) throw new AssertionError("Missing production mixin");
            new ClassReader(input).accept(mixin, 0);
        }
        MethodNode handler = mixin.methods.stream().filter(m -> m.name.startsWith("modernui$")).findFirst().orElseThrow();
        var redirect = handler.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Redirect;")).findFirst().orElseThrow();
        String method = ((List<?>) value(redirect, "method")).getFirst().toString();
        String target = value((AnnotationNode) value(redirect, "at"), "target").toString();
        if (!Integer.valueOf(1).equals(value(redirect, "require"))
                || !Integer.valueOf(1).equals(value(redirect, "allow"))) throw new AssertionError("Required call count changed");
        for (String jar : args) verifyJar(Path.of(jar), method, target);

        // Transplant the production handler unchanged, substituting only the GPU call.
        for (AbstractInsnNode instruction : handler.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/vulkan/VK10")
                    && call.name.equals("vkCmdPipelineBarrier")) {
                call.owner = "icyllis/modernui/mc/test/VulkanUploadBarrierCheck";
                call.name = "capture";
            }
        }
        handler.visibleAnnotations = null;
        handler.invisibleAnnotations = null;
        var probe = new ClassNode();
        probe.version = mixin.version;
        probe.access = org.objectweb.asm.Opcodes.ACC_PUBLIC;
        probe.name = "icyllis/modernui/mc/test/UploadBarrierProbe";
        probe.superName = "java/lang/Object";
        probe.methods.add(handler);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        probe.accept(writer);
        var loader = new ClassLoader(VulkanUploadBarrierCheck.class.getClassLoader()) {
            Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
        };
        var type = loader.define(writer.toByteArray());
        var invoke = type.getDeclaredMethod(handler.name, VkCommandBuffer.class, int.class, int.class, int.class,
                VkMemoryBarrier.Buffer.class, VkBufferMemoryBarrier.Buffer.class, VkImageMemoryBarrier.Buffer.class);
        invoke.setAccessible(true);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var barriers = VkBufferMemoryBarrier.calloc(3, stack);
            barriers.get(0).srcQueueFamilyIndex(7).dstQueueFamilyIndex(8);
            barriers.get(1).sType$Default().buffer(123).offset(16).size(64)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            barriers.get(2).srcQueueFamilyIndex(9).dstQueueFamilyIndex(10);
            var before = barriers.get(0);
            var after = barriers.get(2);
            // calloc reproduces the original invalid zero-valued ownership fields.
            if (barriers.get(1).srcQueueFamilyIndex() != 0 || barriers.get(1).dstQueueFamilyIndex() != 0) {
                throw new AssertionError("Original zero-family negative control changed");
            }
            barriers.position(1).limit(2);
            invoke.invoke(null, null, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, barriers, null);
            if (!captured || before.srcQueueFamilyIndex() != 7
                    || after.srcQueueFamilyIndex() != 9 || barriers.position() != 1) {
                throw new AssertionError("Handler touched barriers outside the submitted range");
            }
        }
        System.out.println("Reforged upload barrier ownership and actual release injection site passed");
    }

    private static void verifyJar(Path path, String method, String target) throws Exception {
        var node = new ClassNode();
        try (var jar = new JarFile(path.toFile()); var input = jar.getInputStream(jar.getJarEntry(
                "net/vulkanmod/render/chunk/buffer/UploadManager.class"))) {
            new ClassReader(input).accept(node, 0);
        }
        int split = method.indexOf('(');
        var selected = node.methods.stream().filter(m -> m.name.equals(method.substring(0, split))
                && m.desc.equals(method.substring(split))).toList();
        if (selected.size() != 1) throw new AssertionError("Unsupported Reforged copyBuffer target");
        int calls = 0;
        for (AbstractInsnNode instruction : selected.getFirst().instructions) {
            if (instruction instanceof MethodInsnNode call
                    && target.equals("L" + call.owner + ";" + call.name + call.desc)) calls++;
        }
        if (calls != 1) throw new AssertionError("Reforged barrier call count changed: " + calls);
    }

    private static Object value(AnnotationNode node, String key) {
        for (int i = 0; i < node.values.size(); i += 2) if (node.values.get(i).equals(key)) return node.values.get(i + 1);
        throw new AssertionError("Missing annotation value " + key);
    }
}
