package com.darren.quietfeed;

/** Short-lived evidence for a shared video, including apps that omit click sources. */
final class ChatVideoOrigin {
    private static final long TRANSITION_MS = 1500;
    private static final long MEDIA_CLICK_MS = 3000;
    private long chatAt = -1;
    private long mediaAt = -1;
    private long blockedUntil;

    void sawChat(long now) {
        if (now >= blockedUntil) chatAt = now;
    }

    void mediaClick(long now) { mediaAt = now; }

    void navigationClick(long now) {
        clear();
        // Ignore an old chat snapshot while navigation is animating.
        blockedUntil = now + TRANSITION_MS;
    }

    boolean canOpen(long now) {
        return now >= blockedUntil && (recent(chatAt, now, TRANSITION_MS)
                || recent(mediaAt, now, MEDIA_CLICK_MS));
    }

    boolean transitioning(long now) {
        return now >= blockedUntil && (recent(chatAt, now, TRANSITION_MS)
                || recent(mediaAt, now, TRANSITION_MS));
    }

    void clear() { chatAt = -1; mediaAt = -1; blockedUntil = 0; }

    private static boolean recent(long started, long now, long duration) {
        return started >= 0 && now >= started && now - started < duration;
    }
}
