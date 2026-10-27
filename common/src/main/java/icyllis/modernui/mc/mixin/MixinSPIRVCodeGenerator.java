package icyllis.modernui.mc.mixin;

import icyllis.arc3d.compiler.MemoryLayout;
import icyllis.arc3d.compiler.TargetApi;
import icyllis.arc3d.compiler.spirv.SPIRVCodeGenerator;
import icyllis.arc3d.compiler.tree.Type;
import icyllis.modernui.mc.SpirvLayoutCompat;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SPIRVCodeGenerator.class, remap = false)
public abstract class MixinSPIRVCodeGenerator {
    @Shadow @Final public TargetApi mOutputTarget;

    // Arc3D 2026.2 gives even function-local arrays a default std140 stride.
    // Vulkan forbids explicit array layout there. The second isSupported call
    // is only the no-layout fallback; keep the first check and explicit block
    // layouts intact. The emitter already keys array types by their stride,
    // so zero also separates local types from identically shaped block arrays.
    @Redirect(method = "writeType(Licyllis/arc3d/compiler/tree/Type;Licyllis/arc3d/compiler/tree/Modifiers;Licyllis/arc3d/compiler/MemoryLayout;)I",
            at = @At(value = "INVOKE", target = "Licyllis/arc3d/compiler/MemoryLayout;isSupported(Licyllis/arc3d/compiler/tree/Type;)Z",
                    ordinal = 1), require = 1)
    private boolean modernui$omitDefaultVulkanArrayLayout(MemoryLayout layout, Type type) {
        return !mOutputTarget.isVulkan() && layout.isSupported(type);
    }
    // Arc3D's lvalue emitter does not carry the block layout through whole-array
    // access/load/store operations. Reconnect these to their actual member types
    // and copy logical values across the layout boundary (SPIR-V 1.4+).
    @Inject(method = "generateCode()Ljava/nio/ByteBuffer;", at = @At("RETURN"),
            cancellable = true, require = 1)
    private void modernui$connectBlockArrayTypes(CallbackInfoReturnable<ByteBuffer> ci) {
        if (mOutputTarget.isVulkan() && ci.getReturnValue() != null) {
            ci.setReturnValue(SpirvLayoutCompat.repair(ci.getReturnValue()));
        }
    }
}
