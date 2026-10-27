package icyllis.modernui.mc.mixin;

import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.Buffer;
import java.nio.ByteBuffer;

@Pseudo
@Mixin(targets = "icyllis.modernui.util.Parcel", remap = false)
public abstract class MixinParcelBufferFree {
    @Redirect(method = "freeData()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/system/MemoryUtil;memFree(Ljava/nio/ByteBuffer;)V"), require = 1, allow = 1)
    private static void modernui$freeParcelBuffer(ByteBuffer buffer) {
        MemoryUtil.memFree((Buffer) buffer);
    }
}
