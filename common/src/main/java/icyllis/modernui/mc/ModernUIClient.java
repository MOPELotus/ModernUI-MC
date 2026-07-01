/*
 * Modern UI.
 * Copyright (C) 2019-2024 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.modernui.mc;

import icyllis.arc3d.engine.DriverBugWorkarounds;
import icyllis.modernui.ModernUI;
import icyllis.modernui.graphics.text.FontFamily;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.view.WindowManager;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static icyllis.modernui.mc.ModernUIMod.*;

public abstract class ModernUIClient extends ModernUI {

    private static volatile ModernUIClient sInstance;

    private static volatile boolean sBootstrap;

    private static Properties getBootstrapProperties() {
        if (!sBootstrap) {
            synchronized (ModernUIClient.class) {
                if (!sBootstrap) {
                    Path path = MuiPlatform.get().getBootstrapPath();
                    if (Files.exists(path)) {
                        try (var is = Files.newInputStream(path, StandardOpenOption.READ)) {
                            props.load(is);
                        } catch (IOException e) {
                            LOGGER.error(MARKER, "Failed to load bootstrap file", e);
                        }
                    } else {
                        try {
                            Files.createFile(path);
                        } catch (IOException e) {
                            LOGGER.error(MARKER, "Failed to create bootstrap file", e);
                        }
                    }
                    sBootstrap = true;
                }
            }
        }
        return props;
    }

    public static final String BOOTSTRAP_USE_STAGING_BUFFERS_IN_OPENGL = "arc3d_context_useStagingBuffers";
    public static final String BOOTSTRAP_ALLOW_SPIRV_IN_OPENGL = "arc3d_context_allowGLSPIRV";
    public static final String BOOTSTRAP_SKIP_GL_VERSION_PROMOTION = "modernui_mc_skipGLPromotion";

    public static volatile boolean sInventoryPause;
    public static volatile boolean sRemoveTelemetrySession;
    public static volatile float sFontScale = 1;
    public static volatile boolean sUseColorEmoji;
    public static volatile boolean sEmojiShortcodes = true;
    public static volatile String sFirstFontFamily;
    public static volatile List<? extends String> sFallbackFontFamilyList;
    // font dir/paths to register
    public static volatile List<? extends String> sFontRegistrationList;
    public static volatile int sFontWeight = FontDefaults.DEFAULT_FONT_WEIGHT;

    protected volatile Typeface mTypeface;
    protected volatile FontFamily mFirstFontFamily;

    protected ModernUIClient() {
        super();
        sInstance = this;
    }

    @Nonnull
    public static ModernUIClient getInstance() {
        if (sInstance == null)
            throw new IllegalStateException("ModernUI mod client was never initialized. " +
                    "Please check whether mod loader threw an exception before.");
        return sInstance;
    }

    public static boolean areShadersEnabled() {
        if (ModernUIMod.isOptiFineLoaded()) {
            if (OptiFineIntegration.isShaderPackLoaded()) {
                return true;
            }
        }
        if (ModernUIMod.isIrisApiLoaded()) {
            return IrisApiIntegration.isShaderPackInUse();
        }
        return false;
    }

    public static String getBootstrapProperty(String key) {
        Properties props = getBootstrapProperties();
        if (props != null) {
            return props.getProperty(key);
        }
        return null;
    }

    public static void setBootstrapProperty(String key, String value) {
        Properties props = getBootstrapProperties();
        if (props != null) {
            props.setProperty(key, value);
            try (var os = Files.newOutputStream(MuiPlatform.get().getBootstrapPath(),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                props.store(os, "Modern UI bootstrap file");
            } catch (IOException e) {
                LOGGER.error(MARKER, "Failed to write bootstrap file", e);
            }
        }
    }

    @Nullable
    public static DriverBugWorkarounds getGpuDriverBugWorkarounds() {
        Properties props = getBootstrapProperties();
        if (props != null) {
            Map<String, Boolean> map = new HashMap<>();
            props.forEach((k, v) -> {
                if (k instanceof String key && v instanceof String value) {
                    Boolean state;
                    if ("true".equalsIgnoreCase(value) ||
                            "yes".equalsIgnoreCase(value) ||
                            "enable".equalsIgnoreCase(value)) {
                        state = Boolean.TRUE;
                    } else if ("false".equalsIgnoreCase(value) ||
                            "no".equalsIgnoreCase(value) ||
                            "disable".equalsIgnoreCase(value)) {
                        state = Boolean.FALSE;
                    } else {
                        return;
                    }
                    if (key.startsWith("arc3d_driverBugWorkarounds_")) { // <- length == 27
                        map.put(key.substring(27), state);
                    }
                }
            });
            if (!map.isEmpty()) {
                return new DriverBugWorkarounds(map);
            }
        }
        return null;
    }

    public static void loadFonts(String first,
                                 @Nonnull Collection<String> fallbacks,
                                 @Nonnull Set<FontFamily> selected,
                                 @Nonnull Consumer<FontFamily> firstSetter,
                                 boolean firstLoad) {
        if (firstLoad) {
            var tasks = new ArrayList<CompletableFuture<Void>>();
            var fontManager = FontResourceManager.getInstance();
            var registrationList = sFontRegistrationList;
            if (registrationList != null) {
                for (var value : new LinkedHashSet<>(registrationList)) {
                    for (File entry : collectFontRegistrationFiles(value)) {
                        tasks.add(CompletableFuture.runAsync(
                                () -> {
                                    try {
                                        FontFamily[] families = FontFamily.createFamilies(
                                                entry, /*register*/true);
                                        for (var f : families) {
                                            fontManager.onFontRegistered(f);
                                            LOGGER.info(MARKER, "Registered font '{}', path '{}'",
                                                    f.getFamilyName(), entry);
                                        }
                                    } catch (Exception e) {
                                        LOGGER.error(MARKER, "Failed to register font '{}'",
                                                entry, e);
                                    }
                                })
                        );
                    }
                }
            }
            var directory = Minecraft.getInstance().getResourcePackDirectory();
            try (var paths = Files.newDirectoryStream(directory)) {
                for (var p : paths) {
                    if (isFontFile(p)) {
                        Path absP = p.toAbsolutePath();
                        tasks.add(CompletableFuture.runAsync(
                                () -> {
                                    try {
                                        FontFamily[] families = FontFamily.createFamilies(
                                                absP.toFile(), /*register*/true);
                                        for (var f : families) {
                                            fontManager.onFontRegistered(f);
                                            LOGGER.info(MARKER, "Registered font '{}', path '{}'",
                                                    f.getFamilyName(), absP);
                                        }
                                    } catch (Exception e) {
                                        LOGGER.error(MARKER, "Failed to register font '{}'",
                                                absP, e);
                                    }
                                }
                        ));
                    }
                }
            } catch (IOException e) {
                LOGGER.error(MARKER, "Failed to open resource pack directory", e);
            }
            var resources = Minecraft.getInstance().getResourceManager();
            for (var entry : resources.listResourceStacks("font",
                    res -> {
                        if (res.getNamespace().equals(ModernUI.ID)) {
                            String p = res.getPath();
                            return p.endsWith(".ttf") ||
                                    p.endsWith(".otf") ||
                                    p.endsWith(".ttc") ||
                                    p.endsWith(".otc");
                        }
                        return false;
                    }).entrySet()) {
                for (var resource : entry.getValue()) {
                    tasks.add(CompletableFuture.runAsync(
                            () -> {
                                try (var inputStream = resource.open()) {
                                    FontFamily[] families = FontFamily.createFamilies(
                                            inputStream, /*register*/true);
                                    for (var f : families) {
                                        fontManager.onFontRegistered(f);
                                        LOGGER.info(MARKER, "Registered font '{}', location '{}' in pack: '{}'",
                                                f.getFamilyName(), entry.getKey(), resource.sourcePackId());
                                    }
                                } catch (Exception e) {
                                    LOGGER.error(MARKER, "Failed to register font '{}' in pack: '{}'",
                                            entry.getKey(), resource.sourcePackId(), e);
                                }
                            }
                    ));
                }
            }
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        }
        boolean success = loadSingleFont(first, selected, firstSetter);
        for (String fallback : fallbacks) {
            success &= loadSingleFont(fallback, selected, null);
        }
        if (!success && ModernUIMod.isDeveloperMode()) {
            LOGGER.debug(MARKER, "Available system font families:\n{}",
                    String.join("\n", FontFamily.getSystemFontMap().keySet()));
        }
    }

    private static boolean loadSingleFont(String value,
                                          @Nonnull Set<FontFamily> selected,
                                          @Nullable Consumer<FontFamily> firstSetter) {
        if (StringUtils.isEmpty(value)) {
            return true;
        }
        try {
            File f = resolveFontRegistrationPath(value).toFile();
            FontFamily family = FontFamily.createFamily(f, /*register*/false);
            selected.add(family);
            LOGGER.debug(MARKER, "Font '{}' was loaded with config value '{}' as LOCAL FILE",
                    family.getFamilyName(), value);
            if (firstSetter != null) {
                firstSetter.accept(family);
            }
            return true;
        } catch (Exception ignored) {
        }
        if (FontDefaults.isWeightControlledFontFamily(value)) {
            FontFamily family = loadWeightedMiSansFont(value);
            if (family != null) {
                selected.add(family);
                if (firstSetter != null) {
                    firstSetter.accept(family);
                }
                return true;
            }
            LOGGER.error(MARKER,
                    "Required MiSans font files for '{}' weight {} are missing. Put MiSans font files in '{}' " +
                            "or set fontRegistrationList to their directory/file. Expected one of: {}",
                    value, FontDefaults.normalizeFontWeight(sFontWeight),
                    FontDefaults.MODPACK_FONT_DIRECTORY,
                    String.join(", ", FontDefaults.createWeightedFontFileNames(value, sFontWeight)));
            return false;
        }
        FontFamily family = FontFamily.getSystemFontWithAlias(value);
        if (family == null) {
            Optional<FontFamily> optional = FontFamily.getSystemFontMap().values().stream()
                    .filter(f -> f.getFamilyName().equalsIgnoreCase(value))
                    .findFirst();
            if (optional.isPresent()) {
                family = optional.get();
            }
        }
        if (family != null) {
            selected.add(family);
            LOGGER.debug(MARKER, "Font '{}' was loaded with config value '{}' as SYSTEM FONT",
                    family.getFamilyName(), value);
            if (firstSetter != null) {
                firstSetter.accept(family);
            }
            return true;
        }
        if (FontDefaults.isRequiredFontFamily(value)) {
            LOGGER.error(MARKER,
                    "Required MiSans font family '{}' is missing. Put MiSans font files in '{}' " +
                            "or set fontRegistrationList to their directory/file.",
                    value, FontDefaults.MODPACK_FONT_DIRECTORY);
        } else {
            LOGGER.info(MARKER, "Font '{}' failed to load or invalid", value);
        }
        return false;
    }

    @Nullable
    private static FontFamily loadWeightedMiSansFont(@Nonnull String value) {
        List<String> fileNames = FontDefaults.createWeightedFontFileNames(value, sFontWeight);
        if (fileNames.isEmpty()) {
            return null;
        }
        File file = findRegisteredFontFile(fileNames);
        if (file == null) {
            return null;
        }
        try {
            FontFamily family = FontFamily.createFamily(file, /*register*/false);
            LOGGER.info(MARKER, "Loaded MiSans font '{}' for config value '{}' weight {} from '{}'",
                    family.getFamilyName(), value, FontDefaults.normalizeFontWeight(sFontWeight), file);
            return family;
        } catch (Exception e) {
            LOGGER.warn(MARKER, "Failed to load MiSans font file '{}' for config value '{}' weight {}",
                    file, value, FontDefaults.normalizeFontWeight(sFontWeight), e);
            return null;
        }
    }

    @Nullable
    private static File findRegisteredFontFile(@Nonnull List<String> fileNames) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        List<? extends String> registrationList = sFontRegistrationList;
        if (registrationList != null) {
            for (String value : new LinkedHashSet<>(registrationList)) {
                try {
                    Path path = resolveFontRegistrationPath(value);
                    if (Files.isDirectory(path)) {
                        roots.add(path);
                    } else if (Files.isRegularFile(path)) {
                        if (containsFileName(fileNames, path.getFileName())) {
                            return path.toFile();
                        }
                        Path parent = path.getParent();
                        if (parent != null) {
                            roots.add(parent);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (roots.isEmpty()) {
            roots.add(resolveFontRegistrationPath(FontDefaults.MODPACK_FONT_DIRECTORY));
        }
        for (String fileName : fileNames) {
            for (Path root : roots) {
                File file = findFontFile(root, fileName);
                if (file != null) {
                    return file;
                }
            }
        }
        return null;
    }

    private static boolean containsFileName(@Nonnull List<String> fileNames,
                                            @Nullable Path fileName) {
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        for (String expected : fileNames) {
            if (expected.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static File findFontFile(@Nonnull Path root,
                                     @Nonnull String fileName) {
        if (!Files.exists(root)) {
            return null;
        }
        if (Files.isRegularFile(root)) {
            return fileName.equalsIgnoreCase(root.getFileName().toString())
                    ? root.toFile()
                    : null;
        }
        Path direct = root.resolve(fileName);
        if (Files.isRegularFile(direct)) {
            return direct.toFile();
        }
        try (var paths = Files.walk(root)) {
            Optional<Path> match = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> fileName.equalsIgnoreCase(path.getFileName().toString()))
                    .findFirst();
            return match.map(Path::toFile).orElse(null);
        } catch (IOException e) {
            LOGGER.warn(MARKER, "Failed to scan font directory '{}' for '{}'", root, fileName, e);
            return null;
        }
    }

    @Nonnull
    private static List<File> collectFontRegistrationFiles(String value) {
        if (StringUtils.isEmpty(value)) {
            return List.of();
        }
        final Path path;
        try {
            path = resolveFontRegistrationPath(value);
        } catch (Exception e) {
            LOGGER.error(MARKER, "Font registration path '{}' is invalid", value, e);
            return List.of();
        }
        if (!Files.exists(path)) {
            LOGGER.warn(MARKER, "Font registration path '{}' does not exist", path);
            return List.of();
        }
        if (Files.isDirectory(path)) {
            try (var paths = Files.walk(path)) {
                return paths
                        .filter(Files::isRegularFile)
                        .filter(ModernUIClient::isFontFile)
                        .map(Path::toFile)
                        .toList();
            } catch (IOException e) {
                LOGGER.error(MARKER, "Failed to scan font registration directory '{}'", path, e);
                return List.of();
            }
        }
        if (isFontFile(path)) {
            return List.of(path.toFile());
        }
        LOGGER.warn(MARKER, "Font registration path '{}' is not a supported font file", path);
        return List.of();
    }

    @Nonnull
    private static Path resolveFontRegistrationPath(String value) {
        Path path = Path.of(value.replace('\\', '/'));
        if (!path.isAbsolute()) {
            path = Minecraft.getInstance().gameDirectory.toPath().resolve(path);
        }
        return path.normalize().toAbsolutePath();
    }

    private static boolean isFontFile(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ttf") ||
                name.endsWith(".otf") ||
                name.endsWith(".ttc") ||
                name.endsWith(".otc");
    }

    @Nonnull
    @Override
    protected Typeface onGetSelectedTypeface() {
        if (mTypeface != null) {
            return mTypeface;
        }
        return super.onGetSelectedTypeface();
    }

    // called from worker thread or resource reload thread
    // this must be called after config is loaded and resource packs are loaded
    // on Forge, config and resources are loaded together, so there's race
    // on NeoForge and Fabric, this is called by FontResourceManager
    public void loadTypeface() {
        if (sFontRegistrationList == null ||
                sFallbackFontFamilyList == null ||
                sFirstFontFamily == null) {
            // possible on Forge, there's race
            return;
        }
        synchronized (this) {
            if (mTypeface == null) {
                assert mFirstFontFamily == null;
                mTypeface = loadTypefaceInternal(this::setFirstFontFamily, true);
                LOGGER.info(MARKER, "Loaded typeface: {}", mTypeface);
            }
        }
    }

    // reload just Typeface on main thread, called after loaded
    public void reloadTypeface() {
        synchronized (this) {
            boolean firstLoad = mTypeface == null;
            mFirstFontFamily = null;
            mTypeface = loadTypefaceInternal(this::setFirstFontFamily, firstLoad);
            LOGGER.info(MARKER, "{} typeface: {}", firstLoad ? "Loaded" : "Reloaded", mTypeface);
        }
    }

    public void reloadFontStrike() {
        Minecraft.getInstance().schedule(
                () -> FontResourceManager.getInstance().reloadAll());
    }

    @Nonnull
    private static Typeface loadTypefaceInternal(
            @Nonnull Consumer<FontFamily> firstSetter,
            boolean firstLoad) {
        Set<FontFamily> families = new LinkedHashSet<>();
        if (sUseColorEmoji) {
            var emojiFont = FontResourceManager.getInstance().getEmojiFont();
            if (emojiFont != null) {
                var colorEmojiFamily = new FontFamily(
                        emojiFont
                );
                families.add(colorEmojiFamily);
            }
        }
        String first = sFirstFontFamily;
        List<? extends String> configs = sFallbackFontFamilyList;
        if (first != null || configs != null) {
            var fallbacks = new LinkedHashSet<String>();
            if (configs != null) {
                fallbacks.addAll(configs);
            }
            if (first != null) {
                fallbacks.remove(first);
            }
            loadFonts(first, fallbacks, families,
                    firstSetter, firstLoad);
        }
        return Typeface.createTypeface(families.toArray(new FontFamily[0]));
    }

    @Nullable
    public FontFamily getFirstFontFamily() {
        return mFirstFontFamily;
    }

    protected void setFirstFontFamily(FontFamily firstFontFamily) {
        mFirstFontFamily = firstFontFamily;
    }

    @Override
    public WindowManager getWindowManager() {
        return UIManager.getInstance().getDecorView();
    }
}
