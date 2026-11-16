package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.DeliveryRequests;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.Order;
import com.example.capstone.data.RecommendApi;
import com.example.capstone.ui.AppDialog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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

    private static final int[] PERIOD_MONTHS = {1, MyPageStore.RECENT_MONTHS, 6, 12, 0};
    private static final String[] PERIOD_LABELS = {"최근 1개월", "최근 3개월", "최근 6개월", "최근 1년", "전체 기간"};

    private TextView[] chips;
    private TextView tvOrderPeriod, tvEmpty;
    private LinearLayout containerOrders;
    private ScrollView scrollOrders;

    private int currentStatus = MyPageStore.STATUS_ALL;
    private int currentMonths = MyPageStore.RECENT_MONTHS;

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

        tvOrderPeriod.setOnClickListener(v -> showPeriodMenu());
    }

    private void showPeriodMenu() {
        float density = getResources().getDisplayMetrics().density;

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackgroundResource(R.drawable.bg_card);
        menu.setPadding(0, Math.round(6 * density), 0, Math.round(6 * density));

        PopupWindow popup = new PopupWindow(menu, Math.round(150 * density),
                LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(8 * density);
        popup.setOutsideTouchable(true);

        for (int i = 0; i < PERIOD_MONTHS.length; i++) {
            final int index = i;
            boolean selected = PERIOD_MONTHS[i] == currentMonths;
            TextView row = new TextView(this);
            row.setText(PERIOD_LABELS[i]);
            row.setTextSize(14);
            row.setTextColor(selected ? 0xFF111111 : 0xFF666666);
            row.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            row.setPadding(Math.round(16 * density), Math.round(11 * density),
                    Math.round(16 * density), Math.round(11 * density));
            row.setOnClickListener(v -> {
                popup.dismiss();
                currentMonths = PERIOD_MONTHS[index];
                renderOrders();
                scrollOrders.scrollTo(0, 0);
            });
            menu.addView(row);
        }

        popup.showAsDropDown(tvOrderPeriod, Math.round(14 * density), 0);
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
        tvOrderPeriod = findViewById(R.id.tvOrderPeriod);
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

        for (int i = 0; i < PERIOD_MONTHS.length; i++) {
            if (PERIOD_MONTHS[i] == currentMonths) {
                tvOrderPeriod.setText(PERIOD_LABELS[i] + " ▾");
            }
        }

        List<Order> orders = MyPageStore.getOrders(currentStatus, currentMonths);
        tvEmpty.setText(currentMonths == 0 ? "아직 주문 내역이 없어요" : "이 기간에 주문 내역이 없어요");
        tvEmpty.setVisibility(orders.isEmpty() ? View.VISIBLE : View.GONE);

        containerOrders.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            boolean sameAsPrevious = i > 0
                    && orders.get(i - 1).getGroupNo().equals(order.getGroupNo());
            boolean sameAsNext = i < orders.size() - 1
                    && orders.get(i + 1).getGroupNo().equals(order.getGroupNo());
            containerOrders.addView(createOrderView(inflater, order, sameAsPrevious, sameAsNext));
        }
    }

    private View createOrderView(LayoutInflater inflater, Order order,
                                 boolean sameAsPrevious, boolean sameAsNext) {
        View view = inflater.inflate(R.layout.item_order, containerOrders, false);

        float density = getResources().getDisplayMetrics().density;
        if (sameAsPrevious) {
            view.findViewById(R.id.layoutOrderHeader).setVisibility(View.GONE);
            View product = view.findViewById(R.id.layoutOrderProduct);
            LinearLayout.LayoutParams plp = (LinearLayout.LayoutParams) product.getLayoutParams();
            plp.topMargin = 0;
            product.setLayoutParams(plp);
            view.setPadding(view.getPaddingLeft(), Math.round(18 * density),
                    view.getPaddingRight(), view.getPaddingBottom());
        }
        if (sameAsNext) {
            View divider = view.findViewById(R.id.viewOrderDivider);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) divider.getLayoutParams();
            lp.height = Math.max(1, Math.round(density));
            lp.topMargin = Math.round(18 * density);
            lp.setMarginStart(Math.round(20 * density));
            lp.setMarginEnd(Math.round(20 * density));
            divider.setLayoutParams(lp);
            divider.setBackgroundColor(0xFFEEEEEE);
        }

        ((TextView) view.findViewById(R.id.tvOrderDate)).setText(order.orderDate);
        view.findViewById(R.id.tvOrderNo).setOnClickListener(v ->
                startActivity(OrderDetailActivity.newIntent(this, order.getGroupNo())));
        view.findViewById(R.id.layoutOrderProduct).setOnClickListener(v ->
                OrderDetailActivity.openProduct(this, order));
        ((TextView) view.findViewById(R.id.tvOrderStatus)).setText(order.getStatusLabel());
        TextView tvBrand = view.findViewById(R.id.tvBrand);
        tvBrand.setText(order.brand);
        tvBrand.setVisibility(TextUtils.isEmpty(order.brand) ? View.GONE : View.VISIBLE);
        ((TextView) view.findViewById(R.id.tvProductName)).setText(order.productName);
        // 장바구니 주문은 option에 "총 n개"까지 들어 있어 수량을 또 붙이지 않는다
        ((TextView) view.findViewById(R.id.tvOption)).setText(order.option.contains("총 ")
                ? order.option
                : (order.option.isEmpty() ? "" : order.option + " / ") + order.quantity + "개");
        ((TextView) view.findViewById(R.id.tvPrice)).setText(MyPageStore.formatPrice(order.getTotalPrice()));

        // 결제 때 적은 배송 요청사항 (없으면 줄을 숨긴다)
        TextView tvRequest = view.findViewById(R.id.tvRequest);
        // 주문에 같이 저장된 값(혜림 11/15) → 없으면 예전 방식(주문번호별 저장)
        String request = TextUtils.isEmpty(order.deliveryRequest)
                ? DeliveryRequests.get(this, order.orderNo) : order.deliveryRequest;
        tvRequest.setText("배송 요청사항: " + request);
        tvRequest.setVisibility(request.isEmpty() ? View.GONE : View.VISIBLE);

        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnTracking = view.findViewById(R.id.btnTracking);
        Button btnReturn = view.findViewById(R.id.btnReturn);
        Button btnReview = view.findViewById(R.id.btnReview);

        boolean cancelable = MyPageStore.canCancel(order);
        boolean trackable = order.status == MyPageStore.STATUS_READY
                || order.status == MyPageStore.STATUS_SHIPPING
                || order.status == MyPageStore.STATUS_DONE;
        boolean returnable = MyPageStore.canReturnOrExchange(order);
        boolean reviewable = false;

        btnCancel.setVisibility(cancelable ? View.VISIBLE : View.GONE);
        btnTracking.setVisibility(trackable ? View.VISIBLE : View.GONE);
        btnReturn.setVisibility(returnable ? View.VISIBLE : View.GONE);
        btnReview.setVisibility(reviewable ? View.VISIBLE : View.GONE);

        // 버튼이 하나도 없는 상태(취소완료 등)면 버튼 줄 자체를 숨긴다.
        view.findViewById(R.id.layoutActions).setVisibility(
                cancelable || trackable || returnable || reviewable ? View.VISIBLE : View.GONE);

        btnCancel.setOnClickListener(v ->
                startActivity(ClaimRequestActivity.newIntent(this, order.orderNo, true)));
        btnReturn.setOnClickListener(v ->
                startActivity(ClaimRequestActivity.newIntent(this, order.orderNo, false)));
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
}
