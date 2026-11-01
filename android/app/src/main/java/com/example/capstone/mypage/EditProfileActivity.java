package com.example.capstone.mypage;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.data.AccountPrefs;
import com.example.capstone.data.AccountStore;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.data.InputRules;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.ProfilePhotoStore;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.data.ShopStore;
import com.example.capstone.login.LoginSession;
import com.example.capstone.ui.AppDialog;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

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
        showValue(tvUserId, LoginSession.getNickname(this));
        showValue(tvName, user.name);
        showValue(tvEmail, user.email);

        String phone = MyPageUi.normalizePhone(user.phone == null ? "" : user.phone);
        showValue(tvPhone, phone != null ? phone : user.phone);
    }

    private void showValue(TextView view, String value) {
        boolean empty = value == null || value.trim().isEmpty();
        view.setText(empty ? "미등록" : value);
        view.setTextColor(empty ? 0xFFAAAAAA : 0xFF111111);
    }

    private void setListeners() {
        findViewById(R.id.tvChangePhoto).setOnClickListener(v -> changePhoto());
        imgProfile.setOnClickListener(v -> changePhoto());

        findViewById(R.id.btnEditName).setOnClickListener(v -> editName());
        findViewById(R.id.btnEditNickname).setOnClickListener(v -> editNickname());
        findViewById(R.id.btnEditEmail).setOnClickListener(v -> editEmail());
        findViewById(R.id.btnEditPhone).setOnClickListener(v -> editPhone());

        findViewById(R.id.menuChangePw).setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));

        TextView tvWithdraw = findViewById(R.id.tvWithdraw);
        tvWithdraw.setPaintFlags(tvWithdraw.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvWithdraw.setOnClickListener(v -> AppDialog.show(this, true,
                "정말 탈퇴하시겠습니까?",
                "탈퇴하면 주문 내역, 쿠폰, 리뷰 등 모든 정보가 삭제되며 복구할 수 없습니다.",
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

    private void editName() {
        MyPageStore.User user = MyPageStore.getUser();

        AppDialog.input(this, "이름 변경", null,
                user.name, "이름을 입력해주세요",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
                "변경", input -> {
                    if (input.trim().isEmpty()) {
                        toast("이름을 입력해주세요.");
                        return false;
                    }
                    String name = InputRules.normalizeName(input);
                    if (name == null) {
                        toast("이름은 20자 이하로 입력해주세요.");
                        return false;
                    }
                    confirmChange("이름 변경", user.name, name, () -> {
                        saveProfile(name, user.email, user.phone);
                        toast("이름이 변경되었습니다.");
                    });
                    return true;
                });
    }

    private void editEmail() {
        MyPageStore.User user = MyPageStore.getUser();

        AppDialog.input(this, "이메일 변경", "주문 안내와 소식을 받을 이메일이에요.",
                user.email, "example@gmail.com",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                "변경", input -> {
                    String email = InputRules.normalizeEmail(input);
                    if (email == null) {
                        toast("이메일 형식이 올바르지 않습니다.");
                        return false;
                    }
                    // [여기에 DB 입력] 다른 회원이 쓰는 이메일인지 중복 확인 + 이메일 인증 후 저장 (users)
                    if (AccountStore.isEmailTakenByOther(this, LoginSession.getUserId(this), email)) {
                        toast("이미 다른 계정에서 쓰는 이메일입니다.");
                        return false;
                    }
                    confirmChange("이메일 변경", user.email, email, () -> {
                        saveProfile(user.name, email, user.phone);
                        toast("이메일이 변경되었습니다.");
                    });
                    return true;
                });
    }

    private void editPhone() {
        MyPageStore.User user = MyPageStore.getUser();

        AppDialog.input(this, "휴대폰 번호 변경", "주문·배송 안내를 받을 번호예요.",
                user.phone, "010-0000-0000", InputType.TYPE_CLASS_PHONE,
                "변경", input -> {
                    String phone = MyPageUi.normalizePhone(input);
                    if (phone == null) {
                        toast("휴대폰 번호를 정확히 입력해주세요.");
                        return false;
                    }
                    if (AccountStore.isPhoneTakenByOther(this, LoginSession.getUserId(this), phone)) {
                        toast("이미 다른 계정에서 쓰는 휴대폰 번호입니다.");
                        return false;
                    }
                    // [여기에 DB 입력] 실제 서비스에서는 바꾼 번호로 본인 인증(문자 인증) 후 저장
                    confirmChange("휴대폰 번호 변경", user.phone, phone, () -> {
                        saveProfile(user.name, user.email, phone);
                        toast("휴대폰 번호가 변경되었습니다.");
                    });
                    return true;
                });
    }

    private void editNickname() {
        String current = LoginSession.getNickname(this);

        AppDialog.input(this, "닉네임 변경", "리뷰와 마이페이지에 보이는 이름이에요.",
                current, "공백 없이 2~10자",
                InputType.TYPE_CLASS_TEXT,
                "변경", input -> {
                    String nickname = InputRules.normalizeNickname(input);
                    if (nickname == null) {
                        toast(InputRules.NICKNAME_ERROR);
                        return false;
                    }
                    confirmChange("닉네임 변경", current, nickname, () -> {
                        MyPageStore.User user = MyPageStore.getUser();
                        if (!AccountStore.updateProfile(this, LoginSession.getUserId(this),
                                user.name, nickname, user.email, user.phone)) {
                            toast("닉네임을 바꾸지 못했어요.");
                            return;
                        }
                        LoginSession.updateNickname(this, nickname);
                        user.nickname = nickname;
                        loadProfile();
                        toast("닉네임이 변경되었습니다.");
                    });
                    return true;
                });
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
        if (!AccountStore.updateProfile(this, LoginSession.getUserId(this),
                name, LoginSession.getNickname(this), email, phone)) {
            toast("정보를 바꾸지 못했어요. 이미 다른 계정에서 쓰는 값인지 확인해주세요.");
            return;
        }
        MyPageStore.updateProfile(name, email, phone);
        LoginSession.updateProfile(this, name, email, phone);

        String after = BodyProfileStore.accountKey(this);
        if (!before.equals(after)) {
            BodyProfileStore.moveAccount(this, before, after);
            ProfilePhotoStore.moveAccount(this, before, after);
            AccountPrefs.moveAccount(this, before, after);
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
        String userId = LoginSession.getUserId(this);
        String account = BodyProfileStore.accountKey(this);
        RecommendApi.forgetAccount(this);
        MyPageStore.deleteAccountData(userId);
        ShopStore.deleteAccountData(userId);
        BodyProfileStore.deleteAccount(this, account);
        ProfilePhotoStore.delete(this);
        getSharedPreferences(PREF_SETTINGS, MODE_PRIVATE).edit()
                .remove(KEY_MARKETING_SMS + account)
                .remove(KEY_MARKETING_EMAIL + account)
                .apply();
        AccountStore.delete(this, userId);
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
