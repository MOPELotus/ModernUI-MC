package icyllis.modernui.mc.mixin;

import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.Buffer;
import java.nio.IntBuffer;

@Pseudo
@Mixin(targets = "icyllis.arc3d.vulkan.VulkanCommandBuffer$SetBindingState", remap = false)
public abstract class MixinVulkanBindingBufferFree {
    @Redirect(method = "destroy()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/system/MemoryUtil;memFree(Ljava/nio/IntBuffer;)V"), require = 1, allow = 1)
    private static void modernui$freeBindingBuffer(IntBuffer buffer) {
        MemoryUtil.memFree((Buffer) buffer);
    }
}
