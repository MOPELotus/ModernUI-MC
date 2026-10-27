package icyllis.modernui.mc.mixin;

import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/** Keep Minecraft's per-frame global uniform writes on Reforged's graphics queue. */
@Pseudo
@Mixin(targets = "net.vulkanmod.render.engine.VkCommandEncoder", remap = false)
public abstract class MixinVulkanCommandEncoder {
    @Unique private static boolean modernui$startingGlobalUniformFrame;

    @Inject(method = "uploadBufferData(Lnet/vulkanmod/vulkan/memory/buffer/Buffer;JLjava/nio/ByteBuffer;)V",
            at = @At("HEAD"), require = 1)
    private void modernui$startGlobalUniformFrame(Buffer buffer, long offset, ByteBuffer data, CallbackInfo ci) {
        if (modernui$startingGlobalUniformFrame || !data.hasRemaining()
                || !"Global Settings UBO".equals(buffer.name)
                || buffer.getBufferSize() != GlobalSettingsUniform.UBO_SIZE) return;
        var renderer = Renderer.getInstance();
        if (renderer == null || Renderer.isRecording()) return;
        // Reforged normally starts here lazily at the first render pass. Start
        // before this UBO upload instead, so its existing recordBufferCopy path
        // supplies graphics-queue barriers for both consecutive frame writes.
        modernui$startingGlobalUniformFrame = true;
        try {
            renderer.beginFrame();
        } finally {
            modernui$startingGlobalUniformFrame = false;
        }
    }
}
