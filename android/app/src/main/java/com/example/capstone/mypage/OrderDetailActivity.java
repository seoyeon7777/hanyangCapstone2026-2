package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.DummyData;
import com.example.capstone.data.LocalAccounts;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.Order;
import com.example.capstone.data.Product;
import com.example.capstone.ui.ProductDetailActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class OrderDetailActivity extends AppCompatActivity {

    private static final String EXTRA_GROUP_NO = "order_group_no";

    public static Intent newIntent(Context context, String groupNo) {
        Intent intent = new Intent(context, OrderDetailActivity.class);
        intent.putExtra(EXTRA_GROUP_NO, groupNo);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);
        MyPageUi.setupToolbar(this, "주문 상세");
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        String groupNo = getIntent().getStringExtra(EXTRA_GROUP_NO);
        List<Order> items = new ArrayList<>();
        for (Order order : MyPageStore.getOrders(MyPageStore.STATUS_ALL)) {
            if (order.getGroupNo().equals(groupNo)) {
                items.add(order);
            }
        }
        if (items.isEmpty()) {
            finish();
            return;
        }
        Order first = items.get(0);

        ((TextView) findViewById(R.id.tvDetailDate)).setText(first.orderDate + " 주문");
        ((TextView) findViewById(R.id.tvDetailNo)).setText("(주문번호 " + groupNo + ")");

        LinearLayout containerItems = findViewById(R.id.containerDetailItems);
        containerItems.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int productTotal = 0;
        int payTotal = 0;
        for (Order item : items) {
            containerItems.addView(itemView(inflater, containerItems, item));
            productTotal += item.price * item.quantity;
            payTotal += item.getTotalPrice();
        }

        LinearLayout shipping = findViewById(R.id.containerShipping);
        shipping.removeAllViews();
        if (TextUtils.isEmpty(first.shippingAddress)) {
            shipping.addView(row("배송지", "정보 없음", false, false));
        } else {
            shipping.addView(row("수령인", maskName(first.receiver), false, false));
            shipping.addView(row("휴대폰", maskPhone(first.receiverPhone), false, false));
            shipping.addView(row("주소", first.shippingAddress, false, false));
        }
        shipping.addView(row("요청사항",
                TextUtils.isEmpty(first.deliveryRequest) ? "없음" : first.deliveryRequest, false, false));

        LinearLayout payment = findViewById(R.id.containerPayment);
        payment.removeAllViews();
        int discount = Math.max(0, productTotal - payTotal);
        payment.addView(row("상품 금액", MyPageStore.formatPrice(productTotal), true, false));
        payment.addView(row("쿠폰 할인",
                discount == 0 ? "0원" : "-" + MyPageStore.formatPrice(discount), true, false));
        if (!TextUtils.isEmpty(first.couponName)) {
            LinearLayout coupon = row("ㄴ " + first.couponName, "", true, false);
            ((TextView) coupon.getChildAt(0)).setTextColor(0xFF999999);
            ((TextView) coupon.getChildAt(0)).setTextSize(13);
            payment.addView(coupon);
        }
        payment.addView(row("배송비", "무료", true, false));

        LinearLayout total = row("총 결제 금액", MyPageStore.formatPrice(payTotal), true, true);
        total.setPadding(0, dp(12), 0, dp(4));
        payment.addView(total);

        TextView method = new TextView(this);
        method.setText(first.getPayMethodText());
        method.setTextColor(0xFF5A5A5A);
        method.setTextSize(13);
        method.setGravity(Gravity.END);
        payment.addView(method, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private View itemView(LayoutInflater inflater, LinearLayout parent, Order item) {
        View view = inflater.inflate(R.layout.item_order_detail_product, parent, false);
        ((TextView) view.findViewById(R.id.tvDetailStatus)).setText(item.getStatusLabel());

        TextView brand = view.findViewById(R.id.tvDetailBrand);
        brand.setText(item.brand);
        brand.setVisibility(TextUtils.isEmpty(item.brand) ? View.GONE : View.VISIBLE);

        ((TextView) view.findViewById(R.id.tvDetailName)).setText(item.productName);
        ((TextView) view.findViewById(R.id.tvDetailOption)).setText(item.option.contains("총 ")
                ? item.option
                : (item.option.isEmpty() ? "" : item.option + " / ") + item.quantity + "개");
        ((TextView) view.findViewById(R.id.tvDetailPrice)).setText(MyPageStore.formatPrice(item.getTotalPrice()));

        view.findViewById(R.id.layoutDetailProduct).setOnClickListener(v -> openProduct(this, item));
        return view;
    }

    private LinearLayout row(String label, String value, boolean amount, boolean bold) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(0, dp(7), 0, dp(7));

        TextView left = new TextView(this);
        left.setText(label);
        left.setTextSize(bold ? 16 : 14);
        left.setTextColor(amount ? 0xFF333333 : 0xFF888888);

        TextView right = new TextView(this);
        right.setText(value);
        right.setTextSize(bold ? 18 : 14);
        right.setTextColor(0xFF111111);
        right.setLineSpacing(0, 1.2f);

        if (bold) {
            left.setTypeface(null, Typeface.BOLD);
            left.setTextColor(0xFF111111);
            right.setTypeface(null, Typeface.BOLD);
        }

        if (amount) {
            line.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            right.setGravity(Gravity.END);
            line.addView(right, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            line.addView(left, new LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT));
            line.addView(right, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        return line;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    static String maskName(String name) {
        if (name == null || name.length() < 2) {
            return name == null ? "" : name;
        }
        StringBuilder masked = new StringBuilder().append(name.charAt(0));
        for (int i = 1; i < name.length() - 1; i++) {
            masked.append('*');
        }
        if (name.length() == 2) {
            masked.append('*');
        } else {
            masked.append(name.charAt(name.length() - 1));
        }
        return masked.toString();
    }

    static String maskPhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        if (digits.length() < 10) {
            return LocalAccounts.formatPhone(phone);
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }

    static void openProduct(Activity activity, Order order) {
        Intent intent = new Intent(activity, ProductDetailActivity.class);

        if (!TextUtils.isEmpty(order.serverProductId)) {
            intent.putExtra("server_product_id", order.serverProductId);
            intent.putExtra("product_id", order.productId);
            intent.putExtra("product_brand", order.brand);
            intent.putExtra("product_name", order.productName);
            intent.putExtra("product_price", order.price);
            activity.startActivity(intent);
            return;
        }

        Product product = order.productId >= 0 ? DummyData.getProductById(order.productId) : null;
        if (product != null && product.isFromServer()) {
            product = null;
        }
        if (product == null) {
            for (Product candidate : DummyData.getProducts()) {
                if (candidate.getName() != null && candidate.getName().equals(order.productName)) {
                    product = candidate;
                    break;
                }
            }
        }
        if (product == null) {
            Toast.makeText(activity, "상품 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        intent.putExtra("product_id", product.getId());
        intent.putExtra("product_brand", product.getBrand());
        intent.putExtra("product_name", product.getName());
        intent.putExtra("product_price", product.getPrice());
        if (product.isFromServer()) {
            intent.putExtra("server_product_id", product.getServerId());
        }
        activity.startActivity(intent);
    }
}
