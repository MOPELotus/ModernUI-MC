package icyllis.modernui.mc.test;

import net.minecraft.client.renderer.GlobalSettingsUniform;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/** Executes the production frame-start condition without creating a device or window. */
public final class VulkanGlobalUniformCheck {
    public static final int UBO_SIZE = GlobalSettingsUniform.UBO_SIZE;
    private static FakeRenderer renderer;

    public static final class FakeBuffer {
        public final String name;
        private final long size;
        FakeBuffer(String name, long size) { this.name = name; this.size = size; }
        public long getBufferSize() { return size; }
    }

    public static final class FakeRenderer {
        boolean recording, fail;
        int starts;
        Runnable nested;
        public void beginFrame() {
            starts++;
            if (nested != null) nested.run();
            if (fail) throw new IllegalStateException("Test acquire failure");
            recording = true;
        }
    }

    public static FakeRenderer getInstance() { return renderer; }
    public static boolean isRecording() {
        if (renderer == null) throw new AssertionError("Recording queried before renderer initialization");
        return renderer.recording;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || UBO_SIZE != 56) throw new AssertionError("Pinned Minecraft/Reforged API required");
        var mixin = new ClassNode();
        try (var input = VulkanGlobalUniformCheck.class.getResourceAsStream(
                "/icyllis/modernui/mc/mixin/MixinVulkanCommandEncoder.class")) {
            if (input == null) throw new AssertionError("Missing production mixin");
            new ClassReader(input).accept(mixin, 0);
        }
        MethodNode handler = mixin.methods.stream().filter(m -> m.name.startsWith("modernui$")).findFirst().orElseThrow();
        var inject = handler.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        if (!Integer.valueOf(1).equals(value(inject, "require"))
                || !"HEAD".equals(value((AnnotationNode) ((List<?>) value(inject, "at")).getFirst(), "value"))) {
            throw new AssertionError("Required early frame-start injection changed");
        }
        String target = ((List<?>) value(inject, "method")).getFirst().toString();
        for (String jar : args) verifyJar(Path.of(jar), target);

