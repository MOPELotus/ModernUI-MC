package icyllis.modernui.mc.mixin;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.vulkan.VK10.*;

@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.texture.VulkanImage", remap = false)
public abstract class MixinVulkanImageUpload {
    @Shadow private int currentLayout;
    @Shadow private long id;
    @Shadow @Final public int aspect;
    @Shadow @Final public int mipLevels;
    @Shadow @Final public int arrayLayers;

    // VulkanMod's layout transition skips equal layouts, including consecutive writes.
    // Its upload batch may overwrite a sprite before transitioning the atlas to read-only.
    @Inject(method = "transferDstLayout(Lorg/lwjgl/system/MemoryStack;Lorg/lwjgl/vulkan/VkCommandBuffer;)V",
            at = @At("HEAD"), require = 1, allow = 1)
    private void modernui$serializeRepeatedUpload(MemoryStack stack, VkCommandBuffer commandBuffer,
                                                 CallbackInfo ci) {
        if (currentLayout != VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            return;
        }
        var barriers = VkImageMemoryBarrier.calloc(1, stack);
        barriers.sType$Default()
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(id);
        barriers.subresourceRange()
                .aspectMask(aspect)
                .baseMipLevel(0).levelCount(mipLevels)
                .baseArrayLayer(0).layerCount(arrayLayers);
        vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barriers);
    }
}
