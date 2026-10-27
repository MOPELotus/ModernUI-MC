package icyllis.modernui.mc.test;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.lwjgl.vulkan.VK10.*;

/** Runs the production handler and each release's original same-layout early return on native structs. */
public final class VulkanImageUploadBarrierCheck {
    private static final String OWNER = "net/vulkanmod/vulkan/texture/VulkanImage";
    private static int captured;
    private static int expectedAspect;
    private static int expectedMips;
    private static int expectedLayers;

    public static void capture(VkCommandBuffer commandBuffer, int src, int dst, int flags,
                               VkMemoryBarrier.Buffer memory, VkBufferMemoryBarrier.Buffer buffers,
                               VkImageMemoryBarrier.Buffer images) {
        if (commandBuffer != null || src != VK_PIPELINE_STAGE_TRANSFER_BIT
                || dst != VK_PIPELINE_STAGE_TRANSFER_BIT || flags != 0 || memory != null || buffers != null
                || images.position() != 0 || images.remaining() != 1) {
            throw new AssertionError("Unexpected native barrier arguments");
        }
        var barrier = images.get(0);
        var range = barrier.subresourceRange();
        if (barrier.sType() != VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER || barrier.pNext() != 0
                || barrier.image() != 0x12345678L
                || barrier.oldLayout() != VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
                || barrier.newLayout() != VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
                || barrier.srcAccessMask() != VK_ACCESS_TRANSFER_WRITE_BIT
                || barrier.dstAccessMask() != VK_ACCESS_TRANSFER_WRITE_BIT
                || barrier.srcQueueFamilyIndex() != VK_QUEUE_FAMILY_IGNORED
                || barrier.dstQueueFamilyIndex() != VK_QUEUE_FAMILY_IGNORED
                || range.aspectMask() != expectedAspect || range.baseMipLevel() != 0
                || range.levelCount() != expectedMips || range.baseArrayLayer() != 0
                || range.layerCount() != expectedLayers) {
            throw new AssertionError("Production handler lost image identity, write dependency, or full subresources");
        }
        captured++;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new AssertionError("Actual VulkanMod release JAR required");
        for (String arg : args) verify(Path.of(arg));
        System.out.println("Image upload WAW: actual handler/native arguments and " + args.length + " release negative controls passed");
    }

