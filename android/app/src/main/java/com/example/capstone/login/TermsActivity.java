package com.example.capstone.login;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 약관 내용 보기. 내용은 res/raw/terms_*.txt 에 있다.
 * 줄 앞의 "## "는 큰 제목(장), "### "는 작은 제목(조), 나머지는 본문
 */
public class TermsActivity extends AppCompatActivity {

    private static final String EXTRA_TITLE = "extra_title";
    private static final String EXTRA_RAW = "extra_raw";

    public static void open(Context context, String title, int rawRes) {
        Intent intent = new Intent(context, TermsActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_RAW, rawRes);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms);

        ((TextView) findViewById(R.id.tvTermsTitle)).setText(getIntent().getStringExtra(EXTRA_TITLE));
        findViewById(R.id.btnCloseTerms).setOnClickListener(v -> finish());

        int raw = getIntent().getIntExtra(EXTRA_RAW, 0);
        ((TextView) findViewById(R.id.tvTermsBody)).setText(format(readRaw(raw)));
    }

    private String readRaw(int raw) {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getResources().openRawResource(raw), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        } catch (IOException | android.content.res.Resources.NotFoundException e) {
            return "약관 내용을 불러오지 못했습니다.";
        }
        return out.toString();
    }

    private static CharSequence format(String text) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("## ")) {
                appendStyled(out, "\n" + line.substring(3) + "\n", 21, 0xFF111111);
            } else if (line.startsWith("### ")) {
                appendStyled(out, "\n" + line.substring(4) + "\n", 16, 0xFF333333);
            } else {
                out.append(line).append('\n');
            }
        }
        return out;
    }

    private static void appendStyled(SpannableStringBuilder out, String text, int sizeSp, int color) {
        int start = out.length();
        out.append(text);
        int end = out.length();
        out.setSpan(new AbsoluteSizeSpan(sizeSp, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
