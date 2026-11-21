package com.example.capstone.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProductTest {

    // 6번: 서버 상품을 다시 받으면 이름·브랜드·가격은 새 값으로, 찜·장바구니 상태는 그대로
    @Test
    public void serverDetailsUpdatedButLikeAndCartKept() {
        Product product = new Product(-1, "옛브랜드", "칼라 셔츠", 39000);
        product.setServerInfo("t01", "칼라가 목선을 세워 줘요.");
        product.setLiked(true);
        product.setInCart(true);

        assertTrue(product.updateServerDetails("새브랜드", "칼라 셔츠(새 이름)", 29000));

        assertEquals("새브랜드", product.getBrand());
        assertEquals("칼라 셔츠(새 이름)", product.getName());
        assertEquals(29000, product.getPrice());
        assertTrue(product.isLiked());
        assertTrue(product.isInCart());
        assertEquals("t01", product.getServerId());
    }

    @Test
    public void sameDetailsReportNoChange() {
        Product product = new Product(-1, "브랜드", "칼라 셔츠", 39000);

        // 바뀐 게 없으면 false → 찜 탭 저장 정보를 다시 쓰지 않는다
        assertFalse(product.updateServerDetails("브랜드", "칼라 셔츠", 39000));
    }
}
