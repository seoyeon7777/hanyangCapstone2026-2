package com.example.capstone.login;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

import com.example.capstone.R;

/**
 * 휴대폰 번호 입력: "010" 고정 + 가운데 4자리 + 끝 4자리 칸 (view_phone_fields.xml).
 * 회원가입 본인확인과 같은 모양이다. 가운데 칸을 채우면 끝 칸으로 넘어가고,
 * 끝 칸을 비운 채 지우면 가운데 칸으로 돌아간다.
 */
public final class PhoneFields {

    private final View root;
    private final EditText mid;
    private final EditText last;
    private Runnable onChanged;

    /** @param root view_phone_fields를 include한 뷰 (또는 그것을 담은 부모) */
    public PhoneFields(View root) {
        this.root = root;
        mid = root.findViewById(R.id.etPhoneMid);
        last = root.findViewById(R.id.etPhoneLast);

        mid.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                changed();
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 4 && mid.hasFocus()) {
                    last.requestFocus();
                    last.setSelection(last.length());
                }
            }
        });

        last.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                changed();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        last.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && last.length() == 0) {
                mid.requestFocus();
                mid.setSelection(mid.length());
                return true;
            }
            return false;
        });
    }

    private void changed() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    /** 번호가 바뀔 때마다 부른다 */
    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public void setVisible(boolean visible) {
        root.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** 두 칸을 비운다 */
    public void clear() {
        mid.setText("");
        last.setText("");
    }

    /** 010으로 시작하는 숫자 11자리(하이픈 있어도 됨)를 칸에 나눠 넣는다. 아니면 비운다 */
    public void setNumber(String phone) {
        String digits = phone == null ? "" : PhoneInput.digitsOf(phone);
        if (digits.length() == 11 && digits.startsWith("010")) {
            mid.setText(digits.substring(3, 7));
            last.setText(digits.substring(7));
        } else {
            clear();
        }
    }

    /** 입력한 번호를 숫자만(01012345678)으로 돌려준다. 칸이 덜 찼거나 숫자가 아니면 null */
    public String number() {
        String m = mid.getText().toString().trim();
        String l = last.getText().toString().trim();
        if (!m.matches("[0-9]{4}") || !l.matches("[0-9]{4}")) {
            return null;
        }
        return com.example.capstone.data.LocalAccounts.normalizePhone("010" + m + l);
    }
}
