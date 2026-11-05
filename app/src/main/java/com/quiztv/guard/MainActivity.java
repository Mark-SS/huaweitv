package com.quiztv.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATION = 11;

    private TextView statusView;
    private TextView permissionHint;
    private TextView intervalText;
    private TextView statsView;
    private Button toggleBtn;
    private Button pauseBtn;
    private Button permBtn;

    /** Parent PIN verified once per visit to the settings screen. */
    private boolean unlocked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.status);
        permissionHint = findViewById(R.id.permissionHint);
        intervalText = findViewById(R.id.intervalText);
        statsView = findViewById(R.id.stats);
        toggleBtn = findViewById(R.id.toggleBtn);
        pauseBtn = findViewById(R.id.pauseBtn);
        permBtn = findViewById(R.id.permBtn);

        findViewById(R.id.intervalMinus).setOnClickListener(v -> withPin(() -> changeInterval(-1)));
        findViewById(R.id.intervalPlus).setOnClickListener(v -> withPin(() -> changeInterval(1)));
        toggleBtn.setOnClickListener(v -> withPin(this::toggleEnabled));
        pauseBtn.setOnClickListener(v -> withPin(this::togglePause));
        findViewById(R.id.pinBtn).setOnClickListener(v -> withPin(this::changePin));
        findViewById(R.id.testBtn).setOnClickListener(v -> testNow());
        permBtn.setOnClickListener(v -> requestOverlayPermission());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Prefs.enabled(this)) {
            GuardService.start(this);
        }
        refresh();
    }

    private void refresh() {
        boolean enabled = Prefs.enabled(this);
        boolean canOverlay = Settings.canDrawOverlays(this);
        int interval = Prefs.intervalMinutes(this);

        StringBuilder sb = new StringBuilder();
        sb.append(enabled ? "● 状态：已开启，后台守护中" : "○ 状态：已关闭");
        sb.append("\n● 悬浮窗权限：").append(canOverlay ? "已授权 ✔" : "未授权 ✘（必须授权才能弹题）");
        sb.append("\n● 规则：每 ").append(interval).append(" 分钟答 1 题，答错再加 1 题");
        statusView.setText(sb);

        if (!canOverlay) {
            permissionHint.setVisibility(View.VISIBLE);
            permissionHint.setText("如果电视设置里找不到“显示在其他应用上层”，请在电脑上用 adb 执行：\n"
                    + "adb shell appops set " + getPackageName() + " SYSTEM_ALERT_WINDOW allow");
        } else {
            permissionHint.setVisibility(View.GONE);
        }

        intervalText.setText(interval + " 分钟");
        toggleBtn.setText(enabled ? "关闭答题功能" : "开启答题功能");
        pauseBtn.setText(Prefs.pausePlayback(this) ? "弹题时暂停并静音：开" : "弹题时暂停并静音：关");
        permBtn.setVisibility(canOverlay ? View.GONE : View.VISIBLE);

        int[] stats = Prefs.todayStats(this);
        statsView.setText("今日答题 " + stats[0] + " 道，答对 " + stats[1] + " 道"
                + (stats[0] > 0 ? "，正确率 " + (stats[1] * 100 / stats[0]) + "%" : ""));
    }

    // ----------------------------------------------------------- actions

    private void changeInterval(int delta) {
        int current = Prefs.intervalMinutes(this);
        int step = current >= 30 ? 5 : 1;
        int next = current + delta * step;
        Prefs.setIntervalMinutes(this, next);
        if (Prefs.enabled(this)) {
            GuardService.send(this, GuardService.ACTION_RESCHEDULE);
        }
        refresh();
    }

    private void toggleEnabled() {
        boolean enable = !Prefs.enabled(this);
        Prefs.setEnabled(this, enable);
        if (enable) {
            GuardService.start(this);
        } else {
            GuardService.send(this, GuardService.ACTION_STOP);
        }
        refresh();
    }

    private void togglePause() {
        Prefs.setPausePlayback(this, !Prefs.pausePlayback(this));
        refresh();
    }

    private void testNow() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授权悬浮窗权限", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return;
        }
        if (!Prefs.enabled(this)) {
            Toast.makeText(this, "请先开启答题功能", Toast.LENGTH_SHORT).show();
            return;
        }
        GuardService.send(this, GuardService.ACTION_TRIGGER_NOW);
    }

    private void requestOverlayPermission() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "这台电视没有可视化的授权页面，请按屏幕提示用 adb 授权", Toast.LENGTH_LONG).show();
        }
    }

    // --------------------------------------------------------------- PIN

    private void withPin(Runnable action) {
        if (unlocked) {
            action.run();
            return;
        }
        EditText input = makePinInput();
        new AlertDialog.Builder(this)
                .setTitle("请输入家长密码")
                .setView(wrap(input))
                .setPositiveButton("确定", (dialog, which) -> {
                    if (input.getText().toString().trim().equals(Prefs.pin(this))) {
                        unlocked = true;
                        action.run();
                    } else {
                        Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void changePin() {
        EditText input = makePinInput();
        new AlertDialog.Builder(this)
                .setTitle("设置新的家长密码（4-8 位数字）")
                .setView(wrap(input))
                .setPositiveButton("保存", (dialog, which) -> {
                    String pin = input.getText().toString().trim();
                    if (pin.length() < 4 || pin.length() > 8) {
                        Toast.makeText(this, "密码长度需为 4-8 位数字", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Prefs.setPin(this, pin);
                    Toast.makeText(this, "密码已更新", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText makePinInput() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(28);
        return input;
    }

    private View wrap(View view) {
        LinearLayout box = new LinearLayout(this);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }
}
