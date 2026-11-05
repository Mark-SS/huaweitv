package com.quiztv.guard;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Serves questions: roughly half generated math, half from the static JSON bank
 * (语文 / 英语 / 科学 / 数学概念). Static questions are not repeated until many others
 * have been shown; option order is shuffled every time.
 */
final class QuestionBank {
    private static final String TAG = "QuestionBank";
    private static final int RECENT_LIMIT = 40;

    private final List<Question> staticQuestions = new ArrayList<>();
    private final Deque<Integer> recent = new ArrayDeque<>();
    private final MathGenerator math = new MathGenerator();
    private final Random rnd = new Random();

    QuestionBank(Context context) {
        load(context);
    }

    private void load(Context context) {
        try (InputStream in = context.getAssets().open("questions.json")) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            JSONArray array = new JSONArray(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                JSONArray opts = obj.getJSONArray("o");
                if (opts.length() != 4) {
                    continue;
                }
                String[] options = new String[4];
                for (int j = 0; j < 4; j++) {
                    options[j] = opts.getString(j);
                }
                staticQuestions.add(new Question(
                        obj.optString("c", "常识"),
                        obj.getString("q"),
                        options,
                        obj.optInt("a", 0),
                        obj.optString("e", "")));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load questions.json", e);
        }
    }

    int staticCount() {
        return staticQuestions.size();
    }

    Question next() {
        if (staticQuestions.isEmpty() || rnd.nextInt(100) < 50) {
            return math.next();
        }
        int index = pickStaticIndex();
        return shuffled(staticQuestions.get(index));
    }

    private int pickStaticIndex() {
        int size = staticQuestions.size();
        int limit = Math.min(RECENT_LIMIT, Math.max(0, size - 1));
        int index = rnd.nextInt(size);
        for (int attempt = 0; attempt < 50 && recent.contains(index); attempt++) {
            index = rnd.nextInt(size);
        }
        recent.addLast(index);
        while (recent.size() > limit) {
            recent.pollFirst();
        }
        return index;
    }

    private Question shuffled(Question source) {
        int[] order = {0, 1, 2, 3};
        for (int i = 3; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        String[] options = new String[4];
        int answerIndex = 0;
        for (int i = 0; i < 4; i++) {
            options[i] = source.options[order[i]];
            if (order[i] == source.answerIndex) {
                answerIndex = i;
            }
        }
        return new Question(source.category, source.text, options, answerIndex, source.explanation);
    }
}
