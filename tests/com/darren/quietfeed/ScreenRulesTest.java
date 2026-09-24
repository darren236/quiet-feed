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

        System.out.println("ScreenRules tests passed");
    }

    private static ScreenRules.Screen instagram(List<ScreenRules.NodeData> nodes) {
        return ScreenRules.instagram(nodes, DISPLAY_HEIGHT, DISPLAY_WIDTH);
    }

    private static boolean facebook(List<ScreenRules.NodeData> nodes) {
        return ScreenRules.facebookReel(nodes, DISPLAY_HEIGHT, DISPLAY_WIDTH);
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
