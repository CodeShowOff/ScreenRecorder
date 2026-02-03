/*
 * Copyright (c) 2016-2018. Vijai Chandra Prasad R.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses
 */

package com.orpheusdroid.screenrecorder.ui;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CancellationSignal;
import android.provider.MediaStore;
import android.util.Size;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.orpheusdroid.screenrecorder.BuildConfig;
import com.orpheusdroid.screenrecorder.Const;
import com.orpheusdroid.screenrecorder.R;
import com.orpheusdroid.screenrecorder.adapter.Video;
import com.orpheusdroid.screenrecorder.adapter.VideoRecyclerAdapter;
import com.orpheusdroid.screenrecorder.interfaces.PermissionResultListener;

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class VideosListFragment extends Fragment implements PermissionResultListener, SwipeRefreshLayout.OnRefreshListener {
    private RecyclerView videoRV;
    private TextView message;
    private SharedPreferences prefs;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ArrayList<Video> videosList = new ArrayList<>();
    private boolean loadInOnCreate = false;

    public VideosListFragment() {

    }

    private static boolean isVideoFile(String path) {
        String mimeType = URLConnection.guessContentTypeFromName(path);
        return mimeType != null && mimeType.startsWith("video");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_videos, container, false);
        message = view.findViewById(R.id.message_tv);
        videoRV = view.findViewById(R.id.videos_rv);

        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary,
                android.R.color.holo_green_dark,
                android.R.color.holo_orange_dark,
                android.R.color.holo_blue_dark);

        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        if (loadInOnCreate)
            checkPermission();

        return view;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser && getActivity() != null) {
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "Videos fragment visible");
            }
            refreshVideoList();
        } else if (isVisibleToUser && getActivity() == null)
            loadInOnCreate = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "VideosListFragment onResume");
        }
        refreshVideoList();
    }

    private void refreshVideoList() {
        if (getActivity() != null && videoRV != null) {
            videosList.clear();
            checkPermission();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        MenuItem refresh = menu.add("Refresh");
        refresh.setIcon(R.drawable.ic_refresh_white_24dp);
        refresh.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
        refresh.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                if (swipeRefreshLayout.isRefreshing())
                        return false;
                videosList.clear();
                checkPermission();
                if (BuildConfig.DEBUG) {
                    Log.d(Const.TAG, "Refreshing");
                }
                return false;
            }
        });
    }

    private void checkPermission() {
        boolean hasPermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission = ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_MEDIA_VIDEO)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            hasPermission = true;
        }
        
        if (!hasPermission) {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).setPermissionResultListener(this);
                ((MainActivity) getActivity()).requestPermissionStorage();
            }
        } else {
            if (videosList.isEmpty()) {
                // Check if using SAF-based storage
                String uriString = prefs.getString(Const.SAVE_LOCATION_URI_KEY, null);
                
                if (uriString != null && getContext() != null) {
                    // Load videos from SAF URI
                    try {
                        Uri treeUri = Uri.parse(uriString);
                        DocumentFile pickedDir = DocumentFile.fromTreeUri(getContext(), treeUri);
                        if (pickedDir != null && pickedDir.exists() && pickedDir.isDirectory()) {
                            new GetVideosFromSAFAsync().execute(pickedDir);
                        } else {
                            if (BuildConfig.DEBUG) {
                                Log.e(Const.TAG, "SAF directory not accessible");
                            }
                            loadFromFileSystem();
                        }
                    } catch (Exception e) {
                        if (BuildConfig.DEBUG) {
                            Log.e(Const.TAG, "Error loading from SAF: " + e.getMessage());
                        }
                        loadFromFileSystem();
                    }
                } else {
                    // Load videos from traditional file system
                    loadFromFileSystem();
                }
            }
        }
    }

    private void loadFromFileSystem() {
                String defaultSaveLocation;
                if (getActivity() != null) {
                    File moviesDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ScreenRecorder");
                    if (!moviesDir.exists() && !moviesDir.mkdirs()) {
                        moviesDir = getActivity().getExternalFilesDir(Environment.DIRECTORY_MOVIES);
                        if (moviesDir == null) {
                            moviesDir = new File(getActivity().getFilesDir(), "Movies");
                        }
                    }
                    defaultSaveLocation = moviesDir.getAbsolutePath();
                } else {
                    defaultSaveLocation = "";
                }
                
                File directory = new File(prefs.getString(getString(R.string.savelocation_key), defaultSaveLocation));
                if (!directory.exists()){
                    if (directory.mkdirs()) {
                        if (BuildConfig.DEBUG) {
                            Log.d(Const.TAG, "Directory created successfully");
                        }
                    } else {
                        if (BuildConfig.DEBUG) {
                            Log.d(Const.TAG, "Directory missing! Failed to create dir");
                        }
                    }
                }
                ArrayList<File> filesList = new ArrayList<File>();
                if (directory.isDirectory() && directory.exists()) {
                    File[] files = directory.listFiles();
                    if (files != null) {
                        filesList.addAll(Arrays.asList(getVideos(files)));
                    }
                }

                new GetVideosAsync().execute(filesList.toArray(new File[filesList.size()]));
            }

    private File[] getVideos(File[] files) {
        List<File> newFiles = new ArrayList<>();
        for (File file : files) {
            if (!file.isDirectory() && isVideoFile(file.getPath()))
                newFiles.add(file);
        }
        return newFiles.toArray(new File[newFiles.size()]);
    }

    private void setRecyclerView(ArrayList<Video> videos) {
        videoRV.setHasFixedSize(true);
        final GridLayoutManager layoutManager = new GridLayoutManager(getActivity(), 2);
        videoRV.setLayoutManager(layoutManager);
        final VideoRecyclerAdapter adapter = new VideoRecyclerAdapter(getActivity(), videos, this);
        videoRV.setAdapter(adapter);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.isSection(position) ? layoutManager.getSpanCount() : 1;
            }
        });
    }

    @Override
    public void onPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case Const.EXTDIR_REQUEST_CODE:
            case Const.MEDIA_VIDEO_REQUEST_CODE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    checkPermission();
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Storage/Media permission denied.");
                    }
                    videoRV.setVisibility(View.GONE);
                    message.setText(R.string.video_list_permission_denied_message);
                }
                break;
        }
    }

    public void removeVideosList() {
        videosList.clear();
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Reached video fragment");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Refresh data after edit!");
        }
        removeVideosList();
        checkPermission();
    }

    @Override
    public void onRefresh() {
        videosList.clear();
        checkPermission();
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Refreshing");
        }
    }

    private ArrayList<Video> addSections(ArrayList<Video> videos) {
        ArrayList<Video> videosWithSections = new ArrayList<>();
        Date currentSection = new Date();
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Original Length: " + videos.size());
        }
        for (int i = 0; i < videos.size(); i++) {
            Video video = videos.get(i);
            //Add the first section arbitrarily
            if (i==0){
                videosWithSections.add(new Video(true, video.getLastModified()));
                videosWithSections.add(video);
                currentSection = video.getLastModified();
                continue;
            }
            if (addNewSection(currentSection, video.getLastModified())){
                videosWithSections.add(new Video(true, video.getLastModified()));
                currentSection = video.getLastModified();
            }
            videosWithSections.add(video);
        }
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Length with sections: " + videosWithSections.size());
        }
        return videosWithSections;
    }

    private boolean addNewSection(Date current, Date next)
    {
        Calendar currentSectionDate = toCalendar(current.getTime());
        Calendar nextVideoDate = toCalendar(next.getTime());

        long milis1 = currentSectionDate.getTimeInMillis();
        long milis2 = nextVideoDate.getTimeInMillis();

        int dayDiff = (int)Math.abs((milis2 - milis1) / (24 * 60 * 60 * 1000));
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Date diff is: " + (dayDiff));
        }
        return dayDiff > 0;
    }

    private Calendar toCalendar(long timestamp)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    class GetVideosAsync extends AsyncTask<File[], Integer, ArrayList<Video>> {
        File[] files;
        ContentResolver resolver;

        GetVideosAsync() {
            if (getActivity() != null) {
                resolver = getActivity().getApplicationContext().getContentResolver();
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            swipeRefreshLayout.setRefreshing(true);
        }

        @Override
        protected void onPostExecute(ArrayList<Video> videos) {
            if (videos.isEmpty()) {
                videoRV.setVisibility(View.GONE);
                message.setVisibility(View.VISIBLE);
            } else {
                Collections.sort(videos, Collections.<Video>reverseOrder());
                setRecyclerView(addSections(videos));
                videoRV.setVisibility(View.VISIBLE);
                message.setVisibility(View.GONE);
            }
            swipeRefreshLayout.setRefreshing(false);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);

            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "Progress is :" + values[0]);
            }
        }

        @Override
        protected ArrayList<Video> doInBackground(File[]... arg) {
            //Get video file name, Uri and video thumbnail from mediastore
            files = arg[0];
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                if (!file.isDirectory() && isVideoFile(file.getPath())) {
                    videosList.add(new Video(file.getName(),
                            file,
                            getBitmap(file),
                            new Date(file.lastModified())));
                    publishProgress(i);
                }
            }
            return videosList;
        }

        Bitmap getBitmap(File file) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return ThumbnailUtils.createVideoThumbnail(
                            file,
                            new Size(320, 180),
                            new CancellationSignal());
                } else {
                    return ThumbnailUtils.createVideoThumbnail(
                            file.getAbsolutePath(),
                            MediaStore.Video.Thumbnails.MINI_KIND);
                }
            } catch (Exception e) {
                if (BuildConfig.DEBUG) {
                    Log.e(Const.TAG, "Error getting thumbnail for " + file.getName() + ": " + e.getMessage());
                }
                return null;
            }
        }
    }

    /**
     * AsyncTask for loading videos from SAF DocumentFile
     */
    private class GetVideosFromSAFAsync extends AsyncTask<DocumentFile, Integer, ArrayList<Video>> {
        private ArrayList<Video> videosList = new ArrayList<>();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            swipeRefreshLayout.setRefreshing(true);
        }

        @Override
        protected void onPostExecute(ArrayList<Video> videos) {
            super.onPostExecute(videos);
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "Loaded " + videos.size() + " videos from SAF");
            }
            if (videos.isEmpty()) {
                videoRV.setVisibility(View.GONE);
                message.setVisibility(View.VISIBLE);
            } else {
                Collections.sort(videos, Collections.<Video>reverseOrder());
                setRecyclerView(addSections(videos));
                videoRV.setVisibility(View.VISIBLE);
                message.setVisibility(View.GONE);
            }
            swipeRefreshLayout.setRefreshing(false);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "Progress is :" + values[0]);
            }
        }

        @Override
        protected ArrayList<Video> doInBackground(DocumentFile... dirs) {
            if (dirs.length == 0 || dirs[0] == null) {
                return videosList;
            }

            DocumentFile directory = dirs[0];
            DocumentFile[] files = directory.listFiles();
            
            if (files == null || files.length == 0) {
                if (BuildConfig.DEBUG) {
                    Log.d(Const.TAG, "No files found in SAF directory");
                }
                return videosList;
            }

            for (int i = 0; i < files.length; i++) {
                DocumentFile docFile = files[i];
                if (docFile.isFile() && isVideoFile(docFile.getName())) {
                    try {
                        videosList.add(new Video(
                                docFile.getName(),
                                docFile.getUri(),
                                getBitmapFromUri(docFile.getUri()),
                                new Date(docFile.lastModified())
                        ));
                        publishProgress(i);
                    } catch (Exception e) {
                        if (BuildConfig.DEBUG) {
                            Log.e(Const.TAG, "Error loading SAF video: " + e.getMessage());
                        }
                    }
                }
            }
            return videosList;
        }

        Bitmap getBitmapFromUri(Uri uri) {
            try {
                if (getContext() == null) {
                    return null;
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        // Try using ContentResolver's loadThumbnail method (API 29+)
                        return getContext().getContentResolver().loadThumbnail(
                                uri,
                                new Size(320, 180),
                                new CancellationSignal()
                        );
                    } catch (IOException e) {
                        if (BuildConfig.DEBUG) {
                            Log.w(Const.TAG, "loadThumbnail failed, trying MediaMetadataRetriever: " + e.getMessage());
                        }
                        // Fallback to MediaMetadataRetriever
                        return getThumbnailFromMediaRetriever(uri);
                    }
                } else {
                    // For older versions, use MediaMetadataRetriever
                    return getThumbnailFromMediaRetriever(uri);
                }
            } catch (Exception e) {
                if (BuildConfig.DEBUG) {
                    Log.e(Const.TAG, "Error getting thumbnail from URI: " + e.getMessage());
                }
                return null;
            }
        }

        private Bitmap getThumbnailFromMediaRetriever(Uri uri) {
            MediaMetadataRetriever retriever = null;
            try {
                if (getContext() == null) {
                    return null;
                }
                retriever = new MediaMetadataRetriever();
                retriever.setDataSource(getContext(), uri);
                Bitmap bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                
                // Scale down the bitmap if it's too large
                if (bitmap != null && (bitmap.getWidth() > 320 || bitmap.getHeight() > 180)) {
                    float scale = Math.min(320f / bitmap.getWidth(), 180f / bitmap.getHeight());
                    int newWidth = Math.round(bitmap.getWidth() * scale);
                    int newHeight = Math.round(bitmap.getHeight() * scale);
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                    if (scaledBitmap != bitmap) {
                        bitmap.recycle();
                    }
                    return scaledBitmap;
                }
                return bitmap;
            } catch (Exception e) {
                if (BuildConfig.DEBUG) {
                    Log.e(Const.TAG, "MediaMetadataRetriever failed: " + e.getMessage());
                }
                return null;
            } finally {
                if (retriever != null) {
                    try {
                        retriever.release();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }
    }
}
