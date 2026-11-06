package com.example.capstone.login;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.example.capstone.R;

import java.security.SecureRandom;

/**
 * 본인확인 인증번호 (아이디 찾기, 비밀번호 재설정).
 *
 * 문자·메일을 실제로 보낼 수 없어서, 만든 인증번호를 안내 창에 보여 준다 (시연용).
 * 안내 창에는 닫기(X) 버튼만 있다. 나중에 메일 발송이 생기면 show()만 빼면 된다.
 *
 * - 인증번호는 4자리, 5분 동안만 유효하다
 * - 인증번호를 받은 연락처(target)와 확인할 때의 연락처가 같아야 통과한다
 * - 화면(Activity)마다 하나씩 만들어 쓴다
 */
final class AuthCode {

    static final int LENGTH = 4;
    private static final long VALID_MILLIS = 5 * 60 * 1000L;

    private static final SecureRandom RANDOM = new SecureRandom();

    private String code;
    private String target;
    private long expiresAt;

    private TextView timerView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::updateTimer;

    /** 남은 시간을 보여 줄 글자칸. 인증번호를 받으면 보이고 1초마다 줄어든다 */
    void bindTimer(TextView view) {
        timerView = view;
    }

    /** 인증번호를 새로 만들어 안내 창으로 보여 준다. 전에 만든 번호는 못 쓴다 */
    void issue(Activity activity, String target, String shownTarget) {
        this.code = String.format("%0" + LENGTH + "d", RANDOM.nextInt((int) Math.pow(10, LENGTH)));
        this.target = target;
        this.expiresAt = System.currentTimeMillis() + VALID_MILLIS;   // 다시 받으면 시간도 처음부터
        updateTimer();
        show(activity, shownTarget);
    }

    boolean isIssued() {
        return code != null;
    }

    /** 맞으면 null, 아니면 화면에 보여 줄 문구 */
    String check(String input, String target) {
        if (code == null) {
            return "먼저 인증받기를 눌러주세요.";
        }
        if (target == null || !target.equals(this.target)) {
            return "인증번호를 다시 받아주세요.";
        }
        if (System.currentTimeMillis() > expiresAt) {
            return "인증번호가 만료되었어요. 다시 받아주세요.";
        }
        if (input == null || !input.equals(code)) {
            return "인증번호가 일치하지 않습니다.";
        }
        return null;
    }

    /** 다시 인증받아야 하는 상태로 되돌린다 */
    void clear() {
        code = null;
        target = null;
        expiresAt = 0;
        updateTimer();
    }

    /** 남은 시간을 mm:ss로 보여 준다 (끝나면 00:00). 인증번호가 없으면 숨긴다 */
    private void updateTimer() {
        handler.removeCallbacks(tick);

        if (timerView == null) {
            return;
        }
        if (code == null) {
            timerView.setVisibility(View.GONE);
            return;
        }

        long left = Math.max(0, expiresAt - System.currentTimeMillis());
        long seconds = (left + 999) / 1000;
        timerView.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
        timerView.setVisibility(View.VISIBLE);

        if (left > 0) {
            handler.postDelayed(tick, 500);
        }
    }

    private void show(Activity activity, String shownTarget) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_auth_code, null, false);
        ((TextView) view.findViewById(R.id.tvAuthCode)).setText(code);
        ((TextView) view.findViewById(R.id.tvAuthMessage)).setText(
                shownTarget + "(으)로 인증번호를 보냈어요.\n5분 안에 입력해주세요.\n(시연용: 문자·메일 대신 이 창에 보여 줘요)");

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(view)
                .setCancelable(false)       // 닫기(X) 버튼으로만 닫는다
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        view.findViewById(R.id.btnAuthClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /** 010-1234-5678 → 010-****-5678 */
    static String maskPhone(String phone) {
        if (phone == null) {
            return "";
        }
        String[] parts = phone.split("-");
        if (parts.length == 3) {
            return parts[0] + "-****-" + parts[2];
        }
        return phone;
    }

    /** example@gmail.com → ex*****@gmail.com */
    static String maskEmail(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        int keep = Math.min(2, at);
        StringBuilder sb = new StringBuilder(email.substring(0, keep));
        for (int i = keep; i < at; i++) {
            sb.append('*');
        }
        return sb.append(email.substring(at)).toString();
    }
}
