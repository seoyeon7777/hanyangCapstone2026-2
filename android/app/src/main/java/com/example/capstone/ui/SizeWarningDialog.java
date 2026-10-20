package com.example.capstone.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.capstone.R;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.data.FitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * =========================
 * 사이즈 주의 팝업
 * =========================
 *
 * 장바구니에 담을 때 / 주문할 때
 * 선택한 사이즈가 사용자 신체 치수와 맞지 않으면 보여준다.
 *
 * 체형 측정을 아직 하지 않은 경우에는
 * "확인할 수 없다"는 안내로 바뀐다.
 */
public class SizeWarningDialog {

    // 지금 떠 있는 주의 팝업
    private static AlertDialog showingDialog;


    // =========================
    // 버튼 콜백
    // =========================

    public interface OnActionListener {

        /** 경고를 확인하고 그래도 진행 */
        void onContinue();

        /** 사이즈 다시 선택 (체형 측정으로 이동할 때는 호출하지 않음) */
        default void onCancel() {
        }
    }


    // =========================
    // 팝업을 띄우는 상황
    //
    // 상황마다 질문 / 버튼 문구가 달라진다.
    // =========================

    public enum Action {

        CART(
                "그래도 장바구니에 담으시겠습니까?",
                "그래도 담기",
                "확인하지 않고 장바구니에 담기"
        ),

        ORDER(
                "그래도 주문하시겠습니까?",
                "그래도 주문하기",
                "확인하지 않고 주문하기"
        ),

        PAYMENT(
                "그래도 결제하시겠습니까?",
                "그래도 결제하기",
                "확인하지 않고 결제하기"
        );


        /** 사이즈가 맞지 않을 때 마지막 질문 */
        final String question;

        /** 사이즈가 맞지 않을 때 진행 버튼 */
        final String confirmText;

        /** 체형 측정을 안 했을 때 진행 버튼 */
        final String skipText;

        Action(
                String question,
                String confirmText,
                String skipText
        ) {
            this.question = question;
            this.confirmText = confirmText;
            this.skipText = skipText;
        }
    }


    // =========================
    // 상품 1개 경고
    // =========================

    public static void show(
            Context context,
            FitResult result,
            Action action,
            OnActionListener listener
    ) {

        List<FitResult> results =
                new ArrayList<>();

        results.add(result);

        show(
                context,
                results,
                action,
                listener
        );
    }


    // =========================
    // 여러 상품 경고
    //
    // 장바구니 / 주문 단계에서 사용
    // =========================

    public static void show(
            Context context,
            List<FitResult> results,
            Action action,
            OnActionListener listener
    ) {

        if (context == null
                || results == null
                || results.isEmpty()) {

            if (listener != null) {
                listener.onContinue();
            }

            return;
        }


        // 이미 주의 팝업이 떠 있으면 새로 띄우지 않는다
        // (버튼을 연달아 눌러도 한 번만 뜨도록)
        if (showingDialog != null
                && showingDialog.isShowing()) {

            return;
        }


        // =========================
        // 내 체형관리 > 체형 설정에서 꺼둔 팝업은 띄우지 않고 그대로 진행
        // =========================

        boolean noProfileOnly =
                isNoProfileOnly(results);

        String settingKey = noProfileOnly
                ? BodyProfileStore.SETTING_MEASURE_NOTICE
                : BodyProfileStore.SETTING_SIZE_WARNING;

        if (!BodyProfileStore.isSettingOn(context, settingKey)) {

            if (listener != null) {
                listener.onContinue();
            }

            return;
        }


        // =========================
        // 팝업 레이아웃
        // =========================

        View view =
                LayoutInflater
                        .from(context)
                        .inflate(
                                R.layout.dialog_size_warning,
                                null,
                                false
                        );


        TextView textTitle =
                view.findViewById(R.id.textWarningTitle);

        TextView textMessage =
                view.findViewById(R.id.textWarningMessage);

        TextView textDetail =
                view.findViewById(R.id.textWarningDetail);

        TextView textRecommend =
                view.findViewById(R.id.textWarningRecommend);

        TextView btnCancel =
                view.findViewById(R.id.btnWarningCancel);

        TextView btnConfirm =
                view.findViewById(R.id.btnWarningConfirm);

        LinearLayout layoutButtons =
                view.findViewById(R.id.layoutWarningButtons);


        if (action == null) {
            action = Action.CART;
        }


        // =========================
        // 체형 측정을 아직 안 한 경우
        // =========================

        if (noProfileOnly) {

            textTitle.setText(
                    "아직 체형 측정을 하지 않았습니다"
            );

            textMessage.setText(
                    "체형 측정을 하면 선택한 사이즈가 "
                            + "회원님의 체형에 맞는지 확인할 수 있습니다."
            );

            textDetail.setVisibility(View.GONE);
            textRecommend.setVisibility(View.GONE);

            btnCancel.setText("체형 측정으로 이동");
            btnConfirm.setText(action.skipText);

            stackButtons(
                    layoutButtons,
                    btnCancel,
                    btnConfirm
            );

        } else {

            textTitle.setText(
                    "사이즈가 맞지 않습니다"
            );

            textMessage.setText(
                    buildMessage(results)
                            + "\n"
                            + action.question
            );

            textDetail.setVisibility(View.VISIBLE);

            textDetail.setText(
                    buildDetail(results)
            );


            String recommendedSize =
                    findRecommendedSize(results);

            if (recommendedSize != null
                    && results.size() == 1) {

                textRecommend.setVisibility(View.VISIBLE);

                textRecommend.setText(
                        "추천 사이즈: " + recommendedSize
                );

            } else {

                textRecommend.setVisibility(View.GONE);
            }

            btnCancel.setText("사이즈 다시 선택");
            btnConfirm.setText(action.confirmText);
        }


        // =========================
        // 팝업 생성
        // =========================

        AlertDialog dialog =
                new AlertDialog.Builder(context)
                        .setView(view)
                        .setCancelable(true)
                        .create();

        showingDialog = dialog;

        dialog.setOnDismissListener(d -> {

            if (showingDialog == dialog) {
                showingDialog = null;
            }
        });


        // 모서리 둥근 배경이 보이도록 기본 배경 제거
        if (dialog.getWindow() != null) {

            dialog.getWindow()
                    .setBackgroundDrawableResource(
                            android.R.color.transparent
                    );
        }


        // =========================
        // 그래도 진행
        // =========================

        btnConfirm.setOnClickListener(v -> {

            dialog.dismiss();

            if (listener != null) {
                listener.onContinue();
            }
        });


        // =========================
        // 취소
        //
        // 체형 측정을 안 한 경우에는
        // 가상피팅(체형 측정) 화면으로 이동
        // 고른 사이즈는 그대로 둔다
        // =========================

        btnCancel.setOnClickListener(v -> {

            dialog.dismiss();

            if (noProfileOnly) {

                Intent intent = new Intent(
                        context,
                        FittingActivity.class
                );

                context.startActivity(intent);

                return;
            }

            if (listener != null) {
                listener.onCancel();
            }
        });


        dialog.show();
    }


