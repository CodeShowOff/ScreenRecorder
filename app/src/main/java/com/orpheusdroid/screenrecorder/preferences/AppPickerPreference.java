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

package com.orpheusdroid.screenrecorder.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

import com.orpheusdroid.screenrecorder.R;

/**
 * Custom DialogPreference for selecting an app from the installed apps list.
 * Modernized to use AndroidX DialogPreference.
 */
public class AppPickerPreference extends DialogPreference {

    private static final String DEFAULT_VALUE = "none";
    
    @NonNull
    private String selectedPackageName = DEFAULT_VALUE;

    public AppPickerPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public AppPickerPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public AppPickerPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AppPickerPreference(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        setPersistent(true);
        setDialogLayoutResource(R.layout.layout_apps_list_preference);
    }

    @Override
    protected Object onGetDefaultValue(@NonNull TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        String def = (defaultValue instanceof String) ? (String) defaultValue : DEFAULT_VALUE;
        selectedPackageName = getPersistedString(def);
    }

    /**
     * Gets the dialog layout resource.
     */
    @Override
    public int getDialogLayoutResource() {
        return R.layout.layout_apps_list_preference;
    }

    /**
     * Gets the currently selected package name.
     */
    @NonNull
    public String getSelectedPackageName() {
        return selectedPackageName;
    }

    /**
     * Sets and persists the selected package name.
     */
    public void setSelectedPackageName(@NonNull String packageName) {
        selectedPackageName = packageName;
        persistString(packageName);
        notifyChanged();
    }

    /**
     * Checks if a specific package is the currently selected app.
     */
    public boolean isSelectedApp(@NonNull String packageName) {
        return selectedPackageName.equals(packageName);
    }
}
