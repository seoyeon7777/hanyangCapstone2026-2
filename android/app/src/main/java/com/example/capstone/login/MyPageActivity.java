package com.example.capstone.login;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.ProfilePhotoStore;
import com.example.capstone.mypage.CancelReturnActivity;
import com.example.capstone.mypage.DeliveryAddressActivity;
import com.example.capstone.mypage.EditProfileActivity;
import com.example.capstone.mypage.MyBodyActivity;
import com.example.capstone.mypage.MyReviewActivity;
import com.example.capstone.mypage.NoticeActivity;
import com.example.capstone.mypage.OrderListActivity;
import com.example.capstone.mypage.PaymentMethodActivity;
import com.example.capstone.mypage.RefundAccountActivity;
import com.example.capstone.ui.AppDialog;
import com.example.capstone.ui.BottomMenu;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MyPageActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private ImageView imgProfile;

    private TextView tvUserName;
    private TextView tvUserEmail;
    private TextView tvEditProfile;

    private TextView tvOrderAll;

    private TextView tvCountPending;
    private TextView tvCountPaid;
    private TextView tvCountReady;
    private TextView tvCountShipping;
    private TextView tvCountDone;

    private LinearLayout itemOrderPending;
    private LinearLayout itemOrderPaid;
    private LinearLayout itemOrderReady;
    private LinearLayout itemOrderShipping;
    private LinearLayout itemOrderDone;

    // XML에서 TextView와 LinearLayout이 섞여 있으므로
    // 공통 부모 타입인 View로 관리한다.
    private View menuOrderList;
    private View menuCancelReturn;
    private View menuDelivery;
    private View menuReview;

    private View menuCoupon;
    private View menuPaymentMethod;
    private View menuRefundAccount;

    private View menuMyInquiry;
    private View menuNotice;

    private TextView tvLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 로그인하지 않은 사용자는 로그인 화면으로 이동
        if (!LoginSession.isLoggedIn(this)) {
            startActivity(
                    new Intent(this, LoginMainActivity.class)
            );
            finish();
            return;
        }

        setContentView(R.layout.activity_mypage);

        BottomMenu.setup(this, BottomMenu.TAB_MY);

        initViews();

        // 로그인한 사용자 정보 연결
        MyPageStore.setLoginUser(
                LoginSession.getName(this),
                LoginSession.getEmail(this),
                LoginSession.getPhone(this)
        );

        setListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!LoginSession.isLoggedIn(this)) {
            finish();
            return;
        }

        loadUserInfo();
        loadOrderCounts();
    }

    private void initViews() {

        btnBack = findViewById(R.id.btnBack);

        imgProfile = findViewById(R.id.imgProfile);

        tvUserName = findViewById(R.id.tvUserName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        tvEditProfile = findViewById(R.id.tvEditProfile);

        tvOrderAll = findViewById(R.id.tvOrderAll);

        tvCountPending = findViewById(R.id.tvCountPending);
        tvCountPaid = findViewById(R.id.tvCountPaid);
        tvCountReady = findViewById(R.id.tvCountReady);
        tvCountShipping = findViewById(R.id.tvCountShipping);
        tvCountDone = findViewById(R.id.tvCountDone);

        itemOrderPending = findViewById(R.id.itemOrderPending);
        itemOrderPaid = findViewById(R.id.itemOrderPaid);
        itemOrderReady = findViewById(R.id.itemOrderReady);
        itemOrderShipping = findViewById(R.id.itemOrderShipping);
        itemOrderDone = findViewById(R.id.itemOrderDone);

        // View 타입으로 받으면 TextView/LinearLayout 모두 호환
        menuOrderList = findViewById(R.id.menuOrderList);
        menuCancelReturn = findViewById(R.id.menuCancelReturn);
        menuDelivery = findViewById(R.id.menuDelivery);
        menuReview = findViewById(R.id.menuReview);

        menuCoupon = findViewById(R.id.menuCoupon);
        menuPaymentMethod = findViewById(R.id.menuPaymentMethod);
        menuRefundAccount = findViewById(R.id.menuRefundAccount);

        menuMyInquiry = findViewById(R.id.menuMyInquiry);
        menuNotice = findViewById(R.id.menuNotice);

        tvLogout = findViewById(R.id.tvLogout);
    }

    private void loadUserInfo() {

        MyPageStore.User user = MyPageStore.getUser();

        String name = user.name == null
                ? ""
                : user.name.trim();

        String email = user.email == null
                ? ""
                : user.email.trim();

        tvUserName.setText(
                (name.isEmpty() ? "회원" : name) + "님"
        );

        tvUserEmail.setText(email);

        // 계정별 프로필 사진 적용
        ProfilePhotoStore.showIn(
                imgProfile,
                Math.round(
                        12 * getResources()
                                .getDisplayMetrics().density
                )
        );
    }

    private void loadOrderCounts() {

        tvCountPending.setText(
                String.valueOf(
                        MyPageStore.countOrders(
                                MyPageStore.STATUS_PENDING
                        )
                )
        );

        tvCountPaid.setText(
                String.valueOf(
                        MyPageStore.countOrders(
                                MyPageStore.STATUS_PAID
                        )
                )
        );

        tvCountReady.setText(
                String.valueOf(
                        MyPageStore.countOrders(
                                MyPageStore.STATUS_READY
                        )
                )
        );

        tvCountShipping.setText(
                String.valueOf(
                        MyPageStore.countOrders(
                                MyPageStore.STATUS_SHIPPING
                        )
                )
        );

        tvCountDone.setText(
                String.valueOf(
                        MyPageStore.countOrders(
                                MyPageStore.STATUS_DONE
                        )
                )
        );
    }

    private void setListeners() {

        // 뒤로가기
        btnBack.setOnClickListener(v -> finish());

        // 프로필 수정
        tvEditProfile.setOnClickListener(
                v -> open(EditProfileActivity.class)
        );

        imgProfile.setOnClickListener(
                v -> open(EditProfileActivity.class)
        );

        // 주문/배송 전체보기
        tvOrderAll.setOnClickListener(
                v -> openOrders(MyPageStore.STATUS_ALL)
        );

        // 주문 상태별 상세 목록
        itemOrderPending.setOnClickListener(
                v -> openOrders(MyPageStore.STATUS_PENDING)
        );

        itemOrderPaid.setOnClickListener(
                v -> openOrders(MyPageStore.STATUS_PAID)
        );

        itemOrderReady.setOnClickListener(
                v -> openOrders(MyPageStore.STATUS_READY)
        );

        itemOrderShipping.setOnClickListener(
                v -> openOrders(MyPageStore.STATUS_SHIPPING)
        );

        itemOrderDone.setOnClickListener(
                v -> openOrders(MyPageStore.STATUS_DONE)
        );

        // 내 체형 관리
        findViewById(R.id.menuMyBody).setOnClickListener(
                v -> open(MyBodyActivity.class)
        );

        // 쇼핑 활동
        menuOrderList.setOnClickListener(
                v -> openOrders(MyPageStore.STATUS_ALL)
        );

        menuCancelReturn.setOnClickListener(
                v -> open(CancelReturnActivity.class)
        );

        menuDelivery.setOnClickListener(
                v -> open(DeliveryAddressActivity.class)
        );

        menuReview.setOnClickListener(
                v -> open(MyReviewActivity.class)
        );

        // 결제 관리
        menuCoupon.setOnClickListener(
                v -> open(CouponActivity.class)
        );

        menuPaymentMethod.setOnClickListener(
                v -> open(PaymentMethodActivity.class)
        );

        menuRefundAccount.setOnClickListener(
                v -> open(RefundAccountActivity.class)
        );

        // 고객센터
        menuMyInquiry.setOnClickListener(
                v -> open(InquiryActivity.class)
        );

        menuNotice.setOnClickListener(
                v -> open(NoticeActivity.class)
        );

        // 로그아웃
        tvLogout.setOnClickListener(v ->
                AppDialog.show(
                        this,
                        false,
                        "로그아웃",
                        "로그아웃 하시겠습니까?",
                        "취소",
                        null,
                        "로그아웃",
                        this::logout
                )
        );
    }

    private void open(Class<?> activityClass) {

        startActivity(
                new Intent(
                        MyPageActivity.this,
                        activityClass
                )
        );
    }

    private void openOrders(int status) {

        startActivity(
                OrderListActivity.newIntent(
                        MyPageActivity.this,
                        status
                )
        );
    }

    private void logout() {

        LoginSession.logout(this);
        MyPageStore.logout();

        Toast.makeText(
                this,
                "로그아웃 되었습니다.",
                Toast.LENGTH_SHORT
        ).show();

        Intent intent = new Intent(
                MyPageActivity.this,
                MainActivity.class
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}
