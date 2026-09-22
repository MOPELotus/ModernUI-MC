package icyllis.modernui.mc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Keeps each edition's client preferences independent and leaves the legacy file intact. */
public final class FontVariantConfig {
    private static boolean importedLegacy;

    private FontVariantConfig() {}

    public static synchronized Path prepare(Path directory) {
        Path target = directory.resolve(FontVariant.CLIENT_CONFIG_NAME);
        Path legacy = directory.resolve("client.toml");
        try {
            Files.createDirectories(directory);
            if (!Files.exists(target) && Files.isRegularFile(legacy)) {
                try {
                    Files.copy(legacy, target);
                    importedLegacy = true;
                } catch (FileAlreadyExistsException ignored) {
                    // Another client already created this edition's file. Never overwrite it.
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot prepare Modern UI " + FontVariant.NAME + " config", e);
        }
        return target;
    }

    public static synchronized boolean consumeLegacyImport() {
        boolean result = importedLegacy;
        importedLegacy = false;
        return result;
    }
}
