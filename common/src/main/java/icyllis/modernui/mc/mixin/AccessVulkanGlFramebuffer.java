package icyllis.modernui.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "net.vulkanmod.gl.VkGlFramebuffer", remap = false)
public interface AccessVulkanGlFramebuffer {
    @Invoker("cleanUp")
    void modernui$cleanUp(boolean freeAttachments);
}
