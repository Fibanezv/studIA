const functions = require('firebase-functions');
const admin = require('firebase-admin');
const express = require('express');
const cors = require('cors');
const axios = require('axios');

admin.initializeApp();

const app = express();
app.use(cors({ origin: true }));
app.use(express.json({ limit: '5mb' }));

// Helper: verify Firebase ID token from Authorization header
async function verifyFirebaseToken(req) {
  const authHeader = req.headers.authorization || '';
  if (!authHeader.startsWith('Bearer ')) throw new functions.https.HttpsError('unauthenticated', 'Missing Authorization header');
  const idToken = authHeader.split('Bearer ')[1];
  try {
    const decoded = await admin.auth().verifyIdToken(idToken);
    return decoded;
  } catch (e) {
    throw new functions.https.HttpsError('unauthenticated', 'Invalid ID token');
  }
}

// POST /generateStudyPlan
// Body: { text: string } OR { chunks: [string] }
app.post('/generateStudyPlan', async (req, res) => {
  try {
    await verifyFirebaseToken(req);
    const cfg = functions.config && functions.config().openai;
    const key = (cfg && cfg.key) || process.env.OPENAI_API_KEY;
    if (!key) return res.status(500).json({ error: 'OpenAI key not configured' });

    const body = req.body || {};
    let promptText = '';
    if (Array.isArray(body.chunks)) {
      // simple approach: concatenate with separators
      promptText = body.chunks.join('\n\n');
    } else if (typeof body.text === 'string') {
      promptText = body.text;
    } else {
      return res.status(400).json({ error: 'Missing text or chunks in body' });
    }

    // Build OpenAI request - adjust model/params as needed
    const openaiReq = {
      model: 'gpt-4o-mini',
      messages: [
        { role: 'system', content: 'You are an assistant that produces a strict JSON StudyPlan object. Respond only with valid JSON.' },
        { role: 'user', content: promptText }
      ],
      temperature: 0.2,
      max_tokens: 1500
    };

    const r = await axios.post('https://api.openai.com/v1/chat/completions', openaiReq, {
      headers: {
        'Authorization': `Bearer ${key}`,
        'Content-Type': 'application/json'
      },
      timeout: 60000
    });

    // Return the full OpenAI response and the assistant text
    const assistantText = (r.data && r.data.choices && r.data.choices[0] && r.data.choices[0].message && r.data.choices[0].message.content) || '';
    return res.json({ success: true, assistant: assistantText, raw: r.data });
  } catch (err) {
    console.error('Error in generateStudyPlan:', err && err.message ? err.message : err);
    if (err instanceof functions.https.HttpsError) {
      return res.status(err.code === 'unauthenticated' ? 401 : 400).json({ error: err.message });
    }
    return res.status(500).json({ error: (err && err.message) || 'internal' });
  }
});

exports.api = functions.https.onRequest(app);
/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

const {setGlobalOptions} = require("firebase-functions");
const {onRequest} = require("firebase-functions/https");
const logger = require("firebase-functions/logger");

// For cost control, you can set the maximum number of containers that can be
// running at the same time. This helps mitigate the impact of unexpected
// traffic spikes by instead downgrading performance. This limit is a
// per-function limit. You can override the limit for each function using the
// `maxInstances` option in the function's options, e.g.
// `onRequest({ maxInstances: 5 }, (req, res) => { ... })`.
// NOTE: setGlobalOptions does not apply to functions using the v1 API. V1
// functions should each use functions.runWith({ maxInstances: 10 }) instead.
// In the v1 API, each function can only serve one request per container, so
// this will be the maximum concurrent request count.
setGlobalOptions({ maxInstances: 10 });

// Create and deploy your first functions
// https://firebase.google.com/docs/functions/get-started

// exports.helloWorld = onRequest((request, response) => {
//   logger.info("Hello logs!", {structuredData: true});
//   response.send("Hello from Firebase!");
// });
