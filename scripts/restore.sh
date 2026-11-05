#!/usr/bin/env bash
# 一键还原到安装前的状态。
#
# 用法:
#   TV=192.168.1.20 ./scripts/restore.sh                       # 使用最近一次备份
#   TV=192.168.1.20 ./scripts/restore.sh backups/20260101-120000
#
# 做的事（全部可逆、不碰系统应用）：
#   1. 关闭我们开启的保活钩子，把 enabled_accessibility_services / accessibility_enabled /
#      通知监听恢复成备份时的值
#   2. 从电池优化白名单移除、重置 appops（撤销悬浮窗等权限）
#   3. 备份时没装过本应用 -> 卸载；备份时装过旧版本 -> 装回旧 APK
#   4. 对比桌面与第三方应用列表，确认与备份一致
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

DIR="${1:-$(latest_backup)}"
[ -n "$DIR" ] || die "没有找到任何备份（$BACKUP_ROOT 为空），请指定备份目录"
[ -f "$DIR/state.env" ] || die "备份目录无效（缺少 state.env）：$DIR"
# shellcheck disable=SC1090
source "$DIR/state.env"
log "使用备份 ${DIR} (${BACKUP_TIME})"

connect_tv

cur_brand="$(sh_ getprop ro.product.brand)"; cur_model="$(sh_ getprop ro.product.model)"
bk_brand="$(sed -n 's/^brand=//p' "$DIR/device.txt")"; bk_model="$(sed -n 's/^model=//p' "$DIR/device.txt")"
if [ "$cur_model" != "$bk_model" ] || [ "$cur_brand" != "$bk_brand" ]; then
  warn "当前设备 $cur_brand $cur_model 与备份设备 $bk_brand $bk_model 不一致！"
  if [ "${FORCE:-0}" != "1" ]; then die "如确认要继续，加 FORCE=1"; fi
fi

# 1. 保活钩子 / 系统设置
log "恢复系统设置"
current_a11y="$(a11y_enabled_services)"
if [[ ":$current_a11y:" == *":$KEEPALIVE_A11Y:"* ]] || [ "$current_a11y" != "$A11Y_SERVICES" ]; then
  if [ -n "$A11Y_SERVICES" ]; then
    sh_ settings put secure enabled_accessibility_services "$A11Y_SERVICES"
  else
    # 只移除我们的服务，保留用户之后可能自己开启的其他无障碍服务
    cleaned="$(echo "$current_a11y" | tr ':' '\n' | grep -v "^$KEEPALIVE_A11Y$" | paste -sd ':' - || true)"
    if [ -n "$cleaned" ]; then
      sh_ settings put secure enabled_accessibility_services "$cleaned"
    else
      sh_ settings delete secure enabled_accessibility_services >/dev/null || true
    fi
  fi
  ok "enabled_accessibility_services 已恢复"
fi
if [ -n "$A11Y_ENABLED" ]; then
  sh_ settings put secure accessibility_enabled "$A11Y_ENABLED"
else
  if [ -z "$(a11y_enabled_services)" ]; then sh_ settings put secure accessibility_enabled 0; fi
fi
sh_ cmd notification disallow_listener "$KEEPALIVE_NOTIF" >/dev/null 2>&1 || true

# 2. 白名单 / appops
sh_ dumpsys deviceidle whitelist "-$PKG" >/dev/null 2>&1 || true
sh_ appops reset "$PKG" >/dev/null 2>&1 || true
ok "已撤销悬浮窗等权限，移出电池白名单"

# 3. 应用本体
if [ "$WAS_INSTALLED" = "1" ] && [ -n "$OLD_APK" ] && [ -f "$DIR/$OLD_APK" ]; then
  log "装回备份时的旧版本 $OLD_APK"
  "$ADB" install -r -d "$DIR/$OLD_APK" | grep -q Success || warn "旧版本安装失败，请手动处理"
  sh_ appops set "$PKG" SYSTEM_ALERT_WINDOW allow
  ok "旧版本已恢复"
elif [ "$WAS_INSTALLED" = "1" ]; then
  warn "备份时已装有本应用，但备份里没有旧 APK 文件，保留当前版本不卸载（如需彻底移除：adb uninstall $PKG）"
  sh_ appops set "$PKG" SYSTEM_ALERT_WINDOW allow
elif installed; then
  log "备份时未安装本应用，卸载 $PKG"
  "$ADB" uninstall "$PKG" | grep -q Success && ok "已卸载" || warn "卸载失败"
else
  ok "应用本来就不在电视上"
fi

# 4. 对比
log "核对"
now_home="$(home_activity)"
if [ "$now_home" = "$HOME_ACTIVITY" ]; then ok "桌面未变：${now_home}"; else warn "桌面变化：备份 ${HOME_ACTIVITY} -> 现在 ${now_home} (本应用不会改桌面，可能是其他操作)"; fi

diff_new="$(comm -13 <(sed 's/^package://' "$DIR/packages-third-party.txt" | sort) <(sh_ pm list packages -3 | sed 's/^package://' | sort) || true)"
if [ -z "$diff_new" ]; then
  ok "第三方应用列表与备份一致"
else
  warn "以下应用是备份之后新装的（非本脚本所为，未动）："; echo "$diff_new" | sed 's/^/     /'
fi

echo
ok "还原完成。电视已回到 $BACKUP_TIME 备份时的状态。"
