package com.example.capstone.login;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

public class InquiryActivity extends AppCompatActivity {

    private ImageButton btnBack;

    private TextView tabWrite, tabHistory;
    private View lineWrite, lineHistory;

    private ScrollView scrollInquiry;
    private LinearLayout containerWrite, containerHistory, containerInquiryList;
    private TextView tvInquiryCount, tvEmptyHistory;

    private Spinner spType;
    private EditText etTitle, etContent, etEmail;
    private TextView tvContentCount;
    private Button btnSubmit;

    /** 활성 탭 글자색 */
    private static final int COLOR_TAB_ACTIVE = Color.BLACK;
    /** 비활성 탭 글자색 (레이아웃의 tabHistory 기본값과 동일) */
    private static final int COLOR_TAB_INACTIVE = Color.parseColor("#AAAAAA");

    /** 문의 유형 첫 항목은 선택 안내용이라 실제 유형이 아니다. */
    private static final String TYPE_PLACEHOLDER = "문의 유형을 선택해주세요";

    /** 내용 최대 글자수 (레이아웃의 maxLength와 같은 값) */
    private static final int CONTENT_MAX_LENGTH = 1000;
    /** 내용 최소 글자수 */
    private static final int CONTENT_MIN_LENGTH = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inquiry);

        initViews();
        setupTypeSpinner();
        renderHistory();
        showWriteTab();
        setListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);

        tabWrite = findViewById(R.id.tabWrite);
        tabHistory = findViewById(R.id.tabHistory);
        lineWrite = findViewById(R.id.lineWrite);
        lineHistory = findViewById(R.id.lineHistory);

        scrollInquiry = findViewById(R.id.scrollInquiry);
        containerWrite = findViewById(R.id.containerWrite);
        containerHistory = findViewById(R.id.containerHistory);
        containerInquiryList = findViewById(R.id.containerInquiryList);
        tvInquiryCount = findViewById(R.id.tvInquiryCount);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);

        spType = findViewById(R.id.spType);
        etTitle = findViewById(R.id.etTitle);
        etContent = findViewById(R.id.etContent);
        etEmail = findViewById(R.id.etEmail);

        tvContentCount = findViewById(R.id.tvContentCount);
        btnSubmit = findViewById(R.id.btnSubmit);
    }

    private void setupTypeSpinner() {
        // [여기에 DB 입력] 문의 유형 목록 조회 (inquiry_types) - 지금은 고정 목록
        String[] types = {
                TYPE_PLACEHOLDER,
                "주문/결제",
                "배송",
                "취소/반품/교환",
                "상품",
                "회원정보",
                "기타"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, types);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(adapter);
    }

    private void setListeners() {

        btnBack.setOnClickListener(v -> finish());

        // 탭 전환
        tabWrite.setOnClickListener(v -> showWriteTab());
        tabHistory.setOnClickListener(v -> showHistoryTab());

        // 내용 글자수 표시
        etContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateContentCount(s.length());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnSubmit.setOnClickListener(v -> submitInquiry());
    }

    /** "문의하기" 탭 - 문의 작성 폼 */
    private void showWriteTab() {
        containerWrite.setVisibility(View.VISIBLE);
        containerHistory.setVisibility(View.GONE);

        lineWrite.setVisibility(View.VISIBLE);
        lineHistory.setVisibility(View.GONE);

        tabWrite.setTextColor(COLOR_TAB_ACTIVE);
        tabWrite.setTypeface(null, Typeface.BOLD);

        tabHistory.setTextColor(COLOR_TAB_INACTIVE);
        tabHistory.setTypeface(null, Typeface.NORMAL);

        // 작성 폼에서만 등록 버튼이 필요하다.
        btnSubmit.setVisibility(View.VISIBLE);

        scrollToTop();
    }

    /** "문의내역" 탭 - 내가 등록한 문의 목록 */
    private void showHistoryTab() {
        containerWrite.setVisibility(View.GONE);
        containerHistory.setVisibility(View.VISIBLE);

        lineWrite.setVisibility(View.GONE);
        lineHistory.setVisibility(View.VISIBLE);

        tabHistory.setTextColor(COLOR_TAB_ACTIVE);
        tabHistory.setTypeface(null, Typeface.BOLD);

        tabWrite.setTextColor(COLOR_TAB_INACTIVE);
        tabWrite.setTypeface(null, Typeface.NORMAL);

        // 목록만 보는 화면이라 등록 버튼은 감춘다.
        btnSubmit.setVisibility(View.GONE);

        scrollToTop();
    }

    /**
     * 문의내역 목록과 "문의 n건"을 지금 저장된 문의에 맞춰 다시 그린다.
     * 목록이 비었으면 "등록한 문의가 없습니다." 안내를 보여준다.
     */
    private void renderHistory() {
        List<MyPageStore.Inquiry> inquiries = MyPageStore.getInquiries();

        containerInquiryList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (MyPageStore.Inquiry inquiry : inquiries) {
            View card = inflater.inflate(R.layout.item_inquiry, containerInquiryList, false);

            TextView tvStatus = card.findViewById(R.id.tvInquiryStatus);
            tvStatus.setText(inquiry.status);
            tvStatus.setTextColor(MyPageStore.INQUIRY_ANSWERED.equals(inquiry.status)
                    ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE);

            ((TextView) card.findViewById(R.id.tvInquiryType)).setText("· " + inquiry.type);
            ((TextView) card.findViewById(R.id.tvInquiryTitle)).setText(inquiry.title);
            ((TextView) card.findViewById(R.id.tvInquiryDate)).setText(inquiry.date + " 등록");

            containerInquiryList.addView(card);
        }

        tvInquiryCount.setText("문의 " + inquiries.size() + "건");
        tvEmptyHistory.setVisibility(inquiries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** 탭을 바꿀 때 목록을 항상 맨 위에서 보여준다. */
    private void scrollToTop() {
        scrollInquiry.post(() -> scrollInquiry.scrollTo(0, 0));
    }

    /** 입력한 글자수를 "0 / 1000" 형태로 보여준다. */
    private void updateContentCount(int length) {
        tvContentCount.setText(length + " / " + CONTENT_MAX_LENGTH);
    }

    /** 문의를 등록한다. */
    private void submitInquiry() {

        String type = spType.getSelectedItem() != null
                ? spType.getSelectedItem().toString() : "";
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString().trim();

        if (TextUtils.isEmpty(type) || TYPE_PLACEHOLDER.equals(type)) {
            Toast.makeText(this, "문의 유형을 선택해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "제목을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(content) || content.length() < CONTENT_MIN_LENGTH) {
            Toast.makeText(this, "내용을 " + CONTENT_MIN_LENGTH + "자 이상 입력해주세요.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String email = normalizeEmail();
        if (email == null) {
            Toast.makeText(this, "답변받을 이메일을 정확히 입력해주세요. (예: example@gmail.com)",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // [여기에 DB 입력] 저장에 성공했을 때만 아래 화면 전환을 할 것
        MyPageStore.addInquiry(type, title, content, email);
        renderHistory();
        Toast.makeText(this, "문의가 등록되었습니다.", Toast.LENGTH_SHORT).show();

        clearWriteForm();
        showHistoryTab();
    }

    /** 등록을 마친 뒤 작성 폼을 비운다. */
    private void clearWriteForm() {
        spType.setSelection(0);
        etTitle.setText("");
        etContent.setText("");
        etEmail.setText("");
        updateContentCount(0);
    }

    /**
     * etEmail의 입력값을 정규화한 이메일 주소로 돌려준다.
     * 공백을 없애고 소문자로 바꾼 뒤 형식을 검사하며, 올바르지 않으면 null을 돌려준다.
     */
    private String normalizeEmail() {
        String input = etEmail.getText().toString().replaceAll("\\s", "");

        if (TextUtils.isEmpty(input)) {
            return null;
        }

        String email = input.toLowerCase(Locale.ROOT);

        // @가 없거나 두 개 이상이면 잘못된 주소
        if (email.indexOf('@') <= 0 || email.indexOf('@') != email.lastIndexOf('@')) {
            return null;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return null;
        }

        return email;
    }
}
