# 26.2 走马灯修复回迁（历史记录）

本文保留当时 `26.2-3.13.0.9-marquee.1` 构建的范围与验证安排。下文的三个加载器、版本后缀和“只回迁 26.2”均指该历史构建，不适用于当前 1.21.9–1.21.11 回移分支。

当前分支的 Minecraft／加载器／渲染后端目标见 [README 回移说明](../README.md)，普通版与 MiSans 版的 JDK 21 构建和配置说明见 [字体版本说明](font-editions.md)。当前每个分支构建 Fabric、NeoForge 两种加载器及两种字体版本，共四个最终 JAR；实际运行验收状态应以当前测试记录为准。

当前 `settings.gradle` 保留项目名 `common`，`common/build.gradle` 注册 `checkTextMarquee` 并将其加入 `check`，因此 `./gradlew :common:checkTextMarquee` 仍是有效检查入口。它是 CPU 回归检查，不代表物理显卡上的渲染验收。

## 当时的构建说明

本构建为 `26.2-3.13.0.9-marquee.1`。26.3 构建的用户实际使用反馈无异常后，
将 `4eeb28301b3786d5049145e3775dc05bf96483ba` 的文字滚动修复回迁到 26.2。
保留 26.2 已有的 Fabric、Forge、NeoForge 三个加载器及现有 MiSans 定制。

## 修复范围

- Minecraft GUI 的字形布局先固定取整，再应用整段文字的浮点位移。
- 对分数像素位置的轮廓字体局部启用线性采样；像素对齐时沿用原有采样器。
- 利用字体图集中已有的透明边框扩展绘制范围，避免滚动时字形边缘被几何边界截断；
  同步扩大准备阶段的包围盒。
- MUI Canvas／TextView 的字形烘焙与缓存不再随仿射平移变化，实际绘制保留完整位移。
- 保留原有 SDF 选择，以及位图字体、emoji、自定义图标的纹理路径。

沿用 26.2 的 Blaze3D 类型与运行环境，没有把 26.3 的 RenderPearl／SDL 适配一起搬回。
修复在 ModernUI 内完成，无需更改 MusicHud 的绘制调用。

## 检查与验证

`./gradlew :common:checkTextMarquee` 检查实际顶点的连续小步移动、字间距、阴影、
采样相位、UV 留白，以及 Arc3D 缓存复用、变换、Mixin 注入点和三个加载器的注册。
Actions 的 `build` 会运行该检查并构建三个加载器的 JAR；分支推送不发布正式 Release。

26.3 的用户验证不能替代 26.2 的实机验收。使用 Actions 的
`modernui-mc-release-jars` artifact 中对应加载器的 JAR，在原 MusicHud 配置下
检查小字号、低速起步、循环接缝，以及文字模糊／亮度变化、图标和裁剪边缘。
进行图形验收时使用物理显卡，并记录 OpenGL／Vulkan 后端及 GUI 缩放。

本轮只回迁 26.2；更早版本与 MiSans 拆分不在此构建中。
