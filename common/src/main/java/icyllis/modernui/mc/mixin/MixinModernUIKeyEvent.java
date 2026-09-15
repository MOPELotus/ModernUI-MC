package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.SdlInput;
import icyllis.modernui.view.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KeyEvent.class, remap = false)
public abstract class MixinModernUIKeyEvent implements SdlInput.KeycodeCarrier {
    @Unique
    private int modernui$keycode;

    @Override
    public void modernui$setKeycode(int keycode) {
        modernui$keycode = keycode;
    }

    @Override
    public int modernui$getKeycode() {
        return modernui$keycode;
    }

    @Inject(method = "obtain(JIIIIII)Licyllis/modernui/view/KeyEvent;", at = @At("RETURN"))
    private static void modernui$resetKeycode(long time, int action, int code, int repeat,
                                             int modifiers, int scanCode, int flags,
                                             CallbackInfoReturnable<KeyEvent> cir) {
        ((SdlInput.KeycodeCarrier) cir.getReturnValue()).modernui$setKeycode(0);
    }

    @Inject(method = "copyFrom", at = @At("RETURN"))
    private void modernui$copyKeycode(KeyEvent other, CallbackInfo ci) {
        modernui$keycode = ((SdlInput.KeycodeCarrier) other).modernui$getKeycode();
    }

    /** @author MOPELotus @reason Preserve the SDL layout mapping captured on the main thread. */
    @Overwrite
    public final char getMappedChar() {
        return modernui$keycode > 0 && modernui$keycode <= Character.MAX_VALUE
                ? (char) modernui$keycode : '\0';
    }
}