    private static void verify(Path path) throws Exception {
        var original = new ClassNode();
        try (var jar = new JarFile(path.toFile()); var stream = jar.getInputStream(jar.getJarEntry(OWNER + ".class"))) {
            new ClassReader(stream).accept(original, 0);
        }
        var mixin = new ClassNode();
        try (var stream = VulkanImageUploadBarrierCheck.class.getResourceAsStream(
                "/icyllis/modernui/mc/mixin/MixinVulkanImageUpload.class")) {
            if (stream == null) throw new AssertionError("Missing production handler");
            new ClassReader(stream).accept(mixin, 0);
        }
        var handler = mixin.methods.stream().filter(m -> m.name.startsWith("modernui$")).findFirst().orElseThrow();
        var inject = handler.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        String target = ((List<?>) value(inject, "method")).getFirst().toString();
        if (!Integer.valueOf(1).equals(value(inject, "require"))
                || !Integer.valueOf(1).equals(value(inject, "allow"))
                || !"HEAD".equals(value((AnnotationNode) ((List<?>) value(inject, "at")).getFirst(), "value"))) {
            throw new AssertionError("Required HEAD injection changed");
        }
        var transfer = original.methods.stream().filter(m -> target.equals(m.name + m.desc)).toList();
        if (transfer.size() != 1) throw new AssertionError("Missing exact upload transition in " + path);
        int delegates = 0;
        for (var instruction : transfer.getFirst().instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(OWNER)
                    && call.name.equals("transitionImageLayout")) delegates++;
        }
        if (delegates != 1) throw new AssertionError("Upload transition no longer delegates once");
        // Prove the upload invokes the transition before copying, using actual release instructions.
        var upload = original.methods.stream().filter(m -> m.name.equals("uploadSubTextureAsync")
                && m.desc.equals("(IIIIIIIIIJ)V")).findFirst().orElseThrow();
        int transitions = 0, copies = 0;
        for (var instruction : upload.instructions) if (instruction instanceof MethodInsnNode call) {
            if (call.owner.equals(OWNER) && call.name.equals("transferDstLayout")) transitions++;
            if (call.name.equals("copyBufferToImageCmd")) {
                if (transitions != 1) throw new AssertionError("Copy occurs outside guarded transition order");
                copies++;
            }
        }
        if (transitions != 1 || copies != 1) throw new AssertionError("Upload call counts changed");
        var earlyReturn = original.methods.stream().filter(m -> m.name.equals("transitionImageLayout")
                && (m.access & Opcodes.ACC_STATIC) != 0).findFirst().orElseThrow();
        var probe = new ClassNode();
        probe.version = mixin.version;
        probe.access = Opcodes.ACC_PUBLIC;
        probe.name = "icyllis/modernui/mc/test/ImageUploadBarrierProbe";
        probe.superName = "java/lang/Object";
        for (String field : new String[]{"currentLayout", "id", "aspect", "mipLevels", "arrayLayers"}) {
            var actual = original.fields.stream().filter(f -> f.name.equals(field)).findFirst().orElseThrow();
            var shadow = mixin.fields.stream().filter(f -> f.name.equals(field)).findFirst().orElseThrow();
            if (!actual.desc.equals(shadow.desc)) throw new AssertionError("Shadow field descriptor changed");
            probe.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, field, actual.desc, null, null));
        }
        var ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));
        probe.methods.add(ctor);
        for (var method : new MethodNode[]{handler, earlyReturn}) {
            method.visibleAnnotations = null;
            method.invisibleAnnotations = null;
            method.desc = method.desc.replace(OWNER, probe.name);
            for (var instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && (field.owner.equals(OWNER) || field.owner.equals(mixin.name))) {
                    field.owner = probe.name;
                }
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals(OWNER)) call.owner = probe.name;
                    call.desc = call.desc.replace(OWNER, probe.name);
                    if (call.owner.equals("org/lwjgl/vulkan/VK10") && call.name.equals("vkCmdPipelineBarrier")) {
                        call.owner = Type.getInternalName(VulkanImageUploadBarrierCheck.class);
                        call.name = "capture";
                    }
                }
                if (instruction instanceof FrameNode) method.instructions.remove(instruction);
            }
            probe.methods.add(method);
        }
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        probe.accept(writer);
        var loader = new ClassLoader(VulkanImageUploadBarrierCheck.class.getClassLoader()) {
            Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
        };
        var type = loader.define(writer.toByteArray());
        var instance = type.getConstructor().newInstance();
        type.getField("id").setLong(instance, 0x12345678L);
        var invoke = type.getDeclaredMethod(handler.name, MemoryStack.class, VkCommandBuffer.class, CallbackInfo.class);
        invoke.setAccessible(true);
        var originalTransition = type.getDeclaredMethod(earlyReturn.name, MemoryStack.class, VkCommandBuffer.class,
                type, int.class);
        try (var stack = MemoryStack.stackPush()) {
            type.getField("currentLayout").setInt(instance, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            captured = 0;
            originalTransition.invoke(null, stack, null, instance, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            if (captured != 0) throw new AssertionError("Original same-layout negative control changed");
            for (int[] range : new int[][]{{VK_IMAGE_ASPECT_COLOR_BIT, 5, 1}, {VK_IMAGE_ASPECT_COLOR_BIT, 1, 3}}) {
                expectedAspect = range[0]; expectedMips = range[1]; expectedLayers = range[2];
                type.getField("aspect").setInt(instance, expectedAspect);
                type.getField("mipLevels").setInt(instance, expectedMips);
                type.getField("arrayLayers").setInt(instance, expectedLayers);
                int before = captured;
                invoke.invoke(instance, stack, null, null);
                originalTransition.invoke(null, stack, null, instance, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
                if (captured != before + 1 || type.getField("currentLayout").getInt(instance) != 7) {
                    throw new AssertionError("Repeated upload must add exactly one barrier and preserve layout");
                }
            }
            for (int layout : new int[]{0, 2, 3, 5, 6, 1000001002}) {
                type.getField("currentLayout").setInt(instance, layout);
                int before = captured;
                invoke.invoke(instance, stack, null, null);
                if (captured != before || type.getField("currentLayout").getInt(instance) != layout) {
                    throw new AssertionError("Handler changed a different layout path");
                }
            }
        }
        System.out.println("Verified " + path.getFileName());
    }

    private static Object value(AnnotationNode node, String key) {
        for (int i = 0; i < node.values.size(); i += 2) if (node.values.get(i).equals(key)) return node.values.get(i + 1);
        throw new AssertionError("Missing annotation value " + key);
    }
}
