package icyllis.modernui.mc.test;

import icyllis.modernui.mc.VulkanGpuDeviceOwnership;
import icyllis.modernui.mc.mixin.AccessVulkanGlFramebuffer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.jar.JarFile;

/** Runs the production encoder hook and each release's actual GL owner cleanup. */
public final class VulkanEncoderOwnershipCheck {
    public static final Map<Integer, Object> FRAMEBUFFERS = new HashMap<>();
    public static Object getFramebuffer(int id) { return FRAMEBUFFERS.get(id); }
    public static final class Framebuffer {
        public int closes;
        public boolean freedAttachments;
        public void cleanUp(boolean freeAttachments) { closes++; freedAttachments |= freeAttachments; }
    }
    public static final class RenderPass {
        public int closes;
        public void cleanUp() { closes++; }
    }
    private static final String HERE = Type.getInternalName(VulkanEncoderOwnershipCheck.class);
    private static final String GL = "net/vulkanmod/gl/VkGlFramebuffer";
    private static final String ACCESS = Type.getInternalName(AccessVulkanGlFramebuffer.class);
    private static final class Loader extends ClassLoader {
        Loader() { super(VulkanEncoderOwnershipCheck.class.getClassLoader()); }
        Class<?> define(ClassNode node) {
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES); node.accept(writer);
            var bytes = writer.toByteArray(); return defineClass(node.name.replace('/', '.'), bytes, 0, bytes.length);
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new AssertionError("Actual VulkanMod release JAR required");
        ClassNode mixin;
        try (var input = VulkanEncoderOwnershipCheck.class.getResourceAsStream(
                "/icyllis/modernui/mc/mixin/MixinVulkanCommandEncoderOwnership.class")) {
            mixin = new ClassNode(); new ClassReader(Objects.requireNonNull(input)).accept(mixin, 0);
        }
        var ownHandler = mixin.methods.stream().filter(m -> m.name.equals("modernui$ownFramebuffer")).findFirst().orElseThrow();
        var inject = ownHandler.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked") var selectors = (List<String>) inject.values.get(inject.values.indexOf("method") + 1);
        check(selectors.equals(List.of("<init>(Lnet/vulkanmod/render/engine/VkGpuDevice;)V")), "Production constructor selector changed");
        int release = 0;
        for (String path : args) {
            try (var jar = new JarFile(path)) {
                var encoder = entry(jar, "net/vulkanmod/render/engine/VkCommandEncoder");
                check(encoder.fields.stream().anyMatch(f -> f.name.equals("framebufferId") && f.desc.equals("I")), "Encoder field changed");
                var constructor = encoder.methods.stream().filter(m -> m.name.equals("<init>") &&
                        m.desc.equals("(Lnet/vulkanmod/render/engine/VkGpuDevice;)V")).findFirst().orElseThrow();
                check(Arrays.stream(constructor.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode m &&
                        m.owner.equals(GL) && m.name.equals("genFramebufferId") && m.desc.equals("()I")), "Encoder no longer creates scratch FBO");
                check(Arrays.stream(constructor.instructions.toArray()).filter(i -> i.getOpcode() == Opcodes.RETURN).count() == 1, "Constructor return paths changed");
                var originalOwner = entry(jar, GL);
                var clean = originalOwner.methods.stream().filter(m -> m.name.equals("cleanUp") && m.desc.equals("(Z)V")).findFirst().orElseThrow();
                var loader = new Loader();
                String ownerName = HERE + "$Owner" + release;
                var ownerNode = base(ownerName);
                ownerNode.interfaces.add(ACCESS);
                ownerNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "framebuffer", "L" + HERE + "$Framebuffer;", null, null));
                ownerNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "renderPass", "L" + HERE + "$RenderPass;", null, null));
                var holder = base(GL); holder.methods.clear(); holder.methods.add(clean);
                var mapped = new ClassNode(); holder.accept(new ClassRemapper(mapped, new SimpleRemapper(Map.of(
                        GL, ownerName,
                        "net/vulkanmod/vulkan/framebuffer/Framebuffer", HERE + "$Framebuffer",
                        "net/vulkanmod/vulkan/framebuffer/RenderPass", HERE + "$RenderPass"))));
                ownerNode.methods.add(mapped.methods.getFirst());
                var invoke = new MethodNode(Opcodes.ACC_PUBLIC, "modernui$cleanUp", "(Z)V", null, null);
                invoke.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); invoke.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
                invoke.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ownerName, "cleanUp", "(Z)V", false));
                invoke.instructions.add(new InsnNode(Opcodes.RETURN)); ownerNode.methods.add(invoke);
                var ownerClass = loader.define(ownerNode);
                Object owner = ownerClass.getConstructor().newInstance();
                var framebuffer = new Framebuffer(); var renderPass = new RenderPass();
                ownerClass.getField("framebuffer").set(owner, framebuffer); ownerClass.getField("renderPass").set(owner, renderPass);
                FRAMEBUFFERS.put(71, owner);
                String encoderName = HERE + "$Encoder" + release;
                var handler = new ClassNode(); mixin.accept(new ClassRemapper(handler, new SimpleRemapper(Map.of(mixin.name, encoderName))));
                handler.access = Opcodes.ACC_PUBLIC; handler.visibleAnnotations = handler.invisibleAnnotations = null;
                handler.methods.removeIf(m -> m.name.equals("<init>"));
                handler.methods.add(base(encoderName).methods.getFirst());
                for (var method : handler.methods) {
                    method.visibleAnnotations = method.invisibleAnnotations = null;
                    for (var instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode call && call.owner.equals(GL) && call.name.equals("getFramebuffer")) {
                            call.owner = HERE; call.desc = "(I)Ljava/lang/Object;";
                        }
                    }
                }
                var handlerClass = loader.define(handler); Object encoderInstance = handlerClass.getConstructor().newInstance();
                var id = handlerClass.getDeclaredField("framebufferId"); id.setAccessible(true); id.setInt(encoderInstance, 71);
                Object device = new Object();
                var own = handlerClass.getDeclaredMethod("modernui$ownFramebuffer", Object.class, CallbackInfo.class);
                own.setAccessible(true); own.invoke(encoderInstance, device, new CallbackInfo("init", false));
                // The real production tracker invokes the unchanged production callback,
                // which then executes the actual release-JAR Java owner cleanup body.
                VulkanGpuDeviceOwnership.closeRemaining(new Object());
                check(framebuffer.closes == 0, "Other device closed encoder");
                VulkanGpuDeviceOwnership.closeRemaining(device);
                check(framebuffer.closes == 1 && !framebuffer.freedAttachments && renderPass.closes == 1,
                        "Encoder failed to close only its FBO/pass, or freed borrowed attachments");
                ((AccessVulkanGlFramebuffer) owner).modernui$cleanUp(false);
                VulkanGpuDeviceOwnership.closeRemaining(device);
                check(framebuffer.closes == 1 && renderPass.closes == 1, "Owner cleanup is not idempotent");
                check(ownerClass.getField("framebuffer").get(owner) == null && ownerClass.getField("renderPass").get(owner) == null,
                        "Owner references were not cleared");
                FRAMEBUFFERS.clear();
                Object missingEncoder = handlerClass.getConstructor().newInstance();
                id.setInt(missingEncoder, 999);
                own.invoke(missingEncoder, device, new CallbackInfo("init", false));
                VulkanGpuDeviceOwnership.closeRemaining(device); // Already removed or never initialized owner.
                release++;
                System.out.println(path + ": production encoder callback and actual scratch FBO/pass cleanup passed");
            }
        }
        System.out.println("Encoder ownership passed for " + release + " actual releases");
    }
    private static ClassNode base(String name) {
        var node = new ClassNode(); node.version = Opcodes.V21; node.access = Opcodes.ACC_PUBLIC; node.name = name; node.superName = "java/lang/Object";
        var init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)); init.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(init); return node;
    }
    private static ClassNode entry(JarFile jar, String name) throws Exception {
        try (var input = jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(name + ".class")))) {
            var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
