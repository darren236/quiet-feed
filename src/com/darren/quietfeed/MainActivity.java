package com.darren.quietfeed;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityManager;
import android.widget.LinearLayout;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Native settings screen. The accessibility service reads the same preference keys. */
public final class MainActivity extends Activity {
    private static final String PREFS = "quietfeed_prefs";
    private static final String INSTAGRAM_MODE = "instagram_mode";
    private static final String FACEBOOK_BLOCK_REELS = "facebook_block_reels";
    private static final String TIKTOK_MODE = "tiktok_mode";

    private static final int BACKGROUND = Color.rgb(247, 248, 252);
    private static final int CARD = Color.WHITE;
    private static final int INK = Color.rgb(28, 34, 52);
    private static final int MUTED = Color.rgb(99, 107, 126);
    private static final int BORDER = Color.rgb(226, 230, 239);
    private static final int ACCENT = Color.rgb(91, 74, 222);
    private static final int ACCENT_PALE = Color.rgb(243, 241, 255);
    private static final int GREEN = Color.rgb(20, 139, 93);
    private static final int AMBER = Color.rgb(196, 120, 35);

    private SharedPreferences preferences;
    private TextView statusTitle;
    private TextView statusDescription;
    private TextView accessibilityButton;
    private View statusDot;
    private final List<ModeRow> instagramRows = new ArrayList<>();
    private final List<ModeRow> tiktokRows = new ArrayList<>();

    private static final class ModeRow {
        final String value;
        final LinearLayout container;
        final RadioButton radio;

        ModeRow(String value, LinearLayout container, RadioButton radio) {
            this.value = value;
            this.container = container;
            this.radio = radio;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);

        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setBackgroundColor(BACKGROUND);

