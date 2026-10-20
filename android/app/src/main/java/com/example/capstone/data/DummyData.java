package com.example.capstone.data;

import java.util.ArrayList;
import java.util.List;

public class DummyData {

    private static final List<Product> PRODUCTS = new ArrayList<>();

    static {
        // 기존 상품 1~6
        add(1, "브랜드이름", "오버핏 반팔 티셔츠", 10000,
                "상의",
                new String[]{"화이트", "블랙", "그레이"},
                new String[]{"캐주얼", "스트릿"},
                new String[]{"봄", "여름"});

        add(2, "브랜드이름", "베이직 셔츠", 19000,
                "상의",
                new String[]{"화이트", "블루", "베이지"},
                new String[]{"미니멀", "클래식"},
                new String[]{"봄", "가을"});

        add(3, "브랜드이름", "와이드 데님 팬츠", 29000,
                "바지",
                new String[]{"블루", "블랙"},
                new String[]{"캐주얼", "스트릿"},
                new String[]{"봄", "여름", "가을", "겨울"});

        add(4, "브랜드이름", "후드 집업", 35000,
                "아우터",
                new String[]{"그레이", "블랙", "네이비"},
                new String[]{"캐주얼", "스포티"},
                new String[]{"봄", "가을", "겨울"});

        add(5, "브랜드이름", "베이직 니트", 24000,
                "상의",
                new String[]{"베이지", "브라운", "핑크"},
                new String[]{"미니멀", "페미닌", "러블리"},
                new String[]{"가을", "겨울"});

        add(6, "브랜드이름", "코튼 팬츠", 32000,
                "바지",
                new String[]{"베이지", "화이트", "블랙"},
                new String[]{"미니멀", "캐주얼"},
                new String[]{"봄", "여름", "가을"});

        // 상의 7~12
        add(7, "모노데이", "크롭 반팔 티셔츠", 15000,
                "상의",
                new String[]{"화이트", "블랙", "핑크"},
                new String[]{"캐주얼", "러블리"},
                new String[]{"봄", "여름"});

        add(8, "무드온", "슬림핏 긴팔 티셔츠", 18000,
                "상의",
                new String[]{"화이트", "그레이", "블랙"},
                new String[]{"미니멀", "페미닌"},
                new String[]{"봄", "가을"});

        add(9, "데일리룸", "스트라이프 셔츠", 27000,
                "상의",
                new String[]{"블루", "화이트"},
                new String[]{"캐주얼", "클래식"},
                new String[]{"봄", "가을"});

        add(10, "어반무드", "오버핏 맨투맨", 33000,
                "상의",
                new String[]{"그레이", "네이비", "블랙"},
                new String[]{"캐주얼", "스트릿", "스포티"},
                new String[]{"봄", "가을", "겨울"});

        add(11, "로제트", "리본 블라우스", 29000,
                "상의",
                new String[]{"화이트", "핑크", "베이지"},
                new String[]{"페미닌", "러블리"},
                new String[]{"봄", "가을"});

        add(12, "스튜디오엘", "라운드넥 가디건", 36000,
                "아우터",
                new String[]{"베이지", "브라운", "그레이"},
                new String[]{"미니멀", "클래식"},
                new String[]{"봄", "가을"});

        // 아우터 13~16
        add(13, "어반무드", "레더 블루종 자켓", 69000,
                "아우터",
                new String[]{"블랙", "브라운"},
                new String[]{"스트릿", "캐주얼"},
                new String[]{"봄", "가을"});

        add(14, "모노데이", "숏 트렌치코트", 79000,
                "아우터",
                new String[]{"베이지", "블랙"},
                new String[]{"클래식", "미니멀"},
                new String[]{"봄", "가을"});

        add(15, "데일리룸", "오버핏 데님 자켓", 54000,
                "아우터",
                new String[]{"블루", "블랙"},
                new String[]{"캐주얼", "스트릿"},
                new String[]{"봄", "가을"});

        add(16, "무드온", "울 블렌드 롱코트", 119000,
                "아우터",
                new String[]{"브라운", "그레이", "블랙"},
                new String[]{"클래식", "미니멀"},
                new String[]{"가을", "겨울"});

        // 원피스 17~20
        add(17, "로제트", "플라워 미니 원피스", 42000,
                "원피스",
                new String[]{"핑크", "화이트"},
                new String[]{"러블리", "페미닌"},
                new String[]{"봄", "여름"});

        add(18, "모노데이", "골지 롱 원피스", 39000,
                "원피스",
                new String[]{"블랙", "그레이", "베이지"},
                new String[]{"미니멀", "페미닌"},
                new String[]{"봄", "가을"});

        add(19, "무드온", "셔츠형 롱 원피스", 49000,
                "원피스",
                new String[]{"화이트", "블루", "네이비"},
                new String[]{"클래식", "미니멀"},
                new String[]{"봄", "여름"});

        add(20, "로제트", "퍼프소매 미디 원피스", 55000,
                "원피스",
                new String[]{"핑크", "블랙"},
                new String[]{"페미닌", "러블리"},
                new String[]{"봄", "여름", "가을"});

        // 바지 21~25
        add(21, "어반무드", "투턱 와이드 슬랙스", 41000,
                "바지",
                new String[]{"블랙", "그레이", "베이지"},
                new String[]{"미니멀", "클래식"},
                new String[]{"봄", "가을", "겨울"});

        add(22, "데일리룸", "스트레이트 데님 팬츠", 36000,
                "바지",
                new String[]{"블루", "블랙"},
                new String[]{"캐주얼", "미니멀"},
                new String[]{"봄", "여름", "가을", "겨울"});

        add(23, "모노데이", "카고 와이드 팬츠", 39000,
                "바지",
                new String[]{"카키", "블랙", "베이지"},
                new String[]{"스트릿", "캐주얼"},
                new String[]{"봄", "가을"});

        add(24, "스튜디오엘", "핀턱 코튼 팬츠", 34000,
                "바지",
                new String[]{"화이트", "베이지", "네이비"},
                new String[]{"미니멀", "클래식"},
                new String[]{"봄", "여름", "가을"});

        add(25, "데일리룸", "밴딩 조거 팬츠", 28000,
                "바지",
                new String[]{"그레이", "블랙"},
                new String[]{"캐주얼", "스포티"},
                new String[]{"봄", "가을", "겨울"});

        // 신규 상의 26~29
        add(26, "무드온", "브이넥 소프트 니트", 29000,
                "상의",
                new String[]{"아이보리", "핑크", "블랙"},
                new String[]{"페미닌", "미니멀"},
                new String[]{"봄", "가을", "겨울"});

        add(27, "스튜디오엘", "슬림핏 폴라 티셔츠", 22000,
                "상의",
                new String[]{"화이트", "블랙", "브라운"},
                new String[]{"미니멀", "클래식"},
                new String[]{"가을", "겨울"});

        add(28, "로제트", "오프숄더 긴팔 티셔츠", 26000,
                "상의",
                new String[]{"핑크", "화이트", "블랙"},
                new String[]{"페미닌", "러블리"},
                new String[]{"봄", "가을"});

        add(29, "데일리룸", "헨리넥 골지 티셔츠", 23000,
                "상의",
                new String[]{"그레이", "화이트", "네이비"},
                new String[]{"캐주얼", "미니멀"},
                new String[]{"봄", "가을"});

        // 신규 아우터 30~32
        add(30, "어반무드", "숏 패딩 점퍼", 89000,
                "아우터",
                new String[]{"블랙", "아이보리", "카키"},
                new String[]{"캐주얼", "스트릿"},
                new String[]{"겨울"});

        add(31, "모노데이", "스웨이드 무스탕 자켓", 109000,
                "아우터",
                new String[]{"브라운", "베이지"},
                new String[]{"클래식", "캐주얼"},
                new String[]{"가을", "겨울"});

        add(32, "데일리룸", "라이트 윈드브레이커", 49000,
                "아우터",
                new String[]{"블랙", "그레이", "네이비"},
                new String[]{"스포티", "캐주얼"},
                new String[]{"봄", "가을"});

        // 신규 바지 33~35
        add(33, "모노데이", "부츠컷 데님 팬츠", 39000,
                "바지",
                new String[]{"블루", "블랙"},
                new String[]{"캐주얼", "페미닌"},
                new String[]{"봄", "여름", "가을", "겨울"});

        add(34, "스튜디오엘", "코듀로이 와이드 팬츠", 42000,
                "바지",
                new String[]{"브라운", "베이지", "블랙"},
                new String[]{"미니멀", "클래식"},
                new String[]{"가을", "겨울"});

        add(35, "데일리룸", "와이드 트레이닝 팬츠", 31000,
                "바지",
                new String[]{"그레이", "블랙", "네이비"},
                new String[]{"스포티", "캐주얼"},
                new String[]{"봄", "가을", "겨울"});

        // 신규 원피스 36~37
        add(36, "무드온", "골지 니트 롱 원피스", 59000,
                "원피스",
                new String[]{"베이지", "블랙", "그레이"},
                new String[]{"페미닌", "미니멀"},
                new String[]{"가을", "겨울"});

        add(37, "로제트", "벨트 셔츠 원피스", 52000,
                "원피스",
                new String[]{"화이트", "베이지", "네이비"},
                new String[]{"클래식", "페미닌"},
                new String[]{"봄", "여름", "가을"});

        // 신규 스커트 38~40
        add(38, "로제트", "플리츠 미니 스커트", 32000,
                "스커트",
                new String[]{"블랙", "그레이", "베이지"},
                new String[]{"러블리", "페미닌"},
                new String[]{"봄", "가을"});

        add(39, "모노데이", "데님 미디 스커트", 36000,
                "스커트",
                new String[]{"블루", "블랙"},
                new String[]{"캐주얼", "미니멀"},
                new String[]{"봄", "여름", "가을"});

        add(40, "스튜디오엘", "A라인 롱 스커트", 41000,
                "스커트",
                new String[]{"블랙", "브라운", "아이보리"},
                new String[]{"클래식", "페미닌"},
                new String[]{"봄", "가을", "겨울"});
    }

    private static void add(
            int id,
            String brand,
            String name,
            int price,
            String category,
            String[] colors,
            String[] styles,
            String[] seasons
    ) {
        Product product = new Product(id, brand, name, price);

        product.setFilterInfo(
                category,
                colors,
                styles,
                seasons
        );

        PRODUCTS.add(product);
    }

    public static List<Product> getProducts() {
        return PRODUCTS;
    }

    public static Product getProductById(int id) {
        for (Product product : PRODUCTS) {
            if (product.getId() == id) {
                return product;
            }
        }
        return null;
    }
}
