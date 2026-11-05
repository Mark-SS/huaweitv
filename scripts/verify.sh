#!/usr/bin/env bash
# 验收：检查安装、权限、服务是否就绪。退出码 0 = 全部通过。
set -uo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

connect_tv
fail=0

log "验收"
if installed; then
  ok "已安装 $PKG  版本 $(sh_ dumpsys package "$PKG" | sed -n 's/.*versionName=//p' | head -n1)"
else
  warn "未安装 $PKG"; fail=1
fi

mode="$(sh_ appops get "$PKG" SYSTEM_ALERT_WINDOW | sed -n 's/.*SYSTEM_ALERT_WINDOW: \([a-z]*\).*/\1/p' | head -n1)"
if [ "$mode" = "allow" ]; then
  ok "悬浮窗权限：allow"
else
  warn "悬浮窗权限：${mode:-未知}（弹题需要 allow）"; fail=1
fi

if sh_ dumpsys activity services "$PKG/.GuardService" | grep -q "ServiceRecord"; then
  ok "守护服务 GuardService 运行中"
else
  warn "守护服务未运行（打开一次 App 即可启动）"; fail=1
fi

if sh_ dumpsys deviceidle whitelist | grep -q "$PKG"; then
  ok "已在电池优化白名单"
else
  warn "不在电池优化白名单（部分电视待机后可能清理后台）"
fi

a11y="$(a11y_enabled_services)"
if [[ ":$a11y:" == *":$KEEPALIVE_A11Y:"* ]]; then ok "保活钩子（无障碍）：已开启"; else echo "   保活钩子（无障碍）：未开启（可选，KEEPALIVE=1 安装时开启）"; fi
if [[ "$(notif_listeners)" == *"$KEEPALIVE_NOTIF"* ]]; then ok "保活钩子（通知监听）：已开启"; else echo "   保活钩子（通知监听）：未开启（可选）"; fi

echo "   当前桌面：$(home_activity)"
exit $fail
