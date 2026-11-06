package com.example.capstone.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LocalAccountsTest {

    @Test
    public void phoneFormatsBecomeSameAccount() {
        // 저장·비교는 숫자만
        assertEquals("01039458685", LocalAccounts.normalizePhone("01039458685"));
        assertEquals("01039458685", LocalAccounts.normalizePhone("010-3945-8685"));
        assertEquals("01039458685", LocalAccounts.normalizePhone(" 010 3945 8685 "));
        assertEquals("0111234567", LocalAccounts.normalizePhone("0111234567"));
    }

    @Test
    public void phoneIsShownWithHyphens() {
        // 화면에 보여 줄 때만 하이픈을 붙인다
        assertEquals("010-3945-8685", LocalAccounts.formatPhone("01039458685"));
        assertEquals("010-3945-8685", LocalAccounts.formatPhone("010-3945-8685"));
        assertEquals("011-123-4567", LocalAccounts.formatPhone("0111234567"));
        assertEquals("1234", LocalAccounts.formatPhone("1234"));       // 번호가 아니면 그대로
        assertEquals("", LocalAccounts.formatPhone(null));
    }

    @Test
    public void notPhoneNumberIsRejected() {
        assertNull(LocalAccounts.normalizePhone("1234"));
        assertNull(LocalAccounts.normalizePhone("id@gmail.com"));
        assertNull(LocalAccounts.normalizePhone(null));
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
