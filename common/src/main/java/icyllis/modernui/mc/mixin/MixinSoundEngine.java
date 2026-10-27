package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.BlurHandler;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the inactive-window multiplier to game channels on the older sound API. */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {
    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
            at = @At("RETURN"), cancellable = true)
    private void modernui$scaleChannelVolume(float volume, SoundSource source,
                                             CallbackInfoReturnable<Float> ci) {
        ci.setReturnValue(ci.getReturnValueF() * BlurHandler.INSTANCE.getChannelVolumeMultiplier());
    }
}
