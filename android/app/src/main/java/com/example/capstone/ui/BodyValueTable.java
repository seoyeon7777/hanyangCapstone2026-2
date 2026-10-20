package com.example.capstone.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.capstone.R;
import com.example.capstone.data.BodyProfileStore;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BodyValueTable {

    private BodyValueTable() {
    }

    public static List<String[]> rows(float heightCm, JSONObject parts, boolean keyOnly) {
        List<String[]> rows = new ArrayList<>();
        if (heightCm > 0f) {
            rows.add(new String[]{"키", cm(heightCm)});
        }
        int count = keyOnly ? BodyProfileStore.KEY_PART_COUNT : BodyProfileStore.PART_KEYS.length;
        addParts(rows, parts, 0, count);
        return rows;
    }

    /** 옷 구매에 직접 쓰이지 않는 나머지 치수 (KEY_PART_COUNT 이후). */
    public static List<String[]> extraRows(JSONObject parts) {
        List<String[]> rows = new ArrayList<>();
        addParts(rows, parts, BodyProfileStore.KEY_PART_COUNT, BodyProfileStore.PART_KEYS.length);
        return rows;
    }

    private static void addParts(List<String[]> rows, JSONObject parts, int from, int to) {
        for (int i = from; i < to; i++) {
            double value = parts == null ? -1d : parts.optDouble(BodyProfileStore.PART_KEYS[i], -1d);
            if (value > 0d) {
                rows.add(new String[]{BodyProfileStore.PART_LABELS[i], cm((float) value)});
            }
        }
    }

    public static void fill(LinearLayout container, List<String[]> rows) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        LinearLayout line = null;
        for (int i = 0; i < rows.size(); i++) {
            if (i % 2 == 0) {
                line = new LinearLayout(container.getContext());
                line.setOrientation(LinearLayout.HORIZONTAL);
                container.addView(line, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            View cell = inflater.inflate(R.layout.item_body_value, line, false);
            ((TextView) cell.findViewById(R.id.tvValueLabel)).setText(rows.get(i)[0]);
            ((TextView) cell.findViewById(R.id.tvValue)).setText(rows.get(i)[1]);
            line.addView(cell);
        }
        if (line != null && rows.size() % 2 == 1) {
            View empty = inflater.inflate(R.layout.item_body_value, line, false);
            empty.setVisibility(View.INVISIBLE);
            line.addView(empty);
        }
    }

    public static String cm(float value) {
        return String.format(Locale.KOREA, "%.1fcm", value);
    }
}
