# 验证范围

## 0.6.3 后台退出与贴边侧栏 — 2026-10-26

退出路径不再使用会置前的 am display move-stack，而是通过 Shizuku 用户服务对本应用临时显示区内的任务进行归还。创建带本次 cookie 的临时后台 root，以一份 WindowContainerTransaction 顺序执行 root 置底、任务移入 root、任务脱离到目标显示区底部。只删除 getChildTasks 确认没有子任务的本次临时 root，不注册 TaskOrganizer，不操作其他任务的前后台位置。优先在修改系统任务前解析所需 API。

若接口不可用，则先释放临时显示区、等待系统迁回，再对对应任务做置后回退。该回退依赖系统 API，仍可能出现短暂前台变化，不宣称所有 ColorOS 情况均已消除闪跳。归还后台时取消原生进度恢复触摸，以免触碰当前前台应用。

Controller 和 View 释放增加完成回调；自动悬停窗口在两个应用完成后台归还，并且方向页停止后才移除。方向页停止有 1.5 秒回收超时保护。退出期间不重复创建新窗口；停止服务后不重入。展开／退出本身不发送应用或桌面的启动 Intent。

工具入口改为右边缘、下半区居中的 24dp × 88dp 竖向手柄，右侧无悬空边距；展开为 76dp 宽的纵向侧栏，列表可滚动，顶部箭头收起。沿用液态玻璃按键与边缘滑入动画。

已通过九组共 4776 项逻辑断言、资源／Java／DEX 编译、签名及 ZIP 对齐检查。BackgroundReturnTest 的 50 项断言覆盖顺序、归还未确认、创建缺失、阶段失败、空容器清理以及有子任务时不删除。当前没有连接 Find N2；跨显示区后台归还、回退无闪跳、方向页退出焦点以及侧栏触控／布局未作真机确认。

实现依据：[WindowContainerTransaction.reparent / reorder](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/window/WindowContainerTransaction.java)、[TaskOrganizerController](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/wm/TaskOrganizerController.java) 及 [WindowOrganizerController](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/wm/WindowOrganizerController.java)。源码检查不等同于 OEM 运行结果。

## 0.6.2 普通点击单开与 Activity 横屏 — 2026-10-26

用户反馈点击任意应用会同时打开两边，以及点击横屏键没有实际转向。源码确认普通点击和自动承接中包含默认双开及 remembered companion 启动分支；已移除。当前 pairMode 只在明确“双开”动作中置为 true，普通模式选择视频会收起下区游戏，选择游戏会收起上区应用。旧 dual 偏好不再读取。半展开自动进入只承接当前应用，不依赖目录加载或搭配。

横屏由 Activity 持有。主界面直接通过 Activity 的方向 API 请求；悬停界面则在显式点击后启动独立、不导出、不列入最近任务、全屏不透明的 RotationActivity，现有 SpacePanel 与上下应用视图不重新挂载。还原、锁屏、退出或方向页离开前台时结束。启动请求用单次标识关联本次界面，过期启动或进程重建不自动恢复方向请求。

“旋转中”最长等待 5 秒，只有方向 Activity 已绑定，默认显示区的原生模式尺寸结合实际旋转值确认宽大于高并稳定至少 350 毫秒才确认，未确认会提示并清理。未改写系统自动旋转开关或用户旋转值。加入 Android 的方向覆盖 opt-out 属性，以减少兼容策略替换应用方向的可能；这些属性不能证明所有 OEM 策略均会接受。

