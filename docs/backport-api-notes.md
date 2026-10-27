# 1.21.9–1.21.11 反向移植 API 核对说明

本说明记录从 26.2 分支移植时需要分别处理的接口，以及仓库中可重复运行的检查。自动检查覆盖参数、字节布局与部分渲染准备逻辑；页面样式、交互和 OpenGL/Vulkan 的物理显卡渲染效果仍需运行客户端验收。

## 逐版本差异

| 接口 | 1.21.9 / 1.21.10 | 1.21.11 |
| --- | --- | --- |
| 资源标识符 | `net.minecraft.resources.ResourceLocation` | `net.minecraft.resources.Identifier` |
| 工具类 | `net.minecraft.Util` | `net.minecraft.util.Util` |
| `DynamicUniforms.Transform` 构造参数 | `Matrix4fc, Vector4fc, Vector3fc, Matrix4fc, float lineWidth` | 删除末尾 `lineWidth` |
| `TRANSFORM_UBO_SIZE` | 164 字节 | 160 字节 |
| GUI 纹理采样 | `TextureSetup` 只带纹理视图，过滤状态存于纹理 | `TextureSetup` 可带独立 sampler，按文字 run 选择 |

9 与 10 的相关文字渲染、输入 record 和 uniform 接口保持一致，但仍分别构建并运行各自检查。不能据此跳过其它 API、加载器或客户端验收。

## Tooltip 参数与 uniform

三版本原版 `GuiGraphics.renderTooltip` 的参数顺序均为 `Font, List<ClientTooltipComponent>, int x, int y, ClientTooltipPositioner, 资源标识符`。NeoForge 增加的重载在末尾追加 `ItemStack`。Fabric 在创建延迟 tooltip 时保存物品栈，实际绘制时消费该快照。

`TooltipRenderer` 调用本版本的 `writeTransform`：前三个向量/矩阵参数依次携带局部矩阵、尺寸和边框参数、阴影参数，第四个矩阵携带四列边框颜色。着色器 `ModernTooltip` 的 std140 字节顺序如下：

| 偏移（字节） | 着色器字段 | 内容 |
| --- | --- | --- |
| 0–63 | `u_LocalMat` (`mat4`) | 局部坐标矩阵，按矩阵列序写入 |
| 64–79 | `u_PushData0` (`vec4`) | 半宽、半高、圆角半径、边框半宽 |
| 80–91 | `u_PushData1` (`vec3`) | 阴影透明度、扩散系数、背景透明度 |
| 92–95 | 对齐空隙 | 下一项按 16 字节对齐 |
| 96–159 | `u_PushData2` 至 `u_PushData5` (`vec4` × 4) | 颜色矩阵的四列；最后一个分量用于彩虹偏移 |
| 160–163 | 仅 9/10 的 `lineWidth` | 原版 serializer 的额外末尾字段，不属于 `ModernTooltip` 声明 |

`MixinGuiRenderer` 在 `executeDrawRange` 中 Redirect `RenderSystem.bindDefaultUniforms(RenderPass)`，先保留原版绑定，再将当前 tooltip 的真实 `GpuBufferSlice` 绑定到 `ModernTooltip`。这个绑定入口直接取得 `RenderPass`，不依赖绘制方法内部局部变量的槽位或捕获顺序。它与 Fabric 容器 tooltip 的物品栈捕获是两个独立入口。

`checkTooltipUniform` 使用运行时 Minecraft 的真实 `DynamicUniforms.Transform.write`，逐字段比对顶点/片元着色器声明及字节值，同时核验 9/10 的末尾 `lineWidth`。

## 输入边界

三版本均按以下真实构造参数使用输入事件：

- `KeyEvent(key, scancode, modifiers)`。
- `CharacterEvent(codepoint, modifiers)`，保留完整 Unicode codepoint。
- `MouseButtonInfo(button, modifiers)`；`MouseButtonEvent(x, y, buttonInfo)`。

`UIManager.normalizeModifiers` 只保留 Shift、Control、Alt、Super，即 `mods & 0x0f`。Caps Lock、Num Lock 的 GLFW 状态位不传入 Core 的快捷键精确匹配。该转换接入按键按下、释放和鼠标按钮分发。

`checkGlfwInput` 用不同数值验证实际 record 字段顺序，检查所有快捷键组合与锁定键组合，并实际运行 Core 的 Backspace 编辑及 Ctrl 快捷键判断；同时核对编译后的输入分发确实调用了转换入口。

## 走马灯与文字采样

9/10 没有每次 GUI draw 独立指定 sampler 的接口。`ModernFontAtlas` 对共享 A8 字形图集使用稳定的 LINEAR min/mag 过滤，避免相邻文字 run 改写共享纹理状态，让分数像素移动保持连续。

11 通过独立 sampler 按 run 选择过滤。`TextSampling.needsLinear` 根据 GUI 缩放后的物理像素位置、缩放和旋转判断是否需要线性采样，不能只看 GUI 坐标是否为整数。`checkTextMarquee` 覆盖实际输出顶点的连续移动、采样判断，以及 Canvas 的文字原点与缓存复用接入。

## 检查入口

在对应版本分支执行：

```sh
./gradlew :common:check
./gradlew :ModernUI-Fabric:build :ModernUI-NeoForge:build
```

定位问题时可单独运行：

| Gradle 任务（均在 `:common:` 下） | 检查内容 |
| --- | --- |
| `checkTooltipUniform` | 本版本真实 uniform serializer 与两阶段着色器布局 |
| `checkGlfwInput` | 事件参数、锁定键屏蔽、Core 编辑和分发接入 |
| `checkTextMarquee` | 文字顶点移动、采样与 Canvas 缓存接入 |
| `checkClampingScrollView` | 限宽容器在不同测量模式下的尺寸及内容测量 |
| `checkPackMetadata` | 真实客户端/服务端 pack codec，以及当前游戏格式是否在声明范围 |
| `checkSpirvArrayLayout` | 实际着色器生成与 SPIR-V 数组布局修复 |
| `checkVulkanDepthBarrier` | Vulkan 深度同步修复与目标 VulkanMod 字节码入口 |
| `checkVulkanSurface` | Vulkan present 支持判定与目标 VulkanMod 字节码入口 |
| `checkFontVariant` | 标准版与 MiSans 版默认配置及编译边界 |

这些任务通过不代表物理显卡、窗口、tooltip 显示或输入体验已经验收通过。
