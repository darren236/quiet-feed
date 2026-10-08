package com.darren.quietfeed;

/** Short-lived evidence for a shared video, including apps that omit click sources. */
final class ChatVideoOrigin {
    private static final long TRANSITION_MS = 1500;
    private static final long MEDIA_CLICK_MS = 8000;
    private long chatAt = -1;
    private long mediaAt = -1;
    private long blockedUntil;

    void sawChat(long now) {
        if (now >= blockedUntil) chatAt = now;
    }

    void leftChat(long now) {
        // An idle thread need not emit events before the viewer starts opening.
        if (chatAt >= 0 && now >= blockedUntil) chatAt = now;
    }

    void mediaClick(long now) { mediaAt = now; }

    boolean mediaClickFromChat(long now, boolean currentChat, boolean previousChat) {
        if (!currentChat && !previousChat) return false;
        if (now < blockedUntil && !currentChat) return false;
        if (currentChat) {
            // A new explicit tap in a confirmed thread is stronger than animation suppression.
            blockedUntil = 0;
            chatAt = now;
        }
        mediaClick(now);
        return true;
    }

    void navigationClick(long now) {
        clear();
        // Ignore an old chat snapshot while navigation is animating.
        blockedUntil = now + TRANSITION_MS;
    }

    boolean canOpen(long now) {
        return now >= blockedUntil && (recent(chatAt, now, TRANSITION_MS)
                || recent(mediaAt, now, MEDIA_CLICK_MS));
    }

    boolean hasMediaClick(long now) {
        return now >= blockedUntil && recent(mediaAt, now, MEDIA_CLICK_MS);
    }

    boolean transitioning(long now) {
        return canOpen(now);
    }

    void clear() { chatAt = -1; mediaAt = -1; blockedUntil = 0; }

    private static boolean recent(long started, long now, long duration) {
        return started >= 0 && now >= started && now - started < duration;
    }
}
