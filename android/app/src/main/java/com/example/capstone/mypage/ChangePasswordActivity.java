package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** 마이페이지 > 프로필 수정 > 비밀번호 변경 (users) */
public class ChangePasswordActivity extends AppCompatActivity {

    private EditText etCurrentPw, etNewPw, etNewPwConfirm;
    private Button btnSave;

    /** 영문 + 숫자 + 특수문자 포함 8~20자 */
    private static final String PASSWORD_RULE =
            "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,20}$";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        MyPageUi.setupToolbar(this, "비밀번호 변경");
        initViews();
        btnSave.setOnClickListener(v -> savePassword());
    }

    private void initViews() {
        etCurrentPw = findViewById(R.id.etCurrentPw);
        etNewPw = findViewById(R.id.etNewPw);
        etNewPwConfirm = findViewById(R.id.etNewPwConfirm);
        btnSave = findViewById(R.id.btnSave);
    }

    private void savePassword() {
        String currentPw = etCurrentPw.getText().toString();
        String newPw = etNewPw.getText().toString();
        String newPwConfirm = etNewPwConfirm.getText().toString();

        // 모두 비워두면 변경하지 않고 돌아간다.
        if (currentPw.isEmpty() && newPw.isEmpty() && newPwConfirm.isEmpty()) {
            finish();
            return;
        }
        if (!MyPageStore.checkPassword(currentPw)) {
            toast("현재 비밀번호가 일치하지 않습니다.");
            return;
        }
        if (!newPw.matches(PASSWORD_RULE)) {
            toast("새 비밀번호는 영문, 숫자, 특수문자를 포함해 8~20자로 입력해주세요.");
            return;
        }
        if (newPw.equals(currentPw)) {
            toast("현재 비밀번호와 다른 비밀번호를 입력해주세요.");
            return;
        }
        if (!newPw.equals(newPwConfirm)) {
            toast("새 비밀번호가 서로 일치하지 않습니다.");
            return;
        }

        MyPageStore.changePassword(newPw);
        toast("비밀번호가 변경되었습니다.");
        finish();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
