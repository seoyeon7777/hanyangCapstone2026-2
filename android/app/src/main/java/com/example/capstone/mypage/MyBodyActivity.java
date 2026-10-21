package com.example.capstone.mypage;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.capstone.R;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.login.LoginMainActivity;
import com.example.capstone.login.LoginSession;
import com.example.capstone.ui.AppDialog;
import com.example.capstone.ui.BodyFeedbackActivity;
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

    private TextView tvSelectMode;
    private TextView tvSelectAll;
    private TextView tvNewMeasure;
    private Button btnMeasureNew;
    private boolean selectMode;
    private final Set<String> selectedIds = new HashSet<>();

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

        tvSelectMode = findViewById(R.id.tvSelectMode);
        tvSelectAll = findViewById(R.id.tvSelectAll);
        tvNewMeasure = findViewById(R.id.tvNewMeasure);
        btnMeasureNew = findViewById(R.id.btnMeasureNew);

        tvNewMeasure.setOnClickListener(v -> openFitting());
        btnMeasureNew.setOnClickListener(v -> {
            if (selectMode) {
                deleteSelected();
            }
        });

        tvSelectMode.setOnClickListener(v -> setSelectMode(!selectMode));
        tvSelectAll.setOnClickListener(v -> toggleSelectAll());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (selectMode) {
                    setSelectMode(false);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        setupSettings();
    }

    private void setSelectMode(boolean on) {
        selectMode = on;
        selectedIds.clear();
        loadProfiles();
    }

    private void toggleSelectAll() {
        List<BodyProfileStore.Profile> profiles = BodyProfileStore.getProfiles(this);
        if (selectedIds.size() == profiles.size()) {
            selectedIds.clear();
        } else {
            for (BodyProfileStore.Profile p : profiles) {
                selectedIds.add(p.id);
            }
        }
        loadProfiles();
    }

    private void deleteSelected() {
        if (selectedIds.isEmpty()) {
            return;
        }
        MyPageUi.confirm(this, "선택 삭제",
                "선택한 체형 " + selectedIds.size() + "개를 삭제하시겠습니까?", () -> {
                    for (String id : selectedIds) {
                        BodyProfileStore.delete(this, id);
                    }
                    Toast.makeText(this, "체형 " + selectedIds.size() + "개를 삭제했어요.",
                            Toast.LENGTH_SHORT).show();
                    setSelectMode(false);
                });
    }

    private void updateSelectBar(int profileCount) {
        if (selectMode && profileCount == 0) {
            selectMode = false;
            selectedIds.clear();
        }

        tvSelectMode.setVisibility(profileCount == 0 ? View.GONE : View.VISIBLE);
        tvSelectMode.setText(selectMode ? "취소" : "선택삭제");
        tvSelectAll.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        tvSelectAll.setText(selectedIds.size() == profileCount ? "전체 해제" : "전체 선택");
        tvNewMeasure.setVisibility(selectMode ? View.GONE : View.VISIBLE);

        btnMeasureNew.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        btnMeasureNew.setText("선택 삭제 (" + selectedIds.size() + ")");
        btnMeasureNew.setEnabled(!selectedIds.isEmpty());
        btnMeasureNew.setAlpha(selectedIds.isEmpty() ? 0.4f : 1f);
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
        updateSelectBar(profiles.size());

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

        CheckBox cbSelect = card.findViewById(R.id.cbSelect);
        cbSelect.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        cbSelect.setChecked(selectedIds.contains(profile.id));
        card.findViewById(R.id.tvRename).setVisibility(selectMode ? View.GONE : View.VISIBLE);
        card.findViewById(R.id.tvDelete).setVisibility(selectMode ? View.GONE : View.VISIBLE);
        card.findViewById(R.id.layoutProfileActions).setVisibility(selectMode ? View.GONE : View.VISIBLE);
        if (selectMode) {
            card.setOnClickListener(v -> {
                if (!selectedIds.remove(profile.id)) {
                    selectedIds.add(profile.id);
                }
                loadProfiles();
            });
        }

        card.findViewById(R.id.btnEditProfile).setOnClickListener(v -> openFeedback(profile));
        card.findViewById(R.id.tvRename).setOnClickListener(v -> showRename(profile));
        card.findViewById(R.id.tvDelete).setOnClickListener(v ->
                MyPageUi.confirm(this, "체형 삭제", "'" + profile.name + "'을(를) 삭제하시겠습니까?", () -> {
                    BodyProfileStore.delete(this, profile.id);
                    loadProfiles();
                }));
    }

    private void openFeedback(BodyProfileStore.Profile profile) {
        startActivity(BodyFeedbackActivity.profileIntent(this, profile));
    }

    private void showRename(BodyProfileStore.Profile profile) {
        AppDialog.input(this, "이름 변경", null,
                profile.name, "체형 이름", InputType.TYPE_CLASS_TEXT,
                "변경", name -> {
                    if (name.isEmpty()) {
                        Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    BodyProfileStore.rename(this, profile.id, name);
                    loadProfiles();
                    return true;
                });
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
