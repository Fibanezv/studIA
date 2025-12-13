package com.app.estudiaapp.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.app.estudiaapp.FlashcardDetailActivity;
import com.app.estudiaapp.R;
import com.app.estudiaapp.models.Flashcard;
import com.app.estudiaapp.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class FlashcardsAdapter extends RecyclerView.Adapter<FlashcardsAdapter.ViewHolder> {

    private List<Flashcard> items = new ArrayList<>();
    private String planId;
    // optional mapping to original plan indices when adapter receives a sub-list
    private List<Integer> originalIndices = null;
    public FlashcardsAdapter(List<Flashcard> items, String planId) {
        this(items, planId, null);
    }

    public FlashcardsAdapter(List<Flashcard> items, String planId, List<Integer> originalIndices) {
        if (items != null) this.items = items;
        this.planId = planId;
        this.originalIndices = originalIndices;
    }

    /**
     * Replace items in the adapter and refresh the view. Original indices mapping is kept as-is
     * if it was provided at construction time.
     */
    public void updateItems(List<Flashcard> newItems) {
        if (newItems == null) newItems = new ArrayList<>();
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_flashcard, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Flashcard f = items.get(position);
        holder.title.setText(f.pregunta != null ? f.pregunta : "");
        // show 'Completada' label if completed (do not change background)
        if (f.completada) {
            holder.status.setVisibility(View.VISIBLE);
        } else {
            holder.status.setVisibility(View.GONE);
        }

        Context ctx = holder.itemView.getContext();
        holder.itemView.setOnClickListener(v -> {
            // open detail activity for this flashcard
            Intent intent = new Intent(ctx, FlashcardDetailActivity.class);
            intent.putExtra(Constants.EXTRA_PLAN_ID, planId);
            int globalIndex = (originalIndices != null && originalIndices.size() > position) ? originalIndices.get(position) : position;
            intent.putExtra(Constants.EXTRA_FLASHCARD_INDEX, globalIndex);
            ctx.startActivity(intent);
            // remove transition animation if context is an Activity
            try {
                if (ctx instanceof android.app.Activity) {
                    ((android.app.Activity) ctx).overridePendingTransition(0, 0);
                }
            } catch (Exception ex) {
                // ignore if override fails for any reason
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
    TextView title;
    CardView card;
    ImageView status;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.item_flashcard_text);
            card = itemView.findViewById(R.id.card_root);
            status = itemView.findViewById(R.id.item_flashcard_status);
        }
    }
}
