package icyllis.modernui.mc.neoforge;

import java.util.concurrent.atomic.AtomicBoolean;

/** One native-device shutdown, including calls made by future VulkanMod hooks. */
public final class NeoVulkanShutdown {
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private NeoVulkanShutdown() {}

    // Keep optional VulkanMod symbols out of Minecraft-targeting mixin bytecode.
    // Mixin eagerly resolves member owners even when a runtime branch skips them.
    public static void waitIdle() {
        net.vulkanmod.vulkan.Vulkan.waitIdle();
    }

    public static void cleanUp() {
        net.vulkanmod.vulkan.Vulkan.cleanUp();
    }

    public static boolean begin() {
        return STARTED.compareAndSet(false, true);
    }

    public static boolean started() {
        return STARTED.get();
    }
}
