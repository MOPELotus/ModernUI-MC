package icyllis.modernui.mc.neoforge;

import java.util.concurrent.atomic.AtomicBoolean;

/** One native-device shutdown, including calls made by future VulkanMod hooks. */
public final class NeoVulkanShutdown {
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private NeoVulkanShutdown() {}

    public static boolean begin() {
        return STARTED.compareAndSet(false, true);
    }

    public static boolean started() {
        return STARTED.get();
    }
}
