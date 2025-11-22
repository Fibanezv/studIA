package com.app.estudiaapp.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
// ImageView removed; replaced by text labels in layout
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.estudiaapp.R;
import com.app.estudiaapp.helper.StorageHelper;
import com.app.estudiaapp.models.Quiz;
import com.app.estudiaapp.models.StudyPlan;

import java.util.ArrayList;

public class QuizzesAdapter extends RecyclerView.Adapter<QuizzesAdapter.QuizViewHolder> {

    private ArrayList<Quiz> quizzes;
    private OnQuizClickListener listener;
    private String planId;

    public interface OnQuizClickListener {
        void onQuizClick(Quiz quiz);
    }

    public QuizzesAdapter(ArrayList<Quiz> quizzes, String planId, OnQuizClickListener listener) {
        this.quizzes = quizzes;
        this.listener = listener;
        this.planId = planId;
    }

    @NonNull
    @Override
    public QuizViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_quiz, parent, false);
        return new QuizViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull QuizViewHolder holder, int position) {
        Quiz q = quizzes.get(position);
        holder.title.setText(q.titulo != null ? q.titulo : q.id);

        // Datos del quiz: total preguntas y respondidas
        int total = (q.questions == null) ? 0 : q.questions.size();
        int answered = (q.progreso == null) ? 0 : q.progreso.preguntasRespondidas;
        int percent = 0;
        if (total > 0) percent = (int) ((answered * 100.0f) / total);
        if (percent > 100) percent = 100; // cap

        holder.percent.setText(percent + "%");

        // Mostrar nota final si existe (nota >= 0)
        if (holder.score != null) {
            int nota = (q.progreso == null) ? -1 : q.progreso.nota;
            if (nota >= 0) {
                // show plain percentage and reveal the label
                holder.score.setText(nota + "%");
                holder.score.setVisibility(View.VISIBLE);
                // reveal label (if present)
                if (holder.scoreLabel != null) holder.scoreLabel.setVisibility(View.VISIBLE);
            } else {
                holder.score.setVisibility(View.GONE);
                if (holder.scoreLabel != null) holder.scoreLabel.setVisibility(View.GONE);
            }
        }

        // Si está completo (100%), hacerlo no clickable pero mantener la apariencia
        boolean isComplete = (percent >= 100 && total > 0);
        // Do not change alpha or enabled state so visual appearance remains the same
        holder.itemView.setClickable(!isComplete);
        holder.itemView.setLongClickable(false);

        if (!isComplete) {
            holder.itemView.setOnClickListener(v -> listener.onQuizClick(q));
        } else {
            holder.itemView.setOnClickListener(null);
        }

        // botón reiniciar
        if (holder.resetButton != null) {
            holder.resetButton.setVisibility(isComplete ? View.VISIBLE : View.GONE);
            holder.resetButton.setOnClickListener(v -> {
                Context ctx = holder.itemView.getContext();
                StudyPlan plan = StorageHelper.cargarPlan(ctx, planId);
                if (plan == null || plan.quizzes == null) {
                    Toast.makeText(ctx, "No se puede reiniciar: plan no encontrado", Toast.LENGTH_SHORT).show();
                    return;
                }
                // encontrar el quiz por id y resetear progreso
                for (int i = 0; i < plan.quizzes.size(); i++) {
                    Quiz pq = plan.quizzes.get(i);
                    if (pq != null && pq.id != null && pq.id.equals(q.id)) {
                        if (pq.progreso == null) pq.progreso = new com.app.estudiaapp.models.QuizProgress();
                        pq.progreso.preguntasRespondidas = 0;
                        pq.progreso.preguntasCorrectas = 0;
                        pq.progreso.preguntasIncorrectas = 0;
                        pq.progreso.currentIndex = 0;
                        pq.progreso.lastUpdatedMillis = System.currentTimeMillis();
                        // guardar y refrescar
                        StorageHelper.guardarPlan(ctx, plan);
                        // actualizar la lista local y notificar cambio
                        q.progreso = pq.progreso;
                        notifyItemChanged(position);
                        Toast.makeText(ctx, "Quiz reiniciado", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                Toast.makeText(ctx, "Quiz no encontrado en el plan", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    public int getItemCount() {
        return quizzes.size();
    }

    static class QuizViewHolder extends RecyclerView.ViewHolder {
    TextView title;
    TextView percent;
    TextView score;
    TextView scoreLabel;
    com.google.android.material.button.MaterialButton resetButton;

        public QuizViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.quiz_title);
            percent = itemView.findViewById(R.id.quiz_percent);
            score = itemView.findViewById(R.id.quiz_score);
            scoreLabel = itemView.findViewById(R.id.quiz_score_label);
            resetButton = itemView.findViewById(R.id.btn_reset_quiz);
        }
    }
}
