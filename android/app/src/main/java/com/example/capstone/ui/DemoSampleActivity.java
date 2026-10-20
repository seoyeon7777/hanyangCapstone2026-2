package com.example.capstone.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;
import com.example.capstone.avatar.DemoBodySamples;

/**
 * 개발용: 설문/카메라 없이 데모 체형 → 측정 결과 → 아바타.
 */
public class DemoSampleActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo_sample);

        LinearLayout list = findViewById(R.id.demoSampleList);
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(14f * density);
        int gap = Math.round(10f * density);

        for (DemoBodySamples.Sample sample : DemoBodySamples.ALL) {
            // 결과 화면으로 가는 카드
            TextView card = new TextView(this);
            card.setText(sample.label + "\n" + sample.blurb
                    + "\n키 " + format(sample.height)
                    + " · 가슴 " + format(sample.chest)
                    + " · 허리 " + format(sample.waist)
                    + " · 엉덩이 " + format(sample.hip));
            card.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            card.setTextColor(0xFF111111);
            card.setLineSpacing(0f, 1.15f);
            card.setPadding(pad, pad, pad, pad);
            card.setBackgroundColor(0xFFF5F5F5);
            card.setGravity(Gravity.START);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = gap;
            card.setLayoutParams(lp);

            card.setOnClickListener(v -> {
                Intent intent = DemoBodySamples.measurementResultIntent(this, sample);
                startActivity(intent);
            });

            // 길게 누르면 아바타 직행
            card.setOnLongClickListener(v -> {
                startActivity(DemoBodySamples.avatarPreviewIntent(this, sample));
                return true;
            });

            list.addView(card);
        }

        TextView hint = new TextView(this);
        hint.setText("탭: 측정 결과 화면 → 아바타\n길게 누르기: 아바타 바로 열기");
        hint.setTextColor(0xFF999999);
        hint.setTextSize(12f);
        hint.setPadding(0, gap, 0, 0);
        list.addView(hint);
    }

    private static String format(float cm) {
        if (cm == (int) cm) {
            return String.valueOf((int) cm);
        }
        return String.format(java.util.Locale.getDefault(), "%.1f", cm);
    }
}
