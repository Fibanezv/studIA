# Cloud Function: processDocument

This Cloud Function downloads a file from Firebase Storage, extracts text (PDF via `pdf-parse`), calls OpenAI to produce a strict JSON StudyPlan, writes the plan into Firestore under `users/{uid}/plans/{planId}`, and returns a JSON response.

Deployment

1. Install Firebase CLI and login:

```bash
npm install -g firebase-tools
firebase login
```

2. From `cloud_function` folder install deps:

```bash
cd cloud_function
npm install
```

3. Set OpenAI API key in Firebase functions config (recommended):

```bash
firebase functions:config:set openai.key="YOUR_OPENAI_API_KEY"
```

Or set environment variable `OPENAI_API_KEY` when deploying.

4. Deploy the function:

```bash
firebase deploy --only functions:processDocument
```

Usage (client)

POST JSON to the function URL (the deployed endpoint), for example:

```json
{
  "uid": "USER_UID",
  "storagePath": "users/USER_UID/incoming/incoming_abc123.pdf",
  "title": "Mi plan",
  "constraints": { "quizzes": 2, "questionsPerQuiz": 5 }
}
```

The function returns `{status: 'ok', planId: 'U0xxxxx'}` on success.

Notes

- The function uses `pdf-parse` for PDF extraction. For DOCX/DOC and OCR you should extend the function to use other libraries or Google Document AI for higher fidelity.
- The function requires the Firebase service account to have permission to read Storage and write Firestore (standard in Firebase projects).
- Consider protecting the function (verify Firebase ID tokens) to ensure only authenticated clients can call it.
