package icyllis.modernui.mc;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.VK10.*;

/** Binary acquire signals owned by one VulkanMod Renderer and its sync objects. */
public final class VulkanAcquireToken {
    private final LinkedHashMap<Long, Boolean> tokens = new LinkedHashMap<>();

    public void acquired(int result, long semaphore) {
        // Failed acquire does not signal anything or consume an earlier signal.
        if ((result == VK_SUCCESS || result == VK_SUBOPTIMAL_KHR) && semaphore != 0) {
            if (Boolean.TRUE.equals(tokens.get(semaphore))) {
                throw new IllegalStateException("Vulkan acquire semaphore reused before its signal was consumed");
            }
            tokens.put(semaphore, true);
        }
    }

    /** The owner destroys/recreates these semaphores after waiting for its device. */
    public void syncObjectsDestroyed() {
        tokens.clear();
    }

    /** Returns null when the original wait list already consumes each signal exactly once. */
    public Waits waits(long[] semaphores, int[] stages) {
        if (semaphores.length != stages.length) throw new IllegalArgumentException("Mismatched semaphore/stage counts");
        var result = new ArrayList<Long>();
        var masks = new ArrayList<Integer>();
        var included = new java.util.HashSet<Long>();
        boolean changed = false;
        for (int i = 0; i < semaphores.length; i++) {
            Boolean pending = tokens.get(semaphores[i]);
            if (pending != null && (!pending || !included.add(semaphores[i]))) {
                changed = true;
                continue;
            }
            result.add(semaphores[i]);
            masks.add(stages[i]);
        }
        for (var token : tokens.entrySet()) {
            if (token.getValue() && !included.contains(token.getKey())) {
                result.add(token.getKey());
                // Resize can transition the acquired image before drawing.
                masks.add(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
                changed = true;
            }
        }
        if (!changed) return null;
        return new Waits(result.stream().mapToLong(Long::longValue).toArray(),
                masks.stream().mapToInt(Integer::intValue).toArray());
    }

    public void submitted(int result) {
        if (result == VK_SUCCESS) tokens.replaceAll((semaphore, pending) -> false);
    }

    public record Waits(long[] semaphores, int[] stages) {}
}
