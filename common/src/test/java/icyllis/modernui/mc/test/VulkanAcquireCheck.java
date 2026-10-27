package icyllis.modernui.mc.test;

import icyllis.modernui.mc.VulkanAcquireToken;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelector.Configure;
import org.spongepowered.asm.mixin.injection.selectors.ElementNode;

import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

/** Exercises production binary-semaphore ownership and actual release-JAR injection points. */
public final class VulkanAcquireCheck {
    public static void main(String[] args) throws Exception {
        var token = new VulkanAcquireToken();
        unchanged(token, new long[]{900}, new int[]{VK_PIPELINE_STAGE_TRANSFER_BIT});
        token.acquired(VK_ERROR_OUT_OF_DATE_KHR, 100);
        unchanged(token, new long[0], new int[0]);
        token.acquired(VK_SUCCESS, 100);
        // Negative control: the unmodified resize submit has no acquire wait.
        expect(token, new long[0], new int[0], new long[]{100}, new int[]{VK_PIPELINE_STAGE_ALL_COMMANDS_BIT});
        unchanged(token, new long[]{100, 900}, new int[]{VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT});
        token.submitted(VK_ERROR_DEVICE_LOST);
        expect(token, new long[0], new int[0], new long[]{100}, new int[]{VK_PIPELINE_STAGE_ALL_COMMANDS_BIT});
        token.acquired(VK_ERROR_OUT_OF_DATE_KHR, 200); // Does not erase the earlier live signal.
        expect(token, new long[0], new int[0], new long[]{100}, new int[]{VK_PIPELINE_STAGE_ALL_COMMANDS_BIT});
        token.submitted(VK_SUCCESS);
        // flushCmds already consumed the acquire signal; normal submit must not wait twice.
        expect(token, new long[]{900, 100}, new int[]{VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT},
                new long[]{900}, new int[]{VK_PIPELINE_STAGE_TRANSFER_BIT});
        unchanged(token, new long[0], new int[0]);
        token.acquired(VK_SUCCESS, 100); // Next frame may reuse the consumed semaphore.
        token.acquired(VK_SUBOPTIMAL_KHR, 200);
        expect(token, new long[]{900}, new int[]{VK_PIPELINE_STAGE_TRANSFER_BIT},
                new long[]{900, 100, 200}, new int[]{VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT});
        expect(token, new long[]{100, 100, 200}, new int[]{1, 2, 3}, new long[]{100, 200}, new int[]{1, 3});
        try {
            token.acquired(VK_SUCCESS, 100);
            throw new AssertionError("Reusing a pending binary signal was accepted");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("before its signal was consumed")) throw expected;
        }
        // Recursive SUBOPTIMAL recreation destroys the old sync objects before a new acquire.
        token.syncObjectsDestroyed();
        unchanged(token, new long[0], new int[0]);
        token.acquired(VK_ERROR_OUT_OF_DATE_KHR, 100);
        unchanged(token, new long[0], new int[0]);
        token.acquired(VK_SUCCESS, 100); // Handle reuse after destruction starts a fresh lifetime.
        expect(token, new long[0], new int[0], new long[]{100}, new int[]{VK_PIPELINE_STAGE_ALL_COMMANDS_BIT});
        token.submitted(VK_SUCCESS);
        token.syncObjectsDestroyed();
        // Unrelated waits are preserved even when their numeric handle was used in an old lifetime.
        unchanged(token, new long[]{100, 900}, new int[]{1, 2});