        String original = mixin.name;
        mixin.name += "$CpuProbe";
        mixin.access &= ~Opcodes.ACC_ABSTRACT;
        mixin.visibleAnnotations = null;
        mixin.invisibleAnnotations = null;
        String buffer = "net/vulkanmod/vulkan/memory/buffer/Buffer";
        String fakeBuffer = Type.getInternalName(FakeBuffer.class);
        String originalRenderer = "net/vulkanmod/vulkan/Renderer";
        String fakeRenderer = Type.getInternalName(FakeRenderer.class);
        for (MethodNode method : mixin.methods) {
            method.desc = method.desc.replace(buffer, fakeBuffer).replace(originalRenderer, fakeRenderer);
            method.visibleAnnotations = null;
            method.invisibleAnnotations = null;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field) {
                    if (field.owner.equals(original)) field.owner = mixin.name;
                    if (field.owner.equals(buffer)) field.owner = fakeBuffer;
                    if (field.owner.equals("net/minecraft/client/renderer/GlobalSettingsUniform")) {
                        field.owner = Type.getInternalName(VulkanGlobalUniformCheck.class);
                    }
                }
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals(original)) call.owner = mixin.name;
                    if (call.owner.equals(buffer)) call.owner = fakeBuffer;
                    if (call.owner.equals(originalRenderer)) {
                        call.owner = call.getOpcode() == Opcodes.INVOKESTATIC
                                ? Type.getInternalName(VulkanGlobalUniformCheck.class) : fakeRenderer;
                    }
                    call.desc = call.desc.replace(originalRenderer, fakeRenderer).replace(buffer, fakeBuffer);
                }
                if (instruction instanceof FrameNode frame) {
                    if (frame.local != null) frame.local.replaceAll(v -> original.equals(v) ? mixin.name
                            : originalRenderer.equals(v) ? fakeRenderer : buffer.equals(v) ? fakeBuffer : v);
                    if (frame.stack != null) frame.stack.replaceAll(v -> original.equals(v) ? mixin.name
                            : originalRenderer.equals(v) ? fakeRenderer : buffer.equals(v) ? fakeBuffer : v);
                }
            }
        }
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        mixin.accept(writer);
        var loader = new ClassLoader(VulkanGlobalUniformCheck.class.getClassLoader()) {
            Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
        };
        Class<?> type = loader.define(writer.toByteArray());
        Object probe = type.getConstructor().newInstance();
        Method invoke = type.getDeclaredMethod(handler.name, FakeBuffer.class, long.class, ByteBuffer.class, CallbackInfo.class);
        invoke.setAccessible(true);
        var global = new FakeBuffer("Global Settings UBO", UBO_SIZE);
        renderer = null;
        run(invoke, probe, global, 56); // Initialization guard must precede isRecording().
        renderer = new FakeRenderer();
        run(invoke, probe, new FakeBuffer("Other uniform", UBO_SIZE), 56);
        run(invoke, probe, new FakeBuffer(null, UBO_SIZE), 56);
        run(invoke, probe, new FakeBuffer("Global Settings UBO", UBO_SIZE + 4), 56);
        run(invoke, probe, global, 0);
        if (renderer.starts != 0) throw new AssertionError("Unrelated/empty upload started a frame");
        renderer.recording = true;
        run(invoke, probe, global, 56);
        if (renderer.starts != 0) throw new AssertionError("Already-recording frame restarted");
        renderer.recording = false;
        renderer.nested = () -> {
            try { run(invoke, probe, global, 56); } catch (Exception e) { throw new AssertionError(e); }
        };
        run(invoke, probe, global, 56);
        if (renderer.starts != 1 || !renderer.recording) throw new AssertionError("Reentrant update restarted frame");
        renderer.nested = null;
        renderer.recording = false;
        renderer.fail = true;
        try {
            run(invoke, probe, global, 56);
            throw new AssertionError("Failed acquisition was swallowed");
        } catch (InvocationTargetException expected) {
            if (!"Test acquire failure".equals(expected.getCause().getMessage())) throw expected;
        }
        renderer.fail = false;
        run(invoke, probe, global, 56);
        if (renderer.starts != 3 || !renderer.recording) throw new AssertionError("Frame-start guard was not restored after failure");
        System.out.println("Reforged global uniform frame routing, initialization, reentry and failure guards passed");
    }

    private static void run(Method method, Object probe, FakeBuffer buffer, int bytes) throws Exception {
        method.invoke(probe, buffer, 0L, ByteBuffer.allocate(bytes), new CallbackInfo("upload", false));
    }

    private static void verifyJar(Path path, String target) throws Exception {
        var encoder = new ClassNode();
        try (var jar = new JarFile(path.toFile()); var input = jar.getInputStream(jar.getJarEntry(
                "net/vulkanmod/render/engine/VkCommandEncoder.class"))) {
            new ClassReader(input).accept(encoder, 0);
        }
        int split = target.indexOf('(');
        var methods = encoder.methods.stream().filter(m -> m.name.equals(target.substring(0, split))
                && m.desc.equals(target.substring(split))).toList();
        if (methods.size() != 1) throw new AssertionError("Unsupported Reforged uploadBufferData signature");
        int recording = -1, graphics = -1, transfer = -1, position = 0;
        for (AbstractInsnNode instruction : methods.getFirst().instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("net/vulkanmod/vulkan/Renderer") && call.name.equals("isRecording")) recording = position;
                if (call.name.equals("recordBufferCopy")) graphics = position;
                if (call.name.equals("getTransferCb")) transfer = position;
            }
            position++;
        }
        if (recording < 0 || graphics <= recording || transfer <= graphics) {
            throw new AssertionError("Reforged recording/graphics/transfer upload routing changed");
        }
    }

    private static Object value(AnnotationNode node, String key) {
        for (int i = 0; i < node.values.size(); i += 2) if (node.values.get(i).equals(key)) return node.values.get(i + 1);
        throw new AssertionError("Missing annotation value " + key);
    }
}
