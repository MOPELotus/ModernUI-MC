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

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class FontDefaults {

    public static final String FIRST_FONT_FAMILY = "MiSans";
    public static final String FALLBACK_FONT_FAMILY_L3 = "MiSans L3";
    public static final String FALLBACK_FONT_FAMILY_LATIN = "MiSans Latin";
    public static final String FALLBACK_FONT_FAMILY_TC = "MiSans TC";
    public static final String FALLBACK_FONT_FAMILY_TC_ALT = "Misans TC";
    public static final String LEGACY_FIRST_FONT_FAMILY_VF = "MiSans VF";
    public static final String LEGACY_FALLBACK_FONT_FAMILY_LATIN_VF = "MiSans Latin VF";
    public static final String LEGACY_FALLBACK_FONT_FAMILY_TC_VF_ALT = "MiSans TC VF";
    public static final String LEGACY_FALLBACK_FONT_FAMILY_TC_VF = "Misans TC VF";
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
        return isMiSansFontFamily(family);
    }

    public static boolean isWeightControlledFontFamily(String family) {
        return getWeightedFontFilePrefix(family) != null;
    }

    public static boolean isMiSansFontFamily(String family) {
        return FIRST_FONT_FAMILY.equals(family) ||
                LEGACY_FIRST_FONT_FAMILY_VF.equals(family) ||
                FALLBACK_FONT_FAMILY_L3.equals(family) ||
                FALLBACK_FONT_FAMILY_LATIN.equals(family) ||
                LEGACY_FALLBACK_FONT_FAMILY_LATIN_VF.equals(family) ||
                FALLBACK_FONT_FAMILY_TC.equals(family) ||
                FALLBACK_FONT_FAMILY_TC_ALT.equals(family) ||
                LEGACY_FALLBACK_FONT_FAMILY_TC_VF_ALT.equals(family) ||
                LEGACY_FALLBACK_FONT_FAMILY_TC_VF.equals(family);
    }

    public static List<String> createWeightedFontFileNames(String family, int weight) {
        String prefix = getWeightedFontFilePrefix(family);
        if (prefix == null) {
            return List.of();
        }
        String suffix = switch (normalizeFontWeight(weight)) {
            case 100 -> "Thin";
            case 200 -> "ExtraLight";
            case 300 -> "Light";
            case 500 -> "Medium";
            case 600 -> "Semibold";
            case 700 -> "Demibold";
            case 800 -> "Bold";
            case 900 -> "Heavy";
            default -> "Regular";
        };
        return List.of(prefix + "-" + suffix + ".ttf",
                prefix + "-" + suffix + ".otf");
    }

    private static String getWeightedFontFilePrefix(String family) {
        if (FIRST_FONT_FAMILY.equals(family) ||
                LEGACY_FIRST_FONT_FAMILY_VF.equals(family)) {
            return "MiSans";
        }
        if (FALLBACK_FONT_FAMILY_LATIN.equals(family) ||
                LEGACY_FALLBACK_FONT_FAMILY_LATIN_VF.equals(family)) {
            return "MiSansLatin";
        }
        if (FALLBACK_FONT_FAMILY_TC.equals(family) ||
                FALLBACK_FONT_FAMILY_TC_ALT.equals(family) ||
                LEGACY_FALLBACK_FONT_FAMILY_TC_VF_ALT.equals(family) ||
                LEGACY_FALLBACK_FONT_FAMILY_TC_VF.equals(family)) {
            return "MisansTC";
        }
        return null;
    }

    public static int normalizeFontWeight(int weight) {
        int clamped = Math.max(FONT_WEIGHT_MIN, Math.min(FONT_WEIGHT_MAX, weight));
        int offset = clamped - FONT_WEIGHT_MIN;
        int roundedOffset = Math.round(offset / (float) FONT_WEIGHT_STEP) * FONT_WEIGHT_STEP;
        return FONT_WEIGHT_MIN + roundedOffset;
    }
}
