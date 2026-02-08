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

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import com.orpheusdroid.screenrecorder.Const;
import com.orpheusdroid.screenrecorder.R;

import java.io.File;
import java.util.ArrayList;

import androidx.documentfile.provider.DocumentFile;

import androidx.appcompat.app.AppCompatActivity;
import life.knowledge4.videotrimmer.K4LVideoTrimmer;
import life.knowledge4.videotrimmer.interfaces.OnTrimVideoListener;

public class EditVideoActivity extends AppCompatActivity implements OnTrimVideoListener{
    private ProgressDialog saveprogress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_video);

        if(!getIntent().hasExtra(Const.VIDEO_EDIT_URI_KEY)) {
            Toast.makeText(this, getResources().getString(R.string.video_not_found), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Uri videoUri = Uri.parse(getIntent().getStringExtra(Const.VIDEO_EDIT_URI_KEY));

        // Check if video exists - handle both file:// and content:// URIs
        boolean videoExists = false;
        String destinationPath;
        
        if ("content".equals(videoUri.getScheme())) {
            // SAF content URI
            DocumentFile docFile = DocumentFile.fromSingleUri(this, videoUri);
            videoExists = docFile != null && docFile.exists();
            
            // Try to get the real path from content URI
            String realPath = getRealPathFromURI(videoUri);
            
            if (realPath != null && new File(realPath).exists()) {
                // We have a real file path, use its parent directory
                File videoFile = new File(realPath);
                destinationPath = videoFile.getParent() + "/";
                Log.d(Const.TAG, "Using parent directory of real path: " + destinationPath);
            } else {
                // Use the user's configured save location for edited videos
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                File moviesDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ScreenRecorder");
                if (!moviesDir.exists()) {
                    moviesDir.mkdirs();
                }
                String defaultSaveLocation = moviesDir.getAbsolutePath();
                destinationPath = prefs.getString(getString(R.string.savelocation_key), defaultSaveLocation) + "/";
                Log.d(Const.TAG, "Using configured save location: " + destinationPath);
            }
        } else {
            // Regular file URI
            File videoFile = new File(videoUri.getPath());
            videoExists = videoFile.exists();
            destinationPath = videoFile.getParent() + "/";
        }

        if (!videoExists) {
            Toast.makeText(this, getResources().getString(R.string.video_not_found), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        K4LVideoTrimmer videoTrimmer = findViewById(R.id.videoTimeLine);

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        //use one of overloaded setDataSource() functions to set your data source
        retriever.setDataSource(this, videoUri);
        String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        int timeInMins = (((int)Long.parseLong(time)) / 1000)+1000;
        Log.d(Const.TAG, timeInMins+"");

        videoTrimmer.setOnTrimVideoListener(this);
        videoTrimmer.setVideoURI(videoUri);
        videoTrimmer.setMaxDuration(timeInMins);
        Log.d(Const.TAG, "Edited file destination: " + destinationPath);
        videoTrimmer.setDestinationPath(destinationPath);
    }

    @Override
    public void getResult(Uri uri) {
        Log.d(Const.TAG, uri.getPath());
        indexFile(uri.getPath());

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                saveprogress = new ProgressDialog(EditVideoActivity.this);
                saveprogress.setMessage("Please wait while the video is being saved");
                saveprogress.setTitle("Please wait");
                saveprogress.setIndeterminate(true);
                saveprogress.show();
            }
        });
    }

    @Override
    public void cancelAction() {
        finish();
    }

    private void indexFile(String SAVEPATH) {
        //Create a new ArrayList and add the newly created video file path to it
        ArrayList<String> toBeScanned = new ArrayList<>();
        toBeScanned.add(SAVEPATH);
        String[] toBeScannedStr = new String[toBeScanned.size()];
        toBeScannedStr = toBeScanned.toArray(toBeScannedStr);

        //Request MediaScannerConnection to scan the new file and index it
        MediaScannerConnection.scanFile(this, toBeScannedStr, null, new MediaScannerConnection.OnScanCompletedListener() {

            @Override
            public void onScanCompleted(String path, Uri uri) {
                Log.i(Const.TAG, "SCAN COMPLETED: " + path);
                saveprogress.cancel();
                setResult(Const.VIDEO_EDIT_RESULT_CODE);
                finish();
            }
        });
    }

    /**
     * Try to get the real file path from a content URI
     * Works for MediaStore and some other content URIs
     */
    private String getRealPathFromURI(Uri contentUri) {
        String result = null;
        
        try {
            // Try to extract path from DocumentsContract URIs
            if (DocumentsContract.isDocumentUri(this, contentUri)) {
                String docId = DocumentsContract.getDocumentId(contentUri);
                
                // ExternalStorageProvider
                if ("com.android.externalstorage.documents".equals(contentUri.getAuthority())) {
                    final String[] split = docId.split(":");
                    if (split.length >= 2) {
                        final String type = split[0];
                        if ("primary".equalsIgnoreCase(type)) {
                            result = Environment.getExternalStorageDirectory() + "/" + split[1];
                        }
                    }
                }
                // MediaProvider
                else if ("com.android.providers.media.documents".equals(contentUri.getAuthority())) {
                    final String[] split = docId.split(":");
                    if (split.length >= 2) {
                        final String type = split[0];
                        Uri mediaUri = null;
                        if ("video".equals(type)) {
                            mediaUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                        }
                        
                        if (mediaUri != null) {
                            final String selection = "_id=?";
                            final String[] selectionArgs = new String[]{split[1]};
                            result = getDataColumn(mediaUri, selection, selectionArgs);
                        }
                    }
                }
            }
            // Try regular MediaStore query for content:// URIs
            else if ("content".equalsIgnoreCase(contentUri.getScheme())) {
                result = getDataColumn(contentUri, null, null);
            }
        } catch (Exception e) {
            Log.w(Const.TAG, "Failed to get real path from URI: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * Get the value of the _data column for a content URI
     */
    private String getDataColumn(Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};
        
        try {
            cursor = getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int columnIndex = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            Log.w(Const.TAG, "Failed to query data column: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

}
