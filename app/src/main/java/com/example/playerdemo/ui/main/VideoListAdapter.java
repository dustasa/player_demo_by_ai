package com.example.playerdemo.ui.main;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.playerdemo.data.model.VideoFile;
import com.example.playerdemo.databinding.ItemVideoBinding;

import java.util.ArrayList;
import java.util.List;

public class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.VideoViewHolder> {
    private List<VideoFile> videos = new ArrayList<>();
    private OnVideoClickListener listener;

    public interface OnVideoClickListener {
        void onPlayClick(VideoFile video);
        void onRecognizeClick(VideoFile video);
    }

    public VideoListAdapter(OnVideoClickListener listener) {
        this.listener = listener;
    }

    public void setVideos(List<VideoFile> videos) {
        this.videos = videos;
        notifyDataSetChanged();
    }

    public void clearVideos() {
        this.videos.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemVideoBinding binding = ItemVideoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VideoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        holder.bind(videos.get(position));
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    class VideoViewHolder extends RecyclerView.ViewHolder {
        private final ItemVideoBinding binding;

        VideoViewHolder(ItemVideoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(VideoFile video) {
            binding.tvVideoName.setText(video.getName());
            binding.tvVideoSize.setText(video.getFormattedSize());
            binding.tvVideoDuration.setText(video.getDuration());

            if (video.isRemote()) {
                binding.chipRemote.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.chipRemote.setVisibility(android.view.View.GONE);
            }

            binding.btnPlay.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlayClick(video);
                }
            });

            binding.btnRecognize.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRecognizeClick(video);
                }
            });

            if (!video.isRemote()) {
                binding.btnRecognize.setVisibility(android.view.View.GONE);
            } else {
                binding.btnRecognize.setVisibility(android.view.View.VISIBLE);
            }
        }
    }
}
