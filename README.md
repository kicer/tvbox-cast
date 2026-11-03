# tvbox-quiz

机顶盒答题系统的 **APK 端**，本仓库只放这个工程。
研究记录、Python 探测脚本、设备实测日志在本地 `../tvbox-cast/`，**不进本仓库**。

当前阶段：**探针 APK**（v0.1.0）。它不实现答题，只负责用一次安装回答 5 个决定架构的问题。

---

## 要回答的 5 个问题

| # | 问题 | 答不上来的后果 |
| --- | --- | --- |
| 1 | `pm set-home-activity` 在 Android 4.4 存在吗 | 方案 B（设为桌面）要换实现 |
| 2 | 我们前台时，**菜单 / 直播 / 回看** 的 keyCode 能到 `dispatchKeyEvent` 吗 | 到不了 → 方案 A/B 都拦不住，只剩改 keylayout / `EVIOCGRAB` |
| 3 | `return true` 能吃掉它们吗（画面留在我们这、不跳走） | 吃不掉 → 同上 |
| 4 | 从我们这里 `am start` 等价的 Intent 能切回 IPTV 吗 | 切不回 → 「答完继续看」断链 |
| 5 | 我们全屏时直播音频是否静音 | 会响 → 能「闭眼听电视」，产品逻辑要改 |

背景事实（已在真实设备上实测）：

- 盒子 `Hi3798MV310` / Android **4.4.2 (SDK 19)** / `ro.debuggable=1` / SELinux 关闭
- root adb 常驻（含重启自动恢复），端口 `19555`
- IPTV 桌面 = `com.ipanel.join.homed.stb/.HostActivity`，**同时是 HOME，uid=1000 (system)**
- 直播页 = `com.ipanel.join.topway.homed.live.LiveForHomeActivity`
- 覆盖直播时音频被静音（初步确认）

---

## 目录结构

```
tvbox-quiz/
├── settings.gradle / build.gradle / gradle.properties   # AGP 8.1.4
├── check.sh                 # 没有 SDK 时的离线语法自检（不产出 APK）
├── .check/stubs/            # check.sh 用的最小 android stub
├── .github/workflows/android.yml   # GitHub Actions 出 APK
└── app/
    ├── build.gradle                        # minSdk 19 / targetSdk 19 / compileSdk 34 / NDK 25.1
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/strings.xml
        ├── cpp/
        │   ├── CMakeLists.txt
        │   └── evgrab.c                    # EVIOCGRAB 独占 event0 + JNI 回调
        └── java/com/kicer/tvboxcast/
            ├── BaseProbeActivity.java      # 全部逻辑：UI + 按键分发 + 两个实验
            ├── MainActivity.java           # 普通 Activity（方案 A）
            ├── HomeProbeActivity.java      # 申请 CATEGORY_HOME（方案 B），默认 disabled
            ├── NativeGrab.java             # evgrab.c 的 Java 封装
            ├── RawKeys.java                # Linux 输入码 ↔ Android keyCode
            └── KeyNames.java               # keyCode → 中文名
```

包名 `com.kicer.tvboxcast`，**无任何第三方 Java 依赖**（离线可用、CI 快）。
ABI 只出 `armeabi-v7a`（盒子是 32 位 ARM）。

---

## 编译：走 GitHub Actions，不在本地编译

推到 `https://github.com/kicer/tvbox-cast`（把本目录内容作为仓库根），或手动触发 workflow。

```
Actions → android → Run workflow
产物：artifact `tvbox-quiz-debug`（app-debug.apk）
打 tag v* 会自动建 Release，方便直接从电视/手机下载
```

工作流：JDK 17 + Gradle 8.7 + `platforms;android-34` / `build-tools;34.0.0` /
`ndk;25.1.8937393` / `cmake;3.22.1`，`gradle :app:assembleDebug`。

> v0.2.0 起带了 NDK 模块（`evgrab.c`），CI 首次跑会多下载一份 NDK，耗时会长一点。

本机想先自查语法（**不产出 APK**）：

```sh
sh check.sh
```

---

## 安装 + 测试流程

前提：Mac 和盒子在同一网络（`adb connect 10.110.52.170:19555`）。

### 0. 装

```sh
adb install -r app-debug.apk
adb shell am start -n com.kicer.tvboxcast/.MainActivity
adb logcat -s KICERPROBE        # 另开一个窗口看
```

