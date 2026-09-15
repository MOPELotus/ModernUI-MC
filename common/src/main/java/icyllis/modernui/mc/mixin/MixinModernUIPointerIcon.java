package icyllis.modernui.mc.mixin;

import icyllis.modernui.view.PointerIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** UIManager maps icon types to Minecraft cursors on the SDL main thread. */
@Mixin(value = PointerIcon.class, remap = false)
public abstract class MixinModernUIPointerIcon {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwCreateStandardCursor(I)J"))
    private static long modernui$useMinecraftCursor(int shape) {
        return 0L;
    }
}
