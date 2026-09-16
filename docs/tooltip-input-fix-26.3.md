# Minecraft 26.3 tooltip and text editing fixes

Version: **26.3-3.13.0.8** (Fabric and NeoForge).

Rounded tooltips previously displayed their text without a background or border,
with a small flashing colored dot. Minecraft 26.3's
`DynamicGpuData.Transform.write()` serializes model view, texture matrix, color
modulator, then model offset. The tooltip shader still expected the earlier
order, interpreting border colors as dimensions. Both shader stages now share
the correct uniform declaration. `checkTooltipUniforms` compares that declaration
with bytes produced by Minecraft's actual serializer.

ModernUI text fields, including MusicHud TuneWeave's search field, rejected
Backspace, Delete, cursor movement and shortcuts when Num Lock or Caps Lock was
enabled. SDL includes lock state in every event; Core 3.13 compares the complete
modifier mask for these operations. The SDL bridge now forwards only pressed
Shift/Control/Alt/Super modifiers, matching the previous GLFW integration with
lock modifiers disabled. Character input still comes from SDL's text events.
`checkSdlInput` covers all four lock combinations and exercises Core's real
Backspace and shortcut handling, including deletion of an emoji.

## Verification (2026-09-16)

- Full Gradle build passed for both loaders; both regression checks passed.
- Packaged JARs ran with MusicHud TuneWeave 1.3.0-beta-3+26.3 in an isolated
  singleplayer world, using Java 25, Fabric Loader 0.19.5 / Fabric API
  0.160.5+26.3, and NeoForge 26.3.0.1-beta.
- Physical NVIDIA RTX A4000, driver 580.173.02, using a headless NVIDIA Xorg
  display. Application device logs confirm the physical GPU on each backend.
- Fabric/OpenGL, Fabric/Vulkan, NeoForge/OpenGL and NeoForge/Vulkan each passed
  27 actual search-field checks: Backspace, left movement plus Backspace, forward
  Delete, Shift selection plus deletion, Control word deletion, select-all
  deletion, and copy under all four lock states; Chinese/emoji paste and deletion
  were also checked. Copied field contents were compared with expected strings.
- Button and item tooltips were inspected in screenshots; backgrounds, rounded
  borders and text render correctly. Multiple frames were captured for each
  backend. All four clients exited with code 0.

Vulkan validation reported no errors during these UI operations. After opening
TuneWeave and then exiting the client, it reported unreleased image/image-view
objects (`VUID-vkDestroyDevice-device-05137`). A 3.13.0.7 control also reproduces
this shutdown issue; a loader-only control and a 3.13.0.7 run without opening
TuneWeave do not. This is an existing integration shutdown issue, not a clean
Vulkan-validation pass, and its ownership is not established by these controls.
The two reported tooltip/input regressions are fixed independently of it.
