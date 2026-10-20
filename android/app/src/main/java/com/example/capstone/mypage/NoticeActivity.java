package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.Notice;
import com.example.capstone.login.InquiryActivity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/** 마이페이지 > 고객센터/공지사항 (notices, faqs) */
public class NoticeActivity extends AppCompatActivity {

    private static final String CS_PHONE = "1588-0000";

    private static final int TAB_NOTICE = 0;
    private static final int TAB_FAQ = 1;

    private TextView tabNotice, tabFaq, tvEmpty;
    private View lineNotice, lineFaq;
    private LinearLayout containerNotice;

    /** FAQ 카테고리 (메인 화면 카테고리 바와 같은 형식) */
    private static final String[] FAQ_CATEGORIES = {
            "가상피팅", "주문/결제", "배송", "취소/반품/교환", "회원정보", "기타"
    };
    private static final int COLOR_CATEGORY_SELECTED = Color.parseColor("#000000");
    private static final int COLOR_CATEGORY_NORMAL = Color.parseColor("#777777");

    private View scrollFaqCategory;
    private TextView[] categoryViews;
    private String selectedCategory = FAQ_CATEGORIES[0];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice);

        MyPageUi.setupToolbar(this, "고객센터");

        tabNotice = findViewById(R.id.tabNotice);
        tabFaq = findViewById(R.id.tabFaq);
        lineNotice = findViewById(R.id.lineNotice);
        lineFaq = findViewById(R.id.lineFaq);
        containerNotice = findViewById(R.id.containerNotice);
        tvEmpty = findViewById(R.id.tvEmpty);
        scrollFaqCategory = findViewById(R.id.scrollFaqCategory);
        setupFaqCategories();

        ((TextView) findViewById(R.id.tvCsPhone)).setText(CS_PHONE);

        // 전화 앱으로 번호만 넘긴다 (ACTION_DIAL은 권한이 필요 없다)
        findViewById(R.id.btnCall).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + CS_PHONE))));

        findViewById(R.id.btnInquiry).setOnClickListener(v ->
                startActivity(new Intent(this, InquiryActivity.class)));

        tabNotice.setOnClickListener(v -> showTab(TAB_NOTICE));
        tabFaq.setOnClickListener(v -> showTab(TAB_FAQ));

        showTab(TAB_NOTICE);
    }

    /** 메인 화면 카테고리처럼 글자만 있는 카테고리 바를 만든다. */
    private void setupFaqCategories() {
        LinearLayout container = findViewById(R.id.containerFaqCategory);
        int gap = Math.round(25 * getResources().getDisplayMetrics().density);

        categoryViews = new TextView[FAQ_CATEGORIES.length];
        for (int i = 0; i < FAQ_CATEGORIES.length; i++) {
            String category = FAQ_CATEGORIES[i];
            TextView tv = new TextView(this);
            tv.setText(category);
            tv.setTextSize(14);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i < FAQ_CATEGORIES.length - 1) {
                params.setMarginEnd(gap);
            }
            tv.setLayoutParams(params);

            tv.setOnClickListener(v -> {
                selectedCategory = category;
                showTab(TAB_FAQ);
            });
            container.addView(tv);
            categoryViews[i] = tv;
        }
    }

    /** 선택된 카테고리는 검정 굵게, 나머지는 회색으로 표시한다. */
    private void updateCategoryStyles() {
        for (TextView tv : categoryViews) {
            boolean selected = selectedCategory.equals(tv.getText().toString());
            tv.setTextColor(selected ? COLOR_CATEGORY_SELECTED : COLOR_CATEGORY_NORMAL);
            tv.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    /** 선택한 카테고리의 FAQ만 돌려준다. */
    private List<Notice> getFaqsInCategory() {
        List<Notice> result = new ArrayList<>();
        for (Notice faq : MyPageStore.getFaqs()) {
            if (selectedCategory.equals(faq.category)) {
                result.add(faq);
            }
        }
        return result;
    }

    private void showTab(int tab) {
        MyPageUi.selectTab(new TextView[]{tabNotice, tabFaq}, new View[]{lineNotice, lineFaq}, tab);

        scrollFaqCategory.setVisibility(tab == TAB_FAQ ? View.VISIBLE : View.GONE);
        if (tab == TAB_FAQ) {
            updateCategoryStyles();
        }

        List<Notice> items = tab == TAB_NOTICE ? MyPageStore.getNotices() : getFaqsInCategory();
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        containerNotice.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Notice item : items) {
            containerNotice.addView(createItemView(inflater, item, tab == TAB_FAQ));
        }
    }

    private View createItemView(LayoutInflater inflater, Notice item, boolean isFaq) {
        View view = inflater.inflate(R.layout.item_notice, containerNotice, false);

        ((TextView) view.findViewById(R.id.tvCategory)).setText(item.category);
        ((TextView) view.findViewById(R.id.tvTitle)).setText(isFaq ? "Q. " + item.title : item.title);

        TextView tvDate = view.findViewById(R.id.tvDate);
        tvDate.setText(item.date);
        tvDate.setVisibility(TextUtils.isEmpty(item.date) ? View.GONE : View.VISIBLE);

        TextView tvContent = view.findViewById(R.id.tvContent);
        tvContent.setText(isFaq ? "A. " + item.content : item.content);

        // 제목을 누르면 내용 펼치기/접기
        TextView tvArrow = view.findViewById(R.id.tvArrow);
        view.findViewById(R.id.layoutHeader).setOnClickListener(v -> {
            boolean open = tvContent.getVisibility() != View.VISIBLE;
            tvContent.setVisibility(open ? View.VISIBLE : View.GONE);
            tvArrow.setText(open ? "▲" : "▼");
        });

        return view;
    }
}
