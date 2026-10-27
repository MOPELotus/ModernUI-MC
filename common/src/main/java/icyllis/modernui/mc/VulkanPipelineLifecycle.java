package icyllis.modernui.mc;

import java.util.List;
import java.util.function.Consumer;

/** Completes registered Java owners after VulkanMod has drained its normal shutdown work. */
public final class VulkanPipelineLifecycle {
    private VulkanPipelineLifecycle() {}

    public static <T> void closeRemaining(List<T> registered, Consumer<T> close) {
        // cleanUp removes itself from this list. Earlier frame operations may
        // already have removed owners, so visit only the remaining snapshot.
        for (T owner : List.copyOf(registered)) {
            if (registered.contains(owner)) close.accept(owner);
        }
    }
}
