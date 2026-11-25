package com.example.capstone.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.capstone.data.Product;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class OptionColorTest {

    private static Product product(java.util.List<String> colors) {
        Product product = new Product(1001, "시연용 브랜드", "하늘색 블라우스", 19000);
        product.setOptionColors(colors);
        return product;
    }

    @Test
    public void productsWithoutColorListUseCommonColors() {
        assertArrayEquals(ProductOptionSheet.COLORS, ProductOptionSheet.colorsFor(product(null)));
        assertNull(ProductOptionSheet.startColor(ProductOptionSheet.COLORS, ""));
    }

    @Test
    public void oneColorIsPreselectedAndOldChoiceIsDropped() {
        String[] colors = ProductOptionSheet.colorsFor(product(Collections.singletonList("하늘색")));
        assertArrayEquals(new String[]{"하늘색"}, colors);
        assertEquals("하늘색", ProductOptionSheet.startColor(colors, "WHITE"));
    }

    @Test
    public void emptyListMeansNoColorChoice() {
        String[] colors = ProductOptionSheet.colorsFor(product(Collections.<String>emptyList()));
        assertEquals(0, colors.length);
        assertEquals("", ProductOptionSheet.startColor(colors, null));
    }

    @Test
    public void cartCopyKeepsColorsAndLabelHasNoEmptyColor() {
        Product line = product(Arrays.asList("하늘색")).copyForCart(1L, "M", "", 1, true);
        assertEquals(Collections.singletonList("하늘색"), line.getOptionColors());
        assertEquals("M", line.getOptionLabel());
        assertEquals("M / 하늘색", Product.optionText("M", "하늘색"));
    }
}
