package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.VulkanAcquireToken;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.Renderer", remap = false)
public abstract class MixinVulkanRenderer {
    @Unique private VulkanAcquireToken modernui$acquireToken;

    @Unique
    private VulkanAcquireToken modernui$acquireToken() {
        if (modernui$acquireToken == null) modernui$acquireToken = new VulkanAcquireToken();
        return modernui$acquireToken;
    }

    @Inject(method = "destroySyncObjects()V", at = @At("HEAD"), require = 1)
    private void modernui$forgetDestroyedSyncObjects(CallbackInfo ci) {
        // SUBOPTIMAL can recursively recreate the swapchain before endFrame.
        // The old acquire signals belong to the semaphores destroyed here.
        modernui$acquireToken().syncObjectsDestroyed();
    }

    // Fabric acquires in beginFrame(); Reforged acquires in beginFrame(int).
    // Select all overloads explicitly, with exactly one matching native call.
    @Redirect(method = "beginFrame*", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkAcquireNextImageKHR(Lorg/lwjgl/vulkan/VkDevice;JJJJLjava/nio/IntBuffer;)I"),
            require = 1, allow = 1)
    private int modernui$trackAcquire(VkDevice device, long swapchain, long timeout,
                                     long semaphore, long fence, IntBuffer imageIndex) {
        int result = KHRSwapchain.vkAcquireNextImageKHR(device, swapchain, timeout, semaphore, fence, imageIndex);
        modernui$acquireToken().acquired(result, semaphore);
        return result;
    }

    @Redirect(method = "submitFrame()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VK10;vkQueueSubmit(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo;J)I"),
            require = 1, allow = 2)
    private int modernui$submitFrame(VkQueue queue, VkSubmitInfo submit, long fence) {
        return modernui$submit(queue, submit, fence);
    }

    @Redirect(method = "flushCmds()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VK10;vkQueueSubmit(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo;J)I"),
            require = 1, allow = 1)
    private int modernui$flushCommands(VkQueue queue, VkSubmitInfo submit, long fence) {
        return modernui$submit(queue, submit, fence);
    }

    @Redirect(method = "waitForSwapChain()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VK10;vkQueueSubmit(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo;J)I"),
            require = 1, allow = 1)
    private int modernui$waitForSwapChain(VkQueue queue, VkSubmitInfo submit, long fence) {
        return modernui$submit(queue, submit, fence);
    }

    @Unique
    private int modernui$submit(VkQueue queue, VkSubmitInfo submit, long fence) {
        var token = modernui$acquireToken();
        var originalSemaphores = submit.pWaitSemaphores();
        var originalStages = submit.pWaitDstStageMask();
        int originalCount = submit.waitSemaphoreCount();
        long[] semaphores = new long[originalCount];
        int[] stages = new int[originalCount];
        for (int i = 0; i < originalCount; i++) {
            semaphores[i] = originalSemaphores.get(originalSemaphores.position() + i);
            stages[i] = originalStages.get(originalStages.position() + i);
        }
        var waits = token.waits(semaphores, stages);
        if (waits == null) {
            int result = VK10.vkQueueSubmit(queue, submit, fence);
            token.submitted(result);
            return result;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            try {
                submit.pWaitSemaphores(stack.longs(waits.semaphores()));
                submit.pWaitDstStageMask(stack.ints(waits.stages()));
                submit.waitSemaphoreCount(waits.semaphores().length);
                int result = VK10.vkQueueSubmit(queue, submit, fence);
                token.submitted(result);
                return result;
            } finally {
                // Restore the caller's struct before our temporary buffers expire.
                submit.pWaitSemaphores(originalSemaphores).pWaitDstStageMask(originalStages)
                        .waitSemaphoreCount(originalCount);
            }
        }
    }
}
