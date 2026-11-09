package com.example.capstone.ui;

import android.content.Context;
import android.widget.ImageView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.capstone.R;

public final class GridColumns {

    private static final String PREF = "ui_prefs";
    private static final String KEY = "grid_columns";

    private GridColumns() {
    }

    public static int get(Context context) {
        int saved = context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getInt(KEY, 2);
        return saved == 3 ? 3 : 2;
    }

    private static void set(Context context, int columns) {
        context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY, columns)
                .apply();
    }

    public static void bind(ImageView toggle, RecyclerView recycler) {
        Context context = toggle.getContext();
        showIcon(toggle, get(context));
        toggle.setOnClickListener(v -> {
            int next = get(context) == 2 ? 3 : 2;
            set(context, next);
            showIcon(toggle, next);
            apply(recycler);
        });
    }

    public static void apply(RecyclerView recycler) {
        int columns = get(recycler.getContext());
        RecyclerView.LayoutManager manager = recycler.getLayoutManager();
        if (manager instanceof GridLayoutManager) {
            GridLayoutManager grid = (GridLayoutManager) manager;
            if (grid.getSpanCount() == columns) {
                return;
            }
            grid.setSpanCount(columns);
        } else {
            recycler.setLayoutManager(new GridLayoutManager(recycler.getContext(), columns));
        }
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        if (adapter != null) {
            recycler.setAdapter(null);
            recycler.setAdapter(adapter);
        }
    }

    public static int columnsOf(RecyclerView recycler) {
        RecyclerView.LayoutManager manager = recycler.getLayoutManager();
        return manager instanceof GridLayoutManager ? ((GridLayoutManager) manager).getSpanCount() : 2;
    }

    private static void showIcon(ImageView toggle, int columns) {
        toggle.setImageResource(columns == 3 ? R.drawable.ic_grid_3 : R.drawable.ic_grid_2);
        toggle.setContentDescription(columns == 3 ? "3열로 보는 중" : "2열로 보는 중");
    }
}
