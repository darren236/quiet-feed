package com.darren.quietfeed;

/** Preserves a shared viewer through short loading gaps and distinguishes paging from layout. */
final class ReelViewerState {
    private long lastViewerAt = -1;
    private String sourceKey = "";
    private int lastIndex = -1;
    private int lastScrollY = -1;

    void sawViewer(long now) { lastViewerAt = now; }

    boolean loading(long now) {
        return lastViewerAt >= 0 && now >= lastViewerAt && now - lastViewerAt < 3000;
    }

    boolean scrolled(ScreenRules.NodeData source, int height, int width, boolean armed,
                     int deltaY, int deltaX, int firstIndex, int scrollY) {
        if (source == null || source.idContains("comment") || source.labelContains("comments")
                || source.width < width * 2 / 3 || source.height < height * 2 / 3)
            return false;
        boolean pager = source.idContains("clips_viewer") || source.idContains("clips_pager")
                || source.idContains("reel_pager") || source.idContains("reels_viewer")
                || source.idContains("reel_viewer") || source.className.contains("viewpager")
                || source.className.contains("recyclerview");
        if (!pager) return false;
        String key = source.id + "|" + source.className;
        if (!key.equals(sourceKey)) {
            sourceKey = key;
            lastIndex = -1;
            lastScrollY = -1;
        }
        boolean changedIndex = firstIndex >= 0 && lastIndex >= 0 && firstIndex != lastIndex;
        boolean movedPosition = scrollY >= 0 && lastScrollY >= 0
                && Math.abs((long) scrollY - lastScrollY) >= height / 3;
        if (firstIndex >= 0) lastIndex = firstIndex;
        if (scrollY >= 0 && (lastScrollY < 0 || !armed)) lastScrollY = scrollY;
        // AccessibilityRecord uses -1 for an unset delta; it is not horizontal movement.
        long vertical = deltaY == -1 ? 0 : Math.abs((long) deltaY);
        long horizontal = deltaX == -1 ? 0 : Math.abs((long) deltaX);
        if (!armed || (horizontal > 0 && horizontal >= vertical)) {
            if (scrollY >= 0) lastScrollY = scrollY;
            return false;
        }
        return (vertical >= height / 3 && vertical > horizontal) || changedIndex || movedPosition;
    }

    void clear() {
        lastViewerAt = -1;
        resetScroll();
    }

    void resetScroll() {
        sourceKey = "";
        lastIndex = -1;
        lastScrollY = -1;
    }
}
