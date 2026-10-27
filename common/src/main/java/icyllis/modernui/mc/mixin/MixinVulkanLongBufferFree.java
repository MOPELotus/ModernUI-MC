package icyllis.modernui.mc.mixin;

import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.Buffer;
import java.nio.LongBuffer;

/** Arc3D's typed free overload is newer than Minecraft's LWJGL 3.3.3 API. */
@Pseudo
@Mixin(targets = {"icyllis.arc3d.vulkan.VulkanCommandBuffer", "icyllis.arc3d.vulkan.VulkanPrimaryCommandBuffer"}, remap = false)
public abstract class MixinVulkanLongBufferFree {
    @Redirect(method = "destroy()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/system/MemoryUtil;memFree(Ljava/nio/LongBuffer;)V"), require = 2, allow = 2)
    private static void modernui$freeLongBuffer(LongBuffer buffer) {
        MemoryUtil.memFree((Buffer) buffer);
    }
}
