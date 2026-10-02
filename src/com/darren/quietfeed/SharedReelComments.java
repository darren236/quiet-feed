package com.darren.quietfeed;

/** Comments belong to the already allowed Reel, including incomplete UI snapshots. */
final class SharedReelComments {
    private boolean open;
    private long openedAt;
    private long closeRequestedAt = -1;

    void open(boolean sharedReelAllowed, long now) {
        if (!sharedReelAllowed || open) return;
        open = true;
        openedAt = now;
        closeRequestedAt = -1;
    }

    boolean isOpen() { return open; }

    void requestClose(long now) { if (open) closeRequestedAt = now; }

    boolean allows(ScreenRules.Screen screen, boolean commentContent,
                   boolean sharedReelAllowed, long now) {
        if (!open || !sharedReelAllowed || screen == ScreenRules.Screen.DM_THREAD
                || screen == ScreenRules.Screen.DM_INBOX || screen == ScreenRules.Screen.LOGIN) {
            clear();
            return false;
        }
        if (screen == ScreenRules.Screen.COMMENTS) return true;
        if (screen == ScreenRules.Screen.REEL && closeRequestedAt >= 0
                && now >= closeRequestedAt + 300) {
            clear();
            return false;
        }
        if (commentContent) return true;
        if (screen == ScreenRules.Screen.REEL && now >= openedAt + 300) {
            // The sheet has closed. The service keeps permission for the same Reel.
            clear();
            return false;
        }
        if (closeRequestedAt >= 0 && now >= closeRequestedAt + 1200) {
            clear();
            return false;
        }
        // A partial/empty comment tree must not turn a known shared Reel into OTHER.
        return true;
    }

    void clear() { open = false; openedAt = 0; closeRequestedAt = -1; }
}
