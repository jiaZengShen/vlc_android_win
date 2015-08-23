/*****************************************************************************
 * VideoListAdapter.java
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

package org.videolan.vlc.gui.video;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.videolan.vlc.MediaGroup;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.util.BitmapCache;
import org.videolan.vlc.util.BitmapUtil;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

public class VideoListAdapter extends ArrayAdapter<MediaWrapper>
                                 implements Comparator<MediaWrapper> {

    public final static int SORT_BY_TITLE = 0;
    public final static int SORT_BY_LENGTH = 1;
    public final static int SORT_BY_DATE = 2;
    private int mSortDirection = 1;
    private int mSortBy = SORT_BY_TITLE;
    private boolean mListMode = false;
    private VideoGridFragment mFragment;

    public VideoListAdapter(VideoGridFragment fragment) {
        super(fragment.getActivity(), 0);
        mFragment = fragment;
    }

    public final static String TAG = "VLC/MediaLibraryAdapter";

    public synchronized void update(MediaWrapper item) {
        int position = getPosition(item);
        if (position != -1) {
            remove(item);
            insert(item, position);
        }
    }

    public void setTimes(HashMap<String, Long> times) {
        boolean notify = false;
        // update times
        for (int i = 0; i < getCount(); ++i) {
            MediaWrapper media = getItem(i);
            Long time = times.get(media.getLocation());
            if (time != null) {
                media.setTime(time);
                notify = true;
            }
        }
        if (notify)
            notifyDataSetChanged();
    }

    public int sortDirection(int sortby) {
        if (sortby == mSortBy)
            return  mSortDirection;
        else
            return -1;
    }

    public void sortBy(int sortby) {
        switch (sortby) {
            case SORT_BY_TITLE:
                if (mSortBy == SORT_BY_TITLE)
                    mSortDirection *= -1;
                else {
                    mSortBy = SORT_BY_TITLE;
                    mSortDirection = 1;
                }
                break;
            case SORT_BY_LENGTH:
                if (mSortBy == SORT_BY_LENGTH)
                    mSortDirection *= -1;
                else {
                    mSortBy = SORT_BY_LENGTH;
                    mSortDirection *= 1;
                }
                break;
            case SORT_BY_DATE:
                if (mSortBy == SORT_BY_DATE)
                    mSortDirection *= -1;
                else {
                    mSortBy = SORT_BY_DATE;
                    mSortDirection *= 1;
                }
                break;
            default:
                mSortBy = SORT_BY_TITLE;
                mSortDirection = 1;
                break;
        }
        sort();
    }

    public void sort() {
        if (!isEmpty())
            try {
                super.sort(this);
            } catch (ArrayIndexOutOfBoundsException e) {} //Exception happening on Android 2.x
    }

    @Override
    public int compare(MediaWrapper item1, MediaWrapper item2) {
        int compare = 0;
        switch (mSortBy) {
            case SORT_BY_TITLE:
                compare = item1.getTitle().toUpperCase(Locale.ENGLISH).compareTo(
                        item2.getTitle().toUpperCase(Locale.ENGLISH));
                break;
            case SORT_BY_LENGTH:
                compare = ((Long) item1.getLength()).compareTo(item2.getLength());
                break;
            case SORT_BY_DATE:
                compare = ((Long) item1.getLastModified()).compareTo(item2.getLastModified());
                break;
        }
        return mSortDirection * compare;
    }

    /**
     * Display the view of a file browser item.
     */
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View v = convertView;

        if (v == null || (((ViewHolder)v.getTag()).listmode != mListMode)) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            if (!mListMode)
                v = inflater.inflate(R.layout.video_grid_card, parent, false);
            else
                v = inflater.inflate(R.layout.video_list_card, parent, false);

            holder = new ViewHolder();
            holder.thumbnail = (ImageView) v.findViewById(R.id.ml_item_thumbnail);
            holder.title = (TextView) v.findViewById(R.id.ml_item_title);
            holder.time = (TextView) v.findViewById(R.id.ml_item_time);
            holder.resolution = (TextView) v.findViewById(R.id.ml_item_resolution);
            holder.progress = (ProgressBar) v.findViewById(R.id.ml_item_progress);
            holder.more = (ImageView) v.findViewById(R.id.item_more);
            holder.listmode = mListMode;
            v.setTag(holder);


            /* Set the layoutParams based on the values set in the video_grid_item.xml root element */
            v.setLayoutParams(new GridView.LayoutParams(v.getLayoutParams().width, v.getLayoutParams().height));
        } else {
            holder = (ViewHolder) v.getTag();
        }

        if (position >= getCount() || position < 0)
            return v;

        holder.more.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFragment != null)
                    mFragment.onContextPopupMenu(v, position);
            }
        });

        MediaWrapper media = getItem(position);

        /* Thumbnail */
        Bitmap thumbnail = BitmapUtil.getPictureFromCache(media);
        holder.thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (thumbnail == null) {
            // missing thumbnail
            holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER);
            thumbnail = BitmapCache.getFromResource(v, R.drawable.ic_cone_o);
        }
        else if (thumbnail.getWidth() == 1 && thumbnail.getHeight() == 1) {
            // dummy thumbnail
            holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER);
            thumbnail = BitmapCache.getFromResource(v, R.drawable.ic_cone_o);
        }
        //FIXME Warning: the thumbnails are upscaled in the grid view!
        holder.thumbnail.setImageBitmap(thumbnail);

        /* Color state */
        ColorStateList titleColor = v.getResources().getColorStateList(
                Util.getResourceFromAttribute(getContext(), R.attr.list_title));
        holder.title.setTextColor(titleColor);

        if (media instanceof MediaGroup)
            fillGroupView(holder, media);
        else
            fillVideoView(holder, media);

        return v;
    }

    private void fillGroupView(ViewHolder holder, MediaWrapper media) {
        MediaGroup mediaGroup = (MediaGroup) media;
        int size = mediaGroup.size();
        String text = getContext().getResources().getQuantityString(R.plurals.videos_quantity, size, size);

        holder.time.setText("");
        holder.resolution.setText(text);
        holder.title.setText(media.getTitle() + "\u2026"); // ellipsis
        holder.more.setVisibility(View.GONE);
        holder.progress.setVisibility(View.INVISIBLE);
    }

    private void fillVideoView(ViewHolder holder, MediaWrapper media) {
        /* Time / Duration */
        if (media.getLength() > 0) {
            long lastTime = media.getTime();
            String text;
            if (lastTime > 0) {
                text = String.format("%s / %s",
                        Strings.millisToText(lastTime),
                        Strings.millisToText(media.getLength()));
                holder.progress.setVisibility(View.VISIBLE);
                holder.progress.setMax((int) (media.getLength() / 1000));
                holder.progress.setProgress((int) (lastTime / 1000));
            } else {
                text = Strings.millisToText(media.getLength());
                holder.progress.setVisibility(View.INVISIBLE);
            }

            holder.time.setText(text);
        } else
                holder.progress.setVisibility(View.INVISIBLE);
        if (media.getWidth() > 0 && media.getHeight() > 0)
            holder.resolution.setText(String.format("%dx%d", media.getWidth(), media.getHeight()));
        else
            holder.resolution.setText("");
        holder.title.setText(media.getTitle());
        holder.more.setVisibility(View.VISIBLE);
    }

    static class ViewHolder {
        boolean listmode;
        ImageView thumbnail;
        TextView title;
        TextView time;
        TextView resolution;
        ImageView more;
        ProgressBar progress;
    }

    public void setListMode(boolean value) {
        mListMode = value;
    }

    public boolean isListMode() {
        return mListMode;
    }
}
