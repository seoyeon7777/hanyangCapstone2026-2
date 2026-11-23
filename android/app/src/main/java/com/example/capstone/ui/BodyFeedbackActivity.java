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
import com.example.capstone.data.BodyMeasurementContract;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.login.LoginSession;

import java.util.List;

public class BodyFeedbackActivity extends AppCompatActivity {

    public static final String EXTRA_SURVEY_TYPE = "survey_type";
    public static final String EXTRA_PROFILE_NAME = "profile_name";
    public static final String EXTRA_PROFILE_ID = "profile_id";
    public static final String EXTRA_FROM_MY_BODY = "from_my_body";

    public static Intent profileIntent(
            Context context,
            BodyProfileStore.Profile profile) {

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

        String type = profile == null
                ? null
                : skeletonType(profile.bodyType);

        if (type == null) {
            type = skeletonType(
                    getIntent().getStringExtra(EXTRA_SURVEY_TYPE));
        }

        if (type == null) {
            type = surveyBodyType();
        }

        // 체형 결과 및 이미지
        showHeadline(type);
        showImages(type);

        if (type != null) {
            showTypeContent(type);
        }

        // 추가된 액세서리 추천
        updateAccessoryRecommendations(type);

        // 정밀 분석 결과가 저장된 경우
        if (fullAnalysis) {

            findViewById(R.id.layoutFullDetail)
                    .setVisibility(View.VISIBLE);

            showMyValues(profile);

        } else {

            // 신체 사이즈 미등록 사용자
            findViewById(R.id.layoutCameraCta)
                    .setVisibility(View.VISIBLE);

            findViewById(R.id.btnCameraFitting)
                    .setOnClickListener(v -> openCameraFitting());

            KoreanLineBreak.apply(
                    findViewById(R.id.tvCameraCtaDesc));
        }

        KoreanLineBreak.apply(
                findViewById(R.id.tvFeedbackNotice));

        findViewById(R.id.btnClose)
                .setOnClickListener(v -> close());

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        close();
                    }
                });
    }

    // 체형 결과 제목
    private void showHeadline(String type) {

        String name = LoginSession.getName(this).trim();

        ((TextView) findViewById(R.id.tvUserName)).setText(
                name.isEmpty()
                        ? "회원님은"
                        : name + " 님은");

        TextView tvHeadline =
                findViewById(R.id.tvTypeHeadline);

        if (type == null) {
            tvHeadline.setText("아직 체형 분석 전이에요");
            return;
        }

        String typeText = type + " 체형";

        SpannableStringBuilder text =
                new SpannableStringBuilder(
                        typeText + "이에요");

        text.setSpan(
                new StyleSpan(Typeface.BOLD),
                0,
                typeText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        text.setSpan(
                new UnderlineSpan(),
                0,
                typeText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvHeadline.setText(text);
    }

    // 체형별 특징 및 코디 추천
    private void showTypeContent(String type) {

        TextView tvTypeDescription =
                findViewById(R.id.tvTypeDescription);

        TextView tvStyle =
                findViewById(R.id.tvStyle);

        TextView tvTops =
                findViewById(R.id.tvTops);

        TextView tvBottoms =
                findViewById(R.id.tvBottoms);

        TextView tvTip =
                findViewById(R.id.tvTip);

        switch (type) {

            case "웨이브":

                tvTypeDescription.setText(
                        "부드러운 신체 윤곽과 곡선적인 실루엣이\n" +
                                "특징으로 설명되는 체형이에요.");

                tvStyle.setText(
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

                tvTypeDescription.setText(
                        "골격과 관절의 윤곽이 비교적 두드러지는\n" +
                                "특징으로 설명되는 체형이에요.");

                tvStyle.setText(
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

                tvTypeDescription.setText(
                        "상체의 입체감과 비교적 곧은 실루엣이\n" +
                                "특징으로 설명되는 체형이에요.");

                tvStyle.setText(
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

        String features;
        String people;

        switch (type) {

            case "웨이브":

                features =
                        "✓ 가느다란 골격과 부드러운 어깨선\n" +
                                "✓ 긴 목선과 자연스러운 곡선\n" +
                                "✓ 허리선을 강조하는 스타일 참고";

                people = "윈터 · 로제 · 나연 · 이효리";
                break;

            case "내추럴":

                features =
                        "✓ 골격과 관절의 윤곽이 두드러지는 편\n" +
                                "✓ 직각에 가까운 어깨선\n" +
                                "✓ 여유로운 실루엣과 캐주얼 스타일 참고";

                people = "한소희 · 리사 · 카즈하";
                break;

            default:

                features =
                        "✓ 비교적 균형 잡힌 실루엣\n" +
                                "✓ 상체에 입체감이 있는 편\n" +
                                "✓ 어깨선에 맞는 정돈된 핏 참고";

                people = "지효 · 지수 · 조이";
                break;
        }

        ((TextView) findViewById(R.id.tvTypeFeatures))
                .setText(features);

        ((TextView) findViewById(R.id.tvCelebrities))
                .setText(people);

        KoreanLineBreak.apply(tvTypeDescription);
        KoreanLineBreak.apply(tvStyle);
        KoreanLineBreak.apply(tvTip);
    }

    // 체형별 액세서리 추천
    private void updateAccessoryRecommendations(String bodyType) {

        TextView description =
                findViewById(R.id.tvAccessoryDescription);

        TextView necklace =
                findViewById(R.id.tvAccessoryNecklace);

        TextView earrings =
                findViewById(R.id.tvAccessoryEarrings);

        TextView bag =
                findViewById(R.id.tvAccessoryBag);

        TextView shoes =
                findViewById(R.id.tvAccessoryShoes);

        if ("스트레이트".equals(bodyType)) {

            description.setText(
                    "심플한 디자인과 정돈된 실루엣");

            necklace.setText(
                    "쇄골 아래로 내려오는 중간~긴 펜던트 목걸이");

            earrings.setText(
                    "작은 링 귀걸이, 심플한 드롭 귀걸이");

            bag.setText(
                    "각이 잡힌 스퀘어백, 클래식 토트백");

            shoes.setText(
                    "로퍼, 포인티드 토, 깔끔한 펌프스");

        } else if ("웨이브".equals(bodyType)) {

            description.setText(
                    "작고 섬세한 장식과 부드러운 곡선");

            necklace.setText(
                    "짧은 체인, 작은 펜던트, 진주 목걸이");

            earrings.setText(
                    "미니 진주 귀걸이, 섬세한 드롭 귀걸이");

            bag.setText(
                    "미니백, 곡선형 숄더백");

            shoes.setText(
                    "메리제인, 발레 플랫, 스트랩 샌들");

        } else if ("내추럴".equals(bodyType)) {

            description.setText(
                    "볼드한 디자인과 자연스러운 소재감");

            necklace.setText(
                    "굵은 체인, 롱 펜던트, 볼드한 목걸이");

            earrings.setText(
                    "큰 링 귀걸이, 볼드한 실버 이어링");

            bag.setText(
                    "호보백, 빅 숄더백, 오버사이즈 토트백");

            shoes.setText(
                    "청키 로퍼, 워커, 볼륨감 있는 스니커즈");

        } else {

            description.setText(
                    "체형 진단 후 맞춤 액세서리를 추천해 드려요.");

            necklace.setText("심플한 펜던트 목걸이");
            earrings.setText("기본 링 귀걸이");
            bag.setText("데일리 숄더백");
            shoes.setText("편안한 로퍼 또는 스니커즈");
        }

        KoreanLineBreak.apply(description);
        KoreanLineBreak.apply(necklace);
        KoreanLineBreak.apply(earrings);
        KoreanLineBreak.apply(bag);
        KoreanLineBreak.apply(shoes);
    }

    // 체형 이미지 및 스타일링 사진
    private void showImages(String type) {

        String key = "내추럴".equals(type)
                ? "natural"
                : "웨이브".equals(type)
                  ? "wave"
                  : "straight";

        setOptionalImage(
                R.id.imgBodyType,
                "body_" + key);

        for (int i = 1; i <= 3; i++) {

            int viewId = getResources().getIdentifier(
                    "imgStyle" + i,
                    "id",
                    getPackageName());

            setOptionalImage(
                    viewId,
                    "style_" + key + "_" + i);
        }

        for (int i = 1; i <= 3; i++) {
            int viewId = getResources().getIdentifier(
                    "imgCelebrity" + i,
                    "id",
                    getPackageName()
            );

            setOptionalImage(
                    viewId,
                    "celebrity_" + key + "_" + i
            );
        }


        findViewById(R.id.tvBodyImageHint)
                .setVisibility(
                        getResources().getIdentifier(
                                "body_" + key,
                                "drawable",
                                getPackageName()) == 0
                                ? View.VISIBLE
                                : View.GONE);
    }

    private void setOptionalImage(
            int viewId,
            String drawableName) {

        int drawable = getResources().getIdentifier(
                drawableName,
                "drawable",
                getPackageName());

        if (drawable != 0 && viewId != 0) {

            android.widget.ImageView image =
                    findViewById(viewId);

            if (image != null) {
                image.setImageResource(drawable);
            }
        }
    }

    // 저장된 신체 측정값 표시
    private void showMyValues(
            BodyProfileStore.Profile profile) {

        BodyValueTable.fill(
                findViewById(R.id.layoutMyValues),
                BodyValueTable.rows(
                        profile.heightCm,
                        profile.parts,
                        true));

        List<String[]> extraRows =
                BodyValueTable.extraRows(profile.parts);

        BodyValueTable.fill(
                findViewById(R.id.layoutMyExtraValues),
                extraRows);

        findViewById(R.id.layoutMyExtraValues)
                .setVisibility(
                        extraRows.isEmpty()
                                ? View.GONE
                                : View.VISIBLE);
    }

    // 사진 정밀 분석 화면으로 이동 (치수를 재기 직전에 키·몸무게·나이를 받는다)
    private void openCameraFitting() {

        BodyInputDialog.show(this, BodyTypeActivity.lastSurveyHeight(this), height -> {

            Intent intent =
                    new Intent(this, PhotoMeasurementActivity.class);

            intent.putExtra(
                    BodyMeasurementContract.EXTRA_USER_HEIGHT_CM,
                    height);

            startActivity(intent);
        });
    }

    // 저장된 프로필 찾기
    private BodyProfileStore.Profile findProfile(
            String profileId,
            String profileName) {

        BodyProfileStore.Profile profile =
                BodyProfileStore.find(this, profileId);

        if (profile == null && profileName != null) {

            for (BodyProfileStore.Profile p :
                    BodyProfileStore.getProfiles(this)) {

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
