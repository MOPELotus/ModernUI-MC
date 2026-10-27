package icyllis.modernui.mc.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.modernui.mc.VulkanGpuDeviceOwnership;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.vulkanmod.render.sky.CloudRenderer", remap = false)
public abstract class MixinVulkanCloudOwnership {
    @Shadow public abstract void resetBuffer();

    @Inject(method = "<init>()V", at = @At("RETURN"), require = 1, allow = 1)
    private void modernui$ownCloudBuffer(CallbackInfo ci) {
        // VM's replacement cloud VBO is not part of vanilla CloudRenderer.close.
        VulkanGpuDeviceOwnership.track(RenderSystem.getDevice(), this, this::resetBuffer);
    }
}
