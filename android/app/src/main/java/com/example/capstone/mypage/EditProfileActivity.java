package com.example.capstone.mypage;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.login.LoginSession;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/** 마이페이지 > 프로필 수정 (users) */
public class EditProfileActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPhone;
    private TextView tvChangePhoto, menuChangePw, tvWithdraw;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        MyPageUi.setupToolbar(this, "프로필 수정");
        initViews();
        loadProfile();
        setListeners();
    }

    private void initViews() {
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        menuChangePw = findViewById(R.id.menuChangePw);
        tvChangePhoto = findViewById(R.id.tvChangePhoto);
        tvWithdraw = findViewById(R.id.tvWithdraw);
        btnSave = findViewById(R.id.btnSave);
    }

    private void loadProfile() {
        // [여기에 DB 입력] 로그인한 회원의 이름/이메일/휴대폰 조회 (users)
        MyPageStore.User user = MyPageStore.getUser();
        etName.setText(user.name);
        etEmail.setText(user.email);
        etPhone.setText(user.phone);
    }

    private void setListeners() {
        tvChangePhoto.setOnClickListener(v ->
                // [여기에 DB 입력] 갤러리에서 고른 프로필 사진 저장 (users)
                Toast.makeText(this, "프로필 사진 변경은 준비 중입니다.", Toast.LENGTH_SHORT).show());

        menuChangePw.setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));

        btnSave.setOnClickListener(v -> saveProfile());

        tvWithdraw.setOnClickListener(v -> MyPageUi.confirm(this, "회원 탈퇴",
                "탈퇴하면 주문 내역, 쿠폰, 리뷰 등 모든 정보가 삭제되며 복구할 수 없습니다.\n정말 탈퇴하시겠습니까?",
                this::withdraw));
    }

    private void saveProfile() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().replaceAll("\\s", "").toLowerCase(Locale.ROOT);
        String phone = MyPageUi.normalizePhone(etPhone.getText().toString());

        if (TextUtils.isEmpty(name)) {
            toast("이름을 입력해주세요.");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("이메일 형식이 올바르지 않습니다.");
            return;
        }
        if (phone == null) {
            toast("휴대폰 번호를 정확히 입력해주세요.");
            return;
        }

        // [여기에 DB 입력] 바꾼 이메일을 다른 회원이 쓰고 있는지 중복 확인 (users)
        MyPageStore.updateProfile(name, email, phone);
        LoginSession.updateProfile(this, name, email, phone);

        toast("프로필이 저장되었습니다.");
        finish();
    }

    private void withdraw() {
        // [여기에 DB 입력] 회원 탈퇴 처리 + 로그인 세션 삭제 (users)
        LoginSession.logout(this);
        MyPageStore.logout();
        toast("탈퇴가 완료되었습니다.");

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
