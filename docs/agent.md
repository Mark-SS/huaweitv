# 给 Agent 的安装说明（答题看电视 / QuizTV）

你是在用户电脑上运行命令的 Agent。请**完整读完**再动手，亲自运行命令，需要用户在电视上操作时提醒用户。

## 目标

把本仓库的 Android TV 应用 `com.quiztv.guard` 装到用户的华为智慧屏（或其他 Android TV）上，
**安装前必须备份**，安装后验收，并把还原命令告诉用户。

## 前置

1. 电脑上有 `adb`：`adb version`。没有的话 macOS 用 `brew install --cask android-platform-tools`，
   或使用 `~/Library/Android/sdk/platform-tools/adb`（脚本会自动找）。
2. APK 已构建：`app/build/outputs/apk/release/app-release.apk`。没有则 `./gradlew assembleRelease`（需要 JDK 17+ 与 Android SDK）。
3. 用户已在电视上打开 ADB 调试，并提供了电视 IP。电视和电脑在同一局域网。

## 步骤

```bash
cd <仓库目录>
TV=<电视IP> KEEPALIVE=1 ./scripts/install.sh
```

- 第一次连接时电视可能弹“是否允许 USB 调试”，让用户选“始终允许”，然后重跑命令。
- 如果 `adb connect` 失败：确认 IP、确认 ADB 调试已开、尝试 `adb kill-server && adb start-server`。
- Android 11+ 只有“无线调试”的电视：让用户在 开发者选项 → 无线调试 → 使用配对码配对设备 里读出
  配对 IP:端口 + 6 位码，以及无线调试主页的 IP:端口，然后
  `ADB_PAIR=<配对ip:port> ADB_CODE=<6位码> ADB_CONNECT=<主页ip:port> KEEPALIVE=1 ./scripts/install.sh`。
- 脚本输出的 `backups/<时间戳>` 目录就是备份，**不要删除**。

## 验收（把原始输出贴给用户）

```bash
./scripts/verify.sh
adb shell appops get com.quiztv.guard SYSTEM_ALERT_WINDOW
adb shell dumpsys activity services com.quiztv.guard/.GuardService | head -n 5
```

通过标准：`SYSTEM_ALERT_WINDOW: allow`，且能看到 `ServiceRecord{… com.quiztv.guard/.GuardService}`。
然后让用户在电视上打开「答题看电视」→「立即弹一题试试」，确认全屏题目能弹出、答对后消失。

## 告诉用户

- 默认家长密码 `1234`，请立刻在 App 里修改；
- 还原命令：`TV=<电视IP> ./scripts/restore.sh "<备份目录>"`；
- 本应用不 root、不替换系统应用、不改桌面；还原后电视与安装前一致。

## 禁止

- 不要执行 `adb root`、`adb remount`、`pm disable` 系统应用、修改桌面（HOME）等操作；
- 不要跳过备份（`NO_BACKUP=1`），除非用户明确要求。
