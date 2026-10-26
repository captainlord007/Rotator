package com.example.rotateall;

import android.content.Context;

final class BlockInfo {
    private BlockInfo() {
    }

    static void show(Context context) {
        new android.app.AlertDialog.Builder(context)
                .setTitle(R.string.block_heading)
                .setMessage(R.string.block_help)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
