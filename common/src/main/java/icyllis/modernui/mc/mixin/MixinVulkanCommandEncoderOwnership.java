package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.VulkanGpuDeviceOwnership;
import net.vulkanmod.gl.VkGlFramebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.vulkanmod.render.engine.VkCommandEncoder", remap = false)
public abstract class MixinVulkanCommandEncoderOwnership {
    @Shadow private int framebufferId;

    @Inject(method = "<init>(Lnet/vulkanmod/render/engine/VkGpuDevice;)V",
            at = @At("RETURN"), require = 1, allow = 1)
    private void modernui$ownFramebuffer(@Coerce Object device, CallbackInfo ci) {
        // The encoder is constructed before RenderSystem publishes its device.
        VulkanGpuDeviceOwnership.track(device, this, this::modernui$closeFramebuffer);
    }

    @Unique
    private void modernui$closeFramebuffer() {
        var framebuffer = VkGlFramebuffer.getFramebuffer(framebufferId);
        if (framebuffer != null) {
            // This scratch FBO borrows texture attachments. The public delete
            // method frees them too; the existing owner cleanup with false only
            // releases its framebuffer/render pass through the deferred queue.
            ((AccessVulkanGlFramebuffer) (Object) framebuffer).modernui$cleanUp(false);
        }
    }
}
