package com.orpheusdroid.screenrecorder.folderpicker;

import android.content.Intent;

/**
 * Interface for launching the folder picker from a Fragment or Activity
 */
public interface FolderPickerLauncher {
    void launchFolderPicker(Intent intent);
}
