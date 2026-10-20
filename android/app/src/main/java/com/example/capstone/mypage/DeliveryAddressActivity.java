package com.example.capstone.mypage;

import com.example.capstone.R;
import com.example.capstone.data.MyPageStore;
import com.example.capstone.data.MyPageStore.Address;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/** 마이페이지 > 배송지 관리 (delivery) */
public class DeliveryAddressActivity extends AppCompatActivity {

    /** 등록 가능한 배송지 최대 개수 */
    private static final int MAX_ADDRESS = 10;

    private TextView tvAddressCount, tvEmpty;
    private LinearLayout containerAddresses;
    private Button btnAddAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_address);

        MyPageUi.setupToolbar(this, "배송지 관리");

        tvAddressCount = findViewById(R.id.tvAddressCount);
        tvEmpty = findViewById(R.id.tvEmpty);
        containerAddresses = findViewById(R.id.containerAddresses);
        btnAddAddress = findViewById(R.id.btnAddAddress);

        btnAddAddress.setOnClickListener(v -> {
            if (MyPageStore.getAddresses().size() >= MAX_ADDRESS) {
                Toast.makeText(this, "배송지는 최대 " + MAX_ADDRESS + "개까지 등록할 수 있습니다.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, AddressEditActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 추가/수정 화면에서 돌아오면 목록을 다시 그린다.
        renderAddresses();
    }

    private void renderAddresses() {
        List<Address> addresses = MyPageStore.getAddresses();
        tvAddressCount.setText("배송지 " + addresses.size() + "개");
        tvEmpty.setVisibility(addresses.isEmpty() ? View.VISIBLE : View.GONE);

        containerAddresses.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Address address : addresses) {
            containerAddresses.addView(createAddressView(inflater, address));
        }
    }

    private View createAddressView(LayoutInflater inflater, Address address) {
        View view = inflater.inflate(R.layout.item_address, containerAddresses, false);

        ((TextView) view.findViewById(R.id.tvLabel)).setText(address.label);
        ((TextView) view.findViewById(R.id.tvReceiver)).setText(address.receiver + " · " + address.phone);
        ((TextView) view.findViewById(R.id.tvAddress)).setText(address.getFullAddress());

        TextView tvRequest = view.findViewById(R.id.tvRequest);
        tvRequest.setText(address.request);
        tvRequest.setVisibility(TextUtils.isEmpty(address.request) ? View.GONE : View.VISIBLE);

        view.findViewById(R.id.tvDefaultBadge)
                .setVisibility(address.isDefault ? View.VISIBLE : View.GONE);

        Button btnSetDefault = view.findViewById(R.id.btnSetDefault);
        btnSetDefault.setVisibility(address.isDefault ? View.GONE : View.VISIBLE);
        btnSetDefault.setOnClickListener(v -> {
            MyPageStore.setDefaultAddress(address);
            Toast.makeText(this, "기본 배송지로 설정되었습니다.", Toast.LENGTH_SHORT).show();
            renderAddresses();
        });

        view.findViewById(R.id.btnEdit).setOnClickListener(v ->
                startActivity(AddressEditActivity.newEditIntent(this, address.id)));

        view.findViewById(R.id.btnDelete).setOnClickListener(v ->
                MyPageUi.confirm(this, "배송지 삭제", "'" + address.label + "' 배송지를 삭제하시겠습니까?", () -> {
                    MyPageStore.deleteAddress(address);
                    renderAddresses();
                }));

        return view;
    }
}
