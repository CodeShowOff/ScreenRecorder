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

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.orpheusdroid.screenrecorder.Const;
import com.orpheusdroid.screenrecorder.R;
import com.orpheusdroid.screenrecorder.adapter.Apps;
import com.orpheusdroid.screenrecorder.adapter.AppsListFragmentAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dialog fragment for AppPickerPreference.
 * Handles the app list loading and selection.
 */
public class AppPickerPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat
        implements AppsListFragmentAdapter.OnItemClicked {

    private static final String ARG_KEY = "key";

    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private ArrayList<Apps> apps = new ArrayList<>();
    
    @Nullable
    private String selectedPackageName;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Creates a new instance of this dialog fragment.
     *
     * @param key The preference key
     * @return New instance of AppPickerPreferenceDialogFragmentCompat
     */
    @NonNull
    public static AppPickerPreferenceDialogFragmentCompat newInstance(@NonNull String key) {
        AppPickerPreferenceDialogFragmentCompat fragment = new AppPickerPreferenceDialogFragmentCompat();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    private AppPickerPreference getAppPickerPreference() {
        return (AppPickerPreference) getPreference();
    }

    @Override
    protected void onBindDialogView(@NonNull View view) {
        super.onBindDialogView(view);

        progressBar = view.findViewById(R.id.appsProgressBar);
        recyclerView = view.findViewById(R.id.appsRecyclerView);

        init();
    }

    private void init() {
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);

        // Load apps in background
        loadApps();
    }

    private void loadApps() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        executor.execute(() -> {
            PackageManager pm = requireContext().getPackageManager();
            apps.clear();

            List<PackageInfo> packages = pm.getInstalledPackages(0);
            String currentSelection = getAppPickerPreference() != null 
                    ? getAppPickerPreference().getSelectedPackageName() 
                    : "none";

            for (PackageInfo packageInfo : packages) {
                // Check if the app has launcher intent and exclude our own app
                if (!requireContext().getPackageName().equals(packageInfo.packageName)
                        && pm.getLaunchIntentForPackage(packageInfo.packageName) != null) {

                    Apps app = new Apps(
                            packageInfo.applicationInfo.loadLabel(pm).toString(),
                            packageInfo.packageName,
                            packageInfo.applicationInfo.loadIcon(pm)
                    );

                    // Mark the previously selected app
                    app.setSelectedApp(currentSelection.equals(packageInfo.packageName));
                    apps.add(app);
                }
            }
            
            Collections.sort(apps);

            // Update UI on main thread
            if (getActivity() != null) {
                getActivity().runOnUiThread(this::updateUI);
            }
        });
    }

    private void updateUI() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }

        AppsListFragmentAdapter adapter = new AppsListFragmentAdapter(apps);
        recyclerView.setAdapter(adapter);
        adapter.setOnClick(this);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        // Selection is handled via onItemClick, no action needed here
    }

    @Override
    public void onItemClick(int position) {
        if (position >= 0 && position < apps.size()) {
            selectedPackageName = apps.get(position).getPackageName();
            Log.d(Const.TAG, "App selected: " + selectedPackageName);

            AppPickerPreference preference = getAppPickerPreference();
            if (preference != null && preference.callChangeListener(selectedPackageName)) {
                preference.setSelectedPackageName(selectedPackageName);
            }

            // Dismiss dialog after selection
            dismiss();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
