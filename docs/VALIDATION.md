# 验证范围

截至 2026-10-24，主要测试设备为 OPPO Find N2 PGU110、Android 16、ColorOS 16.0.5，无 Root，Shizuku 用户服务运行于 shell 身份。不公开设备序列号、原始日志或用户画面。

## 自动检查

- `FoldPolicyTest`：46 项边界、连续保持、计时重置及传感器中断断言。
- `ControlGeometryTest`：2041 项进度坐标、范围与校准断言。
- `PlaybackContinuityTest`：15 项进度恢复条件断言。此测试只验证恢复判断逻辑，不代表抖音接受恢复操作。
- 资源 / Java / DEX 编译、APK v3 签名与 ZIP 对齐通过。

## 真机结果

- 沉浸显示、视频目录及抖音上半屏实际画面已确认。
- 自动进入与退出已得到实际操作和用户反馈确认。
- 任务记录确认现有抖音任务移动到临时显示区，并在退出后移回物理屏幕。
- 0.3.1 的进度记录仍出现不可读取或恢复未确认；用户反馈同一视频从头播放的问题仍存在。因此本版以预发布提供。
- 测试期间没有发现本应用的对应崩溃记录；这不代表已完成长时间稳定性测试。

## 实现依据

Android 源码说明，公开虚拟显示区不设置销毁内容标志时，移除显示区会将内容移到主显示区。实现仍先尝试显式移回任务。[DisplayManager 源码](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/hardware/display/DisplayManager.java)

原生进度恢复尝试使用可访问控件提供的进度操作，是否支持由目标应用决定。[Android ACTION_SET_PROGRESS](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.AccessibilityAction#ACTION_SET_PROGRESS)

## 尚未覆盖

其他折叠设备、全部视频平台与内容、长期后台存活、全部系统版本、无缝续播和视频/评论原生重排。请勿根据任务移动成功推断播放位置必然保留。
