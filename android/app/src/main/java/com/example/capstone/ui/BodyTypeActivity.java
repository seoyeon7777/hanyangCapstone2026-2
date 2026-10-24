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
import com.example.capstone.data.RecommendApi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BodyTypeActivity extends AppCompatActivity {

    private TextView progress;
    private TextView question;

    private MaterialButton an1;
    private MaterialButton an2;
    private MaterialButton an3;
    private Button next;
    private Button prev;

    // 그림 카드 보기 (Q1~Q7)
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

    private int currentQuestion = 0;
    private float userHeightCm;

    // 각 질문에서 선택한 답
    private final int[] selectedAnswers = new int[7];


    private final String[] questions = {

            "Q1. 내 어깨는 어떤 모습에 가까운가요?",

            "Q2. 목은 어떤 모습에 가까운가요?",

            "Q3. 쇄골은 평소에 어떻게 보이나요?",

            "Q4. 손가락이나 무릎을 봤을 때\n뼈마디가 어떤가요?",

            "Q5. 거울로 봤을 때\n어느 쪽이 더 길어 보이나요?",

            "Q6. 내 몸은 전체적으로\n어떤 느낌에 가까운가요?",

            "Q7. 몸 전체의 모양은\n어떤 것과 비슷해 보이나요?"
    };

    /*
     * 그림 카드로 보여 줄 질문 (질문 번호 → 보기 1~3 사진·제목)
     * 여기 없는 질문은 기존 버튼 목록으로 보여 줌
     */
    private static final int[][] CARD_IMAGES = {
            // Q1 어깨
            {R.drawable.img_q1_shoulder_1, R.drawable.img_q1_shoulder_2, R.drawable.img_q1_shoulder_3},
            // Q2 목
            {R.drawable.img_q2_neck_1, R.drawable.img_q2_neck_2, R.drawable.img_q2_neck_3},
            // Q3 쇄골
            {R.drawable.img_q3_collarbone_1, R.drawable.img_q3_collarbone_2, R.drawable.img_q3_collarbone_3},
            // Q4 뼈마디
            {R.drawable.img_q4_joint_1, R.drawable.img_q4_joint_2, R.drawable.img_q4_joint_3},
            // Q5 상체/하체 길이
            {R.drawable.img_q5_length_1, R.drawable.img_q5_length_2, R.drawable.img_q5_length_3},
            // Q6 전체 느낌
            {R.drawable.img_q6_feel_1, R.drawable.img_q6_feel_2, R.drawable.img_q6_feel_3},
            // Q7 전체 모양
            {R.drawable.img_q7_shape_1, R.drawable.img_q7_shape_2, R.drawable.img_q7_shape_3}
    };

    private static final String[][] CARD_TITLES = {
            {"둥글고 도톰해 보여요", "얇고 부드러워 보여요", "넓고 각진 느낌이에요"},
            {"짧고 굵어 보여요", "길고 가늘어 보여요", "그 중간인 것 같아요"},
            {"거의 보이지 않아요", "조금 보여요", "선명하게 보여요"},
            {"크게 튀어나와\n보이지 않아요", "보통이에요", "뼈마디가 눈에 잘 보여요"},
            {"다리가 더 길어 보여요", "비슷해 보여요", "상체가 더 길어 보여요"},
            {"두께감이 있고\n볼륨감 있어 보여요", "얇고 부드럽게 보여요", "몸의 선보다\n뼈대가 더 눈에 띄어요"},
            {"둥근 모양", "아래가 넓은 모양", "위가 넓은 모양"}
    };

    private final String[][] answers = {

            {
                    "① 둥글고 도톰해 보여요",
                    "② 얇고 부드러워 보여요",
                    "③ 넓고 각진 느낌이에요"
            },

            {
                    "① 짧고 굵어 보여요",
                    "② 길고 가늘어 보여요",
                    "③ 그 중간인 것 같아요"
            },

            {
                    "① 거의 보이지 않아요",
                    "② 조금 보여요",
                    "③ 선명하게 보여요"
            },

            {
                    "① 크게 튀어나와 보이지 않아요",
                    "② 보통이에요",
                    "③ 뼈마디가 눈에 잘 보여요"
            },

            {
                    "① 다리가 더 길어 보여요",
                    "② 비슷해 보여요",
                    "③ 상체가 더 길어 보여요"
            },

            {
                    "① 두께감이 있고 볼륨감 있어 보여요",
                    "② 얇고 부드럽게 보여요",
                    "③ 몸의 선보다 뼈대가 더 눈에 띄어요"
            },

            {
                    "① 둥근 모양",
                    "② 아래가 넓은 모양",
                    "③ 위가 넓은 모양"
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
        question = findViewById(R.id.question);

        an1 = findViewById(R.id.an1);
        an2 = findViewById(R.id.an2);
        an3 = findViewById(R.id.an3);

        next = findViewById(R.id.next);
        prev = findViewById(R.id.prev);

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

        findViewById(R.id.btnCloseSurvey).setOnClickListener(v -> confirmClose());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmClose();
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

        if (currentQuestion == 6) {
            next.setText("결과 확인");
        } else {
            next.setText("다음");
        }

        prev.setEnabled(currentQuestion > 0);
        prev.setAlpha(currentQuestion > 0 ? 1f : 0.4f);

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

    private void updateProgress() {

        StringBuilder text = new StringBuilder();

        for (int i = 0; i < 7; i++) {

            if (i == currentQuestion) {
                text.append("● ");
            } else {
                text.append("○ ");
            }
        }

        progress.setText(text.toString().trim());
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

        if (currentQuestion < 6) {

            currentQuestion++;

            showQuestion();

        } else {

            showResult();
        }
    }

    /**
     * 체형 진단은 서버(A 파트 body_profile.py) 한 곳에서만 한다.
     * 앱이 따로 채점하면 결과 화면과 홈 추천의 체형이 달라질 수 있어서 (30명 중 6명이 달랐음) 앱 채점은 없앴다.
     */
    private void showResult() {

        next.setEnabled(false);
        next.setText("진단 중...");

        // 답부터 저장한다 (서버가 꺼져 있어도 다시 시도하거나 홈에서 진단할 수 있게).
        // 체형은 서버 진단이 오면 저장한다.
        saveLastSurvey(-1);

        RecommendApi.diagnose(
                this,
                selectedAnswers.clone(),
                RecommendApi.cameraMeasurements(this),
                new RecommendApi.Callback<RecommendApi.Diagnosis>() {

                    @Override
                    public void onSuccess(RecommendApi.Diagnosis result) {

                        if (isFinishing()) {
                            return;
                        }

                        int resultType = typeOf(result.primary);

                        saveLastSurvey(resultType);

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
                        next.setText("결과 확인");

                        AppDialog.show(
                                BodyTypeActivity.this,
                                true,
                                "체형 진단을 하지 못했어요",
                                message + "\n답은 저장해 두었어요. 추천 서버가 켜져 있는지 확인하고 다시 시도해 주세요.",
                                "나중에",
                                BodyTypeActivity.this::finish,
                                "다시 시도",
                                BodyTypeActivity.this::showResult
                        );
                    }
                }
        );
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

    private static final String PREF_NAME = "avatar_input";
    private static final String KEY_SURVEY_ANSWERS = "last_survey_answers";
    private static final String KEY_SURVEY_TYPE = "last_survey_type";
    private static final String KEY_SURVEY_HEIGHT = "last_survey_height";
    private static final String KEY_SURVEY_AT = "last_survey_at";

    private void saveLastSurvey(int resultType) {

        StringBuilder answers = new StringBuilder();

        for (int i = 0; i < selectedAnswers.length; i++) {
            if (i > 0) {
                answers.append(",");
            }
            answers.append(selectedAnswers[i]);
        }

        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_SURVEY_ANSWERS, answers.toString())
                .putFloat(KEY_SURVEY_HEIGHT, userHeightCm)
                .putLong(KEY_SURVEY_AT, System.currentTimeMillis())
                .apply();

        // 체형은 서버 진단 결과만 저장한다 (-1 = 아직 진단 전)
        SharedPreferences.Editor editor =
                getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();

        if (resultType < 0) {
            editor.remove(KEY_SURVEY_TYPE);
        } else {
            editor.putInt(KEY_SURVEY_TYPE, resultType);
        }

        editor.apply();
    }

    private void loadLastAnswers() {

        String[] parts = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .getString(KEY_SURVEY_ANSWERS, "")
                .split(",");

        for (int i = 0; i < selectedAnswers.length && i < parts.length; i++) {
            try {
                int answer = Integer.parseInt(parts[i].trim());
                selectedAnswers[i] = answer >= 1 && answer <= 3 ? answer : 0;
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public static boolean hasLastSurvey(Context context) {

        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .contains(KEY_SURVEY_ANSWERS);
    }

    /**
     * 최근 설문의 7문항 답 (1·2·3). 설문 기록이 없거나 빠진 문항이 있으면 null
     * 추천 서버(RecommendApi)가 체형 진단에 쓴다. 문항 순서와 번호는 서버 A 파트 설문과 같다.
     */
    public static int[] lastSurveyAnswers(Context context) {

        String saved = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SURVEY_ANSWERS, "");

        String[] parts = saved.split(",");

        if (parts.length != 7) {
            return null;
        }

        int[] answers = new int[7];

        for (int i = 0; i < 7; i++) {
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

    public static int lastSurveyType(Context context) {

        SharedPreferences prefs =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        return prefs.contains(KEY_SURVEY_TYPE)
                ? prefs.getInt(KEY_SURVEY_TYPE, TYPE_STRAIGHT)
                : -1;
    }

    /** 최근 설문 저장 시각 (없으면 0). 홈 화면이 설문을 새로 했는지 알아챌 때 쓴다 */
    public static long lastSurveyTime(Context context) {

        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_SURVEY_AT, 0L);
    }

    public static float lastSurveyHeight(Context context) {

        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_SURVEY_HEIGHT, 0f);
    }

    /** 예) "웨이브 체형 · 키 170cm · 10.19 저장" */
    public static String lastSurveySummary(Context context) {

        SharedPreferences prefs =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        SimpleDateFormat format =
                new SimpleDateFormat("MM.dd", Locale.KOREA);

        String type = prefs.contains(KEY_SURVEY_TYPE)
                ? typeName(prefs.getInt(KEY_SURVEY_TYPE, TYPE_STRAIGHT))
                : "진단 전";

        return type
                + " · 키 "
                + Math.round(prefs.getFloat(KEY_SURVEY_HEIGHT, 0f))
                + "cm · "
                + format.format(new Date(prefs.getLong(KEY_SURVEY_AT, 0L)))
                + " 저장";
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