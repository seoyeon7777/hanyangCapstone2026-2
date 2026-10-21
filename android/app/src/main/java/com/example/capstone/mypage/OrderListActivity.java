package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.Order;
import com.example.capstone.ui.AppDialog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * 마이페이지 > 주문 내역 / 주문·배송 전체보기 / 상태별 개수 클릭 (orders + order_items + delivery)
 */
public class OrderListActivity extends AppCompatActivity {

    /** 처음 선택할 상태 필터 (MyPageStore.STATUS_*). 없으면 전체 */
    public static final String EXTRA_STATUS = "EXTRA_STATUS";

    /** 칩 순서와 같은 순서의 상태값 */
    private static final int[] CHIP_STATUSES = {
            MyPageStore.STATUS_ALL,
            MyPageStore.STATUS_PENDING,
            MyPageStore.STATUS_PAID,
            MyPageStore.STATUS_READY,
            MyPageStore.STATUS_SHIPPING,
            MyPageStore.STATUS_DONE
    };

    private static final String[] CANCEL_REASONS = {
            "단순 변심", "다른 상품으로 재주문", "배송 지연", "결제 수단 변경", "기타"
    };

    private static final String[] RETURN_REASONS = {
            "사이즈가 맞지 않음", "상품 불량/파손", "상품 정보와 다름", "오배송", "단순 변심", "기타"
    };

    private TextView[] chips;
    private TextView tvOrderCount, tvEmpty;
    private LinearLayout containerOrders;
    private ScrollView scrollOrders;

    private int currentStatus = MyPageStore.STATUS_ALL;

    public static Intent newIntent(Context context, int status) {
        Intent intent = new Intent(context, OrderListActivity.class);
        intent.putExtra(EXTRA_STATUS, status);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_list);

        MyPageUi.setupToolbar(this, "주문 내역");
        initViews();

        currentStatus = getIntent().getIntExtra(EXTRA_STATUS, MyPageStore.STATUS_ALL);

        for (int i = 0; i < chips.length; i++) {
            final int status = CHIP_STATUSES[i];
            chips[i].setOnClickListener(v -> {
                currentStatus = status;
                renderOrders();
                scrollOrders.scrollTo(0, 0);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 리뷰 작성 화면에서 돌아왔을 때도 목록을 다시 그린다.
        renderOrders();
    }

    private void initViews() {
        chips = new TextView[]{
                findViewById(R.id.chipAll),
                findViewById(R.id.chipPending),
                findViewById(R.id.chipPaid),
                findViewById(R.id.chipReady),
                findViewById(R.id.chipShipping),
                findViewById(R.id.chipDone)
        };
        tvOrderCount = findViewById(R.id.tvOrderCount);
        tvEmpty = findViewById(R.id.tvEmpty);
        containerOrders = findViewById(R.id.containerOrders);
        scrollOrders = findViewById(R.id.scrollOrders);
    }

    private void renderOrders() {
        for (int i = 0; i < CHIP_STATUSES.length; i++) {
            if (CHIP_STATUSES[i] == currentStatus) {
                MyPageUi.selectChip(chips, i);
            }
        }

        List<Order> orders = MyPageStore.getOrders(currentStatus);
        tvOrderCount.setText("주문 " + orders.size() + "건");
        tvEmpty.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);

        containerOrders.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Order order : orders) {
            containerOrders.addView(createOrderView(inflater, order));
        }
    }

