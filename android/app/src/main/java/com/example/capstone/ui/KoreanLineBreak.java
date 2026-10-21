package com.example.capstone.ui;

import android.widget.TextView;

public final class KoreanLineBreak {

    private static final char WORD_JOINER = '⁠';

    private KoreanLineBreak() {
    }

    public static void apply(TextView view) {
        view.setText(keepWords(view.getText().toString()));
    }

    public static String keepWords(String text) {
        StringBuilder out = new StringBuilder(text.length() * 2);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c);

            if (i + 1 < text.length()
                    && !Character.isWhitespace(c)
                    && !Character.isWhitespace(text.charAt(i + 1))
                    && c != WORD_JOINER
                    && text.charAt(i + 1) != WORD_JOINER) {
                out.append(WORD_JOINER);
            }
        }

        return out.toString();
    }
}
