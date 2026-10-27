package icyllis.modernui.mc.test;

import icyllis.modernui.mc.neoforge.NeoVulkanShutdown;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;

/** Real production handlers, pinned MC close order, and Reforged native cleanup entry. */
public final class NeoVulkanShutdownCheck {
    private static final List<String> EVENTS = new ArrayList<>();
    private static final String HERE = Type.getInternalName(NeoVulkanShutdownCheck.class);
    private static Method gate;

    public static final class NativeStub {
        public static boolean loaded;
        public static boolean isVulkanModLoaded() { return loaded; }
        public static void waitIdle() { EVENTS.add("idle"); }
        public static void cleanUp() {
            var ci = new CallbackInfo("cleanUp", true);
            try { gate.invoke(null, ci); } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            if (!ci.isCancelled()) EVENTS.add("native-cleanup");
        }
    }

    private static final class Loader extends ClassLoader {
        Loader() { super(NeoVulkanShutdownCheck.class.getClassLoader()); }
        Class<?> define(String name, byte[] bytes) { return defineClass(name, bytes, 0, bytes.length); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new AssertionError("Actual Reforged release JAR required");
        verifyPreprocessorWithoutVulkanMod();
        var minecraftMixin = readResource("icyllis/modernui/mc/mixin/MixinNeoVulkanShutdown");
        var gateMixin = readResource("icyllis/modernui/mc/mixin/MixinNeoVulkanShutdownGate");
        verifyMinecraft(readResource("net/minecraft/client/Minecraft"), minecraftMixin);
        verifyHead(minecraftMixin, "modernui$idleBeforeOwnersClose", "close()V", false);
        verifyHead(gateMixin, "modernui$cleanUpOnce", "cleanUp()V", true);
        for (String arg : args) try (var jar = new JarFile(Path.of(arg).toFile())) {
            var entry = jar.getJarEntry("net/vulkanmod/vulkan/Vulkan.class");
            if (entry == null) throw new AssertionError("Missing real Vulkan cleanup owner");
            ClassNode vm = new ClassNode();
            new ClassReader(jar.getInputStream(entry).readAllBytes()).accept(vm, 0);
            var cleanup = vm.methods.stream().filter(m -> m.name.equals("cleanUp") && m.desc.equals("()V")).findFirst().orElseThrow();
            if (Arrays.stream(cleanup.instructions.toArray()).noneMatch(i -> i instanceof MethodInsnNode c
                    && c.owner.equals("net/vulkanmod/vulkan/device/DeviceManager") && c.name.equals("destroy")))
                throw new AssertionError("Cleanup no longer owns native device destruction");
            if (vm.methods.stream().noneMatch(m -> m.name.equals("waitIdle") && m.desc.equals("()V")))
                throw new AssertionError("Missing idle entry");
        }
        var loader = new Loader();
        Class<?> gateClass = transplant(loader, gateMixin, "ShutdownGateHarness");
        gate = gateClass.getDeclaredMethod("modernui$cleanUpOnce", CallbackInfo.class);
        Class<?> hooksClass = transplant(loader, minecraftMixin, "ShutdownHooksHarness");
        Object hooks = hooksClass.getConstructor().newInstance();
        var idle = hooksClass.getDeclaredMethod("modernui$idleBeforeOwnersClose", CallbackInfo.class);
        var close = hooksClass.getDeclaredMethod("modernui$closeNativeAfterOwners", CallbackInfo.class);

        reset(false);
        idle.invoke(hooks, new CallbackInfo("close", false));
        close.invoke(hooks, new CallbackInfo("close", false));
        expect(List.of()); // OpenGL must not resolve or call Vulkan native operations.

        reset(true);
        idle.invoke(hooks, new CallbackInfo("close", false));
        EVENTS.add("owners-close");
        close.invoke(hooks, new CallbackInfo("close", false));
        EVENTS.add("window-close");
        idle.invoke(hooks, new CallbackInfo("close", false));
        close.invoke(hooks, new CallbackInfo("close", false));
        NativeStub.cleanUp(); // A future external hook must not destroy the device twice.
        expect(List.of("idle", "owners-close", "native-cleanup", "window-close"));

        reset(true);
        NativeStub.cleanUp(); // External cleanup happens before this compatibility hook.
        idle.invoke(hooks, new CallbackInfo("close", false));
        close.invoke(hooks, new CallbackInfo("close", false));
        expect(List.of("native-cleanup"));
        reset(false);
        System.out.println("Neo native shutdown production handlers, exact-once gate and actual MC close order passed");
    }