### 1. 屏幕上的内容

```
KICER PROBE / MAIN   com.kicer.tvboxcast            ← 标题
MENU(菜单)   (82)                                    ← 最近按下的键 + keyCode
模式: 全部消费   前台: 是   focus: 有   res/pause=2/1
Grab: 未抓   悬浮窗: 关   event0=Hi keyboard          ← v0.2.0 新增状态行
HOME → com.ipanel.join.homed.stb/...  (候选 1)
0=回桌面 1=Grab 2=Release 3=悬浮窗 4=直播 7=消费/放行 8=重读HOME 返回x3=退出
[22:20:01.114] MENU(菜单)  code=82  scan=139  src=0x701  [act]
[22:20:05.220] F2(直播)  linux=60  -> android=132  [raw]
```

日志行尾的来源标记：

| 标记 | 含义 |
| --- | --- |
| `[act]` | 来自 `dispatchKeyEvent`（正常输入管线） |
| `[raw]` | 来自 native 独占 event0（实验 1） |
| `[ovl]` | 来自悬浮窗（实验 2） |

**探针自身操作键**

| 键 | 动作 |
| --- | --- |
| `0` | 回 IPTV 桌面（`HostActivity`） |
| **`1`** | **EVIOCGRAB 独占 event0**（实验 1） |
| **`2`** | 释放独占 |
| **`3`** | 显示 / 隐藏全屏悬浮窗（实验 2） |
| `4` | 进直播（`LiveForHomeActivity`） |
| `7` | 切换「全部消费 / 放行给系统」 |
| `8` | 重新解析当前默认 HOME |
| `返回` ×3 | 退出探针（2.5s 内连按） |

### 2. 实验 1：Grab 独占 event0（**最关键**）

目的：验证 `EVIOCGRAB` 能不能绕开 `system_server` 里那个厂商键策略。

1. 保持探针在前台，按 `1`：
   - 状态行变 `Grab: 已独占 event0` → 成功
   - 变 `Grab: 失败: EVIOCGRAB ... (errno=1: Operation not permitted)` → **这条路不通**，记下原文
2. 按遥控器：`菜单` → `直播` → `回看` → `主页` → 随便按几个数字/方向
   - 出现 `[raw]` 行 → 独占生效（按键改从 native 来）
   - `[act]` 行同时消失 → 正常，说明系统确实读不到事件了
   - **重点看这三个危险键**：
     - 有 `F2(直播) ... [raw]`，且 **IPTV 没有跳出来** → ✅ 逃逸问题解决
     - IPTV 还是跳出来了 → 独占没挡住，说明钩子不在输入层
3. 按 `2` 释放，确认遥控器恢复正常

### 3. 实验 2：全屏悬浮窗（备选，不用 NDK）

1. 按 `3`，屏幕变成**洋红色全屏**（`TYPE_SYSTEM_ALERT` 悬浮窗）
2. 按 `直播 / 回看 / 主页`：
   - 洋红还在 → 悬浮窗压得住（但 IPTV 会在背后切台/出声，需配合静音）
   - 洋红没了 → 压不住，这个方案作废
3. 再按 `3` 关掉

### 4. 验证问题 1 / 2 / 3（HOME 模式，方案 B）

