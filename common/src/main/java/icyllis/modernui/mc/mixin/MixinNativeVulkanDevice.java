package icyllis.modernui.mc.mixin;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import icyllis.modernui.core.Core;
import icyllis.modernui.core.VulkanManager;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.UIManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VulkanDevice.class, remap = false)
public abstract class MixinNativeVulkanDevice {
    // The command encoder first waits for the GPU and drains deferred texture
    // references. Arc3D must then release its resources before VkDevice is freed.
    @Inject(method = "close", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/util/vma/Vma;vmaDestroyAllocator(J)V"))
    private void modernui$releaseSharedContext(CallbackInfo ci) {
        if (ModernUIMod.isVulkanBackend()) {
            if (Core.peekImmediateContext() != null) {
                UIManager.destroy();
            } else {
                VulkanManager.get().close();
            }
        }
    }
}
