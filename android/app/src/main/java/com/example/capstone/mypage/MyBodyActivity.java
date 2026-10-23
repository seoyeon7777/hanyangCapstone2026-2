package com.example.capstone.mypage;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.capstone.R;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.login.LoginMainActivity;
import com.example.capstone.login.LoginSession;
import com.example.capstone.ui.AvatarPreviewActivity;
import com.example.capstone.ui.BodyValueTable;
import com.example.capstone.ui.FittingActivity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MyBodyActivity extends AppCompatActivity {

    private LinearLayout layoutProfiles;
    private TextView tvProfileCount;
    private TextView tvEmptyProfiles;

    private SwitchCompat switchAll;
    private SwitchCompat[] settingSwitches;
    private boolean syncingSwitches;

    private final Set<String> expandedIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!LoginSession.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginMainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_my_body);
        MyPageUi.setupToolbar(this, "내 체형관리");

        layoutProfiles = findViewById(R.id.layoutProfiles);
        tvProfileCount = findViewById(R.id.tvProfileCount);
        tvEmptyProfiles = findViewById(R.id.tvEmptyProfiles);

        findViewById(R.id.tvNewMeasure).setOnClickListener(v -> openFitting());
        findViewById(R.id.btnMeasureNew).setOnClickListener(v -> openFitting());

        setupSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (layoutProfiles != null) {
            loadProfiles();
        }
    }

    private void loadProfiles() {
        List<BodyProfileStore.Profile> profiles = BodyProfileStore.getProfiles(this);
        String activeId = BodyProfileStore.getActiveId(this);

        tvProfileCount.setText("저장된 체형 " + profiles.size() + "개");
        tvEmptyProfiles.setVisibility(profiles.isEmpty() ? View.VISIBLE : View.GONE);

        layoutProfiles.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (BodyProfileStore.Profile profile : profiles) {
            View card = inflater.inflate(R.layout.item_body_profile, layoutProfiles, false);
            bindProfile(card, profile, profile.id.equals(activeId));
            layoutProfiles.addView(card);
        }
    }

    private void bindProfile(View card, BodyProfileStore.Profile profile, boolean active) {
        ((TextView) card.findViewById(R.id.tvProfileName)).setText(profile.name);
        card.findViewById(R.id.tvActiveBadge).setVisibility(active ? View.VISIBLE : View.GONE);
        // 대표 체형 카드는 다른 카드보다 눈에 띄게
        card.setBackgroundResource(active ? R.drawable.bg_card_active : R.drawable.bg_card);
        card.findViewById(R.id.layoutProfileValues)
                .setBackgroundResource(active ? R.drawable.bg_card : R.drawable.bg_info_box);
        ((TextView) card.findViewById(R.id.tvProfileSummary)).setText(
                "키 " + BodyValueTable.cm(profile.heightCm)
                        + " · 체형 " + profile.bodyType
                        + " · " + profile.savedDate());

        boolean expanded = expandedIds.contains(profile.id);
        BodyValueTable.fill(card.findViewById(R.id.layoutProfileValues),
                BodyValueTable.rows(-1f, profile.parts, !expanded));

        TextView tvToggle = card.findViewById(R.id.tvToggleDetail);
        tvToggle.setText(expanded ? "간단히 보기 ⌃" : "전체 치수 보기 ⌄");
        tvToggle.setOnClickListener(v -> {
            if (!expandedIds.remove(profile.id)) {
                expandedIds.add(profile.id);
            }
            loadProfiles();
        });

        TextView btnSetActive = card.findViewById(R.id.btnSetActive);
        btnSetActive.setText(active ? "대표 체형" : "대표로 설정");
        btnSetActive.setEnabled(!active);
        btnSetActive.setAlpha(active ? 0.5f : 1f);
        btnSetActive.setOnClickListener(v -> {
            BodyProfileStore.setActive(this, profile.id);
            Toast.makeText(this, "'" + profile.name + "'을(를) 대표 체형으로 설정했어요.", Toast.LENGTH_SHORT).show();
            loadProfiles();
        });

        card.findViewById(R.id.btnEditProfile).setOnClickListener(v -> openInPreview(profile));
        card.findViewById(R.id.tvRename).setOnClickListener(v -> showRename(profile));
        card.findViewById(R.id.tvDelete).setOnClickListener(v ->
                MyPageUi.confirm(this, "체형 삭제", "'" + profile.name + "'을(를) 삭제하시겠습니까?", () -> {
                    BodyProfileStore.delete(this, profile.id);
                    loadProfiles();
                }));
    }

    private void openInPreview(BodyProfileStore.Profile profile) {
        Intent intent = new Intent(this, AvatarPreviewActivity.class);
        intent.putExtra(AvatarPreviewActivity.EXTRA_HEIGHT_CM, profile.heightCm);
        intent.putExtra(AvatarPreviewActivity.EXTRA_PARTS_JSON, profile.parts.toString());
        intent.putExtra(AvatarPreviewActivity.EXTRA_BODY_FAT, profile.bodyFat);
        intent.putExtra(AvatarPreviewActivity.EXTRA_SAMPLE_LABEL, profile.name);
        intent.putExtra(AvatarPreviewActivity.EXTRA_EDIT_PROFILE_ID, profile.id);
        startActivity(intent);
    }

    private void showRename(BodyProfileStore.Profile profile) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(profile.name);
        input.setSelection(input.length());

        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("이름 변경")
                .setView(box)
                .setPositiveButton("확인", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    BodyProfileStore.rename(this, profile.id, name);
                    loadProfiles();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void setupSettings() {
        switchAll = findViewById(R.id.switchAll);
        settingSwitches = new SwitchCompat[]{
                findViewById(R.id.switchSizeWarning),
                findViewById(R.id.switchMeasureNotice),
                findViewById(R.id.switchAutoActive)
        };

        for (int i = 0; i < settingSwitches.length; i++) {
            String key = BodyProfileStore.SETTING_KEYS[i];
            settingSwitches[i].setChecked(BodyProfileStore.isSettingOn(this, key));
            settingSwitches[i].setOnCheckedChangeListener((b, checked) -> {
                if (syncingSwitches) {
                    return;
                }
                BodyProfileStore.setSetting(this, key, checked);
                syncAllSwitch();
            });
        }
        syncAllSwitch();

        switchAll.setOnCheckedChangeListener((b, checked) -> {
            if (syncingSwitches) {
                return;
            }
            syncingSwitches = true;
            for (int i = 0; i < settingSwitches.length; i++) {
                settingSwitches[i].setChecked(checked);
                BodyProfileStore.setSetting(this, BodyProfileStore.SETTING_KEYS[i], checked);
            }
            syncingSwitches = false;
        });
    }

    private void syncAllSwitch() {
        boolean allOn = true;
        for (SwitchCompat s : settingSwitches) {
            allOn &= s.isChecked();
        }
        syncingSwitches = true;
        switchAll.setChecked(allOn);
        syncingSwitches = false;
    }

    private void openFitting() {
        startActivity(new Intent(this, FittingActivity.class));
    }
}
