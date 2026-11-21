package com.example.capstone.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 이메일 형식 검증: 가입·프로필·아이디 찾기·결제 영수증이 같은 규칙을 쓴다 */
public class EmailCheckTest {

    @Test
    public void acceptsNormalAddresses() {
        assertTrue(EmailCheck.isValidFormat("a@naver.com"));
        assertTrue(EmailCheck.isValidFormat("first.last+tag@sub.example.co.kr"));
        assertTrue(EmailCheck.isValidFormat("user_01@gmail.com"));
    }

    @Test
    public void rejectsBrokenAddresses() {
        assertFalse(EmailCheck.isValidFormat(null));
        assertFalse(EmailCheck.isValidFormat(""));
        assertFalse(EmailCheck.isValidFormat("a@"));
        assertFalse(EmailCheck.isValidFormat("@naver.com"));
        assertFalse(EmailCheck.isValidFormat("a@@naver.com"));
        assertFalse(EmailCheck.isValidFormat("a@naver"));          // 최상위 도메인 없음
        assertFalse(EmailCheck.isValidFormat("a@naver.c"));        // 최상위 도메인 한 글자
        assertFalse(EmailCheck.isValidFormat("a b@naver.com"));    // 공백
        assertFalse(EmailCheck.isValidFormat("가나@naver.com"));    // 한글
        assertFalse(EmailCheck.isValidFormat("a@naver..com"));
    }
}
