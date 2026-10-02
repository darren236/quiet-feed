package com.darren.quietfeed;

import java.util.List;
import java.util.Locale;

/** Small, side effect free rules for the UI exposed by supported social apps. */
final class ScreenRules {
    enum Screen { OTHER, LOGIN, DM_INBOX, DM_THREAD, REEL }

    static final class NodeData {
        final String text;
        final String description;
        final String hint;
        final String id;
        final String className;
        final boolean selected;
        final boolean clickable;
        final int left;
        final int top;
        final int bottom;
        final int width;
        final int height;

        NodeData(String text, String description, String hint, String id, String className,
                 boolean selected, boolean clickable, int top, int bottom, int width, int height) {
            this(text, description, hint, id, className, selected, clickable,
                    0, top, bottom, width, height);
        }

        NodeData(String text, String description, String hint, String id, String className,
                 boolean selected, boolean clickable, int left, int top, int bottom,
                 int width, int height) {
            this.text = clean(text);
            this.description = clean(description);
            this.hint = clean(hint);
            this.id = clean(id);
            this.className = clean(className);
            this.selected = selected;
            this.clickable = clickable;
            this.left = left;
            this.top = top;
            this.bottom = bottom;
            this.width = width;
            this.height = height;
        }

        boolean labelIs(String value) {
            String wanted = clean(value);
            return text.equals(wanted) || description.equals(wanted) || hint.equals(wanted);
        }

        boolean labelContains(String value) {
            String wanted = clean(value);
            return text.contains(wanted) || description.contains(wanted) || hint.contains(wanted);
        }

        boolean idContains(String value) { return id.contains(clean(value)); }
    }