        final LinearLayout page = vertical();
        page.setPadding(dp(20), dp(28), dp(20), dp(36));
        if (Build.VERSION.SDK_INT >= 35) {
            scrollView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                    Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                            | WindowInsets.Type.displayCutout());
                    page.setPadding(dp(20) + safe.left, dp(28) + safe.top,
                            dp(20) + safe.right, dp(36) + safe.bottom);
                    return insets;
                }
            });
        }
        scrollView.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scrollView);

        addHeader(page);
        addStatusCard(page);
        addInstagramCard(page);
        addFacebookCard(page);
        addTikTokCard(page);
        addBankingShortcutCard(page);
        addExplanationCard(page);
        updateInstagramSelection();
        updateTikTokSelection();
        updateAccessibilityStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (preferences != null) {
            updateAccessibilityStatus();
        }
    }

    private void addHeader(LinearLayout page) {
        LinearLayout brand = horizontal();
        brand.setGravity(Gravity.CENTER_VERTICAL);

        TextView mark = text("Q", 21, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(ACCENT, 13, ACCENT));
        brand.addView(mark, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout nameBlock = vertical();
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.leftMargin = dp(12);
        brand.addView(nameBlock, nameParams);
        nameBlock.addView(text("QuietFeed: Shorts Blocker", 20, INK, true));
        TextView eyebrow = text("YOUR SOCIAL, ON YOUR TERMS", 10, ACCENT, true);
        eyebrow.setLetterSpacing(0.12f);
        nameBlock.addView(eyebrow);
        nameBlock.addView(text("Version " + installedVersion(), 11, MUTED, false));
        page.addView(brand);

        TextView intro = text("Keep the conversations. Skip the scroll.", 16, MUTED, false);
        intro.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introParams.topMargin = dp(20);
        introParams.bottomMargin = dp(24);
        page.addView(intro, introParams);
    }

    private String installedVersion() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return version == null ? "unknown" : version;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "unknown";
        }
    }

    private void addStatusCard(LinearLayout page) {
        LinearLayout card = card();
        LinearLayout.LayoutParams cardParams = cardParams(dp(16));
        page.addView(card, cardParams);

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        top.addView(statusDot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        statusTitle = text("Checking protection…", 17, INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = dp(10);
        top.addView(statusTitle, titleParams);
        card.addView(top);

        statusDescription = text("", 13, MUTED, false);
        statusDescription.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(9);
        card.addView(statusDescription, descriptionParams);

        accessibilityButton = text("Open Accessibility settings", 14, Color.WHITE, true);
        accessibilityButton.setGravity(Gravity.CENTER);
        accessibilityButton.setPadding(dp(18), dp(12), dp(18), dp(12));
        accessibilityButton.setBackground(round(ACCENT, 12, ACCENT));
        accessibilityButton.setClickable(true);
        accessibilityButton.setFocusable(true);
        accessibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                openAccessibilitySettings();
            }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(16);
        card.addView(accessibilityButton, buttonParams);

        TextView restrictedNote = text(
                "Installing this APK yourself? If Android blocks accessibility access, open Settings → Apps → QuietFeed: Shorts Blocker → ⋮ → Allow restricted settings, then return here.",
                12, MUTED, false);
        restrictedNote.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(14);
        card.addView(restrictedNote, noteParams);
    }

    private void addInstagramCard(LinearLayout page) {
        LinearLayout card = card();
        page.addView(card, cardParams(dp(16)));

        addServiceHeading(card, "IG", "Instagram", "Choose what stays available.",
                Color.rgb(232, 65, 134));

        addInstagramRow(card, "off", "Off", "Use Instagram normally.");
        addInstagramRow(card, "reels", "Block Reels", "Leave Instagram when a Reel opens. Home previews stay visible.");
        addInstagramRow(card, "dm", "DMs + shared Reels",
                "Use chats and Reels opened from them. Scrolling a Reel leaves Instagram.");
    }

    private void addInstagramRow(LinearLayout card, String value, String label, String description) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(13), dp(13));
        row.setMinimumHeight(dp(70));

        LinearLayout words = vertical();
        LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(words, wordsParams);
        words.addView(text(label, 15, INK, true));
        TextView detail = text(description, 12, MUTED, false);
        detail.setLineSpacing(dp(1), 1f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(3);
        words.addView(detail, detailParams);

        RadioButton radio = new RadioButton(this);
        radio.setButtonTintList(ColorStateList.valueOf(ACCENT));
        radio.setContentDescription(label);
        radio.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                selectInstagramMode(value);
            }
        });
        LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        radioParams.leftMargin = dp(8);
        row.addView(radio, radioParams);

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                selectInstagramMode(value);
            }
        });
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(9);
        card.addView(row, rowParams);
        instagramRows.add(new ModeRow(value, row, radio));
    }

    private void selectInstagramMode(String mode) {
        preferences.edit().putString(INSTAGRAM_MODE, mode).apply();
        updateInstagramSelection();
    }

    private void updateInstagramSelection() {
        String selectedMode = preferences.getString(INSTAGRAM_MODE, "reels");
        for (ModeRow item : instagramRows) {
            boolean selected = item.value.equals(selectedMode);
            item.radio.setChecked(selected);
            item.container.setBackground(round(
                    selected ? ACCENT_PALE : CARD, 12, selected ? ACCENT : BORDER));
        }
    }

    private void addFacebookCard(LinearLayout page) {
        LinearLayout card = card();
        page.addView(card, cardParams(dp(16)));

        addServiceHeading(card, "f", "Facebook", "Keep Facebook available without Reels.",
                Color.rgb(23, 103, 218));

        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(13), dp(13));
        row.setBackground(round(CARD, 12, BORDER));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(14);
        card.addView(row, rowParams);

        LinearLayout words = vertical();
        row.addView(words, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        words.addView(text("Block Reels", 15, INK, true));
        TextView description = text("Leave Facebook when a Reel opens. Feed previews stay visible.", 12, MUTED, false);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(3);
        words.addView(description, descriptionParams);

        Switch control = new Switch(this);
        control.setContentDescription("Block Facebook Reels");
        control.setChecked(preferences.getBoolean(FACEBOOK_BLOCK_REELS, true));
        control.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                preferences.edit().putBoolean(FACEBOOK_BLOCK_REELS, checked).apply();
            }
        });
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchParams.leftMargin = dp(10);
        row.addView(control, switchParams);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                control.setChecked(!control.isChecked());
            }
        });
    }

    private void addTikTokCard(LinearLayout page) {
        LinearLayout card = card();
        page.addView(card, cardParams(dp(16)));

        addServiceHeading(card, "♪", "TikTok", "Choose which videos stay available.",
                Color.rgb(29, 29, 35));

        addTikTokRow(card, "off", "Off", "Use TikTok normally.");
        addTikTokRow(card, "videos", "Block videos",
                "Leave TikTok when a video feed or opened video is detected, including videos from chats.");
        addTikTokRow(card, "dm", "Chats + shared videos",
                "Use the inbox and chats. Watch a video opened directly from a chat until you swipe. The Friends feed is blocked.");
    }

    private void addTikTokRow(LinearLayout card, String value, String label, String description) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(13), dp(13));
        row.setMinimumHeight(dp(70));

        LinearLayout words = vertical();
        row.addView(words, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        words.addView(text(label, 15, INK, true));
        TextView detail = text(description, 12, MUTED, false);
        detail.setLineSpacing(dp(1), 1f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(3);
        words.addView(detail, detailParams);

        RadioButton radio = new RadioButton(this);
        radio.setButtonTintList(ColorStateList.valueOf(ACCENT));
        radio.setContentDescription("TikTok: " + label);
        radio.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                selectTikTokMode(value);
            }
        });
        LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        radioParams.leftMargin = dp(8);
        row.addView(radio, radioParams);

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                selectTikTokMode(value);
            }
        });
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(9);
        card.addView(row, rowParams);
        tiktokRows.add(new ModeRow(value, row, radio));
    }

    private void selectTikTokMode(String mode) {
        preferences.edit().putString(TIKTOK_MODE, mode).apply();
        updateTikTokSelection();
    }

    private void updateTikTokSelection() {
        String selectedMode = preferences.getString(TIKTOK_MODE, "off");
        for (ModeRow item : tiktokRows) {
            boolean selected = item.value.equals(selectedMode);
            item.radio.setChecked(selected);
            item.container.setBackground(round(
                    selected ? ACCENT_PALE : CARD, 12, selected ? ACCENT : BORDER));
        }
    }

    private void addBankingShortcutCard(LinearLayout page) {
        LinearLayout card = card();
        page.addView(card, cardParams(dp(16)));
        card.addView(text(getString(R.string.banking_shortcut_title), 16, INK, true));

        TextView guide = text(getString(R.string.banking_shortcut_guide), 13, MUTED, false);
        guide.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        guideParams.topMargin = dp(10);
        card.addView(guide, guideParams);

        TextView settings = text(getString(R.string.banking_shortcut_settings), 14, Color.WHITE, true);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(dp(18), dp(12), dp(18), dp(12));
        settings.setBackground(round(ACCENT, 12, ACCENT));
        settings.setClickable(true);
        settings.setFocusable(true);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openAccessibilitySettings(); }
        });
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        settingsParams.topMargin = dp(16);
        card.addView(settings, settingsParams);
    }

    private void addExplanationCard(LinearLayout page) {
        LinearLayout card = card();
        page.addView(card, cardParams(0));
        card.addView(text("What to expect", 16, INK, true));

        TextView explanation = text(
                "Opening a blocked Instagram or Facebook Reel shows a brief notice, then sends you Home. Previews in those feeds stay visible. Instagram's DM mode lets you use messages and ends a shared Reel when you scroll. TikTok's chat mode covers video feeds with an inbox button; videos opened directly from a chat end when you swipe. The TikTok Friends feed is separate from chats. App layout changes can affect detection.",
                13, MUTED, false);
        explanation.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams explanationParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        explanationParams.topMargin = dp(10);
        card.addView(explanation, explanationParams);
    }

    private void addServiceHeading(LinearLayout card, String symbol, String title,
                                   String subtitle, int symbolColor) {
        LinearLayout heading = horizontal();
        heading.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = text(symbol, 17, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(symbolColor, 11, symbolColor));
        heading.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout words = vertical();
        LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wordsParams.leftMargin = dp(11);
        heading.addView(words, wordsParams);
        words.addView(text(title, 18, INK, true));
        TextView detail = text(subtitle, 12, MUTED, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(2);
        words.addView(detail, detailParams);
        card.addView(heading);
    }

    private void updateAccessibilityStatus() {
        boolean enabled = isShieldServiceEnabled();
        statusDot.setBackground(oval(enabled ? GREEN : AMBER));
        statusTitle.setText(enabled ? "Protection is ready" : "Turn on protection");
        statusDescription.setText(enabled
                ? "Your choices are active while QuietFeed: Shorts Blocker accessibility access is enabled."
                : "Enable QuietFeed: Shorts Blocker in Android Accessibility settings to apply your choices.");
        accessibilityButton.setText(enabled
                ? "Manage Accessibility access" : "Open Accessibility settings");
    }

    private boolean isShieldServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager)
                getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        String expectedName = getPackageName() + ".ShieldService";
        List<AccessibilityServiceInfo> enabledServices = manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo item : enabledServices) {
            ResolveInfo resolveInfo = item.getResolveInfo();
            ServiceInfo service = resolveInfo == null ? null : resolveInfo.serviceInfo;
            if (service == null || !getPackageName().equals(service.packageName)) continue;
            String name = service.name;
            if (name != null && name.startsWith(".")) name = service.packageName + name;
            if (expectedName.equals(name)) return true;
        }
        return false;
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout layout = vertical();
        layout.setPadding(dp(17), dp(18), dp(17), dp(18));
        layout.setBackground(round(CARD, 18, BORDER));
        return layout;
    }

    private LinearLayout.LayoutParams cardParams(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = bottomMargin;
        return params;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(sizeSp);
        label.setTextColor(color);
        label.setIncludeFontPadding(false);
        label.setTypeface(Typeface.create(
                bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return label;
    }

    private GradientDrawable round(int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable oval(int fill) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
