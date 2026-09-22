# 普通版与 MiSans 版

两版都保留当前 Minecraft 移植、走马灯低速抖动、裁剪和输入修复。

| 版本 | 普通版 | MiSans 版 | 每次构建总数 |
| --- | --- | --- | --- |
| Minecraft 26.3 | Fabric、NeoForge | Fabric、NeoForge | 4 |
| Minecraft 26.2 | Fabric、Forge、NeoForge | Fabric、Forge、NeoForge | 6 |

模组版本为 `3.13.0.9`，不再带 `-marquee.1`。普通版文件名以 `-universal.jar` 结尾；MiSans 版以 `-universal-misans.jar` 结尾，模组列表显示为 `Modern UI (MiSans)`。两版的模组 ID 都是 `modernui`，每次只安装其中一版，切换时替换原 JAR。

## 功能和配置

- **普通版**：恢复 Inter / 思源黑体等默认字体、首选字体选择器、回退字体列表和字体注册列表，保存后尊重用户选择。JAR 不包含 MiSans 安装界面和下载器，不要求安装 MiSans。
- **MiSans 版**：保留 MiSans 固定字体组合、100–900 字重控制、缺失字体检查、安装界面、用户点击下载后从小米下载四套字体/28 个文件、校验、重试、许可及重启提示。已有字体无需重新下载。
- 两版分别使用 `config/ModernUI/client-standard.toml` 和 `config/ModernUI/client-misans.toml`。该版配置首次创建时复制原 `client.toml`，原文件保留；已有该版配置时不覆盖。
- 普通版首次导入旧配置时，仅将旧 MiSans 完整固定字体组合恢复为普通默认字体。其他设置和用户自定义的字体组合保留。MiSans 版继续执行其固定字体策略。
- `text.toml`、`common.toml` 和 bootstrap 配置仍然共用；下载的字体仍存放在 `config/ModernUI/fonts`。

## 构建与取包

GitHub Actions 的 **Build and Release** 每次按普通版、MiSans 版分别执行 `clean build`，运行回归检查并检查最终 JAR 的版本、配置文件名、安装器隔离和加载器配对，合并上传到 `modernui-mc-release-jars` artifact。普通分支推送只产出 artifact；标签或显式填写 release tag 才会发布 GitHub Release。

本地单版构建（JDK 25）：

```sh
./gradlew build -Pfont_variant=standard
./gradlew clean build -Pfont_variant=misans
```

省略 `font_variant` 时构建普通版。构建参数决定版本，运行时配置不会开启普通版的 MiSans 下载器。切换构建参数会重新生成常量并重新编译。