    // =========================
    // 버튼을 세로로 쌓기
    //
    // "확인하지 않고 장바구니에 담기"처럼
    // 문구가 길어서 가로 2칸에 들어가지 않을 때 사용.
    // 체형 측정 버튼을 위에 강조해서 둔다.
    // =========================

    private static void stackButtons(
            LinearLayout layoutButtons,
            TextView btnTop,
            TextView btnBottom
    ) {

        layoutButtons.setOrientation(
                LinearLayout.VERTICAL
        );


        LinearLayout.LayoutParams topParams =
                (LinearLayout.LayoutParams) btnTop.getLayoutParams();

        topParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
        topParams.weight = 0f;

        btnTop.setLayoutParams(topParams);


        LinearLayout.LayoutParams bottomParams =
                (LinearLayout.LayoutParams) btnBottom.getLayoutParams();

        bottomParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
        bottomParams.weight = 0f;
        bottomParams.setMarginStart(0);
        bottomParams.topMargin = Math.round(
                10 * btnBottom.getResources()
                        .getDisplayMetrics()
                        .density
        );

        btnBottom.setLayoutParams(bottomParams);


        // 체형 측정 버튼을 진하게, 건너뛰기 버튼을 연하게
        btnTop.setBackgroundResource(R.drawable.bg_dialog_btn_dark);
        btnTop.setTextColor(Color.WHITE);

        btnBottom.setBackgroundResource(R.drawable.bg_dialog_btn_light);
        btnBottom.setTextColor(Color.parseColor("#111111"));
    }


    // =========================
    // 전부 "체형 정보 없음" 인지 확인
    // =========================

    private static boolean isNoProfileOnly(
            List<FitResult> results
    ) {

        for (FitResult result : results) {

            if (result.getStatus()
                    != FitResult.Status.NO_PROFILE) {

                return false;
            }
        }

        return true;
    }


    // =========================
    // 안내 문구
    // =========================

    private static String buildMessage(
            List<FitResult> results
    ) {

        int count = 0;

        for (FitResult result : results) {

            if (result.getStatus()
                    != FitResult.Status.NO_PROFILE) {

                count++;
            }
        }


        if (results.size() == 1) {

            return "고르신 "
                    + results.get(0).getSelectedSize()
                    + " 사이즈는 회원님의 체형과 "
                    + "옷이 맞지 않습니다.";
        }

        return "주문하려는 상품 중 "
                + count
                + "개가 회원님의 체형과 "
                + "옷이 맞지 않습니다.";
    }


    // =========================
    // 부위별 상세 사유
    // =========================

    private static String buildDetail(
            List<FitResult> results
    ) {

        StringBuilder builder =
                new StringBuilder();


        boolean multiple =
                results.size() > 1;


        for (FitResult result : results) {

            // 상품명 + 선택 사이즈
            if (multiple) {

                if (builder.length() > 0) {
                    builder.append("\n\n");
                }

                builder.append("[ ")
                        .append(result.getProductName())
                        .append(" / ")
                        .append(result.getSelectedSize())
                        .append(" ]");
            }


            // 체형 정보가 없어서 판단 못한 상품
            if (result.getStatus()
                    == FitResult.Status.NO_PROFILE) {

                if (builder.length() > 0
                        && multiple) {

                    builder.append("\n");
                }

                builder.append("체형 정보가 없어 확인할 수 없습니다.");

                continue;
            }


            for (String reason : result.getReasons()) {

                if (builder.length() > 0) {
                    builder.append("\n");
                }

                builder.append("· ")
                        .append(reason);
            }


            // 여러 상품일 때는 상품별 추천 사이즈도 표시
            if (multiple
                    && result.getRecommendedSize() != null) {

                builder.append("\n· 추천 사이즈: ")
                        .append(result.getRecommendedSize());
            }
        }

        return builder.toString();
    }


    // =========================
    // 추천 사이즈 찾기
    // =========================

    private static String findRecommendedSize(
            List<FitResult> results
    ) {

        for (FitResult result : results) {

            if (result.getRecommendedSize() != null) {

                return result.getRecommendedSize();
            }
        }

        return null;
    }
}
