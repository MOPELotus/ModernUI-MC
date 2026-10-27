package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.VulkanGpuDeviceOwnership;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"net.vulkanmod.render.engine.VkGpuBuffer",
        "net.vulkanmod.render.engine.VkGpuTexture"}, remap = false)
public abstract class MixinVulkanGpuResourceOwnership {
    @Inject(method = "close()V", at = @At("RETURN"), require = 1, allow = 1)
    private void modernui$forgetClosedResource(CallbackInfo ci) {
        VulkanGpuDeviceOwnership.closed(this);
    }
}
