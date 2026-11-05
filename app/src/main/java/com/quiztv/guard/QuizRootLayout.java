package com.quiztv.guard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.FrameLayout;

/**
 * Root of the full-screen quiz overlay. Swallows BACK so the kid cannot dismiss the
 * quiz, and forwards remote-control number keys (1-4) to the quiz controller.
 */
public class QuizRootLayout extends FrameLayout {

    interface KeyListener {
        /** @return true when the key was consumed. */
        boolean onNumberKey(int number);

        boolean isPinPanelOpen();
    }

    private KeyListener keyListener;

    public QuizRootLayout(Context context) {
        super(context);
    }

    public QuizRootLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public QuizRootLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    void setKeyListener(KeyListener listener) {
        this.keyListener = listener;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE
                || code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_APP_SWITCH) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyListener != null && !keyListener.isPinPanelOpen()) {
            int number = numberFromKey(code);
            if (number >= 1 && number <= 4 && keyListener.onNumberKey(number)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static int numberFromKey(int code) {
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            return code - KeyEvent.KEYCODE_0;
        }
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
            return code - KeyEvent.KEYCODE_NUMPAD_0;
        }
        return -1;
    }
}
