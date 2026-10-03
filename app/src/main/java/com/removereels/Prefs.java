package com.removereels;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.TileService;

/** The single "is blocking on?" setting, shared by the app screen, the tile and the service. */
final class Prefs {
    static final String KEY_BLOCKING = "blocking";

    private Prefs() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean isBlocking(Context context) {
        return get(context).getBoolean(KEY_BLOCKING, true);
    }

    static void setBlocking(Context context, boolean blocking) {
        get(context).edit().putBoolean(KEY_BLOCKING, blocking).apply();
        // Ask the Quick Settings tile to refresh itself if it's visible.
        TileService.requestListeningState(context,
                new ComponentName(context, ToggleTileService.class));
    }
}
