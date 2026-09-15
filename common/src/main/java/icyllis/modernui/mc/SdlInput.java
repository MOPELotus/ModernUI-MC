package icyllis.modernui.mc;

import com.mojang.blaze3d.platform.InputConstants;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;

import static org.lwjgl.sdl.SDLScancode.*;

/** Converts Minecraft 26.3 SDL input to the ModernUI core input representation. */
public final class SdlInput {
    public interface KeycodeCarrier {
        void modernui$setKeycode(int keycode);
        int modernui$getKeycode();
    }

    private SdlInput() {
    }

    // Minecraft key() is a physical SDL scancode; ModernUI 3.13 uses GLFW key values.
    public static int toModernKey(int scancode) {
        return switch (scancode) {
            case SDL_SCANCODE_SPACE -> KeyEvent.KEY_SPACE;
            case SDL_SCANCODE_APOSTROPHE -> KeyEvent.KEY_APOSTROPHE;
            case SDL_SCANCODE_COMMA -> KeyEvent.KEY_COMMA;
            case SDL_SCANCODE_MINUS -> KeyEvent.KEY_MINUS;
            case SDL_SCANCODE_PERIOD -> KeyEvent.KEY_PERIOD;
            case SDL_SCANCODE_SLASH -> KeyEvent.KEY_SLASH;
            case SDL_SCANCODE_0 -> KeyEvent.KEY_0;
            case SDL_SCANCODE_1 -> KeyEvent.KEY_1;
            case SDL_SCANCODE_2 -> KeyEvent.KEY_2;
            case SDL_SCANCODE_3 -> KeyEvent.KEY_3;
            case SDL_SCANCODE_4 -> KeyEvent.KEY_4;
            case SDL_SCANCODE_5 -> KeyEvent.KEY_5;
            case SDL_SCANCODE_6 -> KeyEvent.KEY_6;
            case SDL_SCANCODE_7 -> KeyEvent.KEY_7;
            case SDL_SCANCODE_8 -> KeyEvent.KEY_8;
            case SDL_SCANCODE_9 -> KeyEvent.KEY_9;
            case SDL_SCANCODE_SEMICOLON -> KeyEvent.KEY_SEMICOLON;
            case SDL_SCANCODE_EQUALS -> KeyEvent.KEY_EQUAL;
            case SDL_SCANCODE_A -> KeyEvent.KEY_A;
            case SDL_SCANCODE_B -> KeyEvent.KEY_B;
            case SDL_SCANCODE_C -> KeyEvent.KEY_C;
            case SDL_SCANCODE_D -> KeyEvent.KEY_D;
            case SDL_SCANCODE_E -> KeyEvent.KEY_E;
            case SDL_SCANCODE_F -> KeyEvent.KEY_F;
            case SDL_SCANCODE_G -> KeyEvent.KEY_G;
            case SDL_SCANCODE_H -> KeyEvent.KEY_H;
            case SDL_SCANCODE_I -> KeyEvent.KEY_I;
            case SDL_SCANCODE_J -> KeyEvent.KEY_J;
            case SDL_SCANCODE_K -> KeyEvent.KEY_K;
            case SDL_SCANCODE_L -> KeyEvent.KEY_L;
            case SDL_SCANCODE_M -> KeyEvent.KEY_M;
            case SDL_SCANCODE_N -> KeyEvent.KEY_N;
            case SDL_SCANCODE_O -> KeyEvent.KEY_O;
            case SDL_SCANCODE_P -> KeyEvent.KEY_P;
            case SDL_SCANCODE_Q -> KeyEvent.KEY_Q;
            case SDL_SCANCODE_R -> KeyEvent.KEY_R;
            case SDL_SCANCODE_S -> KeyEvent.KEY_S;
            case SDL_SCANCODE_T -> KeyEvent.KEY_T;
            case SDL_SCANCODE_U -> KeyEvent.KEY_U;
            case SDL_SCANCODE_V -> KeyEvent.KEY_V;
            case SDL_SCANCODE_W -> KeyEvent.KEY_W;
            case SDL_SCANCODE_X -> KeyEvent.KEY_X;
            case SDL_SCANCODE_Y -> KeyEvent.KEY_Y;
            case SDL_SCANCODE_Z -> KeyEvent.KEY_Z;
            case SDL_SCANCODE_LEFTBRACKET -> KeyEvent.KEY_LEFT_BRACKET;
            case SDL_SCANCODE_BACKSLASH -> KeyEvent.KEY_BACKSLASH;
            case SDL_SCANCODE_RIGHTBRACKET -> KeyEvent.KEY_RIGHT_BRACKET;
            case SDL_SCANCODE_GRAVE -> KeyEvent.KEY_GRAVE_ACCENT;
            case SDL_SCANCODE_ESCAPE -> KeyEvent.KEY_ESCAPE;
            case SDL_SCANCODE_RETURN -> KeyEvent.KEY_ENTER;
            case SDL_SCANCODE_TAB -> KeyEvent.KEY_TAB;
            case SDL_SCANCODE_BACKSPACE -> KeyEvent.KEY_BACKSPACE;
            case SDL_SCANCODE_INSERT -> KeyEvent.KEY_INSERT;
            case SDL_SCANCODE_DELETE -> KeyEvent.KEY_DELETE;
            case SDL_SCANCODE_RIGHT -> KeyEvent.KEY_RIGHT;
            case SDL_SCANCODE_LEFT -> KeyEvent.KEY_LEFT;
            case SDL_SCANCODE_DOWN -> KeyEvent.KEY_DOWN;
            case SDL_SCANCODE_UP -> KeyEvent.KEY_UP;
            case SDL_SCANCODE_PAGEUP -> KeyEvent.KEY_PAGE_UP;
            case SDL_SCANCODE_PAGEDOWN -> KeyEvent.KEY_PAGE_DOWN;
            case SDL_SCANCODE_HOME -> KeyEvent.KEY_HOME;
            case SDL_SCANCODE_END -> KeyEvent.KEY_END;
            case SDL_SCANCODE_CAPSLOCK -> KeyEvent.KEY_CAPS_LOCK;
            case SDL_SCANCODE_SCROLLLOCK -> KeyEvent.KEY_SCROLL_LOCK;
            case SDL_SCANCODE_NUMLOCKCLEAR -> KeyEvent.KEY_NUM_LOCK;
            case SDL_SCANCODE_PRINTSCREEN -> KeyEvent.KEY_PRINT_SCREEN;
            case SDL_SCANCODE_PAUSE -> KeyEvent.KEY_PAUSE;
            case SDL_SCANCODE_F1 -> KeyEvent.KEY_F1;
            case SDL_SCANCODE_F2 -> KeyEvent.KEY_F2;
            case SDL_SCANCODE_F3 -> KeyEvent.KEY_F3;
            case SDL_SCANCODE_F4 -> KeyEvent.KEY_F4;
            case SDL_SCANCODE_F5 -> KeyEvent.KEY_F5;
            case SDL_SCANCODE_F6 -> KeyEvent.KEY_F6;
            case SDL_SCANCODE_F7 -> KeyEvent.KEY_F7;
            case SDL_SCANCODE_F8 -> KeyEvent.KEY_F8;
            case SDL_SCANCODE_F9 -> KeyEvent.KEY_F9;
            case SDL_SCANCODE_F10 -> KeyEvent.KEY_F10;
            case SDL_SCANCODE_F11 -> KeyEvent.KEY_F11;
            case SDL_SCANCODE_F12 -> KeyEvent.KEY_F12;
            case SDL_SCANCODE_F13 -> KeyEvent.KEY_F13;
            case SDL_SCANCODE_F14 -> KeyEvent.KEY_F14;
            case SDL_SCANCODE_F15 -> KeyEvent.KEY_F15;
            case SDL_SCANCODE_F16 -> KeyEvent.KEY_F16;
            case SDL_SCANCODE_F17 -> KeyEvent.KEY_F17;
            case SDL_SCANCODE_F18 -> KeyEvent.KEY_F18;
            case SDL_SCANCODE_F19 -> KeyEvent.KEY_F19;
            case SDL_SCANCODE_F20 -> KeyEvent.KEY_F20;
            case SDL_SCANCODE_F21 -> KeyEvent.KEY_F21;
            case SDL_SCANCODE_F22 -> KeyEvent.KEY_F22;
            case SDL_SCANCODE_F23 -> KeyEvent.KEY_F23;
            case SDL_SCANCODE_F24 -> KeyEvent.KEY_F24;
            case SDL_SCANCODE_KP_0 -> KeyEvent.KEY_KP_0;
            case SDL_SCANCODE_KP_1 -> KeyEvent.KEY_KP_1;
            case SDL_SCANCODE_KP_2 -> KeyEvent.KEY_KP_2;
            case SDL_SCANCODE_KP_3 -> KeyEvent.KEY_KP_3;
            case SDL_SCANCODE_KP_4 -> KeyEvent.KEY_KP_4;
            case SDL_SCANCODE_KP_5 -> KeyEvent.KEY_KP_5;
            case SDL_SCANCODE_KP_6 -> KeyEvent.KEY_KP_6;
            case SDL_SCANCODE_KP_7 -> KeyEvent.KEY_KP_7;
            case SDL_SCANCODE_KP_8 -> KeyEvent.KEY_KP_8;
            case SDL_SCANCODE_KP_9 -> KeyEvent.KEY_KP_9;
            case SDL_SCANCODE_KP_PERIOD -> KeyEvent.KEY_KP_DECIMAL;
            case SDL_SCANCODE_KP_DIVIDE -> KeyEvent.KEY_KP_DIVIDE;
            case SDL_SCANCODE_KP_MULTIPLY -> KeyEvent.KEY_KP_MULTIPLY;
            case SDL_SCANCODE_KP_MINUS -> KeyEvent.KEY_KP_SUBTRACT;
            case SDL_SCANCODE_KP_PLUS -> KeyEvent.KEY_KP_ADD;
            case SDL_SCANCODE_KP_ENTER -> KeyEvent.KEY_KP_ENTER;
            case SDL_SCANCODE_KP_EQUALS -> KeyEvent.KEY_KP_EQUAL;
            case SDL_SCANCODE_LSHIFT -> KeyEvent.KEY_LEFT_SHIFT;
            case SDL_SCANCODE_LCTRL -> KeyEvent.KEY_LEFT_CONTROL;
            case SDL_SCANCODE_LALT -> KeyEvent.KEY_LEFT_ALT;
            case SDL_SCANCODE_LGUI -> KeyEvent.KEY_LEFT_SUPER;
            case SDL_SCANCODE_RSHIFT -> KeyEvent.KEY_RIGHT_SHIFT;
            case SDL_SCANCODE_RCTRL -> KeyEvent.KEY_RIGHT_CONTROL;
            case SDL_SCANCODE_RALT -> KeyEvent.KEY_RIGHT_ALT;
            case SDL_SCANCODE_RGUI -> KeyEvent.KEY_RIGHT_SUPER;
            case SDL_SCANCODE_APPLICATION -> KeyEvent.KEY_MENU;
            default -> KeyEvent.KEY_UNKNOWN;
        };
    }

