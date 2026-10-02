package com.darren.quietfeed;

import java.util.List;
import java.util.Locale;

/** Small, side effect free rules for the UI exposed by supported social apps. */
final class ScreenRules {
    enum Screen { OTHER, LOGIN, DM_INBOX, DM_THREAD, REEL, COMMENTS }

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

        boolean labelMatches(String pattern) {
            return text.matches(pattern) || description.matches(pattern) || hint.matches(pattern);
        }

        boolean actionLabel(String action) {
            return labelIs(action) || labelMatches(action + "[, ].*[0-9].*");
        }

        boolean idContains(String value) { return id.contains(clean(value)); }
    }

    static Screen instagram(List<NodeData> nodes, int displayHeight, int displayWidth) {
        if (commentsPanel(nodes, displayHeight, displayWidth)) return Screen.COMMENTS;
        boolean messageEntry = false;
        boolean messageHeader = false;
        boolean threadComposer = false;
        boolean chatHeader = false;
        boolean fullScreenViewer = false;
        int railLikeTop = -1;
        int railCommentTop = -1;
        int railShareTop = -1;
        boolean inboxCompanion = false;
        boolean inboxContainer = false;
        boolean inboxSearch = false;
        boolean inboxCompose = false;
        boolean inboxRequests = false;
        boolean inboxNotes = false;
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
            if ((n.labelIs("messages") || n.labelIs("chats")
                    || n.labelIs("messages, heading") || n.labelIs("chats, heading"))
                    && n.top < displayHeight * 2 / 3) {
                messageHeader = true;
            }
            if (n.labelContains("search messages") || n.labelIs("requests")
                    || n.labelIs("your notes") || n.labelIs("your note")
                    || n.labelIs("primary") || n.labelIs("general")
                    || n.labelIs("new message") || n.labelIs("new chat")) {
                inboxCompanion = true;
            }
            // Current inbox layouts may show the account name in the title
            // instead of "Messages". Require inbox structure or a combination
            // of inbox controls, rather than interpreting any account name.
            if ((n.idContains("direct_inbox") && n.width >= displayWidth * 2 / 3
                    && n.height >= displayHeight / 4) || n.idContains("inbox_recycler")
                    || n.idContains("inbox_list") || n.idContains("row_inbox_thread")
                    || n.idContains("direct_thread_row") || n.idContains("direct_thread_list"))
                inboxContainer = true;
            if (n.top < displayHeight / 3
                    && (n.labelContains("search messages") || n.idContains("inbox_search")
                            || n.idContains("direct_search")
                            || (n.labelIs("search") && (n.className.contains("edittext")
                                    || n.idContains("search"))))) inboxSearch = true;
            if (n.top < displayHeight / 3
                    && (n.labelIs("new message") || n.labelIs("new chat")
                            || n.labelIs("compose") || n.idContains("direct_compose")
                            || n.idContains("new_message"))) inboxCompose = true;
            if (n.top < displayHeight * 2 / 3
                    && (n.labelMatches("requests([ ,:(].*)?") || n.labelContains("message requests")
                            || n.idContains("inbox_requests"))) inboxRequests = true;
            if (n.top < displayHeight / 2
                    && (n.labelMatches("your notes?([, ].*)?")
                            || n.labelContains("leave a note") || n.idContains("notes_tray")))
                inboxNotes = true;
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
            if (n.actionLabel("like") || n.actionLabel("likes")) like = true;
            if (n.actionLabel("comment") || n.actionLabel("comments")) comment = true;
            if (n.actionLabel("share") || n.actionLabel("send")) share = true;
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
        if (viewerContainer || (reelTabSelected && like && comment)
                || unmarkedViewer) return Screen.REEL;
        if ((messageHeader && inboxCompanion) || inboxContainer
                || (inboxSearch && (inboxCompose || inboxRequests || inboxNotes))
                || (inboxRequests && (inboxCompose || inboxNotes))) return Screen.DM_INBOX;
        if (login && password) return Screen.LOGIN;
        return Screen.OTHER;
    }

    static boolean facebookReel(List<NodeData> nodes, int displayHeight, int displayWidth) {
        if (commentsPanel(nodes, displayHeight, displayWidth)) return false;
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
        if (commentsPanel(nodes, displayHeight, displayWidth)) return Screen.COMMENTS;
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

    static boolean instagramSharedReelEligible(List<NodeData> nodes, int displayHeight,
                                              boolean pendingMediaClick) {
        if (!pendingMediaClick) return false;
        for (NodeData n : nodes) {
            if (isSelectedTab(n, "reels", displayHeight)
                    || (n.selected && n.idContains("clips_tab"))) return false;
        }
        return true;
    }

    static boolean instagramOpenedViewer(List<NodeData> nodes, int displayHeight) {
        boolean backOrClose = false;
        boolean selectedHome = false;
        boolean dedicatedViewer = false;
        for (NodeData n : nodes) {
            if (isSelectedTab(n, "reels", displayHeight)
                    || (n.selected && n.idContains("clips_tab"))) return false;
            if (isViewerNavigation(n, displayHeight)) backOrClose = true;
            if (isSelectedTab(n, "home", displayHeight)) selectedHome = true;
            if (n.top < displayHeight / 6 && n.bottom > displayHeight * 4 / 5
                    && (n.idContains("clips_viewer") || n.idContains("clips_video_container")
                            || n.idContains("reel_pager") || n.idContains("reels_viewer")
                            || n.idContains("reel_viewer"))) dedicatedViewer = true;
        }
        return backOrClose && (!selectedHome || dedicatedViewer);
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

    /** Recognizes video paging without treating comment-list scrolling as the next video. */
    static boolean tiktokViewerScroll(NodeData source, int displayHeight, int displayWidth,
                                      boolean hasScrollDelta, int deltaY, int deltaX) {
        if (source == null || source.idContains("comment") || source.labelContains("comments"))
            return false;
        if (source.width < displayWidth * 2 / 3 || source.height < displayHeight * 3 / 5)
            return false;
        // A known video pager may be implemented as a RecyclerView or another list.
        if (source.idContains("video_pager") || source.idContains("feed_pager")) return true;
        if (source.className.contains("scrollview") || source.className.contains("recyclerview")
                || source.className.contains("listview")) return false;
        if (source.className.contains("viewpager")) return true;
        if (!hasScrollDelta) return false;
        long vertical = Math.abs((long) deltaY);
        long horizontal = Math.abs((long) deltaX);
        return vertical >= displayHeight / 3 && vertical > horizontal;
    }

    static boolean commentsPanel(List<NodeData> nodes, int displayHeight, int displayWidth) {
        boolean heading = false;
        boolean composer = false;
        boolean commentList = false;
        boolean genericList = false;
        boolean sorting = false;
        int replyActions = 0;
        for (NodeData n : nodes) {
            boolean wide = n.width >= displayWidth / 3;
            boolean list = n.idContains("comment_list") || n.idContains("comments_list")
                    || n.idContains("comment_recycler") || n.idContains("comments_recycler");
            if (wide && n.height >= displayHeight / 4 &&
                    (n.idContains("comments_sheet") || n.idContains("comment_sheet")
                            || n.idContains("comments_bottom_sheet")
                            || n.idContains("comment_bottom_sheet")
                            || n.idContains("comments_dialog") || n.idContains("comment_dialog")
                            || n.idContains("comments_panel") || n.idContains("comment_panel"))) return true;
            if (wide && list && n.height >= displayHeight / 4) commentList = true;
            if (wide && n.height >= displayHeight / 4
                    && (n.className.contains("recyclerview") || n.className.contains("listview")
                            || n.className.contains("scrollview"))) genericList = true;
            // Comment-sheet titles are often narrow, wrap-content labels.
            // Exclude the vertical video control rail instead of requiring a
            // wide title, so a video's Comments button cannot exempt it.
            boolean videoRail = n.left >= displayWidth * 2 / 3
                    && n.width <= displayWidth / 3 && n.top > displayHeight / 3;
            if (!videoRail && n.top < displayHeight * 3 / 4
                    && n.height <= displayHeight / 5
                    && (n.labelIs("comments") || n.labelMatches("[0-9.,km ]+ comments")
                            || n.labelMatches("comments[ ,:]*\\(?[0-9.,km ]+\\)?")
                            || n.idContains("comments_title") || n.idContains("comment_header")))
                heading = true;
            if (n.labelContains("add a comment") || n.labelContains("write a comment")
                    || n.labelContains("write a public comment") || n.labelContains("comment as ")
                    || n.labelContains("add comment") || n.labelContains("write comment")
                    || n.labelContains("leave a comment")
                    || (n.className.contains("edittext") && n.labelContains("comment")))
                composer = true;
            if (n.labelMatches("(most relevant|newest|top comments|all comments)(, .*| selected)?"))
                sorting = true;
            if (n.labelIs("reply") && n.top > displayHeight / 5) replyActions++;
        }
        return (heading && (composer || commentList || genericList || sorting))
                || (commentList && composer)
                || (composer && (sorting || replyActions >= 2));
    }

    /**
     * Sparse Instagram sheets may expose only their composer or comment rows.
     * Use this only after a comment-open action on an already allowed shared
     * Reel; these signals alone must not exempt an arbitrary video screen.
     */
    static boolean instagramCommentContent(List<NodeData> nodes,
                                           int displayHeight, int displayWidth) {
        if (commentsPanel(nodes, displayHeight, displayWidth)
                || instagramCommentPanelEvidence(nodes, displayHeight, displayWidth)) return true;
        for (NodeData n : nodes) {
            boolean videoRail = n.left >= displayWidth * 2 / 3
                    && n.width <= displayWidth / 3 && n.top > displayHeight / 3;
            if (videoRail) continue;

            boolean commentComposer = n.labelContains("add a comment")
                    || n.labelContains("write a comment") || n.labelContains("leave a comment")
                    || n.labelContains("add comment") || n.labelContains("write comment")
                    || (n.className.contains("edittext") && n.labelContains("comment"));
            if (commentComposer) return true;
        }
        return false;
    }

    /** Panel evidence that does not depend on a prior comment-open action. */
    static boolean instagramCommentPanelEvidence(List<NodeData> nodes,
                                                 int displayHeight, int displayWidth) {
        int replyActions = 0;
        for (NodeData n : nodes) {
            boolean videoRail = n.left >= displayWidth * 2 / 3
                    && n.width <= displayWidth / 3 && n.top > displayHeight / 3;
            if (videoRail) continue;
            boolean explicitContent = n.idContains("comment_list") || n.idContains("comments_list")
                    || n.idContains("comment_recycler") || n.idContains("comments_recycler")
                    || n.idContains("comments_sheet") || n.idContains("comment_sheet")
                    || n.idContains("comments_bottom_sheet") || n.idContains("comment_bottom_sheet")
                    || n.idContains("comments_dialog") || n.idContains("comment_dialog")
                    || n.idContains("comments_panel") || n.idContains("comment_panel");
            if (explicitContent && n.width >= displayWidth / 3
                    && n.height >= displayHeight / 8) return true;

            boolean commentHeading = n.labelIs("comments")
                    || n.labelMatches("[0-9.,km ]+ comments")
                    || n.labelMatches("comments[ ,:]*\\(?[0-9.,km ]+\\)?")
                    || n.idContains("comments_title") || n.idContains("comment_header");
            if (commentHeading && n.height <= displayHeight / 5
                    && n.top < displayHeight * 9 / 10) return true;

            if (n.labelIs("reply") && n.top > displayHeight / 5) replyActions++;
        }
        return replyActions >= 2;
    }

    /** Strong profile evidence used to end a shared-Reel comment session. */
    static boolean instagramProfileScreen(List<NodeData> nodes,
                                          int displayHeight, int displayWidth) {
        boolean posts = false;
        boolean followers = false;
        boolean following = false;
        for (NodeData n : nodes) {
            boolean profileGrid = n.idContains("profile_grid") || n.idContains("profile_posts_grid")
                    || n.idContains("profile_media_grid") || n.idContains("profile_recycler_view");
            if (profileGrid && n.width >= displayWidth * 2 / 3
                    && n.height >= displayHeight / 5) return true;
            if (n.top >= displayHeight * 3 / 5) continue;
            if (n.labelIs("posts") || n.labelMatches("[0-9.,km ]+ posts")
                    || n.labelMatches("posts[ ,:]+[0-9.,km ]+")) posts = true;
            if (n.labelIs("followers") || n.labelMatches("[0-9.,km ]+ followers")
                    || n.labelMatches("followers[ ,:]+[0-9.,km ]+")) followers = true;
            if (n.labelIs("following") || n.labelMatches("[0-9.,km ]+ following")
                    || n.labelMatches("following[ ,:]+[0-9.,km ]+")) following = true;
        }
        return posts && followers && following;
    }

    static boolean instagramMessagesButton(NodeData n, int displayHeight) {
        boolean navigationPosition = n.top < displayHeight / 3
                || n.top > displayHeight * 2 / 3;
        boolean navigationId = n.idContains("direct_tab") || n.idContains("tab_direct")
                || n.idContains("messages_tab") || n.idContains("tab_messages")
                || n.idContains("direct_inbox") || n.idContains("action_bar_inbox")
                || n.idContains("inbox_button");
        boolean navigationLabel = n.labelIs("messages") || n.labelIs("inbox")
                || n.labelIs("direct") || n.labelIs("chats")
                || n.labelContains("messages, ") || n.labelContains("messages tab")
                || n.labelContains("chats, tab") || n.labelContains("direct, tab")
                || n.labelContains("inbox, ");
        return navigationPosition && (navigationId || navigationLabel)
                && !n.className.contains("edittext");
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
