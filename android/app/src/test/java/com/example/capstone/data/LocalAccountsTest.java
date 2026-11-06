package com.example.capstone.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LocalAccountsTest {

    @Test
    public void phoneFormatsBecomeSameAccount() {
        assertEquals("010-3945-8685", LocalAccounts.normalizePhone("01039458685"));
        assertEquals("010-3945-8685", LocalAccounts.normalizePhone("010-3945-8685"));
        assertEquals("010-3945-8685", LocalAccounts.normalizePhone(" 010 3945 8685 "));
        assertEquals("011-123-4567", LocalAccounts.normalizePhone("0111234567"));
    }

    @Test
    public void notPhoneNumberIsRejected() {
        assertNull(LocalAccounts.normalizePhone("1234"));
        assertNull(LocalAccounts.normalizePhone("id@gmail.com"));
        assertNull(LocalAccounts.normalizePhone(null));
    }

    @Test
    public void idNeedsLowercaseAndDigit4to16() {
        assertTrue(LocalAccounts.isValidId("adda24"));
        assertTrue(LocalAccounts.isValidId("a1b2"));
        assertTrue(LocalAccounts.isValidId("abcdefghijklmn12"));
        assertFalse(LocalAccounts.isValidId("abc"));
        assertFalse(LocalAccounts.isValidId("a12"));
        assertFalse(LocalAccounts.isValidId("abcdefghijklmno12"));
        assertFalse(LocalAccounts.isValidId("abcdefgh"));
        assertFalse(LocalAccounts.isValidId("12345678"));
        assertFalse(LocalAccounts.isValidId("Adda24"));
        assertFalse(LocalAccounts.isValidId("adda_24"));
        assertFalse(LocalAccounts.isValidId("한글ab12"));
        assertFalse(LocalAccounts.isValidId(null));
    }

    @Test
    public void passwordNeedsTwoKinds8to16() {
        assertTrue(LocalAccounts.isValidPassword("abcd1234"));
        assertTrue(LocalAccounts.isValidPassword("ABCDefgh!"));
        assertTrue(LocalAccounts.isValidPassword("1234!@#$"));
        assertTrue(LocalAccounts.isValidPassword("Abc123!@Abc123!@"));
        assertFalse(LocalAccounts.isValidPassword("abcdefgh"));
        assertFalse(LocalAccounts.isValidPassword("ABCDEFGH"));
        assertFalse(LocalAccounts.isValidPassword("AbCdEfGh"));
        assertFalse(LocalAccounts.isValidPassword("12345678"));
        assertFalse(LocalAccounts.isValidPassword("!@#$%^&*"));
        assertFalse(LocalAccounts.isValidPassword("abc123!"));
        assertFalse(LocalAccounts.isValidPassword("Abc123!@Abc123!@x"));
        assertFalse(LocalAccounts.isValidPassword("abcd 1234"));
        assertFalse(LocalAccounts.isValidPassword("비밀번호abc123"));
        assertTrue(LocalAccounts.isValidPassword("abcd~`!@#$%^()*_-={}[]|;:<>,.?/".substring(0, 16)));
        assertFalse(LocalAccounts.isValidPassword("abcd123&"));
        assertFalse(LocalAccounts.isValidPassword("abcd123+"));
        assertFalse(LocalAccounts.isValidPassword("abcd123'"));
        assertFalse(LocalAccounts.isValidPassword(null));
    }

    @Test
    public void emailIsOptionalButMustBeValid() {
        assertEquals("abc.d_1@naver.com", LocalAccounts.normalizeEmail(" Abc.D_1@Naver.com "));
        assertEquals("me@mail.co.kr", LocalAccounts.normalizeEmail("me@mail.co.kr"));
        assertNull(LocalAccounts.normalizeEmail("abc@"));
        assertNull(LocalAccounts.normalizeEmail("abc@gmail"));
        assertNull(LocalAccounts.normalizeEmail("ab c@gmail.com"));
    }

    @Test
    public void onlySamePasswordGivesSameHash() throws Exception {
        byte[] salt = LocalAccounts.fromHex("00112233445566778899aabbccddeeff");

        String saved = LocalAccounts.hash("Password1!", salt);

        assertEquals(saved, LocalAccounts.hash("Password1!", salt));
        assertEquals(saved, LocalAccounts.hash(" Password1! ", salt));
        assertNotEquals(saved, LocalAccounts.hash("password1!", salt));
        assertNotEquals(saved, LocalAccounts.hash("Password1!",
                LocalAccounts.fromHex("ffeeddccbbaa99887766554433221100")));
    }

    @Test
    public void hexRoundTrip() {
        byte[] bytes = {0, 15, (byte) 255, 127};
        assertEquals("000fff7f", LocalAccounts.toHex(bytes));
        assertEquals("000fff7f", LocalAccounts.toHex(LocalAccounts.fromHex("000fff7f")));
    }
}
