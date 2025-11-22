package com.app.estudiaapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.ViewGroup;

 
public class QuizResultsActivity extends BaseActivity {

    public static final String EXTRA_CORRECT = "extra_correct";
    public static final String EXTRA_INCORRECT = "extra_incorrect";
    public static final String EXTRA_PERCENT = "extra_percent";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_results);

    TextView summaryCorrect = findViewById(R.id.results_correct);
    TextView summaryIncorrect = findViewById(R.id.results_incorrect);
    TextView summaryPercent = findViewById(R.id.results_percent);
        Button accept = findViewById(R.id.btn_results_accept);

        // progress views
        final View progressContainer = findViewById(R.id.quiz_progress_container);
        final View progressFill = findViewById(R.id.quiz_progress_fill);

        Intent i = getIntent();
        int correct = i.getIntExtra(EXTRA_CORRECT, 0);
        int incorrect = i.getIntExtra(EXTRA_INCORRECT, 0);
        int percent = i.getIntExtra(EXTRA_PERCENT, 0);
    summaryCorrect.setText("✔\uFE0F Aciertos: " + correct);
    summaryIncorrect.setText("\u200B❌\u200B Fallos: " + incorrect);
    summaryPercent.setText("\uD83C\uDF93\u200B Nota: " + percent + "%");

        accept.setOnClickListener(v -> finish());

        // The results screen is only reachable when the quiz is complete — show the bar as 100% filled (blue)
        if (progressContainer != null && progressFill != null) {
            progressContainer.post(() -> {
                int containerWidth = progressContainer.getWidth();
                int target = containerWidth; // full width (100%)
                // Ensure the fill drawable is the navy color (progress_fill_shape)
                progressFill.setBackgroundResource(R.drawable.progress_fill_shape);
                ValueAnimator va = ValueAnimator.ofInt(0, target);
                va.setDuration(600);
                va.setInterpolator(new AccelerateDecelerateInterpolator());
                va.addUpdateListener(animation -> {
                    int val = (int) animation.getAnimatedValue();
                    ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
                    lp.width = val;
                    progressFill.setLayoutParams(lp);
                });
                va.start();
            });
        }
    }
}
