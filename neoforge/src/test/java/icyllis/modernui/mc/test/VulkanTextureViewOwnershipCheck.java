package icyllis.modernui.mc.test;

import icyllis.modernui.mc.VulkanGpuDeviceOwnership;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.jar.JarFile;

/** Production view hooks and actual Reforged view-close/deferred-reference bytecode, without a GPU. */
public final class VulkanTextureViewOwnershipCheck {
    private static final String HERE = Type.getInternalName(VulkanTextureViewOwnershipCheck.class);
    private static final String VIEW = "net/vulkanmod/render/engine/VkTextureView";
    private static final String TEX = "net/vulkanmod/render/engine/VkGpuTexture";
    private static Class<?> lifetimeClass;
    public static final class Texture {
        final Object lifetime;
        public boolean released;
        public View internal;
        Texture() throws Exception { var c = lifetimeClass.getDeclaredConstructor(); c.setAccessible(true); lifetime = c.newInstance(); }
        public void addView() { lifetime("addView"); }
        public void removeViews() { if ((boolean) lifetime("removeView")) released = true; }
        public void close() { if (internal != null) internal.close(); if ((boolean) lifetime("requestClose")) released = true; }
        public int views() { return (int) lifetime("viewCount"); }
        private Object lifetime(String name) {
            try { var m = lifetimeClass.getDeclaredMethod(name); m.setAccessible(true); return m.invoke(lifetime); }
            catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        }
    }
    public static final class Fbo {
        public int closes;
        public void close() { if (++closes != 1) throw new AssertionError("View cached FBO closed twice"); }
    }
    public abstract static class View {
        public Texture texture;
        public void close() {
            try {
                var ci = new CallbackInfo("close", true);
                invoke(this, "modernui$skipClosedView", ci);
                if (!ci.isCancelled()) {
                    invoke(this, "actualClose");
                    invoke(this, "modernui$forgetClosedView", new CallbackInfo("close", false));
                }
            } catch (Exception ex) { throw new AssertionError(ex); }
        }
    }
    private static final class Loader extends ClassLoader {
        Loader() { super(VulkanTextureViewOwnershipCheck.class.getClassLoader()); }
        Class<?> define(ClassNode node) {
            var w = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES); node.accept(w);
            var b = w.toByteArray(); return defineClass(node.name.replace('/', '.'), b, 0, b.length);
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected actual Reforged release JAR");
        try (var jar = new JarFile(args[0])) {
            var loader = new Loader();
            lifetimeClass = loader.define(entry(jar, "net/vulkanmod/render/engine/DeferredTextureRelease"));
            var originalView = entry(jar, VIEW);
            var nativeClose = originalView.methods.stream().filter(m -> m.name.equals("close") && m.desc.equals("()V")).findFirst().orElseThrow();
            var device = entry(jar, "net/vulkanmod/render/engine/VkGpuDevice");
            check(device.methods.stream().filter(m -> m.name.equals("createTextureView")).map(m -> m.desc).collect(java.util.stream.Collectors.toSet()).equals(Set.of(
                    "(Lcom/mojang/blaze3d/textures/GpuTexture;)Lcom/mojang/blaze3d/textures/GpuTextureView;",
                    "(Lcom/mojang/blaze3d/textures/GpuTexture;II)Lcom/mojang/blaze3d/textures/GpuTextureView;")), "Actual createTextureView descriptors changed");
            for (var method : device.methods) if (method.name.equals("createTextureView"))
                check(Arrays.stream(method.instructions.toArray()).filter(i -> i.getOpcode() == Opcodes.ARETURN).count() == 1, "View return paths changed");
            var lifecycle = resource("MixinVulkanTextureViewLifecycle");
            String viewName = HERE + "$ActualView";
            var mappings = Map.of(lifecycle.name, viewName, VIEW, viewName, TEX, HERE + "$Texture", "net/vulkanmod/render/engine/VkFbo", HERE + "$Fbo");
            var viewNode = new ClassNode(); lifecycle.accept(new ClassRemapper(viewNode, new SimpleRemapper(mappings)));
            viewNode.access = Opcodes.ACC_PUBLIC; viewNode.superName = HERE + "$View";
            strip(viewNode); viewNode.methods.removeIf(m -> m.name.equals("<init>"));
            viewNode.methods.add(constructor(viewNode.superName));
            viewNode.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "fboCache", "Lit/unimi/dsi/fastutil/ints/Int2ReferenceMap;", null, null));
            var holder = new ClassNode(); holder.version = Opcodes.V21; holder.name = VIEW; holder.superName = "java/lang/Object";
            holder.methods.add(nativeClose);
            var mappedClose = new ClassNode(); holder.accept(new ClassRemapper(mappedClose, new SimpleRemapper(mappings)));
            var close = mappedClose.methods.getFirst(); close.name = "actualClose"; close.access = Opcodes.ACC_PUBLIC;
            viewNode.methods.add(close);
            var textureGetter = new MethodNode(Opcodes.ACC_PUBLIC, "texture", "()L" + HERE + "$Texture;", null, null);
            textureGetter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            textureGetter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, HERE + "$View", "texture", "L" + HERE + "$Texture;"));
            textureGetter.instructions.add(new InsnNode(Opcodes.ARETURN)); viewNode.methods.add(textureGetter);
            var viewClass = loader.define(viewNode);
            var creation = resource("MixinVulkanTextureViewOwnership");
            var inject = creation.methods.stream().filter(m -> m.name.equals("modernui$ownTextureView")).findFirst().orElseThrow().visibleAnnotations.getFirst();
            check(inject.values.contains("method") && inject.values.get(inject.values.indexOf("method") + 1).equals(List.of("createTextureView*")), "Production creation selector changed");
            var creationNode = new ClassNode(); creation.accept(new ClassRemapper(creationNode, new SimpleRemapper(Map.of(
                    creation.name, HERE + "$ActualDevice", "com/mojang/blaze3d/textures/GpuTextureView", HERE + "$View"))));
            creationNode.access = Opcodes.ACC_PUBLIC; strip(creationNode);
            var deviceClass = loader.define(creationNode);
            // Negative control: unmodified release bytecode repeats cached FBO
            // cleanup even though its texture-reference decrement is guarded.
            var negative = newView(viewClass, new Texture());
            invoke(negative, "actualClose");
            boolean reproducedDoubleClose = false;
            try { invoke(negative, "actualClose"); }
            catch (java.lang.reflect.InvocationTargetException expected) {
                reproducedDoubleClose = expected.getCause() instanceof AssertionError;
            }
            check(reproducedDoubleClose, "Unmodified repeated view close did not expose cached FBO double close");
            // Normal texture-first close retains the image until the actual
            // Reforged reference counter receives the last view release.
            Object firstDevice = deviceClass.getConstructor().newInstance();
            var first = new Texture(); var firstView = newView(viewClass, first);
            own(firstDevice, firstView); own(firstDevice, firstView); // Delegating overload dedup.
            first.close(); check(!first.released && first.views() == 1, "Real deferred texture released before live view");
            VulkanGpuDeviceOwnership.closeRemaining(firstDevice);
            check(first.released && first.views() == 0, "Device failed to release remaining view/image");
            firstView.close(); // Production HEAD cancels the native cache loop.
            // View-first close followed by texture closing its internal alias
            // must not run the native cached FBO close a second time.
            Object secondDevice = deviceClass.getConstructor().newInstance();
            var second = new Texture(); var secondView = newView(viewClass, second); second.internal = secondView;
            own(secondDevice, secondView);
            VulkanGpuDeviceOwnership.closeRemaining(secondDevice);
            check(!second.released && second.views() == 0, "Open texture released by closing only view");
            second.close(); check(second.released, "Real deferred texture did not release after view-first close");
            // A view closed by another normal owner is removed from the device
            // tracker and cannot decrement the real reference count twice.
            Object thirdDevice = deviceClass.getConstructor().newInstance();
            var third = new Texture(); var thirdView = newView(viewClass, third); own(thirdDevice, thirdView);
            thirdView.close(); VulkanGpuDeviceOwnership.closeRemaining(thirdDevice); third.close();
            check(third.released && third.views() == 0, "Early normal close left a stale owner");
            System.out.println("Production view hooks + actual Reforged VkTextureView.close and DeferredTextureRelease passed: both close orders, FBO exactly once, remaining/early-close ownership");
        }
    }
    private static View newView(Class<?> type, Texture texture) throws Exception {
        var view = (View) type.getConstructor().newInstance(); view.texture = texture; texture.addView();
        Int2ReferenceMap<Fbo> cache = new Int2ReferenceOpenHashMap<>(); cache.put(1, new Fbo());
        type.getField("fboCache").set(view, cache); return view;
    }
    private static void own(Object device, View view) throws Exception {
        invoke(device, "modernui$ownTextureView", new CallbackInfoReturnable<>("return", false, view));
    }
    private static void invoke(Object target, String name, Object... args) throws Exception {
        var method = Arrays.stream(target.getClass().getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        method.setAccessible(true); method.invoke(target, args);
    }
    private static ClassNode resource(String simpleName) throws Exception {
        try (var in = VulkanTextureViewOwnershipCheck.class.getResourceAsStream("/icyllis/modernui/mc/mixin/" + simpleName + ".class")) {
            var n = new ClassNode(); new ClassReader(Objects.requireNonNull(in)).accept(n, 0); return n;
        }
    }
    private static ClassNode entry(JarFile jar, String name) throws Exception {
        try (var in = jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(name + ".class")))) {
            var n = new ClassNode(); new ClassReader(in).accept(n, 0); return n;
        }
    }
    private static void strip(ClassNode n) {
        n.visibleAnnotations = n.invisibleAnnotations = null;
        for (var method : n.methods) method.visibleAnnotations = method.invisibleAnnotations = null;
    }
    private static MethodNode constructor(String parent) {
        var m = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); m.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false)); m.instructions.add(new InsnNode(Opcodes.RETURN)); return m;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
