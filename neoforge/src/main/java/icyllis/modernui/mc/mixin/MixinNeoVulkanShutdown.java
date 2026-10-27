package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.neoforge.NeoVulkanShutdown;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reforged 0.3.0 has no caller for its native cleanup routine. */
@Mixin(Minecraft.class)
public abstract class MixinNeoVulkanShutdown {
    @Inject(method = "close()V", at = @At("HEAD"), require = 1, allow = 1)
    private void modernui$idleBeforeOwnersClose(CallbackInfo ci) {
        if (ModernUIMod.isVulkanModLoaded() && !NeoVulkanShutdown.started()) {
            NeoVulkanShutdown.waitIdle();
        }
    }

    @Inject(method = "close()V", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuDevice;close()V", shift = At.Shift.AFTER,
            remap = false), require = 1, allow = 1)
    private void modernui$closeNativeAfterOwners(CallbackInfo ci) {
        if (ModernUIMod.isVulkanModLoaded() && !NeoVulkanShutdown.started()) {
            NeoVulkanShutdown.cleanUp();
        }
    }
}
