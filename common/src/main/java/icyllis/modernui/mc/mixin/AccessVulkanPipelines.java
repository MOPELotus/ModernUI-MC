package icyllis.modernui.mc.mixin;

import net.vulkanmod.vulkan.shader.Pipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.shader.Pipeline", remap = false)
public interface AccessVulkanPipelines {
    @Accessor("PIPELINES")
    static List<Pipeline> modernui$getRegisteredPipelines() {
        throw new AssertionError();
    }
}
