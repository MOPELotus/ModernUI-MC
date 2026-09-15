package icyllis.modernui.mc.mixin;

import icyllis.modernui.core.Core;
import org.lwjgl.sdl.SDLTimer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/** Minecraft owns the SDL lifecycle; ModernUI shares its main thread and clock. */
@Mixin(value = Core.class, remap = false)
public abstract class MixinModernUICore {
    @Shadow
    private static volatile Thread sMainThread;

    /** @author MOPELotus @reason Attach to Minecraft's initialized SDL backend. */
    @Overwrite
    public static void initialize() {
        synchronized (Core.class) {
            if (sMainThread == null) {
                sMainThread = Thread.currentThread();
            } else {
                assert false;
            }
        }
    }

    /** @author MOPELotus @reason Minecraft shuts down its own SDL backend. */
    @Overwrite
    public static void terminate() {
        Core.checkMainThread();
    }

    /** @author MOPELotus @reason Use the same monotonic clock as Minecraft. */
    @Overwrite
    public static long timeNanos() {
        return SDLTimer.SDL_GetTicksNS();
    }

    /** @author MOPELotus @reason Use the same monotonic clock as Minecraft. */
    @Overwrite
    public static long timeMillis() {
        return SDLTimer.SDL_GetTicks();
    }
}