        ClassNode mixin;
        try (var input = VulkanAcquireCheck.class.getResourceAsStream(
                "/icyllis/modernui/mc/mixin/MixinVulkanRenderer.class")) {
            if (input == null) throw new AssertionError("Missing production Renderer mixin");
            mixin = new ClassNode();
            new ClassReader(input).accept(mixin, 0);
        }
        checkNativeSubmit(mixin);
        for (String jar : args) verify(Path.of(jar), mixin);
        System.out.println("Acquire semaphore ownership passed; " + args.length + " actual VulkanMod release injection sets verified");
    }

    private static int nativeResult;
    private static boolean nativeThrows;
    private static long[] observedSemaphores;
    private static int[] observedStages;

    /** Native-call substitute; the surrounding production wrapper is transplanted unchanged. */
    public static int captureSubmission(VkQueue queue, VkSubmitInfo submit, long fence) {
        int count = submit.waitSemaphoreCount();
        observedSemaphores = new long[count];
        observedStages = new int[count];
        for (int i = 0; i < count; i++) {
            observedSemaphores[i] = submit.pWaitSemaphores().get(i);
            observedStages[i] = submit.pWaitDstStageMask().get(i);
        }
        if (nativeThrows) throw new IllegalStateException("Test native submit failure");
        return nativeResult;
    }

    private static void checkNativeSubmit(ClassNode mixin) throws Exception {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var submit = VkSubmitInfo.calloc(stack).sType$Default();
            submit.pWaitSemaphores(stack.longs(100));
            submit.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT));
            if (submit.waitSemaphoreCount() != 0) throw new AssertionError("LWJGL pointer setter count contract changed");

            // Removing only the count setter from the real wrapper reproduces the GPU defect.
            var negative = probe(mixin, true);
            var negativeToken = probeToken(negative);
            negativeToken.acquired(VK_SUCCESS, 100);
            nativeResult = VK_SUCCESS;
            submit.pWaitSemaphores(null).pWaitDstStageMask(null).waitSemaphoreCount(0);
            invokeSubmit(negative, submit);
            if (observedSemaphores.length != 0) throw new AssertionError("Missing-count negative control did not reproduce");

            var production = probe(mixin, false);
            var token = probeToken(production);
            var semaphores = stack.longs(999, 900, 777);
            var stages = stack.ints(1, VK_PIPELINE_STAGE_TRANSFER_BIT, 2);
            semaphores.position(1).limit(2);
            stages.position(1).limit(2);
            submit.pWaitSemaphores(semaphores).pWaitDstStageMask(stages).waitSemaphoreCount(1);
            long originalSemaphores = MemoryUtil.memAddress(semaphores), originalStages = MemoryUtil.memAddress(stages);
            token.acquired(VK_SUCCESS, 100);
            nativeResult = VK_ERROR_DEVICE_LOST;
            invokeSubmit(production, submit);
            observed(new long[]{900, 100}, new int[]{VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT});
            restored(submit, 1, originalSemaphores, originalStages);
            expect(token, new long[0], new int[0], new long[]{100}, new int[]{VK_PIPELINE_STAGE_ALL_COMMANDS_BIT});
            nativeResult = VK_SUCCESS;
            invokeSubmit(production, submit);
            observed(new long[]{900, 100}, new int[]{VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT});
            restored(submit, 1, originalSemaphores, originalStages);
            if (semaphores.position() != 1 || stages.position() != 1) throw new AssertionError("Caller buffer positions changed");

            // The later normal submission still lists the already-consumed binary signal.
            submit.pWaitSemaphores(stack.longs(100)).pWaitDstStageMask(stack.ints(1)).waitSemaphoreCount(1);
            long obsoleteSemaphores = MemoryUtil.memAddress(submit.pWaitSemaphores());
            long obsoleteStages = MemoryUtil.memAddress(submit.pWaitDstStageMask());
            invokeSubmit(production, submit);
            observed(new long[0], new int[0]);
            restored(submit, 1, obsoleteSemaphores, obsoleteStages);

            token.acquired(VK_SUCCESS, 200);
            submit.pWaitSemaphores(null).pWaitDstStageMask(null).waitSemaphoreCount(0);
            nativeThrows = true;
            try {
                invokeSubmit(production, submit);
                throw new AssertionError("Native exception was swallowed");
            } catch (java.lang.reflect.InvocationTargetException expected) {
                if (!"Test native submit failure".equals(expected.getCause().getMessage())) throw expected;
            } finally { nativeThrows = false; }
            restored(submit, 0, 0, 0);
            expect(token, new long[0], new int[0], new long[]{200}, new int[]{VK_PIPELINE_STAGE_ALL_COMMANDS_BIT});
        }
    }

    private static void observed(long[] semaphores, int[] stages) {
        if (!Arrays.equals(observedSemaphores, semaphores) || !Arrays.equals(observedStages, stages)) {
            throw new AssertionError("Actual native VkSubmitInfo count/pointers do not expose the rewritten waits");
        }
    }

    private static void restored(VkSubmitInfo submit, int count, long semaphores, long stages) {
        if (submit.waitSemaphoreCount() != count
                || MemoryUtil.memAddressSafe(submit.pWaitSemaphores()) != semaphores
                || MemoryUtil.memAddressSafe(submit.pWaitDstStageMask()) != stages) {
            throw new AssertionError("Original native VkSubmitInfo count/pointers were not restored");
        }
    }

    private static VulkanAcquireToken probeToken(Object probe) throws Exception {
        var method = probe.getClass().getDeclaredMethod("modernui$acquireToken");
        method.setAccessible(true);
        return (VulkanAcquireToken) method.invoke(probe);
    }

    private static void invokeSubmit(Object probe, VkSubmitInfo submit) throws Exception {
        var method = probe.getClass().getDeclaredMethod("modernui$submit", VkQueue.class, VkSubmitInfo.class, long.class);
        method.setAccessible(true);
        method.invoke(probe, null, submit, 0L);
    }

    private static Object probe(ClassNode production, boolean omitCount) throws Exception {
        ClassNode copy = new ClassNode();
        ClassWriter original = new ClassWriter(0);
        production.accept(original);
        new ClassReader(original.toByteArray()).accept(copy, 0);
        String oldOwner = copy.name;
        copy.name += "$CpuProbe";
        copy.access &= ~Opcodes.ACC_ABSTRACT;
        copy.visibleAnnotations = null;
        copy.invisibleAnnotations = null;
        boolean countRemoved = false;
        for (MethodNode method : copy.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof FrameNode frame) {
                    if (frame.local != null) frame.local.replaceAll(value -> oldOwner.equals(value) ? copy.name : value);
                    if (frame.stack != null) frame.stack.replaceAll(value -> oldOwner.equals(value) ? copy.name : value);
                }
                if (instruction instanceof FieldInsnNode field && field.owner.equals(oldOwner)) field.owner = copy.name;
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals(oldOwner)) call.owner = copy.name;
                    if (call.owner.equals("org/lwjgl/vulkan/VK10") && call.name.equals("vkQueueSubmit")) {
                        call.owner = "icyllis/modernui/mc/test/VulkanAcquireCheck";
                        call.name = "captureSubmission";
                    } else if (omitCount && !countRemoved && method.name.equals("modernui$submit")
                            && call.owner.equals("org/lwjgl/vulkan/VkSubmitInfo") && call.name.equals("waitSemaphoreCount")
                            && call.desc.equals("(I)Lorg/lwjgl/vulkan/VkSubmitInfo;")) {
                        // Setter consumes (submit,count), returns submit. POP only discards count.
                        method.instructions.set(call, new InsnNode(Opcodes.POP));
                        countRemoved = true;
                    }
                }
            }
        }
        if (omitCount && !countRemoved) throw new AssertionError("Production count setter missing");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        copy.accept(writer);
        var loader = new ClassLoader(VulkanAcquireCheck.class.getClassLoader()) {
            Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
        };
        return loader.define(writer.toByteArray()).getConstructor().newInstance();
    }

    private static void unchanged(VulkanAcquireToken token, long[] semaphores, int[] stages) {
        if (token.waits(semaphores, stages) != null) throw new AssertionError("Unrelated/correct waits changed");
    }

    private static void expect(VulkanAcquireToken token, long[] semaphores, int[] stages,
                               long[] expectedSemaphores, int[] expectedStages) {
        long[] originalSemaphores = semaphores.clone();
        int[] originalStages = stages.clone();
        var result = token.waits(semaphores, stages);
        if (result == null || !Arrays.equals(result.semaphores(), expectedSemaphores)
                || !Arrays.equals(result.stages(), expectedStages)
                || !Arrays.equals(semaphores, originalSemaphores) || !Arrays.equals(stages, originalStages)) {
            throw new AssertionError("Wrong acquire wait list or mutated caller arrays");
        }
    }

    private static void verify(Path path, ClassNode mixin) throws Exception {
        ClassNode renderer = new ClassNode();
        try (JarFile jar = new JarFile(path.toFile())) {
            var entry = jar.getJarEntry("net/vulkanmod/vulkan/Renderer.class");
            if (entry == null) throw new AssertionError("Missing Renderer: " + path);
            try (var input = jar.getInputStream(entry)) { new ClassReader(input).accept(renderer, 0); }
        }
        int redirects = 0, lifecycle = 0;
        for (MethodNode handler : mixin.methods) {
            if (handler.visibleAnnotations == null) continue;
            for (AnnotationNode annotation : handler.visibleAnnotations) {
                if (!annotation.desc.endsWith("/Redirect;") && !annotation.desc.endsWith("/Inject;")) continue;
                var values = values(annotation);
                if (!Integer.valueOf(1).equals(values.get("require"))) throw new AssertionError("Required injection changed");
                String target = ((List<?>) values.get("method")).getFirst().toString();
                var methods = selectedMethods(renderer, target);
                if (methods.isEmpty()) throw new AssertionError("Unsupported Renderer method " + target + ": " + path);
                if (target.equals("beginFrame*")) {
                    var bare = selectedMethods(renderer, "beginFrame");
                    if (bare.size() != 1 || !bare.getFirst().desc.equals("()V")) {
                        throw new AssertionError("Mixin's bare-name SINGLE selector semantics changed");
                    }
                    if (renderer.methods.stream().anyMatch(m -> m.name.equals("beginFrame") && m.desc.equals("(I)V"))) {
                        // On Reforged, a bare selector chooses only the delegating public wrapper.
                        if (methods.size() != 2 || countAcquire(bare) != 0 || countAcquire(methods) != 1) {
                            throw new AssertionError("Real Mixin overload selector negative control failed: " + path);
                        }
                    }
                }
                if (annotation.desc.endsWith("/Redirect;")) {
                    String callTarget = values((AnnotationNode) values.get("at")).get("target").toString();
                    int calls = 0;
                    for (MethodNode method : methods) for (AbstractInsnNode instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode call
                                && callTarget.equals("L" + call.owner + ";" + call.name + call.desc)) calls++;
                    }
                    if (calls < 1 || calls > (Integer) values.get("allow")) {
                        throw new AssertionError("Unexpected number of acquire/submit calls " + calls + ": " + path);
                    }
                    redirects++;
                } else {
                    AnnotationNode at = (AnnotationNode) ((List<?>) values.get("at")).getFirst();
                    if (!"HEAD".equals(values(at).get("value")) || !target.equals("destroySyncObjects()V")) {
                        throw new AssertionError("Sync lifetime reset moved");
                    }
                    int destroys = countDestruction(methods.getFirst(), renderer);
                    if (destroys < 1) throw new AssertionError("Sync reset no longer owns semaphore destruction: " + path);
                    lifecycle++;
                }
            }
        }
        if (redirects != 4 || lifecycle != 1) throw new AssertionError("Incomplete acquire/submit/lifetime coverage");
    }

    private static List<MethodNode> selectedMethods(ClassNode owner, String text) {
        var selector = MemberInfo.parse(text, null).configure(Configure.SELECT_MEMBER).validate();
        var selected = new ArrayList<MethodNode>();
        for (MethodNode method : owner.methods) {
            if (selected.size() >= selector.getMaxMatchCount()) break;
            if (selector.match(ElementNode.of(owner, method)).isExactMatch()) selected.add(method);
        }
        if (selected.size() < selector.getMinMatchCount()) throw new AssertionError("Not enough methods for " + text);
        return selected;
    }

    private static int countAcquire(List<MethodNode> methods) {
        int result = 0;
        for (MethodNode method : methods) for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/vulkan/KHRSwapchain")
                    && call.name.equals("vkAcquireNextImageKHR")) result++;
        }
        return result;
    }

    private static int countDestruction(MethodNode method, ClassNode owner) {
        int result = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/vulkan/VK10")
                    && call.name.equals("vkDestroySemaphore")) result++;
            if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                for (Object argument : dynamic.bsmArgs) if (argument instanceof Handle handle
                        && handle.getOwner().equals(owner.name)) {
                    MethodNode lambda = owner.methods.stream().filter(m -> m.name.equals(handle.getName())
                            && m.desc.equals(handle.getDesc())).findFirst().orElseThrow();
                    result += countDestruction(lambda, owner);
                }
            }
        }
        return result;
    }

    private static Map<String, Object> values(AnnotationNode annotation) {
        var result = new HashMap<String, Object>();
        for (int i = 0; i < annotation.values.size(); i += 2) {
            result.put((String) annotation.values.get(i), annotation.values.get(i + 1));
        }
        return result;
    }
}
