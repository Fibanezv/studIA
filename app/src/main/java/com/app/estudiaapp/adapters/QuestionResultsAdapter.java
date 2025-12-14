package com.app.estudiaapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.estudiaapp.R;
import com.app.estudiaapp.models.QuestionResult;

import java.util.ArrayList;

public class QuestionResultsAdapter extends RecyclerView.Adapter<QuestionResultsAdapter.QuestionResultViewHolder> {

    private ArrayList<QuestionResult> questionResults;

    public QuestionResultsAdapter(ArrayList<QuestionResult> questionResults) {
        this.questionResults = questionResults;
    }

    @NonNull
    @Override
    public QuestionResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_question_result, parent, false);
        return new QuestionResultViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull QuestionResultViewHolder holder, int position) {
        QuestionResult result = questionResults.get(position);
        
        holder.questionText.setText((position + 1) + ". " + result.questionText);
        
        // Mostrar respuesta del usuario
        if (result.userAnswer != null && !result.userAnswer.isEmpty()) {
            holder.userAnswer.setText("Tu respuesta: " + result.userAnswer);
            holder.userAnswer.setVisibility(View.VISIBLE);
        } else {
            holder.userAnswer.setVisibility(View.GONE);
        }
        
        if (result.isCorrect) {
            holder.resultIndicator.setText("✔️");
            holder.correctAnswer.setVisibility(View.GONE);
        } else {
            holder.resultIndicator.setText("❌");
            if (result.correctAnswer != null && !result.correctAnswer.isEmpty()) {
                holder.correctAnswer.setText("Correcta: " + result.correctAnswer);
                holder.correctAnswer.setVisibility(View.VISIBLE);
            } else {
                holder.correctAnswer.setVisibility(View.GONE);
            }
        }
        
        android.util.Log.d("QuestionResultsAdapter", "Position " + position + 
            ": user=" + result.userAnswer + ", correct=" + result.correctAnswer + 
            ", isCorrect=" + result.isCorrect);
    }

    @Override
    public int getItemCount() {
        return questionResults.size();
    }

    static class QuestionResultViewHolder extends RecyclerView.ViewHolder {
        TextView questionText;
        TextView userAnswer;
        TextView correctAnswer;
        TextView resultIndicator;

        public QuestionResultViewHolder(@NonNull View itemView) {
            super(itemView);
            questionText = itemView.findViewById(R.id.question_text);
            userAnswer = itemView.findViewById(R.id.user_answer);
            correctAnswer = itemView.findViewById(R.id.correct_answer);
            resultIndicator = itemView.findViewById(R.id.result_indicator);
        }
    }
}
