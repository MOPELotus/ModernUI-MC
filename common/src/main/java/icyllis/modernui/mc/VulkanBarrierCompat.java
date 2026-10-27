package icyllis.modernui.mc;

import static org.lwjgl.vulkan.VK10.*;

/** Stage coverage for VulkanMod's depth/stencil image layout transitions. */
public final class VulkanBarrierCompat {
    private VulkanBarrierCompat() {}

    public static int depthStencilStages(int stages, int access, int aspect) {
        if ((aspect & (VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT)) != 0
                && (access & (VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)) != 0) {
            // Attachment writes can happen in either fragment-test stage;
            // vkCmdClearAttachments specifically executes in the late stage.
            return stages | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                    | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
        }
        return stages;
    }
}
