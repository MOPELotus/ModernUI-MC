/*
 * Modern UI.
 * Copyright (C) 2025 BloCamLimb. All rights reserved.
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

package icyllis.modernui.mc;

import org.jetbrains.annotations.ApiStatus;

import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class FontDefaults {

    public static final String FIRST_FONT_FAMILY = "MiSans VF";
    public static final String FALLBACK_FONT_FAMILY_L3 = "MiSans L3";
    public static final String FALLBACK_FONT_FAMILY_LATIN = "MiSans Latin VF";
    public static final String FALLBACK_FONT_FAMILY_TC = "Misans TC VF";
    public static final String MODPACK_FONT_DIRECTORY = "config/ModernUI/fonts";
    public static final int FONT_WEIGHT_MIN = 100;
    public static final int FONT_WEIGHT_MAX = 900;
    public static final int FONT_WEIGHT_STEP = 100;
    public static final int DEFAULT_FONT_WEIGHT = 400;

    private FontDefaults() {
    }

    public static List<String> createFallbackFontFamilyList() {
        List<String> list = new ArrayList<>();
        list.add(FALLBACK_FONT_FAMILY_L3);
        list.add(FALLBACK_FONT_FAMILY_LATIN);
        list.add(FALLBACK_FONT_FAMILY_TC);
        return list;
    }

    public static List<String> createFontRegistrationList() {
        List<String> list = new ArrayList<>();
        list.add(MODPACK_FONT_DIRECTORY);
        return list;
    }

    public static boolean isRequiredFontFamily(String family) {
        return isWeightControlledFontFamily(family);
    }

    public static boolean isWeightControlledFontFamily(String family) {
        return FIRST_FONT_FAMILY.equals(family) ||
                FALLBACK_FONT_FAMILY_L3.equals(family) ||
                FALLBACK_FONT_FAMILY_LATIN.equals(family) ||
                FALLBACK_FONT_FAMILY_TC.equals(family);
    }

    public static int normalizeFontWeight(int weight) {
        int clamped = Math.max(FONT_WEIGHT_MIN, Math.min(FONT_WEIGHT_MAX, weight));
        int offset = clamped - FONT_WEIGHT_MIN;
        int roundedOffset = Math.round(offset / (float) FONT_WEIGHT_STEP) * FONT_WEIGHT_STEP;
        return FONT_WEIGHT_MIN + roundedOffset;
    }

    public static float toTextAttributeWeight(int weight) {
        return switch (normalizeFontWeight(weight)) {
            case 100 -> TextAttribute.WEIGHT_EXTRA_LIGHT;
            case 200 -> TextAttribute.WEIGHT_LIGHT;
            case 300 -> TextAttribute.WEIGHT_DEMILIGHT;
            case 500 -> TextAttribute.WEIGHT_SEMIBOLD;
            case 600 -> TextAttribute.WEIGHT_MEDIUM;
            case 700 -> TextAttribute.WEIGHT_DEMIBOLD;
            // Java treats weight >= 2.0 as Font.BOLD, but ModernUI stores the
            // regular face in a slot that must report FontPaint.NORMAL.
            case 800 -> 1.9f;
            case 900 -> 1.999f;
            default -> TextAttribute.WEIGHT_REGULAR;
        };
    }
}
