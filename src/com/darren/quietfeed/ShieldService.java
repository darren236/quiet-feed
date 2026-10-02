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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Watches supported apps and sends the user Home from opened short-video viewers. */
public final class ShieldService extends AccessibilityService {
    private static final String INSTAGRAM = "com.instagram.android";
    private static final String FACEBOOK = "com.facebook.katana";
    private static final String TIKTOK = "com.zhiliaoapp.musically";
    private static final String TIKTOK_SG = "com.ss.android.ugc.trill";
    private static final String PREFS = "quietfeed_prefs";
    private static final long INSPECTION_DELAY_MS = 100;
    private static final long DM_SCROLL_ARM_DELAY_MS = 300;
    private static final long EXIT_COOLDOWN_MS = 1200;
    private static final long EXIT_NOTICE_LEAD_MS = 220;
    private static final long EXIT_NOTICE_HOLD_MS = 1400;
    private static final long TIKTOK_OPEN_GRACE_MS = 4000;
    private static final long TIKTOK_TRANSITION_GRACE_MS = 1200;
    private static final long COMMENTS_TRANSITION_MS = 1200;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Rect tempBounds = new Rect();
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private WindowManager windowManager;
    private View overlay;
    private String overlayPackage = "";
    private View exitNotice;
    private Runnable pendingHomeAction;
    private String targetPackage = "";
    private ScreenRules.Screen lastInstagramScreen = ScreenRules.Screen.OTHER;
    private final ChatVideoOrigin instagramOrigin = new ChatVideoOrigin();
    private long commentsTransitionUntil;
    private long navigationGraceUntil;
    private boolean dmReelActive;
    private long dmReelAllowedAt;
    private ScreenRules.Screen lastTikTokScreen = ScreenRules.Screen.OTHER;
    private long pendingTikTokOpenUntil;
    private long pendingTikTokOpenStartedAt;
    private long lastTikTokChatAt;
    private long tiktokNavigationGraceUntil;
    private boolean tiktokSharedVideoActive;
    private long tiktokVideoAllowedAt;
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
                if ("instagram_mode".equals(key) || "facebook_block_reels".equals(key)
                        || "tiktok_mode".equals(key)) {
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
                if (className.contains("MainActivity")) {
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

        setTargetPackage(eventPackage);

        final long now = SystemClock.elapsedRealtime();
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo source = event.getSource();
            if (isCommentOpenClick(event, source)) {
                commentsTransitionUntil = now + COMMENTS_TRANSITION_MS;
                cancelPendingExit();
                exitSuppressedUntil = 0;
                hideExitNotice();
                hideOverlay();
            }
            if (INSTAGRAM.equals(eventPackage) && "dm".equals(instagramMode())) {
                boolean chat = false;
                AccessibilityNodeInfo root = findTargetRoot();
                if (root != null && INSTAGRAM.equals(asString(root.getPackageName()))
                        && ScreenRules.instagram(collectScreenNodes(root),
                                getResources().getDisplayMetrics().heightPixels,
                                getResources().getDisplayMetrics().widthPixels)
                                == ScreenRules.Screen.DM_THREAD) {
                    instagramOrigin.sawChat(now);
                    chat = true;
                }
                if (root == null && lastInstagramScreen == ScreenRules.Screen.DM_THREAD
                        && instagramOrigin.transitioning(now)) chat = true;
                if (chat) {
                    if ((source != null && isPossibleMediaOpen(source))
                            || eventLabel(event).contains("reel")
                            || eventLabel(event).contains("video")) instagramOrigin.mediaClick(now);
                }
                if (isInstagramNavigationClick(event, source)) {
                    instagramOrigin.navigationClick(now);
                    dmReelActive = false;
                    dmReelAllowedAt = 0;
                }
            } else if (source != null) {
                if (isTikTok(eventPackage) && "dm".equals(tiktokMode())) {
                    if (lastTikTokScreen == ScreenRules.Screen.DM_THREAD
                            && isPossibleMediaOpen(source)) {
                        pendingTikTokOpenUntil = now + TIKTOK_OPEN_GRACE_MS;
                        pendingTikTokOpenStartedAt = now;
                    } else {
                        pendingTikTokOpenUntil = 0;
                        pendingTikTokOpenStartedAt = 0;
                        if (lastTikTokScreen == ScreenRules.Screen.DM_THREAD) {
                            lastTikTokChatAt = 0;
                        }
                    }
                }
            }
        }

        if (now < commentsTransitionUntil
                && event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED
                && currentIsVideoViewer(eventPackage)) {
            commentsTransitionUntil = 0;
        }
        if (now < commentsTransitionUntil) {
            scheduleInspection(INSPECTION_DELAY_MS);
            return;
        }
        if (now < exitSuppressedUntil) {
            scheduleInspection(exitSuppressedUntil - now + 25);
            return;
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED
                && INSTAGRAM.equals(eventPackage) && dmReelActive
                && lastInstagramScreen == ScreenRules.Screen.REEL
                && now >= dmReelAllowedAt + DM_SCROLL_ARM_DELAY_MS
                && currentIsVideoViewer(eventPackage) && isLargeScroll(event)) {
            exitTarget(eventPackage, true);
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED
                && isTikTok(eventPackage) && tiktokSharedVideoActive
                && lastTikTokScreen == ScreenRules.Screen.REEL
                && now >= tiktokVideoAllowedAt + DM_SCROLL_ARM_DELAY_MS
                && currentIsVideoViewer(eventPackage) && isTikTokViewerScroll(event)) {
            exitTarget(eventPackage, true);
            return;
        }

        scheduleInspection(INSPECTION_DELAY_MS);
    }

