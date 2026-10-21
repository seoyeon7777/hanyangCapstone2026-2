
package com.example.capstone.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.avatar.BodyMeasurementContract;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.login.LoginSession;

import java.util.List;

public class BodyFeedbackActivity extends AppCompatActivity {

    public static final String EXTRA_PROFILE_NAME = "profile_name";
    public static final String EXTRA_PROFILE_ID = "profile_id";
    public static final String EXTRA_FROM_MY_BODY = "from_my_body";

    public static Intent profileIntent(Context context, BodyProfileStore.Profile profile) {
        return new Intent(context, BodyFeedbackActivity.class)
                .putExtra(EXTRA_PROFILE_NAME, profile.name)
                .putExtra(EXTRA_PROFILE_ID, profile.id)
                .putExtra(EXTRA_FROM_MY_BODY, true);
    }

    public static Intent surveyIntent(Context context) {
        return new Intent(context, BodyFeedbackActivity.class)
                .putExtra(EXTRA_FROM_MY_BODY, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_body_feedback);

        BodyProfileStore.Profile profile = findProfile(
                getIntent().getStringExtra(EXTRA_PROFILE_ID),
                getIntent().getStringExtra(EXTRA_PROFILE_NAME));

        boolean fullAnalysis = profile != null
                && profile.parts != null
                && profile.parts.length() > 0;

        String type = profile == null ? null : skeletonType(profile.bodyType);

        if (type == null) {
            type = surveyBodyType();
        }

        showHeadline(type);

        if (type != null) {
            showTypeContent(type);
        }

        if (fullAnalysis) {
            findViewById(R.id.layoutFullDetail).setVisibility(View.VISIBLE);
            showMyValues(profile);
        } else {
            findViewById(R.id.layoutCameraCta).setVisibility(View.VISIBLE);
            findViewById(R.id.btnCameraFitting).setOnClickListener(v -> openCameraFitting());
            KoreanLineBreak.apply(findViewById(R.id.tvCameraCtaDesc));
        }

        KoreanLineBreak.apply(findViewById(R.id.tvFeedbackNotice));

        findViewById(R.id.btnClose)
                .setOnClickListener(v -> close());

        getOnBackPressedDispatcher().addCallback(
                this, new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        close();
                    }
                });
    }

    private void showHeadline(String type) {
        String name = LoginSession.getName(this).trim();

        ((TextView) findViewById(R.id.tvUserName)).setText(
                name.isEmpty() ? "회원님은" : name + " 님은");

        TextView tvHeadline = findViewById(R.id.tvTypeHeadline);

        if (type == null) {
            tvHeadline.setText("아직 체형 분석 전이에요");
            return;
        }

        String typeText = type + " 체형";
        SpannableStringBuilder text = new SpannableStringBuilder(typeText + " 입니다");
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, typeText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new UnderlineSpan(), 0, typeText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvHeadline.setText(text);
    }

    private void showTypeContent(String type) {
        TextView tvTypeDescription = findViewById(R.id.tvTypeDescription);
        TextView tvStyle = findViewById(R.id.tvStyle);
        TextView tvTops = findViewById(R.id.tvTops);
        TextView tvBottoms = findViewById(R.id.tvBottoms);
        TextView tvTip = findViewById(R.id.tvTip);

        switch (type) {
            case "웨이브":
                tvTypeDescription.setText(
                        "부드러운 신체 윤곽과 곡선적인 실루엣이\n" +
                                "특징으로 설명되는 체형이에요.");

                tvStyle.setText("허리선을 강조하는 스타일을 참고해 보세요.");

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
                tvTypeDescription.setText(
                        "골격과 관절의 윤곽이 비교적 두드러지는\n" +
                                "특징으로 설명되는 체형이에요.");

                tvStyle.setText("여유 있는 실루엣을 참고해 보세요.");

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
                tvTypeDescription.setText(
                        "상체의 입체감과 비교적 곧은 실루엣이\n" +
                                "특징으로 설명되는 체형이에요.");

                tvStyle.setText("깔끔하고 정돈된 스타일을 참고해 보세요.");

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

        KoreanLineBreak.apply(tvTypeDescription);
        KoreanLineBreak.apply(tvStyle);
        KoreanLineBreak.apply(tvTip);
    }

    private void showMyValues(BodyProfileStore.Profile profile) {
        BodyValueTable.fill(findViewById(R.id.layoutMyValues),
                BodyValueTable.rows(profile.heightCm, profile.parts, true));

        List<String[]> extraRows = BodyValueTable.extraRows(profile.parts);
        BodyValueTable.fill(findViewById(R.id.layoutMyExtraValues), extraRows);
        findViewById(R.id.layoutMyExtraValues)
                .setVisibility(extraRows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void openCameraFitting() {
        float heightCm = BodyTypeActivity.lastSurveyHeight(this);

        if (heightCm <= 0f) {
            startActivity(new Intent(this, FittingActivity.class));
            return;
        }

        Intent intent = new Intent(this, BodyMeasurementActivity.class);
        intent.putExtra(BodyMeasurementContract.EXTRA_USER_HEIGHT_CM, heightCm);
        startActivity(intent);
    }

    private BodyProfileStore.Profile findProfile(String profileId, String profileName) {
        BodyProfileStore.Profile profile = BodyProfileStore.find(this, profileId);

        if (profile == null && profileName != null) {
            for (BodyProfileStore.Profile p : BodyProfileStore.getProfiles(this)) {
                if (profileName.equals(p.name)) {
                    return p;
                }
            }
        }
        return profile;
    }

    private static String skeletonType(String bodyType) {
        if (bodyType == null) {
            return null;
        }
        switch (bodyType) {
            case "스트레이트":
            case "웨이브":
            case "내추럴":
                return bodyType;
            default:
                return null;
        }
    }

    private String surveyBodyType() {
        switch (BodyTypeActivity.lastSurveyType(this)) {
            case BodyTypeActivity.TYPE_STRAIGHT:
                return "스트레이트";
            case BodyTypeActivity.TYPE_WAVE:
                return "웨이브";
            case BodyTypeActivity.TYPE_NATURAL:
                return "내추럴";
            default:
                return null;
        }
    }

    private void close() {
        finish();
    }
}
