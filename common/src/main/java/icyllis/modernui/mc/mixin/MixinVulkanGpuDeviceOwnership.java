package icyllis.modernui.mc.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.GpuTexture;
import icyllis.modernui.mc.VulkanGpuDeviceOwnership;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.vulkanmod.render.engine.VkGpuDevice", remap = false)
public abstract class MixinVulkanGpuDeviceOwnership {
    // Wildcards explicitly select both overloads, including the 1.21.9 int
    // versus 1.21.11 long size parameter. All three releases have two returns.
    @Inject(method = "createBuffer*", at = @At("RETURN"), require = 2, allow = 2)
    private void modernui$ownBuffer(CallbackInfoReturnable<GpuBuffer> cir) {
        var resource = cir.getReturnValue();
        VulkanGpuDeviceOwnership.track(this, resource, resource::close);
    }

    @Inject(method = {"createTexture(Ljava/util/function/Supplier;ILcom/mojang/blaze3d/textures/TextureFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;",
            "createTexture(Ljava/lang/String;ILcom/mojang/blaze3d/textures/TextureFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;"},
            at = @At("RETURN"), require = 2, allow = 2)
    private void modernui$ownTexture(CallbackInfoReturnable<GpuTexture> cir) {
        var resource = cir.getReturnValue();
        VulkanGpuDeviceOwnership.track(this, resource, resource::close);
    }

    @Inject(method = "close()V", at = @At("HEAD"), require = 1, allow = 1)
    private void modernui$closeOwnedResources(CallbackInfo ci) {
        VulkanGpuDeviceOwnership.closeRemaining(this);
    }
}