    @Override public void onInterrupt() {
        clearTargetState();
        hideOverlay();
        cancelPendingExit();
        hideExitNotice();
        scheduleInspection(INSPECTION_DELAY_MS);
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
        instagramOrigin.clear();
        navigationGraceUntil = 0;
        commentsTransitionUntil = 0;
        dmReelActive = false;
        dmReelAllowedAt = 0;
        lastTikTokScreen = ScreenRules.Screen.OTHER;
        pendingTikTokOpenUntil = 0;
        pendingTikTokOpenStartedAt = 0;
        lastTikTokChatAt = 0;
        tiktokNavigationGraceUntil = 0;
        tiktokSharedVideoActive = false;
        tiktokVideoAllowedAt = 0;
        exitSuppressedUntil = 0;
    }

    private void setTargetPackage(String packageName) {
        if (packageName.equals(targetPackage)) return;
        clearTargetState();
        targetPackage = packageName;
        hideOverlay();
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
        setTargetPackage(packageName);

        // A disabled control should not inspect that app's text at all.
        if (INSTAGRAM.equals(packageName) && "off".equals(instagramMode())) {
            instagramOrigin.clear();
            dmReelActive = false;
            hideOverlay();
            return;
        }
        if (FACEBOOK.equals(packageName) && !facebookEnabled()) {
            hideOverlay();
            return;
        }
        if (isTikTok(packageName) && "off".equals(tiktokMode())) {
            lastTikTokScreen = ScreenRules.Screen.OTHER;
            tiktokSharedVideoActive = false;
            pendingTikTokOpenUntil = 0;
            pendingTikTokOpenStartedAt = 0;
            lastTikTokChatAt = 0;
            hideOverlay();
            return;
        }

        List<ScreenRules.NodeData> nodes = collectScreenNodes(root);
        int displayHeight = getResources().getDisplayMetrics().heightPixels;
        int displayWidth = getResources().getDisplayMetrics().widthPixels;

        if (ScreenRules.commentsPanel(nodes, displayHeight, displayWidth)) {
            // Keep any existing shared-video grant while reading or writing comments.
            cancelPendingExit();
            hideExitNotice();
            exitSuppressedUntil = 0;
            commentsTransitionUntil = 0;
            hideOverlay();
            return;
        }
        if (now < commentsTransitionUntil) {
            hideOverlay();
            scheduleInspection(100);
            return;
        }

        if (INSTAGRAM.equals(packageName)) {
            String mode = instagramMode();
            ScreenRules.Screen screen = ScreenRules.instagram(nodes, displayHeight, displayWidth);
            lastInstagramScreen = screen;

            if ("reels".equals(mode)) {
                instagramOrigin.clear();
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
                if (screen == ScreenRules.Screen.DM_THREAD) instagramOrigin.sawChat(now);
                else instagramOrigin.clear();
                hideOverlay();
            } else if (screen == ScreenRules.Screen.REEL) {
                if (!ScreenRules.instagramSharedReelEligible(nodes, displayHeight, true)) {
                    dmReelActive = false;
                    dmReelAllowedAt = 0;
                    instagramOrigin.clear();
                }
                // A bounded chat transition covers click events without a source.
                // Back/Close and feed-tab checks distinguish the opened shared viewer.
                if (!dmReelActive && instagramOrigin.canOpen(now)
                        && ScreenRules.instagramOpenedViewer(nodes, displayHeight)) {
                    dmReelActive = true;
                    dmReelAllowedAt = now;
                    instagramOrigin.clear();
                }
                if (dmReelActive) hideOverlay();
                else exitTarget(packageName);
            } else if (now < navigationGraceUntil || instagramOrigin.transitioning(now)) {
                dmReelActive = false;
                dmReelAllowedAt = 0;
                hideOverlay();
                scheduleInspection(150);
            } else {
                dmReelActive = false;
                dmReelAllowedAt = 0;
                instagramOrigin.clear();
                showDmGate(packageName);
            }
        } else if (FACEBOOK.equals(packageName)) {
            boolean reel = ScreenRules.facebookReel(nodes, displayHeight, displayWidth);
            if (reel) exitTarget(packageName);
            else hideOverlay();
        } else if (isTikTok(packageName)) {
            ScreenRules.Screen screen = ScreenRules.tiktok(nodes, displayHeight, displayWidth);
            if ("videos".equals(tiktokMode())) {
                tiktokSharedVideoActive = false;
                pendingTikTokOpenUntil = 0;
                lastTikTokChatAt = 0;
                lastTikTokScreen = screen;
                if (screen == ScreenRules.Screen.REEL) exitTarget(packageName);
                else hideOverlay();
                return;
            }
            ScreenRules.Screen previousScreen = lastTikTokScreen;
            lastTikTokScreen = screen;

            if (screen == ScreenRules.Screen.DM_THREAD
                    || screen == ScreenRules.Screen.DM_INBOX
                    || screen == ScreenRules.Screen.LOGIN) {
                tiktokSharedVideoActive = false;
                tiktokVideoAllowedAt = 0;
                tiktokNavigationGraceUntil = 0;
                if (screen != ScreenRules.Screen.DM_THREAD) {
                    pendingTikTokOpenUntil = 0;
                    pendingTikTokOpenStartedAt = 0;
                    lastTikTokChatAt = 0;
                } else {
                    lastTikTokChatAt = now;
                }
                hideOverlay();
            } else if (screen == ScreenRules.Screen.REEL) {
                boolean openedFromChat = ScreenRules.tiktokOpenedViewer(
                        nodes, displayHeight);
                boolean directChatTransition = lastTikTokChatAt > 0
                        && (previousScreen == ScreenRules.Screen.DM_THREAD
                                || now < lastTikTokChatAt + TIKTOK_TRANSITION_GRACE_MS);
                if (!tiktokSharedVideoActive && openedFromChat
                        && (now < pendingTikTokOpenUntil || directChatTransition)) {
                    tiktokSharedVideoActive = true;
                    tiktokVideoAllowedAt = now;
                    pendingTikTokOpenUntil = 0;
                    pendingTikTokOpenStartedAt = 0;
                    lastTikTokChatAt = 0;
                }
                if (tiktokSharedVideoActive && openedFromChat) hideOverlay();
                else showDmGate(packageName);
            } else if (now < tiktokNavigationGraceUntil
                    || (pendingTikTokOpenUntil > now
                            && now < pendingTikTokOpenStartedAt
                                    + TIKTOK_TRANSITION_GRACE_MS)) {
                tiktokSharedVideoActive = false;
                tiktokVideoAllowedAt = 0;
                hideOverlay();
                scheduleInspection(250);
            } else {
                tiktokSharedVideoActive = false;
                tiktokVideoAllowedAt = 0;
                if (now >= pendingTikTokOpenStartedAt + TIKTOK_TRANSITION_GRACE_MS) {
                    pendingTikTokOpenUntil = 0;
                    pendingTikTokOpenStartedAt = 0;
                    lastTikTokChatAt = 0;
                }
                showDmGate(packageName);
            }
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
        exitTarget(packageName, false);
    }

    private void exitTarget(String packageName, final boolean viewerSwipe) {
        AccessibilityNodeInfo foreground = findTargetRoot();
        if (foreground == null || foreground.getPackageName() == null
                || !packageName.equals(foreground.getPackageName().toString())) return;
        long now = SystemClock.elapsedRealtime();
        if (pendingHomeAction != null) return;
        if (now < exitSuppressedUntil) return;
        exitSuppressedUntil = now + EXIT_COOLDOWN_MS;
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
                // Accessibility can navigate Home; it cannot force-stop other apps.
                if (SystemClock.elapsedRealtime() < commentsTransitionUntil
                        || !shouldExitViewer(packageName, collectScreenNodes(current), viewerSwipe)) {
                    hideExitNotice();
                    exitSuppressedUntil = 0;
                    scheduleInspection(100);
                    return;
                }
                if (performGlobalAction(GLOBAL_ACTION_HOME)) {
                    dmReelActive = false;
                    dmReelAllowedAt = 0;
                    instagramOrigin.clear();
                    tiktokSharedVideoActive = false;
                    tiktokVideoAllowedAt = 0;
                    pendingTikTokOpenUntil = 0;
                    pendingTikTokOpenStartedAt = 0;
                    lastTikTokChatAt = 0;
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
        title.setText(isTikTok(targetPackage) ? ("videos".equals(tiktokMode())
                ? "TikTok blocked" : "Next video blocked") : "Reel blocked");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView detail = new TextView(this);
        detail.setText("Taking you Home");
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

    private String tiktokMode() {
        if (preferences == null) preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        return preferences.getString("tiktok_mode", "off");
    }

    private boolean facebookEnabled() {
        if (preferences == null) preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        return preferences.getBoolean("facebook_block_reels", true);
    }

    private static boolean isTarget(String name) {
        return INSTAGRAM.equals(name) || FACEBOOK.equals(name) || isTikTok(name);
    }

    private static boolean isTikTok(String name) {
        return TIKTOK.equals(name) || TIKTOK_SG.equals(name);
    }

    private AccessibilityNodeInfo findTargetRoot() {
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null && active.getPackageName() != null
                && isTarget(active.getPackageName().toString())) return active;

        // A background social app window must not trigger a Home action.
        List<AccessibilityWindowInfo> windows = getWindows();
        boolean ourGateFocused = active != null
                && getPackageName().equals(asString(active.getPackageName())) && overlay != null;
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                if (!window.isActive() && !window.isFocused() && !ourGateFocused) continue;
                if (ourGateFocused && window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && root.getPackageName() != null
                        && root.isVisibleToUser()
                        && isTarget(root.getPackageName().toString())
                        && (!ourGateFocused || targetPackage.equals(asString(root.getPackageName())))) return root;
            }
        }
        return null;
    }

