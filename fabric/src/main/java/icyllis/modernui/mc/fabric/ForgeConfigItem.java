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

package icyllis.modernui.mc.fabric;

import icyllis.modernui.mc.ConfigItem;
import org.apache.commons.lang3.Range;

import java.util.List;

public class ForgeConfigItem<T> extends ConfigItem<T> {

    private final SimpleConfigSpec.ConfigValue<T> value;
    private final SimpleConfigSpec.ValueSpec spec;

    public ForgeConfigItem(SimpleConfigSpec.ConfigValue<T> value,
                           SimpleConfigSpec.ValueSpec spec) {
        this.value = value;
        this.spec = spec;
    }

    @Override
    public T get() {
        return value.get();
    }

    @Override
    public List<String> getPath() {
        return value.getPath();
    }

    @Override
    public void set(T value) {
        this.value.set(value);
    }

    @Override
    public T getDefault() {
        return value.getDefault();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Range<T> getRange() {
        Object min = spec.getMin();
        Object max = spec.getMax();
        if (min instanceof Comparable<?> && max instanceof Comparable<?>) {
            return (Range<T>) Range.of((Comparable) min, (Comparable) max);
        }
        return null;
    }
}
