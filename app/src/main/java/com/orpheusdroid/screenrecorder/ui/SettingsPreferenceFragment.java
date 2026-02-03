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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.snackbar.Snackbar;
import com.orpheusdroid.screenrecorder.BuildConfig;
import com.orpheusdroid.screenrecorder.Const;
import com.orpheusdroid.screenrecorder.R;
import com.orpheusdroid.screenrecorder.folderpicker.FolderChooser;
import com.orpheusdroid.screenrecorder.folderpicker.FolderPickerLauncher;
import com.orpheusdroid.screenrecorder.folderpicker.OnDirectorySelectedListener;
import com.orpheusdroid.screenrecorder.interfaces.PermissionResultListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class SettingsPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener
        , PermissionResultListener, OnDirectorySelectedListener, FolderPickerLauncher {

    SharedPreferences prefs;
    private ListPreference res;
    private ListPreference recaudio;
    private FolderChooser dirChooser;
    private MainActivity activity;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        super.onDisplayPreferenceDialog(preference);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        androidx.recyclerview.widget.RecyclerView recyclerView = getListView();
        if (recyclerView != null) {
            recyclerView.setClipToPadding(false);
            int paddingBottom = (int) (180 * getResources().getDisplayMetrics().density);
            recyclerView.setPadding(0, 0, 0, paddingBottom);
            
            recyclerView.addOnChildAttachStateChangeListener(new androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(@NonNull View view) {
                    view.setPadding(0, view.getPaddingTop(), 0, view.getPaddingBottom());
                }

                @Override
                public void onChildViewDetachedFromWindow(@NonNull View view) {
                }
            });
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);

        setPermissionListener();

        File moviesDir = getActivity().getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (moviesDir == null) {
            moviesDir = new File(getActivity().getFilesDir(), "Movies");
        }
        String defaultSaveLoc = moviesDir.getAbsolutePath();

        prefs = getPreferenceScreen().getSharedPreferences();
        res = (ListPreference) findPreference(getString(R.string.res_key));
        ListPreference fps = (ListPreference) findPreference(getString(R.string.fps_key));
        ListPreference bitrate = (ListPreference) findPreference(getString(R.string.bitrate_key));
        recaudio = (ListPreference) findPreference(getString(R.string.audiorec_key));
        ListPreference filenameFormat = (ListPreference) findPreference(getString(R.string.filename_key));
        EditTextPreference filenamePrefix = (EditTextPreference) findPreference(getString(R.string.fileprefix_key));
        dirChooser = (FolderChooser) findPreference(getString(R.string.savelocation_key));
        dirChooser.setCurrentDir(getValue(getString(R.string.savelocation_key), defaultSaveLoc));

        ListPreference orientation = (ListPreference) findPreference(getString(R.string.orientation_key));
        orientation.setSummary(orientation.getEntry());

        checkNativeRes(res);
        updateResolution(res);
        fps.setSummary(getValue(getString(R.string.fps_key), "30"));
        float bps = bitsToMb(Integer.parseInt(getValue(getString(R.string.bitrate_key), "7130317")));
        bitrate.setSummary(bps + " Mbps");
        dirChooser.setSummary(getValue(getString(R.string.savelocation_key), defaultSaveLoc));
        filenameFormat.setSummary(getFileSaveFormat());
        filenamePrefix.setSummary(getValue(getString(R.string.fileprefix_key), "recording"));

        setAudioSettingsSummary();

        checkAudioRecPermission();

        dirChooser.setOnDirectorySelectedListener(this);
    }

    public int getBestSampleRate() {
        AudioManager am = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        String sampleRateString = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        int samplingRate = (sampleRateString == null) ? 44100 : Integer.parseInt(sampleRateString);
        return samplingRate;
    }

    private void checkNativeRes(ListPreference res) {

        String[] allEntries = getResources().getStringArray(R.array.resolutionsArray);
        String[] allValues = getResources().getStringArray(R.array.resolutionValues);

        int nativeWidth;
        try {
            nativeWidth = Integer.parseInt(getNativeRes());
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.w(Const.TAG, "Failed to parse native resolution");
            }
            return;
        }

        ArrayList<String> filteredEntries = new ArrayList<>();
        ArrayList<String> filteredValues = new ArrayList<>();

        boolean hasValuesChanged = false;

        for (int i = 0; i < allValues.length && i < allEntries.length; i++) {
            String value = allValues[i];
            String entry = allEntries[i];

            if (value == null) {
                continue;
            }

            if (value.equalsIgnoreCase("native")) {
                filteredEntries.add(entry);
                filteredValues.add(value);
                continue;
            }

            try {
                int width = Integer.parseInt(value);
                if (width <= nativeWidth) {
                    filteredEntries.add(entry);
                    filteredValues.add(value);
                } else {
                    hasValuesChanged = true;
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Removed resolution option " + value + " (nativeWidth=" + nativeWidth + ")");
                    }
                }
            } catch (NumberFormatException ex) {
                filteredEntries.add(entry);
                filteredValues.add(value);
            }
        }

        if (hasValuesChanged) {
            res.setEntries(filteredEntries.toArray(new CharSequence[0]));
            res.setEntryValues(filteredValues.toArray(new CharSequence[0]));
        }
    }

    private void checkAudioRecPermission() {
        String value = recaudio.getValue();
        switch (value) {
            case "1":
                requestAudioPermission(Const.AUDIO_REQUEST_CODE);
                break;
            case "2":
                requestAudioPermission(Const.INTERNAL_AUDIO_REQUEST_CODE);
                break;
        }
        recaudio.setSummary(recaudio.getEntry());
    }

    private void updateResolution(ListPreference pref) {
        String resolution = getValue(getString(R.string.res_key), getNativeRes());
        pref.setSummary(resolution + "P");
    }

    private String getNativeRes() {
        DisplayMetrics metrics = getRealDisplayMetrics();
        return String.valueOf(getScreenWidth(metrics));
    }

    private void updateScreenAspectRatio() {
        CharSequence[] entriesValues = getResolutionEntriesValues();
        res.setEntries(getResolutionEntries(entriesValues));
        res.setEntryValues(entriesValues);
    }

    private CharSequence[] getResolutionEntriesValues() {

        ArrayList<String> entrieValues = buildEntries(R.array.resolutionValues);

        String[] entriesArray = new String[entrieValues.size()];
        return entrieValues.toArray(entriesArray);
    }

    private CharSequence[] getResolutionEntries(CharSequence[] entriesValues) {
        ArrayList<String> entries = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.resolutionsArray)));
        ArrayList<String> newEntries = new ArrayList<>();
        for (CharSequence values : entriesValues) {
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "res entries:" + values.toString());
            }
            
            if (values.toString().equalsIgnoreCase("native")) {
                for (String entry : entries) {
                    if (entry.toLowerCase().contains("native")) {
                        newEntries.add(entry);
                        break;
                    }
                }
                continue;
            }
            
            for (String entry : entries) {
                if (entry.contains(values))
                    newEntries.add(entry);
            }
            if (BuildConfig.DEBUG) {
                Log.d(Const.TAG, "res entries: split " + values.toString().split("P")[0] + " val: ");
            }
        }
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "res entries" + newEntries.toString());
        }
        String[] entriesArray = new String[newEntries.size()];
        return newEntries.toArray(entriesArray);
    }

    private ArrayList<String> buildEntries(int resID) {
        DisplayMetrics metrics = getRealDisplayMetrics();
        int deviceWidth = getScreenWidth(metrics);
        ArrayList<String> entries = new ArrayList<>(Arrays.asList(getResources().getStringArray(resID)));
        Iterator<String> entriesIterator = entries.iterator();
        while (entriesIterator.hasNext()) {
            String width = entriesIterator.next();
            if (width.equalsIgnoreCase("native")) {
                continue;
            }
            try {
                if (deviceWidth < Integer.parseInt(width)) {
                    entriesIterator.remove();
                }
            } catch (NumberFormatException e) {
                if (BuildConfig.DEBUG) {
                    Log.d(Const.TAG, "Keeping non-numeric resolution: " + width);
                }
            }
        }
        if (!entries.contains("" + deviceWidth))
            entries.add("" + deviceWidth);
        return entries;
    }

    private DisplayMetrics getRealDisplayMetrics(){
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager window = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
        window.getDefaultDisplay().getRealMetrics(metrics);
        return metrics;
    }

    private void setAudioSettingsSummary() {
        // Audio settings removed - using defaults
    }

    private int getScreenWidth(DisplayMetrics metrics) {
        return metrics.widthPixels;
    }

    private int getScreenHeight(DisplayMetrics metrics) {
        return metrics.heightPixels;
    }

    private void setPermissionListener() {
        if (getActivity() != null && getActivity() instanceof MainActivity) {
            activity = (MainActivity) getActivity();
            activity.setPermissionResultListener(this);
        }
    }

    private String getValue(String key, String defVal) {
        return prefs.getString(key, defVal);
    }

    private float bitsToMb(float bps) {
        return bps / (1024 * 1024);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == Const.FOLDER_PICKER_REQUEST_CODE && resultCode == getActivity().RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri treeUri = data.getData();
                
                // Take persistable permissions
                final int takeFlags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getActivity().getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                
                // Notify the FolderChooser
                if (dirChooser != null) {
                    dirChooser.onFolderSelected(treeUri);
                }
            }
        }
    }

    @Override
    public void launchFolderPicker(Intent intent) {
        startActivityForResult(intent, Const.FOLDER_PICKER_REQUEST_CODE);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        Preference pref = findPreference(s);
        if (pref == null) return;
        String key = pref.getKey();
        if (key == null) return;
        
        if (key.equals(getString(R.string.res_key))) {
            updateResolution((ListPreference) pref);
        } else if (key.equals(getString(R.string.fps_key))) {
            String fps = String.valueOf(getValue(getString(R.string.fps_key), "30"));
            pref.setSummary(fps);
        } else if (key.equals(getString(R.string.bitrate_key))) {
            float bps = bitsToMb(Integer.parseInt(getValue(getString(R.string.bitrate_key), "7130317")));
            pref.setSummary(bps + " Mbps");
            if (bps > 12)
                Toast.makeText(getActivity(), R.string.toast_message_bitrate_high_warning, Toast.LENGTH_SHORT).show();
        } else if (key.equals(getString(R.string.filename_key))) {
            pref.setSummary(getFileSaveFormat());
        } else if (key.equals(getString(R.string.audiorec_key))) {
            switch (recaudio.getValue()) {
                case "0":
                    break;
                case "1":
                    requestAudioPermission(Const.AUDIO_REQUEST_CODE);
                    break;
                case "2":
                    requestAudioPermission(Const.INTERNAL_AUDIO_REQUEST_CODE);
                    break;
                default:
                    recaudio.setValue("0");
                    break;
            }
            pref.setSummary(((ListPreference) pref).getEntry());
            setAudioSettingsSummary();
        } else if (key.equals(getString(R.string.fileprefix_key))) {
            EditTextPreference etp = (EditTextPreference) pref;
            etp.setSummary(etp.getText());
            ListPreference filename = (ListPreference) findPreference(getString(R.string.filename_key));
            filename.setSummary(getFileSaveFormat());
        } else if (key.equals(getString(R.string.orientation_key))) {
            pref.setSummary(((ListPreference) pref).getEntry());
        }
    }

    public String getFileSaveFormat() {
        String filename = prefs.getString(getString(R.string.filename_key), "yyyyMMdd_HHmmss").replace("hh", "HH");
        String prefix = prefs.getString(getString(R.string.fileprefix_key), "recording");
        return prefix + "_" + filename;
    }

    public void requestAudioPermission(int requestCode) {
        if (activity != null) {
            activity.requestPermissionAudio(requestCode);
        }
    }

    private void showSnackbar() {
        Snackbar.make(getActivity().findViewById(R.id.fab), R.string.snackbar_storage_permission_message,
                Snackbar.LENGTH_INDEFINITE).setAction(R.string.snackbar_storage_permission_action_enable,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (activity != null){
                            activity.requestPermissionStorage();
                        }
                    }
                }).show();
    }

    private void showPermissionDeniedDialog(){
        new AlertDialog.Builder(activity)
                .setTitle(R.string.alert_permission_denied_title)
                .setMessage(R.string.alert_permission_denied_message)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (activity != null){
                            activity.requestPermissionStorage();
                        }
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        showSnackbar();
                    }
                })
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setCancelable(false)
                .create().show();
    }

    @Override
    public void onPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case Const.EXTDIR_REQUEST_CODE:
            case Const.MEDIA_VIDEO_REQUEST_CODE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_DENIED)) {
                    if (BuildConfig.DEBUG) {
                        Log.d(Const.TAG, "Storage/Media permission denied. Requesting again");
                    }
                    dirChooser.setEnabled(false);
                    showPermissionDeniedDialog();
                } else if((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)){
                    dirChooser.setEnabled(true);
                }
                return;
            case Const.AUDIO_REQUEST_CODE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    recaudio.setValue("1");
                } else {
                    recaudio.setValue("0");
                }
                recaudio.setSummary(recaudio.getEntry());
                return;
            case Const.INTERNAL_AUDIO_REQUEST_CODE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    recaudio.setValue("2");
                } else {
                    recaudio.setValue("0");
                }
                recaudio.setSummary(recaudio.getEntry());
                return;
            default:
                if (BuildConfig.DEBUG) {
                    Log.d(Const.TAG, "Unknown permission request with request code: " + requestCode);
                }
        }
    }

    @Override
    public void onDirectorySelected() {
        if (BuildConfig.DEBUG) {
            Log.d(Const.TAG, "In settings fragment");
        }
        if (getActivity() != null && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onDirectoryChanged();
        }
    }
}
