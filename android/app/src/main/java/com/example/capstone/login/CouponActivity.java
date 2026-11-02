package com.example.capstone.login;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class CouponActivity extends AppCompatActivity {

    private ImageButton btnBack;

    private TextView tabOwned, tabAvailable;
    private View lineOwned, lineAvailable;

    private ScrollView scrollCoupon;
    private TextView tvCouponCount;
    private LinearLayout containerOwned, containerAvailable;

    private EditText etCouponCode;
    private Button btnRegisterCode;
    /** AVAILABLE_COUPONS 와 같은 순서의 받기 버튼 */
    private Button[] downloadButtons;

    /** 지금 보고 있는 탭이 "보유"인지 */
    private boolean ownedTabShown = true;

    /**
     * 받을 수 있는 쿠폰 목록. 레이아웃의 btnDownload1~3 카드와 같은 순서.
     * [여기에 DB 입력] 지금 받을 수 있는 쿠폰 목록 조회 (coupons)
     */
    private static final String[][] AVAILABLE_COUPONS = {
            // id, 할인, 이름, 사용 조건, 사용 기한
            {"NEWBETTER_NEW_30", "30%", "뉴베러 신상품 쿠폰", "1원 이상 구매 시 최대 50,000원 할인", "2026.08.15 23:59 까지"},
            {"FALL_LONGSLEEVE_30", "30%", "가을 긴소매 쿠폰", "1원 이상 구매 시 최대 30,000원 할인", "2026.08.18 23:59 까지"},
            {"ALL_5", "5%", "전상품 5% 쿠폰", "20,000원 이상 구매 시 사용", "2026.08.31 23:59 까지"},
    };

    /** 활성 탭 글자색 */
    private static final int COLOR_TAB_ACTIVE = Color.BLACK;
    /** 비활성 탭 글자색 (레이아웃의 tabAvailable 기본값과 동일) */
    private static final int COLOR_TAB_INACTIVE = Color.parseColor("#AAAAAA");

    /** 쿠폰번호 최소 자릿수 */
    private static final int COUPON_CODE_MIN_LENGTH = 8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coupon);

        initViews();
        restoreReceivedCoupons();
        showOwnedTab();
        setListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);

        tabOwned = findViewById(R.id.tabOwned);
        tabAvailable = findViewById(R.id.tabAvailable);
        lineOwned = findViewById(R.id.lineOwned);
        lineAvailable = findViewById(R.id.lineAvailable);

        scrollCoupon = findViewById(R.id.scrollCoupon);
        tvCouponCount = findViewById(R.id.tvCouponCount);
        containerOwned = findViewById(R.id.containerOwned);
        containerAvailable = findViewById(R.id.containerAvailable);

        etCouponCode = findViewById(R.id.etCouponCode);
        btnRegisterCode = findViewById(R.id.btnRegisterCode);

        downloadButtons = new Button[]{
                findViewById(R.id.btnDownload1),
                findViewById(R.id.btnDownload2),
                findViewById(R.id.btnDownload3),
        };
    }

    private void setListeners() {

        btnBack.setOnClickListener(v -> finish());

        // 탭 전환
        tabOwned.setOnClickListener(v -> showOwnedTab());
        tabAvailable.setOnClickListener(v -> showAvailableTab());

        // 쿠폰 받기 버튼 → 보유 탭에 바로 추가
        for (int i = 0; i < downloadButtons.length; i++) {
            final int index = i;
            downloadButtons[i].setOnClickListener(v -> downloadCoupon(index));
        }

        // 쿠폰번호 등록
        btnRegisterCode.setOnClickListener(v -> registerCouponCode());
    }

    /** "보유" 탭 - 지금 가지고 있는 쿠폰 목록 */
    private void showOwnedTab() {
        // [여기에 DB 입력] 로그인한 회원의 보유 쿠폰 목록 조회 → containerOwned 채우기 (user_coupons, coupons)
        //                 개수는 tvCouponCount에, 사용완료/기간만료 쿠폰은 흐리게 표시
        //                 지금 XML에 있는 쿠폰 카드 3장은 자리만 잡아둔 가짜 화면
        ownedTabShown = true;
        updateCouponCount();
        containerOwned.setVisibility(View.VISIBLE);
        containerAvailable.setVisibility(View.GONE);

        lineOwned.setVisibility(View.VISIBLE);
        lineAvailable.setVisibility(View.GONE);

        tabOwned.setTextColor(COLOR_TAB_ACTIVE);
        tabOwned.setTypeface(null, Typeface.BOLD);

        tabAvailable.setTextColor(COLOR_TAB_INACTIVE);
        tabAvailable.setTypeface(null, Typeface.NORMAL);

        scrollToTop();
    }

    /** "쿠폰 받기" 탭 - 받을 수 있는 쿠폰 목록과 쿠폰번호 등록 */
    private void showAvailableTab() {
        // [여기에 DB 입력] 지금 받을 수 있는 쿠폰 목록 조회 → containerAvailable 채우기 (coupons)
        //                 이미 받은 쿠폰은 버튼을 "받기완료"로 표시
        ownedTabShown = false;
        updateCouponCount();
        containerOwned.setVisibility(View.GONE);
        containerAvailable.setVisibility(View.VISIBLE);

        lineOwned.setVisibility(View.GONE);
        lineAvailable.setVisibility(View.VISIBLE);

        tabAvailable.setTextColor(COLOR_TAB_ACTIVE);
        tabAvailable.setTypeface(null, Typeface.BOLD);

        tabOwned.setTextColor(COLOR_TAB_INACTIVE);
        tabOwned.setTypeface(null, Typeface.NORMAL);

        scrollToTop();
    }

    /** 탭을 바꿀 때 목록을 항상 맨 위에서 보여준다. */
    private void scrollToTop() {
        scrollCoupon.post(() -> scrollCoupon.scrollTo(0, 0));
    }

    /**
     * 쿠폰 받기 처리. 받은 쿠폰은 보유 탭 맨 위에 바로 추가하고, 버튼은 다시 누를 수 없게 잠근다.
     */
    private void downloadCoupon(int index) {
        Button button = downloadButtons[index];
        if (!button.isEnabled()) {
            return;
        }

        String[] coupon = AVAILABLE_COUPONS[index];
        // [여기에 DB 입력] 받은 쿠폰 저장 - 이미 받은 쿠폰이면 저장하지 않음 (user_coupons)
        //                 저장에 실패하면 버튼을 원래 상태로 되돌릴 것
        if (!MyPageStore.receiveCoupon(coupon[0])) {
            markReceived(button);
            updateCouponCount();
            Toast.makeText(this, "이미 받은 쿠폰입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        markReceived(button);
        addOwnedCard(coupon);
        updateCouponCount();
        Toast.makeText(this, "쿠폰을 받았습니다.", Toast.LENGTH_SHORT).show();
    }

    /** 화면을 다시 열었을 때, 이전에 받은 쿠폰을 보유 탭과 버튼 상태에 다시 반영한다. */
    private void restoreReceivedCoupons() {
        for (int i = 0; i < AVAILABLE_COUPONS.length; i++) {
            if (MyPageStore.isCouponReceived(AVAILABLE_COUPONS[i][0])) {
                markReceived(downloadButtons[i]);
                addOwnedCard(AVAILABLE_COUPONS[i]);
            }
        }
    }

    private void markReceived(Button button) {
        button.setText("받기완료");
        button.setEnabled(false);
        button.setAlpha(0.4f);
    }

    /** 보유 탭 맨 위에 쿠폰 카드를 추가한다. */
    private void addOwnedCard(String[] coupon) {
        View card = LayoutInflater.from(this)
                .inflate(R.layout.item_coupon_owned, containerOwned, false);
        ((TextView) card.findViewById(R.id.tvCouponDiscount)).setText(coupon[1]);
        ((TextView) card.findViewById(R.id.tvCouponName)).setText(coupon[2]);
        ((TextView) card.findViewById(R.id.tvCouponCondition)).setText(coupon[3]);
        ((TextView) card.findViewById(R.id.tvCouponExpire)).setText(coupon[4]);
        containerOwned.addView(card, 0);
    }

    /**
     * "쿠폰 n개" 갱신.
     * 보유 탭: 사용할 수 있는 보유 쿠폰 수 (tag="used" 인 사용완료/기간만료 카드는 제외)
     * 쿠폰 받기 탭: 아직 받지 않은 쿠폰 수
     */
    private void updateCouponCount() {
        int count = 0;
        if (ownedTabShown) {
            for (int i = 0; i < containerOwned.getChildCount(); i++) {
                if (!"used".equals(containerOwned.getChildAt(i).getTag())) {
                    count++;
                }
            }
        } else {
            for (Button button : downloadButtons) {
                if (button.isEnabled()) {
                    count++;
                }
            }
        }
        tvCouponCount.setText("쿠폰 " + count + "개");
    }

    /**
     * 쿠폰번호를 입력해 쿠폰을 등록한다.
     */
    private void registerCouponCode() {
        String code = etCouponCode.getText().toString().replaceAll("\\s", "");

        if (TextUtils.isEmpty(code)) {
            Toast.makeText(this, "쿠폰번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (code.length() < COUPON_CODE_MIN_LENGTH) {
            Toast.makeText(this, "쿠폰번호는 " + COUPON_CODE_MIN_LENGTH + "자리 이상이어야 합니다.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!code.matches("[A-Za-z0-9-]+")) {
            Toast.makeText(this, "쿠폰번호는 영문, 숫자, - 만 사용할 수 있습니다.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String normalized = code.toUpperCase(Locale.ROOT);

        // [여기에 DB 입력] 입력한 쿠폰번호가 있는지, 기간이 지났는지, 이미 등록했는지 확인 (coupons)
        //                 문제가 있으면 안내 후 return, 괜찮으면 회원 쿠폰으로 저장 (user_coupons)
        etCouponCode.setText("");
        Toast.makeText(this, normalized + " 쿠폰이 등록되었습니다.", Toast.LENGTH_SHORT).show();
    }
}