    static Screen instagram(List<NodeData> nodes, int displayHeight, int displayWidth) {
        boolean messageEntry = false;
        boolean messageHeader = false;
        boolean threadComposer = false;
        boolean chatHeader = false;
        boolean fullScreenViewer = false;
        int railLikeTop = -1;
        int railCommentTop = -1;
        int railShareTop = -1;
        boolean inboxCompanion = false;
        boolean login = false;
        boolean password = false;
        boolean viewerContainer = false;
        boolean fullScreenMedia = false;
        boolean reelTabSelected = false;
        boolean viewerNavigation = false;
        boolean reelHeading = false;
        boolean originalAudio = false;
        boolean like = false;
        boolean comment = false;
        boolean share = false;

        for (NodeData n : nodes) {
            if (n.idContains("message_composer") || n.idContains("thread_composer")
                    || n.idContains("row_thread_composer")) threadComposer = true;
            if (n.top < displayHeight / 4 && (n.labelContains("audio call")
                    || n.labelContains("video call") || n.labelContains("conversation details")
                    || n.idContains("thread_header"))) chatHeader = true;
            if (n.left >= displayWidth * 2 / 3 && n.width <= displayWidth / 3
                    && n.top > displayHeight / 3 && n.bottom < displayHeight * 9 / 10) {
                if (n.labelContains("like")) railLikeTop = n.top;
                if (n.labelContains("comment")) railCommentTop = n.top;
                if (n.labelContains("share") || n.labelIs("send")) railShareTop = n.top;
            }
            if ((n.className.contains("edittext") && (n.labelContains("message") || n.id.contains("message")))
                    || n.idContains("message_composer") || n.idContains("thread_composer")) {
                messageEntry = true;
            }
            if ((n.labelIs("message...") || n.labelIs("type a message"))
                    && n.top > displayHeight * 2 / 3) {
                messageEntry = true;
            }
            if ((n.labelIs("messages") || n.labelIs("chats")) && n.top < displayHeight / 3) {
                messageHeader = true;
            }
            if (n.labelContains("search messages") || n.labelIs("requests")
                    || n.labelIs("your notes") || n.labelIs("your note")
                    || n.labelIs("primary") || n.labelIs("general")
                    || n.labelIs("new message") || n.labelIs("new chat")) {
                inboxCompanion = true;
            }
            if (n.labelIs("log in") || n.labelIs("login")) login = true;
            if (n.labelIs("password")) password = true;

            if (isLargeVisibleNode(n, displayHeight, displayWidth)) {
                if (n.idContains("clips_viewer") || n.idContains("clips_video_container")
                        || n.idContains("reel_pager") || n.idContains("reels_viewer")
                        || n.idContains("reel_viewer") || n.labelContains("reels viewer")
                        || n.labelContains("reel viewer")) {
                    viewerContainer = true;
                    if (n.top < displayHeight / 6 && n.bottom > displayHeight * 4 / 5)
                        fullScreenViewer = true;
                }
                if (n.idContains("clips_media") || n.idContains("reel_video")
                        || n.labelContains("reel by") || n.labelContains("watch reel")
                        || n.labelContains("reels video")) fullScreenMedia = true;
            }
            if (n.labelIs("reels") && n.top < displayHeight / 6) reelHeading = true;
            if (isSelectedTab(n, "reels", displayHeight)
                    || (n.selected && n.idContains("clips_tab"))) reelTabSelected = true;
            if (isViewerNavigation(n, displayHeight)) viewerNavigation = true;
            if (n.labelContains("original audio") || n.labelIs("use audio") || n.labelIs("remix")) originalAudio = true;
            if (n.labelIs("like") || n.labelIs("likes")) like = true;
            if (n.labelIs("comment") || n.labelIs("comments")) comment = true;
            if (n.labelIs("share") || n.labelIs("send")) share = true;
        }

        // A shared Reel preview in a chat is not a viewer. A reply field inside a
        // full-screen Reel must not make that viewer look like a conversation.
        boolean actionRail = railLikeTop >= 0
                && railCommentTop > railLikeTop + displayHeight / 25
                && railShareTop > railCommentTop + displayHeight / 25;
        boolean confirmedChat = threadComposer || (messageEntry && chatHeader);
        if (confirmedChat && !fullScreenViewer && !actionRail) return Screen.DM_THREAD;
        boolean unmarkedViewer = (!messageEntry || actionRail) && viewerNavigation && like && comment && share
                && (fullScreenMedia || originalAudio || reelHeading);
        if (messageEntry && !viewerContainer && !reelTabSelected && !unmarkedViewer)
            return Screen.DM_THREAD;
        if (messageHeader && inboxCompanion) return Screen.DM_INBOX;
        if (viewerContainer || (reelTabSelected && like && comment)
                || unmarkedViewer) return Screen.REEL;
        if (login && password) return Screen.LOGIN;
        return Screen.OTHER;
    }

