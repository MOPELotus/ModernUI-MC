package icyllis.modernui.mc.mixin;

import net.vulkanmod.vulkan.memory.buffer.UniformBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** VulkanMod 0.6.8 owns section data independently of its lazily allocated terrain buffers. */
@Pseudo
@Mixin(targets = "net.vulkanmod.render.chunk.buffer.DrawBuffers", remap = false)
public abstract class MixinVulkanDrawBuffersLifecycle {
    @Shadow @Final private UniformBuffer sectionDataBuffer;
    @Unique private boolean modernui$sectionDataReleased;

    @Inject(method = "free()V", at = @At("HEAD"), require = 1, allow = 1)
    private void modernui$releaseSectionData(CallbackInfo ci) {
        // releaseBuffers() also runs when an area is reused, so retain the
        // section data until the final owner free(), including empty areas.
        if (!modernui$sectionDataReleased) {
            sectionDataBuffer.scheduleFree();
            modernui$sectionDataReleased = true;
        }
    }
}
