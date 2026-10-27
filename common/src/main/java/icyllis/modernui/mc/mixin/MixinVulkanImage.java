package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.VulkanBarrierCompat;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.texture.VulkanImage", remap = false)
public abstract class MixinVulkanImage {
    // Checked against Fabric VulkanMod 0.6.6/0.6.8 and Reforged 0.3.0.
    // The outer overload delegates here, so each transition is handled once.
    @Redirect(method = "transitionLayout(Lorg/lwjgl/system/MemoryStack;Lorg/lwjgl/vulkan/VkCommandBuffer;Lnet/vulkanmod/vulkan/texture/VulkanImage;IIIIIII)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK10;vkCmdPipelineBarrier(Lorg/lwjgl/vulkan/VkCommandBuffer;IIILorg/lwjgl/vulkan/VkMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkBufferMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkImageMemoryBarrier$Buffer;)V"),
            require = 1)
    private static void modernui$coverDepthStencilStages(VkCommandBuffer commandBuffer,
                                                       int srcStages, int dstStages, int dependencyFlags,
                                                       VkMemoryBarrier.Buffer memoryBarriers,
                                                       VkBufferMemoryBarrier.Buffer bufferBarriers,
                                                       VkImageMemoryBarrier.Buffer imageBarriers) {
        if (imageBarriers != null) {
            for (int i = imageBarriers.position(); i < imageBarriers.limit(); i++) {
                VkImageMemoryBarrier barrier = imageBarriers.get(i);
                int aspect = barrier.subresourceRange().aspectMask();
                srcStages = VulkanBarrierCompat.depthStencilStages(srcStages, barrier.srcAccessMask(), aspect);
                dstStages = VulkanBarrierCompat.depthStencilStages(dstStages, barrier.dstAccessMask(), aspect);
            }
        }
        VK10.vkCmdPipelineBarrier(commandBuffer, srcStages, dstStages, dependencyFlags,
                memoryBarriers, bufferBarriers, imageBarriers);
    }
}
