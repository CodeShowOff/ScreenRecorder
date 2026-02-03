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
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.michaelflisar.changelog.ChangelogBuilder;
import com.orpheusdroid.screenrecorder.BuildConfig;
import com.orpheusdroid.screenrecorder.Const;
import com.orpheusdroid.screenrecorder.R;
import com.orpheusdroid.screenrecorder.interfaces.PermissionResultListener;
import com.orpheusdroid.screenrecorder.services.RecorderService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private PermissionResultListener mPermissionResultListener;

    private MediaProjection mMediaProjection;
    private MediaProjectionManager mProjectionManager;
    private MaterialButton fab;
    private MaterialButton btnPause;
    private MaterialButton btnStop;
    private MaterialButton btnResume;
    private LinearLayout recordingControlsContainer;
    private BroadcastReceiver recordingStateReceiver;
    private ViewPager viewPager;
    private SharedPreferences prefs;

    public static void createDir(Context context) {
        // Use app-specific directory for scoped storage
        File appDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (appDir != null && !appDir.exists()) {
            appDir.mkdirs();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        String theme = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(getString(R.string.preference_theme_key), Const.PREFS_LIGHT_THEME);
        int popupOverlayTheme = 0;
        int toolBarColor = 0;
        switch (theme) {
            case Const.PREFS_WHITE_THEME:
                setTheme(R.style.AppTheme_White_NoActionBar);
                break;
            case Const.PREFS_DARK_THEME:
                setTheme(R.style.AppTheme_Dark_NoActionBar);
                popupOverlayTheme = R.style.AppTheme_PopupOverlay_Dark;
                toolBarColor = ContextCompat.getColor(this, R.color.colorPrimary_dark);
                break;
            case Const.PREFS_BLACK_THEME:
                setTheme(R.style.AppTheme_Black_NoActionBar);
                popupOverlayTheme = R.style.AppTheme_PopupOverlay_Black;
                toolBarColor = ContextCompat.getColor(this, R.color.colorPrimary_black);
                break;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(toolBarColor);

        if (popupOverlayTheme != 0)
            toolbar.setPopupTheme(popupOverlayTheme);

        setSupportActionBar(toolbar);

        viewPager = findViewById(R.id.viewpager);
        setupViewPager(viewPager);

        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.setBackgroundColor(toolBarColor);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        //Arbitrary "Write to external storage" permission since this permission is most important for the app
        requestPermissionStorage();

        // Request notification permission for Android 13+
        requestNotificationPermission();

        fab = findViewById(R.id.fab);
        btnPause = findViewById(R.id.btn_pause);
        btnStop = findViewById(R.id.btn_stop);
        btnResume = findViewById(R.id.btn_resume);
        recordingControlsContainer = findViewById(R.id.recording_controls_container);

        // Setup broadcast receiver for recording state changes
        setupRecordingStateReceiver();

        //Acquiring media projection service to start screen mirroring
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        //Respond to app shortcut
        if (getIntent().getAction() != null) {
            if (getIntent().getAction().equals(getString(R.string.app_shortcut_action))) {
                startActivityForResult(mProjectionManager.createScreenCaptureIntent(), Const.SCREEN_RECORD_REQUEST_CODE);
                return;
            } else if (getIntent().getAction().equals(Const.SCREEN_RECORDER_VIDEOS_LIST_FRAGMENT_INTENT)) {
                viewPager.setCurrentItem(1);
            }
        }

        if (isServiceRunning(RecorderService.class)) {
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "service is running");
            }
            updateUIForRecordingState(Const.RecordingState.RECORDING);
        }
        fab.setOnClickListener(view -> {
            if (mMediaProjection == null && !isServiceRunning(RecorderService.class)) {
                //Request Screen recording permission
                startActivityForResult(mProjectionManager.createScreenCaptureIntent(), Const.SCREEN_RECORD_REQUEST_CODE);
            } else if (isServiceRunning(RecorderService.class)) {
                //stop recording if the service is already active and recording
                Toast.makeText(MainActivity.this, "Screen already recording", Toast.LENGTH_SHORT).show();
            }
        });
        fab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Toast.makeText(MainActivity.this, R.string.fab_record_hint, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        // Pause button click listener
        btnPause.setOnClickListener(view -> {
            Intent pauseIntent = new Intent(this, RecorderService.class);
            pauseIntent.setAction(Const.SCREEN_RECORDING_PAUSE);
            startForegroundService(pauseIntent);
        });

        // Stop button click listener
        btnStop.setOnClickListener(view -> {
            Intent stopIntent = new Intent(this, RecorderService.class);
            stopIntent.setAction(Const.SCREEN_RECORDING_STOP);
            startForegroundService(stopIntent);
        });

        // Resume button click listener
        btnResume.setOnClickListener(view -> {
            Intent resumeIntent = new Intent(this, RecorderService.class);
            resumeIntent.setAction(Const.SCREEN_RECORDING_RESUME);
            startForegroundService(resumeIntent);
        });
    }

    private void setupViewPager(ViewPager viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new SettingsPreferenceFragment(), getString(R.string.tab_settings_title));
        adapter.addFragment(new VideosListFragment(), getString(R.string.tab_videos_title));
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 0:
                        fab.setVisibility(View.VISIBLE);
                        break;
                    case 1:
                        fab.setVisibility(View.GONE);
                        break;
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Only handle screen recording permission results, ignore other activity results
        if (requestCode != Const.SCREEN_RECORD_REQUEST_CODE) {
            return;
        }

        String intentAction = getIntent().getAction();

        //The user has denied permission for screen mirroring. Let's notify the user
        if (resultCode == RESULT_CANCELED) {
            Toast.makeText(this,
                    getString(R.string.screen_recording_permission_denied), Toast.LENGTH_SHORT).show();
            //Return to home screen if the app was started from app shortcut
            if (intentAction != null && intentAction.equals(getString(R.string.app_shortcut_action)))
                this.finish();
            return;
        }

        /*If code reaches this point, congratulations! The user has granted screen mirroring permission
         * Let us set the recorderservice intent with relevant data and start service*/
        Intent recorderService = new Intent(this, RecorderService.class);
        recorderService.setAction(Const.SCREEN_RECORDING_START);
        recorderService.putExtra(Const.RECORDER_INTENT_RESULT, resultCode);
        // For Android 14+, use fillIn() instead of putExtra() for MediaProjection data
        // This ensures the Intent data is properly transferred without parceling issues
        if (data != null) {
            try {
                recorderService.fillIn(data, Intent.FILL_IN_DATA | Intent.FILL_IN_CLIP_DATA);
            } catch (Exception e) {
                if (BuildConfig.DEBUG) {
                    Log.e(Const.TAG, "Failed to fill in MediaProjection data: " + e.getMessage());
                }
            }
        }
        
        // Use startForegroundService for Android 12+
        startForegroundService(recorderService);
        
        // Move app to background so recording starts immediately without the app being visible
        moveTaskToBack(true);

        if (intentAction != null && intentAction.equals(getString(R.string.app_shortcut_action)))
            this.finish();
    }


    public void onDirectoryChanged() {
        ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
        ((VideosListFragment) adapter.getItem(1)).removeVideosList();
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "reached main act");
        }
    }


    public boolean requestPermissionStorage() {
        // Android 13+ (API 33+) uses READ_MEDIA_VIDEO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder alert = new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.storage_permission_request_title))
                        .setMessage(getString(R.string.storage_permission_request_summary))
                        .setNeutralButton(getString(R.string.ok), (dialogInterface, i) -> 
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.READ_MEDIA_VIDEO},
                                    Const.MEDIA_VIDEO_REQUEST_CODE))
                        .setCancelable(false);
                alert.create().show();
                return false;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) needs MANAGE_EXTERNAL_STORAGE for folder creation
            if (!Environment.isExternalStorageManager()) {
                AlertDialog.Builder alert = new AlertDialog.Builder(this)
                        .setTitle("Storage Permission Required")
                        .setMessage("This app needs full storage access to create folders and save recordings.")
                        .setNeutralButton(getString(R.string.ok), (dialogInterface, i) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            } catch (Exception e) {
                                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivity(intent);
                            }
                        })
                        .setCancelable(false);
                alert.create().show();
                return false;
            }
        } else {
            // For Android 6 - 10, request WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder alert = new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.storage_permission_request_title))
                        .setMessage(getString(R.string.storage_permission_request_summary))
                        .setNeutralButton(getString(R.string.ok), (dialogInterface, i) -> 
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                                    Const.EXTDIR_REQUEST_CODE))
                        .setCancelable(false);
                alert.create().show();
                return false;
            }
        }
        return true;
    }


    public void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        Const.NOTIFICATION_REQUEST_CODE);
            }
        }
    }

    public void requestPermissionAudio(int requestCode) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case Const.MEDIA_VIDEO_REQUEST_CODE:
                if ((grantResults.length > 0) &&
                        (grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Media Permission Denied");
                    }
                    /* Disable floating action Button in case media permission is denied.
                     * There is no use in recording screen when the video is unable to be saved */
                    fab.setEnabled(false);
                } else {
                    /* Since we have media permission now, lets create the app directory */
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Media Permission granted");
                    }
                    createDir(this);
                }
                break;
            case Const.NOTIFICATION_REQUEST_CODE:
                if ((grantResults.length > 0) &&
                        (grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Notification Permission Denied");
                    }
                    Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_SHORT).show();
                }
                break;
        }

        // Let's also pass the result data to SettingsPreferenceFragment using the callback interface
        if (mPermissionResultListener != null) {
            mPermissionResultListener.onPermissionResult(requestCode, permissions, grantResults);
        }
    }


    public void setPermissionResultListener(PermissionResultListener mPermissionResultListener) {
        this.mPermissionResultListener = mPermissionResultListener;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check recording state when activity starts
        if (isServiceRunning(RecorderService.class)) {
            // Restore actual recording state from SharedPreferences
            String stateStr = prefs.getString("recording_state", Const.RecordingState.RECORDING.name());
            try {
                Const.RecordingState state = Const.RecordingState.valueOf(stateStr);
                if (BuildConfig.DEBUG) {
                    Log.d(Const.TAG, "Restoring UI state from preferences: " + state);
                }
                updateUIForRecordingState(state);
            } catch (IllegalArgumentException e) {
                if (BuildConfig.DEBUG) {
                    Log.w(Const.TAG, "Invalid state in preferences, defaulting to RECORDING");
                }
                updateUIForRecordingState(Const.RecordingState.RECORDING);
            }
        } else {
            // Service not running, ensure UI shows STOPPED state
            updateUIForRecordingState(Const.RecordingState.STOPPED);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recordingStateReceiver != null) {
            unregisterReceiver(recordingStateReceiver);
        }
        // Clean up state if service is not running
        if (!isServiceRunning(RecorderService.class)) {
            prefs.edit().remove("recording_state").apply();
        }
    }

    private void setupRecordingStateReceiver() {
        recordingStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Const.RECORDING_STATE_CHANGED.equals(intent.getAction())) {
                    String stateStr = intent.getStringExtra(Const.RECORDING_STATE_EXTRA);
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Received broadcast: state = " + stateStr);
                    }
                    if (stateStr != null) {
                        Const.RecordingState state = Const.RecordingState.valueOf(stateStr);
                        if (BuildConfig.DEBUG) {
                            Log.d(Const.TAG, "Updating UI for state: " + state);
                        }
                        updateUIForRecordingState(state);
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter(Const.RECORDING_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recordingStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(recordingStateReceiver, filter);
        }
    }

    private void updateUIForRecordingState(Const.RecordingState state) {
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "updateUIForRecordingState called with state: " + state);
        }
        runOnUiThread(() -> {
            switch (state) {
                case RECORDING:
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Setting UI for RECORDING state");
                    }
                    // Hide start button, show pause and stop buttons
                    fab.setVisibility(View.GONE);
                    recordingControlsContainer.setVisibility(View.VISIBLE);
                    btnPause.setVisibility(View.VISIBLE);
                    btnResume.setVisibility(View.GONE);
                    btnStop.setVisibility(View.VISIBLE);
                    break;
                case PAUSED:
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Setting UI for PAUSED state");
                    }
                    // Hide start and pause buttons, show resume and stop buttons
                    fab.setVisibility(View.GONE);
                    recordingControlsContainer.setVisibility(View.VISIBLE);
                    btnPause.setVisibility(View.GONE);
                    btnResume.setVisibility(View.VISIBLE);
                    btnStop.setVisibility(View.VISIBLE);
                    break;
                case STOPPED:
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Setting UI for STOPPED state");
                    }
                    // Show start button, hide all control buttons
                    fab.setVisibility(View.VISIBLE);
                    recordingControlsContainer.setVisibility(View.GONE);
                    btnPause.setVisibility(View.GONE);
                    btnResume.setVisibility(View.GONE);
                    btnStop.setVisibility(View.GONE);
                    break;
            }
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "UI update complete. FAB visibility: " + fab.getVisibility() + 
                      ", Container visibility: " + recordingControlsContainer.getVisibility() +
                      ", Pause: " + btnPause.getVisibility() +
                      ", Resume: " + btnResume.getVisibility() +
                      ", Stop: " + btnStop.getVisibility());
            }
        });
    }

    class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        ViewPagerAdapter(FragmentManager manager) {
            super(manager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            return super.getItemPosition(object);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }
    }
}
