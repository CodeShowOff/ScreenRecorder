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

package com.orpheusdroid.screenrecorder.adapter;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.orpheusdroid.screenrecorder.Const;
import com.orpheusdroid.screenrecorder.R;
import com.orpheusdroid.screenrecorder.ui.EditVideoActivity;
import com.orpheusdroid.screenrecorder.ui.VideosListFragment;

import java.io.File;

import androidx.documentfile.provider.DocumentFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

/**
 * <p>
 *     Custom Recycler view adapter for video list fragment
 * </p>
 *
 * @author Vijai Chandra Prasad .R
 * @see SectionViewHolder
 * @see ItemViewHolder
 */
public class VideoRecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    /**
     * Integer value for Section view type
     */
    private static final int VIEW_SECTION = 0;
    /**
     * Integer value for Video item view type
     */
    private static final int VIEW_ITEM = 1;

    /**
     * VideoListFragment object
     */
    private VideosListFragment videosListFragment;

    /**
     * ArrayList containing the videos
     */
    private ArrayList<Video> videos;
    private Context context;

    /**
     * Boolean to set if multi select to be enabled
     */
    private boolean isMultiSelect = false;
    private int count = 0;

    /**
     * Actionbar actionmode object
     */
    private ActionMode mActionMode;

    /**
     * Show a contextual menu in multiselect mode
     */
    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.video_list_action_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false; // Return false if nothing is done
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            // Build an arraylist of selected item positions when an menu item is clicked
            int itemId = item.getItemId();
            if (itemId == R.id.delete) {
                ArrayList<Video> deleteFiles = new ArrayList<>();
                for (Video video : videos) {
                    if (video.isSelected()) {
                        //deleFiles.add(new File(videos.get(videos.indexOf(video)).getFile().getPath()));
                        deleteFiles.add(video);
                    }
                }
                if (!deleteFiles.isEmpty())
                    confirmDelete(deleteFiles);
                mActionMode.finish();
            } else if (itemId == R.id.share) {
                ArrayList<Integer> positions = new ArrayList<>();
                for (Video video : videos) {
                    if (video.isSelected()) {
                        positions.add(videos.indexOf(video));
                    }
                }
                if (!positions.isEmpty())
                    shareVideos(positions);
                mActionMode.finish();
            } else if (itemId == R.id.select_all) {
                for (Video video : videos)
                    video.setSelected(true);
                mActionMode.setTitle("" + videos.size());
                notifyDataSetChanged();
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            // remove all selected videos and reload recyclerview
            for (Video video :
                    videos) {
                video.setSelected(false);
            }
            isMultiSelect = false;
            notifyDataSetChanged();
        }
    };

    public VideoRecyclerAdapter(Context context, ArrayList<Video> android, VideosListFragment videosListFragment) {
        this.videos = android;
        this.context = context;
        this.videosListFragment = videosListFragment;
    }

    /**
     * Find if the view is a section type or video type
     *
     * @param position position of the arraylist of videos
     * @return the item type
     */
    @Override
    public int getItemViewType(int position) {
        return isSection(position) ? VIEW_SECTION : VIEW_ITEM;
    }

    /**
     * Method to determine the type
     * @param position position of the arraylist of videos
     * @return true if the item is a section. False otherwise
     */
    public boolean isSection(int position) {
        return videos.get(position).isSection();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_SECTION:
                view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.content_video_section, viewGroup, false);
                return new SectionViewHolder(view);
            case VIEW_ITEM:
                view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.content_video, viewGroup, false);
                return new ItemViewHolder(view);
            default:
                view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.content_video, viewGroup, false);
                return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
        final Video video = videos.get(position);
        switch (holder.getItemViewType()) {
            case VIEW_ITEM:
                final ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
                //Set video file name
                itemViewHolder.tv_fileName.setText(video.getFileName());
                //If thumbnail has failed for some reason, set empty image resource to imageview
                if (videos.get(position).getThumbnail() != null) {
                    itemViewHolder.iv_thumbnail.setImageBitmap(video.getThumbnail());
                } else {
                    itemViewHolder.iv_thumbnail.setImageResource(0);
                    Log.d(Const.TAG, "thumbnail error");
                }

                // Hide the play image over thumbnail and overflow menu if multiselect enabled
                if (isMultiSelect) {
                    itemViewHolder.iv_play.setVisibility(View.INVISIBLE);
                    itemViewHolder.overflow.setVisibility(View.INVISIBLE);
                } else {
                    itemViewHolder.iv_play.setVisibility(View.VISIBLE);
                    itemViewHolder.overflow.setVisibility(View.VISIBLE);
                }

                // Set foreground color to identify selected items
                if (video.isSelected()) {
                    itemViewHolder.selectableFrame.setForeground(new ColorDrawable(ContextCompat.getColor(context, R.color.multiSelectColor)));
                } else {
                    itemViewHolder.selectableFrame.setForeground(new ColorDrawable(ContextCompat.getColor(context, android.R.color.transparent)));
                }

                itemViewHolder.overflow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        PopupMenu popupMenu = new PopupMenu(context, view);
                        popupMenu.inflate(R.menu.popupmenu);
                        popupMenu.show();
                        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem item) {
                                int itemId = item.getItemId();
                                if (itemId == R.id.share) {
                                    shareVideo(itemViewHolder.getAdapterPosition());
                                } else if (itemId == R.id.delete) {
                                    confirmDelete(holder.getAdapterPosition());
                                } else if (itemId == R.id.edit) {
                                    Intent editIntent = new Intent(context, EditVideoActivity.class);
                                    Uri videoUri;
                                    if (video.isSAFFile()) {
                                        videoUri = video.getUri();
                                    } else {
                                        videoUri = Uri.fromFile(video.getFile());
                                    }
                                    editIntent.putExtra(Const.VIDEO_EDIT_URI_KEY, videoUri.toString());
                                    Log.d(Const.TAG, "Edit Uri: " + videoUri);
                                    videosListFragment.startActivityForResult(editIntent, Const.VIDEO_EDIT_REQUEST_CODE);
                                }
                                return true;
                            }
                        });
                    }
                });

                //Show user a chooser to play the video with
                itemViewHolder.videoCard.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //Video video = videos.get(itemViewHolder.getAdapterPosition());

                        // If multiselect is enabled, select the items pressed by user
                        if (isMultiSelect) {

                            // main count of the selected items
                            // If the video is already selected, reduce count else increment count
                            if (video.isSelected())
                                count--;
                            else
                                count++;

                            // Enable disable selection based on previous choice
                            video.setSelected(!video.isSelected());
                            notifyDataSetChanged();
                            mActionMode.setTitle("" + count);

                            // If the count is 0, disable muliselect
                            if (count == 0)
                                setMultiSelect(false);
                            return;
                        }

                        Log.d("Videos List", "video position clicked: " + itemViewHolder.getAdapterPosition());

                        Uri fileUri;
                        if (video.isSAFFile()) {
                            // Use SAF URI directly
                            fileUri = video.getUri();
                        } else {
                            // Use FileProvider for traditional files
                            fileUri = FileProvider.getUriForFile(
                                    context, context.getPackageName() +
                                            ".provider",
                                    video.getFile()
                            );
                        }
                        Log.d(Const.TAG, fileUri.toString());
                        Intent openVideoIntent = new Intent();
                        openVideoIntent.setAction(Intent.ACTION_VIEW)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .setDataAndType(
                                        fileUri,
                                        context.getContentResolver().getType(fileUri));
                        context.startActivity(openVideoIntent);
                    }
                });

                // LongClickListener to enable multiselect
                itemViewHolder.videoCard.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        if (!isMultiSelect) {
                            setMultiSelect(true);
                            video.setSelected(true);
                            count++;
                            mActionMode.setTitle("" + count);
                            notifyDataSetChanged();
                        }
                        return true;
                    }
                });

                break;
            case VIEW_SECTION:
                SectionViewHolder sectionViewHolder = (SectionViewHolder) holder;
                sectionViewHolder.section.setText(generateSectionTitle(video.getLastModified()));
                break;
        }
    }

    /**
     * method to set if multi select to be enabled
     * @param isMultiSelect boolean
     */
    private void setMultiSelect(boolean isMultiSelect) {
        if (isMultiSelect) {
            this.isMultiSelect = true;
            count = 0;
            mActionMode = ((AppCompatActivity) videosListFragment.getActivity()).startSupportActionMode(mActionModeCallback);
        } else {
            this.isMultiSelect = false;
            mActionMode.finish();
        }
    }

    /**
     * Share the videos selected
     *
     * @param position Integer value representing the position of the video to be shared
     * @see #shareVideos(ArrayList positions)
     */
    private void shareVideo(int position) {
        Video video = videos.get(position);
        Uri fileUri;
        
        if (video.isSAFFile()) {
            // Use SAF URI directly
            fileUri = video.getUri();
        } else {
            // Use FileProvider for traditional files
            fileUri = FileProvider.getUriForFile(
                    context, context.getPackageName() +
                            ".provider",
                    video.getFile()
            );
        }

        Intent Shareintent = new Intent()
                .setAction(Intent.ACTION_SEND)
                .setType("video/*")
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_STREAM, fileUri);
        context.startActivity(Intent.createChooser(Shareintent,
                context.getString(R.string.share_intent_notification_title)));
    }

    /**
     * Share the videos selected
     *
     * @param positions Integer ArrayList containing the positions of the videos to be shared
     *
     * @see #shareVideo(int postion)
     */
    private void shareVideos(ArrayList<Integer> positions) {
        ArrayList<Uri> videoList = new ArrayList<>();
        for (int position : positions) {
            Video video = videos.get(position);
            Uri fileUri;
            
            if (video.isSAFFile()) {
                // Use SAF URI directly
                fileUri = video.getUri();
            } else {
                // Use FileProvider for traditional files
                fileUri = FileProvider.getUriForFile(
                        context, context.getPackageName() +
                                ".provider",
                        video.getFile()
                );
            }
            videoList.add(fileUri);
        }
        Intent Shareintent = new Intent()
                .setAction(Intent.ACTION_SEND_MULTIPLE)
                .setType("video/*")
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, videoList);
        context.startActivity(Intent.createChooser(Shareintent,
                context.getString(R.string.share_intent_notification_title)));
    }

    /**
     * Delete the videos selected
     *
     * @param position integer value representing the position of the video to be deleted
     *
     * @see #deleteVideo(int position)
     */
    private void deleteVideo(int position) {
        Log.d("Videos List", "delete position clicked: " + position);
        Video video = videos.get(position);
        boolean deleted = false;
        
        if (video.isSAFFile()) {
            // Handle SAF-based file deletion using DocumentFile
            try {
                DocumentFile docFile = DocumentFile.fromSingleUri(context, video.getUri());
                if (docFile != null && docFile.exists()) {
                    deleted = docFile.delete();
                    Log.d(Const.TAG, "SAF file delete result: " + deleted);
                }
            } catch (Exception e) {
                Log.e(Const.TAG, "Error deleting SAF file: " + e.getMessage());
            }
        } else {
            // Handle traditional File deletion
            File file = video.getFile();
            if (file != null) {
                deleted = file.delete();
                Log.d(Const.TAG, "File delete result: " + deleted);
            }
        }
        
        if (deleted) {
            Toast.makeText(context, "File deleted successfully", Toast.LENGTH_SHORT).show();
            videos.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, videos.size());
        } else {
            Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show();
            Log.e(Const.TAG, "Failed to delete video at position: " + position);
        }
    }

    /**
     * Delete the videos selected
     *
     * @param deleteVideos ArrayList of integers containing the position of the videos to be deleted
     *
     * @see #deleteVideo(int position)
     */
    private void deleteVideos(ArrayList<Video> deleteVideos) {
        for (Video video : deleteVideos) {
            if (!video.isSection()) {
                boolean deleted = false;
                
                if (video.isSAFFile()) {
                    // Handle SAF-based file deletion
                    try {
                        DocumentFile docFile = DocumentFile.fromSingleUri(context, video.getUri());
                        if (docFile != null && docFile.exists()) {
                            deleted = docFile.delete();
                        }
                    } catch (Exception e) {
                        Log.e(Const.TAG, "Error deleting SAF file: " + e.getMessage());
                    }
                } else {
                    // Handle traditional File deletion
                    File file = video.getFile();
                    if (file != null) {
                        deleted = file.delete();
                    }
                }
                
                if (deleted) {
                    notifyItemRemoved(videos.indexOf(video));
                    videos.remove(video);
                }
            }
        }
        notifyDataSetChanged();
    }

    /**
     * Show confirmation dialog before the video is deleted
     *
     * @param position integer representing the postion of the video in the dataset to delete
     */
    private void confirmDelete(final int position) {
        new AlertDialog.Builder(context)
                .setTitle(context.getResources().getQuantityString(R.plurals.delete_alert_title, 1))
                .setMessage(context.getResources().getQuantityString(R.plurals.delete_alert_message, 1))
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        deleteVideo(position);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .show();
    }

    /**
     * Show confirmation dialog before the video is deleted
     *
     * @param deleteVideos Array list of integer containing the positions of the videos in the dataset to delete
     */
    private void confirmDelete(final ArrayList<Video> deleteVideos) {
        int count = deleteVideos.size();
        new AlertDialog.Builder(context)
                .setTitle(context.getResources().getQuantityString(R.plurals.delete_alert_title, count))
                .setMessage(context.getResources().getQuantityString(R.plurals.delete_alert_message,
                        count, count))
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        deleteVideos(deleteVideos);
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .show();
    }

    /**
     * Generate title for the section depending on the recording date
     * @param date Date when the videos where recorded
     * @return Simplified string for the section header
     */
    private String generateSectionTitle(Date date) {
        Calendar sDate = toCalendar(new Date().getTime());
        Calendar eDate = toCalendar(date.getTime());

        // Get the represented date in milliseconds
        long milis1 = sDate.getTimeInMillis();
        long milis2 = eDate.getTimeInMillis();

        // Calculate difference in milliseconds
        int dayDiff = (int) Math.abs((milis2 - milis1) / (24 * 60 * 60 * 1000));

        int yearDiff = sDate.get(Calendar.YEAR) - eDate.get(Calendar.YEAR);
        Log.d("ScreenRecorder", "yeardiff: " + yearDiff);

        if (yearDiff == 0) {
            switch (dayDiff) {
                case 0:
                    return "Today";
                case 1:
                    return "Yesterday";
                default:
                    SimpleDateFormat format = new SimpleDateFormat("EEEE, dd MMM", Locale.getDefault());
                    return format.format(date);
            }
        } else {
            SimpleDateFormat format = new SimpleDateFormat("EEEE, dd MMM YYYY", Locale.getDefault());
            return format.format(date);
        }
    }

    /**
     * Generate Calendar object and return it
     * @param timestamp timestamp for which Calendar object to be created
     * @return Calendar object
     */
    private Calendar toCalendar(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    /**
     * ViewHolder class for video items
     */
    private final class ItemViewHolder extends RecyclerView.ViewHolder {
        private TextView tv_fileName;
        private ImageView iv_thumbnail;
        private RelativeLayout videoCard;
        private FrameLayout selectableFrame;
        private ImageButton overflow;
        private ImageView iv_play;

        ItemViewHolder(View view) {
            super(view);
            tv_fileName = view.findViewById(R.id.fileName);
            iv_thumbnail = view.findViewById(R.id.thumbnail);
            iv_thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            videoCard = view.findViewById(R.id.videoCard);
            overflow = view.findViewById(R.id.ic_overflow);
            selectableFrame = view.findViewById(R.id.selectableFrame);
            iv_play = view.findViewById(R.id.play_iv);
        }
    }

    /**
     * ViewHolder class for sections
     */
    private final class SectionViewHolder extends RecyclerView.ViewHolder {
        private TextView section;

        SectionViewHolder(View view) {
            super(view);
            section = view.findViewById(R.id.sectionID);
        }
    }
}
