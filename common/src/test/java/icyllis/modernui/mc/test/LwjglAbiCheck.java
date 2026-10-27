package icyllis.modernui.mc.test;

import org.lwjgl.system.MemoryUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.selectors.ElementNode;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelector.Configure;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;

import java.io.IOException;
import java.nio.*;
import java.util.*;

/** Checks pinned Arc/Core call sites and executes the production shims against LWJGL's real ABI. */
public final class LwjglAbiCheck {
    private static final List<String> MIXINS = List.of("MixinVulkanLongBufferFree", "MixinVulkanBindingBufferFree",
            "MixinParcelBufferFree", "MixinGpuBufferPoolAddress", "MixinWindowsWindowBorderAbi");
    private static boolean windowLongCalled, windowPosCalled;

    public static void main(String[] args) throws Exception {
        MemoryUtil.class.getMethod("memFree", Buffer.class);
        MemoryUtil.class.getMethod("memAddress0", Buffer.class);
        // Minecraft's real 3.3.3 ABI must reproduce the missing typed entry point.
        try {
            MemoryUtil.class.getMethod("memFree", LongBuffer.class);
            throw new AssertionError("Expected actual LWJGL 3.3.3 without typed memFree");
        } catch (NoSuchMethodException expected) {
            // The original Arc call would fail to link, as observed during real shutdown.
        }
        var probe = new ClassNode();
        probe.version = Opcodes.V21;
        probe.access = Opcodes.ACC_PUBLIC;
        probe.name = "icyllis/modernui/mc/test/LwjglAbiProbe";
        probe.superName = "java/lang/Object";
        int callSites = 0;
        for (String name : MIXINS) {
            ClassNode mixin = read("icyllis/modernui/mc/mixin/" + name);
            AnnotationNode declaration = mixin.invisibleAnnotations.stream()
                    .filter(a -> a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
            List<?> targets = (List<?>) values(declaration).get("targets");
            for (MethodNode handler : mixin.methods) {
                if (handler.visibleAnnotations == null) continue;
                AnnotationNode redirect = handler.visibleAnnotations.stream()
                        .filter(a -> a.desc.endsWith("/Redirect;")).findFirst().orElse(null);
                if (redirect == null) continue;
                var annotation = values(redirect);
                String nativeTarget = values((AnnotationNode) annotation.get("at")).get("target").toString();
                for (Object targetName : targets) {
                    ClassNode target = read(targetName.toString().replace('.', '/'));
                    int calls = 0;
                    for (Object selector : (List<?>) annotation.get("method")) {
                        for (MethodNode method : selected(target, selector.toString())) {
                            for (AbstractInsnNode instruction : method.instructions) {
                                if (instruction instanceof MethodInsnNode call
                                        && nativeTarget.equals("L" + call.owner + ";" + call.name + call.desc)) calls++;
                            }
                        }
                    }
                    if (!Integer.valueOf(calls).equals(annotation.get("require"))
                            || !Integer.valueOf(calls).equals(annotation.get("allow"))) {
                        throw new AssertionError("Pinned ABI call site changed: " + targetName + " " + nativeTarget + " count=" + calls);
                    }
                    callSites += calls;
                }
                for (AbstractInsnNode instruction : handler.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/system/windows/User32")) {
                        call.owner = "icyllis/modernui/mc/test/LwjglAbiCheck";
                        call.name = call.name.equals("SetWindowLongPtr") ? "captureWindowLong" : "captureWindowPos";
                    }
                }
                handler.visibleAnnotations = null;
                handler.invisibleAnnotations = null;
                probe.methods.add(handler);
            }
        }
        if (callSites != 11) throw new AssertionError("Incomplete Arc/Core ABI coverage: " + callSites);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        probe.accept(writer);
        var loader = new ClassLoader(LwjglAbiCheck.class.getClassLoader()) {
            Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
        };
        Class<?> type = loader.define(writer.toByteArray());
        var bytes = MemoryUtil.memAlloc(32);
        long base = MemoryUtil.memAddress0((Buffer) bytes);
        bytes.position(7).limit(29);
        try {
            long actual = (long) invoke(type, "modernui$bufferBaseAddress", new Class<?>[]{ByteBuffer.class}, bytes);
            if (actual != base || bytes.position() != 7 || bytes.limit() != 29) {
                throw new AssertionError("Base address shim applied position or changed the buffer");
            }
        } finally {
            invoke(type, "modernui$freeParcelBuffer", new Class<?>[]{ByteBuffer.class}, bytes);
        }
        invoke(type, "modernui$freeLongBuffer", new Class<?>[]{LongBuffer.class}, MemoryUtil.memAllocLong(4));
        invoke(type, "modernui$freeBindingBuffer", new Class<?>[]{IntBuffer.class}, MemoryUtil.memAllocInt(4));
        invoke(type, "modernui$freeParcelBuffer", new Class<?>[]{ByteBuffer.class}, new Object[]{null});
        invoke(type, "modernui$freeLongBuffer", new Class<?>[]{LongBuffer.class}, new Object[]{null});
        invoke(type, "modernui$freeBindingBuffer", new Class<?>[]{IntBuffer.class}, new Object[]{null});
        long previous = (long) invoke(type, "modernui$setWindowLongPtr",
                new Class<?>[]{IntBuffer.class, long.class, int.class, long.class}, null, 11L, -4, 23L);
        boolean moved = (boolean) invoke(type, "modernui$setWindowPos",
                new Class<?>[]{IntBuffer.class, long.class, long.class, int.class, int.class, int.class, int.class, int.class},
                null, 11L, 22L, 3, 4, 55, 66, 77);
        if (previous != 31 || !moved || !windowLongCalled || !windowPosCalled) throw new AssertionError("Windows ABI return/argument change");
        try {
            invoke(type, "modernui$setWindowLongPtr", new Class<?>[]{IntBuffer.class, long.class, int.class, long.class},
                    IntBuffer.allocate(1), 11L, -4, 23L);
            throw new AssertionError("Non-null new-API error buffer silently discarded");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            if (!(expected.getCause() instanceof IllegalArgumentException)) throw expected;
        }
        System.out.println("11 pinned Arc/Core call sites use real LWJGL 3.3.3-compatible ABI; native buffers and Windows argument shims passed");
        System.out.println("MemoryUtil runtime source: " + MemoryUtil.class.getProtectionDomain().getCodeSource());
    }

