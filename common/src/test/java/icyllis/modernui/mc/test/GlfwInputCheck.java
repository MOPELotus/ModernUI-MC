package icyllis.modernui.mc.test;

import icyllis.modernui.mc.UIManager;
import icyllis.modernui.text.Selection;
import icyllis.modernui.text.SpannableStringBuilder;
import icyllis.modernui.text.method.TextKeyListener;
import icyllis.modernui.view.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.lwjgl.glfw.GLFW.*;

/** Checks actual Minecraft event fields and the GLFW-to-Core editing boundary. */
public final class GlfwInputCheck {
    public static void main(String[] args) throws Exception {
        checkInputBoundary();
        // Distinct key/scancode/modifier values catch accidental constructor reordering.
        var minecraftEvent = new net.minecraft.client.input.KeyEvent(GLFW_KEY_A, 38, GLFW_MOD_CONTROL);
        equal(GLFW_KEY_A, minecraftEvent.key(), "Minecraft key");
        equal(38, minecraftEvent.scancode(), "Minecraft scancode");
        equal(GLFW_MOD_CONTROL, minecraftEvent.modifiers(), "Minecraft modifiers");
        var character = new CharacterEvent(0x1f600, GLFW_MOD_SHIFT);
        equal(0x1f600, character.codepoint(), "supplementary character codepoint");
        equal(GLFW_MOD_SHIFT, character.modifiers(), "character modifiers");

        var mouse = new MouseButtonEvent(17.25, 29.5,
                new MouseButtonInfo(GLFW_MOUSE_BUTTON_RIGHT, GLFW_MOD_ALT));
        if (mouse.x() != 17.25 || mouse.y() != 29.5) throw new AssertionError("Minecraft mouse position order");
        equal(GLFW_MOUSE_BUTTON_RIGHT, mouse.button(), "Minecraft mouse button");
        equal(GLFW_MOD_ALT, mouse.modifiers(), "Minecraft mouse modifiers");

        for (int locks : new int[]{0, GLFW_MOD_CAPS_LOCK, GLFW_MOD_NUM_LOCK,
                GLFW_MOD_CAPS_LOCK | GLFW_MOD_NUM_LOCK}) {
            for (int shortcuts = 0; shortcuts < 16; shortcuts++) {
                equal(shortcuts, UIManager.normalizeModifiers(locks | shortcuts),
                        "shortcut modifiers with locks " + locks);
            }
            // Core compares exact modifier values, so lock bits must not reach it.
            var editable = new SpannableStringBuilder("A测😀");
            Selection.setSelection(editable, editable.length());
            var backspace = KeyEvent.obtain(0, KeyEvent.ACTION_DOWN, GLFW_KEY_BACKSPACE,
                    0, UIManager.normalizeModifiers(locks), 22, 0);
            try {
                if (!TextKeyListener.getInstance().onKeyDown(null, editable, GLFW_KEY_BACKSPACE, backspace)
                        || !editable.toString().equals("A测")) {
                    throw new AssertionError("Core Backspace failed with lock state " + locks);
                }
            } finally {
                backspace.recycle();
            }
            var shortcut = KeyEvent.obtain(0, KeyEvent.ACTION_DOWN, minecraftEvent.key(),
                    0, UIManager.normalizeModifiers(locks | minecraftEvent.modifiers()), minecraftEvent.scancode(), 0);
            try {
                if (!shortcut.hasModifiers(KeyEvent.META_CONTROL_ON)) {
                    throw new AssertionError("Core Ctrl+A failed with lock state " + locks);
                }
            } finally {
                shortcut.recycle();
            }
        }
        System.out.println("GLFW event parameter order, lock modifiers and Core text editing checks passed");
    }

    private static void checkInputBoundary() throws Exception {
        // Testing the helper alone would miss a disconnected input integration.
        var node = new ClassNode();
        try (var stream = UIManager.class.getResourceAsStream("UIManager.class")) {
            if (stream == null) throw new AssertionError("Missing compiled UIManager");
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        for (String name : new String[]{"onKeyPress", "onKeyRelease", "onPostMouseInput"}) {
            boolean normalizes = false;
            for (var method : node.methods) {
                if (!method.name.equals(name)) continue;
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call
                            && call.owner.equals("icyllis/modernui/mc/UIManager")
                            && call.name.equals("normalizeModifiers") && call.desc.equals("(I)I")) {
                        normalizes = true;
                    }
                }
            }
            if (!normalizes) throw new AssertionError(name + " must normalize GLFW modifiers before Core dispatch");
        }
    }

    private static void equal(int expected, int actual, String description) {
        if (actual != expected) throw new AssertionError(description + ": expected " + expected + ", got " + actual);
    }
}
