package com.example.capstone.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.example.capstone.login.LoginSession;

import org.junit.Before;
import org.junit.Test;

/** 내장 상품의 찜: 앱을 다시 켜도 남고, 계정마다 따로다 */
public class LocalLikesTest {

    private Context context;
    private Product product;

    @Before
    public void setUp() {
        context = new CartStoreTest.FakeContext();
        product = DummyData.getProductById(1);
        product.setLiked(false);
    }

    @Test
    public void likeSurvivesAppRestart() {
        product.setLiked(true);
        LocalLikes.save(context, product);

        product.setLiked(false);                            // 앱을 껐다 켬
        LocalLikes.refresh(context);

        assertTrue(product.isLiked());
    }

    @Test
    public void unlikeIsSaved() {
        product.setLiked(true);
        LocalLikes.save(context, product);
        product.setLiked(false);
        LocalLikes.save(context, product);

        LocalLikes.refresh(context);

        assertFalse(product.isLiked());
    }

    @Test
    public void likesAreSeparatePerAccount() {
        LoginSession.updateProfile(context, "가", "a@test.com", "");
        product.setLiked(true);
        LocalLikes.save(context, product);

        LoginSession.updateProfile(context, "나", "b@test.com", "");
        LocalLikes.refresh(context);
        assertFalse(product.isLiked());

        LoginSession.updateProfile(context, "가", "a@test.com", "");
        LocalLikes.refresh(context);
        assertTrue(product.isLiked());
    }

    @Test
    public void serverProductIsNotSavedHere() {
        Product server = new Product(9101, "브랜드", "서버 상품", 10000);
        server.setServerInfo("t01", "");
        server.setLiked(true);

        LocalLikes.save(context, server);

        assertFalse(AccountPrefs.recommend(context)
                .contains(AccountPrefs.key(context, AccountPrefs.RECOMMEND_LIKED_LOCAL)));
    }
}
