package com.example.capstone.data;

import android.content.Context;

import com.example.capstone.login.LoginSession;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 회원 탈퇴: 그 계정 이름으로 기기에 저장된 기록을 모두 지운다.
 * 계정 이름은 이메일이 있으면 이메일, 없으면 번호다. 이메일을 지웠다 넣었다 하거나 예전 형식(하이픈 번호)으로 저장된
 * 것이 남아 있을 수 있어서, 이 회원의 이름으로 쓰였을 수 있는 값을 모두 지운다. 비회원(guest) 기록은 건드리지 않는다.
 */
public final class AccountData {

    private AccountData() {
    }

    /** 지금 로그인한 회원의 이름으로 쓰였을 수 있는 값들 (이메일, 번호, 예전 형식 번호) */
    public static Set<String> accountNames(Context context) {
        Set<String> names = new LinkedHashSet<>();
        add(names, BodyProfileStore.accountKey(context));
        add(names, LoginSession.getEmail(context));
        String phone = LoginSession.getPhone(context);
        add(names, phone);
        add(names, LocalAccounts.formatPhone(phone));
        return names;
    }

    private static void add(Set<String> names, String name) {
        if (name != null && !name.isEmpty() && !"guest".equals(name)) {
            names.add(name);
        }
    }

    /** 이 이름들로 저장된 기록을 모두 지운다 (주문, 배송지, 카드, 장바구니, 체형, 설문, 찜, 프로필 사진, 판매자 브랜드, 입력값) */
    public static void deleteAll(Context context, Set<String> names) {
        // 지워지는 주문들의 "주문번호별 서버 상품" 기록도 정리한다 (주문 기록을 지우기 전에 번호를 모은다)
        List<String> orderNos = new ArrayList<>();
        for (MyPageStore.Order order : MyPageStore.getOrders(MyPageStore.STATUS_ALL)) {
            orderNos.add(order.orderNo);
        }
        RecommendApi.forgetOrders(context, orderNos);

        for (String name : names) {
            MyPageStore.deleteAccount(context, name);
            CartStore.deleteAccount(context, name);
            BodyProfileStore.deleteAccount(context, name);
            AccountPrefs.deleteAccount(context, name);
            ProfilePhotoStore.deleteAccount(context, name);
            SellerStore.deleteAccount(context, name);
        }

        // 이 회원이 입력해 둔 키·몸무게·나이도 지운다
        BodyInput.clear(context);
    }
}
