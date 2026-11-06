package com.example.capstone.ui;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import com.example.capstone.R;
import com.example.capstone.data.LocalAccounts;

/**
 * 이메일 입력: [앞부분] @ [도메인 목록]. "직접 입력"을 고르면 도메인 칸이 나온다.
 * 회원가입 화면과 프로필 수정 > 이메일 변경이 같이 쓴다.
 */
public final class EmailDomainInput {

    public static final String PLACEHOLDER = "이메일 선택";
    public static final String DIRECT = "직접 입력";
    private static final String[] DOMAINS = {
            PLACEHOLDER, "naver.com", "gmail.com", "hanmail.net", "daum.net", "nate.com", DIRECT
    };

    private final EditText etLocal;
    private final Spinner spDomain;
    private final EditText etDirect;
    private final View btnList;

    /** onChange: 주소가 바뀔 때마다 부른다 (필요 없으면 null) */
    public EmailDomainInput(Context context, EditText etLocal, Spinner spDomain, EditText etDirect,
                            Runnable onChange) {
        this.etLocal = etLocal;
        this.spDomain = spDomain;
        this.etDirect = etDirect;

        // 키보드가 한글이어도 주소에 쓸 수 있는 글자만 들어가게 한다
        etLocal.setFilters(new InputFilter[]{allowOnly("[A-Za-z0-9._%+-]"), new InputFilter.LengthFilter(64)});
        etDirect.setFilters(new InputFilter[]{allowOnly("[A-Za-z0-9.-]"), new InputFilter.LengthFilter(100)});

        // 같은 자리에 겹쳐 둔 ▾: 직접 입력 중에 누르면 목록을 다시 연다
        btnList = ((View) etDirect.getParent()).findViewById(R.id.btnDomainList);
        if (btnList != null) {
            btnList.setOnClickListener(v -> spDomain.performClick());
        }

        spDomain.setAdapter(placeholderAdapter(context, DOMAINS));
        spDomain.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean direct = isDirect();
                showDirect(direct);
                if (direct) {
                    // 고르자마자 그 칸에 바로 쓸 수 있게 키보드까지 연다
                    etDirect.requestFocus();
                    InputMethodManager keyboard =
                            (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    etDirect.post(() -> keyboard.showSoftInput(etDirect, InputMethodManager.SHOW_IMPLICIT));
                }
                if (onChange != null) {
                    onChange.run();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        if (onChange != null) {
            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    onChange.run();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };
            etLocal.addTextChangedListener(watcher);
            etDirect.addTextChangedListener(watcher);
        }
    }

    /** 이미 있는 주소를 칸에 나눠 넣는다 (목록에 없는 도메인은 "직접 입력") */
    public void setEmail(String email) {
        if (email == null || !email.contains("@")) {
            return;
        }
        String domain = email.substring(email.indexOf('@') + 1);
        etLocal.setText(email.substring(0, email.indexOf('@')));
        int position = DOMAINS.length - 1;
        for (int i = 1; i < DOMAINS.length - 1; i++) {
            if (DOMAINS[i].equals(domain)) {
                position = i;
            }
        }
        spDomain.setSelection(position);
        etDirect.setText(position == DOMAINS.length - 1 ? domain : "");
        showDirect(position == DOMAINS.length - 1);
    }

    /** "직접 입력"이면 목록 칸 자리에 입력칸과 ▾를 보여 준다 (목록은 ▾로 열 수 있게 자리만 남긴다) */
    private void showDirect(boolean direct) {
        spDomain.setVisibility(direct ? View.INVISIBLE : View.VISIBLE);
        etDirect.setVisibility(direct ? View.VISIBLE : View.GONE);
        if (btnList != null) {
            btnList.setVisibility(direct ? View.VISIBLE : View.GONE);
        }
    }

    public boolean isEmpty() {
        return etLocal.getText().toString().trim().isEmpty();
    }

    public boolean isDirect() {
        return DIRECT.equals(String.valueOf(spDomain.getSelectedItem()));
    }

    /** 앞부분은 썼는데 "이메일 선택"에서 도메인을 안 골랐으면 true */
    public boolean isDomainMissing() {
        return !isEmpty() && PLACEHOLDER.equals(String.valueOf(spDomain.getSelectedItem()));
    }

    /** 입력한 주소 (소문자). 비어 있으면 "", 형식이 틀리거나 도메인을 안 골랐으면 null */
    public String email() {
        if (isEmpty()) {
            return "";
        }
        if (isDomainMissing()) {
            return null;
        }
        String domain = isDirect()
                ? etDirect.getText().toString().trim()
                : String.valueOf(spDomain.getSelectedItem());
        return LocalAccounts.normalizeEmail(etLocal.getText().toString().trim() + "@" + domain);
    }

    /** pattern(글자 하나)에 맞지 않는 글자는 입력에서 뺀다 */
    private static InputFilter allowOnly(String pattern) {
        return (source, start, end, dest, dstart, dend) -> {
            StringBuilder kept = new StringBuilder();
            for (int i = start; i < end; i++) {
                String c = String.valueOf(source.charAt(i));
                if (c.matches(pattern)) {
                    kept.append(c);
                }
            }
            return kept.length() == end - start ? null : kept.toString();
        };
    }

    /** 맨 앞 항목("이메일 선택"·"년도"·"월"·"일")은 칸에만 보이고 펼친 목록에서는 숨긴다 */
    public static ArrayAdapter<String> placeholderAdapter(Context context, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                context,
                android.R.layout.simple_spinner_item,
                items
        ) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                // 숨긴 칸을 다른 줄이 다시 쓰지 않게 매번 새로 만든다
                View view = super.getDropDownView(position, null, parent);
                if (position == 0) {
                    view.setVisibility(View.GONE);
                    view.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }
}
