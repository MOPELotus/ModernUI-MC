package icyllis.modernui.mc;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Downloads only on explicit consent. ZIP paths are never used as output paths. */
public final class MiSansInstaller {
    public static final String LICENSE_PAGE = "https://hyperos.mi.com/font/zh/download/";
    private static final List<String> PACKS = List.of("MiSans", "MiSans_Latin", "MiSans_TC", "MiSans_L3");
    public static List<String> expected() {
        var names = new ArrayList<String>();
        for (String family : List.of("MiSans", "MiSans Latin", "MiSans TC"))
            for (int weight = 100; weight <= 900; weight += 100)
                names.add(FontDefaults.createWeightedFontFileNames(family, weight).getFirst());
        names.add("MiSans L3.ttf");
        return names;
    }
    public static List<String> missing(Path root) {
        var missing = new ArrayList<>(expected());
        if (!Files.isDirectory(root)) return missing;
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                if (valid(p)) missing.removeIf(n -> n.equalsIgnoreCase(p.getFileName().toString()));
            });
        } catch (IOException ignored) {}
        return missing;
    }
    private static boolean valid(Path path) {
        try (var in = new RandomAccessFile(path.toFile(), "r")) {
            long size = in.length();
            if (size < 1024 || in.readInt() != 0x00010000) return false;
            int tables = in.readUnsignedShort();
            if (tables == 0 || 12L + tables * 16L > size) return false;
            in.seek(12);
            for (int i = 0; i < tables; i++) {
                in.readInt();
                in.readInt();
                long offset = Integer.toUnsignedLong(in.readInt());
                long length = Integer.toUnsignedLong(in.readInt());
                if (offset + length > size) return false;
            }
            return true;
        } catch (IOException e) { return false; }
    }
    public static void extract(Path archive, Path destination) throws IOException {
        Set<String> wanted = new HashSet<>(expected());
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || name.startsWith("__MACOSX/")) continue;
                String base = name.substring(name.lastIndexOf('/') + 1);
                if (!wanted.contains(base)) continue;
                Path target = destination.resolve(base);
                try (var input = zip.getInputStream(entry); var output = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[65536];
                    long total = 0;
                    for (int count; (count = input.read(buffer)) != -1;) {
                        total += count;
                        if (total > 128L * 1024 * 1024) throw new IOException("字体文件过大");
                        output.write(buffer, 0, count);
                    }
                }
                if (!valid(target)) throw new IOException("无效字体：" + base);
            }
        }
    }
    public static void install(Path root, Consumer<String> progress) throws IOException {
        Files.createDirectories(root);
        Path staging = Files.createTempDirectory(root.getParent(), ".misans-install-");
        try {
            Path fonts = Files.createDirectory(staging.resolve("fonts"));
            for (int i = 0; i < PACKS.size(); i++) {
                String pack = PACKS.get(i);
                String prefix = "(" + (i + 1) + "/4) " + pack;
                Path zip = staging.resolve(pack + ".zip");
                download("https://hyperos.mi.com/font-download/" + pack + ".zip", zip,
                        text -> progress.accept(prefix + " " + text), 512L * 1024 * 1024);
                progress.accept(prefix + " 校验并解压…");
                extract(zip, fonts);
                Files.delete(zip);
            }
            var missing = missing(fonts);
            if (!missing.isEmpty()) throw new IOException("官方包缺少字体：" + missing);
            download("https://hyperos.mi.com/font-download/" +
                    URLEncoder.encode("MiSans字体知识产权许可协议", java.nio.charset.StandardCharsets.UTF_8) + ".pdf",
                    staging.resolve("MiSans-License.pdf"), text -> progress.accept("下载许可协议 " + text), 16 * 1024 * 1024);
            for (String name : expected())
                Files.move(fonts.resolve(name), root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            Files.move(staging.resolve("MiSans-License.pdf"), root.resolve("MiSans-License.pdf"), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(root.resolve("MiSans-download.txt"),
                    "MiSans © Xiaomi. Downloaded from https://hyperos.mi.com/font-download/\n" +
                    "User clicked Download to accept the MiSans Font License Agreement.\n" +
                    java.time.Instant.now() + "\n");
        } finally {
            try (var paths = Files.walk(staging)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }
    private static void download(String url, Path target, Consumer<String> progress, long max) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            long length = connection.getContentLengthLong();
            if (length > max) throw new IOException("下载文件过大");
            try (var input = connection.getInputStream(); var output = Files.newOutputStream(target)) {
                byte[] buffer = new byte[65536];
                long received = 0, last = 0;
                for (int count; (count = input.read(buffer)) != -1;) {
                    received += count;
                    if (received > max) throw new IOException("下载文件过大");
                    output.write(buffer, 0, count);
                    if (System.nanoTime() - last > 250_000_000L) {
                        progress.accept("已下载 " + received / 1048576 + " MB" +
                                (length > 0 ? " / " + length / 1048576 + " MB" : ""));
                        last = System.nanoTime();
                    }
                }
                if (length >= 0 && received != length) throw new IOException("下载不完整");
            }
        } finally { connection.disconnect(); }
    }
}
