package icyllis.modernui.mc;

import java.nio.ByteBuffer;
import java.util.*;

/** Keeps explicit block array layouts separate from logical values in Arc3D 2026.2 SPIR-V. */
public final class SpirvLayoutCompat {
    private SpirvLayoutCompat() {}

    public static ByteBuffer repair(ByteBuffer source) {
        if (source == null) return null;
        var input = source.duplicate().order(source.order()).asIntBuffer();
        int[] header = new int[5];
        input.get(header);
        if (header[0] != 0x07230203 || header[1] < 0x00010400) {
            throw new IllegalStateException("Vulkan array layout compatibility requires SPIR-V 1.4 or later");
        }
        var instructions = new ArrayList<int[]>();
        var types = new HashMap<Integer, int[]>();
        var constants = new HashMap<Integer, Integer>();
        while (input.hasRemaining()) {
            int word = input.get(), count = word >>> 16;
            if (count == 0 || count - 1 > input.remaining()) throw new IllegalArgumentException("Invalid SPIR-V instruction");
            int[] instruction = new int[count];
            instruction[0] = word;
            input.get(instruction, 1, count - 1);
            instructions.add(instruction);
            int opcode = word & 0xffff;
            if (opcode >= 19 && opcode <= 39) types.put(instruction[1], instruction);
            if (opcode == 43 && count == 4) constants.put(instruction[2], instruction[3]);
        }
        var values = new HashMap<Integer, Integer>();
        for (int[] instruction : instructions) {
            int opcode = instruction[0] & 0xffff;
            if ((opcode > 39 || opcode == 12) && opcode != 71 && opcode != 72
                    && instruction.length >= 3 && types.containsKey(instruction[1])) {
                values.put(instruction[2], instruction[1]);
            }
        }
        var repair = new Repair(types, constants, values, header[3]);
        var body = new ArrayList<int[]>();
        for (int[] instruction : instructions) repair.instruction(instruction, body);
        if (!repair.changed) return source;
        header[3] = repair.nextId;
        int count = 5 + repair.pointers.stream().mapToInt(i -> i.length).sum()
                + body.stream().mapToInt(i -> i.length).sum();
        ByteBuffer output = ByteBuffer.allocateDirect(count * 4).order(source.order());
        for (int word : header) output.putInt(word);
        boolean inserted = false;
        for (int[] instruction : body) {
            if (!inserted && (instruction[0] & 0xffff) == 54) {
                for (int[] pointer : repair.pointers) for (int word : pointer) output.putInt(word);
                inserted = true;
            }
            for (int word : instruction) output.putInt(word);
        }
        return output.flip();
    }

    private static final class Repair {
        final Map<Integer, int[]> types;
        final Map<Integer, Integer> constants;
        final Map<Integer, Integer> values;
        final List<int[]> pointers = new ArrayList<>();
        int nextId;
        boolean changed;

        Repair(Map<Integer, int[]> types, Map<Integer, Integer> constants,
               Map<Integer, Integer> values, int nextId) {
            this.types = types;
            this.constants = constants;
            this.values = values;
            this.nextId = nextId;
        }

        void instruction(int[] words, List<int[]> output) {
            int opcode = words[0] & 0xffff;
            if (opcode == 65 || opcode == 66) { // Op[InBounds]AccessChain
                int[] base = pointer(words[3]);
                int actual = base[3];
                for (int i = 4; i < words.length; i++) actual = indexed(actual, constants.get(words[i]));
                int[] declared = types.get(words[1]);
                if (declared[2] != base[2]) throw new IllegalStateException("AccessChain storage class differs from its base");
                if (declared[3] != actual) {
                    requireLogicalMatch(declared[3], actual);
                    words = words.clone();
                    words[1] = pointerType(base[2], actual);
                    values.put(words[2], words[1]);
                    changed = true;
                }
            } else if (opcode == 61) { // OpLoad: load the real block type, then obtain a logical value.
                int actual = pointer(words[3])[3];
                if (actual != words[1]) {
                    requireLogicalMatch(words[1], actual);
                    int logical = words[1], result = words[2], loaded = nextId++;
                    int[] load = words.clone();
                    load[1] = actual;
                    load[2] = loaded;
                    output.add(load);
                    values.put(loaded, actual);
                    output.add(copy(logical, result, loaded));
                    changed = true;
                    return;
                }
            } else if (opcode == 62) { // OpStore: convert logical values back to the actual block type.
                int actual = pointer(words[1])[3];
                Integer valueType = values.get(words[2]);
                if (valueType != null && valueType != actual) {
                    requireLogicalMatch(valueType, actual);
                    int converted = nextId++;
                    output.add(copy(actual, converted, words[2]));
                    words = words.clone();
                    words[2] = converted;
                    changed = true;
                }
            } else if (opcode == 81) { // OpCompositeExtract of nested arrays/structs.
                Integer composite = values.get(words[3]);
                if (composite != null) {
                    int actual = composite;
                    for (int i = 4; i < words.length; i++) actual = indexed(actual, words[i]);
                    if (actual != words[1]) {
                        requireLogicalMatch(words[1], actual);
                        int logical = words[1], result = words[2], extracted = nextId++;
                        int[] extract = words.clone();
                        extract[1] = actual;
                        extract[2] = extracted;
                        output.add(extract);
                        values.put(extracted, actual);
                        output.add(copy(logical, result, extracted));
                        changed = true;
                        return;
                    }
                }
            }
            output.add(words);
        }

        int[] pointer(int value) {
            int[] type = types.get(values.get(value));
            if (type == null || (type[0] & 0xffff) != 32) {
                throw new IllegalStateException("Missing SPIR-V pointer type for " + value);
            }
            return type;
        }

        int indexed(int id, Integer index) {
            int[] type = types.get(id);
            return switch (type[0] & 0xffff) {
                case 23, 24, 28, 29 -> type[2]; // vector, matrix, array, runtime array
                case 30 -> {
                    if (index == null || index < 0 || index + 2 >= type.length) {
                        throw new IllegalStateException("Invalid SPIR-V struct index");
                    }
                    yield type[index + 2];
                }
                default -> throw new IllegalStateException("Cannot index SPIR-V type " + id);
            };
        }

        int pointerType(int storage, int element) {
            for (int[] type : types.values()) {
                if ((type[0] & 0xffff) == 32 && type[2] == storage && type[3] == element) return type[1];
            }
            int id = nextId++;
            int[] pointer = {(4 << 16) | 32, id, storage, element};
            types.put(id, pointer);
            pointers.add(pointer);
            return id;
        }

        int[] copy(int type, int result, int source) {
            values.put(result, type);
            return new int[]{(4 << 16) | 400, type, result, source}; // OpCopyLogical, SPIR-V 1.4+
        }

        void requireLogicalMatch(int first, int second) {
            if (!logicalMatch(first, second)) {
                throw new IllegalStateException("SPIR-V types differ beyond explicit array layout: " + first + "/" + second);
            }
        }

        boolean logicalMatch(int first, int second) {
            if (first == second) return true;
            int[] a = types.get(first), b = types.get(second);
            if (a == null || b == null || a.length != b.length || a[0] != b[0]) return false;
            int opcode = a[0] & 0xffff;
            if (opcode == 28) {
                Integer length = constants.get(a[3]);
                return length != null && length.equals(constants.get(b[3])) && logicalMatch(a[2], b[2]);
            }
            if (opcode == 30) {
                for (int i = 2; i < a.length; i++) if (!logicalMatch(a[i], b[i])) return false;
                return true;
            }
            return false;
        }
    }
}
