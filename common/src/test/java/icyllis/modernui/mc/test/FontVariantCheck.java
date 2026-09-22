package icyllis.modernui.mc.test;

import icyllis.modernui.mc.FontDefaults;
import icyllis.modernui.mc.FontVariant;
import icyllis.modernui.mc.FontVariantConfig;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
