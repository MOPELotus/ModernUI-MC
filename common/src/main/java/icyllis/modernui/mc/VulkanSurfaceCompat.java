package icyllis.modernui.mc;

import java.util.function.IntPredicate;

import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_TRUE;

/** Checks actual presentation support, independently of VulkanMod queue fallbacks. */
public final class VulkanSurfaceCompat {
    private VulkanSurfaceCompat() {}

    public static boolean supportsPresentation(int result, int supported) {
        return result == VK_SUCCESS && supported == VK_TRUE;
    }

    public static boolean hasPresentQueue(int familyCount, IntPredicate querySupport) {
        for (int family = 0; family < familyCount; family++) {
            if (querySupport.test(family)) return true;
        }
        return false;
    }
}
