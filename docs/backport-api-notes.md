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

## Vulkan 交换链同步入口

Fabric VulkanMod 的 acquire 调用位于 `beginFrame()`，NeoForge Reforged 位于 `beginFrame(int)`。`MixinVulkanRenderer` 使用 `beginFrame*` 显式选择所有重载，并要求唯一的实际 `vkAcquireNextImageKHR` 调用。Mixin 的裸方法名默认仅选择第一个重载，不能用扫描所有同名方法的测试代替真实选择规则。

兼容层按实际 acquire 成功或 SUBOPTIMAL 的 semaphore 信号跟踪等待状态，补齐 resize/flush 提交缺失的等待，避免重复消费二进制 semaphore；失败提交保留信号，同步对象销毁时清理旧生命周期。`checkVulkanAcquire` 同时检查状态机、真实 Mixin `SELECT_MEMBER` 规则和实际发行 JAR 的 acquire/submit/销毁入口，包含 Neo 裸方法名漏选的负对照。

## Neo 编译器类加载与 LWJGL ABI

NeoForge 将没有游戏库标记的 JarJar 依赖放入 FML Plugins 类加载器，该路径不应用游戏 Mixin。Arc 编译器现与其余 Arc 模块一起 shadow 到 Modern UI 主模组；成品检查核验 compiler 目标和三个 shader 资源位于顶层，拒绝嵌套重复副本及模块描述符污染。

Minecraft 使用的 LWJGL 3.3.3 没有当前 Arc/Core 字节码引用的部分新重载。兼容 Mixin 将 Byte/Int/LongBuffer 的释放及 ByteBuffer 基地址调用显式转到既有 `Buffer` 接口，保留分配器和 position 语义；Windows 窗口接口保留参数顺序，仅在原有错误输出参数为 null 时转到旧签名。`checkLwjglAbi` 检查真实消费者调用点，实际分配/释放原生缓冲区并验证基地址；Windows 边界只运行参数 stub 检查，不代表 Windows 实机验收。

NeoForge 1.21.11 的 Reforged 队列检查包括：`checkVulkanUploadBarrier` 核验 concurrent buffer 的队列族索引，`checkVulkanGlobalUniform` 核验 Global Settings UBO 稳定进入 graphics 上传路径的条件和帧重入保护。这两项在 `:ModernUI-NeoForge:` 下，仍需物理显卡验证实际队列与生命周期。

## Vulkan 退出与连续纹理写入

Reforged 0.3.0 的发行包没有调用 `Vulkan.cleanUp` 的入口。NeoForge 1.21.11 专用 Mixin 在真实 `Minecraft.close` 入口等待设备空闲，并在 Java `GpuDevice.close` 完成后、窗口销毁前调用原生清理；清理入口有一次性保护，防止重复执行原生设备清理。`checkNeoVulkanShutdown` 使用真实 Minecraft 和 Reforged 字节码检查这一顺序，并执行实际 handler 的重复关闭及 OpenGL 保护检查。运行验收还需确认真实 `vkDestroyDevice` 调用完成，不能仅看退出码或零校验错误。

面向 Minecraft 的 Mixin 仅调用本模组的 helper，可选 VulkanMod 符号留在 helper 内，避免 Mixin 预处理器在无 VulkanMod 时提前解析它们。该检查实际执行 Mixin 的成员引用预处理，在拒绝所有 VulkanMod 元数据的环境中验证成功，并以原始直接调用复现缺类异常。

桥接层在 UI 线程退出、两个 Arc context 引用归零后关闭自身 VulkanManager 的分配器和 feature 结构，并拒绝拥有外部 device 或 instance 的 manager。VulkanMod 的图形设备登记自身创建的 Java buffer/texture wrapper，正常 close 时移除；设备关闭时补完剩余 wrapper 和旧版 cloud buffer 的正常释放。管线清理仅遍历实际登记的剩余 Pipeline owner，放在原有延迟释放排空之后，避免重复销毁。

VkCommandEncoder 构造时将其 scratch framebuffer 的所有者登记到传入的实际设备；关闭时调用既有 `cleanUp(false)`，只释放 framebuffer/render pass，保留借用的纹理附件。

1.21.11 的两种后端另有不同的终结路径：Fabric VulkanMod 0.6.8 的 `DrawBuffers` 在构造时分配 section data uniform buffer，却未在 `free()` 中释放；专用 Mixin 只在终结 `free()` 时排入正常释放队列，不影响 `releaseBuffers()` 的区域复用。`checkVulkanDrawBuffersLifecycle` 使用单独配置取得真实 0.6.8 发行包，不以编译所用 0.6.6 API 代替运行时检查。

Reforged 的 texture close 使用延迟引用释放，尚有 view 时保留 image。Neo 专用 Mixin 将设备实际创建的 texture view 也纳入正常 close 的所有者登记，关闭后移除，并保护 view 的 framebuffer cache 不被重复关闭；借用 view 不会提前释放未关闭的 texture。`checkVulkanTextureViewOwnership` 执行实际 Reforged 的 view 与延迟引用字节码，覆盖 texture/view 关闭的正反顺序及内部别名。

相同 TRANSFER_DST 布局下的连续纹理上传仍然需要写后写依赖。兼容 Mixin 仅在重复上传布局时补 TRANSFER_WRITE 到 TRANSFER_WRITE 的屏障，保留图像、完整 mip/layer 范围及队列族 IGNORE 语义。以下检查运行真实目标 JAR 和产品 handler 的 CPU 边界验证，仍需物理显卡验证退出与首次上传。

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
| `checkVulkanAcquire` | acquire 信号所有权、真实 Mixin 重载选择和提交等待入口 |
| `checkLwjglAbi` | 真实 LWJGL 方法链接、原生缓冲区语义与窗口参数兼容 |
| `checkVulkanContextLifecycle` | 真实 manager 的桥接分配器所有权、活 context/线程与外部设备保护 |
| `checkVulkanGpuOwnership` | 真实设备创建/关闭入口、wrapper 隔离与重复关闭 |
| `checkVulkanPipelineLifecycle` | 实际管线 owner 登记、延迟清理后唯一释放 |
| `checkVulkanImageUploadBarrier` | 实际连续上传负对照、写屏障 native 字段和子资源范围 |
| `checkVulkanEncoderOwnership` | 真实 encoder 构造参数、scratch framebuffer 所有权及借用附件保护 |
| `checkFontVariant` | 标准版与 MiSans 版默认配置及编译边界 |

这些任务通过不代表物理显卡、窗口、tooltip 显示或输入体验已经验收通过。