    private View createOrderView(LayoutInflater inflater, Order order) {
        View view = inflater.inflate(R.layout.item_order, containerOrders, false);

        ((TextView) view.findViewById(R.id.tvOrderDate)).setText(order.orderDate);
        ((TextView) view.findViewById(R.id.tvOrderNo)).setText(order.orderNo);
        ((TextView) view.findViewById(R.id.tvOrderStatus)).setText(order.getStatusLabel());
        ((TextView) view.findViewById(R.id.tvBrand)).setText(order.brand);
        ((TextView) view.findViewById(R.id.tvProductName)).setText(order.productName);
        ((TextView) view.findViewById(R.id.tvOption)).setText(order.option + " / " + order.quantity + "개");
        ((TextView) view.findViewById(R.id.tvPrice)).setText(MyPageStore.formatPrice(order.getTotalPrice()));

        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnTracking = view.findViewById(R.id.btnTracking);
        Button btnReturn = view.findViewById(R.id.btnReturn);
        Button btnReview = view.findViewById(R.id.btnReview);

        boolean cancelable = MyPageStore.canCancel(order);
        boolean trackable = order.status == MyPageStore.STATUS_READY
                || order.status == MyPageStore.STATUS_SHIPPING
                || order.status == MyPageStore.STATUS_DONE;
        boolean returnable = MyPageStore.canReturnOrExchange(order);
        boolean reviewable = order.status == MyPageStore.STATUS_DONE && !order.reviewed;

        btnCancel.setVisibility(cancelable ? View.VISIBLE : View.GONE);
        btnTracking.setVisibility(trackable ? View.VISIBLE : View.GONE);
        btnReturn.setVisibility(returnable ? View.VISIBLE : View.GONE);
        btnReview.setVisibility(reviewable ? View.VISIBLE : View.GONE);

        // 버튼이 하나도 없는 상태(취소완료 등)면 버튼 줄 자체를 숨긴다.
        view.findViewById(R.id.layoutActions).setVisibility(
                cancelable || trackable || returnable || reviewable ? View.VISIBLE : View.GONE);

        btnCancel.setOnClickListener(v -> showClaimDialog(order, true));
        btnReturn.setOnClickListener(v -> showClaimDialog(order, false));
        btnTracking.setOnClickListener(v -> showTracking(order));
        btnReview.setOnClickListener(v ->
                startActivity(ReviewWriteActivity.newWriteIntent(this, order.orderNo)));

        return view;
    }

    /** 배송조회 (delivery) */
    private void showTracking(Order order) {
        // [여기에 DB 입력] 주문의 택배사/송장번호/배송 상태 조회 (delivery)
        String message;
        if (TextUtils.isEmpty(order.trackingNo)) {
            message = "아직 송장번호가 등록되지 않았습니다.";
        } else {
            message = "택배사: " + order.courier
                    + "\n송장번호: " + order.trackingNo
                    + "\n현재 상태: " + order.getStatusLabel();
        }

        AppDialog.alert(this, "배송조회", message, "확인");
    }

    /**
     * 주문취소(isCancel=true) 또는 반품/교환(isCancel=false) 신청 다이얼로그
     */
    private void showClaimDialog(Order order, boolean isCancel) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_claim_request, null);

        TextView tvProduct = dialogView.findViewById(R.id.tvClaimProduct);
        RadioGroup rgType = dialogView.findViewById(R.id.rgClaimType);
        RadioButton rbExchange = dialogView.findViewById(R.id.rbExchange);
        Spinner spReason = dialogView.findViewById(R.id.spReason);
        EditText etDetail = dialogView.findViewById(R.id.etReasonDetail);

        tvProduct.setText(order.productName + " · " + order.option + "\n"
                + MyPageStore.formatPrice(order.getTotalPrice()));
        rgType.setVisibility(isCancel ? View.GONE : View.VISIBLE);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, isCancel ? CANCEL_REASONS : RETURN_REASONS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spReason.setAdapter(adapter);

        new AlertDialog.Builder(this)
                .setTitle(isCancel ? "주문 취소" : "반품/교환 신청")
                .setView(dialogView)
                .setPositiveButton("신청", (d, w) -> {
                    String type;
                    if (isCancel) {
                        type = MyPageStore.CLAIM_CANCEL;
                    } else {
                        type = rbExchange.isChecked() ? MyPageStore.CLAIM_EXCHANGE : MyPageStore.CLAIM_RETURN;
                    }

                    String reason = spReason.getSelectedItem().toString();
                    String detail = etDetail.getText().toString().trim();
                    if (!detail.isEmpty()) {
                        reason += " - " + detail;
                    }

                    MyPageStore.requestClaim(order, type, reason);
                    Toast.makeText(this, type + " 신청이 완료되었습니다.", Toast.LENGTH_SHORT).show();
                    renderOrders();
                })
                .setNegativeButton("닫기", null)
                .show();
    }
}
