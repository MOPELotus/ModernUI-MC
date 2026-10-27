package icyllis.modernui.mc.test;

import icyllis.modernui.mc.VulkanGpuDeviceOwnership;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelector.Configure;
import org.spongepowered.asm.mixin.injection.selectors.ElementNode;

import java.util.*;
import java.util.jar.JarFile;

/** Runs the production injection handlers with CPU-only resource-close substitutes. */
public final class VulkanGpuOwnershipCheck {
    public static class Resource {
        public int closes;
        public boolean closed;
        public boolean fail;
        public void close() {
            if (closed) throw new AssertionError("Resource closed twice");
            closed = true;
            closes++;
            if (fail) throw new IllegalStateException("Injected owner failure");
            try { call(this, "modernui$forgetClosedResource", new CallbackInfo("close", false)); }
            catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        }
    }
    public static class RenderSystemStub {
        public static Object device;
        public static Object getDevice() { return device; }
    }
    public static class Cloud {
        public int resets;
        public void resetBuffer() { resets++; }
    }
    private static final String PREFIX = "icyllis/modernui/mc/mixin/";
    private static final String HERE = Type.getInternalName(VulkanGpuOwnershipCheck.class);
    private static final class Loader extends ClassLoader {
        Loader() { super(VulkanGpuOwnershipCheck.class.getClassLoader()); }
        Class<?> define(String name, byte[] bytes) { return defineClass(name, bytes, 0, bytes.length); }
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new AssertionError("Actual VulkanMod release JAR required");
        var deviceMixin = read(PREFIX + "MixinVulkanGpuDeviceOwnership");
        var resourceMixin = read(PREFIX + "MixinVulkanGpuResourceOwnership");
        var cloudMixin = read(PREFIX + "MixinVulkanCloudOwnership");
        var loader = new Loader();
        var deviceClass = transplant(loader, deviceMixin, "OwnershipDeviceHarness", "java/lang/Object");
        var resourceClass = transplant(loader, resourceMixin, "OwnershipResourceHarness", HERE + "$Resource");
        var cloudClass = transplant(loader, cloudMixin, "OwnershipCloudHarness", HERE + "$Cloud");
        Object first = deviceClass.getConstructor().newInstance();
        Object second = deviceClass.getConstructor().newInstance();
        var buffer = (Resource) resourceClass.getConstructor().newInstance();
        var texture = (Resource) resourceClass.getConstructor().newInstance();
        var alreadyClosed = (Resource) resourceClass.getConstructor().newInstance();
        var otherDevice = (Resource) resourceClass.getConstructor().newInstance();
        own(first, "modernui$ownBuffer", buffer);
        own(first, "modernui$ownTexture", texture);
        own(first, "modernui$ownTexture", texture); // Supplier overload delegates to String overload.
        own(first, "modernui$ownBuffer", alreadyClosed);
        alreadyClosed.close(); // Executes the actual production forget handler.
        own(second, "modernui$ownBuffer", otherDevice);
        RenderSystemStub.device = first;
        var cloud = (Cloud) cloudClass.getConstructor().newInstance();
        call(cloud, "modernui$ownCloudBuffer", new CallbackInfo("init", false));
        call(first, "modernui$closeOwnedResources", new CallbackInfo("close", false));
        check(buffer.closes == 1 && texture.closes == 1 && alreadyClosed.closes == 1,
                "Remaining wrappers must close exactly once");
        check(otherDevice.closes == 0 && cloud.resets == 1, "Device ownership or cloud reset mismatch");
        call(first, "modernui$closeOwnedResources", new CallbackInfo("close", false));
        check(cloud.resets == 1, "Repeated shutdown reset cloud twice");
        call(second, "modernui$closeOwnedResources", new CallbackInfo("close", false));
        check(otherDevice.closes == 1, "Other device's resource was lost");
        Object failingOwner = new Object();
        var attempts = new int[1];
        VulkanGpuDeviceOwnership.track(failingOwner, new Object(), () -> { throw new IllegalStateException("test"); });
        VulkanGpuDeviceOwnership.track(failingOwner, new Object(), () -> attempts[0]++);
        try { VulkanGpuDeviceOwnership.closeRemaining(failingOwner); throw new AssertionError("Failure swallowed"); }
        catch (IllegalStateException expected) { check(attempts[0] == 1, "Failure skipped another owner"); }
        for (String jar : args) verify(jar, deviceMixin, resourceMixin, cloudMixin);
        System.out.println("Production GPU wrapper/cloud ownership handlers passed; " + args.length + " release JARs checked");
    }
    private static void own(Object device, String handler, Resource resource) throws Exception {
        call(device, handler, new CallbackInfoReturnable<>("return", false, resource));
    }
    private static void call(Object target, String name, Object callback) throws ReflectiveOperationException {
        for (var method : target.getClass().getDeclaredMethods()) {
            if (method.getName().equals(name)) { method.setAccessible(true); method.invoke(target, callback); return; }
        }
        throw new NoSuchMethodException(name);
    }
    private static ClassNode read(String name) throws Exception {
        try (var input = VulkanGpuOwnershipCheck.class.getResourceAsStream("/" + name + ".class")) {
            var node = new ClassNode(); new ClassReader(Objects.requireNonNull(input)).accept(node, 0); return node;
        }
    }
    private static Class<?> transplant(Loader loader, ClassNode original, String suffix, String superName) {
        String name = HERE + "$" + suffix;
        var mappings = new HashMap<String, String>();
        mappings.put(original.name, name);
        mappings.put("com/mojang/blaze3d/buffers/GpuBuffer", HERE + "$Resource");
        mappings.put("com/mojang/blaze3d/textures/GpuTexture", HERE + "$Resource");
        mappings.put("com/mojang/blaze3d/systems/RenderSystem", HERE + "$RenderSystemStub");
        mappings.put("com/mojang/blaze3d/systems/GpuDevice", "java/lang/Object");
        var node = new ClassNode(); original.accept(new ClassRemapper(node, new SimpleRemapper(mappings)));
        node.access = Opcodes.ACC_PUBLIC;
        node.superName = superName;
        node.visibleAnnotations = node.invisibleAnnotations = null;
        node.methods.removeIf(m -> m.name.equals("<init>") || (m.access & Opcodes.ACC_ABSTRACT) != 0);
        for (var method : node.methods) method.visibleAnnotations = method.invisibleAnnotations = null;
        var constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(constructor);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
        return loader.define(name.replace('/', '.'), writer.toByteArray());
    }
    private static void verify(String path, ClassNode deviceMixin, ClassNode resourceMixin, ClassNode cloudMixin) throws Exception {
        try (var jar = new JarFile(path)) {
            var device = entry(jar, "net/vulkanmod/render/engine/VkGpuDevice");
            verifySelectors(device, deviceMixin, Map.of("modernui$ownBuffer", 2, "modernui$ownTexture", 2,
                    "modernui$closeOwnedResources", 1));
            for (String kind : List.of("VkGpuBuffer", "VkGpuTexture")) {
                var resource = entry(jar, "net/vulkanmod/render/engine/" + kind);
                verifySelectors(resource, resourceMixin, Map.of("modernui$forgetClosedResource", 1));
                var close = resource.methods.stream().filter(m -> m.name.equals("close") && m.desc.equals("()V")).findFirst().orElseThrow();
                check(Arrays.stream(close.instructions.toArray()).anyMatch(i -> i instanceof FieldInsnNode f && f.name.equals("closed")),
                        "Native wrapper lost its closed guard: " + kind);
            }
            var cloudEntry = jar.getJarEntry("net/vulkanmod/render/sky/CloudRenderer.class");
            if (cloudEntry != null) {
                var cloud = entry(jar, "net/vulkanmod/render/sky/CloudRenderer");
                verifySelectors(cloud, cloudMixin, Map.of("modernui$ownCloudBuffer", 1));
                var reset = cloud.methods.stream().filter(m -> m.name.equals("resetBuffer") && m.desc.equals("()V")).findFirst().orElseThrow();
                check(Arrays.stream(reset.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode m && m.owner.equals("net/vulkanmod/render/VBO") && m.name.equals("close")), "Cloud reset no longer owns VBO.close");
            }
            // Borrowed Arc3D image wrappers must never become owned here.
            var borrowed = device.methods.stream().filter(m -> m.name.equals("gpuTextureFromVulkanImage")).findFirst().orElseThrow();
            check(!matches(borrowed, "createBuffer*") && !matches(borrowed, "createTexture(Ljava/lang/String;ILcom/mojang/blaze3d/textures/TextureFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;"), "Borrowed image selected");
            System.out.println(path + ": device return paths and native closed guards verified; cloud "
                    + (cloudEntry == null ? "target absent (@Pseudo)" : "owner verified"));
        }
    }
    private static ClassNode entry(JarFile jar, String name) throws Exception {
        try (var input = jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(name + ".class")))) {
            var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private static void verifySelectors(ClassNode target, ClassNode mixin, Map<String, Integer> expected) {
        for (var handler : mixin.methods) {
            if (!expected.containsKey(handler.name)) continue;
            var annotation = handler.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") var selectors = (List<String>) annotation.values.get(annotation.values.indexOf("method") + 1);
            int returns = 0;
            for (var method : target.methods) {
                if (selectors.stream().anyMatch(s -> matches(method, s)))
                    for (var instruction : method.instructions) if (instruction.getOpcode() == Opcodes.RETURN || instruction.getOpcode() == Opcodes.ARETURN) returns++;
            }
            check(returns == expected.get(handler.name), target.name + " " + handler.name + " return count=" + returns);
        }
    }
    private static boolean matches(MethodNode method, String selector) {
        return MemberInfo.parse(selector, null).configure(Configure.SELECT_MEMBER).match(ElementNode.of(new ClassNode(), method)).isMatch();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
