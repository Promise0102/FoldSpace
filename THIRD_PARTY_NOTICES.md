# 第三方依赖

`foldspace/libs/` 保存构建所需的固定版本 JAR、来源 URL、SHA-256 和许可证。构建前校验 JAR 哈希，许可证也会复制到 APK assets。

| 依赖 | 版本 | 许可证 | 来源 |
| --- | --- | --- | --- |
| Shizuku API / provider / aidl / shared | 13.1.5 | MIT | [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) / Maven Central |
| AndroidX Annotation | 1.3.0 | Apache-2.0 | [AndroidX](https://developer.android.com/jetpack/androidx) / Google Maven |

完整许可证见 `foldspace/libs/Shizuku-API-LICENSE.txt` 和 `foldspace/libs/AndroidX-Apache-2.0-LICENSE.txt`。具体下载地址和校验值见 `foldspace/libs/dependencies.json`。

Android SDK、Build Tools 和 JDK 由构建者自行安装，不包含在仓库中。OPPO、抖音、哔哩哔哩、快手等名称属于各自权利人，本项目不代表这些平台。
