/*
 * Modern UI.
 * Copyright (C) 2026 BloCamLimb. All rights reserved.
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

import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static icyllis.modernui.mc.ModernUIMod.LOGGER;
import static icyllis.modernui.mc.ModernUIMod.MARKER;

final class SimpleConfigSpec {

    private final Path file;
    private final List<ConfigValue<?>> values;
    private final Map<List<String>, ValueSpec> specs;

    private SimpleConfigSpec(@Nonnull Path file,
                             @Nonnull List<ConfigValue<?>> values,
                             @Nonnull Map<List<String>, ValueSpec> specs) {
        this.file = file;
        this.values = List.copyOf(values);
        this.specs = Map.copyOf(specs);
    }

    public Map<List<String>, ValueSpec> getSpec() {
        return specs;
    }

    public void load() {
        Map<List<String>, String> rawValues;
        try {
            rawValues = readToml(file);
        } catch (IOException e) {
            LOGGER.warn(MARKER, "Failed to read config {}, defaults will be used", file, e);
            rawValues = Collections.emptyMap();
        }
        for (ConfigValue<?> value : values) {
            value.load(rawValues.get(value.getPath()));
        }
    }

    public void save() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, writeToml(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error(MARKER, "Failed to save config {}", file, e);
        }
    }

    private String writeToml() {
        StringBuilder builder = new StringBuilder();
        List<String> lastSection = Collections.emptyList();
        boolean first = true;
        for (ConfigValue<?> value : values) {
            List<String> path = value.getPath();
            List<String> section = path.subList(0, path.size() - 1);
            if (!section.equals(lastSection)) {
                if (!first) {
                    builder.append('\n');
                }
                for (String comment : value.spec.comments) {
                    builder.append("# ").append(comment).append('\n');
                }
                builder.append('[').append(String.join(".", section)).append("]\n");
                lastSection = section;
                first = false;
            } else if (!first) {
                builder.append('\n');
            }
            for (String comment : value.spec.comments) {
                builder.append("# ").append(comment).append('\n');
            }
            builder.append(path.get(path.size() - 1))
                    .append(" = ")
                    .append(formatValue(value.get()))
                    .append('\n');
            first = false;
        }
        return builder.toString();
    }

    private static Map<List<String>, String> readToml(@Nonnull Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return Collections.emptyMap();
        }
        Map<List<String>, String> values = new HashMap<>();
        List<String> section = Collections.emptyList();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = stripComment(line).trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                String name = trimmed.substring(1, trimmed.length() - 1).trim();
                section = name.isEmpty() ? Collections.emptyList() : List.of(name.split("\\."));
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String key = trimmed.substring(0, equals).trim();
            if (key.isEmpty()) {
                continue;
            }
            ArrayList<String> path = new ArrayList<>(section);
            path.add(key);
            values.put(List.copyOf(path), trimmed.substring(equals + 1).trim());
        }
        return values;
    }

    private static String stripComment(@Nonnull String line) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (c == '#' && !inString) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String formatValue(@Nullable Object value) {
        if (value instanceof List<?> list) {
            StringJoiner joiner = new StringJoiner(", ", "[", "]");
            for (Object element : list) {
                joiner.add(formatValue(element));
            }
            return joiner.toString();
        }
        if (value instanceof String string) {
            return "\"" + escape(string) + "\"";
        }
        if (value instanceof Enum<?> enumValue) {
            return "\"" + enumValue.name() + "\"";
        }
        return String.valueOf(value);
    }

    private static String escape(@Nonnull String string) {
        return string.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unquote(@Nonnull String raw) {
        String value = raw.trim();
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1);
        }
        StringBuilder builder = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                builder.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                builder.append(c);
            }
        }
        if (escaped) {
            builder.append('\\');
        }
        return builder.toString();
    }

    private static List<String> parseStringList(@Nonnull String raw) {
        String value = raw.trim();
        if (!value.startsWith("[") || !value.endsWith("]")) {
            return List.of(unquote(value));
        }
        value = value.substring(1, value.length() - 1);
        ArrayList<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\' && inString) {
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
            } else if (c == ',' && !inString) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) {
            result.add(last);
        }
        result.replaceAll(SimpleConfigSpec::unquote);
        return List.copyOf(result);
    }

    static final class Builder {

        private final ArrayList<ConfigValue<?>> values = new ArrayList<>();
        private final HashMap<List<String>, ValueSpec> specs = new HashMap<>();
        private final ArrayList<String> section = new ArrayList<>();
        private List<String> pendingComments = List.of();

        public Builder comment(@Nonnull String... comments) {
            pendingComments = Arrays.asList(comments);
            return this;
        }

        public Builder push(@Nonnull String name) {
            section.add(name);
            pendingComments = List.of();
            return this;
        }

        public Builder pop() {
            section.remove(section.size() - 1);
            pendingComments = List.of();
            return this;
        }

        public BooleanValue define(@Nonnull String name, boolean defaultValue) {
            return add(new BooleanValue(path(name), defaultValue, spec(null, null)));
        }

        public ConfigValue<String> define(@Nonnull String name, @Nonnull String defaultValue) {
            return add(new ConfigValue<>(path(name), defaultValue, spec(null, null), String.class, null));
        }

        public IntValue defineInRange(@Nonnull String name, int defaultValue, int min, int max) {
            return add(new IntValue(path(name), defaultValue, spec(min, max)));
        }

        public DoubleValue defineInRange(@Nonnull String name, double defaultValue, double min, double max) {
            return add(new DoubleValue(path(name), defaultValue, spec(min, max)));
        }

        public <E extends Enum<E>> EnumValue<E> defineEnum(@Nonnull String name, @Nonnull E defaultValue) {
            return add(new EnumValue<>(path(name), defaultValue, spec(null, null), defaultValue.getDeclaringClass()));
        }

        public ConfigValue<List<? extends String>> defineList(@Nonnull String name,
                                                              @Nonnull Supplier<List<String>> defaultSupplier,
                                                              @Nonnull Predicate<Object> validator) {
            return add(new ConfigValue<>(path(name), List.copyOf(defaultSupplier.get()),
                    spec(null, null), List.class, validator));
        }

        public SimpleConfigSpec build(@Nonnull Path file) {
            return new SimpleConfigSpec(file, values, specs);
        }

        private List<String> path(@Nonnull String name) {
            ArrayList<String> path = new ArrayList<>(section);
            path.add(name);
            return List.copyOf(path);
        }

        private ValueSpec spec(@Nullable Object min, @Nullable Object max) {
            ValueSpec spec = new ValueSpec(pendingComments, min, max);
            pendingComments = List.of();
            return spec;
        }

        private <T extends ConfigValue<?>> T add(@Nonnull T value) {
            values.add(value);
            specs.put(value.getPath(), value.spec);
            return value;
        }
    }

    static class ConfigValue<T> {

        private final List<String> path;
        private final T defaultValue;
        final ValueSpec spec;
        private final Class<?> valueType;
        @Nullable
        private final Predicate<Object> validator;
        private T value;

        private ConfigValue(@Nonnull List<String> path,
                            @Nonnull T defaultValue,
                            @Nonnull ValueSpec spec,
                            @Nonnull Class<?> valueType,
                            @Nullable Predicate<Object> validator) {
            this.path = path;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
            this.spec = spec;
            this.valueType = valueType;
            this.validator = validator;
        }

        public T get() {
            return value;
        }

        public void set(T value) {
            this.value = sanitize(value);
        }

        public T getDefault() {
            return defaultValue;
        }

        public List<String> getPath() {
            return path;
        }

        @SuppressWarnings("unchecked")
        private void load(@Nullable String raw) {
            if (raw == null) {
                value = defaultValue;
                return;
            }
            try {
                Object parsed;
                if (valueType == Boolean.class) {
                    parsed = Boolean.parseBoolean(raw);
                } else if (valueType == Integer.class) {
                    parsed = Integer.parseInt(raw);
                } else if (valueType == Double.class) {
                    parsed = Double.parseDouble(raw);
                } else if (valueType == String.class) {
                    parsed = unquote(raw);
                } else if (valueType == List.class) {
                    parsed = parseStringList(raw);
                } else if (Enum.class.isAssignableFrom(valueType)) {
                    parsed = Enum.valueOf((Class<? extends Enum>) valueType.asSubclass(Enum.class), unquote(raw));
                } else {
                    parsed = defaultValue;
                }
                value = sanitize((T) parsed);
            } catch (RuntimeException e) {
                LOGGER.warn(MARKER, "Wrong config value for {}, using default {}", String.join(".", path),
                        defaultValue, e);
                value = defaultValue;
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private T sanitize(@Nullable T candidate) {
            if (candidate == null) {
                return defaultValue;
            }
            Object value = candidate;
            if (spec.min instanceof Number min && spec.max instanceof Number max && value instanceof Number number) {
                if (valueType == Integer.class) {
                    int n = Math.max(min.intValue(), Math.min(max.intValue(), number.intValue()));
                    value = n;
                } else if (valueType == Double.class) {
                    double n = Math.max(min.doubleValue(), Math.min(max.doubleValue(), number.doubleValue()));
                    value = n;
                }
            }
            if (validator != null && value instanceof List<?> list) {
                for (Object element : list) {
                    if (!validator.test(element)) {
                        return defaultValue;
                    }
                }
            }
            return (T) value;
        }
    }

    static final class BooleanValue extends ConfigValue<Boolean> {

        private BooleanValue(@Nonnull List<String> path, boolean defaultValue, @Nonnull ValueSpec spec) {
            super(path, defaultValue, spec, Boolean.class, null);
        }
    }

    static final class IntValue extends ConfigValue<Integer> {

        private IntValue(@Nonnull List<String> path, int defaultValue, @Nonnull ValueSpec spec) {
            super(path, defaultValue, spec, Integer.class, null);
        }
    }

    static final class DoubleValue extends ConfigValue<Double> {

        private DoubleValue(@Nonnull List<String> path, double defaultValue, @Nonnull ValueSpec spec) {
            super(path, defaultValue, spec, Double.class, null);
        }
    }

    static final class EnumValue<E extends Enum<E>> extends ConfigValue<E> {

        private EnumValue(@Nonnull List<String> path,
                          @Nonnull E defaultValue,
                          @Nonnull ValueSpec spec,
                          @Nonnull Class<E> enumClass) {
            super(path, defaultValue, spec, enumClass, null);
        }
    }

    static final class ValueSpec {

        private final List<String> comments;
        @Nullable
        private final Object min;
        @Nullable
        private final Object max;

        private ValueSpec(@Nonnull List<String> comments, @Nullable Object min, @Nullable Object max) {
            this.comments = List.copyOf(comments);
            this.min = min;
            this.max = max;
        }

        @Nullable
        public Object getMin() {
            return min;
        }

        @Nullable
        public Object getMax() {
            return max;
        }
    }
}
