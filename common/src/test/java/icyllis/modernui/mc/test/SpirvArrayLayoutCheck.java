package icyllis.modernui.mc.test;

import icyllis.modernui.mc.SpirvLayoutCompat;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Compiles real shaders with the actual redirect bytecode, without a GPU or Mixin platform. */
public final class SpirvArrayLayoutCheck {
    private static final String GENERATOR = "icyllis.arc3d.compiler.spirv.SPIRVCodeGenerator";
    private static final String MIXIN = "icyllis/modernui/mc/mixin/MixinSPIRVCodeGenerator";
    private static final String SOURCE = """
            layout(set=0, binding=0) uniform Uniforms { vec4 values[2]; };
            layout(location=0) out vec4 color;
            void main() {
                vec4 local[2] = vec4[2](values[0], values[1]);
                int index = int(values[0].x) & 1;
                color = local[index];
            }
            """;

    private static final String WHOLE_ARRAY = """
            layout(set=0, binding=0) uniform Uniforms { vec4 values[2]; };
            layout(location=0) out vec4 color;
            vec4 sum(vec4 items[2]) { return items[0] + items[1]; }
            void main() { vec4 local[2] = values; color = sum(local) + sum(values); }
            """;
    private static final String COPY_BACK = """
            layout(set=0, binding=0, std430) buffer Uniforms { vec4 values[2]; };
            layout(location=0) out vec4 color;
            void swap(inout vec4 items[2]) {
                vec4 first = items[0]; items[0] = items[1]; items[1] = first;
            }
            void main() { swap(values); color = values[0]; }
            """;

    public static void main(String[] args) throws Exception {
        var original = compile(false, "VULKAN_1_0");
        var fixed = compile(true, "VULKAN_1_0");
        var glOriginal = compile(false, "OPENGL_4_5");
        var glFixed = compile(true, "OPENGL_4_5");
        check(original, true);
        check(fixed, false);
        byte[] storage = compile(true, "VULKAN_1_0", SOURCE.replace("uniform Uniforms", "buffer Uniforms"));
        check(storage, false);
        check(glOriginal, true);
        check(glFixed, true);
        var fixtures = new LinkedHashMap<String, byte[]>();
        fixtures.put("fixed", fixed);
        fixtures.put("storage", storage);
        for (int size : new int[]{2, 4}) {
            String whole = WHOLE_ARRAY.replace("[2]", "[" + size + "]");
            byte[] disconnected = compile(true, "VULKAN_1_0", whole, false);
            try {
                checkTypeLinks(disconnected);
                throw new AssertionError("Fixture failed to reproduce the original disconnected AccessChain");
            } catch (IllegalStateException expected) {
                // The original redirect alone must reproduce the real GPU validation failure.
            }
            fixtures.put("uniform-whole-" + size, compile(true, "VULKAN_1_0", whole));
            fixtures.put("storage-copyback-" + size,
                    compile(true, "VULKAN_1_0", COPY_BACK.replace("[2]", "[" + size + "]")));
        }
        ByteBuffer unchanged = ByteBuffer.wrap(fixed).order(ByteOrder.nativeOrder());
        if (SpirvLayoutCompat.repair(unchanged) != unchanged) throw new AssertionError("Unchanged modules must retain their buffer");
        checkRejectedShapeChange(compile(true, "VULKAN_1_0", WHOLE_ARRAY, false));
        for (var fixture : fixtures.entrySet()) {
            checkTypeLinks(fixture.getValue());
            validate(fixture.getKey(), fixture.getValue());
        }
        if (args.length > 0) {
            Files.createDirectories(Path.of(args[0]));
            Files.write(Path.of(args[0], "gl-original.spv"), glOriginal);
            Files.write(Path.of(args[0], "gl-fixed.spv"), glFixed);
            Files.write(Path.of(args[0], "original.spv"), original);
            Files.write(Path.of(args[0], "fixed.spv"), fixed);
            for (var fixture : fixtures.entrySet()) {
                Files.write(Path.of(args[0], fixture.getKey() + ".spv"), fixture.getValue());
            }
        }
        System.out.println("Real Arc3D SPIR-V: Vulkan local layouts and block AccessChain/load/store types validated, including uniform array parameters and storage copyback; OpenGL preserved");
    }

    private static byte[] compile(boolean fixed, String target) throws Exception {
        return compile(fixed, target, SOURCE);
    }

    private static byte[] compile(boolean fixed, String target, String source) throws Exception {
        return compile(fixed, target, source, true);
    }

