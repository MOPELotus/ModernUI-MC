package icyllis.modernui.mc.mixin;

import icyllis.arc3d.granite.GraniteDevice;
import icyllis.arc3d.sketch.Matrix;
import icyllis.modernui.mc.CanvasTextOrigin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GraniteDevice.class, remap = false)
public abstract class MixinGraniteText {
    @Redirect(method = "onDrawGlyphRunList", at = @At(value = "INVOKE",
            target = "Licyllis/arc3d/sketch/Matrix;preTranslate(FF)V"),
            require = 1)
    private void modernui$stableTextOrigin(Matrix matrix, float x, float y) {
        matrix.preTranslate(x, y);
        CanvasTextOrigin.forRasterization(matrix);
    }
}
