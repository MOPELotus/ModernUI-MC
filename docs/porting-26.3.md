# Minecraft 26.3 adaptation

The target is the **26.3 release**, with ModernUI pages verified in actual
Minecraft clients using both OpenGL and native Vulkan. Compilation alone does
not satisfy this gate. Both Fabric and NeoForge are included.

## Verified inputs (2026-09-15 to 2026-09-16, Asia/Shanghai)

- Mojang version manifest lists `26.3`, type `release`, released at
  `2026-09-15T11:23:02Z`.
- Official version metadata:
  <https://piston-meta.mojang.com/v1/packages/96c00d95a31328714d3811cfade2804bb050e455/26.3.json>
- Client JAR SHA-1: `e877b6a07acd633fb3bb475002175cec036e7b87` (verified).
  Java 25; LWJGL 3.4.3.
- Fabric's version-specific loader endpoint lists `0.19.5`:
  <https://meta.fabricmc.net/v2/versions/loader/26.3>.
- Fabric API `0.160.5+26.3` declares Minecraft `~26.3-` in its actual JAR.
  <https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml>
- Mod Menu `21.0.0-beta.1` declares Minecraft `>=26.3-` in its actual JAR.
  <https://maven.terraformersmc.com/releases/com/terraformersmc/modmenu/maven-metadata.xml>
- NeoForge `26.3.0.1-beta` became available after the initial Fabric checkpoint.
  The 2026-09-16 07:00 CST monitor and a fresh metadata check confirm it:
  <https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml>.
  Official installer, userdev and source JARs were downloaded. Userdev SHA-256:
  `5221335091bc52998c4fb11cc8307cf15f806855b6df49bc655504e38dd532d3`.
  NeoForm applied the actual 26.3 patches; FML is 12.0.0.

## Changes confirmed from the release bytecode

Sources were decompiled from the hash-verified official JAR with Vineflower
1.12.0. No mappings or inferred class names are used.

- GPU types moved from Blaze3D to `com.mojang.renderpearl`.
  `GpuDevice` and `GpuTexture` are now interfaces; backend storage belongs to
  `FrontendGpuDevice` and texture fields to `BaseGpuTexture`.
- `VulkanRenderPass` binds sampled textures with `VK_IMAGE_LAYOUT_GENERAL`.
- Window/input use SDL. Minecraft's `KeyEvent.key()` is a physical scancode;
  `keycode()` is the layout-mapped SDL keycode. Modifier bits and mouse button
  numbers differ from ModernUI Core 3.13's representation.
- Text input now has an owner. Fullscreen changes use Options and Window's
  `updateFullscreenIfChanged`. Cursor changes use Minecraft's CursorTypes.
- ModernUI Core 3.13 still uses GLFW for initialization, clocks, clipboard,
  mapped characters and standard cursor creation. Minecraft-specific mixins
  bridge these to SDL. The GLFW Java API is bundled for Core's class signatures;
  GLFW natives are not required by this integration.
- Vanilla Minecraft no longer provides TinyFD. ModernUI retains its native
  dialog dependency and bundles LWJGL 3.4.3 TinyFD and platform libraries.
  NeoForge also ships TinyFD; the tested launcher uses its normal dependency
  resolution. Opening native dialogs remains outside the tested scope.
- GUI extraction moved to `Gui.extractRenderState`; ModernUI's overlay hook
  remains before toast extraction. Render and command submission hooks follow
  the new `GameRenderer.render()` and `CommandEncoder.submit()` calls.
- Tooltip extraction carries an explicit first-line spacing flag. Both the
  wrapped text path and the custom tooltip renderer preserve that flag.
- Arc3D receives the Vulkan features enabled on Minecraft's logical device,
  with the API version limited to Minecraft's requested Vulkan 1.2. Arc3D
  2026.2 derives its shader target from the physical device despite this limit,
  so the integration explicitly selects SPIR-V 1.5 before creating pipelines.
- Shader includes and input/output locations follow the release's shader
  compiler and vanilla vertex shaders. Texture sampler bindings use the new
  RenderPearl uniform declaration.
- UI fragment teardown tolerates Minecraft removing the last screen during
  shutdown. Arc3D resources and its allocator are released after Minecraft's
  Vulkan command encoder has drained deferred references, before VkDevice dies.

## Build and runtime validation

- Branch: `port/minecraft-26.3`; starting commit `0295ab8a`.
- Default configured platforms: Fabric and NeoForge; Minecraft 26.3 and
  NeoForge 26.3.0.1-beta are pinned in `gradle.properties`.
- Common, Fabric and NeoForge sources compile against 26.3. The full configured
  `build` passed on 2026-09-16, including `:common:checkSdlInput`.
- `SdlInputCheck` passed when compiled against the actual 26.3 and Core 3.13
  JARs. It is also wired into Gradle `check` as `:common:checkSdlInput`.
- Bytecode audits found no missing selectors across 43 Fabric and 42 NeoForge mixin targets,
  including required invocation/field injection points. Runtime tests below
  also exercised actual mixin application.
