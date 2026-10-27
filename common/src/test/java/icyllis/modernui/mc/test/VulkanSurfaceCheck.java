package icyllis.modernui.mc.test;

import icyllis.modernui.mc.VulkanSurfaceCompat;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;

import static org.lwjgl.vulkan.VK10.*;

/** CPU policy checks plus optional checks against the actual VulkanMod release JARs. */
public final class VulkanSurfaceCheck {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (!VulkanSurfaceCompat.supportsPresentation(VK_SUCCESS, VK_TRUE)
                || VulkanSurfaceCompat.supportsPresentation(VK_SUCCESS, VK_FALSE)
                || VulkanSurfaceCompat.supportsPresentation(VK_ERROR_DEVICE_LOST, VK_TRUE)
                || VulkanSurfaceCompat.supportsPresentation(VK_INCOMPLETE, VK_TRUE)) {
            throw new AssertionError("Only successful, explicitly supported presentation is accepted");
        }
        var queries = new AtomicInteger();
        if (VulkanSurfaceCompat.hasPresentQueue(0, family -> { throw new AssertionError("Empty device queried"); })) {
            throw new AssertionError("Empty device accepted");
        }
        if (VulkanSurfaceCompat.hasPresentQueue(3, family -> { queries.incrementAndGet(); return false; })
                || queries.get() != 3) throw new AssertionError("Must reject devices without present support");
        queries.set(0);
        if (!VulkanSurfaceCompat.hasPresentQueue(4, family -> { queries.incrementAndGet(); return family == 1; })
                || queries.get() != 2) throw new AssertionError("Must accept a real present queue, including a separate queue family");

        ClassNode mixin;
        try (var input = VulkanSurfaceCheck.class.getResourceAsStream(
                "/icyllis/modernui/mc/mixin/MixinVulkanDeviceManager.class")) {
            if (input == null) throw new AssertionError("Missing production mixin");
            mixin = read(new ClassReader(input));
        }
        MethodNode handler = mixin.methods.stream().filter(m -> m.name.equals("modernui$requirePresentSupport"))
                .findFirst().orElseThrow();
        AnnotationNode inject = handler.visibleAnnotations.stream()
                .filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        var annotation = values(inject);
        if (!Boolean.TRUE.equals(annotation.get("cancellable")) || !Integer.valueOf(1).equals(annotation.get("require"))) {
            throw new AssertionError("Guard must be required and cancellable");
        }
        AnnotationNode at = ((List<AnnotationNode>) annotation.get("at")).getFirst();
        if (!"HEAD".equals(values(at).get("value"))) throw new AssertionError("Guard must precede unsafe surface queries");
        String method = ((List<?>) annotation.get("method")).getFirst().toString();
        for (String jar : args) verifyJar(Path.of(jar), method);
        System.out.println("Presentation support policy passed; " + args.length + " actual VulkanMod release guards verified");
    }

    private static void verifyJar(Path path, String target) throws Exception {
        try (JarFile jar = new JarFile(path.toFile())) {
            ClassNode manager = read(jar, "net/vulkanmod/vulkan/device/DeviceManager");
            int split = target.indexOf('(');
            var matching = manager.methods.stream().filter(m -> m.name.equals(target.substring(0, split))
                    && m.desc.equals(target.substring(split)) && (m.access & Opcodes.ACC_STATIC) != 0).toList();
            if (matching.size() != 1) throw new AssertionError("Unsupported device suitability target: " + path);
            ClassNode vulkan = read(jar, "net/vulkanmod/vulkan/Vulkan");
            if (vulkan.methods.stream().noneMatch(m -> m.name.equals("getSurface") && m.desc.equals("()J")
                    && (m.access & Opcodes.ACC_STATIC) != 0)) throw new AssertionError("Unsupported surface accessor: " + path);
            ClassNode config = read(jar, "net/vulkanmod/config/Config");
            if (config.fields.stream().noneMatch(f -> f.name.equals("device") && f.desc.equals("I"))) {
                throw new AssertionError("Unsupported selected device index: " + path);
            }
            MethodNode pick = manager.methods.stream().filter(m -> m.name.equals("pickPhysicalDevice"))
                    .findFirst().orElseThrow();
            boolean suitableIndex = false;
            boolean listGet = false;
            for (AbstractInsnNode instruction : pick.instructions) {
                if (instruction instanceof FieldInsnNode field && field.name.equals("suitableDevices")
                        && field.desc.equals("Ljava/util/List;")) suitableIndex = true;
                if (instruction instanceof MethodInsnNode call && call.owner.equals("java/util/List")
                        && call.name.equals("get") && call.desc.equals("(I)Ljava/lang/Object;")) listGet = true;
            }
            if (!suitableIndex || !listGet) throw new AssertionError("Device index no longer addresses suitableDevices: " + path);
        }
    }

    private static ClassNode read(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry(name + ".class");
        if (entry == null) throw new AssertionError("Missing " + name);
        try (var input = jar.getInputStream(entry)) { return read(new ClassReader(input)); }
    }

    private static ClassNode read(ClassReader reader) {
        var node = new ClassNode();
        reader.accept(node, 0);
        return node;
    }

    private static Map<String, Object> values(AnnotationNode annotation) {
        var result = new HashMap<String, Object>();
        for (int i = 0; i < annotation.values.size(); i += 2) {
            result.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        }
        return result;
    }
}
