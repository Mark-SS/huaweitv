<div align="center">

# 答题看电视 QuizTV

**看电视每 10 分钟答一道四年级题，答错再加一题。**

给华为智慧屏 / Android TV 的家长守护小工具：不 root、不动系统应用、不改桌面，安装前自动备份，随时一键还原。

[下载 APK](release/quiztv-v1.0.apk) · [安装](#安装) · [还原](#还原) · [常见问题](#常见问题) · [开发文档](docs/DEVELOPMENT.md)

</div>

## 它做什么

- 后台守护服务计时，**每 10 分钟**（可调 1–120 分钟）全屏弹出一道题，盖住正在播放的画面，并让视频暂停。
- 遥控器方向键选择、OK 确认，或直接按数字键 1–4 作答；返回/菜单键无效，答对才能继续看。
- **答错一题就再加一题**，并显示正确答案和解析。
- 题目范围：小学四年级（人教版/部编版）
  - 数学：程序随机生成、永不重复，覆盖三位数乘两位数、两位数除法、混合运算、大数、小数、单位换算、角与三角形、鸡兔同笼、植树问题、平均数、行程问题等 17 类；
  - 语文 / 英语 / 科学常识：125 道精选题，选项每次打乱。
- 家长密码（默认 `1234`）保护：改间隔、关闭功能、临时跳过都要密码；首页显示今日答题数和正确率。
- 开机自启；可选"复活钩子"防止被电视清理后台。

## 适用机型

| 电视 | 能不能用 |
| --- | --- |
| 华为智慧屏 2019–2023 年机型（HarmonyOS 1.x / 2.x / 3.x，V / S / SE / 智慧屏 Z 等，底层为 Android） | ✅ 可以，通过 ADB 安装 |
| 2024 年起搭载 **HarmonyOS NEXT**（纯鸿蒙）的新机型 | ❌ 不兼容 APK |
| 小米、TCL、海信、索尼等 Android TV（Android 7.0+） | ✅ 可以 |

## 安装

全程局域网无线安装，不需要 USB 线，不需要 root。

### 第 0 步：电脑准备

- 装好 `adb`（Android platform-tools）：
  - macOS：`brew install --cask android-platform-tools`；装了 Android Studio 的直接可用，脚本会自动找到 `~/Library/Android/sdk/platform-tools/adb`。
  - Windows：从 [Android 官网](https://developer.android.com/tools/releases/platform-tools) 下载 platform-tools 解压，命令在该目录下用 `adb.exe` 执行。
- 电脑与电视连接**同一个路由器**（手机热点、访客 Wi‑Fi 通常有设备隔离，连不上）。
- 下载本仓库：`git clone https://github.com/Mark-SS/huaweitv.git`（或页面右上角 Code → Download ZIP），APK 已在 `release/` 目录，不需要自己编译。

### 第 1 步：电视上打开 ADB 调试

华为智慧屏：**设置 → 关于 / 系统信息 → 连续点击"版本号"5–7 次** 进入开发者模式 → 返回设置找到 **开发人员选项** → 打开 **ADB 调试**（部分固件叫"网络调试"）。

顺便记下电视 IP：**设置 → 网络 → 已连接的网络**，最下面，例如 `192.168.1.20`。

> 不同固件菜单名称略有差异，找不到时在设置里搜"开发""调试"。电视重启后 ADB 可能自动关闭，下次安装/还原前再打开即可，不影响 App 运行。

### 第 2 步：一键安装（macOS / Linux）

```bash
cd huaweitv
TV=192.168.1.20 KEEPALIVE=1 ./scripts/install.sh
```

第一次连接电视会弹"是否允许调试"，遥控器选 **始终允许**，再跑一次命令。

脚本会依次：

1. **备份**电视状态到 `backups/<时间戳>/`（应用列表、桌面、系统设置、电池白名单、无障碍设置；如果之前装过本应用还会保存旧 APK）；
2. 安装 `release/quiztv-v1.0.apk`；
3. 用 adb 授予"显示在其他应用上层"等权限（电视设置里通常没有这个开关）、加入电池白名单；
4. `KEEPALIVE=1` 时开启无障碍 + 通知监听"复活钩子"，待机/清理后台后系统会自动拉起守护服务（改动已包含在备份里，可还原）；
5. 启动应用并**验收**，打印权限、服务状态。

看到 `✔ 安装完成` 后，在电视上打开「答题看电视」→「立即弹一题试试」。

#### 只有"无线调试"的电视（Android 11+，没有 ADB 调试开关）

电视：开发人员选项 → **无线调试** → **使用配对码配对设备**，屏幕显示配对用的 `IP:端口` 和 6 位配对码；无线调试主页面还有另一组连接用的 `IP:端口`。

```bash
ADB_PAIR=192.168.1.20:37115 ADB_CODE=123456 ADB_CONNECT=192.168.1.20:39201 \
KEEPALIVE=1 ./scripts/install.sh
```

配对一次即可。想以后直接用 5555 端口，连上后执行一次 `adb tcpip 5555`。

### 第 2 步（Windows 或手动安装）

在 platform-tools 目录打开命令行，逐条执行（IP 换成你的）：

```bat
adb connect 192.168.1.20:5555
adb install -r quiztv-v1.0.apk
adb shell appops set com.quiztv.guard SYSTEM_ALERT_WINDOW allow
adb shell dumpsys deviceidle whitelist +com.quiztv.guard
adb shell am start -n com.quiztv.guard/.MainActivity
```

可选保活（会修改系统无障碍设置，手动还原见下文）：

```bat
adb shell settings put secure enabled_accessibility_services com.quiztv.guard/com.quiztv.guard.KeepAliveService
adb shell settings put secure accessibility_enabled 1
adb shell cmd notification allow_listener com.quiztv.guard/com.quiztv.guard.RevivalListener
```

> 手动安装前建议先备份：`adb shell settings get secure enabled_accessibility_services` 的输出记下来，还原时填回去。

### 第 3 步：验收

```bash
./scripts/verify.sh
```

或手动：

```bash
adb shell appops get com.quiztv.guard SYSTEM_ALERT_WINDOW      # 应为 allow
adb shell dumpsys activity services com.quiztv.guard/.GuardService | head -n 5   # 应看到 ServiceRecord
```

### 让 Agent 帮你装

把下面这段发给能在电脑上运行命令的 Agent（Codex、Claude Code 等）：

```text
请把这个仓库里的答题看电视 App 装到我的电视上，电视 IP：<电视IP>。
先备份再安装，开启保活（KEEPALIVE=1），最后把 ./scripts/verify.sh 的原始输出贴给我，并告诉我还原命令。
安装说明见 docs/agent.md，请先完整读完再动手。
```

## 使用

- 首页：答题间隔 ± 调整、开启/关闭、弹题时是否暂停播放、修改家长密码、今日统计。
- 默认家长密码 **1234**，装好后请立即修改。
- 弹题界面右下角「家长模式」输入密码可临时跳过本次答题。

## 还原

```bash
TV=192.168.1.20 ./scripts/restore.sh                          # 还原到最近一次备份
TV=192.168.1.20 ./scripts/restore.sh backups/20260101-120000    # 指定某次备份
```

还原会：恢复无障碍/通知监听等系统设置（只移除本应用的服务，电视上原有的不动）→ 撤销权限、移出电池白名单 → 卸载本应用（或装回备份时的旧版本）→ 对比桌面与第三方应用列表并列出差异。设备型号与备份不一致会拒绝执行（需 `FORCE=1`）。

手动还原：

```bash
adb shell settings put secure enabled_accessibility_services "<备份时记下的值>"   # 原来为空则执行 settings delete secure enabled_accessibility_services
adb shell cmd notification disallow_listener com.quiztv.guard/com.quiztv.guard.RevivalListener
adb shell dumpsys deviceidle whitelist -com.quiztv.guard
adb uninstall com.quiztv.guard
```

## 常见问题

| 现象 | 处理 |
| --- | --- |
| `adb connect` 失败 / 超时 | 电视 ADB 没开、IP 不对、或路由器开了 AP 隔离；`adb kill-server && adb start-server` 后重试 |
| `adb devices` 显示 `unauthorized` | 电视上的授权弹窗选"始终允许" |
| `INSTALL_FAILED_USER_RESTRICTED` 等 | 电视 设置 → 安全/应用 里允许安装未知来源应用 |
| 到点不弹题，通知提示缺少权限 | 执行 `adb shell appops set com.quiztv.guard SYSTEM_ALERT_WINDOW allow` |
| 待机一晚后不弹题 | 用 `KEEPALIVE=1` 重新安装，并在 电视设置 → 应用 → 答题看电视 中允许自启动/后台运行 |
| 弹题时视频没暂停 | 部分播放器不响应媒体键，属正常；题目会盖住画面，答完自动恢复 |
| 忘记家长密码 | `adb shell pm clear com.quiztv.guard` 重置为 1234（统计也会清空） |

## 校验

```
SHA256  release/quiztv-v1.0.apk
0cf7262f42b49c0a00829cc72604bec8edef200b0953902f9ae8fad058527f2b
```

## 开发与扩充题库

见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)：构建方式、代码结构、`questions.json` 题库格式（往里追加即可扩题）。

## 致谢

安装、授权、保活与还原思路参考了开源电视桌面 [Aerial](https://github.com/pengchujin/aerial-tv)（MIT）。

## 许可证

MIT
