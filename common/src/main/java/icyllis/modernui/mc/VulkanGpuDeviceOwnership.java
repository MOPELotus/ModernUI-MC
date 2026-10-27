package icyllis.modernui.mc;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/** Java resource owners left alive by Minecraft when its graphics device closes. */
public final class VulkanGpuDeviceOwnership {
    private record Owned(Object device, Runnable close) {}
    private static final Map<Object, Owned> OWNED = new IdentityHashMap<>();

    private VulkanGpuDeviceOwnership() {}

    public static synchronized void track(Object device, Object resource, Runnable close) {
        OWNED.putIfAbsent(resource, new Owned(device, close));
    }

    public static synchronized void closed(Object resource) {
        OWNED.remove(resource);
    }

    public static void closeRemaining(Object device) {
        var remaining = new ArrayList<Owned>();
        synchronized (VulkanGpuDeviceOwnership.class) {
            var iterator = OWNED.values().iterator();
            while (iterator.hasNext()) {
                var owned = iterator.next();
                if (owned.device == device) {
                    remaining.add(owned);
                    iterator.remove();
                }
            }
        }
        // Use each wrapper's normal close path, including its closed flag and
        // deferred release. Never destroy native handles or borrowed images.
        RuntimeException failure = null;
        for (var owned : remaining) {
            try {
                owned.close.run();
            } catch (RuntimeException ex) {
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
    }
}
