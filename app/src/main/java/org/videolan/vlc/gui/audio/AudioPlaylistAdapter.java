/*****************************************************************************
 * AudioSongsListAdapter.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.DataSetObserver;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.widget.AudioPlaylistItemViewGroup;

import java.util.ArrayList;
import java.util.List;

public class AudioPlaylistAdapter extends ArrayAdapter<MediaWrapper> {

    private int mCurrentIndex;
    private Context mContext;
    private int mAlignMode;

    public AudioPlaylistAdapter(Context context) {
        super(context, 0);
        mContext = context;
        mCurrentIndex = -1;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        mAlignMode = Integer.valueOf(preferences.getString("audio_title_alignment", "0"));
    }

    public void setCurrentIndex(int currentIndex) {
        mCurrentIndex = currentIndex;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View v = convertView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.audio_playlist_item, parent, false);
            holder = new ViewHolder();
            holder.title = (TextView) v.findViewById(R.id.title);
            Util.setAlignModeByPref(mAlignMode, holder.title);
            holder.artist = (TextView) v.findViewById(R.id.artist);
            holder.moveButton = (ImageButton) v.findViewById(R.id.move);
            holder.expansion = (LinearLayout)v.findViewById(R.id.item_expansion);
            holder.layoutItem = (LinearLayout)v.findViewById(R.id.layout_item);
            holder.layoutFooter = v.findViewById(R.id.layout_footer);
            holder.itemGroup = (AudioPlaylistItemViewGroup)v.findViewById(R.id.playlist_item);
            v.setTag(holder);
        } else
            holder = (ViewHolder) v.getTag();

        holder.expansion.setVisibility(LinearLayout.GONE);
        holder.layoutItem.setVisibility(LinearLayout.VISIBLE);
        holder.layoutFooter.setVisibility(LinearLayout.VISIBLE);
        holder.itemGroup.scrollTo(1);

        MediaWrapper media = getItem(position);
        final String title = media.getTitle();
        final String artist = Util.getMediaSubtitle(mContext, media);
        final int pos = position;
        final View itemView = v;

        holder.title.setText(title);
        ColorStateList titleColor = v.getResources().getColorStateList(mCurrentIndex == position
                ? Util.getResourceFromAttribute(mContext, R.attr.list_title_last)
                : Util.getResourceFromAttribute(mContext, R.attr.list_title));
        holder.title.setTextColor(titleColor);
        holder.artist.setText(artist);
        holder.position = position;

        final AudioPlaylistView playlistView = (AudioPlaylistView)parent;

        holder.moveButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    playlistView.startDrag(pos, title, artist);
                    return true;
                }
                else
                    return false;
            }
        });
        holder.itemGroup.setOnItemSlidedListener(
                new AudioPlaylistItemViewGroup.OnItemSlidedListener() {
            @Override
            public void onItemSlided() {
                playlistView.removeItem(pos);
            }
        });
        holder.layoutItem.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                playlistView.performItemClick(itemView, pos, 0);
            }
        });
        holder.layoutItem.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                playlistView.performItemLongClick(itemView, pos, 0);
                return true;
            }
        });

        return v;
    }

    public String getLocation(int position) {
        String location = null;
        if (position >= 0 && position < getCount())
            location = getItem(position).getLocation();
        return location;
    }

    public List<String> getLocations() {
        List<String> locations = new ArrayList<String>();
        for (int i = 0 ; i < getCount() ; ++i)
            locations.add(getItem(i).getLocation());
        return locations;
    }

    static class ViewHolder {
        int position;
        TextView title;
        TextView artist;
        ImageButton moveButton;
        LinearLayout expansion;
        LinearLayout layoutItem;
        View layoutFooter;
        AudioPlaylistItemViewGroup itemGroup;
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        if (observer != null)
            super.unregisterDataSetObserver(observer);
    }
}
