package com.example.playerdemo.ui.editor;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.playerdemo.data.model.SubtitleEntry;
import com.example.playerdemo.databinding.ItemSubtitleBinding;

import java.util.ArrayList;
import java.util.List;

public class SubtitleAdapter extends RecyclerView.Adapter<SubtitleAdapter.SubtitleViewHolder> {
    private List<SubtitleEntry> subtitles = new ArrayList<>();
    private OnSubtitleActionListener listener;

    public interface OnSubtitleActionListener {
        void onToggleKeep(SubtitleEntry entry, boolean kept);
        void onEdit(SubtitleEntry entry);
        void onPreview(SubtitleEntry entry);
    }

    public SubtitleAdapter(OnSubtitleActionListener listener) {
        this.listener = listener;
    }

    public void setSubtitles(List<SubtitleEntry> subtitles) {
        this.subtitles = subtitles;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SubtitleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSubtitleBinding binding = ItemSubtitleBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SubtitleViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SubtitleViewHolder holder, int position) {
        holder.bind(subtitles.get(position));
    }

    @Override
    public int getItemCount() {
        return subtitles.size();
    }

    class SubtitleViewHolder extends RecyclerView.ViewHolder {
        private final ItemSubtitleBinding binding;

        SubtitleViewHolder(ItemSubtitleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SubtitleEntry entry) {
            binding.tvIndex.setText(String.valueOf(entry.getIndex() + 1));
            binding.tvTimeRange.setText(entry.getStartTime() + " --> " + entry.getEndTime());
            binding.tvContent.setText(entry.getContent());
            binding.checkKeep.setChecked(entry.isKept());

            if (entry.isKept()) {
                binding.tvContent.setAlpha(1.0f);
                binding.tvContent.setPaintFlags(0);
            } else {
                binding.tvContent.setAlpha(0.5f);
                binding.tvContent.setPaintFlags(
                        binding.tvContent.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            }

            binding.checkKeep.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onToggleKeep(entry, isChecked);
                }
            });

            binding.btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEdit(entry);
                }
            });

            binding.btnPreview.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPreview(entry);
                }
            });
        }
    }
}
