/*****************************************************************************
 * MediaInfoActivity.java
 *****************************************************************************
 * Copyright © 2011-2015 VLC authors and VideoLAN
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

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.Extensions;
import org.videolan.libvlc.util.VLCUtil;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.util.BitmapUtil;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaInfoFragment extends ListFragment {

    public final static String TAG = "VLC/MediaInfoFragment";

    public final static String ITEM_KEY = "key_item";

    private MediaWrapper mItem;
    private Bitmap mImage;
    private TextView mLengthView;
    private TextView mSizeView;
    private TextView mPathView;
    private FloatingActionButton mPlayButton;
    private ImageButton mDelete;
    private ImageView mSubtitles;
    private Media mMedia;
    private MediaInfoAdapter mAdapter;
    private final static int NEW_IMAGE = 0;
    private final static int NEW_TEXT = 1;
    private final static int NEW_SIZE = 2;
    private final static int HIDE_DELETE = 3;
    private final static int EXIT = 4;
    private final static int SHOW_SUBTITLES = 5;
    ExecutorService mThreadPoolExecutor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null)
            mItem = savedInstanceState.getParcelable(ITEM_KEY);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View v = inflater.inflate(R.layout.media_info, container, false);

        mLengthView = (TextView) v.findViewById(R.id.length);
        mSizeView = (TextView) v.findViewById(R.id.size_value);
        mPathView = (TextView) v.findViewById(R.id.info_path);
        mPlayButton = (FloatingActionButton) v.findViewById(R.id.play);
        mDelete = (ImageButton) v.findViewById(R.id.info_delete);
        mSubtitles = (ImageView) v.findViewById(R.id.info_subtitles);

        mThreadPoolExecutor = Executors.newFixedThreadPool(2);
        mThreadPoolExecutor.submit(mCheckFile);
        mThreadPoolExecutor.submit(mLoadImage);

        mPathView.setText(mItem == null ? "" : Uri.decode(mItem.getLocation().substring(7)));
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                VideoPlayerActivity.start(getActivity(), mItem.getUri());
            }
        });

        mDelete.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mItem != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            boolean deleted = Util.deleteFile(mItem.getLocation());
                            if (deleted) {
                                mHandler.obtainMessage(EXIT).sendToTarget();
                            }
                        }
                    }).start();
                }
            }
        });
        mAdapter = new MediaInfoAdapter(getActivity());
        setListAdapter(mAdapter);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mItem == null) {
            // Shouldn't happen, maybe user opened it faster than Media Library could index it
            return;
        }

        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(mItem.getTitle());
        mLengthView.setText(Strings.millisToString(mItem.getLength()));
    }

    public void onStop(){
        super.onStop();
        if (mThreadPoolExecutor != null)
            mThreadPoolExecutor.shutdownNow();
        if (mMedia != null)
            mMedia.release();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ITEM_KEY, mItem);
    }

    public void setMediaLocation(String MRL) {
        if (MRL == null)
            return;
        mItem = MediaLibrary.getInstance().getMediaItem(MRL);
    }

    Runnable mCheckFile = new Runnable() {
        @Override
        public void run() {
            File itemFile = new File(Uri.decode(mItem.getLocation().substring(5)));
            if (!itemFile.canWrite())
                mHandler.obtainMessage(HIDE_DELETE).sendToTarget();
            long length = itemFile.length();
            mHandler.obtainMessage(NEW_SIZE, Long.valueOf(length)).sendToTarget();
            checkSubtitles(itemFile);
        }
    };

    private void checkSubtitles(File itemFile) {
        String extension, filename, videoName = Uri.decode(itemFile.getName()), parentPath = Uri.decode(itemFile.getParent());
        videoName = videoName.substring(0, videoName.lastIndexOf('.'));
        String[] subFolders = {"/Subtitles", "/subtitles", "/Subs", "/subs"};
        String[] files = itemFile.getParentFile().list();
        int filesLength = files == null ? 0 : files.length;
        for (int i = 0 ; i < subFolders.length ; ++i){
            File subFolder = new File(parentPath+subFolders[i]);
            if (!subFolder.exists())
                continue;
            String[] subFiles = subFolder.list();
            int subFilesLength = 0;
            String[] newFiles = new String[0];
            if (subFiles != null) {
                subFilesLength = subFiles.length;
                newFiles = new String[filesLength+subFilesLength];
                System.arraycopy(subFiles, 0, newFiles, 0, subFilesLength);
            }
            if (files != null)
                System.arraycopy(files, 0, newFiles, subFilesLength, filesLength);
            files = newFiles;
            filesLength = files.length;
        }
        for (int i = 0; i<filesLength ; ++i){
            filename = Uri.decode(files[i]);
            int index = filename.lastIndexOf('.');
            if (index <= 0)
                continue;
            extension = filename.substring(index);
            if (!Extensions.SUBTITLES.contains(extension))
                continue;
            if (filename.startsWith(videoName)) {
                mHandler.obtainMessage(SHOW_SUBTITLES).sendToTarget();
                return;
            }
        }
    }

    Runnable mLoadImage = new Runnable() {
        @Override
        public void run() {
            final LibVLC libVlc = VLCInstance.get();
            if (libVlc == null)
                return;
            mMedia = new Media(libVlc, mItem.getUri());
            mMedia.parse();
            int videoHeight = mItem.getHeight();
            int videoWidth = mItem.getWidth();
            if (videoWidth == 0 || videoHeight == 0) {
                return;
            }

            mHandler.sendEmptyMessage(NEW_TEXT);

            DisplayMetrics screen = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(screen);
            int width, height;
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                width = Math.min(screen.widthPixels, screen.heightPixels);
            } else {
                width = screen.widthPixels /2 ;
            }
            height = width * videoHeight/videoWidth;

            // Get the thumbnail.
            mImage = Bitmap.createBitmap(width, height, Config.ARGB_8888);

            byte[] b = VLCUtil.getThumbnail(mMedia, width, height);

            if (b == null) // We were not able to create a thumbnail for this item.
                return;

            if (Thread.interrupted()) {
                return;
            }
            mImage.copyPixelsFromBuffer(ByteBuffer.wrap(b));
            mImage = BitmapUtil.cropBorders(mImage, width, height);

            mHandler.sendEmptyMessage(NEW_IMAGE);
        }
    };

    private void updateImage() {
        if (getView() == null)
            return;
        ImageView imageView = (ImageView) getView().findViewById(R.id.image);
        imageView.setImageBitmap(mImage);
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        lp.height = mImage.getHeight();
        lp.width = mImage.getWidth();
        imageView.setLayoutParams(lp);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mPlayButton.setVisibility(View.VISIBLE);
        mLengthView.setVisibility(View.VISIBLE);
    }

    private void updateText() {
        boolean hasSubs = false;
        if (mMedia == null)
            return;
        final int trackCount = mMedia.getTrackCount();
        for (int i = 0; i < trackCount; ++i) {
            final Media.Track track = mMedia.getTrack(i);
            if (track.type == Media.Track.Type.Text)
                hasSubs = true;
            mAdapter.add(track);
        }
        if (mAdapter.isEmpty()) {
            getActivity().finish();
            return;
        }
        if (hasSubs)
            mHandler.obtainMessage(SHOW_SUBTITLES).sendToTarget();
    }

    private void updateSize(Long size){
        mSizeView.setText(Strings.readableFileSize(size.longValue()));
    }
    private Handler mHandler = new MediaInfoHandler(this);

    private static class MediaInfoHandler extends WeakHandler<MediaInfoFragment> {
        public MediaInfoHandler(MediaInfoFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            MediaInfoFragment fragment = getOwner();
            if(fragment == null) return;

            switch (msg.what) {
                case NEW_IMAGE:
                    fragment.updateImage();
                    break;
                case NEW_TEXT:
                    fragment.updateText();
                    break;
                case NEW_SIZE:
                    fragment.updateSize((Long) msg.obj);
                    break;
                case HIDE_DELETE:
                    fragment.mDelete.setClickable(false);
                    fragment.mDelete.setVisibility(View.GONE);
                    break;
                case EXIT:
                    fragment.getActivity().finish();
                    MediaLibrary.getInstance().loadMediaItems(true);
                    break;
                case SHOW_SUBTITLES:
                    fragment.mSubtitles.setVisibility(View.VISIBLE);
                    break;
            }
        };

    };

}
