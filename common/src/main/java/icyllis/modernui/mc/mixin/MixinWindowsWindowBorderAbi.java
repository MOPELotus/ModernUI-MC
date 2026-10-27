package icyllis.modernui.mc.mixin;

import org.lwjgl.system.windows.User32;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.IntBuffer;

/** ModernUI passes no last-error output buffer; use the matching LWJGL 3.3.3 entry points. */
@Pseudo
@Mixin(targets = "icyllis.modernui.core.windows.WindowsNativeWindowBorder$WndProc", remap = false)
public abstract class MixinWindowsWindowBorderAbi {
    @Redirect(method = {"<init>(J)V", "destroy()V"}, at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/system/windows/User32;SetWindowLongPtr(Ljava/nio/IntBuffer;JIJ)J"), require = 2, allow = 2)
    private static long modernui$setWindowLongPtr(IntBuffer lastError, long window, int index, long value) {
        if (lastError != null) throw new IllegalArgumentException("Unsupported window last-error buffer");
        return User32.SetWindowLongPtr(window, index, value);
    }

    @Redirect(method = {"<init>(J)V", "destroy()V"}, at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/system/windows/User32;SetWindowPos(Ljava/nio/IntBuffer;JJIIIII)Z"), require = 2, allow = 2)
    private static boolean modernui$setWindowPos(IntBuffer lastError, long window, long after,
                                               int x, int y, int width, int height, int flags) {
        if (lastError != null) throw new IllegalArgumentException("Unsupported window last-error buffer");
        return User32.SetWindowPos(window, after, x, y, width, height, flags);
    }
}
