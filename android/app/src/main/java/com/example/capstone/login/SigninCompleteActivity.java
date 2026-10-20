package com.example.capstone.login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.capstone.MainActivity;
import com.example.capstone.R;

public class SigninCompleteActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "extra_name";

    private TextView tvWelcome;
    private Button btnGoHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin_complete);

        initViews();
        applyUserName();
        setListeners();
    }

    private void initViews() {
        tvWelcome = findViewById(R.id.tvWelcome);
        btnGoHome = findViewById(R.id.btnGoHome);
    }

    private void applyUserName() {
        String name = getIntent().getStringExtra(EXTRA_NAME);

        if (name == null || name.trim().isEmpty()) {
            name = "고객";
        }

        tvWelcome.setText(name + " 고객님\n환영합니다!");
    }

    private void setListeners() {

        btnGoHome.setOnClickListener(v -> {

            Intent intent =
                    new Intent(SigninCompleteActivity.this, MainActivity.class);

            intent.setFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_NEW_TASK
            );

            startActivity(intent);
            finish();
        });
    }
}