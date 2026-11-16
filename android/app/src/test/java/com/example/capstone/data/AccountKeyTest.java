package com.example.capstone.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AccountKeyTest {

    @Test
    public void userIdWinsOverEmailAndPhone() {
        assertEquals("test1111", AccountKey.choose("test1111", "a@test.com", "01011112222"));
        assertEquals("test1111", AccountKey.choose("test1111", "", ""));
    }

    @Test
    public void changingEmailOrPhoneDoesNotChangeTheKey() {
        String before = AccountKey.choose("test1111", "a@test.com", "01011112222");
        String after = AccountKey.choose("test1111", "b@test.com", "01099998888");
        assertEquals(before, after);
    }

    @Test
    public void oldAccountsWithoutUserIdKeepEmailThenPhone() {
        assertEquals("a@test.com", AccountKey.choose("", "a@test.com", "01011112222"));
        assertEquals("a@test.com", AccountKey.choose(null, "a@test.com", "01011112222"));
        assertEquals("01011112222", AccountKey.choose("", "", "01011112222"));
    }

    @Test
    public void notLoggedInIsGuest() {
        assertEquals("guest", AccountKey.choose("", "", ""));
        assertEquals("guest", AccountKey.choose(null, null, null));
    }

    @Test
    public void validUserIdsNeverEqualGuestOrPhoneOrEmail() {
        // 아이디 규칙: 영문 소문자·숫자를 모두 포함 → "guest"·숫자만의 번호·'@' 있는 이메일과 겹치지 않는다
        assertEquals(false, LocalAccounts.isValidId("guest"));
        assertEquals(false, LocalAccounts.isValidId("01011112222"));
        assertEquals(false, LocalAccounts.isValidId("a@test.com"));
    }
}
