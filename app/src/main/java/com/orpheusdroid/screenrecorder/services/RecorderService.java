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

package com.orpheusdroid.screenrecorder.services;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.orpheusdroid.screenrecorder.BuildConfig;
import com.orpheusdroid.screenrecorder.Const;
import com.orpheusdroid.screenrecorder.R;
import com.orpheusdroid.screenrecorder.ui.EditVideoActivity;
import com.orpheusdroid.screenrecorder.ui.MainActivity;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecorderService extends Service {
    private int WIDTH, HEIGHT, FPS, DENSITY_DPI;
    private int BITRATE;
    private String audioRecSource;
    private String SAVEPATH;
    private AudioManager mAudioManager;

    private int screenOrientation;
    private String saveLocation;

    private boolean isRecording;
    private boolean isPaused;
    private NotificationManager mNotificationManager;
    Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message message) {
            Toast.makeText(RecorderService.this, R.string.screen_recording_stopped_toast, Toast.LENGTH_SHORT).show();
            showShareNotification();
        }
    };
    private Intent data;
    private int result;
    private long startTime, elapsedTime = 0;
    private SharedPreferences prefs;
    private WindowManager window;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;
    private MediaRecorder mMediaRecorder;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Always create notification channels for Android 12+
        createNotificationChannels();

        // Handle null intent - service may be restarted by system
        if (intent == null || intent.getAction() == null) {
            if (BuildConfig.DEBUG) {
                Log.w(Const.TAG, "RecorderService started with null intent or action");
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Find the action to perform from intent
        switch (intent.getAction()) {
            case Const.SCREEN_RECORDING_START:

                /* Wish MediaRecorder had a method isRecording() or similar. But, we are forced to
                 * manage the state ourself. Let's hope the request is honored.
                 * Request: https://code.google.com/p/android/issues/detail?id=800 */
                if (!isRecording) {
                    // Get values from Default SharedPreferences
                    screenOrientation = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                    result = intent.getIntExtra(Const.RECORDER_INTENT_RESULT, Activity.RESULT_OK);
                    
                    // For Android 14+, the MediaProjection data is in the intent itself (via fillIn)
                    // not as a Parcelable extra. This avoids parceling issues.
                    data = intent;

                    // Check if result code indicates permission was granted
                    if (result != Activity.RESULT_OK) {
                        if (BuildConfig.DEBUG) {
                            Log.e(Const.TAG, "MediaProjection permission denied, result code: " + result);
                        }
                        Toast.makeText(this, R.string.recording_failed_toast, Toast.LENGTH_SHORT).show();
                        stopSelf();
                        return START_NOT_STICKY;
                    }

                    getValues();

                    startRecording();

                } else {
                    Toast.makeText(this, R.string.screenrecording_already_active_toast, Toast.LENGTH_SHORT).show();
                }
                break;
            case Const.SCREEN_RECORDING_PAUSE:
                pauseScreenRecording();
                break;
            case Const.SCREEN_RECORDING_RESUME:
                resumeScreenRecording();
                break;
            case Const.SCREEN_RECORDING_STOP:
                stopRecording();
                break;
        }
        return START_STICKY;
    }

    private void stopRecording() {
        // CRITICAL: Broadcast state change and stop foreground BEFORE destroying media projection
        // destroyMediaProjection() calls stopSelf() which might kill the service before broadcast is sent
        broadcastRecordingState(Const.RecordingState.STOPPED);
        
        //The service is started as foreground service and hence has to be stopped
        stopForeground(true);
        
        // Now safely stop screen sharing and destroy media projection
        stopScreenSharing();
    }

    private void pauseScreenRecording() {
        if (mMediaRecorder == null) {
            if (BuildConfig.DEBUG) {
                Log.w(Const.TAG, "Cannot pause: MediaRecorder is null");
            }
            return;
        }
        
        if (isPaused) {
            if (BuildConfig.DEBUG) {
                Log.w(Const.TAG, "Already paused, ignoring pause request");
            }
            return;
        }
        
        try {
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "====== PAUSE: Starting pause process ======");
            }
            mMediaRecorder.pause();
            isPaused = true;
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "PAUSE: MediaRecorder.pause() called successfully, isPaused=" + isPaused);
            }
            
            //calculate total elapsed time until pause
            elapsedTime += (System.currentTimeMillis() - startTime);

            //Set Resume action to Notification and update the current notification
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "PAUSE: Creating RESUME notification action");
            }
            Intent recordResumeIntent = new Intent(this, RecorderService.class);
            recordResumeIntent.setAction(Const.SCREEN_RECORDING_RESUME);
            PendingIntent precordResumeIntent = PendingIntent.getService(this, 2, recordResumeIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.ic_play_arrow_white,
                    getString(R.string.screen_recording_notification_action_resume), precordResumeIntent);
            
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "PAUSE: Updating notification with RESUME button");
            }
            updateNotification(createRecordingNotification(action).setUsesChronometer(false).build(), Const.SCREEN_RECORDER_NOTIFICATION_ID);
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "PAUSE: Notification updated");
            }
            
            // Broadcast recording state change
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "PAUSE: Broadcasting PAUSED state");
            }
            broadcastRecordingState(Const.RecordingState.PAUSED);
            
            Toast.makeText(this, R.string.screen_recording_paused_toast, Toast.LENGTH_SHORT).show();
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "====== PAUSE: Pause process completed ======");
            }
        } catch (IllegalStateException e) {
            if (BuildConfig.DEBUG) {
                Log.e(Const.TAG, "Failed to pause recording: " + e.getMessage());
            }
            Toast.makeText(this, R.string.recording_failed_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void resumeScreenRecording() {
        if (mMediaRecorder == null) {
            if (BuildConfig.DEBUG) {
                Log.w(Const.TAG, "Cannot resume: MediaRecorder is null");
            }
            return;
        }
        
        if (!isPaused) {
            if (BuildConfig.DEBUG) {
                Log.w(Const.TAG, "Not paused, ignoring resume request");
            }
            return;
        }
        
        try {
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "====== RESUME: Starting resume process ======");
            }
            mMediaRecorder.resume();
            isPaused = false;
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "RESUME: MediaRecorder.resume() called successfully, isPaused=" + isPaused);
            }

            //Reset startTime to current time again
            startTime = System.currentTimeMillis();

            //set Pause action to Notification and update current Notification
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "RESUME: Creating PAUSE notification action");
            }
            Intent recordPauseIntent = new Intent(this, RecorderService.class);
            recordPauseIntent.setAction(Const.SCREEN_RECORDING_PAUSE);
            PendingIntent precordPauseIntent = PendingIntent.getService(this, 1, recordPauseIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.ic_pause_white,
                    getString(R.string.screen_recording_notification_action_pause), precordPauseIntent);
            
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "RESUME: Updating notification with PAUSE button");
            }
            updateNotification(createRecordingNotification(action).setUsesChronometer(true)
                    .setWhen((System.currentTimeMillis() - elapsedTime)).build(), Const.SCREEN_RECORDER_NOTIFICATION_ID);
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "RESUME: Notification updated");
            }
            
            // Broadcast recording state change
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "RESUME: Broadcasting RECORDING state");
            }
            broadcastRecordingState(Const.RecordingState.RECORDING);
            
            Toast.makeText(this, R.string.screen_recording_resumed_toast, Toast.LENGTH_SHORT).show();
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "====== RESUME: Resume process completed ======");
            }
        } catch (IllegalStateException e) {
            if (BuildConfig.DEBUG) {
                Log.e(Const.TAG, "Failed to resume recording: " + e.getMessage());
            }
            Toast.makeText(this, R.string.recording_failed_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void startRecording() {
        // IMPORTANT: On Android 14+, we MUST call startForeground() with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        // BEFORE obtaining the MediaProjection object. This is a requirement to prevent crashes.
        startTime = System.currentTimeMillis();
        elapsedTime = 0; // Reset elapsed time for new recording
        isPaused = false; // Reset pause state
        
        // Clear any stale SAF URIs from previous recordings
        prefs.edit().remove("last_video_saf_uri").apply();

        // STEP 1: Start foreground service FIRST with the notification
        // This must happen before obtaining MediaProjection on Android 14+
        Intent recordPauseIntent = new Intent(this, RecorderService.class);
        recordPauseIntent.setAction(Const.SCREEN_RECORDING_PAUSE);
        PendingIntent precordPauseIntent = PendingIntent.getService(this, 1, recordPauseIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.ic_pause_white,
                getString(R.string.screen_recording_notification_action_pause), precordPauseIntent);
        startNotificationForeGround(createRecordingNotification(action).build(), Const.SCREEN_RECORDER_NOTIFICATION_ID);

        // STEP 2: NOW that we're in foreground mode, we can obtain MediaProjection
        //Set Callback for MediaProjection
        mMediaProjectionCallback = new MediaProjectionCallback();
        MediaProjectionManager mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        //Initialize MediaProjection using data received from Intent
        mMediaProjection = mProjectionManager.getMediaProjection(result, data);
        if (mMediaProjection == null) {
            if (BuildConfig.DEBUG) {
                Log.e(Const.TAG, "MediaProjection is null, cannot start recording");
            }
            Toast.makeText(this, R.string.recording_failed_toast, Toast.LENGTH_SHORT).show();
            stopForeground(true);
            stopSelf();
            return;
        }
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);

        //Initialize MediaRecorder class and initialize it with preferred configuration
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setOnErrorListener((mr, what, extra) -> {
            if (BuildConfig.DEBUG) {
                Log.e(Const.TAG, "Screen Recorder Error: " + what + ", Extra: " + extra);
            }
            Toast.makeText(this, R.string.recording_failed_toast, Toast.LENGTH_SHORT).show();
            destroyMediaProjection();
        });
        mMediaRecorder.setOnInfoListener((mr, what, extra) -> {
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "Screen Recorder Info: " + what + ", Extra: " + extra);
            }
        });
        initRecorder();

        // Check if initRecorder failed and MediaRecorder is null
        if (mMediaRecorder == null) {
            if (BuildConfig.DEBUG) {
                Log.e(Const.TAG, "MediaRecorder initialization failed");
            }
            if (mMediaProjection != null) {
                mMediaProjection.unregisterCallback(mMediaProjectionCallback);
                mMediaProjection.stop();
                mMediaProjection = null;
            }
            stopForeground(true);
            stopSelf();
            return;
        }

        /* Create a new virtual display with the actual default display
         * and pass it on to MediaRecorder to start recording */
        mVirtualDisplay = createVirtualDisplay();
        try {
            mMediaRecorder.start();

            isRecording = true;
            
            // Broadcast recording state change
            broadcastRecordingState(Const.RecordingState.RECORDING);
            
            Toast.makeText(this, R.string.screen_recording_started_toast, Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException e) {
            if (BuildConfig.DEBUG) {
                Log.e(Const.TAG, "MediaRecorder reached Illegal state exception. Did you start the recording twice?");
            }
            Toast.makeText(this, R.string.recording_failed_toast, Toast.LENGTH_SHORT).show();
            isRecording = false;
            
            // CRITICAL FIX: Properly clean up all resources when start fails
            if (mMediaRecorder != null) {
                try {
                    mMediaRecorder.reset();
                    mMediaRecorder.release();
                } catch (Exception ex) {
                    Log.e(Const.TAG, "Failed to release MediaRecorder: " + ex.getMessage());
                }
                mMediaRecorder = null;
            }
            
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
                mVirtualDisplay = null;
            }
            
            if (mMediaProjection != null) {
                mMediaProjection.unregisterCallback(mMediaProjectionCallback);
                mMediaProjection.stop();
                mMediaProjection = null;
            }
            
            // Broadcast stopped state
            broadcastRecordingState(Const.RecordingState.STOPPED);
            
            stopForeground(true);
            stopSelf();
        }
    }

    //Virtual display created by mirroring the actual physical display
    private VirtualDisplay createVirtualDisplay() {
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR 
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
        
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Creating VirtualDisplay: " + WIDTH + "x" + HEIGHT + " @ " + DENSITY_DPI + " dpi");
        }
        
        return mMediaProjection.createVirtualDisplay("ScreenRecorder",
                WIDTH, HEIGHT, DENSITY_DPI,
                flags,
                mMediaRecorder.getSurface(), null, null);
    }

    public int getBestSampleRate() {
        AudioManager am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        String sampleRateString = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        int samplingRate = (sampleRateString == null) ? 44100 : Integer.parseInt(sampleRateString);
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Sampling rate: " + samplingRate);
        }
        return samplingRate;
    }

    private boolean getMediaCodecFor(String format) {
        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(
                format,
                WIDTH,
                HEIGHT
        );
        String encoder = list.findEncoderForFormat(mediaFormat);
        if (encoder == null) {
            if (BuildConfig.DEBUG) {
                Log.d("Null Encoder: ", format);
            }
            return false;
        }
        if (BuildConfig.DEBUG) {
            Log.d("Encoder", encoder);
        }
        return !encoder.startsWith("OMX.google");
    }

    private int getBestVideoEncoder() {
        if (getMediaCodecFor(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "Using H.264 hardware encoder");
            }
            return MediaRecorder.VideoEncoder.H264;
        }
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Using default encoder");
        }
        return MediaRecorder.VideoEncoder.DEFAULT;
    }

    private int calculateOptimalBitrate(int width, int height, int frameRate) {
        double pixels = (double) width * height;
        double baseMultiplier;
        
        // More conservative multipliers for better performance
        if (pixels >= 3840 * 2160) { // 4K
            baseMultiplier = 0.15; // ~50 Mbps for 4K@30fps
        } else if (pixels >= 2560 * 1440) { // 2K/1440p
            baseMultiplier = 0.13; // ~20 Mbps for 1440p@30fps
        } else if (pixels >= 1920 * 1080) { // 1080p
            baseMultiplier = 0.10; // ~10 Mbps for 1080p@30fps
        } else if (pixels >= 1280 * 720) { // 720p
            baseMultiplier = 0.08; // ~5 Mbps for 720p@30fps
        } else {
            baseMultiplier = 0.06;
        }
        
        // Adjust for frame rate
        double fpsMultiplier = frameRate / 30.0;
        
        int calculatedBitrate = (int) (pixels * baseMultiplier * fpsMultiplier);
        
        // Conservative limits for better performance
        int minBitrate = 3000000; // 3 Mbps minimum
        int maxBitrate = 60000000; // 60 Mbps maximum
        
        calculatedBitrate = Math.max(minBitrate, Math.min(maxBitrate, calculatedBitrate));
        
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Optimal bitrate: " + (calculatedBitrate / 1000000.0) + " Mbps for " + width + "x" + height + "@" + frameRate);
        }
        
        return calculatedBitrate;
    }

    /* Initialize MediaRecorder with desired default values and values set by user. Everything is
     * pretty much self explanatory */
    private void initRecorder() {
        boolean mustRecAudio;
        try {
            // CRITICAL: Configure audio BEFORE video source to prevent MediaRecorder from entering invalid state
            // if audio configuration fails. Audio source must be set before video source per Android docs.
            mustRecAudio = configureAudioRecording();
            
            // If audio configuration failed and MediaRecorder is in error state, recreate it
            if (mMediaRecorder == null) {
                if (BuildConfig.DEBUG) {
                    Log.e(Const.TAG, "MediaRecorder is null after audio configuration, aborting");
                }
                Toast.makeText(this, R.string.recording_failed_toast, Toast.LENGTH_SHORT).show();
                stopSelf();
                return;
            }
            
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setOutputFile(SAVEPATH);
            mMediaRecorder.setVideoSize(WIDTH, HEIGHT);
            mMediaRecorder.setVideoEncoder(getBestVideoEncoder());
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    mMediaRecorder.setVideoEncodingProfileLevel(
                            MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                            MediaCodecInfo.CodecProfileLevel.AVCLevel41
                    );
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Using H.264 High Profile");
                    }
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) {
                        Log.w(Const.TAG, "Could not set High Profile: " + e.getMessage());
                    }
                }
            }
            
            mMediaRecorder.setMaxFileSize(getFreeSpaceInBytes());
            if (mustRecAudio)
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            
            int optimalBitrate = calculateOptimalBitrate(WIDTH, HEIGHT, FPS);
            int finalBitrate = (BITRATE > 1000000) ? BITRATE : optimalBitrate;
            
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "Encoding bitrate: " + (finalBitrate / 1000000.0) + " Mbps");
            }
            mMediaRecorder.setVideoEncodingBitRate(finalBitrate);
            mMediaRecorder.setVideoFrameRate(FPS);
            
            mMediaRecorder.setMaxDuration(-1);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mMediaRecorder.setCaptureRate(FPS);
            }
            
            mMediaRecorder.prepare();
        } catch (RuntimeException e) {
            // MediaRecorder frequently throws RuntimeException (e.g. setAudioSource failed)
            if (BuildConfig.DEBUG) {
                Log.e(Const.TAG, "MediaRecorder runtime failure: " + e.getMessage());
            }
            e.printStackTrace();
            Toast.makeText(this, R.string.recording_failed_toast, Toast.LENGTH_SHORT).show();
            if (mMediaRecorder != null) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
            stopSelf();
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.e(Const.TAG, "Failed to prepare MediaRecorder: " + e.getMessage());
            }
            e.printStackTrace();
            Toast.makeText(this, R.string.recording_failed_toast, Toast.LENGTH_SHORT).show();
            if (mMediaRecorder != null) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
            stopSelf();
        }
    }

    private boolean configureAudioRecording() {
        if (audioRecSource == null || audioRecSource.equals("0")) {
            return false;
        }

        boolean recordAudioPermissionGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        // Use default audio settings (removed user configuration)
        String audioBitRate = "192000";  // 192 kbps default
        String audioSamplingRate = getBestSampleRate() + "";  // Best sample rate
        String audioChannel = "1";  // Mono default

        try {
            switch (audioRecSource) {
                case "1":
                    // Case 1: Microphone only
                    if (!recordAudioPermissionGranted) {
                        if (BuildConfig.DEBUG) {
                            Log.w(Const.TAG, "Mic audio selected but RECORD_AUDIO permission not granted");
                        }
                        Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    try {
                        mMediaRecorder.setAudioEncodingBitRate(Integer.parseInt(audioBitRate));
                        mMediaRecorder.setAudioSamplingRate(Integer.parseInt(audioSamplingRate));
                        mMediaRecorder.setAudioChannels(Integer.parseInt(audioChannel));
                    } catch (NumberFormatException e) {
                        mMediaRecorder.setAudioEncodingBitRate(192000);
                        mMediaRecorder.setAudioSamplingRate(getBestSampleRate());
                        mMediaRecorder.setAudioChannels(1);
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Audio source: MIC - capturing microphone audio only");
                    }
                    return true;
                    
                default:
                    if (BuildConfig.DEBUG) {
                        Log.w(Const.TAG, "Unknown audio source: " + audioRecSource);
                    }
                    return false;
            }
        } catch (RuntimeException e) {
            if (BuildConfig.DEBUG) {
                Log.e(Const.TAG, "Audio configuration failed: " + e.getMessage());
            }
            e.printStackTrace();
            if (BuildConfig.DEBUG) {
                Log.w(Const.TAG, "Recreating MediaRecorder without audio");
            }
            
            if (mMediaRecorder != null) {
                try {
                    mMediaRecorder.reset();
                    mMediaRecorder.release();
                } catch (Exception ex) {
                    Log.e(Const.TAG, "Failed to release MediaRecorder: " + ex.getMessage());
                }
                mMediaRecorder = null;
            }
            
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setOnErrorListener((mr, what, extra) -> {
                if (BuildConfig.DEBUG) {
                    Log.e(Const.TAG, "Screen Recorder Error: " + what + ", Extra: " + extra);
                }
                Toast.makeText(this, R.string.recording_failed_toast, Toast.LENGTH_SHORT).show();
                destroyMediaProjection();
            });
            mMediaRecorder.setOnInfoListener((mr, what, extra) -> {
                if (BuildConfig.DEBUG) {
                    Log.d(Const.TAG, "Screen Recorder Info: " + what + ", Extra: " + extra);
                }
            });
            
            if (BuildConfig.DEBUG) {
                Log.w(Const.TAG, "Continuing without audio");
            }
            return false;
        }
    }

    private long getFreeSpaceInBytes() {
        StatFs FSStats = new StatFs(saveLocation);
        long bytesAvailable = FSStats.getAvailableBytes();
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Free space in GB: " + bytesAvailable / (1000 * 1000 * 1000));
        }
        return bytesAvailable;
    }

    // Create notification channels for Android 12+
    private void createNotificationChannels() {
        List<NotificationChannel> notificationChannels = new ArrayList<>();
        NotificationChannel recordingNotificationChannel = new NotificationChannel(
                Const.RECORDING_NOTIFICATION_CHANNEL_ID,
                Const.RECORDING_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
        );
        recordingNotificationChannel.enableLights(false);
        recordingNotificationChannel.setShowBadge(true);
        recordingNotificationChannel.enableVibration(false);
        recordingNotificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationChannels.add(recordingNotificationChannel);

        NotificationChannel shareNotificationChannel = new NotificationChannel(
                Const.SHARE_NOTIFICATION_CHANNEL_ID,
                Const.SHARE_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        shareNotificationChannel.enableLights(true);
        shareNotificationChannel.setLightColor(Color.RED);
        shareNotificationChannel.setShowBadge(true);
        shareNotificationChannel.enableVibration(true);
        shareNotificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationChannels.add(shareNotificationChannel);

        getManager().createNotificationChannels(notificationChannels);
    }

    /* Create Notification.Builder with action passed in case user's android version is greater than
     * API24 */
    private NotificationCompat.Builder createRecordingNotification(NotificationCompat.Action action) {
        Bitmap icon = BitmapFactory.decodeResource(getResources(),
                R.mipmap.ic_notification_big);

        Intent recordStopIntent = new Intent(this, RecorderService.class);
        recordStopIntent.setAction(Const.SCREEN_RECORDING_STOP);
        PendingIntent precordStopIntent = PendingIntent.getService(this, 3, recordStopIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent UIIntent = new Intent(this, MainActivity.class);
        PendingIntent notificationContentIntent = PendingIntent.getActivity(this, 0, UIIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_stop_white,
                getResources().getString(R.string.screen_recording_notification_action_stop),
                precordStopIntent
        ).build();

        androidx.media.app.NotificationCompat.MediaStyle mediaStyle = 
                new androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, Const.RECORDING_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getResources().getString(R.string.screen_recording_notification_title))
                .setTicker(getResources().getString(R.string.screen_recording_notification_title))
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(icon)
                .setUsesChronometer(true)
                .setOngoing(true)
                .setContentIntent(notificationContentIntent)
                .setStyle(mediaStyle)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true);
        
        if (action != null) {
            notification.addAction(action);
        }
        notification.addAction(stopAction);
        
        return notification;
    }

    private void showShareNotification() {
        Bitmap icon = BitmapFactory.decodeResource(getResources(),
                R.mipmap.ic_notification_big);

        String safUriString = prefs.getString("last_video_saf_uri", null);
        Uri videoUri;
        boolean isSAFUri = (safUriString != null && !safUriString.isEmpty());
        
        if (isSAFUri) {
            videoUri = Uri.parse(safUriString);
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "Using SAF URI: " + videoUri);
            }
        } else {
            File videoFile = new File(SAVEPATH);
            if (!videoFile.exists()) {
                if (BuildConfig.DEBUG) {
                    Log.e(Const.TAG, "Video file doesn't exist: " + SAVEPATH);
                }
                return;
            }
            videoUri = FileProvider.getUriForFile(
                    this, BuildConfig.APPLICATION_ID + ".provider",
                    videoFile);
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "Using FileProvider URI: " + videoUri);
            }
        }

        Intent Shareintent = new Intent()
                .setAction(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_STREAM, videoUri)
                .setType("video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        PendingIntent sharePendingIntent = PendingIntent.getActivity(this, 0, Intent.createChooser(
                Shareintent, getString(R.string.share_intent_title)), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).setAction(Const.SCREEN_RECORDER_VIDEOS_LIST_FRAGMENT_INTENT), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder shareNotification = new NotificationCompat.Builder(this, Const.SHARE_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.share_intent_notification_title))
                .setContentText(getString(R.string.share_intent_notification_content))
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .addAction(android.R.drawable.ic_menu_share, getString(R.string.share_intent_notification_action_text)
                        , sharePendingIntent);
        
        if (!isSAFUri) {
            Intent editIntent = new Intent(this, EditVideoActivity.class);
            editIntent.putExtra(Const.VIDEO_EDIT_URI_KEY, SAVEPATH);
            PendingIntent editPendingIntent = PendingIntent.getActivity(this, 0, editIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            shareNotification.addAction(android.R.drawable.ic_menu_edit, getString(R.string.edit_intent_notification_action_text)
                    , editPendingIntent);
        }
        
        updateNotification(shareNotification.build(), Const.SCREEN_RECORDER_SHARE_NOTIFICATION_ID);
    }

    //Start service as a foreground service. We dont want the service to be killed in case of low memory
    private void startNotificationForeGround(Notification notification, int ID) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            int foregroundServiceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            
            boolean audioEnabled = audioRecSource != null && !audioRecSource.equals("0");
            boolean hasAudioPermission = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            
            if (audioEnabled && hasAudioPermission) {
                foregroundServiceType |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            
            startForeground(ID, notification, foregroundServiceType);
        } else {
            startForeground(ID, notification);
        }
    }

    //Update existing notification with its ID and new Notification data
    private void updateNotification(Notification notification, int ID) {
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "updateNotification: Updating notification ID " + ID);
            Log.d(Const.TAG, "updateNotification: Notification has " + notification.actions.length + " actions");
            for (int i = 0; i < notification.actions.length; i++) {
                Notification.Action action = notification.actions[i];
                Log.d(Const.TAG, "updateNotification: Action " + i + ": " + action.title);
            }
        }
        getManager().notify(ID, notification);
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "updateNotification: Notification posted");
        }
    }

    private NotificationManager getManager() {
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return mNotificationManager;
    }

    @Override
    public void onDestroy() {
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Recorder service destroyed");
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    //Get user's choices for user choosable settings
    public void getValues() {
        String res = getResolution();
        setWidthHeight(res);
        FPS = Integer.parseInt(prefs.getString(getString(R.string.fps_key), "30"));
        BITRATE = Integer.parseInt(prefs.getString(getString(R.string.bitrate_key), "7130317"));
        audioRecSource = prefs.getString(getString(R.string.audiorec_key), "0");
        
        // Get the save path - either from SAF URI or fallback to File-based
        String saveFileName = getFileSaveName();
        SAVEPATH = getSaveFilePath(saveFileName);
    }
    
    private String getSaveFilePath(String saveFileName) {
        File moviesDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ScreenRecorder");
        if (!moviesDir.exists()) {
            moviesDir.mkdirs();
        }
        String defaultSaveLocation = moviesDir.getAbsolutePath();
        saveLocation = prefs.getString(getString(R.string.savelocation_key), defaultSaveLocation);
        
        // Check if user selected a folder via SAF
        String uriString = prefs.getString(Const.SAVE_LOCATION_URI_KEY, null);
        
        if (uriString != null && !uriString.isEmpty()) {
            try {
                Uri treeUri = Uri.parse(uriString);
                
                // Verify we still have permission to access this URI
                boolean hasPermission = false;
                for (android.content.UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
                    if (permission.getUri().equals(treeUri) && permission.isWritePermission()) {
                        hasPermission = true;
                        break;
                    }
                }
                
                if (!hasPermission) {
                    if (BuildConfig.DEBUG) {
                        Log.w(Const.TAG, "No longer have permission for SAF URI, falling back to file storage");
                    }
                    // Clear the invalid URI
                    prefs.edit().remove(Const.SAVE_LOCATION_URI_KEY).apply();
                } else {
                    DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);
                    
                    if (pickedDir != null && pickedDir.exists() && pickedDir.canWrite()) {
                        // Create the file in the SAF directory
                        DocumentFile newFile = pickedDir.createFile("video/mp4", saveFileName + ".mp4");
                        if (newFile != null && newFile.getUri() != null) {
                            // For MediaRecorder, we need to write to a file descriptor
                            // We'll use a temporary file and then copy it
                            File tempDir = new File(getExternalFilesDir(null), "temp");
                            if (!tempDir.exists()) {
                                tempDir.mkdirs();
                            }
                            String tempPath = tempDir.getAbsolutePath() + File.separator + saveFileName + ".mp4";
                            if (BuildConfig.DEBUG) {
                                Log.d(Const.TAG, "Using temp file path (will copy to SAF): " + tempPath);
                            }
                            // Store the SAF URI for later copying
                            prefs.edit().putString("temp_saf_uri", newFile.getUri().toString()).apply();
                            // Update saveLocation to temp dir for free space check
                            saveLocation = tempDir.getAbsolutePath();
                            return tempPath;
                        }
                    } else {
                        if (BuildConfig.DEBUG) {
                            Log.w(Const.TAG, "SAF directory no longer accessible, falling back to file storage");
                        }
                        // Clear the invalid URI
                        prefs.edit().remove(Const.SAVE_LOCATION_URI_KEY).apply();
                    }
                }
            } catch (Exception e) {
                if (BuildConfig.DEBUG) {
                    Log.e(Const.TAG, "Failed to use SAF location: " + e.getMessage());
                }
                // Clear the problematic URI
                prefs.edit().remove(Const.SAVE_LOCATION_URI_KEY).apply();
            }
        }
        
        // Use File-based storage
        File saveDir = new File(saveLocation);
        if (!saveDir.isDirectory()) {
            saveDir.mkdirs();
        }
        return saveLocation + File.separator + saveFileName + ".mp4";
    }

    /* The PreferenceScreen save values as string and we save the user selected video resolution as
     * WIDTH x HEIGHT. Lets split the string on 'x' and retrieve width and height */
    private void setWidthHeight(String res) {
        String[] widthHeight = res.split("x");
        String orientationPrefs = prefs.getString(getString(R.string.orientation_key), "auto");
        switch (orientationPrefs) {
            case "auto":
                if (screenOrientation == 0 || screenOrientation == 2) {
                    WIDTH = Integer.parseInt(widthHeight[0]);
                    HEIGHT = Integer.parseInt(widthHeight[1]);
                } else {
                    HEIGHT = Integer.parseInt(widthHeight[0]);
                    WIDTH = Integer.parseInt(widthHeight[1]);
                }
                break;
            case "portrait":
                WIDTH = Integer.parseInt(widthHeight[0]);
                HEIGHT = Integer.parseInt(widthHeight[1]);
                break;
            case "landscape":
                HEIGHT = Integer.parseInt(widthHeight[0]);
                WIDTH = Integer.parseInt(widthHeight[1]);
                break;
        }
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Width: " + WIDTH + ",Height:" + HEIGHT);
        }
    }

    //Get the device resolution in pixels
    private String getResolution() {
        DisplayMetrics metrics = new DisplayMetrics();
        window = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        window.getDefaultDisplay().getRealMetrics(metrics);
        DENSITY_DPI = metrics.densityDpi;
        
        int deviceWidth = metrics.widthPixels;
        int deviceHeight = metrics.heightPixels;
        
        String resPref = prefs.getString(getString(R.string.res_key), Integer.toString(deviceWidth));
        
        int width;
        int height;
        
        // Check if user selected "native" resolution (use device's actual resolution)
        if (resPref.equalsIgnoreCase("native")) {
            // Use native device resolution - ensure dimensions are divisible by 16 for encoder compatibility
            width = (deviceWidth / 16) * 16;
            height = (deviceHeight / 16) * 16;
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "Using native resolution: " + width + "x" + height);
            }
        } else {
            // Parse the selected resolution
            width = Integer.parseInt(resPref);
            float aspectRatio = getAspectRatio(metrics);
            height = calculateClosestHeight(width, aspectRatio);
        }
        
        String res = width + "x" + height;
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "resolution service: " + "[Width: "
                    + width + ", Height: " + height + ", Device: " + deviceWidth + "x" + deviceHeight + "]");
        }
        return res;
    }

    private int calculateClosestHeight(int width, float aspectRatio) {
        int calculatedHeight = (int) (width * aspectRatio);
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Calculated width=" + calculatedHeight);
            Log.d(Const.TAG, "Aspect ratio: " + aspectRatio);
        }
        if (calculatedHeight / 16 != 0) {
            int quotient = calculatedHeight / 16;
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, calculatedHeight + " not divisible by 16");
            }

            calculatedHeight = 16 * quotient;

            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "Maximum possible height is " + calculatedHeight);
            }
        }
        return calculatedHeight;
    }

    private float getAspectRatio(DisplayMetrics metrics) {
        float screen_width = metrics.widthPixels;
        float screen_height = metrics.heightPixels;
        float aspectRatio;
        if (screen_width > screen_height) {
            aspectRatio = screen_width / screen_height;
        } else {
            aspectRatio = screen_height / screen_width;
        }
        return aspectRatio;
    }

    //Return filename of the video to be saved formatted as chosen by the user
    private String getFileSaveName() {
        String filename = prefs.getString(getString(R.string.filename_key), "yyyyMMdd_HHmmss");

        //Required to handle preference change
        filename = filename.replace("hh", "HH");
        String prefix = prefs.getString(getString(R.string.fileprefix_key), "recording");
        Date today = Calendar.getInstance().getTime();
        // CRITICAL FIX: Specify Locale.US to ensure consistent filename formatting across all locales
        SimpleDateFormat formatter = new SimpleDateFormat(filename, Locale.US);
        return prefix + "_" + formatter.format(today);
    }

    //Stop and destroy all the objects used for screen recording
    private void destroyMediaProjection() {
        if (mAudioManager != null) {
            mAudioManager.setParameters("screenRecordAudioSource=0");
        }
        try {
            if (mMediaRecorder != null) {
                mMediaRecorder.stop();
            }
            indexFile();
            if (BuildConfig.DEBUG) {
                Log.i(Const.TAG, "MediaProjection Stopped");
            }
        } catch (RuntimeException e) {
            if (BuildConfig.DEBUG) {
                Log.e(Const.TAG, "Fatal exception! Destroying media projection failed." + "\n" + e.getMessage());
            }
            if (SAVEPATH != null && new File(SAVEPATH).delete())
                if (BuildConfig.DEBUG) {
                    Log.d(Const.TAG, "Corrupted file delete successful");
                }
            Toast.makeText(this, getString(R.string.fatal_exception_message), Toast.LENGTH_SHORT).show();
        } finally {
            if (mMediaRecorder != null) {
                mMediaRecorder.reset();
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
                mVirtualDisplay = null;
            }
            if (mMediaProjection != null) {
                mMediaProjection.unregisterCallback(mMediaProjectionCallback);
                mMediaProjection.stop();
                mMediaProjection = null;
            }
            stopSelf();
        }
        isRecording = false;
        isPaused = false;
    }

    /* Its weird that android does not index the files immediately once its created and that causes
     * trouble for user in finding the video in gallery. Let's explicitly announce the file creation
     * to android and index it */
    private void indexFile() {
        // Check if we need to copy to SAF location
        copyToSAFLocationIfNeeded();
        
        // Only scan if file still exists (not deleted after SAF copy)
        File videoFile = new File(SAVEPATH);
        if (!videoFile.exists()) {
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "File moved to SAF location, skipping media scan");
            }
            // Show toast and notification anyway
            Message message = mHandler.obtainMessage();
            message.sendToTarget();
            stopSelf();
            return;
        }
        
        //Create a new ArrayList and add the newly created video file path to it
        ArrayList<String> toBeScanned = new ArrayList<>();
        toBeScanned.add(SAVEPATH);
        String[] toBeScannedStr = new String[toBeScanned.size()];
        toBeScannedStr = toBeScanned.toArray(toBeScannedStr);

        //Request MediaScannerConnection to scan the new file and index it
        MediaScannerConnection.scanFile(this, toBeScannedStr, null, new MediaScannerConnection.OnScanCompletedListener() {

            @Override
            public void onScanCompleted(String path, Uri uri) {
                if (BuildConfig.DEBUG) {
                    Log.i(Const.TAG, "SCAN COMPLETED: " + path);
                }
                //Show toast on main thread
                Message message = mHandler.obtainMessage();
                message.sendToTarget();
                stopSelf();
            }
        });
    }
    
    /**
     * Copy the recorded file to SAF location if user selected a folder via Storage Access Framework
     */
    private void copyToSAFLocationIfNeeded() {
        String safUriString = prefs.getString("temp_saf_uri", null);
        if (safUriString == null || safUriString.isEmpty()) {
            return; // No SAF location selected, file is already in the right place
        }
        
        try {
            Uri safUri = Uri.parse(safUriString);
            File sourceFile = new File(SAVEPATH);
            
            if (!sourceFile.exists()) {
                if (BuildConfig.DEBUG) {
                    Log.e(Const.TAG, "Source file doesn't exist: " + SAVEPATH);
                }
                prefs.edit().remove("temp_saf_uri").apply();
                return;
            }
            
            boolean copySuccess = false;
            
            // Copy file to SAF location
            try (java.io.InputStream in = new java.io.FileInputStream(sourceFile);
                 java.io.OutputStream out = getContentResolver().openOutputStream(safUri)) {
                
                if (out == null) {
                    if (BuildConfig.DEBUG) {
                        Log.e(Const.TAG, "Failed to open output stream for SAF URI");
                    }
                } else {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();
                    
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Successfully copied file to SAF location: " + safUri);
                    }
                    copySuccess = true;
                    
                    // Store the final SAF URI for later use
                    prefs.edit().putString("last_video_saf_uri", safUri.toString()).apply();
                }
                
            } catch (Exception e) {
                if (BuildConfig.DEBUG) {
                    Log.e(Const.TAG, "Failed to copy file to SAF location: " + e.getMessage());
                }
                e.printStackTrace();
            }
            
            // Only delete temp file if copy was successful
            if (copySuccess) {
                if (sourceFile.delete()) {
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Temp file deleted: " + SAVEPATH);
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.w(Const.TAG, "Failed to delete temp file: " + SAVEPATH);
                    }
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.w(Const.TAG, "Copy failed, keeping temp file: " + SAVEPATH);
                }
            }
            
            // Clean up the temp SAF URI
            prefs.edit().remove("temp_saf_uri").apply();
            
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(Const.TAG, "Error in copyToSAFLocationIfNeeded: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    private void stopScreenSharing() {
        if (mVirtualDisplay == null) {
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "Virtual display is null. Screen sharing already stopped");
            }
            return;
        }
        destroyMediaProjection();
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.v(Const.TAG, "Recording Stopped");
            stopScreenSharing();
        }
    }

    /**
     * Broadcast recording state changes to MainActivity
     */
    private void broadcastRecordingState(Const.RecordingState state) {
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Broadcasting recording state: " + state.name());
        }
        
        // Store state in SharedPreferences so MainActivity can restore correct UI when resuming
        if (prefs != null) {
            prefs.edit().putString("recording_state", state.name()).apply();
        }
        
        // Use explicit intent to ensure broadcast is received by our app even in background
        Intent intent = new Intent(Const.RECORDING_STATE_CHANGED);
        intent.putExtra(Const.RECORDING_STATE_EXTRA, state.name());
        // Set package to make it explicit
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "Broadcast sent for state: " + state.name() + " to package: " + getPackageName());
        }
    }
}