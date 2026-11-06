package com.example.capstone.mypage;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.data.AccountPrefs;
import com.example.capstone.data.CartStore;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.data.LocalAccounts;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.ProfilePhotoStore;
import com.example.capstone.login.AuthCode;
import com.example.capstone.login.PhoneInput;
import com.example.capstone.login.LoginSession;
import com.example.capstone.ui.AppDialog;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.net.Uri;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.util.Patterns;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.Locale;
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
        // 이메일을 등록했을 때만 삭제 버튼을 보여 준다
        findViewById(R.id.btnDeleteEmail).setVisibility(
                user.email != null && !user.email.isEmpty() ? View.VISIBLE : View.GONE);

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

        findViewById(R.id.btnEditName).setOnClickListener(v -> editName());
        findViewById(R.id.btnEditEmail).setOnClickListener(v -> editEmail());
        findViewById(R.id.btnEditPhone).setOnClickListener(v -> editPhone());
        findViewById(R.id.btnDeleteEmail).setOnClickListener(v -> deleteEmail());

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
                "변경", name -> {
                    if (name.isEmpty()) {
                        toast("이름을 입력해주세요.");
                        return false;
                    }
                    if (name.length() > 20) {
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

    /** 이메일 등록·변경: 새 이메일을 인증받아야 저장된다 */
    private void editEmail() {
        MyPageStore.User user = MyPageStore.getUser();
        boolean registered = user.email != null && !user.email.isEmpty();

        showVerifyDialog(
                registered ? "이메일 변경" : "이메일 등록",
                "주문 안내와 소식을 받을 이메일이에요.\n인증을 받아야 저장돼요.",
                "example@gmail.com", false,
                registered ? user.email : "",
                user.email,
                input -> {
                    String email = cleanEmail(input);
                    return Patterns.EMAIL_ADDRESS.matcher(email).matches() ? email : null;
                },
                "이메일 형식이 올바르지 않습니다.",
                "이미 등록된 이메일입니다.",
                "이미 가입된 이메일입니다.",
                AuthCode::maskEmail,
                email -> {
                    saveProfile(user.name, email, user.phone);
                    toast("이메일이 변경되었습니다.");
                });
    }

    /** 휴대폰 번호 변경: 새 번호를 인증받아야 저장된다 */
    private void editPhone() {
        MyPageStore.User user = MyPageStore.getUser();

        showVerifyDialog(
                "휴대폰 번호 변경",
                "주문·배송 안내를 받을 번호예요.\n새 번호로 인증을 받아야 저장돼요.",
                "010-0000-0000", true,
                LocalAccounts.formatPhone(user.phone),
                user.phone,
                LocalAccounts::normalizePhone,
                "휴대폰 번호를 정확히 입력해주세요.",
                "이미 등록된 번호입니다.",
                "이미 가입된 휴대폰 번호입니다.",
                AuthCode::maskPhone,
                phone -> {
                    saveProfile(user.name, user.email, phone);
                    toast("휴대폰 번호가 변경되었습니다.");
                });
    }

    /** 이메일 삭제: 등록을 없앤다 (이메일은 선택 항목이라 없어도 된다) */
    private void deleteEmail() {
        MyPageStore.User user = MyPageStore.getUser();

        AppDialog.show(this, false, "이메일 삭제",
                "등록한 이메일을 삭제할까요?",
                "취소", null,
                "삭제", () -> {
                    saveProfile(user.name, "", user.phone);
                    toast("이메일이 삭제되었습니다.");
                });
    }

    /**
     * 이메일·휴대폰 번호 변경용 인증 창: 입력 → 인증받기(인증번호는 안내 창) → 인증번호 입력 → 변경.
     * 인증번호는 4자리·5분 유효, 재전송은 언제든 가능(시간은 처음부터).
     *
     * @param normalize  입력을 저장·비교용 값으로 바꾼다 (올바르지 않으면 null)
     * @param current    지금 등록된 값 (같은 값으로는 못 바꾼다)
     * @param onVerified 인증을 마친 새 값을 저장한다
     */
    private void showVerifyDialog(String title, String desc, String hint, boolean phone,
                                  String initialText, String current,
                                  Function<String, String> normalize,
                                  String invalidMessage, String sameMessage, String takenMessage,
                                  Function<String, String> mask,
                                  Consumer<String> onVerified) {

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_contact_verify, null, false);
        ((TextView) view.findViewById(R.id.tvContactDialogTitle)).setText(title);
        ((TextView) view.findViewById(R.id.tvContactDialogDesc)).setText(desc);

        EditText etContact = view.findViewById(R.id.etDialogContact);
        EditText etCode = view.findViewById(R.id.etDialogCode);
        Button btnSend = view.findViewById(R.id.btnDialogSendCode);

        etContact.setHint(hint);
        if (phone) {
            PhoneInput.attach(etContact);           // 숫자만 받고 하이픈은 자동으로 붙는다
        } else {
            etContact.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        }
        if (initialText != null && !initialText.isEmpty()) {
            etContact.setText(initialText);
            etContact.setSelection(etContact.getText().length());
        }

        AuthCode authCode = new AuthCode();
        authCode.bindTimer(view.findViewById(R.id.tvDialogTimer));
        String[] sent = {null};

        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnDismissListener(d -> authCode.clear());     // 남은 시간 갱신을 멈춘다

        // 입력을 고치면 처음부터 다시 인증받아야 한다
        etContact.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (sent[0] != null && !sent[0].equals(normalize.apply(s.toString()))) {
                    authCode.clear();
                    sent[0] = null;
                    etCode.setText("");
                    btnSend.setText("인증받기");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnSend.setOnClickListener(v -> {
            String value = normalize.apply(etContact.getText().toString());
            if (value == null) {
                toast(invalidMessage);
                return;
            }
            if (value.equalsIgnoreCase(current)) {
                toast(sameMessage);
                return;
            }
            // [여기에 DB 입력] 다른 회원이 쓰는 값인지 중복 확인 (users)
            if (LocalAccounts.isTaken(this, LoginSession.getUserId(this), value)) {
                toast(takenMessage);
                return;
            }
            // [여기에 DB 입력] 발송한 인증번호를 만료시각과 함께 저장 (auth_codes)
            etCode.setText("");
            sent[0] = value;
            authCode.issue(this, value, mask.apply(value));
            btnSend.setText("재전송");
        });

        view.findViewById(R.id.btnContactCancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnContactOk).setOnClickListener(v -> {
            if (sent[0] == null) {
                toast("값을 입력하고 인증받기를 눌러주세요.");
                return;
            }
            String input = etCode.getText().toString().trim();
            if (input.length() != AuthCode.LENGTH) {
                toast("인증번호 " + AuthCode.LENGTH + "자리를 입력해주세요.");
                return;
            }
            String error = authCode.check(input, sent[0]);
            if (error != null) {
                toast(error);
                return;
            }
            // [여기에 DB 입력] 사용한 인증번호 만료 처리 (auth_codes)
            String value = sent[0];
            dialog.dismiss();
            onVerified.accept(value);
        });

        dialog.show();
    }

    /** 공백을 없애고 소문자로 */
    private static String cleanEmail(String input) {
        return input.trim().replace(" ", "").toLowerCase(Locale.ROOT);
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
        LocalAccounts.delete(this, LoginSession.getUserId(this));
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
