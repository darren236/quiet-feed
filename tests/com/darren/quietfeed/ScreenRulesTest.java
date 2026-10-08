package com.darren.quietfeed;

import java.util.ArrayList;
import java.util.List;

public final class ScreenRulesTest {
    private static final int DISPLAY_HEIGHT = 2400;
    private static final int DISPLAY_WIDTH = 1080;
    private static int assertionCount;

    public static void main(String[] args) {
        List<ScreenRules.NodeData> instagramFeed = nodes(
                node("Home", "", "", false, 2240, 80),
                node("Reels", "", "", false, 110, 80),
                wideNode("", "Watch reel by Alex", "clips_media", 220, 1820),
                node("Like", "", "", false, 1400, 50),
                node("Comment", "", "", false, 1450, 50),
                node("Share", "", "", false, 1500, 50),
                node("Original audio", "", "", false, 1550, 50));
        expect("Large Instagram Home preview is not a viewer", ScreenRules.Screen.OTHER,
                instagram(instagramFeed));

        List<ScreenRules.NodeData> facebookFeed = nodes(
                node("Reels", "", "", false, 100, 80),
                wideNode("", "Watch reel by Alex", "short_video", 220, 1820),
                node("Like", "", "", false, 1400, 50),
                node("Comment", "", "", false, 1450, 50),
                node("Share", "", "", false, 1500, 50));
        expect("Large Facebook feed preview is not a viewer", false,
                facebook(facebookFeed));

        List<ScreenRules.NodeData> facebookHomeWithActions = nodes(
                node("Home", "", "", false, 90, 80),
                node("Reels", "", "", false, 250, 80),
                node("Like this reel", "", "", false, 1500, 50),
                node("View comments", "", "", false, 1600, 50));
        expect("Facebook Home Reel controls stay visible", false,
                facebook(facebookHomeWithActions));

        List<ScreenRules.NodeData> facebookViewerWithoutIds = nodes(
                node("Reels", "", "", false, 100, 80),
                node("", "Like this reel", "", false, 1500, 50),
                node("", "View comments", "", false, 1600, 50));
        expect("Facebook viewer without dedicated ids", true,
                facebook(facebookViewerWithoutIds));

        List<ScreenRules.NodeData> facebookProfilePreview = nodes(
                node("Reels", "", "", false, 100, 80),
                node("Posts", "", "", false, 250, 80),
                node("Like", "", "", false, 1500, 50),
                node("Comment", "", "", false, 1600, 50));
        expect("Facebook profile preview stays visible", false,
                facebook(facebookProfilePreview));

        List<ScreenRules.NodeData> facebookPhotoViewer = nodes(
                node("Reels", "", "", false, 100, 80),
                node("", "Photo viewer", "", false, 300, 80),
                node("Like", "", "", false, 1500, 50),
                node("Comment", "", "", false, 1600, 50));
        expect("Facebook photo viewer is not a Reel", false,
                facebook(facebookPhotoViewer));

        List<ScreenRules.NodeData> screenshotLikeFacebookViewer = nodes(
                node("", "Back", "", false, 95, 80),
                railNode("", "Like, 949", 1400),
                railNode("", "Comment, 28", 1600),
                railNode("", "Share, 6", 1800),
                railNode("", "Save, 86", 2000),
                node("Add a comment...", "", "", false, 2180, 100));
        expect("Facebook viewer shown in screenshot", true,
                facebook(screenshotLikeFacebookViewer));

        List<ScreenRules.NodeData> viewerWithUnderlyingHome = nodes(
                node("Home", "", "home_tab", false, 100, 80),
                node("", "Watch Reel", "", false, 300, 100),
                node("", "Back", "", false, 95, 80),
                railNode("", "Like, 949", 1400),
                railNode("", "Comment, 28", 1600),
                railNode("", "Share, 6", 1800),
                node("Add a comment...", "", "", false, 2180, 100));
        expect("Opened Facebook viewer overrides hidden Home and Watch Reel labels", true,
                facebook(viewerWithUnderlyingHome));

        List<ScreenRules.NodeData> viewerWithWideActionParents = nodes(
                node("", "Back", "", false, 95, 80),
                node("", "Like, 949", "", false, 1400, 90),
                node("", "Comment, 28", "", false, 1600, 90),
                node("", "Share, 6", "", false, 1800, 90),
                node("Add a comment...", "", "", false, 2180, 100));
        expect("Facebook viewer with wide action nodes", true,
                facebook(viewerWithWideActionParents));

        List<ScreenRules.NodeData> horizontalFacebookPreview = nodes(
                node("Home", "", "", false, 100, 80),
                wideNode("", "Watch Reel", "short_video", 240, 1850),
                railNode("Like", "", 1850),
                railNode("Comment", "", 1850),
                railNode("Share", "", 1850),
                node("Add a comment...", "", "", false, 2180, 100));
        expect("Facebook feed card actions stay visible", false,
                facebook(horizontalFacebookPreview));

        List<ScreenRules.NodeData> chat = nodes(
                node("Back", "", "", false, 90, 70),
                wideNode("", "Watch reel by Alex", "clips_media", 500, 1700),
                node("", "Message...", "", false, 2150, 100, "android.widget.EditText"));
        expect("Shared Reel preview stays in chat", ScreenRules.Screen.DM_THREAD,
                instagram(chat));

        List<ScreenRules.NodeData> chatWithReelControls = nodes(
                node("Back", "", "", false, 90, 70),
                node("", "Video call", "", false, 100, 70),
                wideNode("", "Watch reel by Alex", "clips_media", 500, 1700),
                node("Original audio", "", "", false, 1300, 60),
                node("Like", "", "", false, 1400, 60),
                node("Comment", "", "", false, 1460, 60),
                node("Send", "", "", false, 1520, 60),
                node("", "Message...", "thread_composer", false, 2150, 100,
                        "android.widget.EditText"));
        expect("Instagram chat with Reel card and controls stays a chat",
                ScreenRules.Screen.DM_THREAD, instagram(chatWithReelControls));

        List<ScreenRules.NodeData> chatWithEmbeddedViewer = nodes(
                node("", "Video call", "", false, 100, 70),
                wideNode("", "", "clips_viewer", 500, 1650),
                node("", "Message...", "thread_composer", false, 2150, 100,
                        "android.widget.EditText"));
        expect("Embedded viewer does not turn Instagram chat into a Reel",
                ScreenRules.Screen.DM_THREAD, instagram(chatWithEmbeddedViewer));

        List<ScreenRules.NodeData> reelWithReplyAndRail = nodes(
                node("Back", "", "", false, 90, 70),
                node("Original audio", "", "", false, 1800, 60),
                railNode("Like", "", 1300),
                railNode("Comment", "", 1500),
                railNode("Share", "", 1700),
                node("", "Message...", "", false, 2150, 100,
                        "android.widget.EditText"));
        expect("Opened Reel with reply field and vertical controls is still blocked",
                ScreenRules.Screen.REEL, instagram(reelWithReplyAndRail));

        List<ScreenRules.NodeData> viewerWithId = nodes(
                node("Home", "", "", true, 2250, 80),
                wideNode("", "", "reels_viewer", 0, 2300),
                node("Like", "", "", false, 1500, 50));
        expect("Opened Instagram viewer overrides Home tab", ScreenRules.Screen.REEL,
                instagram(viewerWithId));
        expect("Opened Facebook viewer overrides Home tab", true,
                facebook(viewerWithId));

        List<ScreenRules.NodeData> reelTab = nodes(
                node("Reels", "", "", true, 2250, 80),
                node("Like", "", "", false, 1500, 50),
                node("Comment", "", "", false, 1600, 50));
        expect("Instagram Reel tab", ScreenRules.Screen.REEL, instagram(reelTab));
        expect("Facebook Reel tab", true, facebook(reelTab));

        List<ScreenRules.NodeData> openedViewer = nodes(
                node("Back", "", "", false, 90, 70),
                node("Reels", "", "", false, 115, 80),
                node("Like", "", "", false, 1500, 50),
                node("Comment", "", "", false, 1600, 50),
                node("Share", "", "", false, 1700, 50));
        expect("Opened Instagram viewer with Back control", ScreenRules.Screen.REEL,
                instagram(openedViewer));
        expect("Opened Facebook viewer with Back control", true,
                facebook(openedViewer));

        List<ScreenRules.NodeData> replyInViewer = nodes(
                wideNode("", "", "reels_viewer", 0, 2300),
                node("", "Message...", "", false, 2150, 100, "android.widget.EditText"));
        expect("Reply field does not turn viewer into chat", ScreenRules.Screen.REEL,
                instagram(replyInViewer));

        List<ScreenRules.NodeData> inbox = nodes(
                node("Messages", "", "", false, 120, 90),
                node("Requests", "", "", false, 450, 70));
        expect("DM inbox", ScreenRules.Screen.DM_INBOX, instagram(inbox));

        List<ScreenRules.NodeData> login = nodes(
                node("Log in", "", "", false, 900, 80),
                node("Password", "", "", false, 1100, 80));
        expect("Login remains available", ScreenRules.Screen.LOGIN, instagram(login));

        List<ScreenRules.NodeData> tiktokForYou = nodes(
                node("For You", "", "", true, 110, 80),
                node("Inbox", "", "", false, 2250, 80),
                railNode("", "Like video", 1350),
                railNode("", "Comments", 1550),
                railNode("", "Share video", 1750));
        expect("TikTok For You feed is video", ScreenRules.Screen.REEL,
                tiktok(tiktokForYou));
        expect("TikTok For You feed cannot use chat viewer grant", false,
                ScreenRules.tiktokOpenedViewer(tiktokForYou, DISPLAY_HEIGHT));

        List<ScreenRules.NodeData> tiktokFriendsFeed = nodes(
                node("Friends", "", "", true, 110, 80),
                railNode("", "Like video", 1350),
                railNode("", "Comments", 1550),
                railNode("", "Share video", 1750));
        expect("TikTok Friends tab is still a video feed", ScreenRules.Screen.REEL,
                tiktok(tiktokFriendsFeed));

        List<ScreenRules.NodeData> tiktokInbox = nodes(
                node("Inbox", "", "", false, 110, 80),
                node("Inbox", "", "tab_inbox", true, 2250, 80),
                node("Messages", "", "", false, 400, 80));
        expect("TikTok inbox", ScreenRules.Screen.DM_INBOX, tiktok(tiktokInbox));

        List<ScreenRules.NodeData> tiktokChat = nodes(
                node("", "Back", "", false, 110, 80),
                wideNode("", "Shared video", "video_view", 450, 1550),
                node("", "Like video", "", false, 1300, 80),
                node("", "Comments", "", false, 1400, 80),
                node("", "Share video", "", false, 1500, 80),
                node("", "Message...", "", false, 2150, 100,
                        "android.widget.EditText"));
        expect("Shared TikTok preview remains a chat", ScreenRules.Screen.DM_THREAD,
                tiktok(tiktokChat));

        List<ScreenRules.NodeData> tiktokOpenedVideo = nodes(
                node("", "Back", "", false, 110, 80),
                railNode("", "Like video", 1350),
                railNode("", "Comments", 1550),
                railNode("", "Share video", 1750));
        expect("Opened TikTok video", ScreenRules.Screen.REEL,
                tiktok(tiktokOpenedVideo));
        expect("Opened TikTok video can use a chat viewer grant", true,
                ScreenRules.tiktokOpenedViewer(tiktokOpenedVideo, DISPLAY_HEIGHT));

        List<ScreenRules.NodeData> tiktokViewerWithReply = nodes(
                node("", "Back", "", false, 110, 80),
                wideNode("", "Video", "video_pager", 0, 2300),
                node("", "Message...", "", false, 2150, 100,
                        "android.widget.EditText"));
        expect("TikTok viewer reply field is not a chat", ScreenRules.Screen.REEL,
                tiktok(tiktokViewerWithReply));

        List<ScreenRules.NodeData> feedWithBackAndSelectedTab = nodes(
                node("For You", "", "", true, 110, 80),
                node("", "Back", "", false, 110, 80),
                railNode("", "Like video", 1350),
                railNode("", "Comments", 1550),
                railNode("", "Share video", 1750));
        expect("Selected feed tab blocks chat viewer grant", false,
                ScreenRules.tiktokOpenedViewer(feedWithBackAndSelectedTab, DISPLAY_HEIGHT));

        List<ScreenRules.NodeData> tiktokProfile = nodes(
                node("Profile", "", "", false, 100, 80),
                node("Inbox", "", "", false, 2250, 80));
        expect("TikTok profile is outside messages", ScreenRules.Screen.OTHER,
                tiktok(tiktokProfile));

        // Replay a chat tap, slow load, stationary pager events, comments, then a real swipe.
        ChatVideoOrigin tikTokOrigin = new ChatVideoOrigin();
        ReelViewerState tikTokViewer = ReelViewerState.forTikTok();
        SharedReelComments tikTokComments = new SharedReelComments();
        tikTokOrigin.sawChat(1000);
        expect("TikTok idle chat tap survives root changing before click delivery", true,
                tikTokOrigin.mediaClickFromChat(61000, false, true));
        expect("TikTok chat tap survives five-second video loading", true,
                tikTokOrigin.transitioning(66000));
        expect("TikTok slow loading allowance still expires", false, tikTokOrigin.canOpen(69000));
        List<ScreenRules.NodeData> tikTokWithoutBack = nodes(
                wideNode("", "", "video_pager", 0, 2300), railNode("", "Like video", 1350),
                railNode("", "Comments", 1550), railNode("", "Share video", 1750));
        expect("TikTok dedicated viewer with no Back label is a video", ScreenRules.Screen.REEL,
                tiktok(tikTokWithoutBack));
        expect("TikTok missing Back still requires explicit chat context", false,
                ScreenRules.tiktokOpenedViewer(tikTokWithoutBack, DISPLAY_HEIGHT));
        expect("TikTok explicit chat tap permits dedicated viewer without Back", true,
                tikTokOrigin.canOpen(66000) && ScreenRules.tiktokOpenedViewer(tikTokWithoutBack,
                        DISPLAY_HEIGHT, tikTokOrigin.hasMediaClick(66000)));
        tikTokOrigin.clear();
        tikTokViewer.sawViewer(66000);
        expect("TikTok confirmed shared viewer tolerates a short partial tree", true,
                tikTokViewer.loading(68000));
        expect("TikTok unrecognized viewer loading is bounded", false, tikTokViewer.loading(69000));
        ScreenRules.NodeData recyclerVideoPager = new ScreenRules.NodeData("", "", "", "video_pager",
                "androidx.recyclerview.widget.RecyclerView", false, false, 0, 2300, 1080, 2300);
        expect("TikTok opening animation is ignored before swipe detection is armed", false,
                tikTokViewer.scrolled(recyclerVideoPager, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        false, 2300, 0, 7, 2300));
        expect("TikTok opening position stays allowed after arming", false,
                tikTokViewer.scrolled(recyclerVideoPager, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, 0, 0, 7, 2300));
        expect("TikTok repeated stationary event is not a swipe", false,
                tikTokViewer.scrolled(recyclerVideoPager, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, -1, -1, 7, 2300));
        ScreenRules.NodeData commentsList = new ScreenRules.NodeData("", "", "", "comments_list",
                "androidx.recyclerview.widget.RecyclerView", false, false, 0, 2300, 1080, 2300);
        tikTokComments.open(true, 67000);
        tikTokViewer.resetScroll();
        List<ScreenRules.NodeData> tikTokSparseComments = nodes(
                wideNode("", "", "video_pager", 0, 2300),
                node("Add comment…", "", "", false, 2100, 100));
        expect("TikTok sparse comment composer preserves a known shared-video session", true,
                tikTokComments.allows(tiktok(tikTokSparseComments),
                        ScreenRules.commentContent(tikTokSparseComments, DISPLAY_HEIGHT, DISPLAY_WIDTH),
                        true, 72000));
        expect("TikTok empty comment snapshot does not prompt Chats only", true,
                tikTokComments.allows(tiktok(nodes()), false, true, 120000));
        expect("TikTok comment list movement is allowed", false,
                tikTokViewer.scrolled(commentsList, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, 2400, 0, 49, 2400));
        tikTokComments.requestClose(121000);
        expect("TikTok Back from comments keeps the video during animation", true,
                tikTokComments.allows(ScreenRules.Screen.OTHER, false, true, 121100));
        expect("TikTok closing comments ends only the comment context", false,
                tikTokComments.allows(tiktok(tikTokWithoutBack), false, true, 121400));
        expect("TikTok closing comments re-establishes stationary baseline", false,
                tikTokViewer.scrolled(recyclerVideoPager, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, -1, -1, 7, 2300));
        expect("TikTok next page with undefined deltas is still blocked", true,
                tikTokViewer.scrolled(recyclerVideoPager, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, -1, -1, 8, 2300));
        tikTokViewer.clear();
        expect("TikTok leaving app clears loading permission", false, tikTokViewer.loading(122000));
        expect("TikTok first stationary event after a new video is allowed", false,
                tikTokViewer.scrolled(recyclerVideoPager, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, 0, 0, 20, 0));
        expect("TikTok partial movement stays below paging threshold", false,
                tikTokViewer.scrolled(recyclerVideoPager, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, 400, 0, 20, 400));
        expect("TikTok cumulative vertical paging is blocked", true,
                tikTokViewer.scrolled(recyclerVideoPager, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, 500, 0, 20, 900));
        ScreenRules.NodeData listFeedPager = new ScreenRules.NodeData("", "", "", "feed_pager",
                "android.widget.ListView", false, false, 0, 2300, 1080, 2300);
        expect("TikTok replacing pager source establishes a fresh baseline", false,
                tikTokViewer.scrolled(listFeedPager, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, 0, 0, 0, 0));
        expect("TikTok known ListView pager with real movement is blocked", true,
                tikTokViewer.scrolled(listFeedPager, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, 2400, 0, 1, 2400));
        expect("TikTok feed pager without other labels is recognized", ScreenRules.Screen.REEL,
                tiktok(nodes(listFeedPager)));
        ScreenRules.NodeData namedCommentsPager = new ScreenRules.NodeData("", "Comments", "", "video_pager",
                "androidx.recyclerview.widget.RecyclerView", false, false, 0, 2300, 1080, 2300);
        expect("TikTok comment label overrides known pager ID", false,
                tikTokViewer.scrolled(namedCommentsPager, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, 2400, 0, 2, 2400));
        ScreenRules.NodeData unnamedList = new ScreenRules.NodeData("", "", "", "",
                "androidx.recyclerview.widget.RecyclerView", false, false, 0, 2300, 1080, 2300);
        expect("TikTok unidentified list cannot count as next video", false,
                tikTokViewer.scrolled(unnamedList, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, 2400, 0, 2, 2400));
        ScreenRules.NodeData smallVideoPager = new ScreenRules.NodeData("", "", "", "video_pager",
                "androidx.recyclerview.widget.RecyclerView", false, false, 800, 1400, 1080, 600);
        expect("TikTok embedded preview cannot count as full-screen paging", false,
                tikTokViewer.scrolled(smallVideoPager, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, 2400, 0, 2, 2400));
        ScreenRules.NodeData unmarkedVideoContainer = new ScreenRules.NodeData("", "", "", "",
                "android.widget.FrameLayout", false, false, 0, 2300, 1080, 2300);
        expect("TikTok unmarked viewer needs substantial vertical movement", true,
                tikTokViewer.scrolled(unmarkedVideoContainer, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, -900, 0, -1, -1));
        expect("TikTok horizontal movement is allowed", false,
                tikTokViewer.scrolled(unmarkedVideoContainer, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, 900, 1100, -1, -1));
        expect("TikTok unmarked viewer layout alone is allowed", false,
                tikTokViewer.scrolled(unmarkedVideoContainer, DISPLAY_HEIGHT, DISPLAY_WIDTH,
                        true, -1, -1, -1, -1));
        expect("TikTok chat context cannot permit For You feed", false,
                ScreenRules.tiktokOpenedViewer(tiktokForYou, DISPLAY_HEIGHT, true));
        expect("TikTok chat context cannot permit Friends feed", false,
                ScreenRules.tiktokOpenedViewer(tiktokFriendsFeed, DISPLAY_HEIGHT, true));
        expect("TikTok unselected Following heading still rejects feed", false,
                ScreenRules.tiktokOpenedViewer(nodes(node("Following", "", "", false, 110, 80),
                        wideNode("", "", "video_pager", 0, 2300)), DISPLAY_HEIGHT, true));
        expect("TikTok selected bottom Friends tab revokes chat permission", false,
                ScreenRules.tiktokSharedVideoEligible(nodes(node("Friends", "", "friends_tab",
                        true, 2250, 80)), DISPLAY_HEIGHT));
        expect("TikTok selected Home means feed navigation during loading", true,
                ScreenRules.tiktokFeedNavigation(nodes(node("Home", "", "home_tab", true, 2250, 80)),
                        DISPLAY_HEIGHT));
        expect("TikTok blank loading snapshot is not feed navigation", false,
                ScreenRules.tiktokFeedNavigation(nodes(), DISPLAY_HEIGHT));
        expect("TikTok commenter profile ends shared-video context", true,
                ScreenRules.tiktokProfileScreen(nodes(node("12 Following", "", "", false, 400, 80),
                        node("104 Followers", "", "", false, 400, 80),
                        node("1.2K Likes", "", "", false, 400, 80)), DISPLAY_HEIGHT));
        expect("TikTok video controls are not a profile", false,
                ScreenRules.tiktokProfileScreen(tiktokOpenedVideo, DISPLAY_HEIGHT));
        tikTokOrigin.navigationClick(123000);
        expect("TikTok navigation cannot reuse the old chat tap", false,
                tikTokOrigin.mediaClickFromChat(123100, false, true));
        expect("TikTok video tap outside a chat cannot create permission", false,
                tikTokOrigin.mediaClickFromChat(125000, false, false));
        tikTokOrigin.sawChat(126000);
        tikTokOrigin.leftChat(180000);
        expect("TikTok source-less idle-chat handoff permits a viewer with Back", true,
                tikTokOrigin.canOpen(180100)
                        && ScreenRules.tiktokOpenedViewer(tiktokOpenedVideo, DISPLAY_HEIGHT));
        expect("TikTok source-less handoff does not permit a dedicated viewer without Back", false,
                ScreenRules.tiktokOpenedViewer(tikTokWithoutBack, DISPLAY_HEIGHT,
                        tikTokOrigin.hasMediaClick(180100)));

        expect("Instagram bottom DM tab", true, ScreenRules.instagramMessagesButton(
                node("", "Messages, 2 unread, tab", "", false, 2250, 80), DISPLAY_HEIGHT));
        expect("Instagram top Messages icon", true, ScreenRules.instagramMessagesButton(
                node("", "Messages", "", false, 100, 80), DISPLAY_HEIGHT));
        expect("Instagram direct tab resource", true, ScreenRules.instagramMessagesButton(
                node("", "", "tab_direct", false, 2250, 80), DISPLAY_HEIGHT));
        expect("Chat message text is not a navigation tab", false, ScreenRules.instagramMessagesButton(
                node("Messages", "", "", false, 1100, 80), DISPLAY_HEIGHT));
        expect("Send button is not the DM tab", false, ScreenRules.instagramMessagesButton(
                node("Send", "", "", false, 2250, 80), DISPLAY_HEIGHT));
        List<ScreenRules.NodeData> commentsOverVideo = nodes(
                wideNode("", "", "reels_viewer", 0, 2300),
                wideNode("", "", "video_pager", 0, 2300),
                railNode("Like", "", 1300),
                railNode("Comment", "", 1500),
                railNode("Share", "", 1700),
                node("Comments", "", "", false, 500, 90),
                node("Add a comment...", "", "", false, 2150, 100,
                        "android.widget.EditText"));
        expect("Instagram comments override underlying Reel", ScreenRules.Screen.COMMENTS,
                instagram(commentsOverVideo));
        expect("Facebook comments override underlying Reel", false,
                facebook(commentsOverVideo));
        expect("TikTok comments override underlying video", ScreenRules.Screen.COMMENTS,
                tiktok(commentsOverVideo));
        expect("Comment button alone is not an open comment panel", false,
                ScreenRules.commentsPanel(tiktokOpenedVideo, DISPLAY_HEIGHT, DISPLAY_WIDTH));
        expect("Facebook viewer composer alone is still a Reel", true,
                facebook(screenshotLikeFacebookViewer));
        List<ScreenRules.NodeData> commentSheet = nodes(
                wideNode("", "", "comments_bottom_sheet", 900, 1400));
        expect("Dedicated comment sheet is recognized", true,
                ScreenRules.commentsPanel(commentSheet, DISPLAY_HEIGHT, DISPLAY_WIDTH));
        List<ScreenRules.NodeData> commentListWithKeyboard = nodes(
                wideNode("", "", "comments_list", 400, 1100),
                node("Write a comment", "", "", false, 1600, 100,
                        "android.widget.EditText"));
        expect("Comment list with keyboard remains allowed", true,
                ScreenRules.commentsPanel(commentListWithKeyboard, DISPLAY_HEIGHT, DISPLAY_WIDTH));
        expect("Comment heading alone does not exempt a video", false,
                ScreenRules.commentsPanel(nodes(node("Comments", "", "", false, 500, 90)),
                        DISPLAY_HEIGHT, DISPLAY_WIDTH));
        List<ScreenRules.NodeData> countedActions = nodes(
                node("", "Back", "", false, 90, 80),
                node("Original audio", "", "", false, 1800, 80),
                railNode("", "Like, 100", 1300),
                railNode("", "Comment, 20", 1500),
                railNode("", "Share, 5", 1700));
        expect("Counted Instagram controls identify an opened Reel", ScreenRules.Screen.REEL,
                instagram(countedActions));
        expect("Chat history alone cannot grant a Reel", false,
                ScreenRules.instagramSharedReelEligible(countedActions, DISPLAY_HEIGHT, false));
        expect("Recent chat media click permits an opened Reel", true,
                ScreenRules.instagramSharedReelEligible(countedActions, DISPLAY_HEIGHT, true));
        expect("Selected Reels feed cannot use a chat media click", false,
                ScreenRules.instagramSharedReelEligible(reelTab, DISPLAY_HEIGHT, true));

        List<ScreenRules.NodeData> countedFeed = nodes(
                node("Home", "", "", true, 2250, 80),
                railNode("", "Like, 100", 1300),
                railNode("", "Comment, 20", 1500),
                railNode("", "Share, 5", 1700));
        expect("Counted controls alone do not turn Home into a viewer", ScreenRules.Screen.OTHER,
                instagram(countedFeed));
        for (int labelField = 0; labelField < 3; labelField++) {
            List<ScreenRules.NodeData> describedComments = nodes(
                    wideNode("", "", "reels_viewer", 0, 2300),
                    wideNode("", "", "video_pager", 0, 2300),
                    new ScreenRules.NodeData(labelField == 0 ? "128 comments" : "",
                            labelField == 1 ? "128 comments" : "",
                            labelField == 2 ? "128 comments" : "", "", "", false, true,
                            500, 590, 500, 90),
                    node("Add a comment...", "", "", false, 2150, 100,
                            "android.widget.EditText"));
            expect("Instagram counted comments label field " + labelField, ScreenRules.Screen.COMMENTS,
                    instagram(describedComments));
            expect("Facebook counted comments label field " + labelField, false,
                    facebook(describedComments));
            expect("TikTok counted comments label field " + labelField, ScreenRules.Screen.COMMENTS,
                    tiktok(describedComments));
        }
        expect("Username inbox with search and compose", ScreenRules.Screen.DM_INBOX,
                instagram(nodes(node("darren236", "", "", false, 100, 80),
                        node("Search", "", "", false, 300, 100, "android.widget.EditText"),
                        node("", "New message", "", false, 100, 80))));
        expect("Username inbox with Notes and counted Requests", ScreenRules.Screen.DM_INBOX,
                instagram(nodes(node("darren236", "", "", false, 100, 80),
                        node("Your note", "", "", false, 400, 80),
                        node("Requests (5)", "", "", false, 900, 80))));
        expect("Inbox heading below Notes", ScreenRules.Screen.DM_INBOX,
                instagram(nodes(node("Messages", "", "", false, 950, 80),
                        node("Requests", "", "", false, 950, 80))));
        expect("Home inbox icon does not qualify as inbox", ScreenRules.Screen.OTHER,
                instagram(nodes(node("", "Messages", "direct_inbox", false, 100, 80))));

        List<ScreenRules.NodeData> narrowComments = nodes(
                wideNode("", "", "reels_viewer", 0, 2300),
                wideNode("", "", "video_pager", 0, 2300),
                new ScreenRules.NodeData("", "Comments (128)", "", "", "", false, false,
                        400, 650, 730, 240, 80),
                node("Add comment…", "", "", false, 2100, 100));
        expect("Instagram narrow comment heading", ScreenRules.Screen.COMMENTS, instagram(narrowComments));
        expect("Facebook narrow comment heading", false, facebook(narrowComments));
        expect("TikTok narrow comment heading", ScreenRules.Screen.COMMENTS, tiktok(narrowComments));
        ScreenRules.NodeData genericList = new ScreenRules.NodeData("", "", "", "", "androidx.recyclerview.widget.RecyclerView",
                false, false, 0, 800, 2000, 1080, 1200);
        List<ScreenRules.NodeData> sortedComments = nodes(
                wideNode("", "", "reels_viewer", 0, 2300), genericList,
                node("Most relevant", "", "", false, 700, 80),
                node("Write a comment…", "", "", false, 2100, 100));
        expect("Facebook generic comment list and sorting", false, facebook(sortedComments));
        List<ScreenRules.NodeData> replies = nodes(wideNode("", "", "video_pager", 0, 2300),
                genericList, node("Reply", "", "", false, 1000, 70),
                node("Reply", "", "", false, 1500, 70),
                node("Add comment…", "", "", false, 2100, 100));
        expect("TikTok generic comments list and replies", ScreenRules.Screen.COMMENTS, tiktok(replies));
        expect("Custom-rendered Facebook comments without list class", false,
                facebook(nodes(wideNode("", "", "reels_viewer", 0, 2300),
                        node("All comments", "", "", false, 700, 80),
                        node("Write a public comment…", "", "", false, 2100, 100))));
        expect("Custom-rendered TikTok comments without list class", ScreenRules.Screen.COMMENTS,
                tiktok(nodes(wideNode("", "", "video_pager", 0, 2300),
                        node("Reply", "", "", false, 1000, 70),
                        node("Reply", "", "", false, 1500, 70),
                        node("Add comment…", "", "", false, 2100, 100))));
        List<ScreenRules.NodeData> reelWithList = nodes(genericList,
                wideNode("", "", "reels_viewer", 0, 2300),
                railNode("", "Comments", 1500),
                node("Add a comment...", "", "", false, 2100, 100));
        expect("Right-side comment button and composer remain a Reel", true, facebook(reelWithList));
        expect("Reply list without composer is not comments", false,
                ScreenRules.commentsPanel(nodes(genericList, node("Reply", "", "", false, 1000, 70),
                        node("Reply", "", "", false, 1500, 70)), DISPLAY_HEIGHT, DISPLAY_WIDTH));

        expect("Back confirms opened Instagram shared viewer", true,
                ScreenRules.instagramOpenedViewer(countedActions, DISPLAY_HEIGHT));
        expect("Selected Instagram feed cannot use missing-click fallback", false,
                ScreenRules.instagramOpenedViewer(nodes(node("Reels", "", "", true, 2250, 80),
                        node("Back", "", "", false, 100, 80)), DISPLAY_HEIGHT));
        expect("Home cannot use missing-click fallback", false,
                ScreenRules.instagramOpenedViewer(nodes(node("Home", "", "", true, 2250, 80),
                        node("Back", "", "", false, 100, 80)), DISPLAY_HEIGHT));
        ChatVideoOrigin origin = new ChatVideoOrigin();
        expect("Unknown event cannot create shared video origin", false, origin.canOpen(100));
        origin.sawChat(1000);
        expect("Missing click source preserves recent-chat handoff", true, origin.canOpen(1400));
        expect("Loading screen gets bounded transition grace", true, origin.transitioning(2000));
        expect("Stale chat cannot permit unrelated viewer", false, origin.canOpen(2500));
        origin.mediaClick(3000);
        expect("Explicit media tap survives a slower load", true, origin.canOpen(5200));
        expect("Explicit tap loading remains allowed past old timeout", true, origin.transitioning(7000));
        expect("Explicit tap expires", false, origin.canOpen(11000));
        origin.navigationClick(12000);
        origin.sawChat(12100);
        expect("Navigation rejects stale chat snapshot", false, origin.canOpen(12200));
        origin.sawChat(14000);
        origin.mediaClick(14100);
        origin.clear();
        expect("Leaving app clears shared video evidence", false, origin.canOpen(14200));

        ChatVideoOrigin delayedTap = new ChatVideoOrigin();
        delayedTap.sawChat(1000);
        expect("Idle chat tap survives root already changing to viewer", true,
                delayedTap.mediaClickFromChat(61000, false, true));
        expect("Delayed shared Reel still has origin during loading", true, delayedTap.canOpen(66000));
        expect("Partial loading screen cannot expire earlier than media origin", true,
                delayedTap.transitioning(66000));
        expect("Loading grace remains bounded", false, delayedTap.canOpen(69000));
        delayedTap.navigationClick(70000);
        expect("Navigation rejects delayed media click on old chat snapshot", false,
                delayedTap.mediaClickFromChat(70100, false, true));
        expect("New explicit media tap in a confirmed chat survives quick Back navigation", true,
                delayedTap.mediaClickFromChat(70200, true, false));
        expect("Confirmed chat tap restores origin immediately", true, delayedTap.canOpen(70300));
        delayedTap.clear();
        expect("Media tap outside chats cannot establish origin", false,
                delayedTap.mediaClickFromChat(72000, false, false));
        expect("First observed chat click can establish origin", true,
                delayedTap.mediaClickFromChat(73000, true, false));
        ChatVideoOrigin idleChat = new ChatVideoOrigin();
        idleChat.sawChat(1000);
        idleChat.leftChat(61000);
        expect("Idle chat handoff starts grace when viewer opens", true, idleChat.canOpen(61100));
        expect("Missing click handoff is still bounded", false, idleChat.canOpen(62500));
        idleChat.navigationClick(63000);
        idleChat.leftChat(63100);
        expect("Navigation cannot rearm old thread handoff", false, idleChat.canOpen(63200));
        idleChat.clear();
        idleChat.leftChat(65000);
        expect("Unobserved chat cannot manufacture handoff evidence", false, idleChat.canOpen(65100));
        List<ScreenRules.NodeData> viewerWithoutBack = nodes(
                wideNode("", "", "clips_viewer", 0, 2300), railNode("Like", "", 1300),
                railNode("Comment", "", 1500), railNode("Share", "", 1700));
        expect("Source-less fallback still requires viewer navigation", false,
                ScreenRules.instagramOpenedViewer(viewerWithoutBack, DISPLAY_HEIGHT));
        expect("Explicit chat media tap allows dedicated viewer with missing Back label", true,
                ScreenRules.instagramOpenedViewer(viewerWithoutBack, DISPLAY_HEIGHT, true));
        expect("Explicit tap cannot make generic feed preview a shared viewer", false,
                ScreenRules.instagramOpenedViewer(instagramFeed, DISPLAY_HEIGHT, true));
        expect("Explicit chat tap cannot permit selected Reels feed", false,
                ScreenRules.instagramOpenedViewer(reelTab, DISPLAY_HEIGHT, true));

        ReelViewerState sharedViewer = new ReelViewerState();
        ScreenRules.NodeData instagramPager = new ScreenRules.NodeData("", "", "", "clips_viewer",
                "androidx.recyclerview.widget.RecyclerView", false, false, 0, 2300, 1080, 2300);
        ScreenRules.NodeData instagramCommentsList = new ScreenRules.NodeData("", "", "", "comments_list",
                "androidx.recyclerview.widget.RecyclerView", false, false, 0, 2300, 1080, 2300);
        sharedViewer.sawViewer(1000);
        expect("Allowed shared viewer survives a partial loading tree", true, sharedViewer.loading(3000));
        expect("Unrecognized viewer grace expires", false, sharedViewer.loading(4000));
        expect("Initial full-size scroll event establishes baseline without leaving", false,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 0, 0, 17, 0));
        expect("Repeated layout event is not the next Reel", false,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 0, 0, 17, 0));
        expect("Comment list movement cannot advance shared Reel", false,
                sharedViewer.scrolled(instagramCommentsList, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 2400, 0, 49, 2400));
        expect("Ignored comment list leaves viewer baseline unchanged", false,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 0, 0, 17, 0));
        expect("Actual next Reel is detected with zero reported delta", true,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 0, 0, 18, 0));
        sharedViewer.resetScroll();
        expect("Android unset scroll deltas establish a baseline", false,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, -1, -1, 17, 0));
        expect("Android unset deltas do not mask a real next-page index", true,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, -1, -1, 18, 0));
        sharedViewer.resetScroll();
        expect("Comment transitions retain permission for the same shared viewer", true, sharedViewer.loading(3000));
        expect("Closing comments re-establishes scroll baseline", false,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 0, 0, 18, 0));
        expect("Swipe after closing comments is still blocked", true,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 900, 0, 19, 900));
        sharedViewer.clear();
        expect("App switch revokes loading permission", false, sharedViewer.loading(3100));
        expect("Opening animation can populate paging baseline before scroll is armed", false,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, false, 2400, 0, 8, 2400));
        expect("Opening position does not become a swipe after arming", false,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 0, 0, 8, 2400));
        expect("Next index after opening animation is blocked", true,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 0, 0, 9, 2400));
        sharedViewer.resetScroll();
        expect("Unindexed pager establishes its scroll position", false,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 0, 0, -1, 0));
        expect("Small partial movement does not mean next Reel", false,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 400, 0, -1, 400));
        expect("Cumulative vertical paging is detected", true,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 500, 0, -1, 900));
        sharedViewer.resetScroll();
        expect("Horizontal swipe is not another Reel", false,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 100, 1200, 20, 0));
        expect("Horizontal update does not poison subsequent layout event", false,
                sharedViewer.scrolled(instagramPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 0, 0, 20, 0));
        ScreenRules.NodeData unrelatedContainer = new ScreenRules.NodeData("", "", "", "root",
                "android.widget.FrameLayout", false, false, 0, 2300, 1080, 2300);
        expect("Large container layout alone cannot count as viewer paging", false,
                sharedViewer.scrolled(unrelatedContainer, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 2400, 0, 21, 2400));
        ScreenRules.NodeData embeddedPager = new ScreenRules.NodeData("", "", "", "clips_viewer",
                "androidx.recyclerview.widget.RecyclerView", false, false, 800, 1400, 1080, 600);
        expect("Embedded chat Reel preview scroll is not full-screen paging", false,
                sharedViewer.scrolled(embeddedPager, DISPLAY_HEIGHT, DISPLAY_WIDTH, true, 2400, 0, 21, 2400));
        expect("Selected Home identifies leaving shared viewer", true,
                ScreenRules.instagramFeedNavigation(nodes(node("Home", "", "", true, 2250, 80)), DISPLAY_HEIGHT));
        expect("Empty loading tree is not feed navigation", false,
                ScreenRules.instagramFeedNavigation(nodes(), DISPLAY_HEIGHT));
        expect("Reel caption mentioning Home is not feed navigation", false,
                ScreenRules.instagramFeedNavigation(nodes(node("Home", "", "", false, 1300, 80)), DISPLAY_HEIGHT));
        List<ScreenRules.NodeData> sparseComments = nodes(
                node("Add a comment…", "", "", false, 2100, 100));
        expect("Sparse Instagram comment tree needs shared-Reel context", false,
                ScreenRules.commentsPanel(sparseComments, DISPLAY_HEIGHT, DISPLAY_WIDTH));
        expect("Sparse composer identifies comments inside allowed shared Reel", true,
                ScreenRules.commentContent(sparseComments, DISPLAY_HEIGHT, DISPLAY_WIDTH));
        expect("Inline composer cannot start a comment session by itself", false,
                ScreenRules.commentPanelEvidence(sparseComments, DISPLAY_HEIGHT, DISPLAY_WIDTH));
        expect("Narrow comment heading can establish a sheet", true,
                ScreenRules.commentPanelEvidence(nodes(new ScreenRules.NodeData("Comments", "", "", "", "",
                        false, false, 400, 800, 880, 240, 80)), DISPLAY_HEIGHT, DISPLAY_WIDTH));
        expect("Commenter profile is identifiable outside allowed sheet", true,
                ScreenRules.instagramProfileScreen(nodes(node("Posts", "", "", false, 400, 80),
                        node("Followers", "", "", false, 400, 80),
                        node("Following", "", "", false, 400, 80)), DISPLAY_HEIGHT, DISPLAY_WIDTH));
        expect("Video rail is not a commenter profile", false,
                ScreenRules.instagramProfileScreen(countedActions, DISPLAY_HEIGHT, DISPLAY_WIDTH));
        expect("Right-side comment button cannot keep shared comments open", false,
                ScreenRules.commentContent(nodes(railNode("", "Comments", 1500)),
                        DISPLAY_HEIGHT, DISPLAY_WIDTH));
        expect("Generic video list cannot keep comments open", false,
                ScreenRules.commentContent(nodes(genericList), DISPLAY_HEIGHT, DISPLAY_WIDTH));

        SharedReelComments sharedComments = new SharedReelComments();
        sharedComments.open(false, 1000);
        expect("Comment click cannot create a shared-Reel permission", false, sharedComments.isOpen());
        sharedComments.open(true, 2000);
        expect("Comment loading snapshot stays allowed", true,
                sharedComments.allows(ScreenRules.Screen.OTHER, false, true, 2100));
        expect("Comments survive old transition timeout", true,
                sharedComments.allows(ScreenRules.Screen.OTHER, false, true, 4000));
        expect("Reading comments for a long time remains allowed", true,
                sharedComments.allows(ScreenRules.Screen.OTHER, false, true, 62000));
        expect("Underlying Reel controls do not block recognized comment content", true,
                sharedComments.allows(ScreenRules.Screen.REEL, true, true, 63000));
        sharedComments.requestClose(64000);
        expect("Back from comments preserves Reel during animation", true,
                sharedComments.allows(ScreenRules.Screen.OTHER, false, true, 64100));
        expect("Returning to viewer ends comment context", false,
                sharedComments.allows(ScreenRules.Screen.REEL, false, true, 65000));
        expect("Viewer swipe is no longer exempt after closing comments", false, sharedComments.isOpen());
        sharedComments.open(true, 66000);
        expect("Comments can reopen for same allowed Reel", true,
                sharedComments.allows(ScreenRules.Screen.COMMENTS, true, true, 67000));
        expect("Chat return clears comment context", false,
                sharedComments.allows(ScreenRules.Screen.DM_THREAD, true, true, 68000));
        sharedComments.open(true, 69000);
        expect("Inbox return clears comment context", false,
                sharedComments.allows(ScreenRules.Screen.DM_INBOX, false, true, 70000));
        sharedComments.open(true, 71000);
        expect("Revoked Reel cannot retain comment permission", false,
                sharedComments.allows(ScreenRules.Screen.OTHER, true, false, 72000));
        sharedComments.open(true, 73000);
        sharedComments.clear();
        expect("Navigation/app switch clears comment permission", false,
                sharedComments.allows(ScreenRules.Screen.OTHER, false, true, 74000));
        sharedComments.open(true, 75000);
        sharedComments.requestClose(76000);
        expect("Unidentified dismissed sheet has bounded close animation", false,
                sharedComments.allows(ScreenRules.Screen.OTHER, false, true, 77200));
        sharedComments.open(true, 80000);
        sharedComments.requestClose(81000);
        expect("Inline composer after closing cannot suppress viewer swipe", false,
                sharedComments.allows(ScreenRules.Screen.REEL, true, true, 81300));
        System.out.println("ScreenRules tests passed: " + assertionCount + " assertions");
    }

    private static ScreenRules.Screen instagram(List<ScreenRules.NodeData> nodes) {
        return ScreenRules.instagram(nodes, DISPLAY_HEIGHT, DISPLAY_WIDTH);
    }

    private static boolean facebook(List<ScreenRules.NodeData> nodes) {
        return ScreenRules.facebookReel(nodes, DISPLAY_HEIGHT, DISPLAY_WIDTH);
    }

    private static ScreenRules.Screen tiktok(List<ScreenRules.NodeData> nodes) {
        return ScreenRules.tiktok(nodes, DISPLAY_HEIGHT, DISPLAY_WIDTH);
    }

    private static ScreenRules.NodeData node(String text, String description, String id,
                                             boolean selected, int top, int height) {
        return node(text, description, id, selected, top, height, "");
    }

    private static ScreenRules.NodeData node(String text, String description, String id,
                                             boolean selected, int top, int height,
                                             String className) {
        return new ScreenRules.NodeData(text, description, "", id, className,
                selected, true, top, top + height, 500, height);
    }

    private static ScreenRules.NodeData wideNode(String text, String description, String id,
                                                 int top, int height) {
        return new ScreenRules.NodeData(text, description, "", id, "",
                false, true, top, top + height, 1000, height);
    }

    private static ScreenRules.NodeData railNode(String text, String description, int top) {
        return new ScreenRules.NodeData(text, description, "", "", "", false, true,
                920, top, top + 80, 100, 80);
    }

    private static List<ScreenRules.NodeData> nodes(ScreenRules.NodeData... values) {
        List<ScreenRules.NodeData> result = new ArrayList<ScreenRules.NodeData>();
        for (ScreenRules.NodeData value : values) result.add(value);
        return result;
    }

    private static void expect(String name, Object expected, Object actual) {
        assertionCount++;
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    private ScreenRulesTest() { }
}
