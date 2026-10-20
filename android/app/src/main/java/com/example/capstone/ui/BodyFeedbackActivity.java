
package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;

public class BodyFeedbackActivity extends AppCompatActivity {

    private TextView tvTypeTitle;
    private TextView tvTypeDescription;
    private TextView tvTops;
    private TextView tvBottoms;
    private TextView tvTip;

    private TextView btnStraight;
    private TextView btnWave;
    private TextView btnNatural;

    private String selectedType = "스트레이트";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_body_feedback);

        tvTypeTitle = findViewById(R.id.tvTypeTitle);
        tvTypeDescription = findViewById(R.id.tvTypeDescription);
        tvTops = findViewById(R.id.tvTops);
        tvBottoms = findViewById(R.id.tvBottoms);
        tvTip = findViewById(R.id.tvTip);

        btnStraight = findViewById(R.id.btnStraight);
        btnWave = findViewById(R.id.btnWave);
        btnNatural = findViewById(R.id.btnNatural);

        String profileName =
                getIntent().getStringExtra("profile_name");

        TextView tvProfileName =
                findViewById(R.id.tvProfileName);

        tvProfileName.setText(
                (profileName == null ? "내 아바타" : profileName)
                        + " 저장 완료");

        btnStraight.setOnClickListener(v ->
                showFeedback("스트레이트"));

        btnWave.setOnClickListener(v ->
                showFeedback("웨이브"));

        btnNatural.setOnClickListener(v ->
                showFeedback("내추럴"));

        findViewById(R.id.btnStartShopping)
                .setOnClickListener(v -> goHome());

        findViewById(R.id.btnClose)
                .setOnClickListener(v -> goHome());

        getOnBackPressedDispatcher().addCallback(
                this, new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        goHome();
                    }
                });

        showFeedback(selectedType);
    }

    private void showFeedback(String type) {
        selectedType = type;

        updateChip(btnStraight, type.equals("스트레이트"));
        updateChip(btnWave, type.equals("웨이브"));
        updateChip(btnNatural, type.equals("내추럴"));

        switch (type) {
            case "웨이브":
                tvTypeTitle.setText("웨이브 체형");

                tvTypeDescription.setText(
                        "부드러운 신체 윤곽과 곡선적인 실루엣이 " +
                                "특징으로 설명되는 체형이에요.\n" +
                                "허리선을 강조하는 스타일을 참고해 보세요.");

                tvTops.setText(
                        "크롭 가디건\n" +
                                "허리선이 잡힌 블라우스\n" +
                                "부드러운 소재의 니트\n" +
                                "짧은 기장의 재킷");

                tvBottoms.setText(
                        "하이웨이스트 팬츠\n" +
                                "A라인 스커트\n" +
                                "허리선이 높은 데님\n" +
                                "플레어 스커트");

                tvTip.setText(
                        "상의는 비교적 짧게, 하의는 허리선이 " +
                                "높은 디자인을 선택하면 비율을 " +
                                "강조하는 스타일을 연출할 수 있어요.");
                break;

            case "내추럴":
                tvTypeTitle.setText("내추럴 체형");

                tvTypeDescription.setText(
                        "골격과 관절의 윤곽이 비교적 " +
                                "두드러지는 특징으로 설명되는 체형이에요.\n" +
                                "여유 있는 실루엣을 참고해 보세요.");

                tvTops.setText(
                        "오버핏 셔츠\n" +
                                "루즈핏 니트\n" +
                                "여유로운 후드티\n" +
                                "캐주얼 재킷");

                tvBottoms.setText(
                        "와이드 데님\n" +
                                "루즈핏 슬랙스\n" +
                                "카고 팬츠\n" +
                                "롱스커트");

                tvTip.setText(
                        "몸에 너무 밀착되지 않는 디자인과 " +
                                "자연스럽게 떨어지는 소재를 활용하면 " +
                                "편안한 스타일을 연출할 수 있어요.");
                break;

            case "스트레이트":
            default:
                tvTypeTitle.setText("스트레이트 체형");

                tvTypeDescription.setText(
                        "상체의 입체감과 비교적 곧은 " +
                                "실루엣이 특징으로 설명되는 체형이에요.\n" +
                                "깔끔하고 정돈된 스타일을 참고해 보세요.");

                tvTops.setText(
                        "베이직 티셔츠\n" +
                                "깔끔한 기본 셔츠\n" +
                                "V넥 니트\n" +
                                "테일러드 재킷");

                tvBottoms.setText(
                        "스트레이트 팬츠\n" +
                                "일자 데님\n" +
                                "기본 슬랙스\n" +
                                "H라인 스커트");

                tvTip.setText(
                        "너무 과한 장식보다는 단정한 디자인과 " +
                                "적당한 여유가 있는 핏을 활용하면 " +
                                "균형 잡힌 스타일을 연출할 수 있어요.");
                break;
        }
    }

    private void updateChip(TextView chip, boolean selected) {
        chip.setTextColor(selected ?
                Color.WHITE : Color.BLACK);

        chip.setTypeface(null, selected ?
                Typeface.BOLD : Typeface.NORMAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ?
                Color.BLACK : Color.rgb(245, 245, 245));
        bg.setCornerRadius(dp(24));

        chip.setBackground(bg);
    }

    private int dp(int value) {
        return (int) (value *
                getResources().getDisplayMetrics().density);
    }

    private void goHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
