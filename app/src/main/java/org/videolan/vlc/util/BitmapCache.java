/*****************************************************************************
 * BitmapCache.java
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.util;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.view.View;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.VLCApplication;

public class BitmapCache {

    public final static String TAG = "VLC/BitmapCache";
    private final static boolean LOG_ENABLED = false;

    private static BitmapCache mInstance;
    private final LruCache<String, Bitmap> mMemCache;

    public static BitmapCache getInstance() {
        if (mInstance == null)
            mInstance = new BitmapCache();
        return mInstance;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private BitmapCache() {

        // Get memory class of this device, exceeding this amount will throw an
        // OutOfMemory exception.
        final ActivityManager am = ((ActivityManager) VLCApplication.getAppContext().getSystemService(
                Context.ACTIVITY_SERVICE));
        final int memClass = AndroidUtil.isHoneycombOrLater() ? am.getLargeMemoryClass() : am.getMemoryClass();

        // Use 1/5th of the available memory for this memory cache.
        final int cacheSize = 1024 * 1024 * memClass / 5;

        Log.d(TAG, "LRUCache size sets to " + cacheSize);

        mMemCache = new LruCache<String, Bitmap>(cacheSize) {

            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }

        };
    }

    public Bitmap getBitmapFromMemCache(String key) {
        final Bitmap b = mMemCache.get(key);
        if (LOG_ENABLED)
            Log.d(TAG, (b == null) ? "Cache miss" : "Cache found");
        if (b != null && b.isRecycled()) {
            /* A recycled bitmap cannot be used again */
            mMemCache.remove(key);
            return null;
        }
        return b;
    }

    public void addBitmapToMemCache(String key, Bitmap bitmap) {
        if (key != null && bitmap != null && getBitmapFromMemCache(key) == null)
            mMemCache.put(key, bitmap);
    }

    private Bitmap getBitmapFromMemCache(int resId) {
        return getBitmapFromMemCache("res:" + resId);
    }

    private void addBitmapToMemCache(int resId, Bitmap bitmap) {
        addBitmapToMemCache("res:" + resId, bitmap);
    }

    public void clear() {
        mMemCache.evictAll();
    }

    public static Bitmap getFromResource(View v, int resId) {
        BitmapCache cache = BitmapCache.getInstance();
        Bitmap bitmap = cache.getBitmapFromMemCache(resId);
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeResource(v.getResources(), resId);
            cache.addBitmapToMemCache(resId, bitmap);
        }
        return bitmap;
    }
}
