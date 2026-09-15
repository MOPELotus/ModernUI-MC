package icyllis.modernui.mc.mixin;

import icyllis.modernui.core.Clipboard;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = Clipboard.class, remap = false)
public abstract class MixinModernUIClipboard {
    /** @author MOPELotus @reason Share Minecraft's SDL clipboard implementation. */
    @Overwrite
    public static String getText() {
        return Minecraft.getInstance().keyboardHandler.getClipboard();
    }

    /** @author MOPELotus @reason Share Minecraft's SDL clipboard implementation. */
    @Overwrite
    public static void setText(CharSequence text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text.toString());
    }
}
