# 第三方依赖

`foldspace/libs/` 保存构建所需的固定版本 JAR、来源 URL、SHA-256 和许可证。构建前校验 JAR 哈希，许可证也会复制到 APK assets。

| 依赖 | 版本 | 许可证 | 来源 |
| --- | --- | --- | --- |
| Shizuku API / provider / aidl / shared | 13.1.5 | MIT | [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) / Maven Central |
| AndroidX Annotation | 1.3.0 | Apache-2.0 | [AndroidX](https://developer.android.com/jetpack/androidx) / Google Maven |
| AndroidLiquidGlassView AGSL 着色器与 RenderNode 管线适配 | `d4238c557e46271f29ec0e3d3ee211bcf58dc2e0` | MIT | [QmDeve/AndroidLiquidGlassView](https://github.com/QmDeve/AndroidLiquidGlassView/tree/d4238c557e46271f29ec0e3d3ee211bcf58dc2e0) |

完整许可证见 `foldspace/libs/Shizuku-API-LICENSE.txt` 和 `foldspace/libs/AndroidX-Apache-2.0-LICENSE.txt`。具体下载地址和校验值见 `foldspace/libs/dependencies.json`。

液态玻璃复用上述固定提交的 `core/src/main/res/raw/liquidglass_effect.agsl` 原始文件，保留文件内完整 MIT 版权和贡献者声明；FoldSpace 新增共享背景采样、原生按钮适配、光泽描边及按压动效。Java 适配器参考上游 `LiquidGlassimpl` 的 RenderNode / RenderEffect 管线。版权归 Donny Yang / QmDeve 及相应贡献者所有，完整许可证也以 `foldspace/libs/AndroidLiquidGlassView-MIT-LICENSE.txt` 打包进 APK assets。来源与原文件 SHA-256 见 `foldspace/libs/liquid-glass-source.json`，构建时核对着色器与许可证哈希。Android 13+ 使用原始 AGSL 折射、色散及模糊；更旧系统或着色器不可用时使用渐变玻璃与高光回退。

Android SDK、Build Tools 和 JDK 由构建者自行安装，不包含在仓库中。OPPO、抖音、哔哩哔哩、快手等名称属于各自权利人，本项目不代表这些平台。
