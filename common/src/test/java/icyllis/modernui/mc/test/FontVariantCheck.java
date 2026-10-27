package icyllis.modernui.mc.test;

import icyllis.modernui.mc.FontDefaults;
import icyllis.modernui.mc.FontVariant;
import icyllis.modernui.mc.FontVariantConfig;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runs without a client or GPU; examines the actual compiled edition, not source text. */
public final class FontVariantCheck {
    public static void main(String[] args) throws Exception {
        boolean misans = FontVariant.MISANS;
        require(FontDefaults.FIRST_FONT_FAMILY.equals(misans ? "MiSans" : "Inter Frozen Medium"), "first font");
        require(FontDefaults.createFallbackFontFamilyList().equals(misans
                ? List.of("MiSans L3", "MiSans Latin", "MiSans TC")
                : List.of("Source Han Sans CN Medium", "Noto Sans", "Segoe UI Variable", "Segoe UI",
                    "San Francisco", "Open Sans", "SimHei", "STHeiti", "Segoe UI Symbol", "mui-i18n-compat")),
                "fallbacks");
        require(FontDefaults.createFontRegistrationList().equals(misans
                ? List.of("config/ModernUI/fonts") : List.of()), "registration");
        require(!FontDefaults.isMiSansFontFamily("Inter Frozen Medium"), "ordinary font must not be MiSans");
        require(FontDefaults.createWeightedFontFileNames("MiSans", 510).getFirst().equals("MiSans-Medium.ttf"),
                "MiSans weight mapping");
        checkOfficialMiSansWeightPresets();
        require(FontDefaults.isLegacyMiSansProfile("MiSans", List.of("MiSans L3", "MiSans Latin", "MiSans TC"),
                List.of("config/ModernUI/fonts")), "recognize old forced profile");
        require(!FontDefaults.isLegacyMiSansProfile("MiSans", List.of("My Custom Font"),
                List.of("config/ModernUI/fonts")), "preserve customized legacy fallbacks");
        require(!FontDefaults.isLegacyMiSansProfile("My Custom Font", List.of("MiSans L3", "MiSans Latin", "MiSans TC"),
                List.of("config/ModernUI/fonts")), "preserve customized legacy first font");

        Path root = Files.createTempDirectory("modernui-config-variants-");
        try {
            Path blank = root.resolve("blank");
            Path fresh = FontVariantConfig.prepare(blank);
            require(fresh.getFileName().toString().equals("client-" + FontVariant.NAME + ".toml"), "edition filename");
            require(!Files.exists(fresh) && !FontVariantConfig.consumeLegacyImport(), "fresh install uses defaults");
            Path legacy = blank.resolve("client.toml");
            String settings = "[font]\nfirstFontFamily = \"MiSans\"\n[screen]\nblurRadius = 13\n";
            Files.writeString(legacy, settings);
            Path other = blank.resolve(misans ? "client-standard.toml" : "client-misans.toml");
            Files.writeString(other, "other edition preferences");
            require(Files.readString(FontVariantConfig.prepare(blank)).equals(settings), "import all legacy settings");
            require(FontVariantConfig.consumeLegacyImport() && !FontVariantConfig.consumeLegacyImport(), "migrate once");
            Files.writeString(fresh, "custom preferences after first launch");
            FontVariantConfig.prepare(blank);
            require(!FontVariantConfig.consumeLegacyImport(), "existing edition must never migrate again");
            require(Files.readString(fresh).equals("custom preferences after first launch"), "preserve existing settings");
            require(Files.readString(legacy).equals(settings), "legacy file unchanged");
            require(Files.readString(other).equals("other edition preferences"), "other edition unchanged");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }

        ClassNode client = read("icyllis/modernui/mc/ModernUIClient");
        ClassNode tick = read("icyllis/modernui/mc/mixin/MixinMinecraft");
        require(calls(client, "icyllis/modernui/mc/MiSansSetup", "ensureInstalled") == misans, "installation entry point");
        require(calls(tick, "icyllis/modernui/mc/MiSansSetup", "tick") == misans, "installation screen entry point");
        require(calls(client, "icyllis/modernui/mc/ModernUIClient", "loadWeightedMiSansFont") == misans,
                "special weight loading only in MiSans");
        ClassNode preferences = read("icyllis/modernui/mc/ui/PreferencesFragment");
        List<Object> labels = new ArrayList<>();
        preferences.methods.stream().filter(m -> m.name.equals("createPage3")).forEach(m -> {
            for (var insn : m.instructions) if (insn instanceof LdcInsnNode ldc) labels.add(ldc.cst);
        });
        require(labels.contains("modernui.center.font.fontWeight") == misans, "weight setting visibility");
        require(labels.contains("modernui.center.font.fallbackFonts") != misans, "fallback setting visibility");
        require(labels.contains("modernui.center.font.fontRegistrationList") != misans, "registration setting visibility");
        require(calls(preferences, "icyllis/modernui/mc/ui/PreferredFontAccordion", "<init>") != misans,
                "preferred font picker visibility");
        System.out.println("PASS: " + FontVariant.NAME + " defaults, configuration migration and compiled font/UI boundaries");
    }

