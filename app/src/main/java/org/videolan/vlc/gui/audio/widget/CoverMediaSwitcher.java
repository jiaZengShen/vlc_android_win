/*****************************************************************************
 * CoverMediaSwitcher.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.audio.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;

import org.videolan.vlc.R;
import org.videolan.vlc.widget.AudioMediaSwitcher;

public class CoverMediaSwitcher extends AudioMediaSwitcher {

    public CoverMediaSwitcher(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void addMediaView(LayoutInflater inflater, String title, String artist, Bitmap cover) {

        if (cover == null)
            cover = BitmapFactory.decodeResource(getResources(), R.drawable.icon);

        ImageView imageView = new ImageView(getContext());
        imageView.setImageBitmap(cover);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(imageView);
    }
}
