package icyllis.modernui.mc.test;

import icyllis.arc3d.engine.Context;
import icyllis.arc3d.vulkan.VulkanMemoryAllocator;
import icyllis.modernui.core.VulkanManager;
import icyllis.modernui.mc.VulkanContextLifecycle;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

/** Exercises the production close against the real pinned manager without a GPU. */
public final class VulkanContextLifecycleCheck {
    public static void main(String[] args) throws Exception {
        var allocator = new TrackedAllocator();
        var manager = manager(allocator);
        var immediate = new TestContext();
        var recording = new TestContext();
        expectRefused(manager, null, immediate, recording);
        immediate.unref();
        expectRefused(manager, null, immediate, recording);
        recording.unref();
        var started = new CountDownLatch(1);
        var stop = new CountDownLatch(1);
        var thread = new Thread(() -> {
            started.countDown();
            try { stop.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
        });
        thread.start();
        started.await();
        try { expectRefused(manager, thread, immediate, recording); }
        finally { stop.countDown(); thread.join(); }
        if (allocator.closeCount != 0) throw new AssertionError("Live clients closed their allocator");
        VulkanContextLifecycle.close(manager, thread, immediate, recording);
        if (allocator.closeCount != 1 || manager.getMemoryAllocator() != null)
            throw new AssertionError("The real manager did not release its own allocator");
        VulkanContextLifecycle.close(manager, thread, immediate, recording);
        if (allocator.closeCount != 1) throw new AssertionError("Repeated close freed the allocator twice");

        // An accidental external-device transfer must be rejected before manager.close can destroy it.
        for (String name : new String[]{"mDevice", "mInstance"}) {
            var guardedAllocator = new TrackedAllocator();
            var guarded = manager(guardedAllocator);
            Field field = VulkanManager.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(guarded, uninitialized(name.equals("mDevice") ? VkDevice.class : VkInstance.class));
            expectRefused(guarded, null, null, null);
            if (guardedAllocator.closeCount != 0) throw new AssertionError("External Vulkan owner was touched");
            field.set(guarded, null);
            VulkanContextLifecycle.close(guarded, null, null, null);
        }
        System.out.println("Vulkan bridge ownership and real manager shutdown checks passed");
    }

    private static VulkanManager manager(TrackedAllocator allocator) throws Exception {
        var constructor = VulkanManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var manager = constructor.newInstance();
        manager.setMemoryAllocator(allocator);
        return manager;
    }

    private static Object uninitialized(Class<?> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }

    private static void expectRefused(VulkanManager manager, Thread thread, Context immediate, Context recording) {
        try {
            VulkanContextLifecycle.close(manager, thread, immediate, recording);
            throw new AssertionError("Unsafe bridge shutdown was accepted");
        } catch (IllegalStateException expected) {
            // Production checks must run before the real manager/native cleanup.
        }
    }

    private static final class TrackedAllocator extends VulkanMemoryAllocator {
        int closeCount;
        TrackedAllocator() { super(0, false); }
        @Override public void close() { closeCount++; super.close(); }
    }

    private static final class TestContext extends Context {
        TestContext() { super(null); }
        @Override public void freeGpuResources() { }
        @Override public void performDeferredCleanup(long time) { }
        @Override protected void deallocate() { }
    }
}
