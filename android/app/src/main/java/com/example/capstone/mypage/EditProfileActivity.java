package com.example.capstone.mypage;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.data.AccountData;
import com.example.capstone.data.AccountPrefs;
import com.example.capstone.data.CartStore;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.data.LocalAccounts;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.ProfilePhotoStore;
import com.example.capstone.login.AuthCode;
import com.example.capstone.login.LoginSession;
import com.example.capstone.ui.AppDialog;
import com.example.capstone.ui.EmailDomainInput;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** 마이페이지 > 프로필 수정 (users) */
public class EditProfileActivity extends AppCompatActivity {

    private static final String PREF_SETTINGS = "profile_settings";
    private static final String KEY_MARKETING_SMS = "marketing_sms_";
    private static final String KEY_MARKETING_EMAIL = "marketing_email_";

    private TextView tvUserId, tvName, tvEmail, tvPhone;
    private ImageView imgProfile;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    savePhoto(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        MyPageUi.setupToolbar(this, "프로필 수정");

        tvUserId = findViewById(R.id.tvUserId);
        tvName = findViewById(R.id.tvName);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);
        imgProfile = findViewById(R.id.imgProfile);

        showPhoto();
        loadProfile();
        setListeners();
        setupMarketingSwitches();
    }

    private void loadProfile() {
        // [여기에 DB 입력] 로그인한 회원의 아이디/이름/이메일/휴대폰 조회 (users)
        MyPageStore.User user = MyPageStore.getUser();
        showValue(tvUserId, LoginSession.getUserId(this));
        showValue(tvName, user.name);
        showValue(tvEmail, user.email);

        String phone = MyPageUi.normalizePhone(user.phone == null ? "" : user.phone);
        showValue(tvPhone, phone != null ? com.example.capstone.data.LocalAccounts.formatPhone(phone) : user.phone);
    }

    private void showValue(TextView view, String value) {
        boolean empty = value == null || value.trim().isEmpty();
        view.setText(empty ? "미등록" : value);
        view.setTextColor(empty ? 0xFFAAAAAA : 0xFF111111);
    }

    private void setListeners() {
        findViewById(R.id.tvChangePhoto).setOnClickListener(v -> changePhoto());
        imgProfile.setOnClickListener(v -> changePhoto());

        // 이름은 환불 계좌 예금주로 쓰므로 바꿀 수 없다 (변경 버튼 없음)
        findViewById(R.id.btnEditEmail).setOnClickListener(v -> editEmail());
        // 휴대폰 번호는 가입할 때 본인확인한 번호라 바꿀 수 없다 (변경 버튼 없음)

        findViewById(R.id.menuChangePw).setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));

        TextView tvWithdraw = findViewById(R.id.tvWithdraw);
        tvWithdraw.setPaintFlags(tvWithdraw.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvWithdraw.setOnClickListener(v -> AppDialog.show(this, true,
                "정말 탈퇴하시겠습니까?",
                "탈퇴하면 배송지, 쿠폰, 찜, 체형 등 모든 정보가 삭제되며 복구할 수 없어요.\n단, 주문 내역은 관련 법령에 따라 일정 기간 보관돼요.",
                "취소", null,
                "탈퇴하기", this::withdraw));
    }

    private void changePhoto() {
        if (!ProfilePhotoStore.hasPhoto(this)) {
            openGallery();
            return;
        }
        AppDialog.show(this, false,
                "프로필 사진", "새 사진을 고르거나 기본 이미지로 바꿀 수 있어요.",
                "기본 이미지로", () -> {
                    ProfilePhotoStore.delete(this);
                    showPhoto();
                    toast("기본 이미지로 변경되었습니다.");
                },
                "앨범에서 선택", this::openGallery);
    }

    private void openGallery() {
        pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void savePhoto(Uri uri) {
        new Thread(() -> {
            // [여기에 DB 입력] 갤러리에서 고른 프로필 사진 저장 (users)
            boolean ok = ProfilePhotoStore.save(getApplicationContext(), uri);
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                if (ok) {
                    showPhoto();
                    toast("프로필 사진이 변경되었습니다.");
                } else {
                    toast("사진을 불러오지 못했어요. 다른 사진을 골라 주세요.");
                }
            });
        }).start();
    }

    private void showPhoto() {
        ProfilePhotoStore.showIn(imgProfile, dp(16));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * 이메일 변경: 주소를 [앞부분] @ [도메인]으로 받고, "인증번호 받기"를 누르면 화면에 인증번호 4자리가 나온다.
     * 그 번호를 입력해 인증확인을 눌러야 바뀐다. (메일 서버 설정이 없어 번호를 화면에 보여 주는 시연용 방식)
     */
    private void editEmail() {
        MyPageStore.User user = MyPageStore.getUser();

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_email_change, null, false);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView btnSendMail = view.findViewById(R.id.btnSendMail);
        TextView btnVerify = view.findViewById(R.id.btnVerifyEmail);
        TextView tvNotice = view.findViewById(R.id.tvMailNotice);
        View layoutCode = view.findViewById(R.id.layoutEmailCode);
        EditText etCode = view.findViewById(R.id.etEmailCode);
        AuthCode authCode = new AuthCode();
        authCode.bindTimer(view.findViewById(R.id.tvEmailTimer));
        // 인증번호를 받은 주소. 주소를 고치면 비운다
        String[] sent = {null};

        EmailDomainInput input = new EmailDomainInput(this,
                view.findViewById(R.id.etEmailId),
                view.findViewById(R.id.spEmailDomain),
                view.findViewById(R.id.etEmailDomain),
                () -> {
                    if (sent[0] != null) {
                        sent[0] = null;
                        authCode.clear();
                        etCode.setText("");
                        btnSendMail.setText("인증번호 받기");
                        tvNotice.setVisibility(View.GONE);
                        layoutCode.setVisibility(View.GONE);
                        setVerifyEnabled(btnVerify, false);
                    }
                });
        input.setEmail(user.email);

        btnSendMail.setOnClickListener(v -> {
            String email = input.email();
            if (email == null || email.isEmpty()) {
                toast(input.isDomainMissing() ? "이메일 주소(@ 뒤)를 선택해주세요." : "이메일 형식이 올바르지 않습니다.");
                return;
            }
            if (email.equals(user.email)) {
                toast("지금 등록된 이메일과 같아요.");
                return;
            }
            // [여기에 DB 입력] 다른 회원이 쓰는 이메일인지 중복 확인 (users)
            if (LocalAccounts.isTaken(this, LoginSession.getUserId(this), email)) {
                toast("이미 가입된 이메일입니다.");
                return;
            }
            // 메일 서버가 없어서 인증번호를 안내 창에 보여 준다 (다시 받으면 시간도 처음부터)
            authCode.issue(this, email, AuthCode.maskEmail(email));
            sent[0] = email;
            etCode.setText("");
            btnSendMail.setText("인증번호 다시 받기");
            tvNotice.setText("안내 창에 보이는 인증번호 4자리를 입력해 주세요. (5분 동안 유효)");
            tvNotice.setVisibility(View.VISIBLE);
            layoutCode.setVisibility(View.VISIBLE);
            setVerifyEnabled(btnVerify, true);
        });

        btnVerify.setOnClickListener(v -> {
            if (sent[0] == null) {
                return;
            }
            String error = authCode.check(etCode.getText().toString().trim(), sent[0]);
            if (error != null) {
                toast(error);
                return;
            }
            authCode.hideTimer();
            saveProfile(user.name, sent[0], user.phone);
            toast("이메일이 변경되었습니다.");
            dialog.dismiss();
        });

        view.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private static void setVerifyEnabled(TextView button, boolean enabled) {
        button.setEnabled(enabled);
        button.setBackgroundResource(enabled ? R.drawable.bg_dialog_btn_dark : R.drawable.bg_button_gray);
    }

    private void confirmChange(String title, String before, String after, Runnable onConfirm) {
        if (after.equals(before)) {
            return;
        }
        AppDialog.show(this, false, title,
                "'" + after + "'(으)로 변경하시겠습니까?",
                "취소", null,
                "변경", onConfirm);
    }

    private void saveProfile(String name, String email, String phone) {
        String before = BodyProfileStore.accountKey(this);

        // [여기에 DB 입력] 회원 정보 수정 (users)
        MyPageStore.updateProfile(name, email, phone);
        LoginSession.updateProfile(this, name, email, phone);
        LocalAccounts.updateProfile(this, LoginSession.getUserId(this), name, email, phone);

        String after = BodyProfileStore.accountKey(this);
        if (!before.equals(after)) {
            BodyProfileStore.moveAccount(this, before, after);
            ProfilePhotoStore.moveAccount(this, before, after);
            AccountPrefs.moveAccount(this, before, after);
            CartStore.moveAccount(this, before, after);
            MyPageStore.moveAccount(this, before, after);
            moveMarketingSettings(before, after);
            setupMarketingSwitches();
        }

        loadProfile();
    }

    private void setupMarketingSwitches() {
        // [여기에 DB 입력] 마케팅 수신 동의 여부 조회·저장 (users). 지금은 기기에만 저장
        SharedPreferences prefs = getSharedPreferences(PREF_SETTINGS, MODE_PRIVATE);
        String account = BodyProfileStore.accountKey(this);

        bindSwitch(findViewById(R.id.switchMarketingSms), prefs,
                KEY_MARKETING_SMS + account, "문자(SMS)");
        bindSwitch(findViewById(R.id.switchMarketingEmail), prefs,
                KEY_MARKETING_EMAIL + account, "이메일");
    }

    private void moveMarketingSettings(String from, String to) {
        SharedPreferences prefs = getSharedPreferences(PREF_SETTINGS, MODE_PRIVATE);
        SharedPreferences.Editor e = prefs.edit();
        for (String key : new String[]{KEY_MARKETING_SMS, KEY_MARKETING_EMAIL}) {
            if (prefs.contains(key + from)) {
                e.putBoolean(key + to, prefs.getBoolean(key + from, false)).remove(key + from);
            }
        }
        e.apply();
    }

    private void bindSwitch(SwitchCompat sw, SharedPreferences prefs, String key, String label) {
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(prefs.getBoolean(key, false));
        sw.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            toast("마케팅 정보 " + label + " 수신을 " + (checked ? "동의" : "거부") + "했어요.");
        });
    }

    private void withdraw() {
        // [여기에 DB 입력] 회원 탈퇴 처리 + 로그인 세션 삭제 (users)
        // 이 계정으로 기기에 쌓인 기록(주문·배송지·장바구니·체형·설문·찜·사진 ...)을 모두 지우고, 회원 정보도 지운다
        Set<String> accounts = AccountData.accountNames(this);
        AccountData.deleteAll(this, accounts);
        removeMarketingSettings(accounts);
        LocalAccounts.delete(this, LoginSession.getUserId(this));
        LoginSession.logout(this);
        MyPageStore.logout();
        toast("탈퇴가 완료되었습니다.");

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void removeMarketingSettings(Set<String> accounts) {
        SharedPreferences.Editor editor = getSharedPreferences(PREF_SETTINGS, MODE_PRIVATE).edit();
        for (String account : accounts) {
            editor.remove(KEY_MARKETING_SMS + account);
            editor.remove(KEY_MARKETING_EMAIL + account);
        }
        editor.apply();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
