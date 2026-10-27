package icyllis.modernui.mc.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;

import java.nio.file.Files;
import java.nio.file.Path;

/** Verifies the mod pack declaration with Minecraft's resource and data pack codecs. */
public final class PackMetadataCheck {

    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of(System.getProperty("modernui.projectRoot"));
        Path metadataPath = projectRoot.resolve("neoforge/src/main/resources/pack.mcmeta");
        var metadata = JsonParser.parseString(Files.readString(metadataPath)).getAsJsonObject().get("pack");
        SharedConstants.tryDetectVersion();
        for (PackType packType : PackType.values()) {
            var range = PackFormat.packCodec(packType).codec().parse(JsonOps.INSTANCE, metadata)
                    .getOrThrow(message -> new AssertionError(packType + ": " + message));
            var currentFormat = SharedConstants.getCurrentVersion().packVersion(packType);
            if (!range.isValueInRange(currentFormat)) {
                throw new AssertionError(packType + " format " + currentFormat + " is excluded by " + range);
            }
            System.out.println(packType + " metadata accepted; " + currentFormat + " in " + range);
        }
    }
}
