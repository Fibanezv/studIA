const functions = require('firebase-functions');
const admin = require('firebase-admin');
const {Storage} = require('@google-cloud/storage');
const axios = require('axios');
const fs = require('fs');
const os = require('os');
const path = require('path');
const pdf = require('pdf-parse');

admin.initializeApp();
const db = admin.firestore();
const storage = new Storage();

// Helper: compute next U0##### id by scanning collection
async function getNextPlanId(uid) {
  const snaps = await db.collection('users').doc(uid).collection('plans').get();
  let max = 0;
  snaps.forEach(ds => {
    const id = ds.id;
    if (!id) return;
    if (id.startsWith('U0')) {
      const num = id.substring(2).replace(/[^0-9]/g, '');
      if (num) {
        const v = parseInt(num, 10);
        if (!isNaN(v) && v > max) max = v;
      }
    }
  });
  return `U0${String(max + 1).padStart(5, '0')}`;
}

exports.processDocument = functions.https.onRequest(async (req, res) => {
  try {
    if (req.method !== 'POST') return res.status(405).send({error: 'Only POST'});
    const body = req.body || {};
    const uid = body.uid;
    const storagePath = body.storagePath;
    const title = body.title || '';
    const constraints = body.constraints || {};
    if (!uid || !storagePath) return res.status(400).send({error: 'Missing uid or storagePath'});

    // Verify Firebase ID token (if provided) to ensure caller is authorized
    try {
      const authHeader = req.get('authorization') || req.get('Authorization') || '';
      const m = authHeader.match(/Bearer\s+(.+)/i);
      if (!m) return res.status(401).send({ error: 'Missing Authorization header with Bearer token' });
      const idToken = m[1];
      const decoded = await admin.auth().verifyIdToken(idToken);
      if (!decoded || decoded.uid !== uid) return res.status(403).send({ error: 'Token uid mismatch' });
    } catch (e) {
      return res.status(401).send({ error: 'Invalid or expired token', details: e.message || String(e) });
    }

    // download the file
    const bucket = admin.storage().bucket();
    const tmpdir = os.tmpdir();
    const tmpFile = path.join(tmpdir, 'download_' + Date.now());
    const file = bucket.file(storagePath);
    await file.download({destination: tmpFile});

    // extract text (pdf-parse only; extend for docx if needed)
    let extractedText = '';
    try {
      const dataBuffer = fs.readFileSync(tmpFile);
      const pdfData = await pdf(dataBuffer);
      extractedText = (pdfData && pdfData.text) ? pdfData.text : '';
    } catch (err) {
      // extraction failed; try fallback: read raw bytes as utf-8
      try { extractedText = fs.readFileSync(tmpFile, 'utf8'); } catch (e) { extractedText = ''; }
    }

    // Build the prompt (strict JSON schema) -- keep it similar to client prompt builder
    const systemPrompt = "Eres un asistente que convierte información en UN SOLO objeto JSON que represente un plan de estudio. DEVUELVE SOLO EL JSON, sin explicaciones.";
    const userPrompt = `TITULO: ${title}\nCONSTRAINTS: ${JSON.stringify(constraints)}\n\nCONTENIDO:\n${extractedText.substring(0, 200000)}`;

    // Call OpenAI
    const OPENAI_KEY = functions.config().openai && functions.config().openai.key ? functions.config().openai.key : process.env.OPENAI_API_KEY;
    if (!OPENAI_KEY) return res.status(500).send({error: 'OpenAI key not configured'});

    // Use chat completions endpoint (adjust model as needed)
    const payload = {
      model: 'gpt-4o-mini',
      messages: [
        {role: 'system', content: systemPrompt},
        {role: 'user', content: userPrompt}
      ],
      max_tokens: 2000
    };

    const openaiResp = await axios.post('https://api.openai.com/v1/chat/completions', payload, {
      headers: { Authorization: `Bearer ${OPENAI_KEY}`, 'Content-Type': 'application/json' }
    });

    const text = openaiResp.data && openaiResp.data.choices && openaiResp.data.choices[0] && openaiResp.data.choices[0].message ? openaiResp.data.choices[0].message.content : null;
    if (!text) return res.status(500).send({error: 'OpenAI returned no content'});

    // Try parse JSON from model output
    let planObj = null;
    try {
      planObj = JSON.parse(text.trim());
    } catch (err) {
      // Try to extract JSON substring
      const m = text.match(/\{[\s\S]*\}/);
      if (m) {
        try { planObj = JSON.parse(m[0]); } catch (e) { planObj = null; }
      }
    }

    if (!planObj) return res.status(500).send({error: 'OpenAI did not return valid JSON', raw: text});

    // Generate plan id and write to Firestore
    const newPlanId = await getNextPlanId(uid);
    planObj.id = newPlanId;

    // add planDocument meta
    const planDocument = {
      name: path.basename(storagePath),
      storagePath: storagePath,
      uploadedAt: admin.firestore.FieldValue.serverTimestamp(),
      status: 'uploaded'
    };

    await db.collection('users').doc(uid).collection('plans').doc(newPlanId).set(planObj);
    await db.collection('users').doc(uid).collection('plans').doc(newPlanId).update({ planDocument });

    // respond success
    return res.status(200).send({ status: 'ok', planId: newPlanId });

  } catch (err) {
    console.error('processDocument error', err);
    try { await db.collection('app_diagnostics').add({ stage: 'processDocument', error: err.message || String(err), timestamp: Date.now() }); } catch (e) {}
    return res.status(500).send({ error: err.message || String(err) });
  }
});
