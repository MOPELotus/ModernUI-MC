package icyllis.modernui.mc.mixin;

import com.mojang.blaze3d.textures.GpuTextureView;
import icyllis.modernui.mc.VulkanGpuDeviceOwnership;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.vulkanmod.render.engine.VkGpuDevice", remap = false)
public abstract class MixinVulkanTextureViewOwnership {
    @Inject(method = "createTextureView*", at = @At("RETURN"), require = 2, allow = 2)
    private void modernui$ownTextureView(CallbackInfoReturnable<GpuTextureView> cir) {
        var view = cir.getReturnValue();
        VulkanGpuDeviceOwnership.track(this, view, view::close);
    }
}
