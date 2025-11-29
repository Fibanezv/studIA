package com.app.estudiaapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.estudiaapp.R;
import com.app.estudiaapp.models.HotTopic;

import java.util.ArrayList;
import java.util.List;

public class HotTopicsAdapter extends RecyclerView.Adapter<HotTopicsAdapter.ViewHolder> {

    private List<HotTopic> items = new ArrayList<>();

    public HotTopicsAdapter(List<HotTopic> items) {
        if (items != null) this.items = items;
    }

    public void updateItems(List<HotTopic> newItems) {
        if (newItems == null) newItems = new ArrayList<>();
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hot_topic, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HotTopic t = items.get(position);
        holder.title.setText(t.title != null ? t.title : "");
        holder.description.setText(t.description != null ? t.description : "");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView description;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.hot_topic_title);
            description = itemView.findViewById(R.id.hot_topic_description);
        }
    }
}
