package com.darren.quietfeed;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Watches supported apps and sends the user Home from opened short-video viewers. */
public final class ShieldService extends AccessibilityService {
    private static final String INSTAGRAM = "com.instagram.android";
    private static final String FACEBOOK = "com.facebook.katana";
    private static final String PREFS = "quietfeed_prefs";
    private static final long INSPECTION_DELAY_MS = 100;
    private static final long DM_SCROLL_ARM_DELAY_MS = 300;
    private static final long EXIT_COOLDOWN_MS = 1200;
    private static final long EXIT_NOTICE_LEAD_MS = 220;
    private static final long EXIT_NOTICE_HOLD_MS = 1400;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Rect tempBounds = new Rect();
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private WindowManager windowManager;
    private View overlay;
    private View exitNotice;
    private Runnable pendingHomeAction;
    private String targetPackage = "";
    private ScreenRules.Screen lastInstagramScreen = ScreenRules.Screen.OTHER;
    private long pendingDmOpenUntil;
    private long pendingDmOpenStartedAt;
    private long navigationGraceUntil;
    private boolean dmReelActive;
    private long dmReelAllowedAt;
    private long exitSuppressedUntil;
    private long nextInspectionAt;
    private final Runnable inspection = new Runnable() {
        @Override public void run() {
            nextInspectionAt = 0;
            inspectCurrentScreen();
        }
    };
    private final Runnable dismissExitNotice = new Runnable() {
        @Override public void run() { hideExitNotice(); }
    };

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        preferenceListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if ("instagram_mode".equals(key) || "facebook_block_reels".equals(key)) {
                    handler.removeCallbacks(inspection);
                    nextInspectionAt = 0;
                    scheduleInspection(0);
                }
            }
        };
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        scheduleInspection(0);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String eventPackage = event.getPackageName().toString();
        if (getPackageName().equals(eventPackage)) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                String className = asString(event.getClassName());
                if (className.contains("MainActivity") || findTargetRoot() == null) {
                    clearTargetState();
                    hideOverlay();
                }
            }
            return; // Events from our settings screen or DM navigation overlay.
        }

        if (!isTarget(eventPackage)) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                if (findTargetRoot() != null) {
                    // Keyboard and system dialogs can appear on top of an Instagram chat.
                    hideOverlay();
                    return;
                }
                clearTargetState();
                hideOverlay();
            }
            return;
        }

        if (!eventPackage.equals(targetPackage)) {
            targetPackage = eventPackage;
            lastInstagramScreen = ScreenRules.Screen.OTHER;
            pendingDmOpenUntil = 0;
            pendingDmOpenStartedAt = 0;
            navigationGraceUntil = 0;
            dmReelActive = false;
            dmReelAllowedAt = 0;
            exitSuppressedUntil = 0;
        }

        final long now = SystemClock.elapsedRealtime();
        if (now < exitSuppressedUntil) {
            scheduleInspection(exitSuppressedUntil - now + 25);
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                if (INSTAGRAM.equals(eventPackage) && "dm".equals(instagramMode())) {
                    if (lastInstagramScreen == ScreenRules.Screen.DM_THREAD && isPossibleMediaOpen(source)) {
                        // The next Reel viewer is eligible only when reached directly from a chat.
                        pendingDmOpenUntil = now + 3000;
                        pendingDmOpenStartedAt = now;
                    } else if (lastInstagramScreen != ScreenRules.Screen.DM_THREAD) {
                        pendingDmOpenUntil = 0;
                        pendingDmOpenStartedAt = 0;
                    }
                }
            }
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED
                && INSTAGRAM.equals(eventPackage) && dmReelActive
                && lastInstagramScreen == ScreenRules.Screen.REEL
                && now >= dmReelAllowedAt + DM_SCROLL_ARM_DELAY_MS
                && isLargeScroll(event)) {
            exitTarget(eventPackage);
            return;
        }

        scheduleInspection(INSPECTION_DELAY_MS);
    }

    @Override public void onInterrupt() {
        hideOverlay();
        cancelPendingExit();
        hideExitNotice();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(inspection);
        cancelPendingExit();
        hideExitNotice();
        nextInspectionAt = 0;
        if (preferences != null && preferenceListener != null) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        }
        hideOverlay();
        super.onDestroy();
    }

    private void clearTargetState() {
        handler.removeCallbacks(inspection);
        nextInspectionAt = 0;
        targetPackage = "";
        lastInstagramScreen = ScreenRules.Screen.OTHER;
        pendingDmOpenUntil = 0;
        pendingDmOpenStartedAt = 0;
        navigationGraceUntil = 0;
        dmReelActive = false;
        dmReelAllowedAt = 0;
        exitSuppressedUntil = 0;
    }

    private void inspectCurrentScreen() {
        long now = SystemClock.elapsedRealtime();
        if (now < exitSuppressedUntil) {
            scheduleInspection(exitSuppressedUntil - now + 25);
            return;
        }
        AccessibilityNodeInfo root = findTargetRoot();
        if (root == null) {
            return;
        }
        String packageName = String.valueOf(root.getPackageName());
        if (!isTarget(packageName)) return;
        targetPackage = packageName;

        // A disabled control should not inspect that app's text at all.
        if (INSTAGRAM.equals(packageName) && "off".equals(instagramMode())) {
            dmReelActive = false;
            hideOverlay();
            return;
        }
        if (FACEBOOK.equals(packageName) && !facebookEnabled()) {
            hideOverlay();
            return;
        }

        List<ScreenRules.NodeData> nodes = new ArrayList<ScreenRules.NodeData>();
        collect(root, nodes, 0);
        int displayHeight = getResources().getDisplayMetrics().heightPixels;
        int displayWidth = getResources().getDisplayMetrics().widthPixels;

        if (INSTAGRAM.equals(packageName)) {
            String mode = instagramMode();
            ScreenRules.Screen screen = ScreenRules.instagram(nodes, displayHeight, displayWidth);
            ScreenRules.Screen previousScreen = lastInstagramScreen;
            lastInstagramScreen = screen;

            if ("reels".equals(mode)) {
                dmReelActive = false;
                dmReelAllowedAt = 0;
                if (screen == ScreenRules.Screen.REEL) exitTarget(packageName);
                else hideOverlay();
                return;
            }

            // DM mode deliberately fails closed when the screen cannot be identified.
            if (screen == ScreenRules.Screen.DM_THREAD || screen == ScreenRules.Screen.DM_INBOX
                    || screen == ScreenRules.Screen.LOGIN) {
                dmReelActive = false;
                dmReelAllowedAt = 0;
                if (screen != ScreenRules.Screen.DM_THREAD) {
                    pendingDmOpenUntil = 0;
                    pendingDmOpenStartedAt = 0;
                }
                hideOverlay();
            } else if (screen == ScreenRules.Screen.REEL) {
                // A tapped view may disappear before Android delivers its click event.
                // A direct transition from a confirmed chat to a Reel is also origin evidence.
                if (!dmReelActive && (now < pendingDmOpenUntil
                        || previousScreen == ScreenRules.Screen.DM_THREAD)) {
                    dmReelActive = true;
                    dmReelAllowedAt = now;
                    pendingDmOpenUntil = 0;
                    pendingDmOpenStartedAt = 0;
                }
                if (dmReelActive) hideOverlay();
                else exitTarget(packageName);
            } else if (now < navigationGraceUntil) {
                dmReelActive = false;
                dmReelAllowedAt = 0;
                if (now > pendingDmOpenStartedAt + 600) {
                    pendingDmOpenUntil = 0;
                    pendingDmOpenStartedAt = 0;
                }
                hideOverlay();
                scheduleAfterGrace();
            } else {
                dmReelActive = false;
                dmReelAllowedAt = 0;
                if (now > pendingDmOpenStartedAt + 600) {
                    pendingDmOpenUntil = 0;
                    pendingDmOpenStartedAt = 0;
                }
                showDmGate();
            }
        } else if (FACEBOOK.equals(packageName)) {
            boolean reel = ScreenRules.facebookReel(nodes, displayHeight, displayWidth);
            if (reel) exitTarget(packageName);
            else hideOverlay();
        }
    }

    private void scheduleAfterGrace() {
        long wait = navigationGraceUntil - SystemClock.elapsedRealtime();
        if (wait > 0) scheduleInspection(wait + 25);
    }

    private void scheduleInspection(long delayMs) {
        long when = SystemClock.elapsedRealtime() + delayMs;
        if (nextInspectionAt != 0 && nextInspectionAt <= when) return;
        handler.removeCallbacks(inspection);
        nextInspectionAt = when;
        handler.postDelayed(inspection, delayMs);
    }

    private void exitTarget(String packageName) {
        AccessibilityNodeInfo foreground = findTargetRoot();
        if (foreground == null || foreground.getPackageName() == null
                || !packageName.equals(foreground.getPackageName().toString())) return;
        long now = SystemClock.elapsedRealtime();
        if (pendingHomeAction != null) return;
        if (now < exitSuppressedUntil) return;
        exitSuppressedUntil = now + EXIT_COOLDOWN_MS;
        dmReelActive = false;
        dmReelAllowedAt = 0;
        pendingDmOpenUntil = 0;
        pendingDmOpenStartedAt = 0;
        hideOverlay();
        showExitNotice();
        cancelPendingExit();
        pendingHomeAction = new Runnable() {
            @Override public void run() {
                pendingHomeAction = null;
                AccessibilityNodeInfo current = findTargetRoot();
                if (current == null || current.getPackageName() == null
                        || !packageName.equals(current.getPackageName().toString())) {
                    hideExitNotice();
                    return;
                }
                // Accessibility can navigate Home; it cannot force-stop Meta apps.
                if (performGlobalAction(GLOBAL_ACTION_HOME)) {
                    handler.removeCallbacks(dismissExitNotice);
                    handler.postDelayed(dismissExitNotice, EXIT_NOTICE_HOLD_MS);
                } else {
                    hideExitNotice();
                    exitSuppressedUntil = 0;
                    scheduleInspection(INSPECTION_DELAY_MS);
                }
            }
        };
        handler.postDelayed(pendingHomeAction, EXIT_NOTICE_LEAD_MS);
    }

    private void cancelPendingExit() {
        if (pendingHomeAction != null) handler.removeCallbacks(pendingHomeAction);
        pendingHomeAction = null;
    }

    private void showExitNotice() {
        if (windowManager == null) return;
        hideExitNotice();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(22), dp(15), dp(22), dp(15));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xEE192335);
        background.setCornerRadius(dp(18));
        card.setBackground(background);
        card.setElevation(dp(12));

        TextView title = new TextView(this);
        title.setText("Reel blocked");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView detail = new TextView(this);
        detail.setText("Quiet Feed is taking you Home");
        detail.setTextColor(0xFFD4E0ED);
        detail.setTextSize(13);
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(4);
        card.addView(detail, detailParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        try {
            windowManager.addView(card, params);
            exitNotice = card;
        } catch (RuntimeException ignored) { }
    }

    private void hideExitNotice() {
        handler.removeCallbacks(dismissExitNotice);
        if (exitNotice != null && windowManager != null) {
            try { windowManager.removeView(exitNotice); } catch (RuntimeException ignored) { }
        }
        exitNotice = null;
    }

    private String instagramMode() {
        if (preferences == null) preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        return preferences.getString("instagram_mode", "reels");
    }

    private boolean facebookEnabled() {
        if (preferences == null) preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        return preferences.getBoolean("facebook_block_reels", true);
    }

    private static boolean isTarget(String name) {
        return INSTAGRAM.equals(name) || FACEBOOK.equals(name);
    }

    private AccessibilityNodeInfo findTargetRoot() {
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null && active.getPackageName() != null
                && isTarget(active.getPackageName().toString())) return active;

        // A background Instagram/Facebook window must not trigger a Home action.
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                if (!window.isActive() && !window.isFocused()) continue;
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && root.getPackageName() != null
                        && isTarget(root.getPackageName().toString())) return root;
            }
        }
        return null;
    }

    private void collect(AccessibilityNodeInfo node, List<ScreenRules.NodeData> out, int depth) {
        if (node == null || depth > 35 || out.size() >= 500) return;
        out.add(describe(node));
        int children = Math.min(node.getChildCount(), 100);
        for (int i = 0; i < children && out.size() < 500; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collect(child, out, depth + 1);
        }
    }

    private ScreenRules.NodeData describe(AccessibilityNodeInfo node) {
        node.getBoundsInScreen(tempBounds);
        return new ScreenRules.NodeData(asString(node.getText()), asString(node.getContentDescription()),
                Build.VERSION.SDK_INT >= 26 ? asString(node.getHintText()) : "",
                node.getViewIdResourceName(), asString(node.getClassName()),
                node.isSelected(), node.isClickable(), tempBounds.left,
                tempBounds.top, tempBounds.bottom,
                tempBounds.width(), tempBounds.height());
    }

    private static String asString(CharSequence value) { return value == null ? "" : value.toString(); }

    private boolean isPossibleMediaOpen(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int level = 0; current != null && level < 3; level++, current = current.getParent()) {
            ScreenRules.NodeData n = describe(current);
            if (level == 0 && (n.className.contains("edittext") || n.idContains("composer")
                    || n.labelIs("back") || n.labelIs("send") || n.labelIs("camera")
                    || n.labelContains("call") || n.labelIs("more options"))) return false;
            if (n.labelContains("reel") || n.labelContains("video")
                    || n.idContains("reel") || n.idContains("clips") || n.idContains("video")) return true;
            int minSize = dp(56);
            if (level == 0 && n.className.contains("imageview")
                    && n.width > minSize && n.height > minSize
                    && n.top > getResources().getDisplayMetrics().heightPixels / 5) return true;
        }
        return false;
    }

    private boolean isLargeScroll(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return false;
        source.getBoundsInScreen(tempBounds);
        int height = getResources().getDisplayMetrics().heightPixels;
        int width = getResources().getDisplayMetrics().widthPixels;
        if (tempBounds.height() < height * 2 / 3 || tempBounds.width() < width * 2 / 3) return false;
        // Some pagers report zero delta even when a new Reel becomes visible.
        return true;
    }

    private void showDmGate() {
        if (windowManager == null || overlay != null) return;

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xF20B1120);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(28), dp(24), dp(24));
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(0xFF192335);
        cardBackground.setCornerRadius(dp(24));
        card.setBackground(cardBackground);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("QUIET FEED");
        eyebrow.setTextColor(0xFF70E0CC);
        eyebrow.setTextSize(12);
        eyebrow.setLetterSpacing(0.16f);
        card.addView(eyebrow);

        TextView title = new TextView(this);
        title.setText("Messages only");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(25);
        title.setPadding(0, dp(12), 0, dp(8));
        card.addView(title);

        TextView body = new TextView(this);
        body.setText("Instagram is limited to your inbox and chats. Reels opened directly from a chat can play until you scroll.");
        body.setTextColor(0xFFBCC9D8);
        body.setTextSize(16);
        body.setLineSpacing(dp(3), 1f);
        card.addView(body);

        Button primary = new Button(this);
        primary.setText("Open messages");
        primary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openInstagramMessages(); }
        });
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        primaryParams.topMargin = dp(24);
        card.addView(primary, primaryParams);

        Button leave = new Button(this);
        leave.setText("Leave app");
        card.addView(leave, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        leave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                hideOverlay();
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
        });

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        cardParams.leftMargin = dp(20);
        cardParams.rightMargin = dp(20);
        frame.addView(card, cardParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(frame, params);
            overlay = frame;
        } catch (RuntimeException ignored) { }
    }

    private void hideOverlay() {
        if (overlay != null && windowManager != null) {
            try { windowManager.removeView(overlay); } catch (RuntimeException ignored) { }
        }
        overlay = null;
    }

    private void openInstagramMessages() {
        AccessibilityNodeInfo root = findTargetRoot();
        AccessibilityNodeInfo button = findMessagesButton(root, 0);
        hideOverlay();
        navigationGraceUntil = SystemClock.elapsedRealtime() + 6000;
        boolean clicked = button != null && clickNodeOrParent(button);
        if (!clicked) Toast.makeText(this, "Tap the Instagram messages icon now", Toast.LENGTH_LONG).show();
        scheduleAfterGrace();
    }

    private AccessibilityNodeInfo findMessagesButton(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 30) return null;
        ScreenRules.NodeData n = describe(node);
        if ((n.labelIs("messages") || n.labelIs("inbox") || n.labelIs("direct")
                || n.idContains("direct_inbox") || n.idContains("direct_tab"))
                && n.top < getResources().getDisplayMetrics().heightPixels / 3) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findMessagesButton(node.getChild(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; current != null && i < 4; i++, current = current.getParent()) {
            if (current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        return false;
    }

    private int dp(float dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }
}
