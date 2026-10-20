package com.example.capstone.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.google.android.material.button.MaterialButton;

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

    // 체형 종류
    public static final int TYPE_STRAIGHT = 0;
    public static final int TYPE_WAVE = 1;
    public static final int TYPE_NATURAL = 2;

    // 결과 화면으로 전달할 값
    public static final String EXTRA_RESULT_TYPE = "result_type";
    public static final String EXTRA_SCORES = "scores";

    private int currentQuestion = 0;
    private float userHeightCm;

    // 각 질문에서 선택한 답
    private final int[] selectedAnswers = new int[7];

    /*
     * 각 질문의 답변이 어떤 체형에 해당하는지 설정
     *
     * 0 = 스트레이트
     * 1 = 웨이브
     * 2 = 내추럴
     */
    private final int[][] answerTypeMap = {

            // Q1 어깨
            {TYPE_STRAIGHT, TYPE_WAVE, TYPE_NATURAL},

            // Q2 목
            {TYPE_STRAIGHT, TYPE_WAVE, TYPE_NATURAL},

            // Q3 쇄골
            {TYPE_STRAIGHT, TYPE_WAVE, TYPE_NATURAL},

            // Q4 뼈마디
            {TYPE_STRAIGHT, TYPE_WAVE, TYPE_NATURAL},

            // Q5 상체/하체 길이
            {TYPE_STRAIGHT, TYPE_NATURAL, TYPE_WAVE},

            // Q6 전체 느낌
            {TYPE_STRAIGHT, TYPE_WAVE, TYPE_NATURAL},

            // Q7 전체 모양
            {TYPE_STRAIGHT, TYPE_WAVE, TYPE_NATURAL}
    };

    /*
     * 질문별 가중치
     *
     * Q6, Q7은 전체적인 체형을 묻기 때문에
     * 다른 질문보다 점수를 높게 설정
     */
    private final int[] questionWeight = {
            1, 1, 1, 1, 1, 2, 2
    };

    private final String[] questions = {

            "Q1. 내 어깨는 어떤 모습에 가까운가요?",

            "Q2. 목은 어떤 모습에 가까운가요?",

            "Q3. 쇄골은 평소에 어떻게 보이나요?",

            "Q4. 손가락이나 무릎을 봤을 때 뼈마디가 어떤가요?",

            "Q5. 거울로 봤을 때 어느 쪽이 더 길어 보이나요?",

            "Q6. 내 몸은 전체적으로 어떤 느낌에 가까운가요?",

            "Q7. 몸 전체의 모양은 어떤 것과 비슷해 보이나요?"
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

        // '이전 설문 불러오기'로 들어오면 지난번 답을 미리 선택
        if (getIntent().getBooleanExtra(EXTRA_LOAD_LAST, false)) {
            loadLastAnswers();
        }

        showQuestion();

        an1.setOnClickListener(v -> selectAnswer(1));
        an2.setOnClickListener(v -> selectAnswer(2));
        an3.setOnClickListener(v -> selectAnswer(3));

        next.setOnClickListener(v -> goNext());
    }

    private void showQuestion() {

        question.setText(questions[currentQuestion]);

        an1.setText(answers[currentQuestion][0]);
        an2.setText(answers[currentQuestion][1]);
        an3.setText(answers[currentQuestion][2]);

        updateProgress();

        if (currentQuestion == 6) {
            next.setText("결과 확인");
        } else {
            next.setText("다음");
        }

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
     * 7개 질문의 답변을 이용해
     * 스트레이트 / 웨이브 / 내추럴 점수 계산
     */
    private int[] calculateScores() {

        int[] scores = new int[3];

        for (int i = 0; i < 7; i++) {

            int answer = selectedAnswers[i];

            if (answer < 1 || answer > 3) {
                continue;
            }

            int type =
                    answerTypeMap[i][answer - 1];

            scores[type] += questionWeight[i];
        }

        return scores;
    }

    /**
     * 가장 점수가 높은 체형 선택
     *
     * 동점인 경우
     * Q7 → Q6 순서로 우선 판단
     */
    private int decideBodyType(int[] scores) {

        int maxScore = Math.max(
                scores[0],
                Math.max(scores[1], scores[2])
        );

        int resultType = -1;
        int tieCount = 0;

        for (int i = 0; i < 3; i++) {

            if (scores[i] == maxScore) {

                tieCount++;

                if (resultType == -1) {
                    resultType = i;
                }
            }
        }

        // 동점이 아니면 바로 반환
        if (tieCount == 1) {
            return resultType;
        }

        // 동점이면 Q7 → Q6 순으로 판단
        int[] tieBreakQuestions = {
                6,
                5
        };

        for (int q : tieBreakQuestions) {

            int answer = selectedAnswers[q];

            if (answer < 1 || answer > 3) {
                continue;
            }

            int type =
                    answerTypeMap[q][answer - 1];

            if (scores[type] == maxScore) {
                return type;
            }
        }

        return resultType;
    }

    private void showResult() {

        int[] scores = calculateScores();

        int resultType =
                decideBodyType(scores);

        saveLastSurvey(resultType);

        Intent intent =
                new Intent(
                        BodyTypeActivity.this,
                        BodyTypeResultActivity.class
                );

        intent.putExtra(
                EXTRA_RESULT_TYPE,
                resultType
        );

        intent.putExtra(
                EXTRA_SCORES,
                scores
        );

        // 처음 입력한 키도 결과 화면으로 전달
        intent.putExtra(
                "USER_HEIGHT_CM",
                userHeightCm
        );

        startActivity(intent);

        finish();
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
                .putInt(KEY_SURVEY_TYPE, resultType)
                .putFloat(KEY_SURVEY_HEIGHT, userHeightCm)
                .putLong(KEY_SURVEY_AT, System.currentTimeMillis())
                .apply();
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

        return typeName(prefs.getInt(KEY_SURVEY_TYPE, TYPE_STRAIGHT))
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