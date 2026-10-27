package icyllis.modernui.mc;

import icyllis.arc3d.engine.Context;
import icyllis.modernui.core.VulkanManager;

/** Closes the allocator owned by the Arc3D bridge after its rendering clients exit. */
public final class VulkanContextLifecycle {
    private VulkanContextLifecycle() {
    }

    public static void close(VulkanManager manager, Thread uiThread,
                             Context immediateContext, Context recordingContext) {
        if (uiThread != null && uiThread.isAlive()) {
            throw new IllegalStateException("Cannot close the Vulkan bridge while the UI thread is alive");
        }
        if ((immediateContext != null && immediateContext.getRefCntAcquire() != 0) ||
                (recordingContext != null && recordingContext.getRefCntAcquire() != 0)) {
            throw new IllegalStateException("Cannot close the Vulkan bridge while Arc3D contexts are referenced");
        }
        // wrapContext transfers only its own allocator and feature struct to this manager.
        // The instance and logical device remain owned by VulkanMod, never by this manager.
        if (manager.getDevice() != null || manager.getInstance() != null) {
            throw new IllegalStateException("The Vulkan bridge must not own VulkanMod's device or instance");
        }
        manager.close();
    }
}
