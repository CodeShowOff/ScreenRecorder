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

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.orpheusdroid.screenrecorder.Const;
import com.orpheusdroid.screenrecorder.R;

import java.io.File;
import java.util.List;

/**
 * RecyclerView adapter for displaying directory listings.
 * Modernized with proper nullability annotations and lambda expressions.
 */
class DirectoryRecyclerAdapter extends RecyclerView.Adapter<DirectoryRecyclerAdapter.ItemViewHolder> {

    @NonNull
    private final OnDirectoryClickedListener listener;
    @NonNull
    private final List<File> directories;

    DirectoryRecyclerAdapter(@NonNull OnDirectoryClickedListener listener, 
                             @NonNull List<File> directories) {
        this.listener = listener;
        this.directories = directories;
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.content_directory_chooser, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        File directory = directories.get(position);
        holder.directoryName.setText(directory.getName());
        
        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                File clickedDir = directories.get(adapterPosition);
                Log.d(Const.TAG, "Directory clicked: " + clickedDir.getPath());
                listener.onDirectoryClicked(clickedDir);
            }
        });
    }

    @Override
    public int getItemCount() {
        return directories.size();
    }

    /**
     * Callback interface for directory click events.
     */
    interface OnDirectoryClickedListener {
        void onDirectoryClicked(@NonNull File directory);
    }

    /**
     * ViewHolder for directory items.
     */
    static class ItemViewHolder extends RecyclerView.ViewHolder {
        final TextView directoryName;
        final LinearLayout directoryContainer;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            directoryName = itemView.findViewById(R.id.directory);
            directoryContainer = itemView.findViewById(R.id.directory_view);
        }
    }
}
