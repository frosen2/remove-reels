package com.removereels;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile for flipping Reels blocking on and off. */
public class ToggleTileService extends TileService {

    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        Prefs.setBlocking(this, !Prefs.isBlocking(this));
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean on = Prefs.isBlocking(this);
        tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
        tile.setLabel(getString(R.string.tile_label));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            tile.setSubtitle(on ? "On" : "Off");
        }
        tile.updateTile();
    }
}
