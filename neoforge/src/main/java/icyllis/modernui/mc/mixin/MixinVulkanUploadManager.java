package icyllis.modernui.mc.mixin;

import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reforged's buffer copies synchronize accesses without transferring queue ownership. */
@Pseudo
@Mixin(targets = "net.vulkanmod.render.chunk.buffer.UploadManager", remap = false)
public abstract class MixinVulkanUploadManager {
    @Redirect(method = "copyBuffer(Lnet/vulkanmod/vulkan/memory/buffer/Buffer;ILnet/vulkanmod/vulkan/memory/buffer/Buffer;II)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK10;vkCmdPipelineBarrier(Lorg/lwjgl/vulkan/VkCommandBuffer;IIILorg/lwjgl/vulkan/VkMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkBufferMemoryBarrier$Buffer;Lorg/lwjgl/vulkan/VkImageMemoryBarrier$Buffer;)V"),
            require = 1, allow = 1)
    private static void modernui$ignoreBufferQueueOwnership(VkCommandBuffer commandBuffer,
                                                           int srcStages, int dstStages, int dependencyFlags,
                                                           VkMemoryBarrier.Buffer memoryBarriers,
                                                           VkBufferMemoryBarrier.Buffer bufferBarriers,
                                                           VkImageMemoryBarrier.Buffer imageBarriers) {
        // Reforged creates shared upload buffers with CONCURRENT sharing. This
        // particular barrier leaves both fields at calloc's zero defaults;
        // it is an access dependency, not a queue-family ownership transfer.
        if (bufferBarriers != null) {
            for (int i = bufferBarriers.position(); i < bufferBarriers.limit(); i++) {
                bufferBarriers.get(i).srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
            }
        }
        VK10.vkCmdPipelineBarrier(commandBuffer, srcStages, dstStages, dependencyFlags,
                memoryBarriers, bufferBarriers, imageBarriers);
    }
}
