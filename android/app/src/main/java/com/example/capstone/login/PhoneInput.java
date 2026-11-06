package com.example.capstone.login;

import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextWatcher;
import android.widget.EditText;

/**
 * 휴대전화 번호 입력칸: 숫자만 들어가고, 하이픈은 입력하는 대로 자동으로 붙는다.
 * 010 번호만 받는다 (11자리, 010-1234-5678).
 *
 * 숫자가 아닌 글자·010이 아닌 시작·11자리 넘는 입력은 칸에 들어가기 전에 필터가 막는다
 * (들어갔다가 지우는 게 아니라서 깜빡이지 않는다). 하이픈은 글자가 바뀐 직후에 채운다.
 */
public final class PhoneInput {
    private static final String PREFIX = "010";
    private static final int MAX_DIGITS = 11;

    private PhoneInput() {
    }

    /** 칸에 붙인다. 숫자판만 뜨게 하고, 글자 필터와 하이픈 자동 입력을 건다. */
    public static void attach(EditText edit) {
        Formatter formatter = new Formatter();
        edit.setTag(formatter);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        edit.setFilters(new InputFilter[]{formatter});
        edit.addTextChangedListener(formatter);
        formatter.apply(edit.getText());           // 이미 들어 있던 글자도 모양을 맞춘다
    }

    /** 붙였던 것을 뗀다 (이메일 입력으로 바꿀 때). 입력 종류는 부르는 쪽이 다시 정한다. */
    public static void detach(EditText edit) {
        Object tag = edit.getTag();
        if (tag instanceof Formatter) {
            edit.removeTextChangedListener((Formatter) tag);
            edit.setFilters(new InputFilter[0]);
            edit.setTag(null);
        }
    }

    /** 숫자만 남긴다 */
    static String digitsOf(CharSequence text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 숫자 → 입력 중 모양. 0 / 01 / 010 / 010-1 / 010-1234 / 010-1234-5 / 010-1234-5678 */
    static String format(String digits) {
        if (digits.length() <= 3) {
            return digits;
        }
        if (digits.length() <= 7) {
            return digits.substring(0, 3) + "-" + digits.substring(3);
        }
        return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
    }

    /** 이 숫자가 010 번호가 될 수 있는 입력인지 (앞 3자리가 010이고 11자리 이하) */
    static boolean isAllowed(String digits) {
        if (digits.length() > MAX_DIGITS) {
            return false;
        }
        int head = Math.min(3, digits.length());
        return PREFIX.startsWith(digits.substring(0, head));
    }

    /**
     * 현재 글자(current)의 [start, end)를 source로 바꾸려 할 때 실제로 넣을 글자.
     * 숫자가 아닌 글자는 버리고, 010이 아니게 되거나 11자리를 넘으면 아무것도 넣지 않는다.
     * 붙여넣기는 넘치는 뒷부분만 자른다.
     */
    static String filterInsert(String current, int start, int end, String source) {
        String before = digitsOf(current.substring(0, start));
        String after = digitsOf(current.substring(end));
        String insert = digitsOf(source);
        int room = MAX_DIGITS - before.length() - after.length();
        if (room < insert.length()) {
            insert = insert.substring(0, Math.max(room, 0));
        }
        if (!isAllowed(before + insert + after)) {
            return "";
        }
        return insert;
    }

    private static final class Formatter implements InputFilter, TextWatcher {
        private boolean formatting;

        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            if (formatting) {
                return null;
            }
            String piece = source.subSequence(start, end).toString();
            String result = filterInsert(dest.toString(), dstart, dend, piece);
            return result.equals(piece) ? null : result;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            apply(s);
        }

        /** 숫자에 맞게 하이픈을 채운다. 커서는 같은 숫자 뒤에 둔다. */
        void apply(Editable s) {
            if (formatting) {
                return;
            }
            String text = s.toString();
            String formatted = format(digitsOf(text));
            if (formatted.equals(text)) {
                return;
            }
            int cursor = Math.min(Selection.getSelectionEnd(s), text.length());
            int digitsBeforeCursor = cursor < 0 ? digitsOf(text).length() : digitsOf(text.substring(0, cursor)).length();
            formatting = true;
            try {
                s.replace(0, s.length(), formatted);
                int pos = 0;
                int seen = 0;
                while (pos < formatted.length() && seen < digitsBeforeCursor) {
                    if (formatted.charAt(pos) != '-') {
                        seen++;
                    }
                    pos++;
                }
                Selection.setSelection(s, pos);
            } finally {
                formatting = false;
            }
        }
    }
}