    private List<ScreenRules.NodeData> collectScreenNodes(AccessibilityNodeInfo foreground) {
        List<ScreenRules.NodeData> nodes = new ArrayList<ScreenRules.NodeData>();
        if (foreground == null) return nodes;
        String pkg = asString(foreground.getPackageName());
        List<AccessibilityWindowInfo> available = getWindows();
        Set<Integer> collected = new HashSet<Integer>();
        if (available != null) {
            List<AccessibilityWindowInfo> windows = new ArrayList<AccessibilityWindowInfo>(available);
            Collections.sort(windows, new Comparator<AccessibilityWindowInfo>() {
                @Override public int compare(AccessibilityWindowInfo a, AccessibilityWindowInfo b) {
                    return Integer.compare(b.getLayer(), a.getLayer());
                }
            });
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && root.isVisibleToUser() && pkg.equals(asString(root.getPackageName()))) {
                    // Comment dialogs can be separate windows; inspect them before the underlying video.
                    collect(root, nodes, 0);
                    collected.add(window.getId());
                }
            }
        }
        if (!collected.contains(foreground.getWindowId())) collect(foreground, nodes, 0);
        return nodes;
    }

    private boolean currentIsVideoViewer(String packageName) {
        AccessibilityNodeInfo root = findTargetRoot();
        if (root == null || !packageName.equals(asString(root.getPackageName()))) return false;
        List<ScreenRules.NodeData> nodes = collectScreenNodes(root);
        int h = getResources().getDisplayMetrics().heightPixels;
        int w = getResources().getDisplayMetrics().widthPixels;
        if (INSTAGRAM.equals(packageName)) return ScreenRules.instagram(nodes, h, w) == ScreenRules.Screen.REEL;
        return ScreenRules.tiktok(nodes, h, w) == ScreenRules.Screen.REEL;
    }

    private boolean shouldExitViewer(String pkg, List<ScreenRules.NodeData> nodes, boolean swipe) {
        int h = getResources().getDisplayMetrics().heightPixels;
        int w = getResources().getDisplayMetrics().widthPixels;
        if (ScreenRules.commentsPanel(nodes, h, w)) return false;
        if (FACEBOOK.equals(pkg)) return facebookEnabled() && ScreenRules.facebookReel(nodes, h, w);
        if (INSTAGRAM.equals(pkg)) return !"off".equals(instagramMode())
                && ScreenRules.instagram(nodes, h, w) == ScreenRules.Screen.REEL
                && (swipe || "reels".equals(instagramMode()) || !dmReelActive);
        return !"off".equals(tiktokMode()) && ScreenRules.tiktok(nodes, h, w) == ScreenRules.Screen.REEL
                && (swipe || "videos".equals(tiktokMode()) || !tiktokSharedVideoActive);
    }

    private void collect(AccessibilityNodeInfo node, List<ScreenRules.NodeData> out, int depth) {
        if (node == null || depth > 35 || out.size() >= 1200 || !node.isVisibleToUser()) return;
        out.add(describe(node));
        int children = Math.min(node.getChildCount(), 100);
        for (int i = 0; i < children && out.size() < 1200; i++) {
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

    private String eventLabel(AccessibilityEvent event) {
        return (asString(event.getContentDescription()) + " " + event.getText().toString())
                .toLowerCase(Locale.ROOT);
    }

    private boolean isCommentOpenClick(AccessibilityEvent event, AccessibilityNodeInfo source) {
        if (eventLabel(event).contains("comment")) return true;
        for (int i = 0; source != null && i < 3; i++, source = source.getParent()) {
            ScreenRules.NodeData n = describe(source);
            if (n.labelContains("comment") || n.idContains("comment")) return true;
        }
        return false;
    }

    private boolean isInstagramNavigationClick(AccessibilityEvent event, AccessibilityNodeInfo source) {
        int h = getResources().getDisplayMetrics().heightPixels;
        for (int i = 0; source != null && i < 3; i++, source = source.getParent()) {
            ScreenRules.NodeData n = describe(source);
            boolean navigationPosition = n.top < h / 4 || n.top > h * 4 / 5;
            if (navigationPosition && (n.labelIs("back") || n.labelIs("close")
                    || n.labelIs("home") || n.labelIs("reels") || n.labelIs("explore")
                    || n.labelIs("search") || n.labelIs("profile")
                    || n.idContains("clips_tab") || n.idContains("home_tab")
                    || n.idContains("profile_tab") || n.idContains("search_tab"))) return true;
            if (n.labelIs("view profile") || n.labelIs("open profile")
                    || (n.top < h / 4 && (n.idContains("thread_title")
                            || n.idContains("thread_header") || n.idContains("profile_button")))) return true;
        }
        String label = eventLabel(event).replace("[", "").replace("]", "").trim();
        return label.equals("back") || label.equals("close") || label.equals("home")
                || label.equals("reels") || label.equals("explore") || label.equals("profile");
    }

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

    private boolean isCommentScrollSource(AccessibilityNodeInfo node) {
        for (int i = 0; node != null && i < 5; i++, node = node.getParent()) {
            ScreenRules.NodeData n = describe(node);
            if (n.idContains("comment") || n.labelIs("comments")) return true;
        }
        return false;
    }

    private boolean isLargeScroll(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        if (source == null || isCommentScrollSource(source)) return false;
        source.getBoundsInScreen(tempBounds);
        int height = getResources().getDisplayMetrics().heightPixels;
        int width = getResources().getDisplayMetrics().widthPixels;
        if (tempBounds.height() < height * 2 / 3 || tempBounds.width() < width * 2 / 3) return false;
        // Some pagers report zero delta even when a new Reel becomes visible.
        return true;
    }

    private boolean isTikTokViewerScroll(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        if (source == null || isCommentScrollSource(source)) return false;
        ScreenRules.NodeData sourceData = describe(source);
        if (sourceData.idContains("comment") || sourceData.labelContains("comments")
                || sourceData.className.contains("scrollview")
                || sourceData.className.contains("recyclerview")
                || sourceData.className.contains("listview")) return false;
        source.getBoundsInScreen(tempBounds);
        int height = getResources().getDisplayMetrics().heightPixels;
        int width = getResources().getDisplayMetrics().widthPixels;
        if (tempBounds.width() < width * 2 / 3
                || tempBounds.height() < height * 3 / 5) return false;
        if (sourceData.idContains("video_pager") || sourceData.idContains("feed_pager")
                || sourceData.className.contains("viewpager")) return true;
        if (Build.VERSION.SDK_INT < 28) return false;
        long vertical = Math.abs((long) event.getScrollDeltaY());
        long horizontal = Math.abs((long) event.getScrollDeltaX());
        return vertical >= height / 3 && vertical > horizontal;
    }

    private void showDmGate(String packageName) {
        if (windowManager == null) return;
        if (overlay != null && packageName.equals(overlayPackage)) return;
        hideOverlay();
        final boolean tiktok = isTikTok(packageName);

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
        eyebrow.setText("QUIETFEED");
        eyebrow.setTextColor(0xFF70E0CC);
        eyebrow.setTextSize(12);
        eyebrow.setLetterSpacing(0.16f);
        card.addView(eyebrow);

        TextView title = new TextView(this);
        title.setText(tiktok ? "Chats only" : "Messages only");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(25);
        title.setPadding(0, dp(12), 0, dp(8));
        card.addView(title);

        TextView body = new TextView(this);
        body.setText(tiktok
                ? "TikTok is limited to your inbox and chats. A video opened from a chat can play until you swipe. The Friends feed is still blocked."
                : "Instagram is limited to your inbox and chats. Reels opened directly from a chat can play until you scroll.");
        body.setTextColor(0xFFBCC9D8);
        body.setTextSize(16);
        body.setLineSpacing(dp(3), 1f);
        card.addView(body);

        Button primary = new Button(this);
        primary.setText(tiktok ? "Open TikTok inbox" : "Open messages");
        primary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (tiktok) openTikTokInbox(packageName);
                else openInstagramMessages();
            }
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
            overlayPackage = packageName;
        } catch (RuntimeException ignored) { }
    }

    private void hideOverlay() {
        if (overlay != null && windowManager != null) {
            try { windowManager.removeView(overlay); } catch (RuntimeException ignored) { }
        }
        overlay = null;
        overlayPackage = "";
    }

    private void openInstagramMessages() {
        hideOverlay();
        instagramOrigin.clear();
        dmReelActive = false;
        navigationGraceUntil = SystemClock.elapsedRealtime() + 2000;
        // Reacquire the app tree after removing our overlay; old nodes can be stale.
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!INSTAGRAM.equals(targetPackage)) return;
                AccessibilityNodeInfo root = findTargetRoot();
                if (root == null || !INSTAGRAM.equals(asString(root.getPackageName()))) return;
                AccessibilityNodeInfo button = findMessagesButton(root, 0);
                boolean clicked = button != null && clickNodeOrParent(button);
                if (!clicked) {
                    Toast.makeText(ShieldService.this,
                            "Instagram's DM button wasn't found. Tap its Messages tab.",
                            Toast.LENGTH_LONG).show();
                }
                scheduleInspection(250);
                scheduleAfterGrace();
            }
        }, 150);
        scheduleAfterGrace();
    }

    private void openTikTokInbox(String packageName) {
        AccessibilityNodeInfo root = findVisibleTargetRootForGate(packageName);
        AccessibilityNodeInfo button = findTikTokInboxButton(root, 0);
        hideOverlay();
        pendingTikTokOpenUntil = 0;
        pendingTikTokOpenStartedAt = 0;
        lastTikTokChatAt = 0;
        tiktokSharedVideoActive = false;
        tiktokNavigationGraceUntil = SystemClock.elapsedRealtime() + 2000;
        boolean clicked = button != null && clickNodeOrParent(button);
        if (!clicked) {
            Toast.makeText(this, "Tap TikTok's Inbox tab now", Toast.LENGTH_LONG).show();
        }
        scheduleInspection(250);
    }

    private AccessibilityNodeInfo findVisibleTargetRootForGate(String packageName) {
        AccessibilityNodeInfo active = findTargetRoot();
        if (active != null && active.getPackageName() != null
                && packageName.equals(active.getPackageName().toString())) return active;
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return null;
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null && root.getPackageName() != null
                    && packageName.equals(root.getPackageName().toString())) return root;
        }
        return null;
    }

    private AccessibilityNodeInfo findTikTokInboxButton(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 30) return null;
        ScreenRules.NodeData n = describe(node);
        if ((n.labelContains("inbox") || n.idContains("inbox_tab")
                || n.idContains("tab_inbox"))
                && n.top > getResources().getDisplayMetrics().heightPixels * 2 / 3) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findTikTokInboxButton(node.getChild(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findMessagesButton(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 30 || !node.isVisibleToUser()) return null;
        ScreenRules.NodeData n = describe(node);
        if (ScreenRules.instagramMessagesButton(n,
                getResources().getDisplayMetrics().heightPixels) && hasClickableParent(node)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findMessagesButton(node.getChild(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private boolean hasClickableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int i = 0; current != null && i < 4; i++, current = current.getParent()) {
            if (current.isClickable()) return true;
        }
        return false;
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
