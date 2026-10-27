package icyllis.modernui.mc.test;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkAllocationCallbacks;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/** Executes release owner registration and cleanUp bytecode with only native destruction stubbed. */
public final class VulkanPipelineLifecycleCheck {
    private static final String P = "net/vulkanmod/vulkan/shader/Pipeline";
    private static final String G = "net/vulkanmod/vulkan/shader/GraphicsPipeline";
    private static final String D = "net/vulkanmod/vulkan/shader/DescriptorSets";
    private static final String R = "net/vulkanmod/vulkan/Renderer";
    private static final String DM = "net/vulkanmod/vulkan/device/DeviceManager";
    private static final String N = Type.getInternalName(NativeCalls.class);
    private static final Map<Long, Integer> destroyed = new HashMap<>();
    private static int removed;

    public static void main(String[] args) throws Exception {
        verifyHook();
        if (args.length == 0) throw new AssertionError("Actual VulkanMod release JAR required");
        for (String arg : args) verifyRelease(Path.of(arg));
        System.out.println("Registered pipeline lifecycle passed for " + args.length + " actual VulkanMod releases");
    }

    @SuppressWarnings("unchecked")
    private static void verifyRelease(Path path) throws Exception {
        destroyed.clear(); removed = 0;
        try (var jar = new JarFile(path.toFile())) {
            ClassNode pipeline = read(jar, P), graphics = read(jar, G), descriptors = read(jar, D);
            ClassNode vulkan = read(jar, "net/vulkanmod/vulkan/Vulkan");
            var shutdown = method(vulkan, "cleanUp", "()V");
            int drains = 0, destroyAt = -1, drainAt = -1, index = 0;
            for (var instruction : shutdown.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals("net/vulkanmod/vulkan/memory/MemoryManager") && call.name.equals("freeAllBuffers") && call.desc.equals("()V")) { drains++; drainAt = index; }
                    if (call.owner.equals("org/lwjgl/util/vma/Vma") && call.name.equals("vmaDestroyAllocator")) destroyAt = index;
                }
                index++;
            }
            if (drains != 1 || drainAt >= destroyAt) throw new AssertionError("Unsupported shutdown drain: " + path);
            if (pipeline.fields.stream().noneMatch(f -> f.name.equals("PIPELINES") && f.desc.equals("Ljava/util/List;"))) throw new AssertionError("Missing actual owner registry");
            MethodNode constructor = graphics.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
            MethodNode registration = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "registerActualOwner", "(L" + G + ";)V", null, null);
            boolean copy = false;
            for (var instruction : constructor.instructions) {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC && field.name.equals("PIPELINES")) copy = true;
                if (copy && !(instruction instanceof LabelNode) && !(instruction instanceof LineNumberNode) && !(instruction instanceof FrameNode)) registration.instructions.add(instruction.clone(new HashMap<>()));
            }
            if (!copy || registration.instructions.getLast().getOpcode() != Opcodes.RETURN) throw new AssertionError("Actual registration tail changed: " + path);
            registration.maxStack = 2; registration.maxLocals = 1;
            ClassNode base = shell(P, "java/lang/Object");
            fields(pipeline, base, "PIPELINES", "descriptorSetLayout", "pipelineLayout", "descriptorSets");
            base.access |= Opcodes.ACC_ABSTRACT;
            base.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "cleanUp", "()V", null, null));
            base.methods.add(method(pipeline, "destroyDescriptorSets", "()V"));
            ClassNode owner = shell(G, P);
            fields(graphics, owner, "vertShaderModule", "fragShaderModule", "vertexInputDescription", "graphicsPipelines");
            owner.methods.add(method(graphics, "cleanUp", "()V"));
            graphics.methods.stream().filter(m -> m.name.startsWith("lambda$cleanUp$")).forEach(owner.methods::add);
            owner.methods.add(registration);
            ClassNode ds = shell(D, "java/lang/Object");
            fields(descriptors, ds, "DEVICE", "descriptorPool", "dynamicOffsets");
            ds.methods.add(method(descriptors, "cleanUp", "()V"));
            var classes = new HashMap<String, byte[]>();
            for (var node : List.of(base, owner, ds)) {
                for (var m : node.methods) for (var i : m.instructions) if (i instanceof MethodInsnNode call &&
                        (call.owner.equals("org/lwjgl/vulkan/VK10") || call.owner.equals("org/lwjgl/system/MemoryUtil"))) call.owner = N;
                classes.put(node.name.replace('/', '.'), bytes(node));
            }
            var device = shell(DM, "java/lang/Object");
            device.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "vkDevice", "Lorg/lwjgl/vulkan/VkDevice;", null, null));
            classes.put(DM.replace('/', '.'), bytes(device));
            classes.put((G + "$VertexInputDescription").replace('/', '.'), simpleStub(G + "$VertexInputDescription", "cleanUp", "()V"));
            classes.put("net.vulkanmod.vulkan.shader.PipelineState", bytes(shell("net/vulkanmod/vulkan/shader/PipelineState", "java/lang/Object")));
            ClassNode renderer = shell(R, "java/lang/Object");
            var getter = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getInstance", "()L" + R + ";", null, null);
            // Allocate the inert receiver without invoking any renderer constructor.
            getter.instructions.add(new LdcInsnNode(Type.getObjectType(R)));
            getter.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, N, "allocate", "(Ljava/lang/Class;)Ljava/lang/Object;", false));
            getter.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, R)); getter.instructions.add(new InsnNode(Opcodes.ARETURN)); renderer.methods.add(getter);
            var remove = new MethodNode(Opcodes.ACC_PUBLIC, "removeUsedPipeline", "(L" + P + ";)V", null, null);
            remove.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, N, "removed", "()V", false)); remove.instructions.add(new InsnNode(Opcodes.RETURN)); renderer.methods.add(remove);
            classes.put(R.replace('/', '.'), bytes(renderer));
            // Execute the production injection handler too. The generated accessor
            // substitutes only Mixin's generated direct static field accessor.
            ClassNode productionMixin;
            try (var input = VulkanPipelineLifecycleCheck.class.getResourceAsStream("/icyllis/modernui/mc/mixin/MixinVulkanPipelineLifecycle.class")) {
                productionMixin = new ClassNode(); new ClassReader(input).accept(productionMixin, 0);
            }
            var handlerFixture = shell(productionMixin.name, "java/lang/Object");
            var actualHandler = productionMixin.methods.stream().filter(m -> m.name.equals("modernui$closeRemainingPipelines")).findFirst().orElseThrow();
            actualHandler.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
            handlerFixture.methods.add(actualHandler);
            classes.put(handlerFixture.name.replace('/', '.'), bytes(handlerFixture));
            String accessName = "icyllis/modernui/mc/mixin/AccessVulkanPipelines";
            var accessFixture = shell(accessName, "java/lang/Object");
            accessFixture.access |= Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
            var accessor = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "modernui$getRegisteredPipelines", "()Ljava/util/List;", null, null);
            accessor.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, P, "PIPELINES", "Ljava/util/List;")); accessor.instructions.add(new InsnNode(Opcodes.ARETURN));
            accessFixture.methods.add(accessor); classes.put(accessName.replace('/', '.'), bytes(accessFixture));
            ClassLoader loader = new ClassLoader(VulkanPipelineLifecycleCheck.class.getClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> type = findLoadedClass(name);
                        if (type == null) { byte[] code = classes.get(name); type = code == null ? super.loadClass(name, false) : defineClass(name, code, 0, code.length); }
                        if (resolve) resolveClass(type); return type;
                    }
                }
            };
            Class<?> ownerClass = loader.loadClass(G.replace('/', '.')), baseClass = loader.loadClass(P.replace('/', '.')), descriptorClass = loader.loadClass(D.replace('/', '.'));
            var registry = new ArrayList<>(); set(baseClass, null, "PIPELINES", registry);
            var nativeHandles = new HashSet<Long>();
            var register = ownerClass.getMethod("registerActualOwner", ownerClass);
            var cleanup = ownerClass.getMethod("cleanUp");
            var created = new ArrayList<>();
            for (int n = 0; n < 4; n++) {
                Object value = NativeCalls.allocate(ownerClass); created.add(value);
                long offset = 100L * (n + 1);
                for (String field : List.of("vertShaderModule", "fragShaderModule", "descriptorSetLayout", "pipelineLayout")) { long handle = ++offset; set(ownerClass, value, field, handle); nativeHandles.add(handle); }
                Object frameArray = java.lang.reflect.Array.newInstance(descriptorClass, 2);
                for (int frame = 0; frame < 2; frame++) { Object d = NativeCalls.allocate(descriptorClass); long handle = ++offset; set(descriptorClass, d, "descriptorPool", handle); nativeHandles.add(handle); java.lang.reflect.Array.set(frameArray, frame, d); }
                set(ownerClass, value, "descriptorSets", frameArray);
                set(ownerClass, value, "vertexInputDescription", NativeCalls.allocate(loader.loadClass((G + "$VertexInputDescription").replace('/', '.'))));
                var map = new it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<>(); long handle = ++offset; map.put((Object) null, handle); nativeHandles.add(handle); set(ownerClass, value, "graphicsPipelines", map);
                register.invoke(null, value);
            }
            if (registry.size() != 4) throw new AssertionError("Actual owner registration failed");
            // Earlier built-in cleanup or drained Reforged frame operation.
            cleanup.invoke(created.getFirst());
            if (registry.size() != 3) throw new AssertionError("Actual cleanup did not unregister owner");
            var productionHandler = loader.loadClass(handlerFixture.name.replace('/', '.')).getMethod("modernui$closeRemainingPipelines", org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
            productionHandler.invoke(null, new Object[]{null});
            productionHandler.invoke(null, new Object[]{null});
            if (!registry.isEmpty() || removed != 4 || !destroyed.keySet().equals(nativeHandles) || destroyed.values().stream().anyMatch(count -> count != 1)) throw new AssertionError("Owner/native cleanup mismatch: " + path + " " + destroyed);
            System.out.println(path.getFileName() + ": real registered owners=4, native children=" + nativeHandles.size() + ", remaining=0, double destroy=0");
        }
    }

    private static void verifyHook() throws Exception {
        try (var input = VulkanPipelineLifecycleCheck.class.getResourceAsStream("/icyllis/modernui/mc/mixin/MixinVulkanPipelineLifecycle.class")) {
            if (input == null) throw new AssertionError("Missing production mixin");
            var node = new ClassNode(); new ClassReader(input).accept(node, 0);
            var handler = node.methods.stream().filter(m -> m.name.equals("modernui$closeRemainingPipelines")).findFirst().orElseThrow();
            var annotation = handler.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
            Map<String,Object> inject = values(annotation);
            if (!List.of("cleanUp()V").equals(inject.get("method")) || !Integer.valueOf(1).equals(inject.get("require")) || !Integer.valueOf(1).equals(inject.get("allow"))) throw new AssertionError("Shutdown selector must be exact");
            var at = (AnnotationNode) ((List<?>) inject.get("at")).getFirst(); var point = values(at);
            if (!"Lnet/vulkanmod/vulkan/memory/MemoryManager;freeAllBuffers()V".equals(point.get("target")) || !"AFTER".equals(((String[]) point.get("shift"))[1])) throw new AssertionError("Cleanup must follow normal frame-operation drain");
        }
    }
    private static Map<String,Object> values(AnnotationNode n) { var map = new HashMap<String,Object>(); for(int i=0;i<n.values.size();i+=2) map.put((String)n.values.get(i), n.values.get(i+1)); return map; }
    private static ClassNode read(JarFile jar, String name) throws Exception { try(var in=jar.getInputStream(jar.getJarEntry(name+".class"))) { var node=new ClassNode(); new ClassReader(in).accept(node,0); return node; } }
    private static MethodNode method(ClassNode n,String name,String desc) { return n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow(); }
    private static ClassNode shell(String name,String parent) { var n=new ClassNode(); n.version=Opcodes.V21; n.access=Opcodes.ACC_PUBLIC; n.name=name; n.superName=parent; return n; }
    private static void fields(ClassNode from,ClassNode to,String... names) { for(String name:names) { var f=from.fields.stream().filter(v->v.name.equals(name)).findFirst().orElseThrow(); to.fields.add(new FieldNode((f.access & Opcodes.ACC_STATIC)|Opcodes.ACC_PUBLIC,f.name,f.desc,null,null)); } }
    private static byte[] bytes(ClassNode n) { var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS); n.accept(writer); return writer.toByteArray(); }
    private static byte[] simpleStub(String owner,String name,String desc) { var n=shell(owner,"java/lang/Object"); var m=new MethodNode(Opcodes.ACC_PUBLIC,name,desc,null,null); m.instructions.add(new InsnNode(Opcodes.RETURN)); n.methods.add(m); return bytes(n); }
    private static void set(Class<?> type,Object owner,String name,Object value) throws Exception { Field f=null; for(Class<?> c=type;c!=null;c=c.getSuperclass()) try { f=c.getDeclaredField(name); break; } catch(NoSuchFieldException ignored){} if(f==null)throw new NoSuchFieldException(name); f.setAccessible(true); f.set(owner,value); }

    /** Only native operations are replaced; the release Java owner controls all destruction. */
    public static final class NativeCalls {
        public static Object allocate(Class<?> type) { try { var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); return ((sun.misc.Unsafe)field.get(null)).allocateInstance(type); } catch(Exception e) { throw new AssertionError(e); } }
        public static void removed() { removed++; }
        private static void destroy(long handle) { if(handle!=0) destroyed.merge(handle,1,Integer::sum); }
        public static void vkDestroyShaderModule(VkDevice d,long h,VkAllocationCallbacks a){destroy(h);}
        public static void vkDestroyPipeline(VkDevice d,long h,VkAllocationCallbacks a){destroy(h);}
        public static void vkDestroyDescriptorSetLayout(VkDevice d,long h,VkAllocationCallbacks a){destroy(h);}
        public static void vkDestroyPipelineLayout(VkDevice d,long h,VkAllocationCallbacks a){destroy(h);}
        public static void vkDestroyDescriptorPool(VkDevice d,long h,VkAllocationCallbacks a){destroy(h);}
        public static int vkResetDescriptorPool(VkDevice d,long h,int flags){return 0;}
        public static void memFree(Buffer buffer){}
    }
}