    private static void verifyPreprocessorWithoutVulkanMod() throws Exception {
        // Run the exact Mixin stage that failed on the real no-VulkanMod Neo launch.
        // Its bytecode provider denies the optional package, independently of the test classpath.
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Class<?> serviceClass = Class.forName("org.spongepowered.asm.service.MixinService");
        var singleton = serviceClass.getDeclaredField("instance"); singleton.setAccessible(true);
        Object previousService = singleton.get(null);
        Class<?> api = Class.forName("org.spongepowered.asm.service.IMixinService");
        Class<?> providerApi = Class.forName("org.spongepowered.asm.service.IClassBytecodeProvider");
        int[] denied = {0};
        Object provider = java.lang.reflect.Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{providerApi}, (p,m,a) -> {
            if (!m.getName().equals("getClassNode")) throw new AssertionError("Unexpected provider method " + m);
            String name = ((String) a[0]).replace('.', '/');
            if (name.startsWith("net/vulkanmod/")) {
                denied[0]++;
                throw new ClassNotFoundException(name);
            }
            return readResource(name);
        });
        Object service = java.lang.reflect.Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, (p,m,a) -> {
            if (m.getName().equals("getBytecodeProvider")) return provider;
            if (m.getName().equals("getLogger")) return new org.spongepowered.asm.logging.LoggerAdapterDefault((String) a[0]);
            if (m.getName().equals("getName")) return "No optional VulkanMod fixture";
            if (m.getReturnType() == boolean.class) return false;
            return null;
        });
        Object container = unsafe.allocateInstance(serviceClass);
        var serviceField = serviceClass.getDeclaredField("service"); serviceField.setAccessible(true); serviceField.set(container, service);
        singleton.set(null, container);
        Class<?> environment = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment");
        var current = environment.getDeclaredField("currentEnvironment"); current.setAccessible(true);
        Object previousEnvironment = current.get(null);
        Object fixtureEnvironment = unsafe.allocateInstance(environment);
        Class<?> option = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment$Option");
        int optionCount = java.lang.reflect.Array.getLength(option.getMethod("values").invoke(null));
        var options = environment.getDeclaredField("options"); options.setAccessible(true); options.set(fixtureEnvironment, new boolean[optionCount]);
        current.set(null, fixtureEnvironment);
        Class<?> info = Class.forName("org.spongepowered.asm.mixin.transformer.ClassInfo");
        var cacheField = info.getDeclaredField("cache"); cacheField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,Object> cache = (Map<String,Object>) cacheField.get(null);
        var previousCache = new HashMap<>(cache);
        try {
            Class<?> processorClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinPreProcessorStandard");
            Object processor = unsafe.allocateInstance(processorClass);
            var activities = processorClass.getDeclaredField("activities"); activities.setAccessible(true);
            activities.set(processor, new org.spongepowered.asm.mixin.transformer.ActivityStack());
            var profiler = processorClass.getDeclaredField("profiler"); profiler.setAccessible(true);
            profiler.set(processor, new org.spongepowered.asm.util.perf.Profiler("optional-vulkan-shutdown"));
            Method transform = processorClass.getDeclaredMethod("transformMethod", MethodInsnNode.class);
            transform.setAccessible(true);
            ClassNode mixin = readResource("icyllis/modernui/mc/mixin/MixinNeoVulkanShutdown");
            int references = 0;
            for (var method : mixin.methods) for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call) {
                    transform.invoke(processor, call);
                    references++;
                }
            if (references < 6 || denied[0] != 0) throw new AssertionError("Optional VulkanMod was resolved during mixin preprocessing");
            // Negative mutation restores the original direct call and must reproduce the real CNFE.
            var original = new MethodInsnNode(Opcodes.INVOKESTATIC, "net/vulkanmod/vulkan/Vulkan", "waitIdle", "()V", false);
            cache.remove(original.owner);
            try {
                transform.invoke(processor, original);
                throw new AssertionError("Original direct optional reference unexpectedly preprocessed");
            } catch (java.lang.reflect.InvocationTargetException expected) {
                Throwable cause = expected.getCause();
                if (!(cause instanceof RuntimeException) || !(cause.getCause() instanceof ClassNotFoundException)
                        || !cause.getCause().getMessage().equals("net.vulkanmod.vulkan.Vulkan")) throw expected;
            }
            if (denied[0] != 1) throw new AssertionError("Missing no-VulkanMod preprocessing negative control");
            System.out.println("Actual Mixin preprocessing passed without VulkanMod; direct-call CNFE negative control reproduced");
        } finally {
            cache.clear(); cache.putAll(previousCache);
            current.set(null, previousEnvironment);
            singleton.set(null, previousService);
        }
    }

    private static void reset(boolean loaded) throws Exception {
        var field = NeoVulkanShutdown.class.getDeclaredField("STARTED");
        field.setAccessible(true);
        ((AtomicBoolean) field.get(null)).set(false);
        NativeStub.loaded = loaded;
        EVENTS.clear();
    }

    private static void expect(List<String> expected) {
        if (!EVENTS.equals(expected)) throw new AssertionError("Shutdown order: " + EVENTS + ", expected " + expected);
    }

    private static void verifyMinecraft(ClassNode mc, ClassNode mixin) {
        var close = mc.methods.stream().filter(m -> m.name.equals("close") && m.desc.equals("()V")).findFirst().orElseThrow();
        int device = -1, window = -1, deviceCalls = 0;
        AbstractInsnNode[] code = close.instructions.toArray();
        for (int i = 0; i < code.length; i++) if (code[i] instanceof MethodInsnNode call) {
            if (call.owner.equals("com/mojang/blaze3d/systems/GpuDevice") && call.name.equals("close")) { device = i; deviceCalls++; }
            if (window < 0 && call.owner.equals("net/minecraft/client/renderer/VirtualScreen") && call.name.equals("close")) window = i;
        }
        if (deviceCalls != 1 || device < 0 || window <= device)
            throw new AssertionError("Native cleanup injection must follow the unique owner close and precede the window");
        var nativeHook = mixin.methods.stream().filter(m -> m.name.equals("modernui$closeNativeAfterOwners")).findFirst().orElseThrow();
        AnnotationNode inject = nativeHook.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        Map<String,Object> values = values(inject);
        if (!values.get("method").equals(List.of("close()V"))) throw new AssertionError("Unexpected MC method selector");
        var at = (AnnotationNode) ((List<?>) values.get("at")).getFirst();
        Map<String,Object> target = values(at);
        if (!"Lcom/mojang/blaze3d/systems/GpuDevice;close()V".equals(target.get("target"))
                || !"AFTER".equals(((String[]) target.get("shift"))[1]))
            throw new AssertionError("Native cleanup moved outside the owner/window boundary");
    }

    private static void verifyHead(ClassNode owner, String name, String selector, boolean cancellable) {
        var method = owner.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
        var inject = method.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        var config = values(inject);
        var at = (AnnotationNode) ((List<?>) config.get("at")).getFirst();
        if (!config.get("method").equals(List.of(selector)) || !values(at).get("value").equals("HEAD")
                || !Integer.valueOf(1).equals(config.get("require")) || !Integer.valueOf(1).equals(config.get("allow"))
                || (cancellable && !Boolean.TRUE.equals(config.get("cancellable"))))
            throw new AssertionError("Shutdown HEAD or exact-once cancellation contract changed");
    }

    private static Map<String,Object> values(AnnotationNode annotation) {
        Map<String,Object> result = new HashMap<>();
        for (int i = 0; i < annotation.values.size(); i += 2) result.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        return result;
    }

    private static ClassNode readResource(String name) throws Exception {
        try (var stream = NeoVulkanShutdownCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null) throw new AssertionError("Missing actual class " + name);
            ClassNode node = new ClassNode(); new ClassReader(stream.readAllBytes()).accept(node, 0); return node;
        }
    }

    private static Class<?> transplant(Loader loader, ClassNode source, String suffix) {
        String oldName = source.name;
        source.name = "icyllis/modernui/mc/test/" + suffix;
        source.access = Opcodes.ACC_PUBLIC;
        source.visibleAnnotations = null;
        source.invisibleAnnotations = null;
        for (MethodNode method : source.methods) {
            method.access = (method.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
            method.visibleAnnotations = null; method.invisibleAnnotations = null;
            for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals(oldName)) call.owner = source.name;
                if ((call.owner.equals("icyllis/modernui/mc/ModernUIMod") && call.name.equals("isVulkanModLoaded"))
                        || (call.owner.equals("icyllis/modernui/mc/neoforge/NeoVulkanShutdown") && (call.name.equals("waitIdle") || call.name.equals("cleanUp"))))
                    call.owner = HERE + "$NativeStub";
            }
        }
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); source.accept(writer);
        return loader.define(source.name.replace('/', '.'), writer.toByteArray());
    }
}
