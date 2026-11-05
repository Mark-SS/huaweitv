package com.quiztv.guard;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Full-screen quiz window drawn on top of everything (TYPE_APPLICATION_OVERLAY).
 * Rules: start with 1 question to answer. Correct answer -> one less remaining.
 * Wrong answer -> one MORE remaining ("答错再加一题"). Window closes when remaining hits 0.
 */
final class QuizOverlay implements QuizRootLayout.KeyListener {
    private static final String TAG = "QuizOverlay";
    private static final long CORRECT_DELAY_MS = 1500;
    private static final long WRONG_DELAY_MS = 4000;
    private static final String[] LABELS = {"1. ", "2. ", "3. ", "4. "};

    interface Listener {
        void onQuizFinished(boolean parentSkipped);
    }

    private final Context context;
    private final WindowManager windowManager;
    private final QuestionBank bank;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private QuizRootLayout root;
    private TextView categoryView;
    private TextView questionView;
    private TextView feedbackView;
    private final Button[] optionButtons = new Button[4];
    private View pinPanel;
    private EditText pinInput;
    private TextView pinError;

    private Question current;
    private int remaining;
    private boolean locked;
    private boolean showing;

    QuizOverlay(Context context, QuestionBank bank, Listener listener) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.bank = bank;
        this.listener = listener;
    }

    boolean isShowing() {
        return showing;
    }

    void show() {
        if (showing) {
            return;
        }
        root = (QuizRootLayout) LayoutInflater.from(context).inflate(R.layout.overlay_quiz, null);
        root.setKeyListener(this);
        categoryView = root.findViewById(R.id.category);
        questionView = root.findViewById(R.id.question);
        feedbackView = root.findViewById(R.id.feedback);
        optionButtons[0] = root.findViewById(R.id.opt0);
        optionButtons[1] = root.findViewById(R.id.opt1);
        optionButtons[2] = root.findViewById(R.id.opt2);
        optionButtons[3] = root.findViewById(R.id.opt3);
        pinPanel = root.findViewById(R.id.pinPanel);
        pinInput = root.findViewById(R.id.pinInput);
        pinError = root.findViewById(R.id.pinError);

        for (int i = 0; i < 4; i++) {
            final int index = i;
            optionButtons[i].setOnClickListener(v -> onAnswer(index));
        }
        root.findViewById(R.id.parentBtn).setOnClickListener(v -> openPinPanel());
        root.findViewById(R.id.pinOk).setOnClickListener(v -> checkPin());
        root.findViewById(R.id.pinCancel).setOnClickListener(v -> closePinPanel());
        pinInput.setOnEditorActionListener((v, actionId, event) -> {
            checkPin();
            return true;
        });

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
        try {
            windowManager.addView(root, params);
        } catch (Exception e) {
            Log.e(TAG, "addView failed (missing SYSTEM_ALERT_WINDOW?)", e);
            root = null;
            listener.onQuizFinished(true);
            return;
        }
        showing = true;
        remaining = 1;
        showNextQuestion();
    }

    void hide() {
        handler.removeCallbacksAndMessages(null);
        if (root != null) {
            try {
                windowManager.removeView(root);
            } catch (Exception e) {
                Log.w(TAG, "removeView failed", e);
            }
        }
        root = null;
        showing = false;
    }

    private void showNextQuestion() {
        current = bank.next();
        locked = false;
        categoryView.setText("📚 " + current.category + "  ·  还需答对 " + remaining + " 题");
        questionView.setText(current.text);
        feedbackView.setText("");
        for (int i = 0; i < 4; i++) {
            optionButtons[i].setText(LABELS[i] + current.options[i]);
            optionButtons[i].setEnabled(true);
            optionButtons[i].setAlpha(1f);
        }
        optionButtons[0].requestFocus();
    }

    private void onAnswer(int index) {
        if (locked || current == null) {
            return;
        }
        locked = true;
        boolean correct = index == current.answerIndex;
        Prefs.recordAnswer(context, correct);
        for (int i = 0; i < 4; i++) {
            optionButtons[i].setEnabled(false);
            optionButtons[i].setAlpha(i == current.answerIndex || i == index ? 1f : 0.35f);
        }
        long delay;
        if (correct) {
            remaining--;
            feedbackView.setTextColor(context.getColor(R.color.ok));
            feedbackView.setText(remaining == 0
                    ? "✔ 答对了！继续看电视吧～"
                    : "✔ 答对了！还需答对 " + remaining + " 题");
            delay = CORRECT_DELAY_MS;
        } else {
            remaining++;
            feedbackView.setTextColor(context.getColor(R.color.bad));
            String explain = current.explanation.isEmpty() ? "" : "（" + current.explanation + "）";
            feedbackView.setText("✘ 答错了，正确答案是 " + LABELS[current.answerIndex] + current.correctOption()
                    + explain + "\n再加一题！还需答对 " + remaining + " 题");
            delay = WRONG_DELAY_MS;
        }
        handler.postDelayed(() -> {
            if (!showing) {
                return;
            }
            if (remaining <= 0) {
                hide();
                listener.onQuizFinished(false);
            } else {
                showNextQuestion();
            }
        }, delay);
    }

    // ------------------------------------------------------------ parent PIN

    private void openPinPanel() {
        pinError.setText("");
        pinInput.setText("");
        pinPanel.setVisibility(View.VISIBLE);
        pinInput.requestFocus();
    }

    private void closePinPanel() {
        hideKeyboard();
        pinPanel.setVisibility(View.GONE);
        if (!locked) {
            optionButtons[0].requestFocus();
        }
    }

    private void checkPin() {
        String entered = pinInput.getText().toString().trim();
        if (entered.equals(Prefs.pin(context))) {
            hideKeyboard();
            hide();
            listener.onQuizFinished(true);
        } else {
            pinError.setText("密码错误");
            pinInput.setText("");
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && pinInput != null) {
            imm.hideSoftInputFromWindow(pinInput.getWindowToken(), 0);
        }
    }

    // ---------------------------------------------- QuizRootLayout.KeyListener

    @Override
    public boolean onNumberKey(int number) {
        if (locked || current == null) {
            return true;
        }
        onAnswer(number - 1);
        return true;
    }

    @Override
    public boolean isPinPanelOpen() {
        return pinPanel != null && pinPanel.getVisibility() == View.VISIBLE;
    }
}
