# EstudiaApp Extraction Server (Java)

This is a minimal Spring Boot server that downloads a file (PDF/DOCX), extracts text using Apache Tika, calls OpenAI to produce a structured study plan JSON, and writes the result to Firestore using the Firebase Admin SDK.

Important notes
- This server requires a Google service account JSON for Firebase Admin operations. Set the env var `GOOGLE_APPLICATION_CREDENTIALS` to point to the JSON file.
- Set `OPENAI_API_KEY` env var with your OpenAI API key.

Build & run

1. Build with Maven (Java 11+):

```powershell
cd server
mvn -U package
```

2. Run (ensure env vars set):

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS = 'C:\path\to\service-account.json'
$env:OPENAI_API_KEY = 'sk-...'
java -jar target/extraction-server-0.1.0.jar
```

API

POST /api/extract-process
Content-Type: application/json

Body:
```
{
  "fileUrl": "https://.../file.pdf",
  "uid": "firebaseUid",
  "planId": "plan-uuid",
  "constraints": {
    "quizzes": 2,
    "questionsPerQuiz": 5,
    "flashcards": 10,
    "hotTopics": 3
  }
}
```

Response: JSON string describing processed/saved info or an error.

Security
- Do not expose this server to the public without authentication. At minimum, restrict access to your Android app via a network-level firewall, API key, or an auth token. In production, validate and authenticate requests.

Notes
- Firestore writing uses the Admin SDK and will create/update the document `users/{uid}/plans/{planId}`.
- If OpenAI returns unparsable JSON, the server saves `raw_response` and sets `status: needs_review` so the app can surface this to the user.