    public static long captureWindowLong(long window, int index, long value) {
        if (window != 11 || index != -4 || value != 23) throw new AssertionError("Window procedure arguments changed");
        windowLongCalled = true;
        return 31;
    }

    public static boolean captureWindowPos(long window, long after, int x, int y, int width, int height, int flags) {
        if (window != 11 || after != 22 || x != 3 || y != 4 || width != 55 || height != 66 || flags != 77) {
            throw new AssertionError("Window position arguments changed");
        }
        windowPosCalled = true;
        return true;
    }

    private static Object invoke(Class<?> type, String name, Class<?>[] parameters, Object... arguments) throws Exception {
        var method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(null, arguments);
    }

    private static List<MethodNode> selected(ClassNode owner, String text) {
        var selector = MemberInfo.parse(text, null).configure(Configure.SELECT_MEMBER).validate();
        var result = new ArrayList<MethodNode>();
        for (MethodNode method : owner.methods) {
            if (result.size() >= selector.getMaxMatchCount()) break;
            if (selector.match(ElementNode.of(owner, method)).isExactMatch()) result.add(method);
        }
        if (result.isEmpty()) throw new AssertionError("Missing ABI consumer method " + text);
        return result;
    }

    private static ClassNode read(String name) throws IOException {
        try (var stream = LwjglAbiCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null) throw new IOException("Missing pinned class " + name);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static Map<String, Object> values(AnnotationNode annotation) {
        var result = new HashMap<String, Object>();
        for (int i = 0; i < annotation.values.size(); i += 2) result.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        return result;
    }
}
