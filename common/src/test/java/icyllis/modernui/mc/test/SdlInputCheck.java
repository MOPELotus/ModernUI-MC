package icyllis.modernui.mc.test;

import icyllis.modernui.mc.SdlInput;
import icyllis.modernui.text.Selection;
import icyllis.modernui.text.SpannableStringBuilder;
import icyllis.modernui.text.method.TextKeyListener;
import icyllis.modernui.view.KeyEvent;

/** Regression checks for the incompatible SDL and ModernUI input number spaces. */
public final class SdlInputCheck {
    public static void main(String[] args) {
        // Values from Minecraft 26.3 InputConstants and ModernUI Core 3.13 KeyEvent.
        equal(65, SdlInput.toModernKey(4), "physical A");
        equal(90, SdlInput.toModernKey(29), "physical Z");
        equal(48, SdlInput.toModernKey(39), "zero");
        equal(256, SdlInput.toModernKey(41), "escape");
        equal(257, SdlInput.toModernKey(40), "enter");
        equal(259, SdlInput.toModernKey(42), "backspace");
        equal(261, SdlInput.toModernKey(76), "delete");
        equal(335, SdlInput.toModernKey(88), "keypad enter");
        equal(345, SdlInput.toModernKey(228), "right control");
        equal(-1, SdlInput.toModernKey(0), "unknown key");
        equal(-1, SdlInput.toModernKey(512), "unmapped scancode");

        equal(1, SdlInput.toModernModifiers(2), "right shift must not become control");
        equal(2, SdlInput.toModernModifiers(128), "right control");
        equal(4, SdlInput.toModernModifiers(512), "right alt");
        equal(8, SdlInput.toModernModifiers(2048), "right GUI");
        equal(15, SdlInput.toModernModifiers(3 | 192 | 768 | 3072 | 4096 | 8192), "shortcut modifiers only");
        for (int modifiers = 0; modifiers < 16; modifiers++) {
            equal(modifiers, SdlInput.toModernModifiers(SdlInput.toMinecraftModifiers(modifiers)),
                    "modifier round trip " + modifiers);
        }

        for (int locks : new int[]{0, 4096, 8192, 4096 | 8192}) {
            equal(0, SdlInput.toModernModifiers(locks),
                    "plain editing with locks " + locks);
            equal(1, SdlInput.toModernModifiers(locks | 3),
                    "shift selection with locks " + locks);
            equal(2, SdlInput.toModernModifiers(locks | 192),
                    "control word editing with locks " + locks);
            equal(4, SdlInput.toModernModifiers(locks | 768),
                    "alt editing with locks " + locks);
            equal(8, SdlInput.toModernModifiers(locks | 3072),
                    "super editing with locks " + locks);

            // Exercise Core's actual deletion and exact-match shortcut handling.
            var editable = new SpannableStringBuilder("A测😀");
            Selection.setSelection(editable, editable.length());
            var backspace = KeyEvent.obtain(0, KeyEvent.ACTION_DOWN, KeyEvent.KEY_BACKSPACE,
                    0, SdlInput.toModernModifiers(locks), 42, 0);
            if (!TextKeyListener.getInstance().onKeyDown(null, editable, KeyEvent.KEY_BACKSPACE, backspace)
                    || !editable.toString().equals("A测")) {
                throw new AssertionError("Core Backspace failed with lock state " + locks);
            }
            backspace.recycle();
            var shortcut = KeyEvent.obtain(0, KeyEvent.ACTION_DOWN, KeyEvent.KEY_A,
                    0, SdlInput.toModernModifiers(locks | 192), 4, 0);
            if (!shortcut.hasModifiers(KeyEvent.META_CONTROL_ON)) {
                throw new AssertionError("Core Ctrl+A failed with lock state " + locks);
            }
            shortcut.recycle();
        }

        equal(1, SdlInput.toModernButton(1), "left mouse");
        equal(4, SdlInput.toModernButton(2), "middle mouse");
        equal(2, SdlInput.toModernButton(3), "right mouse");
        equal(0, SdlInput.toModernButton(0), "unknown mouse");
        equal(3, SdlInput.toModernButtonState(5), "left and right held together");
        equal(31, SdlInput.toModernButtonState(31), "all supported buttons held");
        equal(0, SdlInput.toModernButtonState(0), "all buttons released");
        System.out.println("SDL input conversion checks passed");
    }

    private static void equal(int expected, int actual, String description) {
        if (actual != expected) {
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
        }
    }
}
