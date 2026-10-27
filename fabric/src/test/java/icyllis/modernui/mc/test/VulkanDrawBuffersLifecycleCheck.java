package icyllis.modernui.mc.test;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/** Runs actual 0.6.8 owner free and Buffer.scheduleFree/MemoryManager queue bytecode, without a GPU. */
public final class VulkanDrawBuffersLifecycleCheck {
    private static final String DRAW = "net/vulkanmod/render/chunk/buffer/DrawBuffers";
    private static final String BUFFER = "net/vulkanmod/vulkan/memory/buffer/Buffer";
    private static final String UNIFORM = "net/vulkanmod/vulkan/memory/buffer/UniformBuffer";
    private static final String MEMORY = "net/vulkanmod/vulkan/memory/MemoryManager";
    private static final String TYPE = "net/vulkanmod/vulkan/memory/MemoryType";
    private static final String AREA = "net/vulkanmod/render/chunk/buffer/AreaBuffer";
    private static final String PARAMS = "net/vulkanmod/render/chunk/buffer/DrawParametersBuffer";
    private static final String MIXIN = "icyllis/modernui/mc/mixin/MixinVulkanDrawBuffersLifecycle";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new AssertionError("Pass the actual VulkanMod 0.6.8 release JAR");
        try (var jar = new JarFile(Path.of(args[0]).toFile())) {
            ClassNode draw = read(jar, DRAW), buffer = read(jar, BUFFER), memory = read(jar, MEMORY);
            if (draw.fields.stream().noneMatch(f -> f.name.equals("sectionDataBuffer") && f.desc.equals("L" + UNIFORM + ";") && (f.access & Opcodes.ACC_FINAL) != 0)) throw new AssertionError("Missing actual 0.6.8 section data owner");
            var ctor = draw.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
            int allocations = 0, writes = 0;
            for (var i : ctor.instructions) {
                if (i instanceof IntInsnNode size && size.operand == 4096) allocations++;
                if (i instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD && f.name.equals("sectionDataBuffer")) writes++;
            }
            if (allocations != 1 || writes != 1) throw new AssertionError("Expected one unconditional 4096-byte constructor owner");
            if (countCalls(ctor, UNIFORM, "<init>") != 1 || hasBranch(ctor)) throw new AssertionError("Section data allocation must not depend on terrain allocated flag");
            for (String name : List.of("free", "releaseBuffers")) {
                for (var i : method(draw,name,"()V").instructions) if (i instanceof FieldInsnNode f && f.name.equals("sectionDataBuffer")) throw new AssertionError("Baseline now releases section data; review compatibility patch");
            }
            ClassNode production;
            try (var input = VulkanDrawBuffersLifecycleCheck.class.getResourceAsStream("/" + MIXIN + ".class")) { if(input==null)throw new AssertionError("Missing production mixin"); production = new ClassNode(); new ClassReader(input).accept(production,0); }
            var handler = production.methods.stream().filter(m -> m.name.equals("modernui$releaseSectionData")).findFirst().orElseThrow();
            var inject = handler.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
            Map<String,Object> annotation = values(inject);
            if (!List.of("free()V").equals(annotation.get("method")) || !Integer.valueOf(1).equals(annotation.get("require")) || !Integer.valueOf(1).equals(annotation.get("allow")) || !"HEAD".equals(values((AnnotationNode)((List<?>)annotation.get("at")).getFirst()).get("value"))) throw new AssertionError("Terminal owner hook must be exact and required");
            var owner = shell(DRAW,"java/lang/Object");
            fields(draw,owner,"allocated","vertexBuffers","indexBuffer","drawParamsPtr","sectionDataBuffer");
            owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"modernui$sectionDataReleased","Z",null,null));
            owner.methods.add(method(draw,"releaseBuffers","()V"));
            MethodNode baseline = method(draw,"free","()V"); baseline.name="baselineFree"; owner.methods.add(baseline);
            MethodNode fixed = method(read(jar,DRAW),"free","()V");
            var prefix = new InsnList(); prefix.add(new VarInsnNode(Opcodes.ALOAD,0)); prefix.add(new InsnNode(Opcodes.ACONST_NULL)); prefix.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,DRAW,handler.name,handler.desc,false)); fixed.instructions.insert(prefix); owner.methods.add(fixed);
            handler.access=Opcodes.ACC_PUBLIC;
            for(var i:handler.instructions) if(i instanceof FieldInsnNode f && f.owner.equals(MIXIN))f.owner=DRAW;
            owner.methods.add(handler);
            var b = shell(BUFFER,"java/lang/Object"); fields(buffer,b,"id","allocation","bufferSize","type");
            b.methods.add(method(buffer,"scheduleFree","()V")); b.methods.add(method(buffer,"getBufferInfo","()L"+BUFFER+"$BufferInfo;"));
            var mm = shell(MEMORY,"java/lang/Object"); fields(memory,mm,"INSTANCE","buffers","currentFrame","freeableBuffers");
            for(var m:memory.methods) if(m.name.equals("getInstance") || m.name.equals("checkBuffer") || m.name.equals("addToFreeable") && m.desc.equals("(L"+BUFFER+";)V")) mm.methods.add(m);
            var mt = shell(TYPE,"java/lang/Object");
            var getType = new MethodNode(Opcodes.ACC_PUBLIC,"getType","()L"+TYPE+"$Type;",null,null); getType.instructions.add(new InsnNode(Opcodes.ACONST_NULL));getType.instructions.add(new InsnNode(Opcodes.ARETURN));mt.methods.add(getType);
            var map = new HashMap<String,byte[]>();
            for(var n:List.of(owner,b,mm,mt,shell(UNIFORM,BUFFER),shell(TYPE+"$Type","java/lang/Object")))map.put(n.name.replace('/','.'),bytes(n));
            map.put((BUFFER+"$BufferInfo").replace('/','.'),jar.getInputStream(jar.getJarEntry(BUFFER+"$BufferInfo.class")).readAllBytes());
            map.put(AREA.replace('/','.'),stub(AREA,"freeBuffer","()V",false));
            map.put(PARAMS.replace('/','.'),stub(PARAMS,"freeBuffer","(J)V",true));
            var loader = new ClassLoader(VulkanDrawBuffersLifecycleCheck.class.getClassLoader()) {
                @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException { synchronized(getClassLoadingLock(name)){Class<?> c=findLoadedClass(name);if(c==null){byte[] code=map.get(name);c=code==null?super.loadClass(name,false):defineClass(name,code,0,code.length);}if(resolve)resolveClass(c);return c;} }
            };
            Class<?> drawClass=loader.loadClass(DRAW.replace('/','.')), uniformClass=loader.loadClass(UNIFORM.replace('/','.')), memoryClass=loader.loadClass(MEMORY.replace('/','.'));
            Object manager=allocate(memoryClass);set(memoryClass,null,"INSTANCE",manager);
            var live=new it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap<>();set(memoryClass,null,"buffers",live);
            var queue=new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();set(memoryClass,manager,"freeableBuffers",new it.unimi.dsi.fastutil.objects.ObjectArrayList[]{queue});
            long handle=1000;
            for(boolean allocated:List.of(false,true)) {
                for(boolean patched:List.of(false,true)) {
                    Object value=allocate(drawClass), uniform=allocate(uniformClass);set(uniformClass,uniform,"id",++handle);set(uniformClass,uniform,"allocation",handle+1000);set(uniformClass,uniform,"bufferSize",4096L);set(uniformClass,uniform,"type",allocate(loader.loadClass(TYPE.replace('/','.'))));live.put(handle,uniform);
                    set(drawClass,value,"sectionDataBuffer",uniform);set(drawClass,value,"allocated",allocated);set(drawClass,value,"vertexBuffers",new EnumMap<>(Layer.class));
                    int before=queue.size();
                    if (patched) {
                        drawClass.getMethod("releaseBuffers").invoke(value);
                        if (queue.size()!=before) throw new AssertionError("Reusable terrain release freed section data");
                        set(drawClass,value,"allocated",allocated);
                    }
                    drawClass.getMethod(patched?"free":"baselineFree").invoke(value);
                    if(queue.size()!=before+(patched?1:0))throw new AssertionError("Actual schedule queue mismatch allocated="+allocated+" patched="+patched);
                    if(patched) {
                        Object info=queue.getLast();if(!Long.valueOf(handle).equals(info.getClass().getMethod("id").invoke(info)) || !Long.valueOf(4096).equals(info.getClass().getMethod("bufferSize").invoke(info)))throw new AssertionError("Queued wrong allocation owner");
                        drawClass.getMethod(handler.name,org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class).invoke(value,new Object[]{null});
                        if(queue.size()!=before+1)throw new AssertionError("Duplicate scheduleFree");
                    }
                }
            }
            System.out.println("VulkanMod 0.6.8 actual free negative controls (allocated=false/true), production handler and actual MemoryManager queue passed; two section allocations queued once");
        }
    }
    enum Layer { SOLID }
    private static boolean hasBranch(MethodNode m){for(var i:m.instructions)if(i instanceof JumpInsnNode||i instanceof TableSwitchInsnNode||i instanceof LookupSwitchInsnNode)return true;return false;}
    private static long countCalls(MethodNode m,String owner,String name){long n=0;for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals(owner)&&c.name.equals(name))n++;return n;}
    private static Map<String,Object> values(AnnotationNode n){var map=new HashMap<String,Object>();for(int i=0;i<n.values.size();i+=2)map.put((String)n.values.get(i),n.values.get(i+1));return map;}
    private static ClassNode read(JarFile jar,String name)throws Exception{try(var in=jar.getInputStream(jar.getJarEntry(name+".class"))){var n=new ClassNode();new ClassReader(in).accept(n,0);return n;}}
    private static MethodNode method(ClassNode n,String name,String desc){return n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
    private static ClassNode shell(String name,String parent){var n=new ClassNode();n.version=Opcodes.V21;n.access=Opcodes.ACC_PUBLIC;n.name=name;n.superName=parent;return n;}
    private static void fields(ClassNode from,ClassNode to,String... names){for(String name:names){var f=from.fields.stream().filter(v->v.name.equals(name)).findFirst().orElseThrow();to.fields.add(new FieldNode((f.access&Opcodes.ACC_STATIC)|Opcodes.ACC_PUBLIC,f.name,f.desc,null,null));}}
    private static byte[] bytes(ClassNode n){var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);return w.toByteArray();}
    private static byte[] stub(String owner,String name,String desc,boolean isStatic){var n=shell(owner,"java/lang/Object");var m=new MethodNode(Opcodes.ACC_PUBLIC|(isStatic?Opcodes.ACC_STATIC:0),name,desc,null,null);m.instructions.add(new InsnNode(Opcodes.RETURN));n.methods.add(m);return bytes(n);}
    private static Object allocate(Class<?> type)throws Exception{var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);return((sun.misc.Unsafe)f.get(null)).allocateInstance(type);}
    private static void set(Class<?> type,Object owner,String name,Object value)throws Exception{Field f=null;for(Class<?> c=type;c!=null;c=c.getSuperclass())try{f=c.getDeclaredField(name);break;}catch(NoSuchFieldException ignored){}if(f==null)throw new NoSuchFieldException(name);f.setAccessible(true);f.set(owner,value);}
}
