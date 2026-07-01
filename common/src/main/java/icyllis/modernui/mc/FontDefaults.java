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
    public static final String MODPACK_FONT_DIRECTORY = "config/ModernUI/fonts";

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
        return FIRST_FONT_FAMILY.equals(family) ||
                FALLBACK_FONT_FAMILY_L3.equals(family) ||
                FALLBACK_FONT_FAMILY_LATIN.equals(family) ||
                FALLBACK_FONT_FAMILY_TC.equals(family);
    }
}
