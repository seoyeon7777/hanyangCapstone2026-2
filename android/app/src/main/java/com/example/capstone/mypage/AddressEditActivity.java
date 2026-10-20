package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.Address;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** 배송지 추가 / 수정 (delivery) */
public class AddressEditActivity extends AppCompatActivity {

    /** 수정할 배송지 id. 없으면 새 배송지 추가 */
    public static final String EXTRA_ADDRESS_ID = "EXTRA_ADDRESS_ID";

    private static final String REQUEST_NONE = "선택 안 함";
    private static final String[] REQUESTS = {
            REQUEST_NONE,
            "문 앞에 놓아주세요",
            "경비실에 맡겨주세요",
            "택배함에 넣어주세요",
            "배송 전 연락 바랍니다",
            "부재 시 문 앞에 놓아주세요"
    };

    private EditText etLabel, etReceiver, etPhone, etZipCode, etAddress1, etAddress2;
    private Spinner spRequest;
    private CheckBox cbDefault;
    private Button btnSearchZip, btnSave;

    /** 수정 모드일 때 원래 배송지, 추가 모드면 null */
    private Address editing;

    public static Intent newEditIntent(Context context, long addressId) {
        Intent intent = new Intent(context, AddressEditActivity.class);
        intent.putExtra(EXTRA_ADDRESS_ID, addressId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_edit);

        long id = getIntent().getLongExtra(EXTRA_ADDRESS_ID, -1);
        editing = id == -1 ? null : MyPageStore.findAddress(id);

        MyPageUi.setupToolbar(this, editing == null ? "배송지 추가" : "배송지 수정");
        initViews();
        setupRequestSpinner();
        fillForm();
        setListeners();
    }

    private void initViews() {
        etLabel = findViewById(R.id.etLabel);
        etReceiver = findViewById(R.id.etReceiver);
        etPhone = findViewById(R.id.etPhone);
        etZipCode = findViewById(R.id.etZipCode);
        etAddress1 = findViewById(R.id.etAddress1);
        etAddress2 = findViewById(R.id.etAddress2);
        spRequest = findViewById(R.id.spRequest);
        cbDefault = findViewById(R.id.cbDefault);
        btnSearchZip = findViewById(R.id.btnSearchZip);
        btnSave = findViewById(R.id.btnSave);
    }

    private void setupRequestSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, REQUESTS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRequest.setAdapter(adapter);
    }

    private void fillForm() {
        if (editing == null) {
            // 새 배송지는 회원 정보로 받는 분/연락처를 미리 채워둔다.
            MyPageStore.User user = MyPageStore.getUser();
            etReceiver.setText(user.name);
            etPhone.setText(user.phone);
            cbDefault.setChecked(MyPageStore.getAddresses().isEmpty());
            return;
        }

        etLabel.setText(editing.label);
        etReceiver.setText(editing.receiver);
        etPhone.setText(editing.phone);
        etZipCode.setText(editing.zipCode);
        etAddress1.setText(editing.address1);
        etAddress2.setText(editing.address2);
        cbDefault.setChecked(editing.isDefault);

        for (int i = 0; i < REQUESTS.length; i++) {
            if (REQUESTS[i].equals(editing.request)) {
                spRequest.setSelection(i);
            }
        }
    }

    private void setListeners() {
        btnSearchZip.setOnClickListener(v ->
                // TODO: 우편번호 검색 (다음/카카오 우편번호 서비스 WebView) 연결 후
                //       선택한 결과로 etZipCode, etAddress1 자동 입력
                Toast.makeText(this, "주소 검색은 준비 중입니다. 직접 입력해주세요.",
                        Toast.LENGTH_SHORT).show());

        btnSave.setOnClickListener(v -> saveAddress());
    }

    private void saveAddress() {
        String label = etLabel.getText().toString().trim();
        String receiver = etReceiver.getText().toString().trim();
        String phone = MyPageUi.normalizePhone(etPhone.getText().toString());
        String zip = etZipCode.getText().toString().trim();
        String address1 = etAddress1.getText().toString().trim();
        String address2 = etAddress2.getText().toString().trim();

        if (TextUtils.isEmpty(label)) {
            toast("배송지명을 입력해주세요.");
            return;
        }
        if (TextUtils.isEmpty(receiver)) {
            toast("받는 분 이름을 입력해주세요.");
            return;
        }
        if (phone == null) {
            toast("연락처를 정확히 입력해주세요.");
            return;
        }
        if (!zip.matches("[0-9]{5}")) {
            toast("우편번호 5자리를 입력해주세요.");
            return;
        }
        if (TextUtils.isEmpty(address1)) {
            toast("주소를 입력해주세요.");
            return;
        }
        if (TextUtils.isEmpty(address2)) {
            toast("상세 주소를 입력해주세요.");
            return;
        }

        String request = spRequest.getSelectedItem().toString();
        if (REQUEST_NONE.equals(request)) {
            request = "";
        }

        long id = editing == null ? MyPageStore.newId() : editing.id;
        MyPageStore.saveAddress(new Address(id, label, receiver, phone, zip,
                address1, address2, request, cbDefault.isChecked()));

        toast(editing == null ? "배송지가 추가되었습니다." : "배송지가 수정되었습니다.");
        finish();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
