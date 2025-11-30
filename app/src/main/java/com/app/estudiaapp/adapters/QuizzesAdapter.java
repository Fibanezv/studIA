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
import com.app.estudiaapp.models.Quiz;
import com.app.estudiaapp.models.StudyPlan;
import com.app.estudiaapp.UserRepository;

import java.util.ArrayList;

public class QuizzesAdapter extends RecyclerView.Adapter<QuizzesAdapter.QuizViewHolder> {

    private ArrayList<Quiz> quizzes;
    private OnQuizClickListener listener;
    private StudyPlan plan;
    private String userId;
    private OnPlanUpdatedListener planUpdateListener;

    public interface OnQuizClickListener {
        void onQuizClick(Quiz quiz);
    }

    public interface OnPlanUpdatedListener {
        void onPlanUpdated();
    }

    public QuizzesAdapter(ArrayList<Quiz> quizzes, StudyPlan plan, String userId, OnQuizClickListener listener, OnPlanUpdatedListener planUpdateListener) {
        this.quizzes = quizzes;
        this.listener = listener;
        this.plan = plan;
        this.userId = userId;
        this.planUpdateListener = planUpdateListener;
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
        if (holder.typology != null) {
            holder.typology.setText(q.typology != null ? ("Tipo: " + q.typology) : "");
            holder.typology.setVisibility(q.typology != null ? View.VISIBLE : View.GONE);
        }

        // Datos del quiz: total preguntas y respondidas
        int total = (q.questions == null) ? 0 : q.questions.size();
        int answered = (q.progreso == null) ? 0 : q.progreso.preguntasRespondidas;
        int percent = 0;
        if (total > 0) percent = (int) ((answered * 100.0f) / total);
        if (percent > 100) percent = 100; // cap

        // Mostrar siempre el porcentaje de progreso como estaba originalmente
        holder.percent.setVisibility(View.VISIBLE);
        holder.percent.setText(percent + "%");

        // Mostrar nota final si existe (nota >= 0)
        if (holder.score != null) {
            int nota = (q.progreso == null) ? -1 : q.progreso.nota;
            // Show score only when there's an actual answered count (avoid showing 0% when nothing answered)
            if (nota >= 0 && answered > 0) {
                holder.score.setText(nota + "%");
                holder.score.setVisibility(View.VISIBLE);
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
                if (plan == null || plan.quizzes == null) {
                    Toast.makeText(ctx, "No se puede reiniciar: plan no encontrado", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (userId == null || userId.isEmpty()) {
                    Toast.makeText(ctx, "Usuario no autenticado", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Encontrar el quiz por id y resetear progreso
                boolean found = false;
                for (Quiz pq : plan.quizzes) {
                    if (pq != null && pq.id != null && pq.id.equals(q.id)) {
                        if (pq.progreso == null) pq.progreso = new com.app.estudiaapp.models.QuizProgress();
                        pq.progreso.preguntasRespondidas = 0;
                        pq.progreso.preguntasCorrectas = 0;
                        pq.progreso.preguntasIncorrectas = 0;
                        pq.progreso.currentIndex = 0;
                        pq.progreso.nota = -1;
                        pq.progreso.lastUpdatedMillis = System.currentTimeMillis();
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    Toast.makeText(ctx, "Quiz no encontrado en el plan", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Guardar en Firestore
                new UserRepository(ctx).savePlan(userId, plan, success -> {
                    // Actualizar la lista local y notificar cambio
                    q.progreso.preguntasRespondidas = 0;
                    q.progreso.preguntasCorrectas = 0;
                    q.progreso.preguntasIncorrectas = 0;
                    q.progreso.currentIndex = 0;
                    q.progreso.nota = -1;
                    notifyItemChanged(position);
                    if (planUpdateListener != null) planUpdateListener.onPlanUpdated();
                    Toast.makeText(ctx, "Quiz reiniciado", Toast.LENGTH_SHORT).show();
                }, error -> {
                    Toast.makeText(ctx, "Error al reiniciar: " + error, Toast.LENGTH_SHORT).show();
                });
            });
        }
    }

    @Override
    public int getItemCount() {
        return quizzes.size();
    }

    static class QuizViewHolder extends RecyclerView.ViewHolder {
    TextView title;
    TextView typology;
    TextView percent;
    TextView score;
    TextView scoreLabel;
    com.google.android.material.button.MaterialButton resetButton;

        public QuizViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.quiz_title);
            typology = itemView.findViewById(R.id.quiz_typology);
            percent = itemView.findViewById(R.id.quiz_percent);
            score = itemView.findViewById(R.id.quiz_score);
            scoreLabel = itemView.findViewById(R.id.quiz_score_label);
            resetButton = itemView.findViewById(R.id.btn_reset_quiz);
        }
    }
}
