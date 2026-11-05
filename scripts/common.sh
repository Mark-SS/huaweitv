#!/usr/bin/env bash
# 公共函数：被 backup.sh / install.sh / restore.sh / verify.sh source。

PKG="com.quiztv.guard"
KEEPALIVE_A11Y="$PKG/$PKG.KeepAliveService"
KEEPALIVE_NOTIF="$PKG/$PKG.RevivalListener"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKUP_ROOT="${BACKUP_ROOT:-$ROOT_DIR/backups}"

ADB="${ADB:-adb}"
if ! command -v "$ADB" >/dev/null 2>&1; then
  for candidate in "$HOME/Library/Android/sdk/platform-tools/adb" "$ANDROID_HOME/platform-tools/adb" "$ANDROID_SDK_ROOT/platform-tools/adb"; do
    if [ -x "$candidate" ]; then ADB="$candidate"; break; fi
  done
fi

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m ✔ \033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m ! \033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m ✘ \033[0m %s\n' "$*" >&2; exit 1; }

# adb shell 并去掉 \r；电视上部分命令会返回非 0，不让它中断脚本
sh_() { { "$ADB" shell "$@" 2>/dev/null || true; } | tr -d '\r'; }

# 连接电视。支持：
#   TV=192.168.1.20                 普通网络 ADB（5555 端口）
#   ADB_PAIR=ip:port ADB_CODE=123456 ADB_CONNECT=ip:port   Android 11+ 只有“无线调试”的电视
#   不设置 TV 时，直接用当前已连接的设备（USB / 模拟器）
connect_tv() {
  command -v "$ADB" >/dev/null 2>&1 || die "找不到 adb，请安装 Android platform-tools 或设置 ADB=/path/to/adb"
  "$ADB" start-server >/dev/null 2>&1 || true
  if [ -n "${ADB_PAIR:-}" ]; then
    log "无线调试配对 $ADB_PAIR"
    if [ -n "${ADB_CODE:-}" ]; then
      "$ADB" pair "$ADB_PAIR" "$ADB_CODE" || die "配对失败"
    else
      "$ADB" pair "$ADB_PAIR" || die "配对失败"
    fi
    [ -n "${ADB_CONNECT:-}" ] || die "请同时设置 ADB_CONNECT=<无线调试主页面的 IP:端口>"
    "$ADB" connect "$ADB_CONNECT" | grep -qi connected || die "连接 $ADB_CONNECT 失败"
  elif [ -n "${TV:-}" ]; then
    local target="$TV"
    [[ "$target" == *:* ]] || target="$target:5555"
    log "连接电视 $target"
    "$ADB" connect "$target" | grep -qi "connected" || die "连接 $target 失败，请确认电视已开启 ADB 调试且与电脑同一网络"
  fi
  local count
  count="$("$ADB" devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')"
  [ "$count" -ge 1 ] || die "没有在线的 adb 设备（adb devices 为空或 unauthorized，请在电视上点“允许”）"
  if [ "$count" -gt 1 ] && [ -z "${ANDROID_SERIAL:-}" ]; then
    warn "连接了多台设备，请用 ANDROID_SERIAL=<serial> 指定一台"
    "$ADB" devices
    exit 1
  fi
  ok "设备在线：$(sh_ getprop ro.product.brand) $(sh_ getprop ro.product.model)  Android $(sh_ getprop ro.build.version.release)"
}

installed() { sh_ pm path "$PKG" | grep -q package: ; }

a11y_enabled_services() { sh_ settings get secure enabled_accessibility_services | sed 's/^null$//'; }
notif_listeners()       { sh_ settings get secure enabled_notification_listeners | sed 's/^null$//'; }
home_activity()         { sh_ cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1; }

latest_backup() { { ls -1d "$BACKUP_ROOT"/*/ 2>/dev/null || true; } | sort | tail -n 1 | sed 's:/$::'; }
