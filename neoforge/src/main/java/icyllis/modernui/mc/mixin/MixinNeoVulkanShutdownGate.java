package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.neoforge.NeoVulkanShutdown;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.Vulkan", remap = false)
public abstract class MixinNeoVulkanShutdownGate {
    @Inject(method = "cleanUp()V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void modernui$cleanUpOnce(CallbackInfo ci) {
        if (!NeoVulkanShutdown.begin()) {
            ci.cancel();
        }
    }
}
