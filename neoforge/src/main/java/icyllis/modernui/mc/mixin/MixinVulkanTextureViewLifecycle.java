package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.VulkanGpuDeviceOwnership;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.vulkanmod.render.engine.VkTextureView", remap = false)
public abstract class MixinVulkanTextureViewLifecycle {
    @Shadow private boolean closed;

    @Inject(method = "close()V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void modernui$skipClosedView(CallbackInfo ci) {
        // VM 0.6.8 and Reforged guard the reference decrement, but leave their
        // framebuffer-cache cleanup outside that guard. A texture can close its
        // internal view after its device already closed that same view.
        if (closed) ci.cancel();
    }

    @Inject(method = "close()V", at = @At("RETURN"), require = 1, allow = 1)
    private void modernui$forgetClosedView(CallbackInfo ci) {
        VulkanGpuDeviceOwnership.closed(this);
    }
}
