package com.app.estudiaapp;

import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.estudiaapp.adapters.FAQAdapter;
import com.app.estudiaapp.models.FAQ;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class InfoActivity extends BaseActivity {

    private RecyclerView rvFaqs;
    private FAQAdapter faqAdapter;
    private List<FAQ> faqList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        // Initialize RecyclerView
        rvFaqs = findViewById(R.id.rv_faqs);
        rvFaqs.setLayoutManager(new LinearLayoutManager(this));

        // Load FAQs from JSON
        faqList = loadFAQsFromJson();
        faqAdapter = new FAQAdapter(faqList);
        rvFaqs.setAdapter(faqAdapter);
    }

    private List<FAQ> loadFAQsFromJson() {
        List<FAQ> faqs = new ArrayList<>();

        try {
            // Read JSON file from assets
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getAssets().open("faqs.json"))
            );

            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            reader.close();

            // Parse JSON using Gson
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(jsonString.toString(), JsonObject.class);
            JsonArray faqsArray = jsonObject.getAsJsonArray("faqs");

            for (int i = 0; i < faqsArray.size(); i++) {
                JsonObject faqObj = faqsArray.get(i).getAsJsonObject();
                int id = faqObj.get("id").getAsInt();
                String pregunta = faqObj.get("pregunta").getAsString();
                String respuesta = faqObj.get("respuesta").getAsString();

                faqs.add(new FAQ(id, pregunta, respuesta));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return faqs;
    }
}
