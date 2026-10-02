package com.darren.quietfeed;

import java.util.ArrayList;
import java.util.List;

public final class ScreenRulesTest {
    private static final int DISPLAY_HEIGHT = 2400;
    private static final int DISPLAY_WIDTH = 1080;

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
        System.out.println("ScreenRules tests passed");
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
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    private ScreenRulesTest() { }
}
