package com.example.capstone.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;
import com.example.capstone.mypage.MyBodyActivity;

public class BodySaveCompleteActivity extends AppCompatActivity {

    private static final String EXTRA_PROFILE_NAME =
            "extra_profile_name";

    public static Intent newIntent(
            Context context, String profileName) {
        return new Intent(context, BodySaveCompleteActivity.class)
                .putExtra(EXTRA_PROFILE_NAME, profileName);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_body_save_complete);

        String name = getIntent()
                .getStringExtra(EXTRA_PROFILE_NAME);

        TextView tvSavedName = findViewById(R.id.tvSavedName);
        tvSavedName.setText("'" +
                (name == null ? "내 체형" : name) +
                "'(으)로 저장했어요");

        findViewById(R.id.btnFeedback).setOnClickListener(v -> {
            Intent intent = new Intent(
                    this, BodyFeedbackActivity.class);
            intent.putExtra("profile_name", name);
            startActivity(intent);
        });

        findViewById(R.id.btnGoHome)
                .setOnClickListener(v -> goHome(null));

        TextView tvGoMyBody = findViewById(R.id.tvGoMyBody);
        tvGoMyBody.setPaintFlags(
                tvGoMyBody.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        findViewById(R.id.btnGoMyBody)
                .setOnClickListener(v ->
                        goHome(new Intent(
                                this, MyBodyActivity.class)));

        getOnBackPressedDispatcher().addCallback(
                this, new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        goHome(null);
                    }
                });
    }

    private void goHome(Intent next) {
        Intent home = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (next == null) {
            startActivity(home);
        } else {
            startActivities(new Intent[]{home, next});
        }
        finish();
    }
}
