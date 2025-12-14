package com.app.estudiaapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.estudiaapp.R;
import com.app.estudiaapp.models.FAQ;

import java.util.List;

public class FAQAdapter extends RecyclerView.Adapter<FAQAdapter.FAQViewHolder> {

    private List<FAQ> faqList;

    public FAQAdapter(List<FAQ> faqList) {
        this.faqList = faqList;
    }

    @NonNull
    @Override
    public FAQViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_faq, parent, false);
        return new FAQViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FAQViewHolder holder, int position) {
        FAQ faq = faqList.get(position);
        holder.bind(faq);
    }

    @Override
    public int getItemCount() {
        return faqList.size();
    }

    public static class FAQViewHolder extends RecyclerView.ViewHolder {

        private TextView tvPregunta;
        private TextView tvRespuesta;
        private ImageView ivExpandIcon;
        private View questionContainer;

        public FAQViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPregunta = itemView.findViewById(R.id.faq_pregunta);
            tvRespuesta = itemView.findViewById(R.id.faq_respuesta);
            ivExpandIcon = itemView.findViewById(R.id.faq_expand_icon);
            questionContainer = itemView.findViewById(R.id.faq_question_container);
        }

        public void bind(FAQ faq) {
            tvPregunta.setText(faq.getPregunta());
            tvRespuesta.setText(faq.getRespuesta());

            // Set initial visibility based on expanded state
            if (faq.isExpanded()) {
                tvRespuesta.setVisibility(View.VISIBLE);
                rotateIcon(180);
            } else {
                tvRespuesta.setVisibility(View.GONE);
                rotateIcon(0);
            }

            // Toggle expand/collapse on click
            questionContainer.setOnClickListener(v -> {
                faq.toggleExpanded();
                if (faq.isExpanded()) {
                    tvRespuesta.setVisibility(View.VISIBLE);
                    rotateIcon(180);
                } else {
                    tvRespuesta.setVisibility(View.GONE);
                    rotateIcon(0);
                }
            });
        }

        private void rotateIcon(int degrees) {
            ivExpandIcon.animate()
                    .rotation(degrees)
                    .setDuration(300)
                    .start();
        }
    }
}
