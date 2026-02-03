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

package com.orpheusdroid.screenrecorder.folderpicker;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.orpheusdroid.screenrecorder.Const;
import com.orpheusdroid.screenrecorder.R;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Custom Preference for choosing a folder.
 * Migrated to AndroidX Preference with modern Java patterns.
 */
public class FolderChooser extends Preference implements View.OnClickListener,
        DirectoryRecyclerAdapter.OnDirectoryClickedListener, AdapterView.OnItemSelectedListener {

    private static final Pattern SD_CARD_PATTERN = Pattern.compile(".*[0-9a-f]{4}[-][0-9a-f]{4}", Pattern.CASE_INSENSITIVE);

    @Nullable
    private OnDirectorySelectedListener onDirectorySelectedListener;
    private RecyclerView recyclerView;
    private TextView tvCurrentDir;
    private TextView tvEmpty;
    @Nullable
    private File currentDir;
    @NonNull
    private ArrayList<File> directories = new ArrayList<>();
    @Nullable
    private AlertDialog folderDialog;
    @Nullable
    private AlertDialog newDirDialog;
    @Nullable
    private DirectoryRecyclerAdapter adapter;
    private Spinner spinner;
    @NonNull
    private final List<Storages> storages = new ArrayList<>();
    private boolean isExternalStorageSelected = false;
    private SharedPreferences prefs;

    public FolderChooser(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        Log.d(Const.TAG, "Constructor called");
        initialize(context);
    }

    public FolderChooser(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    @NonNull
    private File getDefaultDirectory(@NonNull Context context) {
        // Use public Movies directory so videos are visible in gallery
        File moviesDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ScreenRecorder");
        if (!moviesDir.exists()) {
            moviesDir.mkdirs();
        }
        return moviesDir;
    }

    private void initialize(@NonNull Context context) {
        setPersistent(true);
        
        // Get default directory using context (safe for scoped storage)
        File defaultDir = getDefaultDirectory(context);
        
        // Initialize storages - use public external storage as base for folder navigation
        String basePath = Environment.getExternalStorageDirectory().getPath();
        storages.add(new Storages(basePath, Storages.StorageType.Internal));
        
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        // Get persisted value or use default
        String persistedValue = getPersistedString(defaultDir.getPath());
        currentDir = new File(persistedValue);
        
        // Ensure directory exists and is writable
        if (!currentDir.exists()) {
            if (!currentDir.mkdirs()) {
                Log.e(Const.TAG, "Failed to create default directory, falling back");
                currentDir = defaultDir;
                if (!currentDir.exists()) {
                    currentDir.mkdirs();
                }
            }
        }
        
        // Don't check canWrite() as it's unreliable on Android 10+ with scoped storage
        
        setSummary(persistedValue);
        Log.d(Const.TAG, "Persisted String is: " + persistedValue);
        
        String[] externalDirs = getExternalStorageDirectories();
        Log.d(Const.TAG, "Total storages: " + externalDirs.length);
        for (String path : externalDirs) {
            Log.d(Const.TAG, "storage path: " + path);
        }
    }

    @NonNull
    private File getCurrentDir() {
        if (currentDir == null) {
            currentDir = getDefaultDirectory(getContext());
        }
        return currentDir;
    }

    @Override
    protected void onClick() {
        super.onClick();
        // Launch Storage Access Framework folder picker
        launchSAFFolderPicker();
    }

    /**
     * Launch the Android Storage Access Framework folder picker
     * This allows users to select any folder including DCIM, Movies, etc.
     */
    public void launchSAFFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | 
                       Intent.FLAG_GRANT_READ_URI_PERMISSION |
                       Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        
        // Set initial location to Movies folder if possible (Android 8.0+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                // Try to set initial location to Movies folder
                Uri moviesUri = DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:Movies");
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, moviesUri);
            } catch (Exception e) {
                // If it fails, just continue without initial URI
                Log.w(Const.TAG, "Could not set initial URI: " + e.getMessage());
            }
        }
        
        // The activity result will be handled in SettingsPreferenceFragment
        if (onDirectorySelectedListener != null && onDirectorySelectedListener instanceof FolderPickerLauncher) {
            ((FolderPickerLauncher) onDirectorySelectedListener).launchFolderPicker(intent);
        } else {
            Log.e(Const.TAG, "Cannot launch folder picker - listener is not a FolderPickerLauncher");
        }
    }

    /**
     * Called when a folder is selected via SAF
     * @param uri The URI of the selected folder
     */
    public void onFolderSelected(Uri uri) {
        if (uri == null) {
            return;
        }
        
        // Store the URI persistently
        prefs.edit().putString(Const.SAVE_LOCATION_URI_KEY, uri.toString()).apply();
        
        // Get a user-friendly display name from the URI
        String displayPath = getDisplayPathFromUri(uri);
        
        setSummary(displayPath);
        persistString(displayPath);
        
        if (onDirectorySelectedListener != null) {
            onDirectorySelectedListener.onDirectorySelected();
        }
    }
    
    /**
     * Convert a SAF tree URI to a user-friendly display path
     */
    private String getDisplayPathFromUri(Uri uri) {
        String path = uri.getPath();
        if (path == null) {
            return uri.toString();
        }
        
        // Parse the document ID from the path
        // Format: /tree/primary:DCIM or /tree/1234-5678:Movies
        if (path.contains("/tree/")) {
            String[] parts = path.split("/tree/");
            if (parts.length > 1) {
                String docId = parts[1];
                
                // Handle primary storage
                if (docId.startsWith("primary:")) {
                    String folder = docId.substring(8); // Remove "primary:"
                    return "/storage/emulated/0/" + folder;
                }
                
                // Handle SD card storage (format: 1234-5678:folder)
                if (docId.matches("^[0-9A-F]{4}-[0-9A-F]{4}:.*")) {
                    String[] sdParts = docId.split(":", 2);
                    if (sdParts.length == 2) {
                        return "/storage/" + sdParts[0] + "/" + sdParts[1];
                    }
                }
                
                // Fallback: just use the document ID
                return docId.replace(":", "/");
            }
        }
        
        // Fallback to the raw path
        return path;
    }

    // Keep the old method for backward compatibility but show the new picker
    private void showFolderChooserDialog() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.director_chooser, null);

        generateFoldersList();
        initView(view);
        initRecyclerView();

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> onDialogClosed(true))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> onDialogClosed(false));

        folderDialog = builder.create();
        folderDialog.show();
    }

    private void onDialogClosed(boolean positiveResult) {
        if (!positiveResult) {
            return;
        }
        
        File dir = getCurrentDir();
        Log.d(Const.TAG, "Directory chosen: " + dir.getPath());
        
        // Don't check canWrite() as it's unreliable on Android 10+ with scoped storage
        // The actual write permission will be tested when recording starts
        
        persistString(dir.getPath());
        if (onDirectorySelectedListener != null) {
            onDirectorySelectedListener.onDirectorySelected();
        }
        setSummary(dir.getPath());
    }

    private void initRecyclerView() {
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                getContext(), 
                RecyclerView.VERTICAL, 
                false
        );
        recyclerView.setLayoutManager(layoutManager);
        
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(
                getContext(), 
                layoutManager.getOrientation()
        );
        recyclerView.addItemDecoration(dividerItemDecoration);
        
        if (!isDirectoryEmpty()) {
            adapter = new DirectoryRecyclerAdapter(this, directories);
            recyclerView.setAdapter(adapter);
        }
        tvCurrentDir.setText(getCurrentDir().getPath());
    }

    private boolean isDirectoryEmpty() {
        boolean isEmpty = directories.isEmpty();
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        return isEmpty;
    }

    private void generateFoldersList() {
        File dir = getCurrentDir();
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            Log.d(Const.TAG, "Directory created: " + created);
        }
        
        File[] dirArray = dir.listFiles(new DirectoryFilter());
        directories = (dirArray != null) 
                ? new ArrayList<>(Arrays.asList(dirArray)) 
                : new ArrayList<>();
        
        // Sort directories by name (case-insensitive)
        Collections.sort(directories, (f1, f2) -> 
                f1.getName().compareToIgnoreCase(f2.getName()));
        
        Log.d(Const.TAG, "Directory size: " + directories.size());
    }

    private void initView(@NonNull View view) {
        ImageButton btnUp = view.findViewById(R.id.nav_up);
        ImageButton btnCreateDir = view.findViewById(R.id.create_dir);
        tvCurrentDir = view.findViewById(R.id.tv_selected_dir);
        recyclerView = view.findViewById(R.id.rv);
        tvEmpty = view.findViewById(R.id.tv_empty);
        spinner = view.findViewById(R.id.storageSpinner);
        
        btnUp.setOnClickListener(this);
        btnCreateDir.setOnClickListener(this);
        
        List<String> storageLabels = new ArrayList<>();
        for (Storages storage : storages) {
            String label = (storage.getType() == Storages.StorageType.Internal) 
                    ? getContext().getString(R.string.internal_storage)
                    : getContext().getString(R.string.removable_storage);
            storageLabels.add(label);
        }
        
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(
                getContext(), 
                android.R.layout.simple_spinner_item, 
                storageLabels
        );
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(dataAdapter);
        spinner.setOnItemSelectedListener(this);
    }

    private void changeDirectory(@NonNull File file) {
        // Check if directory is accessible
        if (!file.exists()) {
            Log.w(Const.TAG, "Directory doesn't exist: " + file.getPath());
            // Try to create it
            if (!file.mkdirs()) {
                Toast.makeText(getContext(), 
                        "Cannot access directory: " + file.getName(), 
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        if (!file.canRead()) {
            Toast.makeText(getContext(), 
                    "Cannot read directory: " + file.getName(), 
                    Toast.LENGTH_SHORT).show();
            Log.w(Const.TAG, "Directory not readable: " + file.getPath());
            return;
        }
        
        currentDir = file;
        Log.d(Const.TAG, "Changed directory to: " + file.getPath() + 
                " | Writable: " + file.canWrite());
        generateFoldersList();
        
        if (!isDirectoryEmpty()) {
            adapter = new DirectoryRecyclerAdapter(this, directories);
            recyclerView.swapAdapter(adapter, true);
        }
        tvCurrentDir.setText(getCurrentDir().getPath());
    }

    public void setCurrentDir(@NonNull String path) {
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            this.currentDir = dir;
            Log.d(Const.TAG, "Directory set to: " + path);
        } else {
            createFolder(dir.getPath());
            Log.d(Const.TAG, "Directory created: " + path);
        }
    }

    public void setOnDirectorySelectedListener(@Nullable OnDirectorySelectedListener listener) {
        this.onDirectorySelectedListener = listener;
    }

    private void showNewDirDialog(@Nullable Bundle savedState) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.directory_chooser_edit_text, null);
        final EditText input = view.findViewById(R.id.et_new_folder);
        
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Not needed
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (newDirDialog != null) {
                    Button positiveButton = newDirDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    positiveButton.setEnabled(!s.toString().trim().isEmpty());
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setTitle(R.string.alert_title_create_folder)
                .setMessage(R.string.alert_message_create_folder)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    String dirName = input.getText().toString().trim();
                    if (!dirName.isEmpty()) {
                        createFolder(dirName);
                    }
                });

        newDirDialog = builder.create();
        if (savedState != null) {
            newDirDialog.onRestoreInstanceState(savedState);
        }
        newDirDialog.show();
        
        Button positiveButton = newDirDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(!input.getText().toString().trim().isEmpty());
    }

    private boolean createFolder(@NonNull String dirName) {
        File dir = getCurrentDir();
        
        // Check if we have the necessary permissions on Android 11+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(getContext(), 
                        "Please grant 'All files access' permission in Settings to create folders.", 
                        Toast.LENGTH_LONG).show();
                Log.e(Const.TAG, "MANAGE_EXTERNAL_STORAGE permission not granted");
                return false;
            }
        }
        
        String externalStoragePath = Environment.getExternalStorageDirectory().getPath();
        File newDir = dirName.contains(externalStoragePath) 
                ? new File(dirName) 
                : new File(dir, dirName);
        
        if (newDir.exists()) {
            Toast.makeText(getContext(), 
                    R.string.directory_already_exists, 
                    Toast.LENGTH_SHORT).show();
            changeDirectory(newDir);
            return false;
        }

        // Try to create the directory
        boolean created = newDir.mkdirs();
        if (!created) {
            // Provide more detailed error message
            String errorMsg = "Cannot create folder. ";
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                errorMsg += "Use app-specific folders for storage on Android 11+.";
            } else {
                errorMsg += "Check storage permissions.";
            }
            Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
            Log.e(Const.TAG, "Failed to create directory: " + newDir.getPath() + 
                    " | Parent writable: " + dir.canWrite() + 
                    " | Parent exists: " + dir.exists());
            return false;
        }

        changeDirectory(newDir);
        return true;
    }

    @Override
    public void onClick(@NonNull View view) {
        int viewId = view.getId();
        
        if (viewId == R.id.nav_up) {
            navigateUp();
        } else if (viewId == R.id.create_dir) {
            showNewDirDialog(null);
        }
    }

    private void navigateUp() {
        File dir = getCurrentDir();
        String parentPath = dir.getParent();
        if (parentPath == null) {
            return;
        }
        
        File parentDirectory = new File(parentPath);
        Log.d(Const.TAG, "Navigating up to: " + parentDirectory.getPath());
        
        if (isExternalStorageSelected) {
            changeExternalDirectory(parentDirectory);
        } else {
            String internalStoragePath = storages.isEmpty() ? "" : storages.get(0).getPath();
            if (parentDirectory.getPath().contains(internalStoragePath)) {
                changeDirectory(parentDirectory);
            }
        }
    }

    @NonNull
    public String[] getExternalStorageDirectories() {
        List<String> results = new ArrayList<>();
        File[] externalDirs = getContext().getExternalFilesDirs(null);
        
        if (externalDirs == null) {
            return new String[]{Environment.getExternalStorageDirectory().getAbsolutePath()};
        }
        
        String internalRoot = Environment.getExternalStorageDirectory()
                .getAbsolutePath()
                .toLowerCase(Locale.ROOT);

        for (File file : externalDirs) {
            if (file == null) {
                continue;
            }
            
            String[] pathParts = file.getPath().split("/Android");
            if (pathParts.length == 0) {
                continue;
            }
            String path = pathParts[0];

            // Skip internal storage
            if (path.toLowerCase(Locale.ROOT).startsWith(internalRoot)) {
                continue;
            }

            // Only add removable storage
            if (Environment.isExternalStorageRemovable(file)) {
                results.add(path);
            }
        }

        // Filter to only include actual SD card paths (matching pattern like "XXXX-XXXX")
        results.removeIf(path -> {
            boolean isValidSdCard = SD_CARD_PATTERN.matcher(path.toLowerCase(Locale.ROOT)).matches();
            if (!isValidSdCard) {
                Log.d(Const.TAG, path + " might not be external SD card");
            }
            return !isValidSdCard;
        });

        // Always include internal storage
        results.add(Environment.getExternalStorageDirectory().getAbsolutePath());
        
        return results.toArray(new String[0]);
    }

    private void changeExternalDirectory(@NonNull File parentDirectory) {
        if (storages.size() < 2) {
            return;
        }
        
        String externalBaseDir = getRemovableSDPath(storages.get(1).getPath());
        String parentPath = parentDirectory.getPath();
        
        if (!parentPath.contains(externalBaseDir)) {
            return;
        }
        
        if (parentDirectory.canWrite()) {
            changeDirectory(parentDirectory);
        } else {
            Toast.makeText(getContext(), 
                    R.string.external_storage_dir_not_writable, 
                    Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    private String getRemovableSDPath(@NonNull String pathSD) {
        int androidIndex = pathSD.indexOf("Android");
        if (androidIndex <= 0) {
            Log.w(Const.TAG, "Invalid SD path format: " + pathSD);
            return pathSD;
        }
        
        String basePath = pathSD.substring(0, androidIndex - 1);
        Log.d(Const.TAG, "External Base Dir: " + basePath);
        return basePath;
    }

    @Override
    public void onDirectoryClicked(@NonNull File directory) {
        changeDirectory(directory);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= storages.size()) {
            return;
        }
        
        Storages selectedStorage = storages.get(position);
        Log.d(Const.TAG, "Selected storage: " + selectedStorage.getPath());
        
        isExternalStorageSelected = (selectedStorage.getType() == Storages.StorageType.External);
        
        if (isExternalStorageSelected && !prefs.getBoolean(Const.ALERT_EXTR_STORAGE_CB_KEY, false)) {
            showExtDirAlert();
        }
        
        changeDirectory(new File(selectedStorage.getPath()));
    }

    private void showExtDirAlert() {
        View checkBoxView = View.inflate(getContext(), R.layout.alert_checkbox, null);
        final CheckBox checkBox = checkBoxView.findViewById(R.id.donot_warn_cb);
        
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.alert_ext_dir_warning_title)
                .setMessage(R.string.alert_ext_dir_warning_message)
                .setView(checkBoxView)
                .setNeutralButton(android.R.string.ok, (dialog, which) -> {
                    if (checkBox.isChecked()) {
                        prefs.edit()
                                .putBoolean(Const.ALERT_EXTR_STORAGE_CB_KEY, true)
                                .apply();
                    }
                })
                .create()
                .show();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // No action needed
    }

    /**
     * FileFilter that accepts only visible directories.
     */
    private static class DirectoryFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            return file != null && file.isDirectory() && !file.isHidden();
        }
    }
}