参考阅读器 [Legado BaseReadBookActivity.setOrientation](https://github.com/LegadoTeam/legado/blob/531f9fbf07bdf491cb0aafaf02c35058f3bd0692/app/src/main/java/io/legado/app/ui/book/read/BaseReadBookActivity.kt#L182) 的页面级方向选择模式，以及 [Android Activity 方向 API](https://developer.android.com/reference/android/app/Activity#setRequestedOrientation(int)) 和 [方向兼容属性](https://developer.android.com/reference/android/view/WindowManager#PROPERTY_COMPAT_ALLOW_ORIENTATION_OVERRIDE)。实现使用 Android 标准 API，自行编写方向页及生命周期协调。

已运行八组共 4726 项逻辑断言（包括 15 项内屏自然横向／外屏自然竖向、四种旋转及无效尺寸判断）、资源／Java／DEX 编译、签名和 ZIP 对齐检查，核对最终 APK 版本、方向页声明及资源。没有连接手机，尚未实际确认普通选择只出现一个应用、显式双开可用、ColorOS 允许方向页启动／执行横屏、还原后保留悬停、系统开关保持不变。方向确认逻辑提供反馈，不替代真机验证。

## 0.6.0 手动横屏、切换与液态玻璃 — 2026-10-26

此版不在 FoldMonitor 状态回调或应用启动完成回调中请求横屏，删除自动横屏开关。方向请求只由“横屏”按钮触发，按钮位于目录、视频单屏、双开工具栏和设置页；再次点击“还原”撤回。离开主界面、退出或关闭悬停后不自动重施请求。

UpperAppSwipeTest 的 26 项断言覆盖左右方向、每手势一次提交、长拖动多次 MOVE、短距离、取消、竖向优先、多指及无效坐标。原生 AppHostView 在父容器接管横向拖动时向目标应用发送 CANCEL，松手达阈值后只切一个视频应用；七组逻辑断言合计 4711 项。

液态玻璃来源为 QmDeve/AndroidLiquidGlassView 提交 `d4238c557e46271f29ec0e3d3ee211bcf58dc2e0`，按 MIT 许可证复用原始 AGSL。构建校验原文件和许可证 SHA-256，APK 包含 shader 资源及 MIT 文本。背景采样显式合成本应用背景图层，跳过玻璃控件自身，不重入根 View 的 draw，避免污染控件缓存或形成 RenderNode 环。背景最多每秒更新 20 次；双开下半区用于玻璃采样的游戏缩略图最长边受限、宽度不超过 480 像素，最多每秒读取 10 次，并只保存在内存中。按钮按当前帧重投影。GPU 着色器运行失败会回退并记录日志。

本次未连接手机，没有可用 AVD。源码／资源／DEX 编译和签名通过不代表 RuntimeShader 已在 Find N2 编译运行，也不代表背景采样、色散、多指 CANCEL、动画帧率与横屏按键已完成视觉和真机确认。下一次需核对这几项，并确认右下角游戏分区在切换视频时保留。

## 0.5.1 旋转与通知修正 — 2026-10-26

用户反馈 0.5.0 在半展开时改变系统自动旋转开关，并回到原方向；关闭系统自动旋转时不能正确转向。源码确认旧实现调用 user-rotation lock，该命令会改变系统旋转策略。此版改为窗口级 SCREEN_ORIENTATION_LANDSCAPE，不再向物理屏幕发送全局锁定、定时续租或退出恢复命令。悬浮窗更新自身 LayoutParams，手动界面更新 Activity 方向；撤回时只恢复各自先前的窗口请求。旧版恢复日志由 shell 服务启动时尝试一次清理，不用于新的悬停过程。

删除 Manifest 的 POST_NOTIFICATIONS 声明和运行时通知授权请求。保留 startForeground 与有效通知对象，使监测继续运行；Android 13+ 无通知授权时不在通知栏显示服务通知，系统后台活动列表仍可显示。Android 12 及以下仍可能显示服务通知。依据 [Android 通知权限说明](https://developer.android.com/develop/ui/views/notifications/notification-permission)。

已通过资源／Java／DEX 编译、签名、ZIP 对齐、最终 APK 权限检查，以及六组共 4685 项逻辑断言。新增 OrientationSessionTest 的 55 项断言覆盖重复进入／退出、先前方向恢复、已有横屏、其他新方向请求、窗口请求失败和独立窗口。原 0.5.0 的自然方向算法已删除，其测试不再作为旋转行为证据。

没有连接手机；尚未实际验证 Find N2 上系统开关开启／关闭时的横屏、ColorOS 对悬浮窗方向请求的处理、退出后方向恢复和覆盖安装后通知栏隐藏。建议两种系统自动旋转状态分别按 110°–140°保持 3 秒进入、范围外保持 3 秒退出测试，并检查开关保持原值。逻辑测试不代表上述系统行为已通过。

窗口选择方向的实现参考 [AOSP DisplayArea.Tokens](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/wm/DisplayArea.java)，实际效果仍受系统窗口策略影响。

## 0.5.0 双开与旋转 — 2026-10-26

本版在原界面调整之上加入上视频、下游戏双开与临时横屏。资源、Java、DEX、签名、ZIP 对齐及五组共 4644 项逻辑断言已通过；没有连接 Find N2，也没有可启动的本地 Android AVD。因此这里不宣称完成双开运行、游戏帧率、多指触摸、音频焦点、受保护视频、旋转方向及恢复的真机验证。

结构检查：CREATE 按上下槽位建立各自显示与任务状态，TOUCH／RESIZE／RELEASE 仍携带显示会话 ID。释放旧 ID 不影响同槽位新 ID。目录与设置保持上下 TextureView 附着，仅覆盖下半区。游戏不进行原生进度树查询。前台视频可接入上区并搭配曾成功选择的游戏，前台游戏接入下区。

旋转通过受限的 wm 接口读取当前 user-rotation 与 fixed-to-user-rotation，再保存恢复日志到 shell 可写目录；设置临时横屏。正常退出、锁屏、应用 Binder 死亡或约 10 秒未续租时尝试恢复。若 shell 进程本身异常终止，恢复日志在下次启动用户服务时重试，无法保证进程已终止期间立即恢复。

待真机验证：保持视频播放后打开游戏；交换其中一个应用确认另一个未重新启动；分别进行上下区点击与游戏多指操作；悬停进入／范围外退出、锁屏、收起及停止 Shizuku 后核对方向和两个任务；切换设置和目录确认返回入口可用。

旋转命令格式依据 [AOSP WindowManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/wm/WindowManagerShellCommand.java)。安全视频窗口在非安全虚拟显示上可能被留黑，详见 [Android DisplayManager](https://developer.android.com/reference/android/hardware/display/DisplayManager)。

## 0.4.0 界面更新 — 2026-10-25

本版调整目录、播放控制区与二级设置。已进行资源 / Java / DEX 编译、签名及 ZIP 对齐检查，并运行现有规则、坐标和恢复判断测试。当前未连接手机，尚未确认四宫格、分页、设置导航、不同字体大小及触控区的真机效果；以下真机记录均来自先前版本。

设置页独立覆盖下半屏，不移除、重建或调整上方 AppHostView；打开与关闭设置不会重新触发上方应用的承接流程。该结构检查不代表目标应用永不自行重建。

## 先前版本

截至 2026-10-24，主要测试设备为 OPPO Find N2 PGU110、Android 16、ColorOS 16.0.5，无 Root，Shizuku 用户服务运行于 shell 身份。不公开设备序列号、原始日志或用户画面。

## 自动检查

- `BackgroundReturnTest`：50 项后台归还与容器清理安全断言。
- `PhysicalOrientationTest`：15 项原生屏幕与实际方向判断断言。

- `FoldPolicyTest`：46 项边界、连续保持、计时重置及传感器中断断言。
- `ControlGeometryTest`：2041 项进度坐标、范围与校准断言。
- `WorkspacePolicyTest`：13 项视频／游戏识别与分区选择断言。
- `OrientationSessionTest`：55 项窗口方向请求与退出恢复断言。
- `UpperAppSwipeTest`：26 项每手势单次切换、竖向／多指保留及取消断言。
- `SessionRegistryTest`：2515 项上下区隔离、替换后过期释放及槽位约束断言。
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
