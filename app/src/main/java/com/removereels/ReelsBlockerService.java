package com.removereels;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/**
 * Hides Instagram's Reels tab while blocking is on.
 *
 * Two layers:
 *  1. An opaque, touch-eating overlay is drawn exactly over the Reels button in
 *     Instagram's bottom tab bar, so the button disappears and can't be tapped.
 *  2. If the Reels tab still somehow becomes the selected tab (e.g. by swiping
 *     between tabs), we immediately switch back to the Home tab.
 *
 * Reels opened from DMs, the feed or links open in Instagram's reel viewer
 * without selecting the Reels tab, so they still play. But that viewer lets you
 * swipe on to an endless stream of other reels, so:
 *  3. If you swipe away from the reel you opened, we snap back to it (or close
 *     the viewer if snapping back isn't possible).
 */
public class ReelsBlockerService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String INSTAGRAM = "com.instagram.android";
    private static final String ID_REELS_TAB = INSTAGRAM + ":id/clips_tab";
    private static final String ID_HOME_TAB = INSTAGRAM + ":id/feed_tab";
    /** The full-screen vertical pager Instagram uses to show reels outside the Reels tab. */
    private static final String ID_REEL_VIEWER = INSTAGRAM + ":id/clips_viewer_view_pager";
    private static final String REEL_VIEWER_ID_PART = "clips_viewer";

    /** Small delay to batch the flood of "content changed" events into one scan. */
    private static final long SCAN_DELAY_MS = 40;
    private static final long KICK_COOLDOWN_MS = 800;
    private static final long TOAST_COOLDOWN_MS = 5000;
    /** Scrolls right after the viewer opens are Instagram positioning it, not the user. */
    private static final long VIEWER_SETTLE_MS = 1200;
    private static final long SNAP_BACK_COOLDOWN_MS = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scanRunnable = this::scan;
    private final Rect coverBounds = new Rect();

    private WindowManager windowManager;
    private View cover;
    private WindowManager.LayoutParams coverParams;
    private boolean scanPending;
    private long lastKickAt;
    private long lastToastAt;

    private boolean viewerOpen;
    private long viewerOpenedAt;
    /** Position of the reel that was opened, or -1 if Instagram doesn't report positions. */
    private int viewerStartIndex = -1;
    private long lastSnapBackAt;

    @Override
    protected void onServiceConnected() {
        windowManager = getSystemService(WindowManager.class);
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this);
        scheduleScan();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!Prefs.isBlocking(this)) {
            hideCover();
            return;
        }
        int type = event.getEventType();
        CharSequence pkg = event.getPackageName();
        boolean fromInstagram = INSTAGRAM.contentEquals(pkg == null ? "" : pkg);
        if (fromInstagram && type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            onInstagramScrolled(event);
        }
        if (fromInstagram
                || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // Instagram changed, or some other window came/went (which may mean
            // Instagram is no longer on screen and the cover must come down).
            scheduleScan();
        }
    }

    private void scheduleScan() {
        if (!scanPending) {
            scanPending = true;
            handler.postDelayed(scanRunnable, SCAN_DELAY_MS);
        }
    }

    private void scan() {
        scanPending = false;
        if (!Prefs.isBlocking(this)) {
            hideCover();
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !INSTAGRAM.contentEquals(root.getPackageName())) {
            viewerOpen = false;
            hideCover();
            return;
        }

        updateViewerState(root);

        AccessibilityNodeInfo reelsTab = findReelsTab(root);
        if (reelsTab == null) {
            // No tab bar on screen (e.g. watching a reel someone sent you).
            hideCover();
            return;
        }

        if (reelsTab.isSelected()) {
            leaveReelsTab(root);
        }

        reelsTab.getBoundsInScreen(coverBounds);
        if (coverBounds.isEmpty()) {
            hideCover();
        } else {
            showCover(coverBounds);
        }
    }

    private AccessibilityNodeInfo findReelsTab(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> byId = root.findAccessibilityNodeInfosByViewId(ID_REELS_TAB);
        for (AccessibilityNodeInfo node : byId) {
            if (node.isVisibleToUser()) return node;
        }

        // Fallback in case Instagram renames the view id: a "Reels" button sitting
        // in the bottom tab bar area of the screen.
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        Rect r = new Rect();
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText("Reels")) {
            CharSequence desc = node.getContentDescription();
            if (desc == null || !"Reels".contentEquals(desc) || !node.isVisibleToUser()) continue;
            node.getBoundsInScreen(r);
            if (r.top < screenHeight * 0.75f || r.height() > screenHeight * 0.15f) continue;
            // Cover the whole tappable button, not just its icon.
            AccessibilityNodeInfo target = node;
            for (int i = 0; i < 3 && !target.isClickable(); i++) {
                AccessibilityNodeInfo parent = target.getParent();
                if (parent == null) break;
                target = parent;
            }
            return target.isClickable() ? target : node;
        }
        return null;
    }

    private void updateViewerState(AccessibilityNodeInfo root) {
        boolean open = false;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(ID_REEL_VIEWER)) {
            if (node.isVisibleToUser()) {
                open = true;
                break;
            }
        }
        if (open && !viewerOpen) startViewerSession();
        viewerOpen = open;
    }

    private void startViewerSession() {
        viewerOpen = true;
        viewerOpenedAt = SystemClock.uptimeMillis();
        viewerStartIndex = -1;
    }

    /** Keeps you on the one reel you opened instead of swiping into an endless feed. */
    private void onInstagramScrolled(AccessibilityEvent event) {
        AccessibilityNodeInfo pager = event.getSource();
        if (pager == null) return;
        String id = pager.getViewIdResourceName();
        if (id == null || !id.contains(REEL_VIEWER_ID_PART)) return;

        if (!viewerOpen) startViewerSession();
        long now = SystemClock.uptimeMillis();
        int index = event.getFromIndex();

        if (now - viewerOpenedAt < VIEWER_SETTLE_MS) {
            if (index >= 0) viewerStartIndex = index;
            return;
        }
        if (index >= 0 && viewerStartIndex < 0) {
            // Never saw where it started; a reel you open is the first one in the viewer.
            viewerStartIndex = 0;
        }
        if (index >= 0 && index == viewerStartIndex) return; // Still on (or back on) your reel.
        if (now - lastSnapBackAt < SNAP_BACK_COOLDOWN_MS) return;
        lastSnapBackAt = now;

        boolean snapped = false;
        if (index > viewerStartIndex) {
            snapped = pager.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        } else if (index >= 0) {
            snapped = pager.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        }
        if (!snapped) {
            // Can't scroll back to it, so close the viewer (back to the chat).
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
        showBlockedToast("Only the reel you opened");
    }

    private void showBlockedToast(String message) {
        long now = SystemClock.uptimeMillis();
        if (now - lastToastAt > TOAST_COOLDOWN_MS) {
            lastToastAt = now;
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void leaveReelsTab(AccessibilityNodeInfo root) {
        long now = SystemClock.uptimeMillis();
        if (now - lastKickAt < KICK_COOLDOWN_MS) return;
        lastKickAt = now;

        boolean switched = false;
        for (AccessibilityNodeInfo home : root.findAccessibilityNodeInfosByViewId(ID_HOME_TAB)) {
            if (home.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                switched = true;
                break;
            }
        }
        if (!switched) {
            performGlobalAction(GLOBAL_ACTION_BACK);
        }

        showBlockedToast("Reels are blocked");
    }

    private int backgroundColor() {
        // Instagram follows the system dark mode by default: pure black or pure white bar.
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES ? Color.BLACK : Color.WHITE;
    }

    private void showCover(Rect bounds) {
        if (windowManager == null) return;

        if (cover == null) {
            cover = new View(this);
            // Clickable so taps on the hidden button are swallowed instead of reaching Instagram.
            cover.setClickable(true);
            cover.setLongClickable(true);

            coverParams = new WindowManager.LayoutParams(
                    bounds.width(), bounds.height(),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.OPAQUE);
            coverParams.gravity = Gravity.TOP | Gravity.START;
            coverParams.x = bounds.left;
            coverParams.y = bounds.top;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                coverParams.setFitInsetsTypes(0);
                coverParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                coverParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            cover.setBackgroundColor(backgroundColor());
            try {
                windowManager.addView(cover, coverParams);
            } catch (RuntimeException e) {
                cover = null;
            }
            return;
        }

        cover.setBackgroundColor(backgroundColor());
        if (coverParams.x != bounds.left || coverParams.y != bounds.top
                || coverParams.width != bounds.width() || coverParams.height != bounds.height()) {
            coverParams.x = bounds.left;
            coverParams.y = bounds.top;
            coverParams.width = bounds.width();
            coverParams.height = bounds.height();
            try {
                windowManager.updateViewLayout(cover, coverParams);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void hideCover() {
        if (cover != null && windowManager != null) {
            try {
                windowManager.removeView(cover);
            } catch (RuntimeException ignored) {
            }
        }
        cover = null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (Prefs.KEY_BLOCKING.equals(key)) {
            if (Prefs.isBlocking(this)) scheduleScan();
            else hideCover();
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this);
        handler.removeCallbacks(scanRunnable);
        hideCover();
        super.onDestroy();
    }
}
