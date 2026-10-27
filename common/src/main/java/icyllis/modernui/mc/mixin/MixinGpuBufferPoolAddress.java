package icyllis.modernui.mc.mixin;

import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.Buffer;
import java.nio.ByteBuffer;

@Pseudo
@Mixin(targets = "icyllis.arc3d.engine.GpuBufferPool", remap = false)
public abstract class MixinGpuBufferPoolAddress {
    @Redirect(method = "getMappedBuffer(Ljava/nio/ByteBuffer;JI)Ljava/nio/ByteBuffer;", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/system/MemoryUtil;memAddress0(Ljava/nio/ByteBuffer;)J"), require = 1, allow = 1)
    private static long modernui$bufferBaseAddress(ByteBuffer buffer) {
        return MemoryUtil.memAddress0((Buffer) buffer);
    }
}
