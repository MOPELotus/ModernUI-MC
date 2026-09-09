package icyllis.modernui.mc.test;

import icyllis.modernui.mc.MiSansInstaller;
import java.nio.file.*;
import java.util.zip.*;

/** Explicit standalone integration check; never downloads in the ordinary build. */
public class MiSansInstallerCheck {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Files.createDirectories(root);
        if (MiSansInstaller.missing(root).size() != 28) throw new AssertionError("empty detection");
        MiSansInstaller.install(root, System.out::println);
        if (!MiSansInstaller.missing(root).isEmpty()) throw new AssertionError("incomplete install");
        for (String name : MiSansInstaller.expected()) {
            java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, root.resolve(name).toFile());
        }
        Path bad = root.resolve("MiSans-Regular.ttf");
        Files.writeString(bad, "truncated");
        if (!MiSansInstaller.missing(root).contains(bad.getFileName().toString()))
            throw new AssertionError("corruption not detected");
        Path zip = root.resolve("malicious.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("../../outside.txt"));
            out.write(new byte[]{1});
            out.closeEntry();
        }
        MiSansInstaller.extract(zip, root);
        if (Files.exists(root.getParent().getParent().resolve("outside.txt")))
            throw new AssertionError("ZIP traversal");
        System.out.println("PASS: official download, 28 fonts parsed, corruption and traversal checks");
    }
}
