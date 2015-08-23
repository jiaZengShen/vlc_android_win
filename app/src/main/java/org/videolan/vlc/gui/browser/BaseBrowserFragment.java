/**
 * **************************************************************************
 * BaseBrowserFragment.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
 * Author: Geoffrey Métais
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.browser;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.DividerItemDecoration;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.dialogs.CommonDialogs;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.VLCRunnable;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.widget.ContextMenuRecyclerView;
import org.videolan.vlc.widget.SwipeRefreshLayout;

import java.util.ArrayList;


public abstract class BaseBrowserFragment extends MediaBrowserFragment implements IRefreshable, MediaBrowser.EventListener, SwipeRefreshLayout.OnRefreshListener {
    protected static final String TAG = "VLC/BaseBrowserFragment";

    public static String ROOT = "smb";
    public static final String KEY_MRL = "key_mrl";
    public static final String KEY_MEDIA = "key_media";
    public static final String KEY_MEDIA_LIST = "key_media_list";
    public static final String KEY_POSITION = "key_list";

    protected BrowserFragmentHandler mHandler;
    protected MediaBrowser mMediaBrowser;
    protected ContextMenuRecyclerView mRecyclerView;
    protected BaseBrowserAdapter mAdapter;
    protected LinearLayoutManager mLayoutManager;
    protected TextView mEmptyView;
    public String mMrl;
    protected MediaWrapper mCurrentMedia;
    protected int mSavedPosition = -1, mFavorites = 0;
    public boolean mRoot;

    private SparseArray<ArrayList<MediaWrapper>> mMediaLists = new SparseArray<ArrayList<MediaWrapper>>();
    private ArrayList<MediaWrapper> mediaList;
    public int mCurrentParsedPosition = 0;

    protected abstract Fragment createFragment();
    protected abstract void browseRoot();
    protected abstract String getCategoryTitle();

    public BaseBrowserFragment(){
        mHandler = new BrowserFragmentHandler(this);
        mAdapter = new BaseBrowserAdapter(this);
    }

    public void onCreate(Bundle bundle){
        super.onCreate(bundle);

        if (bundle == null)
            bundle = getArguments();
        if (bundle != null){
            mediaList = bundle.getParcelableArrayList(KEY_MEDIA_LIST);
            if (mediaList != null)
                mAdapter.addAll(mediaList);
            mCurrentMedia = bundle.getParcelable(KEY_MEDIA);
            if (mCurrentMedia != null)
                mMrl = mCurrentMedia.getLocation();
            else
                mMrl = bundle.getString(KEY_MRL);
            mSavedPosition = bundle.getInt(KEY_POSITION);
        } else if (getActivity().getIntent() != null){
            mMrl = getActivity().getIntent().getDataString();
            getActivity().setIntent(null);
        }
    }

    protected int getLayoutId(){
        return R.layout.directory_browser;
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View v = inflater.inflate(getLayoutId(), container, false);
        mRecyclerView = (ContextMenuRecyclerView) v.findViewById(R.id.network_list);
        mEmptyView = (TextView) v.findViewById(android.R.id.empty);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.addItemDecoration(new DividerItemDecoration(VLCApplication.getAppContext(), DividerItemDecoration.VERTICAL_LIST));
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addOnScrollListener(mScrollListener);
        registerForContextMenu(mRecyclerView);

        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.orange700);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        mMediaBrowser = new MediaBrowser(VLCInstance.get(), this);
    }

    public void onStop(){
        super.onStop();
        releaseBrowser();
    }

    private void releaseBrowser() {
        if (mMediaBrowser != null) {
            mMediaBrowser.release();
            mMediaBrowser = null;
        }
    }

    public void onSaveInstanceState(Bundle outState){
        outState.putString(KEY_MRL, mMrl);
        outState.putParcelable(KEY_MEDIA, mCurrentMedia);
        outState.putParcelableArrayList(KEY_MEDIA_LIST, mediaList);
        if (mRecyclerView != null) {
            outState.putInt(KEY_POSITION, mLayoutManager.findFirstCompletelyVisibleItemPosition());
        }
        super.onSaveInstanceState(outState);
    }

    public boolean isRootDirectory(){
        return mRoot;
    }

    public String getTitle(){
        if (mRoot)
            return getCategoryTitle();
        else
            return mCurrentMedia != null ? mCurrentMedia.getTitle() : mMrl;
    }

    @Override
    protected void display() {
        if (!mReadyToDisplay) {
            mReadyToDisplay = true;
            update();
            return;
        }
        updateDisplay();
    }

    public void goBack(){
        if (!mRoot)
            getActivity().getSupportFragmentManager().popBackStack();
        else
            getActivity().finish();
    }

    public void browse (MediaWrapper media, int position, boolean save){
        FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
        Fragment next = createFragment();
        Bundle args = new Bundle();
        ArrayList<MediaWrapper> list = mMediaLists != null ? mMediaLists.get(position) : null;
        if(list != null && !list.isEmpty())
            args.putParcelableArrayList(KEY_MEDIA_LIST, list);
        args.putParcelable(KEY_MEDIA, media);
        next.setArguments(args);
        ft.replace(R.id.fragment_placeholder, next, media.getLocation());
        if (save)
            ft.addToBackStack(mMrl);
        ft.commit();
    }

    @Override
    public void onMediaAdded(int index, Media media) {
        mAdapter.addItem(media, mReadyToDisplay && mRoot, mRoot);
        if (mReadyToDisplay)
            updateEmptyView();
        if (mRoot)
            mHandler.sendEmptyMessage(BrowserFragmentHandler.MSG_HIDE_LOADING);
    }

    @Override
    public void onMediaRemoved(int index, Media media) {
        mAdapter.removeItem(index, mReadyToDisplay);
    }

    @Override
    public void onBrowseEnd() {
        releaseBrowser();
        mHandler.sendEmptyMessage(BrowserFragmentHandler.MSG_HIDE_LOADING);
        if (mReadyToDisplay)
            display();
    }

    @Override
    public void onRefresh() {
        mSavedPosition = mLayoutManager.findFirstCompletelyVisibleItemPosition();
        refresh();
    }

    RecyclerView.OnScrollListener mScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            int topRowVerticalPosition =
                    (recyclerView == null || recyclerView.getChildCount() == 0) ? 0 : recyclerView.getChildAt(0).getTop();
            mSwipeRefreshLayout.setEnabled(topRowVerticalPosition >= 0);
        }
    };

    /**
     * Update views visibility and emptiness info
     */
    protected void updateEmptyView(){
        if (mAdapter.isEmpty()){
            mEmptyView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
            mSwipeRefreshLayout.setEnabled(false);
        } else if (mEmptyView.getVisibility() == View.VISIBLE) {
            mEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
            mSwipeRefreshLayout.setEnabled(true);
        }
    }

    protected void update(){
        update(false);
    }

    protected void update(boolean force){
        if (mReadyToDisplay) {
            updateEmptyView();
            if (force || mAdapter.isEmpty()) {
                refresh();
            } else {
                updateDisplay();
            }
        }
    }

    protected void updateDisplay() {
        if (!mAdapter.isEmpty()) {
            if (mSavedPosition > 0) {
                mLayoutManager.scrollToPositionWithOffset(mSavedPosition, 0);
                mSavedPosition = 0;
            }
        }
        mAdapter.notifyDataSetChanged();
        parseSubDirectories();
        focusHelper();
    }

    @Override
    public void refresh() {
        mAdapter.clear();
        if (mMediaBrowser == null)
            mMediaBrowser = new MediaBrowser(VLCInstance.get(), this);
        else
            mMediaBrowser.changeEventListener(this);
        mCurrentParsedPosition = 0;
        if (mRoot)
            browseRoot();
        else
            mMediaBrowser.browse(mCurrentMedia != null ? mCurrentMedia.getUri() : Uri.parse(mMrl));
        mHandler.sendEmptyMessageDelayed(BrowserFragmentHandler.MSG_SHOW_LOADING, 300);
    }


    protected static class BrowserFragmentHandler extends WeakHandler<BaseBrowserFragment> {

        public static final int MSG_SHOW_LOADING = 0;
        public static final int MSG_HIDE_LOADING = 1;

        public BrowserFragmentHandler(BaseBrowserFragment owner) {
            super(owner);
        }
        @Override
        public void handleMessage(Message msg) {
            BaseBrowserFragment fragment = getOwner();
            switch (msg.what){
                case MSG_SHOW_LOADING:
                    fragment.mSwipeRefreshLayout.setRefreshing(true);
                    break;
                case MSG_HIDE_LOADING:
                    removeMessages(MSG_SHOW_LOADING);
                    fragment.mSwipeRefreshLayout.setRefreshing(false);
                    break;
            }
        }
    }

    protected void focusHelper() {
        if (getActivity() == null || !(getActivity() instanceof MainActivity))
            return;
        boolean isEmpty = mAdapter.isEmpty();
        MainActivity main = (MainActivity) getActivity();
        main.setMenuFocusDown(isEmpty, R.id.network_list);
        main.setSearchAsFocusDown(isEmpty, getView(), R.id.network_list);
    }

    public void clear(){
        mAdapter.clear();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        ContextMenuRecyclerView.RecyclerContextMenuInfo info = (ContextMenuRecyclerView
                .RecyclerContextMenuInfo) menuInfo;
        if (info != null)
            setContextMenu(getActivity().getMenuInflater(), menu, info.position);
    }

    protected void setContextMenu(MenuInflater inflater, Menu menu, int position) {
        MediaWrapper mw = (MediaWrapper) mAdapter.getItem(position);
        boolean canWrite = Util.canWrite(mw.getLocation());
        boolean isAudio = mw.getType() == MediaWrapper.TYPE_AUDIO;
        boolean isVideo = mw.getType() == MediaWrapper.TYPE_VIDEO;
        if (isAudio || isVideo) {
            inflater.inflate(R.menu.directory_view_file, menu);
            menu.findItem(R.id.directory_view_delete).setVisible(canWrite);
            menu.findItem(R.id.directory_view_info).setVisible(isVideo);
        } else if (mw.getType() == MediaWrapper.TYPE_DIR) {
            boolean isEmpty = mMediaLists.get(position) == null || mMediaLists.get(position).isEmpty();
            if (canWrite || !isEmpty) {
                inflater.inflate(R.menu.directory_view_dir, menu);
//                if (canWrite) {
//                    boolean nomedia = new File(mw.getLocation() + "/.nomedia").exists();
//                    menu.findItem(R.id.directory_view_hide_media).setVisible(!nomedia);
//                    menu.findItem(R.id.directory_view_show_media).setVisible(nomedia);
//                } else {
//                    menu.findItem(R.id.directory_view_hide_media).setVisible(false);
//                    menu.findItem(R.id.directory_view_show_media).setVisible(false);
//                }
                menu.findItem(R.id.directory_view_play_folder).setVisible(!isEmpty);
                menu.findItem(R.id.directory_view_delete).setVisible(canWrite);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ContextMenuRecyclerView.RecyclerContextMenuInfo info = (ContextMenuRecyclerView
                .RecyclerContextMenuInfo) item.getMenuInfo();
        if (info != null && handleContextItemSelected(item, info.position))
            return true;
        return super.onContextItemSelected(item);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onPopupMenu(View anchor, final int position) {
        if (!AndroidUtil.isHoneycombOrLater()) {
            // Call the "classic" context menu
            anchor.performLongClick();
            return;
        }
        PopupMenu popupMenu = new PopupMenu(getActivity(), anchor);
        setContextMenu(popupMenu.getMenuInflater(), popupMenu.getMenu(), position);

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return handleContextItemSelected(item, position);
            }
        });
        popupMenu.show();
    }

    protected boolean handleContextItemSelected(MenuItem item, int position) {
        int id = item.getItemId();
        if (! (mAdapter.getItem(position) instanceof MediaWrapper))
            return super.onContextItemSelected(item);
        MediaWrapper mw = (MediaWrapper) mAdapter.getItem(position);
        switch (id){
            case R.id.directory_view_play:
                mw.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                Util.openMedia(getActivity(), mw);
                return true;
            case R.id.directory_view_append: {
                if (mService != null)
                    mService.append(mw);
                return true;
            }
            case R.id.directory_view_delete:
                AlertDialog alertDialog = CommonDialogs.deleteMedia(
                        mw.getType(), getActivity(), mw.getLocation(),
                        new VLCRunnable() {
                            @Override
                            public void run(Object o) {
                                refresh();
                            }
                        });
                alertDialog.show();
                return true;
            case  R.id.directory_view_info:
                Intent i = new Intent(getActivity(), SecondaryActivity.class);
                i.putExtra("fragment", "mediaInfo");
                i.putExtra("param", mw.getUri().toString());
                startActivity(i);
                return true;
            case R.id.directory_view_play_audio: {
                if (mService != null) {
                    mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                    mService.load(mw);
                }
                return true;
            }
            case  R.id.directory_view_play_video:
                VideoPlayerActivity.start(getActivity(), mw.getUri());
                return true;
            case R.id.directory_view_play_folder:
                ArrayList<MediaWrapper> mediaList = new ArrayList<MediaWrapper>();
                for (MediaWrapper mediaItem : mMediaLists.get(position)){
                    if (mediaItem.getType() == MediaWrapper.TYPE_AUDIO || mediaItem.getType() == MediaWrapper.TYPE_VIDEO)
                        mediaList.add(mediaItem);
                }
                Util.openList(getActivity(), mediaList, 0);
                return true;
//            case R.id.directory_view_hide_media:
//                try {
//                    if (new File(mw.getLocation()+"/.nomedia").createNewFile())
//                        updateLib();
//                } catch (IOException e) {}
//                return true;
//            case R.id.directory_view_show_media:
//                if (new File(mw.getLocation()+"/.nomedia").delete())
//                    updateLib();
//                return true;
        }
        return false;
    }

    protected void parseSubDirectories() {
        if (mCurrentParsedPosition == -1 || mAdapter.isEmpty())
            return;
        mMediaLists.clear();
        if (mMediaBrowser == null)
            mMediaBrowser = new MediaBrowser(VLCInstance.get(), mFoldersBrowserListener);
        else
            mMediaBrowser.changeEventListener(mFoldersBrowserListener);
        mCurrentParsedPosition = 0;
        Object item;
        MediaWrapper mw;
        while (mCurrentParsedPosition <mAdapter.getItemCount()){
            item = mAdapter.getItem(mCurrentParsedPosition);
            if (item instanceof BaseBrowserAdapter.Storage) {
                mw = new MediaWrapper(((BaseBrowserAdapter.Storage) item).getUri());
                mw.setType(MediaWrapper.TYPE_DIR);
            } else  if (item instanceof MediaWrapper){
                mw = (MediaWrapper) item;
            } else
                mw = null;
            if (mw != null){
                if (mw.getType() == MediaWrapper.TYPE_DIR || mw.getType() == MediaWrapper.TYPE_PLAYLIST){
                    mMediaBrowser.browse(mw.getUri());
                    return;
                }
            }
            ++mCurrentParsedPosition;
        }
    }

    private MediaBrowser.EventListener mFoldersBrowserListener = new MediaBrowser.EventListener(){
        ArrayList<MediaWrapper> directories = new ArrayList<MediaWrapper>();
        ArrayList<MediaWrapper> files = new ArrayList<MediaWrapper>();

        @Override
        public void onMediaAdded(int index, Media media) {
            int type = media.getType();
            if (type == Media.Type.Directory)
                directories.add(new MediaWrapper(media));
            else if (type == Media.Type.File)
                files.add(new MediaWrapper(media));
        }

        @Override
        public void onMediaRemoved(int index, Media media) {}

        @Override
        public void onBrowseEnd() {
            if (mAdapter.isEmpty()) {
                mCurrentParsedPosition = -1;
                releaseBrowser();
                return;
            }
            String holderText = getDescription(directories.size(), files.size());
            MediaWrapper mw = null;

            if (!TextUtils.equals(holderText, "")) {
                mAdapter.setDescription(mCurrentParsedPosition, holderText);
                directories.addAll(files);
                mMediaLists.put(mCurrentParsedPosition, new ArrayList<MediaWrapper>(directories));
            }
            while (++mCurrentParsedPosition < mAdapter.getItemCount()){ //skip media that are not browsable
                if (mAdapter.getItem(mCurrentParsedPosition) instanceof MediaWrapper) {
                    mw = (MediaWrapper) mAdapter.getItem(mCurrentParsedPosition);
                    if (mw.getType() == MediaWrapper.TYPE_DIR || mw.getType() == MediaWrapper.TYPE_PLAYLIST)
                        break;
                } else if (mAdapter.getItem(mCurrentParsedPosition) instanceof BaseBrowserAdapter.Storage) {
                    mw = new MediaWrapper(((BaseBrowserAdapter.Storage) mAdapter.getItem(mCurrentParsedPosition)).getUri());
                    break;
                } else
                    mw = null;
            }

            if (mw != null) {
                if (mCurrentParsedPosition < mAdapter.getItemCount()) {
                    mMediaBrowser.browse(mw.getUri());
                } else {
                    mCurrentParsedPosition = -1;
                    releaseBrowser();
                }
            } else
                releaseBrowser();
            directories .clear();
            files.clear();
        }

        private String getDescription(int folderCount, int mediaFileCount) {
            String holderText = "";
            if (folderCount > 0) {
                holderText += VLCApplication.getAppResources().getQuantityString(
                        R.plurals.subfolders_quantity, folderCount, folderCount
                );
                if (mediaFileCount > 0)
                    holderText += ", ";
            }
            if (mediaFileCount > 0)
                holderText += VLCApplication.getAppResources().getQuantityString(
                        R.plurals.mediafiles_quantity, mediaFileCount,
                        mediaFileCount);
            else if (folderCount == 0 && mediaFileCount == 0)
                holderText = getString(R.string.directory_empty);
            return holderText;
        }
    };
}