    private static byte[] compile(boolean fixed, String target, String source, boolean reconnect) throws Exception {
        ClassLoader loader = new ClassLoader(SpirvArrayLayoutCheck.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("icyllis.arc3d.compiler.")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try {
                            byte[] bytes = resource(name.replace('.', '/') + ".class");
                            if (fixed && name.equals(GENERATOR)) bytes = applyActualRedirect(bytes, reconnect);
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (Exception e) { throw new ClassNotFoundException(name, e); }
                    }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
        Class<?> compilerType = loader.loadClass("icyllis.arc3d.compiler.ShaderCompiler");
        Object compiler = compilerType.getConstructor().newInstance();
        Class<?> capsType = loader.loadClass("icyllis.arc3d.compiler.ShaderCaps");
        Object caps = capsType.getConstructor().newInstance();
        capsType.getField("mTargetApi").set(caps, enumValue(loader, "TargetApi", target));
        capsType.getField("mSPIRVVersion").set(caps, enumValue(loader, "SPIRVVersion", "SPIRV_1_5"));
        Class<?> moduleLoaderType = loader.loadClass("icyllis.arc3d.compiler.ModuleLoader");
        Object moduleLoader = moduleLoaderType.getMethod("getInstance").invoke(null);
        Object parent = moduleLoaderType.getMethod("loadGLSLFragModule", compilerType).invoke(moduleLoader, compiler);
        Class<?> optionsType = loader.loadClass("icyllis.arc3d.compiler.CompileOptions");
        Object options = optionsType.getConstructor().newInstance();
        Method compile = compilerType.getMethod("compileIntoSPIRV", String.class,
                loader.loadClass("icyllis.arc3d.compiler.ShaderKind"), capsType, optionsType,
                loader.loadClass("icyllis.arc3d.compiler.ModuleUnit"));
        ByteBuffer buffer = (ByteBuffer) compile.invoke(compiler, target.startsWith("OPENGL") ? source.replace("set=0, ", "") : source,
                enumValue(loader, "ShaderKind", "FRAGMENT"), caps, options, parent);
        if (buffer == null) throw new AssertionError(compilerType.getMethod("getErrorMessage").invoke(compiler));
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(ClassLoader loader, String type, String name) throws Exception {
        return Enum.valueOf((Class) loader.loadClass("icyllis.arc3d.compiler." + type), name);
    }

    // Read the actual production annotations and transplant its actual handler.
    // This fails if the pinned compiler's injection point or signature changes.
    @SuppressWarnings("unchecked")
    private static byte[] applyActualRedirect(byte[] original, boolean reconnect) throws IOException {
        ClassNode mixin = new ClassNode();
        new ClassReader(resource(MIXIN + ".class")).accept(mixin, 0);
        MethodNode handler = mixin.methods.stream().filter(m -> m.name.startsWith("modernui$")).findFirst().orElseThrow();
        AnnotationNode redirect = handler.visibleAnnotations.stream()
                .filter(a -> a.desc.endsWith("/Redirect;")).findFirst().orElseThrow();
        Map<String, Object> redirectValues = annotationValues(redirect);
        String method = ((List<String>) redirectValues.get("method")).getFirst();
        AnnotationNode at = (AnnotationNode) redirectValues.get("at");
        var atValues = annotationValues(at);
        String target = (String) atValues.get("target");
        int ordinal = (Integer) atValues.get("ordinal");
        ClassNode generator = new ClassNode();
        new ClassReader(original).accept(generator, 0);
        int split = method.indexOf('(');
        MethodNode emitter = generator.methods.stream().filter(m -> m.name.equals(method.substring(0, split))
                && m.desc.equals(method.substring(split))).findFirst().orElseThrow();
        int matches = 0;
        boolean injected = false;
        for (AbstractInsnNode instruction : emitter.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode call
                    && target.equals("L" + call.owner + ";" + call.name + call.desc)) {
                if (matches++ != ordinal) continue;
                int local = emitter.maxLocals;
                emitter.maxLocals += 2;
                InsnList receiver = new InsnList();
                receiver.add(new VarInsnNode(Opcodes.ASTORE, local + 1));
                receiver.add(new VarInsnNode(Opcodes.ASTORE, local));
                receiver.add(new VarInsnNode(Opcodes.ALOAD, 0));
                receiver.add(new VarInsnNode(Opcodes.ALOAD, local));
                receiver.add(new VarInsnNode(Opcodes.ALOAD, local + 1));
                emitter.instructions.insertBefore(call, receiver);
                emitter.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESPECIAL,
                        generator.name, handler.name, handler.desc, false));
                injected = true;
            }
        }
        if (!injected || matches != 2) throw new AssertionError("Arc3D default layout injection point changed");
        for (AbstractInsnNode instruction : handler.instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals(MIXIN)) field.owner = generator.name;
        }
        handler.visibleAnnotations = null;
        handler.invisibleAnnotations = null;
        generator.methods.add(handler);
        if (reconnect) {
            MethodNode completion = mixin.methods.stream().filter(m -> m.name.equals("modernui$connectBlockArrayTypes"))
                    .findFirst().orElseThrow();
            AnnotationNode inject = completion.visibleAnnotations.stream()
                    .filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
            var injectValues = annotationValues(inject);
            String generation = ((List<String>) injectValues.get("method")).getFirst();
            AnnotationNode location = ((List<AnnotationNode>) injectValues.get("at")).getFirst();
            if (!"RETURN".equals(annotationValues(location).get("value"))
                    || !Boolean.TRUE.equals(injectValues.get("cancellable"))) {
                throw new AssertionError("Layout repair injection changed");
            }
            int generationSplit = generation.indexOf('(');
            MethodNode generate = generator.methods.stream().filter(m -> m.name.equals(generation.substring(0, generationSplit))
                    && m.desc.equals(generation.substring(generationSplit))).findFirst().orElseThrow();
            String callback = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
            int result = generate.maxLocals++, callbackLocal = generate.maxLocals++;
            int returns = 0;
            for (AbstractInsnNode instruction : generate.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.ARETURN) continue;
                InsnList invoke = new InsnList();
                invoke.add(new VarInsnNode(Opcodes.ASTORE, result));
                invoke.add(new TypeInsnNode(Opcodes.NEW, callback));
                invoke.add(new InsnNode(Opcodes.DUP));
                invoke.add(new LdcInsnNode("generateCode"));
                invoke.add(new InsnNode(Opcodes.ICONST_1));
                invoke.add(new VarInsnNode(Opcodes.ALOAD, result));
                invoke.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, callback, "<init>", "(Ljava/lang/String;ZLjava/lang/Object;)V", false));
                invoke.add(new VarInsnNode(Opcodes.ASTORE, callbackLocal));
                invoke.add(new VarInsnNode(Opcodes.ALOAD, 0));
                invoke.add(new VarInsnNode(Opcodes.ALOAD, callbackLocal));
                invoke.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, generator.name, completion.name, completion.desc, false));
                invoke.add(new VarInsnNode(Opcodes.ALOAD, callbackLocal));
                invoke.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, callback, "getReturnValue", "()Ljava/lang/Object;", false));
                invoke.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/nio/ByteBuffer"));
                generate.instructions.insertBefore(instruction, invoke);
                returns++;
            }
            if (returns == 0) throw new AssertionError("Missing generateCode return injection point");
            for (AbstractInsnNode instruction : completion.instructions) {
                if (instruction instanceof FieldInsnNode field && field.owner.equals(MIXIN)) field.owner = generator.name;
            }
            completion.visibleAnnotations = null;
            completion.invisibleAnnotations = null;
            generator.methods.add(completion);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        generator.accept(writer);
        return writer.toByteArray();
    }

    private static Map<String, Object> annotationValues(AnnotationNode annotation) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < annotation.values.size(); i += 2) {
            result.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        }
        return result;
    }

    private static byte[] resource(String name) throws IOException {
        try (var stream = SpirvArrayLayoutCheck.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) throw new IOException("Missing " + name);
            return stream.readAllBytes();
        }
    }

    private static void checkRejectedShapeChange(byte[] disconnected) {
        var data = ByteBuffer.wrap(disconnected.clone()).order(ByteOrder.nativeOrder()).asIntBuffer();
        Set<Integer> decorated = new HashSet<>();
        int one = 0;
        for (int offset = 5; offset < data.limit(); offset += data.get(offset) >>> 16) {
            int opcode = data.get(offset) & 0xffff;
            if (opcode == 71 && data.get(offset + 2) == 6) decorated.add(data.get(offset + 1));
            if (opcode == 43 && (data.get(offset) >>> 16) == 4 && data.get(offset + 3) == 1) one = data.get(offset + 2);
        }
        if (one == 0) throw new AssertionError("Missing length constant");
        for (int offset = 5; offset < data.limit(); offset += data.get(offset) >>> 16) {
            if ((data.get(offset) & 0xffff) == 28 && !decorated.contains(data.get(offset + 1))) {
                data.put(offset + 3, one); // Deliberately change only the logical array length.
                byte[] changed = new byte[data.limit() * 4];
                var output = ByteBuffer.wrap(changed).order(ByteOrder.nativeOrder()).asIntBuffer();
                output.put(data);
                try {
                    SpirvLayoutCompat.repair(ByteBuffer.wrap(changed).order(ByteOrder.nativeOrder()));
                    throw new AssertionError("Non-equivalent array types were accepted");
                } catch (IllegalStateException expected) {
                    if (!expected.getMessage().contains("differ beyond explicit array layout")) throw expected;
                }
                return;
            }
        }
        throw new AssertionError("Missing logical array type");
    }

    private static void validate(String name, byte[] bytes) throws Exception {
        String validator = System.getProperty("modernui.spirvValidator");
        if (validator == null) return;
        Path path = Files.createTempFile("modernui-" + name, ".spv");
        try {
            Files.write(path, bytes);
            Process process = new ProcessBuilder(validator, "--target-env", "vulkan1.2", path.toString())
                    .redirectErrorStream(true).start();
            String diagnostic = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (process.waitFor() != 0) throw new AssertionError(name + ": " + diagnostic);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    /** Independently reject the exact ID mismatches reported by Vulkan validation. */
    private static void checkTypeLinks(byte[] bytes) {
        var input = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asIntBuffer();
        var types = new HashMap<Integer, int[]>();
        var constants = new HashMap<Integer, Integer>();
        var instructions = new ArrayList<int[]>();
        for (int offset = 5; offset < input.limit();) {
            int count = input.get(offset) >>> 16;
            int[] instruction = new int[count];
            input.get(offset, instruction);
            instructions.add(instruction);
            int op = instruction[0] & 0xffff;
            if (op >= 19 && op <= 39) types.put(instruction[1], instruction);
            if (op == 43 && count == 4) constants.put(instruction[2], instruction[3]);
            offset += count;
        }
        var values = new HashMap<Integer, Integer>();
        for (int[] instruction : instructions) {
            int op = instruction[0] & 0xffff;
            if (op > 39 && op != 71 && op != 72 && instruction.length >= 3 && types.containsKey(instruction[1])) {
                values.put(instruction[2], instruction[1]);
            }
        }
        for (int[] instruction : instructions) {
            int op = instruction[0] & 0xffff;
            if (op == 65 || op == 66) {
                int[] basePointer = types.get(values.get(instruction[3]));
                int element = basePointer[3];
                for (int i = 4; i < instruction.length; i++) {
                    int[] composite = types.get(element);
                    element = (composite[0] & 0xffff) == 30
                            ? composite[2 + constants.get(instruction[i])] : composite[2];
                }
                int[] pointer = types.get(instruction[1]);
                if (pointer[2] != basePointer[2] || pointer[3] != element) {
                    throw new IllegalStateException("AccessChain does not exactly match its base member type");
                }
            } else if (op == 61) {
                if (types.get(values.get(instruction[3]))[3] != instruction[1]) {
                    throw new IllegalStateException("OpLoad does not exactly match its pointer element type");
                }
            } else if (op == 62) {
                if (types.get(values.get(instruction[1]))[3] != values.get(instruction[2])) {
                    throw new IllegalStateException("OpStore does not exactly match its pointer element type");
                }
            }
        }
    }

    private static void check(byte[] bytes, boolean expectInvalidLocalLayout) {
        var words = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asIntBuffer();
        Map<Integer, Integer> pointers = new HashMap<>();
        Set<Integer> arrays = new HashSet<>();
        Set<Integer> decorated = new HashSet<>();
        Set<Integer> localPointers = new HashSet<>();
        for (int i = 5; i < words.limit();) {
            int instruction = words.get(i), count = instruction >>> 16, opcode = instruction & 0xffff;
            if (count == 0) throw new AssertionError("Invalid SPIR-V instruction");
            if (opcode == 28) arrays.add(words.get(i + 1)); // OpTypeArray
            if (opcode == 32) pointers.put(words.get(i + 1), words.get(i + 3)); // OpTypePointer
            if (opcode == 71 && words.get(i + 2) == 6) { // OpDecorate ArrayStride
                if (words.get(i + 3) != 16) throw new AssertionError("Block array stride changed");
                decorated.add(words.get(i + 1));
            }
            if (opcode == 59 && words.get(i + 3) == 7) localPointers.add(words.get(i + 1)); // Function OpVariable
            i += count;
        }
        Set<Integer> locals = new HashSet<>();
        for (int pointer : localPointers) if (arrays.contains(pointers.get(pointer))) locals.add(pointers.get(pointer));
        if (locals.isEmpty() || decorated.isEmpty()) throw new AssertionError("Fixture did not exercise both local and block arrays");
        boolean invalid = locals.stream().anyMatch(decorated::contains);
        if (invalid != expectInvalidLocalLayout) throw new AssertionError("Unexpected Function ArrayStride: " + invalid);
        if (!expectInvalidLocalLayout && (arrays.size() != 2 || decorated.size() != 1)) {
            throw new AssertionError("Uniform and Function array types were not separated");
        }
    }
}
