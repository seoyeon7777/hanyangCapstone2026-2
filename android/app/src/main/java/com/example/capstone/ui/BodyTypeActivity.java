package com.example.capstone.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.data.AccountPrefs;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.data.GuestSurvey;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.login.LoginSession;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BodyTypeActivity extends AppCompatActivity {

    private TextView progress;
    private TextView progressNotice;
    private TextView progressPercent;
    private LinearProgressIndicator progressBar;
    private TextView question;

    private MaterialButton an1;
    private MaterialButton an2;
    private MaterialButton an3;
    private Button next;
    private Button prev;
    private Button btnEarlyResult;

    // 그림 카드 보기 (사진이 있는 문항만, 지금은 없음)
    private View cardOptions;
    private MaterialCardView[] cards;
    private View[] cardRadios;
    private ImageView[] cardImages;
    private TextView[] cardTitles;

    // 체형 종류
    public static final int TYPE_STRAIGHT = 0;
    public static final int TYPE_WAVE = 1;
    public static final int TYPE_NATURAL = 2;

    // 결과 화면으로 전달할 값
    public static final String EXTRA_RESULT_TYPE = "result_type";
    // 서버 진단 비율 (0~1)과 혼합형 여부
    public static final String EXTRA_RATIO_S = "ratio_s";
    public static final String EXTRA_RATIO_W = "ratio_w";
    public static final String EXTRA_RATIO_N = "ratio_n";
    public static final String EXTRA_MIXED = "is_mixed";
    public static final String EXTRA_ANSWERS = "survey_answers";

    private int currentQuestion = 0;
    private float userHeightCm;

    // 각 질문에서 선택한 답 (1 = A, 2 = B, 3 = C)
    private final int[] selectedAnswers = new int[BodyProfileStore.SURVEY_MAX_ANSWERS];

    /** 이 문항부터 "여기까지 하고 결과보기"를 보여 준다 (7번 = 0부터 세어 6) */
    private static final int EARLY_RESULT_FROM = BodyProfileStore.SURVEY_MIN_ANSWERS - 1;
    /** 이 문항부터 "조금만 더 하면 설문이 완성돼요!" (10~12번) */
    private static final int ALMOST_DONE_FROM = 9;
    /** 이 문항부터 "설문 3번만 더 하시면…" (13~15번, 10번 문구 대신) */
    private static final int LAST_THREE_FROM = 12;

    /** 결과 확인을 누를 때 서버로 보낼 문항 수 (다시 시도할 때도 같은 개수) */
    private int resultCount = BodyProfileStore.SURVEY_MAX_ANSWERS;


    private final String[] questions = {
            "Q1. 목의 길이와 특징은?",

            "Q2. 쇄골은 어떻게 보이나?",

            "Q3. 어깨의 특징은?",

            "Q4. 상체를 옆에서 보면?",

            "Q5. 가슴과 허리의 위치는?",

            "Q6. 허리의 특징은?",

            "Q7. 골반과 엉덩이의 특징은?",

            "Q8. 팔과 다리의 특징은?",

            "Q9. 손목의 특징은?",

            "Q10. 손과 손가락의 특징은?",

            "Q11. 무릎과 발목의 특징은?",

            "Q12. 살이 찌면 어디에 먼저 붙는 편인가?",

            "Q13. 피부와 살의 촉감은?",

            "Q14. 몸 전체의 실루엣은?",

            "Q15. 어떤 옷에서 체형의 장점이 잘 드러나나?"
    };

    /*
     * 그림 카드로 보여 줄 질문 (질문 번호 → 보기 1~3 사진·제목)
     * 지금은 사진이 없어 모든 문항을 글자 버튼으로 보여 준다. 사진이 생기면 여기에 문항 순서대로 넣는다
     */
    private static final int[][] CARD_IMAGES = {};

    private static final String[][] CARD_TITLES = {};

    private final String[][] answers = {
            {
                    "A. 목이 비교적 짧고 탄탄하며 두께감이 있다.",
                    "B. 목이 길고 가늘며 여리여리하다.",
                    "C. 목의 힘줄이나 뼈가 도드라져 보인다."
            },

            {
                    "A. 쇄골이 크게 드러나지 않는다.",
                    "B. 쇄골이 가늘고 선명하게 보인다.",
                    "C. 쇄골이 굵거나 뼈의 윤곽이 뚜렷하다."
            },

            {
                    "A. 어깨가 둥글고 두께감이 있다.",
                    "B. 어깨가 좁거나 부드럽게 내려간다.",
                    "C. 어깨뼈가 크거나 각진 느낌이 있다."
            },

            {
                    "A. 가슴과 등 쪽에 입체적인 두께감이 있다.",
                    "B. 상체가 얇고 납작한 편이다.",
                    "C. 두께보다 뼈대나 골격이 눈에 띈다."
            },

            {
                    "A. 가슴과 허리선이 비교적 높은 편이다.",
                    "B. 가슴과 허리선이 비교적 낮은 편이다.",
                    "C. 위치보다 전체적인 골격의 직선감이 두드러진다."
            },

            {
                    "A. 허리 위치가 높고 탄탄한 느낌이다.",
                    "B. 허리가 길어 보이고 곡선이 부드럽다.",
                    "C. 허리 굴곡보다 골반뼈나 골격이 눈에 띈다."
            },

            {
                    "A. 엉덩이가 높고 입체적으로 볼륨감이 있다.",
                    "B. 엉덩이 볼륨이 아래쪽에 있고 부드럽다.",
                    "C. 골반뼈가 뚜렷하고 직선적인 느낌이다."
            },

            {
                    "A. 살과 근육이 탄탄하고 입체적이다.",
                    "B. 가늘고 부드러우며 살이 말랑하다.",
                    "C. 길거나 관절과 뼈의 윤곽이 두드러진다."
            },

            {
                    "A. 손목이 둥글고 두께감이 있다.",
                    "B. 손목이 가늘고 납작하다.",
                    "C. 손목뼈가 크거나 튀어나와 보인다."
            },

            {
                    "A. 손바닥에 두께감이 있고 손가락이 탄탄하다.",
                    "B. 손이 얇고 손가락이 가늘다.",
                    "C. 손마디가 크고 관절이 뚜렷하다."
            },

            {
                    "A. 관절이 작거나 살에 덮여 둥글게 보인다.",
                    "B. 관절이 작고 가느다란 느낌이다.",
                    "C. 무릎뼈와 복사뼈가 크고 선명하다."
            },

            {
                    "A. 상체, 가슴, 배, 팔뚝",
                    "B. 허벅지, 엉덩이, 하체",
                    "C. 특정 부위보다 전신에 비교적 고르게 붙는다."
            },

            {
                    "A. 탄력 있고 단단한 편이다.",
                    "B. 부드럽고 말랑한 편이다.",
                    "C. 살보다 뼈나 힘줄의 존재감이 크다."
            },

            {
                    "A. 입체적이고 탄탄하며 볼륨감이 있다.",
                    "B. 곡선적이고 부드러우며 섬세하다.",
                    "C. 직선적이고 프레임이 크거나 각진 편이다."
            },

            {
                    "A. 깔끔한 정사이즈 셔츠와 일자핏",
                    "B. 허리선을 강조한 짧은 상의와 부드러운 소재",
                    "C. 여유 있는 셔츠와 루즈한 실루엣"
            }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_body_type_test);
        // FittingActivity에서 입력한 키 받기
        userHeightCm = getIntent().getFloatExtra(
                "USER_HEIGHT_CM",
                0f
        );

        progress = findViewById(R.id.progress);
        progressNotice = findViewById(R.id.progressNotice);
        progressPercent = findViewById(R.id.progressPercent);
        progressBar = findViewById(R.id.progressBar);
        question = findViewById(R.id.question);

        an1 = findViewById(R.id.an1);
        an2 = findViewById(R.id.an2);
        an3 = findViewById(R.id.an3);

        next = findViewById(R.id.next);
        prev = findViewById(R.id.prev);
        btnEarlyResult = findViewById(R.id.btnEarlyResult);

        cardOptions = findViewById(R.id.cardOptions);
        cards = new MaterialCardView[]{
                findViewById(R.id.card1),
                findViewById(R.id.card2),
                findViewById(R.id.card3)
        };
        cardRadios = new View[]{
                findViewById(R.id.cardRadio1),
                findViewById(R.id.cardRadio2),
                findViewById(R.id.cardRadio3)
        };
        cardImages = new ImageView[]{
                findViewById(R.id.cardImage1),
                findViewById(R.id.cardImage2),
                findViewById(R.id.cardImage3)
        };
        cardTitles = new TextView[]{
                findViewById(R.id.cardTitle1),
                findViewById(R.id.cardTitle2),
                findViewById(R.id.cardTitle3)
        };

        // '이전 설문 불러오기'로 들어오면 지난번 답을 미리 선택
        if (getIntent().getBooleanExtra(EXTRA_LOAD_LAST, false)) {
            loadLastAnswers();
        }

        showQuestion();

        an1.setOnClickListener(v -> selectAnswer(1));
        an2.setOnClickListener(v -> selectAnswer(2));
        an3.setOnClickListener(v -> selectAnswer(3));

        for (int i = 0; i < cards.length; i++) {
            int answer = i + 1;
            cards[i].setOnClickListener(v -> selectAnswer(answer));
        }

        next.setOnClickListener(v -> goNext());
        prev.setOnClickListener(v -> goPrev());
        btnEarlyResult.setOnClickListener(v -> showEarlyResult());

        findViewById(R.id.btnCloseSurvey).setOnClickListener(v -> confirmClose());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentQuestion > 0) {
                    goPrev();
                } else {
                    confirmClose();
                }
            }
        });
    }

    private void confirmClose() {
        AppDialog.show(this, true,
                "자가진단을 그만할까요?",
                "지금까지 고른 답은 저장되지 않아요.",
                "그만하기", this::finish,
                "계속하기", null);
    }

    private void goPrev() {
        if (currentQuestion > 0) {
            currentQuestion--;
            showQuestion();
        }
    }

    private void showQuestion() {

        question.setText(questions[currentQuestion]);

        an1.setText(answers[currentQuestion][0]);
        an2.setText(answers[currentQuestion][1]);
        an3.setText(answers[currentQuestion][2]);

        // CARD_IMAGES에 있는 질문은 그림 카드, 없으면 기존 버튼
        boolean isCard = currentQuestion < CARD_IMAGES.length;
        cardOptions.setVisibility(isCard ? View.VISIBLE : View.GONE);
        an1.setVisibility(isCard ? View.GONE : View.VISIBLE);
        an2.setVisibility(isCard ? View.GONE : View.VISIBLE);
        an3.setVisibility(isCard ? View.GONE : View.VISIBLE);

        if (isCard) {
            for (int i = 0; i < cards.length; i++) {
                cardImages[i].setImageResource(CARD_IMAGES[currentQuestion][i]);
                cardImages[i].setContentDescription(CARD_TITLES[currentQuestion][i]);
                cardTitles[i].setText(CARD_TITLES[currentQuestion][i]);
            }
        }

        // 그림 카드 질문은 카드 자리를 위해 제목을 위로 올림
        ViewGroup.MarginLayoutParams questionParams =
                (ViewGroup.MarginLayoutParams) question.getLayoutParams();
        questionParams.topMargin = dp(isCard ? 24f : 45f);
        question.setLayoutParams(questionParams);

        // 그림 카드 질문은 진행 점도 위로 올려 위·아래 간격을 맞춤
        ViewGroup.MarginLayoutParams progressParams =
                (ViewGroup.MarginLayoutParams) progress.getLayoutParams();
        progressParams.topMargin = dp(isCard ? 18f : 28f);
        progress.setLayoutParams(progressParams);

        updateProgress();

        if (currentQuestion == questions.length - 1) {
            next.setText("결과 확인");
        } else {
            next.setText("다음");
        }

        prev.setEnabled(currentQuestion > 0);
        prev.setAlpha(currentQuestion > 0 ? 1f : 0.4f);

        // 7번부터는 지금까지 답한 문항으로 결과를 볼 수 있다 (문항이 많을수록 정확해짐)
        // 마지막 15번은 "결과 확인"이 같은 일을 하므로 숨긴다
        btnEarlyResult.setVisibility(currentQuestion >= EARLY_RESULT_FROM
                && currentQuestion < questions.length - 1 ? View.VISIBLE : View.GONE);

        resetButtonState();
    }

    private void selectAnswer(int answer) {

        selectedAnswers[currentQuestion] = answer;

        resetButtonState();
    }

    private void resetButtonState() {

        int selected = selectedAnswers[currentQuestion];

        styleAnswer(an1, selected, 1);
        styleAnswer(an2, selected, 2);
        styleAnswer(an3, selected, 3);

        for (int i = 0; i < cards.length; i++) {
            styleCard(cards[i], cardRadios[i], selected, i + 1);
        }
    }

    /** 그림 카드: 선택하면 검정 테두리 + 동그라미 채움, 나머지는 흐리게 */
    private void styleCard(MaterialCardView card, View radio, int selected, int answer) {

        boolean isSelected = selected == answer;

        card.setStrokeWidth(isSelected ? dp(3.5f) : 0);
        card.setAlpha(selected == 0 || isSelected ? 1.0f : 0.5f);
        radio.setBackgroundResource(isSelected
                ? R.drawable.bg_q1_radio_on
                : R.drawable.bg_q1_radio_off);
    }

    /** 선택한 답변은 검정 테두리로 강조하고, 나머지는 흐리게 */
    private void styleAnswer(MaterialButton button, int selected, int answer) {

        boolean isSelected = selected == answer;

        button.setStrokeColor(ColorStateList.valueOf(Color.BLACK));
        button.setStrokeWidth(isSelected ? dp(3.5f) : 0);
        button.setAlpha(selected == 0 || isSelected ? 1.0f : 0.5f);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** 질문 위 진행 정도: "3 / 15", 아래 줄에 "20%" + 게이지, 10번·13번부터 응원 문구 */
    private void updateProgress() {

        int number = currentQuestion + 1;
        int total = questions.length;
        int percent = Math.round(number * 100f / total);

        progress.setText(number + " / " + total);
        progressPercent.setText(percent + "%");
        progressBar.setMax(total);
        progressBar.setProgressCompat(number, true);
        progressBar.setIndicatorColor(gaugeColor(currentQuestion));

        String notice = "";
        if (number >= total) {
            notice = "마지막 문항이에요! 답하고 결과를 확인해 보세요";
        } else if (currentQuestion >= LAST_THREE_FROM) {
            notice = "설문 " + (total - currentQuestion) + "번만 더 하시면 더 정확한 결과를 얻게 돼요!";
        } else if (currentQuestion >= ALMOST_DONE_FROM) {
            notice = "조금만 더 하면 설문이 완성돼요!";
        }
        progressNotice.setText(notice);
        progressNotice.setVisibility(notice.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** 게이지 색: 1~9번 검정, 10~12번 초록, 13~14번 주황, 15번 노랑 */
    private int gaugeColor(int index) {
        if (index >= questions.length - 1) {
            return Color.parseColor("#FFC107");
        }
        if (index >= LAST_THREE_FROM) {
            return Color.parseColor("#FF8A00");
        }
        if (index >= ALMOST_DONE_FROM) {
            return Color.parseColor("#2EAD4B");
        }
        return Color.parseColor("#111111");
    }

    private void goNext() {

        if (selectedAnswers[currentQuestion] == 0) {

            Toast.makeText(
                    this,
                    "답변을 선택해주세요.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (currentQuestion < questions.length - 1) {

            currentQuestion++;

            showQuestion();

        } else {

            resultCount = questions.length;
            showResult();
        }
    }

    /** "여기까지 하고 결과보기": 지금 문항까지 고른 답으로 결과를 본다 (7번 문항부터) */
    private void showEarlyResult() {

        if (selectedAnswers[currentQuestion] == 0) {
            Toast.makeText(this, "답변을 선택해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        resultCount = currentQuestion + 1;
        showResult();
    }

    /** 결과에 쓰는 답 = 1번부터 resultCount번까지 */
    private int[] answeredAnswers() {
        return java.util.Arrays.copyOf(selectedAnswers, resultCount);
    }

    /**
     * 체형 진단은 서버(A 파트 body_profile.py) 한 곳에서만 한다.
     * 앱이 따로 채점하면 결과 화면과 홈 추천의 체형이 달라질 수 있어서 (30명 중 6명이 달랐음) 앱 채점은 없앴다.
     */
    private void showResult() {

        next.setEnabled(false);
        btnEarlyResult.setEnabled(false);
        next.setText("진단 중...");

        // 답부터 저장한다 (서버가 꺼져 있어도 다시 시도하거나 홈에서 진단할 수 있게).
        // 체형은 서버 진단이 오면 저장한다.
        saveLastSurvey(-1);

        RecommendApi.diagnose(
                this,
                answeredAnswers(),
                null,   // 카메라 치수는 진단에 안 쓴다 (팀 결정: 설문만)
                new RecommendApi.Callback<RecommendApi.Diagnosis>() {

                    @Override
                    public void onSuccess(RecommendApi.Diagnosis result) {

                        if (isFinishing()) {
                            return;
                        }

                        int resultType = typeOf(result.primary);

                        saveLastSurvey(resultType);

                        // 체형 카드 저장은 결과 화면에서 고른다 (정밀 분석 / 이대로 저장 / 나가기)

                        Intent intent =
                                new Intent(
                                        BodyTypeActivity.this,
                                        BodyTypeResultActivity.class
                                );

                        intent.putExtra(EXTRA_RESULT_TYPE, resultType);
                        intent.putExtra(EXTRA_RATIO_S, result.ratioS);
                        intent.putExtra(EXTRA_RATIO_W, result.ratioW);
                        intent.putExtra(EXTRA_RATIO_N, result.ratioN);
                        intent.putExtra(EXTRA_MIXED, result.mixed);
                        // 비회원이 결과 화면에서 사진 측정 때문에 로그인하면 이 설문도 저장하므로 답을 넘긴다
                        intent.putExtra(EXTRA_ANSWERS, answeredAnswers());

                        // 처음 입력한 키도 결과 화면으로 전달
                        intent.putExtra(
                                "USER_HEIGHT_CM",
                                userHeightCm
                        );

                        startActivity(intent);

                        finish();
                    }

                    @Override
                    public void onError(int code, String message) {

                        if (isFinishing()) {
                            return;
                        }

                        next.setEnabled(true);
                        btnEarlyResult.setEnabled(true);
                        showQuestion();

                        AppDialog.show(
                                BodyTypeActivity.this,
                                true,
                                "체형 진단을 하지 못했어요",
                                message + "\n설문 답변은 저장해 두었어요.",
                                "나가기",
                                BodyTypeActivity.this::finish,
                                "다시 시도",
                                BodyTypeActivity.this::showResult
                        );
                    }
                }
        );
    }

    /**
     * 최근 설문(서버 진단까지 끝난 것)을 '내 체형 관리' 카드로 저장한다. 결과 화면의 "이대로 저장하기"가 부른다.
     * 로그인했을 때만 (체형 카드는 계정에 저장). 저장했으면 그 카드, 아니면 null
     */
    public static BodyProfileStore.Profile saveSurveyProfile(Context context) {

        if (!LoginSession.isLoggedIn(context)) {
            return null;
        }

        int[] answers = lastSurveyAnswers(context);
        int type = lastSurveyType(context);

        if (answers == null || type < 0) {
            return null;     // 답이 없거나 아직 진단 전
        }

        return BodyProfileStore.saveSurvey(context, answers, skeletonLabel(type), lastSurveyHeight(context));
    }

    /** 대표 체형 카드에 저장하는 골격 이름 (MeasurementResultActivity와 같은 글자) */
    static String skeletonLabel(int type) {

        switch (type) {
            case TYPE_WAVE:
                return "웨이브";
            case TYPE_NATURAL:
                return "내추럴";
            case TYPE_STRAIGHT:
            default:
                return "스트레이트";
        }
    }

    /** 서버 체형 코드 S / W / N → TYPE_ 값 */
    private static int typeOf(String primary) {

        switch (primary) {
            case "W":
                return TYPE_WAVE;
            case "N":
                return TYPE_NATURAL;
            case "S":
            default:
                return TYPE_STRAIGHT;
        }
    }

    // =====================================
    // 최근 설문 결과 저장 / 불러오기
    //
    // 가장 최근 설문 1개만 덮어써서 보관 (문항별 답 포함)
    // 피팅 시작 화면의 '이전 설문 불러오기'에서 사용
    // =====================================

    /** true면 최근 설문의 답을 미리 선택한 채로 1번 문항부터 연다 */
    public static final String EXTRA_LOAD_LAST = "load_last_survey";

    // 최근 설문(답·체형·키·시각)은 로그인 계정별로 저장한다 (AccountPrefs, 키 이름은 AccountPrefs.SURVEY_*)

    private void saveLastSurvey(int resultType) {

        // 비회원은 기기에 저장하지 않고 앱이 켜져 있는 동안만 메모리에 둔다 (GuestSurvey)
        if (!LoginSession.isLoggedIn(this)) {
            GuestSurvey.set(answeredAnswers(), userHeightCm, resultType);
            return;
        }

        persistSurvey(this, answeredAnswers(), userHeightCm, resultType);
    }

    /** 설문 답·키·체형을 지금 로그인한 계정에 저장한다 (resultType -1 = 아직 진단 전) */
    public static void persistSurvey(Context context, int[] answerValues, float heightCm, int resultType) {

        StringBuilder answers = new StringBuilder();

        for (int i = 0; i < answerValues.length; i++) {
            if (i > 0) {
                answers.append(",");
            }
            answers.append(answerValues[i]);
        }

        AccountPrefs.survey(context)
                .edit()
                .putString(AccountPrefs.key(context, AccountPrefs.SURVEY_ANSWERS), answers.toString())
                .putFloat(AccountPrefs.key(context, AccountPrefs.SURVEY_HEIGHT), heightCm)
                .putLong(AccountPrefs.key(context, AccountPrefs.SURVEY_AT), System.currentTimeMillis())
                .putInt(AccountPrefs.key(context, AccountPrefs.SURVEY_VERSION), BodyProfileStore.SURVEY_VERSION)
                .apply();

        // 체형은 서버 진단 결과만 저장한다 (-1 = 아직 진단 전)
        SharedPreferences.Editor editor =
                AccountPrefs.survey(context).edit();

        if (resultType < 0) {
            editor.remove(AccountPrefs.key(context, AccountPrefs.SURVEY_TYPE));
        } else {
            editor.putInt(AccountPrefs.key(context, AccountPrefs.SURVEY_TYPE), resultType);
        }

        editor.apply();
    }

    private void loadLastAnswers() {

        int[] last = lastSurveyAnswers(this);

        if (last == null) {
            return;
        }

        for (int i = 0; i < selectedAnswers.length && i < last.length; i++) {
            selectedAnswers[i] = last[i];
        }
    }

    public static boolean hasLastSurvey(Context context) {

        if (!LoginSession.isLoggedIn(context)) {
            return GuestSurvey.has();
        }

        return AccountPrefs.survey(context)
                .contains(AccountPrefs.key(context, AccountPrefs.SURVEY_ANSWERS));
    }

    /**
     * 최근 설문의 답 (1·2·3, 7~15문항 — "여기까지 하고 결과보기"를 누른 곳까지). 설문 기록이 없거나 빠진 문항이 있으면 null
     * 추천 서버(RecommendApi)가 체형 진단에 쓴다. 문항 순서와 번호는 서버 A 파트 설문과 같다.
     */
    public static int[] lastSurveyAnswers(Context context) {

        if (!LoginSession.isLoggedIn(context)) {
            return GuestSurvey.answers();
        }

        String saved = AccountPrefs.survey(context)
                .getString(AccountPrefs.key(context, AccountPrefs.SURVEY_ANSWERS), "");

        // 옛 7문항 답(버전 없음)은 문항 뜻이 달라 쓰지 않는다
        if (AccountPrefs.survey(context).getInt(
                AccountPrefs.key(context, AccountPrefs.SURVEY_VERSION), 1) != BodyProfileStore.SURVEY_VERSION) {
            return null;
        }

        String[] parts = saved.split(",");

        if (parts.length < BodyProfileStore.SURVEY_MIN_ANSWERS
                || parts.length > BodyProfileStore.SURVEY_MAX_ANSWERS) {
            return null;
        }

        int[] answers = new int[parts.length];

        for (int i = 0; i < parts.length; i++) {
            try {
                answers[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }

            if (answers[i] < 1 || answers[i] > 3) {
                return null;
            }
        }

        return answers;
    }

    /**
     * 대표 체형이 없을 때 홈 추천에 쓸 설문 답 (RecommendApi.bodyInput).
     * 로그인한 사람은 '내 체형'에 저장한 체형만 쓰므로 null (설문 후 "저장하지 않고 나가기"를 고르면 홈에도 안 씀).
     * 비로그인은 체형을 저장할 수 없으니 최근 설문 답
     */
    public static int[] homeSurveyAnswers(Context context) {

        return LoginSession.isLoggedIn(context) ? null : lastSurveyAnswers(context);
    }

    public static int lastSurveyType(Context context) {

        if (!LoginSession.isLoggedIn(context)) {
            return GuestSurvey.type();
        }

        SharedPreferences prefs =
                AccountPrefs.survey(context);

        return prefs.contains(AccountPrefs.key(context, AccountPrefs.SURVEY_TYPE))
                ? prefs.getInt(AccountPrefs.key(context, AccountPrefs.SURVEY_TYPE), TYPE_STRAIGHT)
                : -1;
    }

    /** 최근 설문 저장 시각 (없으면 0). 홈 화면이 설문을 새로 했는지 알아챌 때 쓴다 */
    public static long lastSurveyTime(Context context) {

        if (!LoginSession.isLoggedIn(context)) {
            return GuestSurvey.savedAt();
        }

        return AccountPrefs.survey(context)
                .getLong(AccountPrefs.key(context, AccountPrefs.SURVEY_AT), 0L);
    }

    public static float lastSurveyHeight(Context context) {

        if (!LoginSession.isLoggedIn(context)) {
            return GuestSurvey.heightCm();
        }

        return AccountPrefs.survey(context)
                .getFloat(AccountPrefs.key(context, AccountPrefs.SURVEY_HEIGHT), 0f);
    }

    /** 예) "웨이브 체형 · 키 170cm · 10.19 저장" (키를 안 받았으면 "웨이브 체형 · 10.19 저장") */
    public static String lastSurveySummary(Context context) {

        SimpleDateFormat format =
                new SimpleDateFormat("MM.dd", Locale.KOREA);

        if (!LoginSession.isLoggedIn(context)) {
            return (GuestSurvey.type() >= 0 ? typeName(GuestSurvey.type()) : "진단 전")
                    + heightPart(GuestSurvey.heightCm())
                    + " · "
                    + format.format(new Date(GuestSurvey.savedAt()))
                    + " 저장";
        }

        SharedPreferences prefs =
                AccountPrefs.survey(context);

        String type = prefs.contains(AccountPrefs.key(context, AccountPrefs.SURVEY_TYPE))
                ? typeName(prefs.getInt(AccountPrefs.key(context, AccountPrefs.SURVEY_TYPE), TYPE_STRAIGHT))
                : "진단 전";

        return type
                + heightPart(prefs.getFloat(AccountPrefs.key(context, AccountPrefs.SURVEY_HEIGHT), 0f))
                + " · "
                + format.format(new Date(prefs.getLong(AccountPrefs.key(context, AccountPrefs.SURVEY_AT), 0L)))
                + " 저장";
    }

    private static String heightPart(float heightCm) {
        return heightCm > 0f ? " · 키 " + Math.round(heightCm) + "cm" : "";
    }

    /** 최근 설문을 답이 선택된 상태로 1번 문항부터 다시 연다. */
    public static Intent lastSurveyIntent(Context context, float heightCm) {

        Intent intent = new Intent(context, BodyTypeActivity.class);
        intent.putExtra(EXTRA_LOAD_LAST, true);
        intent.putExtra("USER_HEIGHT_CM", heightCm);
        return intent;
    }

    public static String typeName(int type) {

        switch (type) {
            case TYPE_WAVE:
                return "웨이브 체형";
            case TYPE_NATURAL:
                return "내추럴 체형";
            case TYPE_STRAIGHT:
            default:
                return "스트레이트 체형";
        }
    }
}