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
import com.example.capstone.ui.AppDialog;
import com.example.capstone.mypage.PaymentMethodActivity;
import com.example.capstone.mypage.RefundAccountActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MyPageActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private ImageView imgProfile;
    private TextView tvUserName, tvUserEmail, tvEditProfile;

    private TextView tvOrderAll;
    private TextView tvCountPending, tvCountPaid, tvCountReady, tvCountShipping, tvCountDone;
    private LinearLayout itemOrderPending, itemOrderPaid, itemOrderReady, itemOrderShipping, itemOrderDone;

    private TextView menuOrderList, menuCancelReturn, menuDelivery, menuReview;
    private TextView menuCoupon, menuPaymentMethod, menuRefundAccount;
    private TextView menuMyInquiry, menuNotice;
    private TextView tvLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 로그인하지 않은 상태로 들어오면 로그인 화면으로 보낸다.
        if (!LoginSession.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginMainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_mypage);

        initViews();

        // 기기에 저장된 로그인 정보를 저장소에 반영
        MyPageStore.setLoginUser(
                LoginSession.getName(this),
                LoginSession.getEmail(this),
                LoginSession.getPhone(this));

        setListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 프로필 수정 / 주문 취소 등 하위 화면에서 바뀐 내용을 돌아올 때마다 다시 반영
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

    /** users 테이블 정보를 화면에 반영 */
    private void loadUserInfo() {
        // [여기에 DB 입력] 로그인한 회원의 이름/이메일/프로필 이미지 조회 (users)
        MyPageStore.User user = MyPageStore.getUser();
        tvUserName.setText((user.name.isEmpty() ? "회원" : user.name) + "님");
        tvUserEmail.setText(user.email);

        ProfilePhotoStore.showIn(imgProfile,
                Math.round(12 * getResources().getDisplayMetrics().density));
    }

    /** orders + delivery 상태별 개수 반영 */
    private void loadOrderCounts() {
        // [여기에 DB 입력] 주문접수/결제완료/배송준비중/배송중/배송완료 상태별 주문 개수 조회 (orders)
        tvCountPending.setText(String.valueOf(MyPageStore.countOrders(MyPageStore.STATUS_PENDING)));
        tvCountPaid.setText(String.valueOf(MyPageStore.countOrders(MyPageStore.STATUS_PAID)));
        tvCountReady.setText(String.valueOf(MyPageStore.countOrders(MyPageStore.STATUS_READY)));
        tvCountShipping.setText(String.valueOf(MyPageStore.countOrders(MyPageStore.STATUS_SHIPPING)));
        tvCountDone.setText(String.valueOf(MyPageStore.countOrders(MyPageStore.STATUS_DONE)));
    }

    private void setListeners() {

        btnBack.setOnClickListener(v -> finish());

        // 프로필 수정
        tvEditProfile.setOnClickListener(v -> open(EditProfileActivity.class));
        imgProfile.setOnClickListener(v -> open(EditProfileActivity.class));

        // 주문/배송 전체보기 + 상태별 개수를 누르면 해당 상태로 필터된 주문 목록
        tvOrderAll.setOnClickListener(v -> openOrders(MyPageStore.STATUS_ALL));
        itemOrderPending.setOnClickListener(v -> openOrders(MyPageStore.STATUS_PENDING));
        itemOrderPaid.setOnClickListener(v -> openOrders(MyPageStore.STATUS_PAID));
        itemOrderReady.setOnClickListener(v -> openOrders(MyPageStore.STATUS_READY));
        itemOrderShipping.setOnClickListener(v -> openOrders(MyPageStore.STATUS_SHIPPING));
        itemOrderDone.setOnClickListener(v -> openOrders(MyPageStore.STATUS_DONE));

        findViewById(R.id.menuMyBody).setOnClickListener(v -> open(MyBodyActivity.class));

        // 쇼핑 활동
        menuOrderList.setOnClickListener(v -> openOrders(MyPageStore.STATUS_ALL));
        menuCancelReturn.setOnClickListener(v -> open(CancelReturnActivity.class));
        menuDelivery.setOnClickListener(v -> open(DeliveryAddressActivity.class));
        menuReview.setOnClickListener(v -> open(MyReviewActivity.class));

        // 결제 관리
        menuCoupon.setOnClickListener(v -> open(CouponActivity.class));
        menuPaymentMethod.setOnClickListener(v -> open(PaymentMethodActivity.class));
        menuRefundAccount.setOnClickListener(v -> open(RefundAccountActivity.class));

        // 문의
        menuMyInquiry.setOnClickListener(v -> open(InquiryActivity.class));
        menuNotice.setOnClickListener(v -> open(NoticeActivity.class));

        // 로그아웃
        tvLogout.setOnClickListener(v -> AppDialog.show(this, false,
                "로그아웃",
                "로그아웃 하시겠습니까?",
                "취소", null,
                "로그아웃", this::logout));
    }

    private void open(Class<?> activityClass) {
        startActivity(new Intent(MyPageActivity.this, activityClass));
    }

    private void openOrders(int status) {
        startActivity(OrderListActivity.newIntent(MyPageActivity.this, status));
    }

    private void logout() {
        // [여기에 DB 입력] 로그인 세션/토큰 삭제
        LoginSession.logout(this);
        MyPageStore.logout();
        Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();

        // 뒤로가기로 마이페이지에 다시 들어오지 못하도록 쌓인 화면을 모두 비우고 메인으로
        Intent intent = new Intent(MyPageActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