    static boolean facebookReel(List<NodeData> nodes, int displayHeight, int displayWidth) {
        boolean viewerContainer = false;
        boolean fullScreenMedia = false;
        boolean reelTabSelected = false;
        boolean viewerNavigation = false;
        boolean reelHeading = false;
        boolean like = false;
        boolean comment = false;
        boolean share = false;
        boolean homeNavigation = false;
        boolean previewPrompt = false;
        boolean profileNavigation = false;
        boolean imageViewer = false;
        boolean commentComposer = false;
        int railLikeTop = -1;
        int railCommentTop = -1;
        int railShareTop = -1;
        for (NodeData n : nodes) {
            if (isLargeVisibleNode(n, displayHeight, displayWidth)) {
                if (n.idContains("reels_viewer") || n.idContains("reel_viewer")
                        || n.idContains("shorts_viewer") || n.labelContains("reels viewer")
                        || n.labelContains("shorts viewer")) viewerContainer = true;
                if (n.idContains("short_video") || n.idContains("reel_video")
                        || n.labelContains("reel by") || n.labelContains("watch reel")
                        || n.labelContains("shorts video")) fullScreenMedia = true;
            }
            if ((n.labelIs("reels") || n.labelIs("shorts")
                    || n.labelContains("reels, ")) && n.top < displayHeight / 4)
                reelHeading = true;
            if (isSelectedTab(n, "reels", displayHeight)
                    || isSelectedTab(n, "shorts", displayHeight)) reelTabSelected = true;
            if (isViewerNavigation(n, displayHeight)) viewerNavigation = true;
            if (n.labelContains("like") || n.labelContains("reactions")) like = true;
            if (n.labelContains("comment")) comment = true;
            if (n.labelContains("share") || n.labelIs("send")) share = true;
            if (n.labelIs("home") || n.labelContains("home, tab")
                    || n.idContains("home_tab") || n.idContains("feed_tab"))
                homeNavigation = true;
            if (n.clickable && n.labelContains("watch reel")) previewPrompt = true;
            if (n.labelIs("posts") || n.labelIs("tagged") || n.labelIs("photos")
                    || n.labelIs("events") || n.labelIs("friends"))
                profileNavigation = true;
            if (n.labelContains("photo viewer") || n.labelContains("image viewer")
                    || n.labelContains("gallery") || n.labelContains("carousel"))
                imageViewer = true;
            if (n.top > displayHeight * 4 / 5
                    && (n.labelContains("add a comment")
                            || n.labelContains("write a comment")
                            || n.labelContains("comment...")
                            || (n.className.contains("edittext")
                                    && n.hint.contains("comment"))))
                commentComposer = true;

            // The opened Facebook Reel has a narrow vertical control rail on
            // the right. Feed-card actions are arranged horizontally instead.
            if (n.left >= displayWidth * 3 / 4 && n.width <= displayWidth / 4
                    && n.top > displayHeight * 2 / 5
                    && n.bottom < displayHeight * 9 / 10) {
                if (n.labelContains("like") || n.idContains("like"))
                    railLikeTop = railLikeTop < 0 ? n.top : Math.min(railLikeTop, n.top);
                if (n.labelContains("comment") || n.idContains("comment"))
                    railCommentTop = railCommentTop < 0 ? n.top : Math.min(railCommentTop, n.top);
                if (n.labelContains("share") || n.idContains("share"))
                    railShareTop = railShareTop < 0 ? n.top : Math.min(railShareTop, n.top);
            }
        }
        boolean verticalActionRail = railLikeTop >= 0
                && railCommentTop > railLikeTop + displayHeight / 25
                && railShareTop > railCommentTop + displayHeight / 25;
        return viewerContainer || (reelTabSelected && like && comment)
                || (viewerNavigation && like && comment && share
                        && (fullScreenMedia || reelHeading))
                || (reelHeading && like && comment && !homeNavigation
                        && !previewPrompt && !profileNavigation && !imageViewer)
                || (commentComposer && (verticalActionRail
                        || (viewerNavigation && like && comment && share))
                        && !profileNavigation && !imageViewer);
    }

