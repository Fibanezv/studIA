package com.app.estudiaapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.estudiaapp.R;
import com.app.estudiaapp.models.StudyPlan;
import com.app.estudiaapp.models.Quiz;

import java.util.ArrayList;

public class PlansAdapter extends RecyclerView.Adapter<PlansAdapter.PlanViewHolder> {

    private ArrayList<StudyPlan> plans;
    private OnPlanClickListener listener;

    public interface OnPlanClickListener {
        void onPlanClick(StudyPlan plan);
    }

    public PlansAdapter(ArrayList<StudyPlan> plans, OnPlanClickListener listener) {
        this.plans = plans;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plan, parent, false);
        return new PlanViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlanViewHolder holder, int position) {
        StudyPlan plan = plans.get(position);
        holder.planName.setText(plan.nombre);  // Mostramos el nombre del plan
        // Mostrar counts si están disponibles
        try {
            int flashCount = (plan.flashcards == null) ? 0 : plan.flashcards.size();
            int quizCount = (plan.quizzes == null) ? 0 : plan.quizzes.size();
            // calcular quizzes completados (preguntasRespondidas >= total preguntas del quiz)
            int completed = 0;
            if (plan.quizzes != null) {
                for (Quiz q : plan.quizzes) {
                    int totalQ = (q.questions == null) ? 0 : q.questions.size();
                    int answered = (q.progreso == null) ? 0 : q.progreso.preguntasRespondidas;
                    if (totalQ > 0 && answered >= totalQ) completed++;
                }
            }
            holder.planDocument.setText("id: " + plan.id);
            // show flashcard progress as studied/total
            int studied = (plan.progreso == null) ? 0 : plan.progreso.flashcardsEstudiadas;
            holder.flashcardCount.setText(studied + "/" + flashCount);
            holder.quizCount.setText(completed + "/" + quizCount);
            int hotCount = (plan.hotTopics == null) ? 0 : plan.hotTopics.size();
            if (holder.hotTopicsCount != null) holder.hotTopicsCount.setText(String.valueOf(hotCount));
        } catch (Exception ex) {
            // safe fallback
        }
        holder.itemView.setOnClickListener(v -> listener.onPlanClick(plan));
    }

    @Override
    public int getItemCount() {
        return plans.size();
    }

    // Allow the caller to replace the data and refresh the list dynamically
    public void setPlans(ArrayList<StudyPlan> newPlans) {
        if (newPlans == null) newPlans = new ArrayList<>();
        this.plans = newPlans;
        notifyDataSetChanged();
    }

    static class PlanViewHolder extends RecyclerView.ViewHolder {
        TextView planName;
        TextView planDocument;
        TextView flashcardCount;
        TextView quizCount;
        TextView hotTopicsCount;

        public PlanViewHolder(@NonNull View itemView) {
            super(itemView);
            planName = itemView.findViewById(R.id.plan_name);
            planDocument = itemView.findViewById(R.id.plan_document_name);
            flashcardCount = itemView.findViewById(R.id.flashcard_count);
            quizCount = itemView.findViewById(R.id.quiz_count);
            hotTopicsCount = itemView.findViewById(R.id.hot_topics_count);
        }
    }
}
