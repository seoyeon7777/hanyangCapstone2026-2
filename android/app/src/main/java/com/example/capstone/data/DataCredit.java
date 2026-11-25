package com.example.capstone.data;

/** 외부 데이터의 출처 문구 (ETRI 이용 조건: 저작물 출처를 밝힌다. 문구는 recommend/data/README.md 2장) */
public final class DataCredit {

    /** 서버 상품의 data_source 값 */
    public static final String SOURCE_ETRI = "etri";

    public static final String ETRI =
            "상품 이미지와 속성 정보는 한국전자통신연구원(ETRI)의 '자율성장 인공지능 기술검증(PoC)을 위한 패션 코디 데이터셋'"
                    + "(DOI 10.22648/ETRI.2022.D.83)을 이용했어요. 졸업 과제 시연용이며 비영리로만 사용해요. "
                    + "상품명·가격·설명은 시연을 위해 만든 정보로, 실제 판매 상품이 아니에요.";

    private DataCredit() {
    }
}
