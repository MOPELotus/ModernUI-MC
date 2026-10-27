package icyllis.modernui.mc.mixin;

import icyllis.modernui.mc.VulkanSurfaceCompat;
import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.device.DeviceManager", remap = false)
public abstract class MixinVulkanDeviceManager {
    // VulkanMod queries surface capabilities before rejecting unsuitable queue
    // families, and can substitute a compute queue for an unsupported present
    // queue. Reject only devices with no real presentation support, before any
    // capability/format/mode query that requires such support.
    @Inject(method = "isDeviceSuitable(Lorg/lwjgl/vulkan/VkPhysicalDevice;)Z",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void modernui$requirePresentSupport(VkPhysicalDevice device,
                                                      CallbackInfoReturnable<Boolean> ci) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var count = stack.ints(0);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(device, count, null);
            var supported = stack.ints(VK10.VK_FALSE);
            long surface = Vulkan.getSurface();
            boolean present = VulkanSurfaceCompat.hasPresentQueue(count.get(0), family -> {
                supported.put(0, VK10.VK_FALSE);
                int result = KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(device, family, surface, supported);
                return VulkanSurfaceCompat.supportsPresentation(result, supported.get(0));
            });
            if (!present) ci.setReturnValue(false);
        }
    }
}
