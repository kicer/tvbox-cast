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
    ├── build.gradle                        # minSdk 19 / targetSdk 19 / compileSdk 34
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/strings.xml
        └── java/com/kicer/tvboxcast/
            ├── BaseProbeActivity.java      # 全部逻辑：UI + dispatchKeyEvent
            ├── MainActivity.java           # 普通 Activity（方案 A）
            ├── HomeProbeActivity.java      # 申请 CATEGORY_HOME（方案 B），默认 disabled
            └── KeyNames.java               # keyCode → 中文名
```

包名 `com.kicer.tvboxcast`，**无任何第三方依赖**（离线可用、CI 快）。

---

## 编译：走 GitHub Actions，不在本地编译

推到 `https://github.com/kicer/tvbox-cast`（把本目录内容作为仓库根），或手动触发 workflow。

```
Actions → android → Run workflow
产物：artifact `tvbox-quiz-debug`（app-debug.apk）
打 tag v* 会自动建 Release，方便直接从电视/手机下载
```

工作流：JDK 17 + Gradle 8.7 + `platforms;android-34` / `build-tools;34.0.0`，`gradle :app:assembleDebug`。

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
KICER PROBE / MAIN   com.kicer.tvboxcast     ← 标题（HOME 模式是另一行）
HOME(主页)   (3)                              ← 最近按下的键 + keyCode
模式: 全部消费   前台: 是   windowFocus: 有   resume=2 pause=1
HOME → com.ipanel.join.homed.stb/com.ipanel.join.homed.stb.HostActivity  (候选 2)
0=回IPTV桌面  9=进直播  7=切换消费/放行  8=重读HOME  返回x3=退出
[21:33:02.114] ENTER(OK) code=66 scan=28 src=0x101 rc=0
[21:33:03.220] ✓ 仍在我们的界面  (after MENU(菜单))
...
```

**`模式: 全部消费` = `dispatchKeyEvent` 对所有键 `return true`**（问题 3 的实验组）。
按 `7` 切成 `放行` 就是对照组。

**探针自身操作键**

| 键 | 动作 |
| --- | --- |
| `0` | 切回 IPTV 桌面（`HostActivity`）→ 验证问题 4 |
| `9` | 直接进直播（`LiveForHomeActivity`） |
| `7` | 切换「全部消费 / 放行给系统」→ 验证问题 3 |
| `8` | 重新解析当前默认 HOME |
| `返回` ×3 | 退出探针（2.5s 内连按） |

### 2. 验证问题 2 / 3 / 5（普通 Activity，方案 A）

保持 `模式: 全部消费`，用**真实遥控器**依次按：

`菜单` → `直播` → `回看` → `主页`

- 每次按完看屏幕：
  - 出现了 `MENU(菜单) code=82` 这类行 → **问题 2 通过**（能拿到）
  - 1.5s 后出现 `✓ 仍在我们的界面` → **问题 3 通过**（吃得掉）
  - 出现 `✗ 焦点已被抢走` 或 `onPause <<< 我们失去前台` → **吃不掉**，记下是哪个键
  - **完全没有任何日志行** → 事件没到我们这里（被更上层截走）
- `主页` 在普通 Activity 模式下**一定**会走掉（这是系统行为，不算失败），
  它只在 `HomeProbeActivity` 作为桌面时才可能被拦。
- 顺便听电视有没有声音（问题 5）。

对照组：按 `7` 切到 `放行`，再按 `菜单/直播/回看`，对比「跳不跳走」。

### 3. 验证问题 1 / 2 / 3（HOME 模式，方案 B）

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

## 结果记录表（测完填这里）

| # | 验证项 | 结果 | 备注 |
| --- | --- | --- | --- |
| 1 | `pm set-home-activity` 可用 | 待测 | |
| 2 | `菜单` keyCode 到达 | 待测 | |
| 2 | `直播` keyCode 到达 | 待测 | |
| 2 | `回看` keyCode 到达 | 待测 | |
| 3 | 全部消费后画面不被抢走 | 待测 | |
| 4 | 能切回 IPTV 桌面 | 待测 | |
| 5 | 全屏时直播音频静音 | 待测 | |
| — | HOME 模式能拦主页键 | 待测 | |

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
| 菜单 | `KEY_MENU` | 82 | **待验证能否消费** |
| 直播 | `KEY_F2` | 132 | **待验证** |
| 回看 | `KEY_RECORD` | 130 | **待验证** |
| 主页 | `KEY_HOME` | 3 | 只有 HOME 应用能拦 |

---

## 下一步（探针测完之后）

1. 按结果选方案：
   - 三个键都能吃掉 → **方案 A**（普通全屏 Activity + 定时拉起），不动系统 HOME，风险最低
   - 只有主页键跑 → **A + B**（再叠一层 `HomeProbeActivity`）
   - 三个键都被 system 级钩子截走 → 只剩改 keylayout（全局静态）或 `EVIOCGRAB`
2. 把 `BaseProbeActivity` 里的 UI 换成正式界面：题目 + 4 个选项 + 倒计时
3. 题库：`assets/questions.json`（简单、可换、不依赖网络）
4. 调度：App 内 `Handler` / `AlarmManager`，每 10 分钟拉起自己
5. 规则待定：超时算不算错、连对后是否重置计时、惩罚是否累加

---

## 注意事项

- 盒子 adbd 是 **root 且对局域网开放**，同网段谁都能连，注意信任边界
- 不要在盒子上用 `head/tail/sort/wc/uname/md5sum`（**都不存在**），只能 `grep`/`cat`/`ls`
- 电源键 / 关机菜单**任何应用都拦不住**（4.4 没有 kiosk 模式）→ 产品目标只能是
  「没法舒服地看直播」，防不住直接关电视