```sh
# 先把 HomeProbeActivity 打开
adb shell pm enable com.kicer.tvboxcast/.HomeProbeActivity

# Android 4.4 大概率没有 pm set-home-activity，先试
adb shell pm set-home-activity com.kicer.tvboxcast/.HomeProbeActivity

# 若不支持：直接发 HOME intent，让电视弹出选择器，用遥控器选我们
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

进去后屏幕标题变成 `KICER PROBE / HOME`，且 `HOME →` 那一行应显示
`com.kicer.tvboxcast/com.kicer.tvboxcast.HomeProbeActivity  [我们=HOME]`。

然后按 `菜单 / 直播 / 回看 / 主页`，看是否都能被吃掉。

**还原（很重要）**：

```sh
adb shell am start -n com.ipanel.join.homed.stb/.HostActivity
# 若已设为默认桌面，用遥控器在「桌面选择器」里选回原桌面；
# 或：
adb shell pm disable com.kicer.tvboxcast/.HomeProbeActivity
adb shell pm clear com.ipanel.join.homed.stb   # 慎用，会清 IPTV 数据，最后手段
```

> ⚠️ 把第三方 App 设成 HOME 有风险：系统重启/崩溃时可能停在我们的黑屏上。
> 测试完一定还原，不要在没人在家的时候保持这个状态。

### 4. 卸载

```sh
adb uninstall com.kicer.tvboxcast
```

---

## 结果记录表（2026-11-03 实测，真实遥控器）

| # | 验证项 | 结果 | 备注 |
| --- | --- | --- | --- |
| 1 | `pm set-home-activity` 可用 | ❌ **不存在** | 4.4 的 `pm` 没有该命令，方案 B 只能靠 HOME 选择器 |
| 2 | `菜单` keyCode 到达 | ✅ 到达 | 真实按键 `code=82 scan=139 src=0x701` |
| 2 | `直播` keyCode 到达 | ❌ **不到达** | 真机被拦；`input keyevent 132` 能到（假阳性） |
| 2 | `回看` keyCode 到达 | ❌ **不到达** | 真机被拦 |
| 3 | 全部消费后画面不被抢走 | ⚠️ **部分** | 菜单/方向/OK/返回/频道/数字都能吃掉；直播/回看/主页被 `system_server` 抢走 |
| 4 | 能切回 IPTV 桌面 | ✅ 可用 | 但被顶掉后要先 `force-stop` 再 `am start` 才拿得回焦点 |
| 5 | 全屏时直播音频静音 | ✅ 静音 | 早前实测确认 |
| — | HOME 模式能拦主页键 | 未测 | 4.4 无 `set-home-activity`，需弹选择器 |

**核心结论：`直播/回看/主页` 在 `system_server` 里就被消费了，应用层无法拦截。**
详细证据、排除过程、可选方案见 `../tvbox-cast/KEY_INTERCEPT.md`。

下一步要么 `EVIOCGRAB` 独占 `/dev/input/event0`（需 NDK），要么接受部分逃逸。

---

## 已知按键映射（实测）

| 遥控器键 | 内核 | Android keyCode | 备注 |
| --- | --- | --- | --- |
| 0-9 | `KEY_0`…`KEY_9` | 7…16 | |
| 「.」 | `KEY_DOT` | — | |
| 上/下/左/右 | `KEY_UP/DOWN/LEFT/RIGHT` | 19/20/21/22 | |
| OK | `KEY_ENTER` | 66 | |
| 返回 | `KEY_BACK` | 4 | |
| 音量 +/- | `KEY_VOLUMEUP/DOWN` | 24/25 | 可能被系统先处理 |
| 静音 | `KEY_MUTE` | 91 | |
| 菜单 | `KEY_MENU` | 82 | ✅ 可消费 |
| 直播 | `KEY_F2` | 132 | ❌ `system_server` 截走 |
| 回看 | `KEY_RECORD` | 130 | ❌ `system_server` 截走 |
| 主页 | `KEY_HOME` | 3 | ❌ 只有 HOME 应用能拦 |

---

下一步（v0.2.0 两个实验出结果后）

1. **实验 1（grab）成功** → 就在这个基础上做正式答题 App：
   - 题目弹出时 grab，结束后 release
   - 按键全部从 native 回调，音量自己用 `AudioManager` 调
   - 全屏 Activity（不需要改 keylayout、不需要当桌面）
2. **实验 1 失败、实验 2（悬浮窗）成功** → 走悬浮窗 + 全程静音，
   能保证「看不到也听不到」，但挡不住系统切台。
3. **两个都失败** → 只能改 keylayout（永久废掉三个键，需重启），或接受部分逃逸。
4. 正式功能：题目 UI（4 选项 + 倒计时）、题库 `assets/questions.json`、
   每 10 分钟用 `AlarmManager` 拉起。
5. 规则待定：超时算不算错、连对后是否重置计时、惩罚是否累加。

---

## 注意事项

- 盒子 adbd 是 **root 且对局域网开放**，同网段谁都能连，注意信任边界
- 不要在盒子上用 `head/tail/sort/wc/uname/md5sum`（**都不存在**），只能 `grep`/`cat`/`ls`
- 电源键 / 关机菜单**任何应用都拦不住**（4.4 没有 kiosk 模式）→ 产品目标只能是
  「没法舒服地看直播」，防不住直接关电视
