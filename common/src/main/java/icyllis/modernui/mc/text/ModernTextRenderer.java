/*
 * Modern UI.
 * Copyright (C) 2019-2021 BloCamLimb. All rights reserved.
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

package icyllis.modernui.mc.text;

import net.minecraft.client.gui.Font;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * Modern text configuration and 3D mode selection.
 * <p>
 * Minecraft 26.2 prepares GUI text through {@link net.minecraft.client.gui.Font.PreparedText};
 * Modern UI renders that path through {@link ModernPreparedText}.
 */
public final class ModernTextRenderer {

    public static final Vector3f SHADOW_OFFSET = new Vector3f(0.0F, 0.0F, 0.03F);
    public static final Vector3f OUTLINE_OFFSET = new Vector3f(0.0F, 0.0F, 0.01F);

    public static volatile boolean sAllowShadow = true;
    public static volatile float sShadowOffset = 1.0f;
    public static volatile float sOutlineOffset = 0.5f;
    public static volatile boolean sComputeDeviceFontSize = true;
    public static volatile boolean sAllowSDFTextIn2D = true;
    public static volatile boolean sTweakExperienceText = true;

    public ModernTextRenderer(TextLayoutEngine engine) {
    }

    public int chooseMode(Matrix4fc ctm, Font.DisplayMode displayMode) {
        if (displayMode == Font.DisplayMode.SEE_THROUGH) {
            return TextRenderType.MODE_SEE_THROUGH;
        } else if (TextLayoutEngine.sCurrentInWorldRendering) {
            return TextRenderType.MODE_SDF_FILL;
        } else if (sAllowSDFTextIn2D) {
            return TextRenderType.MODE_SDF_FILL;
        }
        return TextRenderType.MODE_NORMAL;
    }
}
