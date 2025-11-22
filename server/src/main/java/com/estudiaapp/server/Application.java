package com.estudiaapp.server;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.FileInputStream;
import java.io.InputStream;

@SpringBootApplication
public class Application {

    public static void main(String[] args) throws Exception {
        // Initialize Firebase Admin if service account provided via env var
        String saPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (saPath != null && !saPath.isEmpty()) {
            try (InputStream serviceAccount = new FileInputStream(saPath)) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                FirebaseApp.initializeApp(options);
                System.out.println("Firebase Admin initialized");
            }
        } else {
            System.out.println("GOOGLE_APPLICATION_CREDENTIALS not set; Firebase Admin not initialized");
        }

        SpringApplication.run(Application.class, args);
    }
}
