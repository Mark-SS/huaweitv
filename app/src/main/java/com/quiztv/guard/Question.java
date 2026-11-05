package com.quiztv.guard;

final class Question {
    final String category;
    final String text;
    final String[] options;
    final int answerIndex;
    final String explanation;

    Question(String category, String text, String[] options, int answerIndex, String explanation) {
        if (options.length != 4) {
            throw new IllegalArgumentException("A question must have exactly 4 options");
        }
        if (answerIndex < 0 || answerIndex > 3) {
            throw new IllegalArgumentException("answerIndex out of range");
        }
        this.category = category;
        this.text = text;
        this.options = options;
        this.answerIndex = answerIndex;
        this.explanation = explanation == null ? "" : explanation;
    }

    String correctOption() {
        return options[answerIndex];
    }
}
