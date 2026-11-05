#!/usr/bin/env bash
# 备份电视当前状态（安装前自动调用，也可单独运行）。
# 用法: TV=192.168.1.20 ./scripts/backup.sh
# 产物: backups/<时间戳>/ ，restore.sh 据此还原。
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

connect_tv

STAMP="$(date +%Y%m%d-%H%M%S)"
DIR="$BACKUP_ROOT/$STAMP"
mkdir -p "$DIR"
log "备份到 $DIR"

# 1. 设备信息
sh_ getprop > "$DIR/getprop.txt"
{
  echo "brand=$(sh_ getprop ro.product.brand)"
  echo "model=$(sh_ getprop ro.product.model)"
  echo "android=$(sh_ getprop ro.build.version.release)"
  echo "sdk=$(sh_ getprop ro.build.version.sdk)"
  echo "harmony=$(sh_ getprop hw_sc.build.platform.version)"
  echo "fingerprint=$(sh_ getprop ro.build.fingerprint)"
  echo "serial=$("$ADB" get-serialno 2>/dev/null | tr -d '\r')"
} > "$DIR/device.txt"

# 2. 应用列表与桌面（用于还原后比对）
sh_ pm list packages -f > "$DIR/packages.txt"
sh_ pm list packages -3 > "$DIR/packages-third-party.txt"
home_activity > "$DIR/home.txt"

# 3. 我们会动到的系统设置（原样保存）
sh_ settings list secure > "$DIR/settings-secure.txt"
sh_ settings list global > "$DIR/settings-global.txt"
sh_ settings list system > "$DIR/settings-system.txt"
sh_ dumpsys deviceidle whitelist > "$DIR/deviceidle-whitelist.txt" || true

A11Y_ENABLED="$(sh_ settings get secure accessibility_enabled | sed 's/^null$//')"
A11Y_SERVICES="$(a11y_enabled_services)"
NOTIF_LISTENERS="$(notif_listeners)"

# 4. 如果之前装过本应用，把旧 APK 拉回来，便于回退到旧版本
OLD_APK=""
if installed; then
  path="$(sh_ pm path "$PKG" | head -n1 | sed 's/^package://')"
  if [ -n "$path" ]; then
    "$ADB" pull "$path" "$DIR/previous-$PKG.apk" >/dev/null 2>&1 && OLD_APK="previous-$PKG.apk"
  fi
  sh_ dumpsys package "$PKG" > "$DIR/dumpsys-package-$PKG.txt" || true
  sh_ appops get "$PKG" > "$DIR/appops-$PKG.txt" || true
  # 应用自己的设置（家长密码、间隔）无法在未 root 下导出，仅记录
fi

# 5. 还原脚本需要的关键状态
cat > "$DIR/state.env" <<EOF
# 由 backup.sh 生成，restore.sh 读取。请勿手改。
BACKUP_TIME="$STAMP"
PKG="$PKG"
WAS_INSTALLED=$(installed && echo 1 || echo 0)
OLD_APK="$OLD_APK"
A11Y_ENABLED="$A11Y_ENABLED"
A11Y_SERVICES="$A11Y_SERVICES"
NOTIF_LISTENERS="$NOTIF_LISTENERS"
HOME_ACTIVITY="$(cat "$DIR/home.txt")"
EOF

ok "备份完成：$DIR"
echo "   设备：$(sed -n 's/^brand=//p' "$DIR/device.txt") $(sed -n 's/^model=//p' "$DIR/device.txt")"
echo "   已装本应用：$([ "$OLD_APK" ] && echo "是（旧 APK 已保存）" || (installed && echo 是 || echo 否))"
echo "   还原命令：./scripts/restore.sh \"$DIR\""
echo "$DIR"
