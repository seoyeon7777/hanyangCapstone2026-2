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
import com.example.capstone.data.BodyInput;
import com.example.capstone.data.BodyProfileStore;
import com.example.capstone.login.LoginMainActivity;
import com.example.capstone.ui.BottomMenu;
import com.example.capstone.login.LoginSession;
import com.example.capstone.ui.AppDialog;
import com.example.capstone.ui.BodyFeedbackActivity;
import com.example.capstone.ui.BodyValueTable;
import com.example.capstone.ui.FittingActivity;
import com.example.capstone.ui.LoginRequiredView;
import com.example.capstone.ui.PhotoMeasurementActivity;

import java.util.ArrayList;
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

        // 하단 '체형분석' 탭 화면이다 (11/06, 예전 마이페이지 '내 체형관리'). 탭이라 뒤로 버튼은 숨긴다
        setContentView(R.layout.activity_my_body);
        MyPageUi.setupToolbar(this, "내 체형관리");
        BottomMenu.setup(this, BottomMenu.TAB_FITTING);

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
        String activeId = BodyProfileStore.getActiveId(this);
        List<String> targets = new ArrayList<>();
        for (String id : selectedIds) {
            if (!id.equals(activeId)) {
                targets.add(id);
            }
        }
        boolean skipActive = targets.size() < selectedIds.size();
        if (targets.isEmpty()) {
            AppDialog.alert(this, "선택 삭제",
                    "현재 체형은 삭제할 수 없어요. 다른 체형을 현재 체형으로 선택한 뒤 삭제해 주세요.", "확인");
            return;
        }
        String message = "선택한 체형 " + targets.size() + "개를 삭제하시겠습니까?";
        if (skipActive) {
            message += "\n현재 체형은 삭제되지 않아요.";
        }
        MyPageUi.confirm(this, "선택 삭제", message, () -> {
            for (String id : targets) {
                BodyProfileStore.delete(this, id);
            }
            Toast.makeText(this, "체형 " + targets.size() + "개를 삭제했어요."
                            + (skipActive ? " 현재 체형은 제외했어요." : ""),
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
        // 체형은 로그인해야 쓸 수 있다 (찜 화면과 같음): 비로그인이면 로그인 안내만 보여 준다
        if (!LoginRequiredView.update(this, R.drawable.ic_nav_fitting,
                "체형 분석은 로그인 후 이용할 수 있어요.", findViewById(R.id.scrollMyBody))) {
            return;
        }
        if (layoutProfiles != null) {
            loadProfiles();
        }
    }

    private void loadProfiles() {
        List<BodyProfileStore.Profile> profiles = BodyProfileStore.getProfiles(this);
        String activeId = BodyProfileStore.getActiveId(this);

        tvProfileCount.setText("저장된 체형 " + profiles.size() + "개");
        tvEmptyProfiles.setVisibility(profiles.isEmpty() ? View.VISIBLE : View.GONE);
        if (!LoginSession.isLoggedIn(this)) {
            // 비로그인: 체형은 계정에 저장되므로 로그인 안내 (새 체형 추가하기로 설문은 해 볼 수 있음)
            tvEmptyProfiles.setText("로그인하면 체형을 여러 개 저장하고\n그중 하나를 현재 체형으로 고를 수 있어요.\n눌러서 로그인하기 ›");
            tvEmptyProfiles.setOnClickListener(v ->
                    startActivity(new Intent(this, LoginMainActivity.class)));
        } else {
            tvEmptyProfiles.setText("저장된 체형 정보가 없어요.\n'새 체형 추가하기'로 체형을 분석해 보세요.");
            tvEmptyProfiles.setOnClickListener(null);
        }
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
        boolean surveyOnly = profile.isSurveyOnly();
        ((TextView) card.findViewById(R.id.tvProfileSummary)).setText(
                "키 " + (profile.heightCm > 0f ? BodyValueTable.cm(profile.heightCm) : "-")
                        + " · 체형 " + profile.bodyType
                        + (surveyOnly ? " (설문)" : "")
                        + " · " + profile.savedDate());

        boolean expanded = expandedIds.contains(profile.id);
        if (surveyOnly) {
            // 설문만 한 체형: 치수가 없으니 빈 표 대신 안내 한 줄
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"치수", "사진 측정 전"});
            BodyValueTable.fill(card.findViewById(R.id.layoutProfileValues), rows);
        } else {
            BodyValueTable.fill(card.findViewById(R.id.layoutProfileValues),
                    BodyValueTable.rows(-1f, profile.parts, !expanded));
        }

        TextView tvToggle = card.findViewById(R.id.tvToggleDetail);
        if (surveyOnly) {
            // 설문만 한 체형 → 사진 분석을 이어서 하면 이 카드에 치수가 채워진다
            tvToggle.setText("사진 분석 이어서 하기 ›");
            tvToggle.setOnClickListener(v -> continuePhoto(profile));
        } else {
            tvToggle.setText(expanded ? "간단히 보기 ⌃" : "전체 치수 보기 ⌄");
            tvToggle.setOnClickListener(v -> {
                if (!expandedIds.remove(profile.id)) {
                    expandedIds.add(profile.id);
                }
                loadProfiles();
            });
        }

        TextView btnSetActive = card.findViewById(R.id.btnSetActive);
        btnSetActive.setText(active ? "현재 체형" : "현재 체형으로 선택");
        btnSetActive.setEnabled(!active);
        btnSetActive.setAlpha(active ? 0.5f : 1f);
        btnSetActive.setOnClickListener(v -> {
            selectAsCurrent(profile);
        });

        CheckBox cbSelect = card.findViewById(R.id.cbSelect);
        cbSelect.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        cbSelect.setChecked(selectedIds.contains(profile.id));
        card.findViewById(R.id.tvRename).setVisibility(selectMode ? View.GONE : View.VISIBLE);
        card.findViewById(R.id.tvDelete).setVisibility(selectMode ? View.GONE : View.VISIBLE);
        card.findViewById(R.id.layoutProfileActions).setVisibility(selectMode ? View.GONE : View.VISIBLE);
        if (!selectMode) {
            // 배송지처럼: 카드를 누르면 그 체형을 현재 체형으로 고른다
            card.setOnClickListener(v -> {
                if (!active) {
                    selectAsCurrent(profile);
                }
            });
        }
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
        card.findViewById(R.id.tvDelete).setOnClickListener(v -> {
            if (active) {
                AppDialog.alert(this, "체형 삭제",
                        "현재 체형은 삭제할 수 없어요. 다른 체형을 현재 체형으로 선택한 뒤 삭제해 주세요.", "확인");
                return;
            }
            MyPageUi.confirm(this, "체형 삭제", "'" + profile.name + "'을(를) 삭제하시겠습니까?", () -> {
                BodyProfileStore.delete(this, profile.id);
                loadProfiles();
            });
        });
    }

    private void continuePhoto(BodyProfileStore.Profile profile) {
        float height = profile.heightCm > 0f ? profile.heightCm : BodyInput.height(this);
        if (height < 100f || height > 230f) {
            Toast.makeText(this, "키 정보가 없어요. '새 체형 추가하기'에서 키를 입력해 주세요.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(PhotoMeasurementActivity.continueIntent(this, profile.id, height));
    }

    private void selectAsCurrent(BodyProfileStore.Profile profile) {
        BodyProfileStore.setActive(this, profile.id);
        Toast.makeText(this, "'" + profile.name + "'을(를) 현재 체형으로 선택했어요. 홈 추천도 이 체형으로 바뀌어요.",
                Toast.LENGTH_SHORT).show();
        loadProfiles();
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
                findViewById(R.id.switchAutoActive),
                findViewById(R.id.switchSizeRecommend)
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

        // 설명이 붙은 줄은 글자 부분을 눌러도 스위치가 바뀌게
        SwitchCompat sizeWarning = findViewById(R.id.switchSizeWarning);
        SwitchCompat autoActive = findViewById(R.id.switchAutoActive);
        findViewById(R.id.rowSizeWarning).setOnClickListener(v -> sizeWarning.toggle());
        findViewById(R.id.rowAutoActive).setOnClickListener(v -> autoActive.toggle());
        SwitchCompat sizeRecommend = findViewById(R.id.switchSizeRecommend);
        findViewById(R.id.rowSizeRecommend).setOnClickListener(v -> sizeRecommend.toggle());

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
