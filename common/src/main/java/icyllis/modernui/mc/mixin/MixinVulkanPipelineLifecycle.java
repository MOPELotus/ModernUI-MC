package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.VulkanPipelineLifecycle;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.Vulkan", remap = false)
public abstract class MixinVulkanPipelineLifecycle {
    @Inject(method = "cleanUp()V", at = @At(value = "INVOKE",
            target = "Lnet/vulkanmod/vulkan/memory/MemoryManager;freeAllBuffers()V",
            shift = At.Shift.AFTER), require = 1, allow = 1)
    private static void modernui$closeRemainingPipelines(CallbackInfo ci) {
        // Fabric frees only its four built-in pipelines; ordinary Minecraft
        // pipelines are stored on RenderPipeline instead of the old GL cache.
        // Reforged schedules its built-ins as frame operations. Run after the
        // drain so those owners cannot be cleaned twice by pending callbacks.
        VulkanPipelineLifecycle.closeRemaining(
                AccessVulkanPipelines.modernui$getRegisteredPipelines(), Pipeline::cleanUp);
    }
}