    private record OfficialFont(String fileName, String family, String style, int os2Weight,
                                String sha256, String sourceArchive) {}

    private static void checkOfficialMiSansWeightPresets() throws Exception {
        Map<String, OfficialFont> fonts = new LinkedHashMap<>();
        String resource = "icyllis/modernui/mc/test/misans-official-metadata.tsv";
        try (var input = FontVariantCheck.class.getClassLoader().getResourceAsStream(resource)) {
            require(input != null, "missing official MiSans metadata fixture");
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] fields = line.split("\t", -1);
                    require(fields.length == 6, "invalid official MiSans metadata row");
                    var font = new OfficialFont(fields[0], fields[1], fields[2], Integer.parseInt(fields[3]),
                            fields[4], fields[5]);
                    require(font.sha256.matches("[0-9a-f]{64}"), "official font SHA-256");
                    require(font.sourceArchive.startsWith("https://hyperos.mi.com/font-download/"),
                            "official font source archive");
                    require(fonts.put(font.fileName, font) == null, "duplicate official font metadata");
                }
            }
        }
        require(fonts.size() == 28, "four official packages contain 28 required static fonts");
        String[] styles = {"Thin", "ExtraLight", "Light", "Regular", "Medium", "Demibold", "Semibold", "Bold", "Heavy"};
        int[] os2Weights = {150, 200, 250, 330, 380, 450, 520, 630, 700};
        for (String family : List.of("MiSans", "MiSans Latin", "MiSans TC")) {
            List<OfficialFont> presets = new ArrayList<>();
            for (int i = 0; i < styles.length; i++) {
                String fileName = FontDefaults.createWeightedFontFileNames(family, (i + 1) * 100).getFirst();
                OfficialFont font = fonts.get(fileName);
                require(font != null, "configured preset must select an actual official file: " + fileName);
                require(font.family.equals(family), "official family identity: " + fileName);
                require(font.style.equals(styles[i]), "official style for preset " + ((i + 1) * 100));
                require(font.os2Weight == os2Weights[i], "official OS/2 weight: " + fileName);
                presets.add(font);
            }
            requireIncreasingWeights(presets);
            // The previous 600=Semibold, 700=Demibold mapping must fail against
            // the real metadata, independently of the preset's nominal numbers.
            List<OfficialFont> oldOrder = new ArrayList<>(presets);
            oldOrder.set(5, presets.get(6));
            oldOrder.set(6, presets.get(5));
            boolean rejected = false;
            try {
                requireIncreasingWeights(oldOrder);
            } catch (AssertionError expected) {
                rejected = true;
            }
            require(rejected, "old 600/700 reversed weight mapping must be rejected: " + family);
        }
        OfficialFont l3 = fonts.get("MiSans L3.ttf");
        require(l3 != null && l3.family.equals("MiSans L3") && l3.style.equals("Regular") && l3.os2Weight == 400,
                "L3 remains an independent regular fallback");
    }

    private static void requireIncreasingWeights(List<OfficialFont> presets) {
        int previous = 0;
        for (OfficialFont font : presets) {
            require(font.os2Weight > previous, "MiSans preset weights must increase: " + font.fileName);
            previous = font.os2Weight;
        }
    }

    private static ClassNode read(String name) throws Exception {
        try (var in = FontVariantCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (in == null) throw new AssertionError("Missing class " + name);
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }

    private static boolean calls(ClassNode node, String owner, String name) {
        return node.methods.stream().anyMatch(m -> {
            for (var insn : m.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
            }
            return false;
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
