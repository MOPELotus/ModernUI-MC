/*
 * Modern UI.
 * Copyright (C) 2026 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import icyllis.arc3d.core.RawPtr;
import icyllis.arc3d.engine.Swizzle;
import icyllis.arc3d.vulkan.VKUtil;
import icyllis.arc3d.vulkan.VulkanBackendContext;
import icyllis.arc3d.vulkan.VulkanImage;
import icyllis.arc3d.vulkan.VulkanImageDesc;
import icyllis.arc3d.vulkan.VulkanMemoryAllocator;
import icyllis.modernui.core.VulkanManager;
import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.LongBuffer;

@ApiStatus.Internal
public final class NativeVulkanIntegration {

    private static final Unsafe UNSAFE;
    private static final Field BACKEND_FIELD;

    private static final long GPU_TEXTURE_FORMAT;
    private static final long GPU_TEXTURE_WIDTH;
    private static final long GPU_TEXTURE_HEIGHT;
    private static final long GPU_TEXTURE_DEPTH_OR_LAYERS;
    private static final long GPU_TEXTURE_MIP_LEVELS;
    private static final long GPU_TEXTURE_USAGE;
    private static final long GPU_TEXTURE_LABEL;

    private static final long VULKAN_TEXTURE_DEVICE;
    private static final long VULKAN_TEXTURE_IMAGE;
    private static final long VULKAN_TEXTURE_ALLOCATION;
    private static final long VULKAN_TEXTURE_CLOSED;
    private static final long VULKAN_TEXTURE_VIEWS;

    private static final long VULKAN_VIEW_IMAGE_VIEW;

    static {
        try {
            Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
            unsafe.setAccessible(true);
            UNSAFE = (Unsafe) unsafe.get(null);

            BACKEND_FIELD = GpuDevice.class.getDeclaredField("backend");
            BACKEND_FIELD.setAccessible(true);

            GPU_TEXTURE_FORMAT = offset(GpuTexture.class, "format");
            GPU_TEXTURE_WIDTH = offset(GpuTexture.class, "width");
            GPU_TEXTURE_HEIGHT = offset(GpuTexture.class, "height");
            GPU_TEXTURE_DEPTH_OR_LAYERS = offset(GpuTexture.class, "depthOrLayers");
            GPU_TEXTURE_MIP_LEVELS = offset(GpuTexture.class, "mipLevels");
            GPU_TEXTURE_USAGE = offset(GpuTexture.class, "usage");
            GPU_TEXTURE_LABEL = offset(GpuTexture.class, "label");

            VULKAN_TEXTURE_DEVICE = offset(VulkanGpuTexture.class, "device");
            VULKAN_TEXTURE_IMAGE = offset(VulkanGpuTexture.class, "vkImage");
            VULKAN_TEXTURE_ALLOCATION = offset(VulkanGpuTexture.class, "vmaAllocation");
            VULKAN_TEXTURE_CLOSED = offset(VulkanGpuTexture.class, "closed");
            VULKAN_TEXTURE_VIEWS = offset(VulkanGpuTexture.class, "views");

            VULKAN_VIEW_IMAGE_VIEW = offset(VulkanGpuTextureView.class, "vkImageView");
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private NativeVulkanIntegration() {
    }

    private static long offset(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return UNSAFE.objectFieldOffset(field);
    }

    private static VulkanDevice device() {
        try {
            Object backend = BACKEND_FIELD.get(RenderSystem.getDevice());
            if (backend instanceof VulkanDevice device) {
                return device;
            }
            throw new IllegalStateException("Minecraft is not using the native Vulkan backend");
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to access Minecraft GPU backend", e);
        }
    }

    public static VulkanBackendContext wrapContext() {
        VulkanDevice device = device();
        var vkDevice = device.vkDevice();
        var physicalDevice = vkDevice.getPhysicalDevice();
        var instance = physicalDevice.getInstance();

        VkPhysicalDeviceFeatures2 features2 = VkPhysicalDeviceFeatures2.calloc().sType$Default();
        VK11.vkGetPhysicalDeviceFeatures2(physicalDevice, features2);

        int apiVersion;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(physicalDevice, properties);
            apiVersion = properties.apiVersion();
        }

        VulkanMemoryAllocator allocator = VulkanMemoryAllocator.make(
                instance, physicalDevice, vkDevice, apiVersion, 0);
        if (allocator == null) {
            throw new IllegalStateException("Failed to create Arc3D Vulkan memory allocator");
        }

        VulkanManager vulkanManager = VulkanManager.get();
        vulkanManager.setPhysicalDeviceFeatures2(features2);
        vulkanManager.setMemoryAllocator(allocator);

        VulkanBackendContext backendContext = new VulkanBackendContext();
        backendContext.mInstance = instance;
        backendContext.mPhysicalDevice = physicalDevice;
        backendContext.mDevice = vkDevice;
        backendContext.mQueue = device.graphicsQueue().vkQueue();
        backendContext.mGraphicsQueueIndex = device.graphicsQueue().queueFamilyIndex();
        backendContext.mMaxAPIVersion = apiVersion;
        backendContext.mDeviceFeatures2 = features2;
        backendContext.mMemoryAllocator = allocator;
        return backendContext;
    }

    public static void replaceMainImageViewWithSwizzle(GpuTextureView textureView, short swizzle) {
        if (!(textureView instanceof VulkanGpuTextureView vulkanView)) {
            throw new IllegalArgumentException("Expected native Vulkan texture view");
        }
        VulkanDevice device = device();
        VulkanGpuTexture texture = vulkanView.texture();
        long newView = createImageView(
                device,
                texture.vkImage(),
                texture.getFormat(),
                texture.usage(),
                texture.getDepthOrLayers(),
                vulkanView.baseMipLevel(),
                vulkanView.mipLevels(),
                swizzle
        );
        VK12.vkDestroyImageView(device.vkDevice(), vulkanView.vkImageView(), null);
        UNSAFE.putLong(vulkanView, VULKAN_VIEW_IMAGE_VIEW, newView);
    }

    private static long createImageView(VulkanDevice device, long image, GpuFormat format, int usage,
                                        int depthOrLayers, int baseMipLevel, int mipLevels, short swizzle) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(image)
                    .viewType((usage & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0
                            ? VK10.VK_IMAGE_VIEW_TYPE_CUBE
                            : VK10.VK_IMAGE_VIEW_TYPE_2D)
                    .format(VulkanConst.toVk(format));
            createInfo.components().set(
                    VKUtil.toVkComponentSwizzle(Swizzle.getR(swizzle)),
                    VKUtil.toVkComponentSwizzle(Swizzle.getG(swizzle)),
                    VKUtil.toVkComponentSwizzle(Swizzle.getB(swizzle)),
                    VKUtil.toVkComponentSwizzle(Swizzle.getA(swizzle))
            );
            createInfo.subresourceRange()
                    .aspectMask(VulkanConst.formatAspectMask(format))
                    .baseMipLevel(baseMipLevel)
                    .levelCount(mipLevels)
                    .baseArrayLayer(0)
                    .layerCount((usage & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0 ? 6 : depthOrLayers);

            LongBuffer pView = stack.mallocLong(1);
            VKUtil._CHECK_(VK12.vkCreateImageView(device.vkDevice(), createInfo, null, pView));
            return pView.get(0);
        }
    }

    // caller must track Arc3D CommandBuffer usage and Client usage ref
    // caller must NOT close the returned object
    public static GpuTexture wrapTextureImageFromArc3D(@RawPtr VulkanImage arc3dVulkanImage) {
        VulkanDevice device = device();
        VulkanImageDesc desc = arc3dVulkanImage.getVulkanDesc();
        GpuFormat format = gpuFormatFromVk(desc.mVkFormat);
        try {
            VulkanGpuTexture texture = (VulkanGpuTexture) UNSAFE.allocateInstance(VulkanGpuTexture.class);
            UNSAFE.putObject(texture, GPU_TEXTURE_FORMAT, format);
            UNSAFE.putInt(texture, GPU_TEXTURE_WIDTH, desc.getWidth());
            UNSAFE.putInt(texture, GPU_TEXTURE_HEIGHT, desc.getHeight());
            UNSAFE.putInt(texture, GPU_TEXTURE_DEPTH_OR_LAYERS, Math.max(1, desc.getLayerCount()));
            UNSAFE.putInt(texture, GPU_TEXTURE_MIP_LEVELS, Math.max(1, desc.getMipLevelCount()));
            UNSAFE.putInt(texture, GPU_TEXTURE_USAGE, GpuTexture.USAGE_TEXTURE_BINDING);
            UNSAFE.putObject(texture, GPU_TEXTURE_LABEL, arc3dVulkanImage.getLabel());

            UNSAFE.putObject(texture, VULKAN_TEXTURE_DEVICE, device);
            UNSAFE.putLong(texture, VULKAN_TEXTURE_IMAGE, arc3dVulkanImage.vkImage());
            UNSAFE.putLong(texture, VULKAN_TEXTURE_ALLOCATION, 0L);
            UNSAFE.putBoolean(texture, VULKAN_TEXTURE_CLOSED, false);
            UNSAFE.putInt(texture, VULKAN_TEXTURE_VIEWS, 0);
            return texture;
        } catch (InstantiationException e) {
            throw new IllegalStateException("Failed to wrap Arc3D Vulkan image", e);
        }
    }

    private static GpuFormat gpuFormatFromVk(int vkFormat) {
        for (GpuFormat format : GpuFormat.values()) {
            if (VulkanConst.toVk(format) == vkFormat) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported Vulkan image format: " + vkFormat);
    }

    public static void syncImageLayoutFromArc3D(GpuTexture vulkanTexture, @RawPtr VulkanImage arc3dVulkanImage) {
        int oldLayout = arc3dVulkanImage.getVulkanMutableState().getImageLayout();
        int newLayout = VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        if (oldLayout == newLayout) {
            return;
        }
        transitionImageLayout((VulkanGpuTexture) vulkanTexture, oldLayout, newLayout);
        arc3dVulkanImage.getVulkanMutableState().setImageLayout(newLayout);
    }

    public static void syncImageLayoutFromVulkan(GpuTexture vulkanTexture, @RawPtr VulkanImage arc3dVulkanImage) {
        arc3dVulkanImage.getVulkanMutableState().setImageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }

    private static void transitionImageLayout(VulkanGpuTexture texture, int oldLayout, int newLayout) {
        VulkanDevice device = device();
        VulkanCommandEncoder encoder = device.createCommandEncoder();
        var commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType$Default()
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcAccessMask(oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED
                            ? 0
                            : VK10.VK_ACCESS_MEMORY_WRITE_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT)
                    .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(texture.vkImage());
            barrier.subresourceRange()
                    .aspectMask(VulkanConst.formatAspectMask(texture.getFormat()))
                    .baseMipLevel(0)
                    .levelCount(texture.getMipLevels())
                    .baseArrayLayer(0)
                    .layerCount(texture.getDepthOrLayers());
            VK12.vkCmdPipelineBarrier(
                    commandBuffer,
                    VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0,
                    null,
                    null,
                    barrier
            );
        }
        VKUtil._CHECK_(VK12.vkEndCommandBuffer(commandBuffer));
        encoder.execute(commandBuffer);
    }

    public static boolean sameImage(GpuTexture vulkanTexture, @RawPtr VulkanImage arc3dVulkanImage) {
        return ((VulkanGpuTexture) vulkanTexture).vkImage() == arc3dVulkanImage.vkImage();
    }

    public static void addFrameOp(Runnable runnable) {
        device().createCommandEncoder().queueForDestroy(runnable::run);
    }
}