- Development clients: OpenGL and native Vulkan rendered the ModernUI Center
  and preferences. SDL input, clipboard, scrolling and resizing were exercised.
  Vulkan basic validation passed through shutdown; a separate test-only mod
  compiled all six ModernUI text/tooltip pipeline declarations successfully.
- Packaged Fabric JAR: OpenGL and native Vulkan passed in an isolated game directory with only
  Fabric API, Mod Menu and the universal JAR, without development classpaths.
  Home, preferences and font pages, typing, copy/paste, resize/reopen and
  shutdown passed on both backends. Vulkan ran with basic validation enabled;
  no Vulkan validation, shader, missing-native or UI-thread errors were found.
- Packaged NeoForge JAR: the official installer successfully created an isolated
  installation, with only the ModernUI universal JAR in `mods`. Home, preferences,
  font pages, typing, clipboard, resize/reopen and active-page shutdown passed
  on both OpenGL and native Vulkan. Both processes exited 0; basic Vulkan
  validation reported no errors.
- NeoForge development startup also initializes Core and the UI thread. Its
  shaded Core/Arc3D dependencies are grouped into Loom's `main` mod so FML
  transforms Core's SDL bridge mixins in development as well as production.
- Closing the first-run font prompt originally reopened its destroyed fragment
  during Minecraft's final disconnect frame, leaving an acquired surface.
  This was reproduced twice in the old NeoForge package. `MiSansSetup.tick`
  now skips opening the prompt after `Minecraft.isRunning()` becomes false.
  First-run prompt exit regression passed on Fabric and NeoForge, under both
  OpenGL and Vulkan (all four exited 0; no font download or license acceptance
  was performed). Results are recorded in the delivery README/logs.

## NeoForge API changes

The implementation follows the official 26.3.0.1-beta and FML 12 source JARs:

- Key bindings use `InputConstants.Type.KEYBOARD`, and input dispatch uses SDL
  constants. The tooltip hook accepts the new first-line spacing boolean before
  `ItemStack`; the NeoForge event does not expose this flag itself.
- The post-processing entry point is `setSpectatedEntityPostEffect`.
- NeoForge 26.3.0.1's `ScrollPanel.mouseClicked` still compares button 0; the
  scoped redirect adapts its left-button comparison to SDL's button 1.
- The existing wide logo uses `bannerFile`, replacing deprecated `logoFile`.

## Runtime coverage and limits

Tests use the official 26.3 client, Fabric Loader 0.19.5, Fabric API
0.160.5+26.3, Mod Menu 21.0.0-beta.1, or NeoForge 26.3.0.1-beta, with Java 25
on Linux x86-64. The display is
Xvfb; Mesa 25.2.8 llvmpipe provides OpenGL 4.5 through EGL and native Vulkan
1.4.318. Minecraft requests Vulkan 1.2. This exercises real game backends using
CPU drivers, not discrete GPU hardware or Windows/macOS drivers.

OpenGL used `SDL_VIDEO_FORCE_EGL=1` because this Xvfb environment lacks the
sRGB GLX visual required by Minecraft. Vulkan used `--vulkanValidation` with
Khronos validation 1.3.275 and the lavapipe ICD. No external Vulkan renderer mod
was installed.

**Additional synchronization validation is not a passing result.** Enabling
`VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT` reports a
swapchain `SYNC-HAZARD-WRITE-AFTER-READ` in the first vanilla startup frame,
followed by presentation errors. The same failure reproduces with the
hash-verified official client plus Fabric Loader alone, without ModernUI,
Fabric API or Mod Menu. The control identifies a limitation of this Minecraft/
driver/validation-layer combination; it does not establish the upstream cause.
Basic Vulkan validation is a separate, successful test.

One development screenshot briefly lacked the ModernUI layer during an early
clipboard sequence. The UI thread remained responsive; subsequent stepwise
copy/paste and resize/reopen tests rendered correctly. Preserve that observation
for future hardware testing rather than treating it as a diagnosed clipboard bug.

Offline test-account authentication/Realms errors and no-audio-device warnings
are expected in this fixture. Full world/gameplay, IME, TinyFD dialogs,
exclusive fullscreen and hardware-specific behavior are outside this page
rendering smoke test.

Use JDK 25 and `./gradlew build`. While diagnosing intermittent Maven TLS
failures, the development machine used a temporary local Maven cache populated
from official downloaded artifacts. This workaround is not a repository
dependency or a substitute for compilation/runtime tests.

## Hourly monitoring

The user-selected agent is `gpt-5.6-luna`, reasoning `low`.
The existing user service/timer is `modernui-loader-monitor.service` /
`modernui-loader-monitor.timer`, scheduled hourly on the hour. Working state is
under `build/loader-monitor/`; check `systemctl --user` for live status.
Network/parse failures must be recorded as unknown and retried, never as proof
that a loader is unpublished.

The hourly timer is preserved. The Luna agent performs bounded verification
when invoked; it is not a continuously executing model between timer runs.
The timer performs the unattended HTTP probes. Loader publication was the
previous blocker and is now resolved.
