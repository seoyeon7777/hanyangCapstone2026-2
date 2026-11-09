package com.example.capstone.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.TextView;

import com.example.capstone.R;

public final class FilterChip {

    private FilterChip() {
    }

    public static void apply(TextView chip, boolean active) {
        chip.setBackgroundResource(active ? R.drawable.bg_filter_chip_active : R.drawable.bg_filter_chip);
        chip.setTextColor(active ? Color.WHITE : 0xFF111111);
        chip.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
    }
}
