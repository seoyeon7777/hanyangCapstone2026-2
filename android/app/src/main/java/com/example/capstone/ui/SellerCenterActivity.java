package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.data.SellerApi;
import com.example.capstone.data.SellerStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

/** 판매자 센터: 내 브랜드로 등록한 상품 목록과 "상품 등록하기" 버튼. */
public class SellerCenterActivity extends AppCompatActivity {

    private LinearLayout list;
    private TextView status;
    private TextView brandLine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = SellerUi.page(this, scroll);
        setContentView(scroll);

        page.addView(SellerUi.title(this, "판매자 센터"));
        brandLine = SellerUi.text(this, "", 14, SellerUi.GRAY, false);
        page.addView(brandLine);

        Button add = SellerUi.button(this, "상품 등록하기");
        page.addView(add, SellerUi.matchWidth(this, 16));
        add.setOnClickListener(v -> startActivity(new Intent(this, SellerProductActivity.class)));

        page.addView(SellerUi.section(this, "등록한 상품"));
        status = SellerUi.text(this, "", 13, SellerUi.GRAY, false);
        page.addView(status);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        page.addView(list, SellerUi.matchWidth(this, 4));
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        String brand = SellerStore.getBrand(this);
        if (brand.isEmpty()) {
            // 아직 한 번도 등록하지 않았다: 상품을 등록할 때 브랜드 이름을 적으면 여기에 모인다
            brandLine.setText("상품을 등록하면 브랜드 이름별로 여기에 모여요");
            list.removeAllViews();
            status.setText("아직 등록한 상품이 없어요. 위의 \"상품 등록하기\"로 첫 상품을 올려 보세요.");
            status.setTextColor(SellerUi.GRAY);
            return;
        }
        brandLine.setText("브랜드: " + brand);
        status.setText("불러오는 중이에요…");
        status.setTextColor(SellerUi.GRAY);
        SellerApi.myProducts(brand, new SellerApi.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                show(result.optJSONArray("items"));
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                list.removeAllViews();
                status.setText(message);
                status.setTextColor(SellerUi.RED);
            }
        });
    }

    private void show(JSONArray items) {
        list.removeAllViews();
        if (items == null || items.length() == 0) {
            status.setText("아직 등록한 상품이 없어요. 위의 \"상품 등록하기\"로 첫 상품을 올려 보세요.");
            status.setTextColor(SellerUi.GRAY);
            return;
        }
        status.setText("총 " + items.length() + "개");
        for (int i = items.length() - 1; i >= 0; i--) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) {
                list.addView(card(item), SellerUi.matchWidth(this, 8));
            }
        }
    }

    private View card(JSONObject item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(SellerUi.box(this, 0xFFFFFFFF, SellerUi.LINE));
        card.setPadding(SellerUi.dp(this, 14), SellerUi.dp(this, 12), SellerUi.dp(this, 14), SellerUi.dp(this, 12));

        card.addView(SellerUi.text(this, item.optString("product_name"), 15, SellerUi.BLACK, true));
        String price = NumberFormat.getNumberInstance(Locale.KOREA).format(item.optInt("price")) + "원";
        card.addView(SellerUi.text(this, price + "  ·  " + join(item.optJSONArray("sizes")) + "  ·  " + join(item.optJSONArray("colors")),
                13, SellerUi.GRAY, false));
        if (item.optString("image_url").isEmpty()) {
            card.addView(SellerUi.text(this, "사진 없음", 12, SellerUi.RED, false));
        }
        return card;
    }

    private static String join(JSONArray array) {
        if (array == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            sb.append(i > 0 ? "/" : "").append(array.optString(i));
        }
        return sb.toString();
    }
}
