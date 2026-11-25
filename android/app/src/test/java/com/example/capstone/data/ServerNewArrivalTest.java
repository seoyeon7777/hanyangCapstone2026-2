package com.example.capstone.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ServerNewArrivalTest {

    private static Product serverProduct(boolean isNew) {
        Product product = new Product(1001, "시연용 브랜드", "블라우스", 19000);
        product.setServerInfo("BL-001", "");
        product.setNewArrival(isNew);
        return product;
    }

    @Test
    public void serverProductFollowsServerFlag() {
        assertTrue(DummyData.isNew(serverProduct(true)));
        assertFalse(DummyData.isNew(serverProduct(false)));
    }

    @Test
    public void cartCopyKeepsFlag() {
        assertTrue(DummyData.isNew(serverProduct(true).copyForCart(1L, "M", "", 1, true)));
    }
}
