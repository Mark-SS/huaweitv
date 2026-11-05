#!/usr/bin/env bash
# 一键安装：备份 -> 安装 APK -> 授权 -> 启动 -> 验收
#
# 用法:
#   TV=192.168.1.20 ./scripts/install.sh                 # 使用已构建的 release APK
#   TV=192.168.1.20 ./scripts/install.sh path/to/x.apk   # 指定 APK
#   KEEPALIVE=1 TV=... ./scripts/install.sh              # 同时开启无障碍/通知监听保活（会修改系统设置，备份可还原）
#   NO_BACKUP=1 ...                                      # 跳过备份（不推荐）
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

# APK 优先级：命令行参数 > 本地构建产物 > 仓库自带 release/ 目录
APK="${1:-}"
if [ -z "$APK" ]; then
  if [ -f "$ROOT_DIR/app/build/outputs/apk/release/app-release.apk" ]; then
    APK="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
  else
    APK="$(ls -1 "$ROOT_DIR"/release/*.apk 2>/dev/null | sort | tail -n 1 || true)"
  fi
fi
[ -n "$APK" ] && [ -f "$APK" ] || die "找不到 APK (release/ 目录为空且未构建，请运行 ./gradlew assembleRelease)"

connect_tv

# ---------------------------------------------------------------- 备份
if [ "${NO_BACKUP:-0}" = "1" ]; then
  warn "已跳过备份（NO_BACKUP=1）"
  BACKUP_DIR=""
else
  backup_out="$(mktemp)"
  "$SCRIPT_DIR/backup.sh" > "$backup_out" || { cat "$backup_out"; die "备份失败"; }
  sed '$d' "$backup_out"
  BACKUP_DIR="$(tail -n 1 "$backup_out")"
  rm -f "$backup_out"
  [ -d "$BACKUP_DIR" ] || die "备份失败"
fi

# ---------------------------------------------------------------- 安装
log "安装 $APK"
if ! "$ADB" install -r -g "$APK" 2>&1 | tee /tmp/quiztv-install.log | grep -q Success; then
  if grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE /tmp/quiztv-install.log; then
    warn "签名不一致，先卸载旧版本再装（应用内设置会丢失）"
    "$ADB" uninstall "$PKG" >/dev/null || true
    "$ADB" install -r -g "$APK" | grep -q Success || die "安装失败"
  else
    cat /tmp/quiztv-install.log
    die "安装失败"
  fi
fi
ok "APK 已安装"

# ---------------------------------------------------------------- 授权
log "授予悬浮窗权限（显示在其他应用上层）"
sh_ appops set "$PKG" SYSTEM_ALERT_WINDOW allow
sh_ appops set "$PKG" RUN_IN_BACKGROUND allow || true
sh_ appops set "$PKG" RUN_ANY_IN_BACKGROUND allow || true
sh_ pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
log "加入电池优化白名单（防止待机被清理）"
sh_ dumpsys deviceidle whitelist "+$PKG" >/dev/null || warn "deviceidle whitelist 不可用（忽略）"

if [ "${KEEPALIVE:-0}" = "1" ]; then
  log "开启保活钩子（无障碍 + 通知监听）"
  current="$(a11y_enabled_services)"
  if [[ ":$current:" != *":$KEEPALIVE_A11Y:"* ]]; then
    new="$KEEPALIVE_A11Y"; [ -n "$current" ] && new="$current:$KEEPALIVE_A11Y"
    sh_ settings put secure enabled_accessibility_services "$new"
  fi
  sh_ settings put secure accessibility_enabled 1
  sh_ cmd notification allow_listener "$KEEPALIVE_NOTIF" >/dev/null 2>&1 || warn "通知监听授权不可用（忽略）"
fi

# ---------------------------------------------------------------- 启动
log "启动应用"
sh_ am start -n "$PKG/.MainActivity" >/dev/null
sleep 2
sh_ am startservice -n "$PKG/.GuardService" >/dev/null 2>&1 \
  || sh_ am start-foreground-service -n "$PKG/.GuardService" >/dev/null 2>&1 || true
sh_ cmd package compile -m speed -f "$PKG" >/dev/null 2>&1 || true

# ---------------------------------------------------------------- 验收
"$SCRIPT_DIR/verify.sh"

echo
ok "安装完成。"
if [ -n "$BACKUP_DIR" ]; then
  echo "   随时还原：./scripts/restore.sh \"$BACKUP_DIR\""
fi
echo "   在电视上打开「答题看电视」，默认家长密码 1234，建议立即修改。"
