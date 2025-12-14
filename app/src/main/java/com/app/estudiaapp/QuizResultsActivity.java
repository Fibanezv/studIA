package com.app.estudiaapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.app.estudiaapp.utils.Constants;
import com.app.estudiaapp.models.QuestionResult;
import com.app.estudiaapp.adapters.QuestionResultsAdapter;
import java.util.ArrayList;

 
public class QuizResultsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_results);

        TextView summaryPercent = findViewById(R.id.results_percent_header);
        Button accept = findViewById(R.id.btn_results_accept);
        RecyclerView questionsList = findViewById(R.id.results_questions_list);

        Intent i = getIntent();
        int correct = i.getIntExtra(Constants.EXTRA_CORRECT, 0);
        int incorrect = i.getIntExtra(Constants.EXTRA_INCORRECT, 0);
        int percent = i.getIntExtra(Constants.EXTRA_PERCENT, 0);
        summaryPercent.setText("\uD83C\uDF93\u200B " + percent + "%");

        // Configurar lista de preguntas
        @SuppressWarnings("unchecked")
        ArrayList<QuestionResult> questionResults = (ArrayList<QuestionResult>) i.getSerializableExtra("QUESTION_RESULTS");
        
        android.util.Log.d("QuizResultsActivity", "Received questionResults: " + (questionResults != null ? questionResults.size() + " items" : "null"));
        
        if (questionResults != null && !questionResults.isEmpty()) {
            android.util.Log.d("QuizResultsActivity", "Showing RecyclerView with " + questionResults.size() + " questions");
            questionsList.setVisibility(View.VISIBLE);
            questionsList.setLayoutManager(new LinearLayoutManager(this));
            QuestionResultsAdapter adapter = new QuestionResultsAdapter(questionResults);
            questionsList.setAdapter(adapter);
        } else {
            android.util.Log.w("QuizResultsActivity", "No question results to display");
        }

        accept.setOnClickListener(v -> finish());

        // No progress bar on results screen anymore
    }
}