    public static int toModernModifiers(int modifiers) {
        int result = 0;
        if ((modifiers & InputConstants.MOD_SHIFT) != 0) result |= KeyEvent.META_SHIFT_ON;
        if ((modifiers & InputConstants.MOD_CONTROL) != 0) result |= KeyEvent.META_CONTROL_ON;
        if ((modifiers & InputConstants.MOD_ALT) != 0) result |= KeyEvent.META_ALT_ON;
        if ((modifiers & InputConstants.MOD_SUPER) != 0) result |= KeyEvent.META_SUPER_ON;
        // ModernUI KeyEvent retains GLFW's lock modifier bits.
        if ((modifiers & InputConstants.MOD_CAPS_LOCK) != 0) result |= 0x10;
        if ((modifiers & InputConstants.MOD_NUM_LOCK) != 0) result |= 0x20;
        return result;
    }

    public static int toMinecraftModifiers(int modifiers) {
        int result = 0;
        if ((modifiers & KeyEvent.META_SHIFT_ON) != 0) result |= InputConstants.MOD_SHIFT;
        if ((modifiers & KeyEvent.META_CONTROL_ON) != 0) result |= InputConstants.MOD_CONTROL;
        if ((modifiers & KeyEvent.META_ALT_ON) != 0) result |= InputConstants.MOD_ALT;
        if ((modifiers & KeyEvent.META_SUPER_ON) != 0) result |= InputConstants.MOD_SUPER;
        if ((modifiers & 0x10) != 0) result |= InputConstants.MOD_CAPS_LOCK;
        if ((modifiers & 0x20) != 0) result |= InputConstants.MOD_NUM_LOCK;
        return result;
    }

    public static int toModernButton(int button) {
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> MotionEvent.BUTTON_PRIMARY;
            case InputConstants.MOUSE_BUTTON_RIGHT -> MotionEvent.BUTTON_SECONDARY;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> MotionEvent.BUTTON_TERTIARY;
            case InputConstants.MOUSE_BUTTON_4 -> MotionEvent.BUTTON_BACK;
            case InputConstants.MOUSE_BUTTON_5 -> MotionEvent.BUTTON_FORWARD;
            default -> 0;
        };
    }

    public static int toModernButtonState(int sdlState) {
        int result = 0;
        for (int button = 1; button <= 5; button++) {
            if ((sdlState & (1 << (button - 1))) != 0) result |= toModernButton(button);
        }
        return result;
    }
}
