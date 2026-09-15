package icyllis.modernui.mc.test;

import icyllis.modernui.mc.SdlInput;

/** Regression checks for the incompatible SDL and ModernUI input number spaces. */
public final class SdlInputCheck {
    public static void main(String[] args) {
        // Values from Minecraft 26.3 InputConstants and ModernUI Core 3.13 KeyEvent.
        equal(65, SdlInput.toModernKey(4), "physical A");
        equal(90, SdlInput.toModernKey(29), "physical Z");
        equal(48, SdlInput.toModernKey(39), "zero");
        equal(256, SdlInput.toModernKey(41), "escape");
        equal(257, SdlInput.toModernKey(40), "enter");
        equal(335, SdlInput.toModernKey(88), "keypad enter");
        equal(345, SdlInput.toModernKey(228), "right control");
        equal(-1, SdlInput.toModernKey(0), "unknown key");
        equal(-1, SdlInput.toModernKey(512), "unmapped scancode");

        equal(1, SdlInput.toModernModifiers(2), "right shift must not become control");
        equal(2, SdlInput.toModernModifiers(128), "right control");
        equal(4, SdlInput.toModernModifiers(512), "right alt");
        equal(8, SdlInput.toModernModifiers(2048), "right GUI");
        equal(63, SdlInput.toModernModifiers(3 | 192 | 768 | 3072 | 4096 | 8192), "all modifiers");
        for (int modifiers = 0; modifiers < 64; modifiers++) {
            equal(modifiers, SdlInput.toModernModifiers(SdlInput.toMinecraftModifiers(modifiers)),
                    "modifier round trip " + modifiers);
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
