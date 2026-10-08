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
    private final SharedReelComments instagramComments = new SharedReelComments();
    private final ReelViewerState instagramViewer = new ReelViewerState();
    private long commentsTransitionUntil;
    private long navigationGraceUntil;
    private boolean dmReelActive;
    private long dmReelAllowedAt;
    private ScreenRules.Screen lastTikTokScreen = ScreenRules.Screen.OTHER;
    private final ChatVideoOrigin tiktokOrigin = new ChatVideoOrigin();
    private final SharedReelComments tiktokComments = new SharedReelComments();
    private final ReelViewerState tiktokViewer = ReelViewerState.forTikTok();
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
                    if ("instagram_mode".equals(key)) {
                        instagramComments.clear();
                        instagramOrigin.clear();
                        clearInstagramReel();
                    }
                    if ("tiktok_mode".equals(key)) {
                        tiktokOrigin.clear();
                        clearTikTokVideo();
                        lastTikTokScreen = ScreenRules.Screen.OTHER;
                        tiktokNavigationGraceUntil = 0;
                    }
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
            boolean commentClick = isCommentOpenClick(event, source);
            if (commentClick) {
                if (INSTAGRAM.equals(eventPackage) && "dm".equals(instagramMode())) {
                    instagramComments.open(dmReelActive, now);
                    instagramViewer.resetScroll();
                }
                if (isTikTok(eventPackage) && "dm".equals(tiktokMode())) {
                    tiktokComments.open(tiktokSharedVideoActive, now);
                    tiktokViewer.resetScroll();
                }
                commentsTransitionUntil = now + COMMENTS_TRANSITION_MS;
                cancelPendingExit();
                exitSuppressedUntil = 0;
                hideExitNotice();
                hideOverlay();
            }
            if (INSTAGRAM.equals(eventPackage) && "dm".equals(instagramMode())) {
                boolean chat = false;
                boolean chatHandoff = lastInstagramScreen == ScreenRules.Screen.DM_THREAD
                        || instagramOrigin.canOpen(now);
                AccessibilityNodeInfo root = findTargetRoot();
                if (root != null && INSTAGRAM.equals(asString(root.getPackageName()))) {
                    List<ScreenRules.NodeData> clickNodes = collectScreenNodes(root);
                    int h = getResources().getDisplayMetrics().heightPixels;
                    int w = getResources().getDisplayMetrics().widthPixels;
                    ScreenRules.Screen clickScreen = ScreenRules.instagram(clickNodes, h, w);
                    chat = clickScreen == ScreenRules.Screen.DM_THREAD;
                    if (chat) instagramOrigin.sawChat(now);
                    chatHandoff = (lastInstagramScreen == ScreenRules.Screen.DM_THREAD
                            || instagramOrigin.canOpen(now))
                            && (clickScreen == ScreenRules.Screen.REEL
                                || (clickScreen == ScreenRules.Screen.OTHER
                                    && !ScreenRules.instagramFeedNavigation(clickNodes, h)
                                    && !ScreenRules.instagramProfileScreen(clickNodes, h, w)));
                }
                boolean navigationClick = isInstagramNavigationClick(event, source);
                boolean mediaClick = (source != null && isPossibleMediaOpen(source))
                        || eventLabel(event).contains("reel") || eventLabel(event).contains("video");
                // The click's source can still belong to the chat after the active root changed.
                if (!navigationClick && mediaClick && instagramOrigin.mediaClickFromChat(now, chat,
                        chatHandoff)) {
                    cancelPendingExit();
                    exitSuppressedUntil = 0;
                    hideExitNotice();
                }
                if (navigationClick
                        || (instagramComments.isOpen() && isBackOrCloseClick(event, source))) {
                    if (instagramComments.isOpen() && isBackOrCloseClick(event, source)) {
                        // Closing a sheet returns to the same allowed shared Reel.
                        instagramComments.requestClose(now);
                    } else {
                        instagramComments.clear();
                        instagramOrigin.navigationClick(now);
                        clearInstagramReel();
                    }
                }
            } else if (isTikTok(eventPackage) && "dm".equals(tiktokMode())) {
                boolean chat = false;
                boolean chatHandoff = lastTikTokScreen == ScreenRules.Screen.DM_THREAD
                        || tiktokOrigin.canOpen(now);
                AccessibilityNodeInfo root = findTargetRoot();
                if (root != null && eventPackage.equals(asString(root.getPackageName()))) {
                    List<ScreenRules.NodeData> clickNodes = collectScreenNodes(root);
                    int h = getResources().getDisplayMetrics().heightPixels;
                    int w = getResources().getDisplayMetrics().widthPixels;
                    ScreenRules.Screen clickScreen = ScreenRules.tiktok(clickNodes, h, w);
                    chat = clickScreen == ScreenRules.Screen.DM_THREAD;
                    if (chat) tiktokOrigin.sawChat(now);
                    chatHandoff = (lastTikTokScreen == ScreenRules.Screen.DM_THREAD
                            || tiktokOrigin.canOpen(now))
                            && (clickScreen == ScreenRules.Screen.REEL
                                || (clickScreen == ScreenRules.Screen.OTHER
                                    && !ScreenRules.tiktokFeedNavigation(clickNodes, h)
                                    && !ScreenRules.tiktokProfileScreen(clickNodes, h)));
                }
                boolean navigationClick = isTikTokNavigationClick(event, source);
                boolean mediaClick = (source != null && isPossibleMediaOpen(source))
                        || eventLabel(event).contains("video");
                if (!navigationClick && mediaClick
                        && tiktokOrigin.mediaClickFromChat(now, chat, chatHandoff)) {
                    cancelPendingExit();
                    exitSuppressedUntil = 0;
                    hideExitNotice();
                }
                if (navigationClick) {
                    if (tiktokComments.isOpen() && isBackOrCloseClick(event, source)) {
                        tiktokComments.requestClose(now);
                    } else {
                        tiktokOrigin.navigationClick(now);
                        clearTikTokVideo();
                    }
                }
            }
        }

        if (INSTAGRAM.equals(eventPackage) && dmReelActive && instagramComments.isOpen()
                && event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED
                && now >= dmReelAllowedAt + DM_SCROLL_ARM_DELAY_MS
                && currentIsVideoViewer(eventPackage)
                && isConfirmedViewerPaging(event)) {
            instagramComments.clear();
            commentsTransitionUntil = 0;
            exitTarget(eventPackage, true);
            return;
        }

        if (isTikTok(eventPackage) && tiktokSharedVideoActive && tiktokComments.isOpen()
                && event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED
                && now >= tiktokVideoAllowedAt + DM_SCROLL_ARM_DELAY_MS
                && currentIsVideoViewer(eventPackage) && isConfirmedViewerPaging(event)) {
            tiktokComments.clear();
            commentsTransitionUntil = 0;
            exitTarget(eventPackage, true);
            return;
        }

        if (INSTAGRAM.equals(eventPackage) && instagramComments.isOpen()
                && currentInstagramCommentsAllowed(now)) {
            instagramViewer.resetScroll();
            commentsTransitionUntil = 0;
            cancelPendingExit();
            exitSuppressedUntil = 0;
            hideExitNotice();
            hideOverlay();
            scheduleInspection(100);
            return;
        }

        if (isTikTok(eventPackage) && tiktokComments.isOpen() && currentTikTokCommentsAllowed(now)) {
            tiktokViewer.resetScroll();
            commentsTransitionUntil = 0;
            cancelPendingExit();
            exitSuppressedUntil = 0;
            hideExitNotice();
            hideOverlay();
            scheduleInspection(100);
            return;
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
                && currentIsVideoViewer(eventPackage)
                && isInstagramViewerPaging(event, now >= dmReelAllowedAt + DM_SCROLL_ARM_DELAY_MS)) {
            exitTarget(eventPackage, true);
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED
                && isTikTok(eventPackage) && tiktokSharedVideoActive
                && currentIsVideoViewer(eventPackage)
                && isTikTokViewerScroll(event, now >= tiktokVideoAllowedAt + DM_SCROLL_ARM_DELAY_MS)) {
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

    private void clearInstagramReel() {
        dmReelActive = false;
        dmReelAllowedAt = 0;
        instagramViewer.clear();
    }

    private void clearTikTokVideo() {
        tiktokSharedVideoActive = false;
        tiktokVideoAllowedAt = 0;
        tiktokComments.clear();
        tiktokViewer.clear();
    }

    private void clearTargetState() {
        handler.removeCallbacks(inspection);
        nextInspectionAt = 0;
        targetPackage = "";
        lastInstagramScreen = ScreenRules.Screen.OTHER;
        instagramOrigin.clear();
        instagramComments.clear();
        navigationGraceUntil = 0;
        commentsTransitionUntil = 0;
        clearInstagramReel();
        lastTikTokScreen = ScreenRules.Screen.OTHER;
        tiktokOrigin.clear();
        tiktokNavigationGraceUntil = 0;
        clearTikTokVideo();
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
            instagramComments.clear();
            clearInstagramReel();
            hideOverlay();
            return;
        }
        if (FACEBOOK.equals(packageName) && !facebookEnabled()) {
            hideOverlay();
            return;
        }
        if (isTikTok(packageName) && "off".equals(tiktokMode())) {
            lastTikTokScreen = ScreenRules.Screen.OTHER;
            tiktokOrigin.clear();
            clearTikTokVideo();
            hideOverlay();
            return;
        }

        List<ScreenRules.NodeData> nodes = collectScreenNodes(root);
        int displayHeight = getResources().getDisplayMetrics().heightPixels;
        int displayWidth = getResources().getDisplayMetrics().widthPixels;

        if (ScreenRules.commentsPanel(nodes, displayHeight, displayWidth)) {
            if (INSTAGRAM.equals(packageName) && "dm".equals(instagramMode())) {
                instagramComments.open(dmReelActive, now);
                instagramViewer.resetScroll();
            }
            if (isTikTok(packageName) && "dm".equals(tiktokMode())) {
                tiktokComments.open(tiktokSharedVideoActive, now);
                tiktokViewer.resetScroll();
            }
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
            boolean commentContent = ScreenRules.commentContent(nodes, displayHeight, displayWidth);
            if ("dm".equals(mode) && ScreenRules.commentPanelEvidence(nodes, displayHeight, displayWidth))
                instagramComments.open(dmReelActive, now);
            if (ScreenRules.instagramProfileScreen(nodes, displayHeight, displayWidth)) {
                instagramComments.clear();
                instagramOrigin.clear();
                clearInstagramReel();
            }
            if ("dm".equals(mode) && instagramComments.allows(screen, commentContent, dmReelActive, now)
                    && ScreenRules.instagramSharedReelEligible(nodes, displayHeight, true)) {
                cancelPendingExit();
                exitSuppressedUntil = 0;
                hideExitNotice();
                hideOverlay();
                return;
            }
            if ("dm".equals(mode) && lastInstagramScreen == ScreenRules.Screen.DM_THREAD
                    && ((screen == ScreenRules.Screen.REEL
                            && ScreenRules.instagramOpenedViewer(nodes, displayHeight))
                        || (screen == ScreenRules.Screen.OTHER
                            && !ScreenRules.instagramFeedNavigation(nodes, displayHeight)))
                    && !ScreenRules.instagramProfileScreen(nodes, displayHeight, displayWidth)) {
                instagramOrigin.leftChat(now);
            }
            lastInstagramScreen = screen;

            if ("reels".equals(mode)) {
                instagramOrigin.clear();
                instagramComments.clear();
                clearInstagramReel();
                if (screen == ScreenRules.Screen.REEL) exitTarget(packageName);
                else hideOverlay();
                return;
            }

            // DM mode deliberately fails closed when the screen cannot be identified.
            if (screen == ScreenRules.Screen.DM_THREAD || screen == ScreenRules.Screen.DM_INBOX
                    || screen == ScreenRules.Screen.LOGIN) {
                clearInstagramReel();
                if (screen == ScreenRules.Screen.DM_THREAD) instagramOrigin.sawChat(now);
                else instagramOrigin.clear();
                hideOverlay();
            } else if (screen == ScreenRules.Screen.REEL) {
                if (!ScreenRules.instagramSharedReelEligible(nodes, displayHeight, true)) {
                    instagramComments.clear();
                    clearInstagramReel();
                    instagramOrigin.clear();
                }
                // A bounded chat transition covers click events without a source.
                // Back/Close and feed-tab checks distinguish the opened shared viewer.
                if (!dmReelActive && instagramOrigin.canOpen(now)
                        && ScreenRules.instagramOpenedViewer(nodes, displayHeight,
                                instagramOrigin.hasMediaClick(now))) {
                    dmReelActive = true;
                    dmReelAllowedAt = now;
                    instagramViewer.clear();
                    instagramOrigin.clear();
                    cancelPendingExit();
                    exitSuppressedUntil = 0;
                    hideExitNotice();
                }
                if (dmReelActive) {
                    instagramViewer.sawViewer(now);
                    hideOverlay();
                }
                else exitTarget(packageName);
            } else if (dmReelActive && instagramViewer.loading(now)
                    && !ScreenRules.instagramFeedNavigation(nodes, displayHeight)) {
                // Partial trees during loading must not revoke an already allowed shared Reel.
                hideOverlay();
                scheduleInspection(150);
            } else if (now < navigationGraceUntil || instagramOrigin.transitioning(now)) {
                clearInstagramReel();
                hideOverlay();
                scheduleInspection(150);
            } else {
                clearInstagramReel();
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
                clearTikTokVideo();
                tiktokOrigin.clear();
                lastTikTokScreen = screen;
                if (screen == ScreenRules.Screen.REEL) exitTarget(packageName);
                else hideOverlay();
                return;
            }
            boolean profile = ScreenRules.tiktokProfileScreen(nodes, displayHeight);
            boolean eligible = ScreenRules.tiktokSharedVideoEligible(nodes, displayHeight) && !profile;
            if (!eligible) {
                tiktokOrigin.clear();
                clearTikTokVideo();
            }
            if (ScreenRules.commentPanelEvidence(nodes, displayHeight, displayWidth))
                tiktokComments.open(tiktokSharedVideoActive, now);
            if (eligible && tiktokComments.allows(screen,
                    ScreenRules.commentContent(nodes, displayHeight, displayWidth),
                    tiktokSharedVideoActive, now)) {
                cancelPendingExit();
                exitSuppressedUntil = 0;
                hideExitNotice();
                hideOverlay();
                return;
            }
            if (lastTikTokScreen == ScreenRules.Screen.DM_THREAD && eligible
                    && ((screen == ScreenRules.Screen.REEL
                            && ScreenRules.tiktokOpenedViewer(nodes, displayHeight))
                        || (screen == ScreenRules.Screen.OTHER
                            && !ScreenRules.tiktokFeedNavigation(nodes, displayHeight)))) {
                tiktokOrigin.leftChat(now);
            }
            lastTikTokScreen = screen;

            if (screen == ScreenRules.Screen.DM_THREAD
                    || screen == ScreenRules.Screen.DM_INBOX
                    || screen == ScreenRules.Screen.LOGIN) {
                clearTikTokVideo();
                tiktokNavigationGraceUntil = 0;
                if (screen == ScreenRules.Screen.DM_THREAD) tiktokOrigin.sawChat(now);
                else tiktokOrigin.clear();
                hideOverlay();
            } else if (screen == ScreenRules.Screen.REEL) {
                if (!tiktokSharedVideoActive && eligible && tiktokOrigin.canOpen(now)
                        && ScreenRules.tiktokOpenedViewer(nodes, displayHeight,
                                tiktokOrigin.hasMediaClick(now))) {
                    tiktokSharedVideoActive = true;
                    tiktokVideoAllowedAt = now;
                    tiktokViewer.clear();
                    tiktokOrigin.clear();
                    cancelPendingExit();
                    exitSuppressedUntil = 0;
                    hideExitNotice();
                }
                if (tiktokSharedVideoActive
                        && ScreenRules.tiktokOpenedViewer(nodes, displayHeight, true)) {
                    tiktokViewer.sawViewer(now);
                    hideOverlay();
                } else if (tiktokSharedVideoActive && tiktokViewer.loading(now)
                        && !ScreenRules.tiktokFeedNavigation(nodes, displayHeight)) {
                    hideOverlay();
                    scheduleInspection(150);
                } else if (eligible && tiktokOrigin.transitioning(now)
                        && !ScreenRules.tiktokFeedNavigation(nodes, displayHeight)) {
                    // The action rail can appear before the shared viewer's Back control.
                    hideOverlay();
                    scheduleInspection(150);
                } else {
                    clearTikTokVideo();
                    tiktokOrigin.clear();
                    showDmGate(packageName);
                }
            } else if (tiktokSharedVideoActive && tiktokViewer.loading(now)
                    && !ScreenRules.tiktokFeedNavigation(nodes, displayHeight) && !profile) {
                hideOverlay();
                scheduleInspection(150);
            } else if (now < tiktokNavigationGraceUntil || tiktokOrigin.transitioning(now)) {
                clearTikTokVideo();
                hideOverlay();
                scheduleInspection(150);
            } else {
                clearTikTokVideo();
                tiktokOrigin.clear();
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
                    instagramComments.clear();
                    clearInstagramReel();
                    instagramOrigin.clear();
                    tiktokOrigin.clear();
                    clearTikTokVideo();
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
        if (INSTAGRAM.equals(pkg) && instagramComments.allows(ScreenRules.instagram(nodes, h, w),
                ScreenRules.commentContent(nodes, h, w), dmReelActive,
                SystemClock.elapsedRealtime())) return false;
        if (FACEBOOK.equals(pkg)) return facebookEnabled() && ScreenRules.facebookReel(nodes, h, w);
        if (INSTAGRAM.equals(pkg)) return !"off".equals(instagramMode())
                && ScreenRules.instagram(nodes, h, w) == ScreenRules.Screen.REEL
                && (swipe || "reels".equals(instagramMode()) || !dmReelActive);
        if (ScreenRules.tiktokSharedVideoEligible(nodes, h) && !ScreenRules.tiktokProfileScreen(nodes, h)
                && tiktokComments.allows(ScreenRules.tiktok(nodes, h, w),
                        ScreenRules.commentContent(nodes, h, w), tiktokSharedVideoActive,
                        SystemClock.elapsedRealtime())) return false;
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

    private boolean isBackOrCloseClick(AccessibilityEvent event, AccessibilityNodeInfo source) {
        for (int i = 0; source != null && i < 3; i++, source = source.getParent()) {
            ScreenRules.NodeData n = describe(source);
            if (n.labelIs("back") || n.labelIs("close") || n.labelIs("go back")
                    || n.idContains("back_button") || n.idContains("close_button")) return true;
        }
        String label = eventLabel(event).replace("[", "").replace("]", "").trim();
        return label.equals("back") || label.equals("close") || label.equals("go back");
    }

    private boolean currentInstagramCommentsAllowed(long now) {
        AccessibilityNodeInfo root = findTargetRoot();
        if (root == null || !INSTAGRAM.equals(asString(root.getPackageName()))) return false;
        List<ScreenRules.NodeData> nodes = collectScreenNodes(root);
        int h = getResources().getDisplayMetrics().heightPixels;
        int w = getResources().getDisplayMetrics().widthPixels;
        if (!ScreenRules.instagramSharedReelEligible(nodes, h, true)) {
            instagramComments.clear();
            return false;
        }
        if (ScreenRules.instagramProfileScreen(nodes, h, w)) {
            instagramComments.clear();
            instagramOrigin.clear();
            clearInstagramReel();
            return false;
        }
        return instagramComments.allows(ScreenRules.instagram(nodes, h, w),
                ScreenRules.commentContent(nodes, h, w), dmReelActive, now);
    }

    private boolean isCommentOpenClick(AccessibilityEvent event, AccessibilityNodeInfo source) {
        if (eventLabel(event).contains("comment")) return true;
        for (int i = 0; source != null && i < 3; i++, source = source.getParent()) {
            ScreenRules.NodeData n = describe(source);
            if (n.labelContains("comment") || n.idContains("comment")) return true;
        }
        return false;
    }

    private boolean currentTikTokCommentsAllowed(long now) {
        AccessibilityNodeInfo root = findTargetRoot();
        if (root == null || !isTikTok(asString(root.getPackageName()))) return false;
        List<ScreenRules.NodeData> nodes = collectScreenNodes(root);
        int h = getResources().getDisplayMetrics().heightPixels;
        int w = getResources().getDisplayMetrics().widthPixels;
        if (!ScreenRules.tiktokSharedVideoEligible(nodes, h) || ScreenRules.tiktokProfileScreen(nodes, h)) {
            tiktokOrigin.clear();
            clearTikTokVideo();
            return false;
        }
        return tiktokComments.allows(ScreenRules.tiktok(nodes, h, w),
                ScreenRules.commentContent(nodes, h, w), tiktokSharedVideoActive, now);
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

    private boolean isTikTokNavigationClick(AccessibilityEvent event, AccessibilityNodeInfo source) {
        int h = getResources().getDisplayMetrics().heightPixels;
        for (int i = 0; source != null && i < 3; i++, source = source.getParent()) {
            ScreenRules.NodeData n = describe(source);
            boolean navigationPosition = n.top < h / 4 || n.top > h * 4 / 5;
            if (navigationPosition && (n.labelIs("back") || n.labelIs("close")
                    || n.labelIs("go back") || n.labelIs("home") || n.labelIs("inbox")
                    || n.labelIs("friends") || n.labelIs("following") || n.labelIs("for you")
                    || n.labelIs("profile") || n.labelIs("discover") || n.labelIs("search")
                    || n.idContains("back_button") || n.idContains("close_button")
                    || n.idContains("home_tab") || n.idContains("tab_home")
                    || n.idContains("friends_tab") || n.idContains("tab_friends")
                    || n.idContains("profile_tab") || n.idContains("tab_profile")
                    || n.idContains("inbox_tab") || n.idContains("tab_inbox"))) return true;
            if (n.labelIs("view profile") || n.labelIs("open profile")
                    || (n.top < h / 4 && (n.idContains("chat_header")
                            || n.idContains("profile_button")))) return true;
        }
        String label = eventLabel(event).replace("[", "").replace("]", "").trim();
        return label.equals("back") || label.equals("close") || label.equals("go back")
                || label.equals("home") || label.equals("inbox") || label.equals("friends")
                || label.equals("following") || label.equals("for you") || label.equals("profile")
                || label.equals("discover") || label.equals("search");
    }

    private boolean isCommentScrollSource(AccessibilityNodeInfo node) {
        for (int i = 0; node != null && i < 5; i++, node = node.getParent()) {
            ScreenRules.NodeData n = describe(node);
            if (n.idContains("comment") || n.labelIs("comments")) return true;
        }
        return false;
    }

    private boolean isInstagramViewerPaging(AccessibilityEvent event, boolean armed) {
        AccessibilityNodeInfo source = event.getSource();
        if (source == null || isCommentScrollSource(source)) return false;
        int height = getResources().getDisplayMetrics().heightPixels;
        int width = getResources().getDisplayMetrics().widthPixels;
        boolean hasDelta = Build.VERSION.SDK_INT >= 28;
        return instagramViewer.scrolled(describe(source), height, width, armed,
                hasDelta ? event.getScrollDeltaY() : 0, hasDelta ? event.getScrollDeltaX() : 0,
                event.getItemCount() > 1 ? event.getFromIndex() : -1, event.getScrollY());
    }

    private boolean isConfirmedViewerPaging(AccessibilityEvent event) {
        if (Build.VERSION.SDK_INT < 28) return false;
        AccessibilityNodeInfo source = event.getSource();
        if (source == null || isCommentScrollSource(source)) return false;
        ScreenRules.NodeData n = describe(source);
        int h = getResources().getDisplayMetrics().heightPixels;
        int w = getResources().getDisplayMetrics().widthPixels;
        boolean pager = n.idContains("clips_viewer") || n.idContains("reel_pager")
                || n.idContains("reels_viewer") || n.idContains("reel_viewer")
                || n.idContains("video_pager") || n.idContains("feed_pager")
                || n.className.contains("viewpager");
        boolean tikTok = isTikTok(asString(event.getPackageName()));
        if (!pager || n.width < w * 2 / 3 || n.height < (tikTok ? h * 3 / 5 : h * 2 / 3)) return false;
        long vertical = event.getScrollDeltaY() == -1 ? 0 : Math.abs((long) event.getScrollDeltaY());
        long horizontal = event.getScrollDeltaX() == -1 ? 0 : Math.abs((long) event.getScrollDeltaX());
        return vertical >= h / 3 && vertical > horizontal;
    }

    private boolean isTikTokViewerScroll(AccessibilityEvent event, boolean armed) {
        AccessibilityNodeInfo source = event.getSource();
        if (source == null || isCommentScrollSource(source)) return false;
        int height = getResources().getDisplayMetrics().heightPixels;
        int width = getResources().getDisplayMetrics().widthPixels;
        boolean hasScrollDelta = Build.VERSION.SDK_INT >= 28;
        return tiktokViewer.scrolled(describe(source), height, width, armed,
                hasScrollDelta ? event.getScrollDeltaY() : 0,
                hasScrollDelta ? event.getScrollDeltaX() : 0,
                event.getItemCount() > 1 ? event.getFromIndex() : -1, event.getScrollY());
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
        instagramComments.clear();
        clearInstagramReel();
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
        tiktokOrigin.clear();
        clearTikTokVideo();
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
