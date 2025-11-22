package com.app.estudiaapp;

import android.os.Bundle;
import android.widget.Toast;

public class LogoutActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logout);

        findViewById(R.id.btn_confirm_logout).setOnClickListener(v -> {
            // Placeholder: clear user state if any and return to MainActivity
            Toast.makeText(this, "Cerrar sesión (placeholder)", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