    static Screen tiktok(List<NodeData> nodes, int displayHeight, int displayWidth) {
        boolean inboxHeading = false;
        boolean inboxTabSelected = false;
        boolean messageComposer = false;
        boolean login = false;
        boolean password = false;
        boolean videoContainer = false;
        boolean fullScreenVideo = false;
        boolean videoFeedHeading = false;
        boolean viewerNavigation = false;
        boolean like = false;
        boolean comment = false;
        boolean share = false;
        int railLikeTop = -1;
        int railCommentTop = -1;
        int railShareTop = -1;

        for (NodeData n : nodes) {
            if (n.labelContains("inbox") && n.top < displayHeight / 4) inboxHeading = true;
            if (isSelectedTab(n, "inbox", displayHeight)) inboxTabSelected = true;
            if (n.top > displayHeight * 2 / 3
                    && ((n.className.contains("edittext")
                            && (n.labelContains("message") || n.idContains("message")
                                    || n.idContains("chat_input")))
                            || n.labelIs("message...") || n.labelIs("type a message")
                            || n.labelIs("send a message"))) {
                messageComposer = true;
            }
            if (n.labelIs("log in") || n.labelIs("login")) login = true;
            if (n.labelIs("password")) password = true;

            if (isLargeVisibleNode(n, displayHeight, displayWidth)
                    && (n.idContains("video_player") || n.idContains("video_pager")
                            || n.idContains("video_view")
                            || n.idContains("aweme_video") || n.idContains("feed_video")
                            || n.className.contains("surfaceview")
                            || n.className.contains("textureview"))) {
                videoContainer = true;
                if (n.top < displayHeight / 6 && n.bottom > displayHeight * 4 / 5) {
                    fullScreenVideo = true;
                }
            }
            if (n.top < displayHeight / 4
                    && (n.labelIs("for you") || n.labelIs("following")
                            || n.labelIs("friends"))) {
                videoFeedHeading = true;
            }
            if (isViewerNavigation(n, displayHeight)) viewerNavigation = true;
            if (n.labelContains("like") || n.idContains("like")) like = true;
            if (n.labelContains("comment") || n.idContains("comment")) comment = true;
            if (n.labelContains("share") || n.idContains("share")) share = true;

            if (n.left >= displayWidth * 2 / 3 && n.width <= displayWidth / 3
                    && n.top > displayHeight * 2 / 5
                    && n.bottom < displayHeight * 9 / 10) {
                if (n.labelContains("like") || n.idContains("like"))
                    railLikeTop = railLikeTop < 0 ? n.top : Math.min(railLikeTop, n.top);
                if (n.labelContains("comment") || n.idContains("comment"))
                    railCommentTop = railCommentTop < 0 ? n.top : Math.min(railCommentTop, n.top);
                if (n.labelContains("share") || n.idContains("share"))
                    railShareTop = railShareTop < 0 ? n.top : Math.min(railShareTop, n.top);
            }
        }

        boolean verticalActionRail = railLikeTop >= 0
                && railCommentTop > railLikeTop + displayHeight / 25
                && railShareTop > railCommentTop + displayHeight / 25;
        if (fullScreenVideo || verticalActionRail
                || (videoContainer && like && comment && !messageComposer)
                || (videoFeedHeading && like && comment && share)
                || (viewerNavigation && like && comment && share && !messageComposer)) {
            return Screen.REEL;
        }
        if (messageComposer) return Screen.DM_THREAD;
        if (inboxHeading || inboxTabSelected) return Screen.DM_INBOX;
        if (login && password) return Screen.LOGIN;
        return Screen.OTHER;
    }

    static boolean tiktokOpenedViewer(List<NodeData> nodes, int displayHeight) {
        boolean backOrClose = false;
        boolean selectedFeedTab = false;
        for (NodeData n : nodes) {
            if (isViewerNavigation(n, displayHeight)) backOrClose = true;
            if (n.selected && n.top < displayHeight / 4
                    && (n.labelIs("for you") || n.labelIs("following")
                            || n.labelIs("friends"))) {
                selectedFeedTab = true;
            }
        }
        return backOrClose && !selectedFeedTab;
    }

    private static boolean isLargeVisibleNode(NodeData n, int displayHeight, int displayWidth) {
        int visibleHeight = Math.max(0, Math.min(n.bottom, displayHeight) - Math.max(n.top, 0));
        return visibleHeight >= displayHeight * 3 / 5 && n.width >= displayWidth * 2 / 3;
    }

    private static boolean isSelectedTab(NodeData n, String label, int displayHeight) {
        return n.selected && (n.labelIs(label) || n.idContains(label + "_tab")
                || n.idContains("tab_" + label))
                && (n.idContains("tab") || n.top < displayHeight / 5
                        || n.top > displayHeight * 4 / 5);
    }

    private static boolean isViewerNavigation(NodeData n, int displayHeight) {
        return n.clickable && n.top < displayHeight / 5
                && (n.labelIs("back") || n.labelIs("close") || n.labelIs("go back")
                        || n.idContains("back_button") || n.idContains("close_button"));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private ScreenRules() { }
}
