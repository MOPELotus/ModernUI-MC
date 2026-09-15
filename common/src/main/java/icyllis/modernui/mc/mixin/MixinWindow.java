/*
 * Modern UI.
 * Copyright (C) 2019-2023 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc.mixin;

import com.mojang.blaze3d.platform.*;
import icyllis.modernui.ModernUI;
import icyllis.modernui.graphics.MathUtil;
import icyllis.modernui.mc.ModernUIClient;
import icyllis.modernui.mc.ModernUIMod;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.util.DisplayMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(Window.class)
public abstract class MixinWindow {

    @Shadow
    private int guiScale;

    @Shadow
    public abstract int getWidth();

    @Shadow
    public abstract int getHeight();

    @Shadow
    @Nullable
    public abstract Monitor findBestMonitor();

    /**
     * @author BloCamLimb
     * @reason Make GUI scale not limited to even numbers when forceUnicode = true
     */
    @Inject(method = "calculateScale", at = @At("HEAD"), cancellable = true)
    public void onCalculateScale(int guiScaleIn, boolean forceUnicode, CallbackInfoReturnable<Integer> ci) {
        int r = MuiModApi.calcGuiScales((Window) (Object) this);
        ci.setReturnValue(guiScaleIn > 0 ? MathUtil.clamp(guiScaleIn, r >> 8 & 0xf, r & 0xf) : r >> 4 & 0xf);
    }

    @Inject(method = "setGuiScale", at = @At("HEAD"))
    private void onSetGuiScale(int scaleFactor, CallbackInfo ci) {
        int oldScale = (int) guiScale;
        int newScale = (int) scaleFactor;

        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setToDefaults();

        metrics.widthPixels = getWidth();
        metrics.heightPixels = getHeight();

        // the base scale is 2x, so divide by 2
        metrics.density = newScale * 0.5f;
        metrics.densityDpi = (int) (metrics.density * DisplayMetrics.DENSITY_DEFAULT);
        metrics.scaledDensity = ModernUIClient.sFontScale * metrics.density;

        // SDL provides display scaling, not physical monitor dimensions. Keep the
        // default physical DPI; UI density above follows Minecraft's actual GUI scale.
        var ctx = ModernUI.getInstance();
        if (ctx != null) {
            ctx.getResources().updateConfiguration(ctx.getResources().getConfiguration(), metrics);
        }

        MuiModApi.dispatchOnWindowResize(getWidth(), getHeight(), newScale, oldScale);
    }
}
