const functions = require('firebase-functions');
const admin = require('firebase-admin');
const axios = require('axios');
const { v4: uuidv4 } = require('uuid');
const Ajv = require('ajv');
const pdfParse = require('pdf-parse');

admin.initializeApp();

// Put the full JSON Schema you want the model to produce here.
// Replace this placeholder with your actual schema object.
const STUDY_PLAN_SCHEMA = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "StudyPlan",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "language",
    "quizTypology",
    "priorityPoints",
    "difficulty",
    "id",
    "nombre",
    "description",
    "sourcePdfUrl",
    "flashcards",
    "hotTopics",
    "quizzes",
    "progreso",
    "estimatedStudyTimeMinutes",
    "flashcardsEstudiadas",
    "nivel"
  ],
  "properties": {
    "language": { "type": "string" },
    "quizTypology": { "type": "string" },
    "priorityPoints": { "type": "string" },
    "difficulty": { "type": "string" },
    "id": { "type": "string" },
    "nombre": { "type": "string" },
    "description": { "type": "string" },
    "sourcePdfUrl": { "type": "string" },
    "flashcards": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
      "required": ["pregunta", "respuesta", "completada", "inferred", "evidence"],
        "properties": {
          "pregunta": { "type": "string" },
          "respuesta": { "type": "string", "minLength": 60 },
          "completada": { "type": "boolean" }
        ,
          "inferred": { "type": "boolean" },
          "evidence": { "type": "string" }
        }
      }
    },
    "hotTopics": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["title", "description", "inferred", "evidence"],
        "properties": {
          "title": { "type": "string" },
          "description": { "type": "string", "minLength": 80 }
        ,
          "inferred": { "type": "boolean" },
          "evidence": { "type": "string" }
        }
      }
    },
    "quizzes": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["id", "titulo", "typology", "progreso", "questions"],
        "properties": {
          "id": { "type": "string" },
          "titulo": { "type": "string" },
          "typology": { "type": "string" },
          "progreso": {
            "type": "object",
            "additionalProperties": false,
            "properties": {
              "currentIndex": { "type": "integer" },
              "lastUpdatedMillis": { "type": "integer" },
              "nota": { "type": "integer" },
              "preguntasCorrectas": { "type": "integer" },
              "preguntasIncorrectas": { "type": "integer" },
              "preguntasRespondidas": { "type": "integer" }
            }
            ,
            "required": [
              "currentIndex",
              "lastUpdatedMillis",
              "nota",
              "preguntasCorrectas",
              "preguntasIncorrectas",
              "preguntasRespondidas"
            ]
          },
          "questions": {
            "type": "array",
            "items": {
              "type": "object",
              "additionalProperties": false,
                              "required": ["question", "options", "answer", "difficulty", "inferred", "evidence"],
              "properties": {
                "question": { "type": "string" },
                "options": { "type": "array", "items": { "type": "string" }, "minItems": 2, "maxItems": 10 },
                "answer": { "type": "string" }
                      ,
                        "difficulty": { "type": "string" },
                        "inferred": { "type": "boolean" },
                        "evidence": { "type": "string" }
              }
            }
          }
        }
      }
    },
    "progreso": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "estimatedStudyTimeMinutes": { "type": "integer" },
        "flashcardsEstudiadas": { "type": "integer" },
        "nivel": { "type": "integer" },
        "preguntasCorrectas": { "type": "integer" },
        "preguntasRespondidas": { "type": "integer" }
      },
      "required": [
        "estimatedStudyTimeMinutes",
        "flashcardsEstudiadas",
        "nivel",
        "preguntasCorrectas",
        "preguntasRespondidas"
      ]
    },
    "estimatedStudyTimeMinutes": { "type": "integer" },
    "flashcardsEstudiadas": { "type": "integer" },
    "nivel": { "type": "integer" }
  }
};

const OPENAI_URL = 'https://api.openai.com/v1/responses';

// Configure runtime: allow higher memory and longer timeout for large PDFs.
// We will further validate incoming PDF size according to membership level.
// Increase timeout to the platform maximum for first-gen functions (540s) to
// allow slow model responses on large inputs. Note this increases cost for
// long-running executions; consider an async enqueue design for extreme cases.
// Bind the secret into the function runtime so it is available as process.env.OPENAI_KEY
exports.generateStudyPlan = functions.runWith({ memory: '8GB', timeoutSeconds: 540, secrets: ['OPENAI_KEY'] }).https.onCall(async (data, context) => {
  // Support both Secrets Manager (recommended) and legacy functions.config().
  const openaiKey = process.env.OPENAI_KEY || (functions.config().openai && functions.config().openai.key);
  if (!openaiKey) {
    throw new functions.https.HttpsError('failed-precondition', 'OpenAI key not configured. Set it via Secrets Manager: `firebase functions:secrets:set OPENAI_KEY` and redeploy, or set legacy config: `firebase functions:config:set openai.key="YOUR_KEY"`.');
  }

  // Safe debug: log whether the secret is present (do not log the secret value)
  console.log('OPENAI_KEY present=', !!openaiKey);

  const uid = (context.auth && context.auth.uid) || 'anonymous';
  const pdfUrl = data.pdfUrl;
  // Accept optional client-provided plan id and title (worker can allocate canonical planId)
  const clientPlanId = data.clientPlanId;
  const inputTitle = data.inputTitle;
  if (!pdfUrl) throw new functions.https.HttpsError('invalid-argument', 'Missing pdfUrl');

  // Forwarded options from the app
  const nivel = data.nivel || '';
  const dias = data.dias || 0;
  const objetivo = data.objetivo || '';
  const includeQuizzes = Boolean(data.includeQuizzes);
  const quizLimit = Number.isInteger(data.quizLimit) ? data.quizLimit : (data.limits && Number.isInteger(data.limits.quizLimit) ? data.limits.quizLimit : 0);
  const questionsLimit = Number.isInteger(data.questionsLimit) ? data.questionsLimit : (data.limits && Number.isInteger(data.limits.questionsLimit) ? data.limits.questionsLimit : 0);
  const includeFlashcards = Boolean(data.includeFlashcards);
  const flashcardLimit = Number.isInteger(data.flashcardLimit) ? data.flashcardLimit : (data.limits && Number.isInteger(data.limits.flashcardLimit) ? data.limits.flashcardLimit : 0);
  const includeHotTopics = Boolean(data.includeHotTopics);
  const hotTopicsLimit = data.limits && Number.isInteger(data.limits.hotTopicsLimit) ? data.limits.hotTopicsLimit : 0;
  // Optional membership string to enforce size/limits per plan
  const membership = data.membership || 'free';
  // New optional fields from client
  const language = data.language || 'es';
  const quizTypology = data.quizTypology || '';
  const priorityPoints = data.priorityPoints || '';
  const difficulty = data.difficulty || data.level || '';

  // Construct a focused prompt explaining the constraints, schema requirement and
  // precise sanitization / synthesis rules. This prompt instructs the model to
  // produce a single JSON object that conforms to the provided STUDY_PLAN_SCHEMA.
  // Key requirements implemented here:
  // - Use ONLY the provided extracted PDF text for grounding. You MAY enrich
  //   or complete missing items with general domain knowledge, BUT any such
  //   synthesized content MUST be marked: `"inferred": true` and the
  //   `"evidence"` field must either contain a short snippet from the
  //   document (<=250 chars) or the literal string "synthesized from model knowledge".
  // - Remove or avoid PDF/file artefacts: filenames, long hex tokens, license
  //   headers, footer copyright lines, extracted URLs, user ids, isolated
  //   punctuation-only tokens, and headings in ALL-CAPS. Output must be
  //   human-readable (sentence case), free of OCR/license junk.
  // - Enforce minimum content quality: flashcard answers >= 60 chars,
  //   hotTopics.description >= 80 chars, quizzes questions/options readable
  //   (no single-dot or placeholder titles). If original text is too short,
  //   synthesize a didactic, multi-sentence answer and mark it inferred.
  // - For quizzes: follow `quizTypology` exactly to determine the number of
  //   options per question. Options must include the correct answer and be
  //   plausible distractors (paraphrases from document when possible).
  // - Output strictly JSON that fully conforms to the STUDY_PLAN_SCHEMA. Do
  //   not output prose, markdown, or any other wrapper. If you cannot produce
  //   required keys, synthesize them and mark them as inferred with evidence.
  // - Do NOT output any raw model traces, tool logs, or provenance fields.
  const prompt = `IMPORTANT: You will receive the extracted PDF text after this
block. Use ONLY that text to ground any factual assertions. If a requested
section (flashcards, hotTopics, quizzes) cannot be fully obtained verbatim
from the document, you MAY enrich or synthesize missing items using general
domain knowledge, BUT you MUST:

- For any synthesized item set: "inferred": true
- For any synthesized content set evidence to either a short snippet (<=250
  chars) extracted from the document that justifies the item OR the exact
  string "synthesized from model knowledge" (do NOT invent fake citations)
- Ensure flashcard answers are multi-sentence and substantive (>=60 chars)
- Ensure hotTopics.description is informative and >=80 chars
- Ensure quiz question objects include: question, options (exact count determined by the quizTypology parameter), answer, difficulty, inferred, evidence
- Avoid and strip any PDF/file artifacts: filenames (.pdf/.docx), long
  hex tokens, URLs, repeated footer/license lines, 'user_12345' type ids,
  isolated punctuation-only items, or all-caps headings
- Convert text to natural sentence-case and avoid ALL CAPS blocks
- Do not emit placeholder or autogenerated labels like "Quiz generado N"
  — derive descriptive titles from content or from the provided priorityPoints
- Return ONLY a single JSON object that strictly matches the STUDY_PLAN_SCHEMA
  provided to the model (no additional keys, no prose explanation, no
  markdown). Include all top-level schema fields even if empty.

Language: ${language}
Quiz typology requested: ${quizTypology}
Priority points (prefer items referencing these): ${priorityPoints}
Requested difficulty/default: ${difficulty}
Client parameters: nivel=${nivel}, dias=${dias}, objetivo=${objetivo},
includeQuizzes=${includeQuizzes}, quizLimit=${quizLimit}, questionsLimit=${questionsLimit},
includeFlashcards=${includeFlashcards}, flashcardLimit=${flashcardLimit},
includeHotTopics=${includeHotTopics}.

EXTRA GUIDANCE:
- If you use the document to build an item, set "inferred": false and set
  "evidence" to a short exact snippet (<=250 chars) from the document.
- If you synthesize to complete counts, set "inferred": true and set
  "evidence": "synthesized from model knowledge" (or use a short
  document-derived snippet if partially grounded).
- For options/distractors, prefer paraphrases drawn from nearby sentences in
  the document; do not repeat the correct answer verbatim across all options.
- For any text you output: apply sentence-case, remove filenames/URLs/license
  lines and trim whitespace; if the result would be empty or junk, synthesize
  a clear human-style phrase instead and mark inferred=true.

Return only JSON. After this block the extracted PDF text will be appended
and you must use it for grounding. Output must conform to the schema exactly.
`;

  try {
    // Attempt server-side download and text extraction from the PDF URL.
    // This ensures the model receives the PDF content even if it cannot fetch the URL itself.
    let extractedText = null;
    try {
      console.log('Downloading PDF for server-side extraction:', pdfUrl);
      // Allow a longer timeout for slow storage buckets/remote hosts
      const pdfResp = await axios.get(pdfUrl, { responseType: 'arraybuffer', timeout: 120000 });
      const buffer = Buffer.from(pdfResp.data);
      try {
        const parsed = await pdfParse(buffer);
        extractedText = (parsed && parsed.text) ? String(parsed.text) : null;
        if (extractedText) {
          // Truncate to a safe length for the prompt to avoid huge requests
          const MAX_CHARS = 16000;
          if (extractedText.length > MAX_CHARS) extractedText = extractedText.substring(0, MAX_CHARS) + '\n\n<...truncated...>';
          console.log('Extracted text length:', extractedText.length);
        }
      } catch (pe) {
        console.warn('pdf-parse failed:', pe && pe.message ? pe.message : pe);
        extractedText = null;
      }
    } catch (de) {
      console.warn('Failed to download PDF for extraction:', de && de.message ? de.message : de);
      extractedText = null;
    }
    // Validate PDF size (HEAD request). Apply membership limits.
    const membershipLimits = {
      free: { maxBytes: 5 * 1024 * 1024 }, // 5 MB
      pro: { maxBytes: 5 * 1024 * 1024 * 1024 }, // 5 GB
      premium: { maxBytes: 25 * 1024 * 1024 * 1024 } // 25 GB
    };
    const allowed = membershipLimits[membership] || membershipLimits['free'];
    try {
      // HEAD may be slow for some hosts; give it a bit more time
      const head = await axios.head(pdfUrl, { timeout: 30000 });
      const len = head.headers && (head.headers['content-length'] || head.headers['Content-Length']);
      if (len) {
        const size = parseInt(len, 10);
        if (!Number.isNaN(size) && size > allowed.maxBytes) {
          throw new functions.https.HttpsError('failed-precondition', `File too large for membership (${membership}). Max allowed: ${allowed.maxBytes} bytes`);
        }
      }
    } catch (he) {
      // If HEAD fails, continue but warn in logs — do not block, since some URLs don't support HEAD
      console.warn('Could not HEAD pdfUrl, proceeding without size check:', he && he.message);
    }

    // Build the request to OpenAI Responses API with json_schema requirement
    // Build request following the Responses API spec: use text.format for response formatting
    // The Responses API in this project does not accept `input_files` parameter.
    // Instead include the `pdfUrl` in the prompt and rely on the model to fetch it
    // if supported, or the function can be changed to fetch and upload content to OpenAI.
    // If we successfully extracted text, include it in the prompt so the model uses the actual PDF content
    let finalPrompt = prompt;
    if (extractedText) {
      finalPrompt = `${prompt}\n\nExtracted PDF text (truncated):\n${extractedText}`;
    }

    const body = {
      model: 'gpt-4o-mini',
      input: finalPrompt,
      text: {
        format: {
          // 'name' is required by the Responses API for formatted outputs
          // The Responses API expects the schema under text.format.schema
          name: 'studyPlan',
          type: 'json_schema',
          schema: STUDY_PLAN_SCHEMA
        }
      }
    };

    // Log the prompt (not the secret) for debugging
    console.log('Sending prompt (truncated):', prompt.substring(0, Math.min(prompt.length, 500)));

    // helper to call OpenAI Responses API with given prompt body
    async function callOpenAI(reqBody) {
      // Allow a longer timeout for the OpenAI call. The function overall timeout
      // is now 540s, so keep the request timeout below that to allow cleanup.
      const r = await axios.post(OPENAI_URL, reqBody, {
        headers: {
          'Authorization': `Bearer ${openaiKey}`,
          'Content-Type': 'application/json'
        },
        timeout: 240000
      });
      return r.data;
    }

    const dataResp = await callOpenAI(body);
    // Save a truncated copy of the raw OpenAI response for debugging/inspection
    let rawResponseStr = '';
    try {
      rawResponseStr = JSON.stringify(dataResp);
      if (rawResponseStr.length > 20000) rawResponseStr = rawResponseStr.substring(0, 20000) + '...<truncated>';
      console.log('OpenAI response keys:', Object.keys(dataResp || {}));
    } catch (e) {
      rawResponseStr = String(dataResp).substring(0, 20000);
    }

    // Try to extract the JSON schema result from common response shapes.
    let planJson = null;

    // helper: try parse a JSON string or extract first JSON object substring
    function tryParseJsonString(s) {
      if (!s || typeof s !== 'string') return null;
      s = s.trim();
      try {
        return JSON.parse(s);
      } catch (e) {
        // attempt to find first { ... } pair
        const first = s.indexOf('{');
        const last = s.lastIndexOf('}');
        if (first !== -1 && last !== -1 && last > first) {
          const sub = s.substring(first, last + 1);
          try { return JSON.parse(sub); } catch (e2) { /* fallthrough */ }
        }
        // attempt to find first [ ... ] array
        const f2 = s.indexOf('[');
        const l2 = s.lastIndexOf(']');
        if (f2 !== -1 && l2 !== -1 && l2 > f2) {
          const sub2 = s.substring(f2, l2 + 1);
          try { return JSON.parse(sub2); } catch (e3) { /* fallthrough */ }
        }
      }
      return null;
    }

    if (dataResp && dataResp.output && Array.isArray(dataResp.output)) {
      for (const out of dataResp.output) {
        // Direct json_schema typed output (some responses use this)
        if (out.type === 'json_schema' && out.content) {
          // content may be an object or an array with the schema result
          if (typeof out.content === 'string') {
            planJson = tryParseJsonString(out.content) || out.content;
          } else {
            planJson = out.content;
          }
          break;
        }

        // Many responses put the generated text in output[].content[] items
        if (out.content && Array.isArray(out.content)) {
          for (const c of out.content) {
            // If the content item is an object wrapper with a .text field, try to parse it
            if (c && typeof c === 'object') {
              if (typeof c.text === 'string') {
                const parsed = tryParseJsonString(c.text);
                if (parsed) { planJson = parsed; break; }
              }
              // If the object itself already looks like the plan (has keys we expect), take it
              const mayBePlan = (k) => Object.prototype.hasOwnProperty.call(c, k);
              if (mayBePlan('id') || mayBePlan('nombre') || mayBePlan('flashcards') || mayBePlan('quizzes')) {
                planJson = c;
                break;
              }
              continue;
            }

            // If the content item is a plain string, try to parse JSON from it
            if (typeof c === 'string') {
              const parsed = tryParseJsonString(c);
              if (parsed) { planJson = parsed; break; }
            }
          }
          if (planJson) break;
        }
      }
    }

    // Fallback: sometimes API returns `output[0].content[0]` stringified JSON
    if (!planJson && dataResp && dataResp.output && dataResp.output[0] && dataResp.output[0].content && Array.isArray(dataResp.output[0].content)) {
      const c0 = dataResp.output[0].content[0];
      if (typeof c0 === 'string') {
        try { planJson = JSON.parse(c0); } catch (e) { /* ignore */ }
      } else if (typeof c0 === 'object') {
        planJson = c0;
      }
    }

    if (!planJson) {
      // Last resort: try to parse top-level dataResp
      if (typeof dataResp === 'object') planJson = dataResp;
    }

    if (!planJson) {
      // attach raw response to thrown error for easier debugging
      const err = new Error('Could not parse JSON plan from OpenAI response');
      err.rawResponse = rawResponseStr;
      throw err;
    }

    // Use client-provided planId if present (so app can enforce canonical ids like U00001)
    const planId = clientPlanId || uuidv4();
    const now = admin.firestore.FieldValue.serverTimestamp();

    // Try to normalize planJson if it is a string (some API variants return text)
    let planObj = planJson;
    if (typeof planObj === 'string') {
      // Try direct parse
      try {
        planObj = JSON.parse(planObj);
      } catch (e) {
        // Try to extract first JSON object in the text
        const first = planObj.indexOf('{');
        const last = planObj.lastIndexOf('}');
        if (first !== -1 && last !== -1 && last > first) {
          try {
            planObj = JSON.parse(planObj.substring(first, last + 1));
          } catch (e2) {
            // leave as string fallback
            planObj = null;
          }
        } else {
          planObj = null;
        }
      }
    }

    // Extract a storage path from a typical Firebase Storage download URL if possible
    let sourcePath = null;
    try {
      // firebase storage v0 urls often contain '/o/{encodedPath}'
      const m = pdfUrl.match(/\/o\/([^?]+)/);
      if (m && m[1]) {
        sourcePath = decodeURIComponent(m[1]);
      }
    } catch (e) {
      sourcePath = null;
    }

    // Build a minimal metadata object that would have been stored; we will NOT persist it
    // to Firestore per request, instead we log it and return the structured plan.
    const metadata = {
      id: planId,
      sourcePdfUrl: pdfUrl,
      sourcePath: sourcePath || null,
      membership: membership,
      language: language,
      quizTypology: quizTypology,
      priorityPoints: priorityPoints,
      difficulty: difficulty,
      limits: {
        quizLimit: quizLimit,
        questionsLimit: questionsLimit,
        flashcardLimit: flashcardLimit,
        hotTopicsLimit: hotTopicsLimit
      },
      includeQuizzes: includeQuizzes,
      includeFlashcards: includeFlashcards,
      includeHotTopics: includeHotTopics,
      inputTitle: inputTitle || null
    };

    // working container for extracted/normalized fields
    const doc = {};

    // Helper to detect junk/noise text that likely originates from filenames, user ids,
    // table-of-contents labels or OCR artifacts. Returns true if the text should be
    // rejected as content for titles/questions/options.
    function isJunkText(s) {
      if (!s || typeof s !== 'string') return true;
      const t = s.trim();
      if (t.length === 0) return true;
      // Too short
      if (t.length < 3) return true;
      // Single punctuation or dot-only
      if (/^[\W_]+$/.test(t)) return true;
      // Filenames like something.pdf or strings containing file tokens
      if (/\.pdf$/i.test(t) || /\.docx?$/i.test(t) || /tema[-_]?\d+/i.test(t)) return true;
      // user ids like user_12345
      if (/^user[_-]?\w{3,}$/i.test(t)) return true;
      // repeated short tokens or lines that are all uppercase (likely headings/metadata)
      const letters = t.replace(/[^A-Za-zÁÉÍÓÚÜÑáéíóúüñ]/g, '');
      if (letters.length >= 5) {
        const upp = letters.replace(/[^A-ZÁÉÍÓÚÜÑ]/g, '').length;
        if ((upp / letters.length) > 0.75) return true; // mostly uppercase
      }
      // generic placeholders
      const low = t.toLowerCase();
      const generic = ['tema', 'unidad', 'capitulo', 'capítulo', 'pregunta', 'ejemplo'];
      for (const g of generic) if (low === g || low.startsWith(g + ' ') || low.startsWith(g + ':')) return true;
      return false;
    }

    // Detect if a string is mostly uppercase letters (ignoring punctuation)
    function isMostlyUpper(s) {
      if (!s || typeof s !== 'string') return false;
      const letters = s.replace(/[^A-Za-zÁÉÍÓÚÜÑáéíóúüñ]/g, '');
      if (letters.length < 4) return false;
      const upp = letters.replace(/[^A-ZÁÉÍÓÚÜÑ]/g, '').length;
      return (upp / letters.length) > 0.6;
    }

    // Remove obvious PDF/file artifacts, urls, user ids and license lines left over from extraction
    function removePdfArtifacts(s) {
      if (!s || typeof s !== 'string') return '';
      let t = s;
      // filenames like tema-29.pdf or long hex tokens
      t = t.replace(/\b[a-z0-9_-]{4,}\.pdf\b/gi, '');
      t = t.replace(/\b[a-f0-9]{16,}\b/gi, '');
      t = t.replace(/https?:\/\/\S+/gi, '');
      t = t.replace(/user_[0-9]{3,}/gi, '');
      t = t.replace(/Reservados todos los derechos\.?/gi, '');
      t = t.replace(/No se permite la explotación económica ni la transformación de esta obra\.?/gi, '');
      t = t.replace(/Queda permitida la impresión en su totalidad\.?/gi, '');
      t = t.replace(/\s{2,}/g, ' ');
      return t.trim();
    }

    // Convert text that's mostly upper-case into sentence case (simple heuristic)
    function sentenceCase(s) {
      if (!s || typeof s !== 'string') return '';
      let t = s.trim().toLowerCase();
      return t.replace(/(^|[\.\!\?]\s+)([a-záéíóúüñ])/g, (m, p, ch) => p + ch.toUpperCase());
    }

    // Normalize text: remove PDF artifacts, collapse whitespace, remove stray dots-only,
    // convert ALL CAPS to sentence-case, trim and ensure it contains letters.
    function normalizeText(s) {
      if (!s && s !== 0) return '';
      let t = String(s).trim();
      t = removePdfArtifacts(t);
      // Replace multiple newlines/spaces with single space
      t = t.replace(/\s+/g, ' ').trim();
      // Remove leading/trailing punctuation-only strings
      if (/^[\W_]+$/.test(t)) return '';
      // If it's a single dot or comma or dash, treat as empty
      if (/^[\.,\-\s]+$/.test(t)) return '';
      // If mostly uppercase, convert to sentence case
      if (isMostlyUpper(t)) {
        t = sentenceCase(t);
      }
      // Ensure it has at least one letter
      if (!/[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]/.test(t)) return '';
      return t;
    }

    if (planObj && typeof planObj === 'object') {
        // Copy simple numeric/string fields
        const simpleKeys = ['estimatedStudyTimeMinutes', 'flashcardsEstudiadas', 'nivel', 'nombre', 'description'];
        for (const k of simpleKeys) {
          if (planObj[k] !== undefined) doc[k] = planObj[k];
        }

        // Flashcards: copy only explicit valid entries; filter obvious placeholders
        doc.flashcards = [];
        if (Array.isArray(planObj.flashcards)) {
          const filtered = (planObj.flashcards || []).map((f) => {
            if (!f || typeof f !== 'object') return null;
            let pregunta = (f.pregunta || f.question || f.prompt || '').toString().trim();
            let respuesta = (f.respuesta || f.answer || '').toString().trim();
            const completada = !!f.completada;
            const inferred = (typeof f.inferred === 'boolean') ? !!f.inferred : false;
            const evidence = (f.evidence || f.evidencia || '').toString().trim();
            // Normalize text and reject obvious junk (filenames, user ids, headings etc.)
            pregunta = normalizeText(pregunta);
            respuesta = normalizeText(respuesta);
            if (isJunkText(pregunta) || isJunkText(respuesta)) return null;
            // Non-inferred: also reject generic placeholders
            const lowerQ = (pregunta || '').toLowerCase();
            const isGeneric = lowerQ.includes('generado') || /pregunta\s*\d+/i.test(lowerQ) || lowerQ.includes('ejemplo');
            if (!pregunta || !respuesta || isGeneric) return null;
            // Ensure required keys exist per schema: inferred (bool) and evidence (string)
            const evidenceVal = normalizeText(evidence) || (inferred ? 'synthesized from model knowledge' : '');
            return { pregunta, respuesta, completada, inferred: inferred, evidence: evidenceVal };
          }).filter(Boolean);
          doc.flashcards = filtered;
          console.log(`Filtered flashcards: accepted=${filtered.length} of ${planObj.flashcards.length}`);
        } else {
          console.log('No flashcards array in planObj; leaving empty.');
        }

        // hotTopics: accept only entries with title and description
        doc.hotTopics = [];
        if (Array.isArray(planObj.hotTopics)) {
          const ht = planObj.hotTopics.map((t) => {
            if (!t || typeof t !== 'object') return null;
            let title = (t.title || t.titulo || '').toString().trim();
            let description = (t.description || t.descripcion || '').toString().trim();
            const inferred = (typeof t.inferred === 'boolean') ? !!t.inferred : false;
            const evidence = (t.evidence || t.evidencia || '').toString().trim();
            // Normalize and reject junk titles/descriptions (filenames, user ids, dots, uppercase garbage)
            title = normalizeText(title);
            description = normalizeText(description);
            if (!title) return null;
            // If description too short, try to synthesize a longer description using extractedText
            if (!description || description.length < 80) {
              let extra = '';
              try {
                const sents = (extractedText || '').split(/(?<=\.|\?|\!)\s+/).map(s=>s.trim()).filter(Boolean);
                if (sents.length > 0) extra = sents[0];
              } catch (e) { extra = ''; }
              const synthesized = (`Descripción sintetizada: ${description || title}. ${extra}`).trim();
              // ensure minimum length by repeating if needed (cheap fallback)
              description = synthesized.length >= 80 ? synthesized : (synthesized + ' ' + (synthesized.repeat(Math.ceil((80 - synthesized.length)/synthesized.length))).slice(0, 80));
            }
            // Build a safe evidence value: prefer normalized evidence, else synthesize from description when inferred
            let evidenceVal = normalizeText(evidence);
            if (!evidenceVal) {
              if (inferred) {
                evidenceVal = (description && description.substring(0, 250)) || 'synthesized from model knowledge';
              } else {
                evidenceVal = '';
              }
            }
            return { title, description, inferred: inferred, evidence: evidenceVal };
          }).filter(Boolean);
          doc.hotTopics = ht;
          console.log(`Filtered hotTopics: accepted=${ht.length} of ${planObj.hotTopics.length}`);
        } else {
          console.log('No hotTopics array in planObj; leaving empty.');
        }

        // progreso (object)
        if (planObj.progreso && typeof planObj.progreso === 'object') {
          doc.progreso = planObj.progreso;
        }

        // quizzes: normalize, but only include quizzes with valid questions
        doc.quizzes = [];
        if (Array.isArray(planObj.quizzes)) {
          const quizzes = planObj.quizzes.map((q) => {
            if (!q || typeof q !== 'object') return null;
            const _q = {};
            _q.id = q.id || q.quizId || uuidv4();
            _q.titulo = (q.titulo || q.title || '').toString().trim();
            if (q.progreso && typeof q.progreso === 'object') _q.progreso = q.progreso;
              if (Array.isArray(q.questions)) {
              // sanitize questions: remove generic items like 'unidad', 'tema', 'capitulo'
              const forbiddenTokens = ['unidad','tema','capitulo','unidad:','tema:','capítulo','capitulo:'];
              const maxQuestionLen = 220;
              const maxOptionLen = 140;
              const maxAnswerLen = 140;

              const qs = q.questions.map((quest) => {
                let questionText = (quest.question || quest.pregunta || '').toString().trim();
                let options = Array.isArray(quest.options) ? quest.options : (quest.opciones || []);
                let answer = (quest.answer || quest.respuesta || '').toString().trim();
                const inferred = !!quest.inferred;
                const evidence = (quest.evidence || quest.evidencia || '').toString().trim();
                const lowerQ = questionText.toLowerCase();
                // reject items that are just structural labels like 'unidad', 'tema', 'capitulo'
                for (const tok of forbiddenTokens) {
                  if (lowerQ === tok || lowerQ.startsWith(tok + ' ') || lowerQ.startsWith(tok + ':')) return null;
                }
                // Normalize question/answer/options text
                questionText = normalizeText(questionText);
                answer = normalizeText(answer);
                options = (options || []).map(o => normalizeText(o)).filter(Boolean);

                const isGenericQ = (questionText || '').toLowerCase().includes('ejemplo') || /pregunta\s*\d+/i.test(questionText || '') || (questionText || '').toLowerCase().includes('generado');

                if (inferred) {
                  if (!questionText) return null;
                  const evidenceVal = normalizeText(evidence) || '';
                  const qText = questionText.length > maxQuestionLen ? questionText.substring(0, maxQuestionLen) + '...' : questionText;
                  answer = answer.length > maxAnswerLen ? answer.substring(0, maxAnswerLen) + '...' : answer;
                  options = options.map(o => o.length > maxOptionLen ? o.substring(0, maxOptionLen) + '...' : o);
                  // ensure at least one plausible option exists (answer or any option)
                  if (options.length === 0 && !answer) return null;
                  return { question: qText, options, answer, inferred: true, evidence: evidenceVal };
                }

                if (!questionText || (options.length === 0 && !answer) || isGenericQ) return null;

                // normalize/truncate options and ensure answer included
                const qText = questionText.length > maxQuestionLen ? questionText.substring(0, maxQuestionLen) + '...' : questionText;
                options = options.map(o => o.length > maxOptionLen ? o.substring(0, maxOptionLen) + '...' : o);
                answer = answer.length > maxAnswerLen ? answer.substring(0, maxAnswerLen) + '...' : answer;
                if (answer && !options.find(o => String(o) === answer)) options.unshift(answer);
                // dedupe and remove junk/too-short options
                options = Array.from(new Set(options)).filter(o => o && o.length >= 2 && !isJunkText(o));
                if (options.length === 0 && !answer) return null;
                return { question: qText, options, answer };
              }).filter(Boolean);
              if (qs.length) _q.questions = qs; else return null;
            } else return null;
            // ensure quiz has a presentable title
            const titleLower = (_q.titulo || '').toString().toLowerCase();
            const isGenericTitle = !(_q.titulo && _q.titulo.trim().length > 3) || /quiz generado/i.test(titleLower) || /^quiz\b/i.test(titleLower);
            if (isGenericTitle) {
              // derive an orientative title from the first question or priority points
              let derived = null;
              try {
                if (Array.isArray(_q.questions) && _q.questions.length > 0) {
                  const firstQ = _q.questions[0].question || '';
                  if (firstQ && firstQ.length > 0) {
                    // take first 7 words
                    const words = firstQ.split(/\s+/).filter(Boolean).slice(0,7).join(' ');
                    derived = 'Quiz: ' + (words.length > 0 ? words : 'sobre el contenido');
                  }
                }
                if (!derived && priorityPoints && priorityPoints.length > 0) {
                  derived = 'Quiz: ' + priorityPoints.split(',').map(s=>s.trim()).filter(Boolean).slice(0,2).join(', ');
                }
              } catch (e) { derived = null; }
              // If derived title looks like junk, fallback to a generic but harmless label
              if (!derived || isJunkText(derived)) _q.titulo = 'Quiz sobre el contenido'; else _q.titulo = derived;
            }

            return _q;
          }).filter(Boolean);
          doc.quizzes = quizzes;
          console.log(`Filtered quizzes: accepted=${quizzes.length} of ${planObj.quizzes.length}`);
        } else {
          console.log('No quizzes array in planObj; leaving empty.');
        }

      // If the plan object contains an explicit 'sourcePath' or 'source' fields, prefer those
      if (!doc.sourcePath && planObj.sourcePath) doc.sourcePath = planObj.sourcePath;
      if (!doc.sourcePath && planObj.source) doc.sourcePath = planObj.source;

    } else {
      // If we couldn't parse structured JSON, store the raw text so you can inspect it later
      doc.rawText = typeof planJson === 'string' ? planJson : JSON.stringify(planJson);
    }

    // Keep some diagnostic fields in memory and logs (do NOT persist to Firestore)
    const rawOpenAIResponse = rawResponseStr;
    let modelOutputSample = null;
    try {
      if (dataResp && dataResp.output) modelOutputSample = JSON.stringify(dataResp.output).substring(0, 10000);
    } catch (e){}

    // Ensure all required top-level keys exist on a normalized object for validation
    const normalized = {
      id: planId,
      nombre: doc.nombre || inputTitle || planId,
      description: doc.description || '',
      // include client-provided top-level metadata required by schema
      language: language || 'es',
      quizTypology: quizTypology || '',
      priorityPoints: priorityPoints || '',
      difficulty: difficulty || '',
      sourcePdfUrl: doc.sourcePdfUrl || pdfUrl,
      flashcards: Array.isArray(doc.flashcards) ? doc.flashcards : [],
      hotTopics: Array.isArray(doc.hotTopics) ? doc.hotTopics : [],
      quizzes: Array.isArray(doc.quizzes) ? doc.quizzes : [],
      progreso: doc.progreso || {
        estimatedStudyTimeMinutes: doc.estimatedStudyTimeMinutes || 0,
        flashcardsEstudiadas: doc.flashcardsEstudiadas || 0,
        nivel: doc.nivel || 1,
        preguntasCorrectas: (doc.progreso && doc.progreso.preguntasCorrectas) || 0,
        preguntasRespondidas: (doc.progreso && doc.progreso.preguntasRespondidas) || 0
      },
      estimatedStudyTimeMinutes: doc.estimatedStudyTimeMinutes || 0,
      flashcardsEstudiadas: doc.flashcardsEstudiadas || 0,
      nivel: doc.nivel || 1
    };

    // Enforce include flags and numeric limits so the stored plan respects the user's request
    try {
      // If the client explicitly requested sections off, clear them
      if (!includeFlashcards) normalized.flashcards = [];
      if (!includeHotTopics) normalized.hotTopics = [];
      if (!includeQuizzes) normalized.quizzes = [];

      // Trim arrays to requested limits (if > 0)
      if (includeFlashcards && Number.isInteger(flashcardLimit) && flashcardLimit > 0) {
        normalized.flashcards = Array.isArray(normalized.flashcards) ? normalized.flashcards.slice(0, flashcardLimit) : [];
      }
      if (includeHotTopics && Number.isInteger(hotTopicsLimit) && hotTopicsLimit > 0) {
        normalized.hotTopics = Array.isArray(normalized.hotTopics) ? normalized.hotTopics.slice(0, hotTopicsLimit) : [];
      }
      if (includeQuizzes && Number.isInteger(quizLimit) && quizLimit > 0) {
        normalized.quizzes = Array.isArray(normalized.quizzes) ? normalized.quizzes.slice(0, quizLimit) : [];
      }
    } catch (e) {
      console.warn('Failed to enforce limits on normalized plan:', e && e.message ? e.message : e);
    }

    // If arrays are smaller than requested limits, try to fill them using extractedText
    try {
      // helper: split text into sentences
      function sentencesFromText(text) {
        if (!text) return [];
        return text.split(/(?<=\.|\?|\!)\s+/).map(s => s.trim()).filter(Boolean);
      }

      // helper: find sentences that mention any priority point keywords
      function findPrioritySentences(text, points, max) {
        const out = [];
        if (!text) return out;
        const sents = sentencesFromText(text);
        const kws = points.split(',').map(p => p.trim().toLowerCase()).filter(Boolean);
        if (kws.length === 0) return out;
        for (const s of sents) {
          const sl = s.toLowerCase();
          for (const k of kws) {
            if (k.length > 1 && sl.includes(k)) { out.push(s); break; }
          }
          if (out.length >= max) break;
        }
        return out.slice(0, max);
      }

      // fill hotTopics
      if (includeHotTopics && Number.isInteger(hotTopicsLimit) && hotTopicsLimit > 0) {
        const need = hotTopicsLimit - (Array.isArray(normalized.hotTopics) ? normalized.hotTopics.length : 0);
        if (need > 0) {
          const found = findPrioritySentences(extractedText, priorityPoints, need);
          const top = found.length > 0 ? found : sentencesFromText(extractedText).slice(0, need);
          for (let i = 0; i < need && i < top.length; i++) {
            const title = top[i].length > 60 ? top[i].substring(0, 60) + '...' : top[i];
            const description = top[i];
            normalized.hotTopics.push({ title: title, description: description, inferred: true, evidence: (top[i].substring(0, 250)) });
          }
        }
      }

      // fill flashcards
      if (includeFlashcards && Number.isInteger(flashcardLimit) && flashcardLimit > 0) {
        const need = flashcardLimit - (Array.isArray(normalized.flashcards) ? normalized.flashcards.length : 0);
        if (need > 0) {
          const found = findPrioritySentences(extractedText, priorityPoints, need);
          const top = found.length > 0 ? found : sentencesFromText(extractedText).slice(0, need*2);
          for (let i = 0; i < need; i++) {
            const q = top[i] || (top[i*2] || 'Resumen del documento');
            const a = top[i+need] || (top[i*2+1] || q);
            normalized.flashcards.push({ pregunta: q, respuesta: a, completada: false, inferred: true, evidence: (q.substring(0,250)) });
          }
        }
      }

      // fill quizzes: create quizLimit quizzes each with questionsLimit questions
      if (includeQuizzes && Number.isInteger(quizLimit) && quizLimit > 0 && Number.isInteger(questionsLimit) && questionsLimit > 0) {
        const existing = Array.isArray(normalized.quizzes) ? normalized.quizzes.length : 0;
        const needQz = quizLimit - existing;
        const sents = sentencesFromText(extractedText);
        let sentIndex = 0;
        for (let qzi = 0; qzi < needQz; qzi++) {
          const qid = 'gen_qz_' + (existing + qzi + 1);
          const title = 'Quiz generado ' + (existing + qzi + 1);
          const questions = [];
          for (let qi = 0; qi < questionsLimit; qi++) {
            const s = sents[sentIndex] || sents[sentIndex % sents.length] || 'Pregunta sobre el contenido';
            const s2 = sents[(sentIndex+1) % sents.length] || s;
            sentIndex++;
            const questionText = s.length > 120 ? s.substring(0, 120) + '...' : s;
            const answerText = s2;
            const options = []; // typology could be used to generate options later
            questions.push({ question: questionText, options: options, answer: answerText, inferred: true, difficulty: (difficulty || ''), evidence: (s.substring(0,250)) });
          }
          normalized.quizzes.push({ id: qid, titulo: title, typology: quizTypology, progreso: { currentIndex: 0, lastUpdatedMillis: 0, nota: -1, preguntasCorrectas: 0, preguntasIncorrectas: 0, preguntasRespondidas: 0 }, questions: questions });
        }
      }
    } catch (fillErr) {
      console.warn('Failed to fill missing items heuristically:', fillErr && fillErr.message ? fillErr.message : fillErr);
    }

    // If after heuristics arrays are still smaller than requested, attempt to ask the model
    // directly for only the missing items up to a few iterations. This helps ensure counts
    // like 5 quizzes x 10 questions are met by re-invoking the model for the deltas.
    try {
      const maxRetries = 3;
      for (let attempt = 0; attempt < maxRetries; attempt++) {
        const needFlash = (includeFlashcards && Number.isInteger(flashcardLimit) && flashcardLimit > 0) ? (flashcardLimit - normalized.flashcards.length) : 0;
        const needHot = (includeHotTopics && Number.isInteger(hotTopicsLimit) && hotTopicsLimit > 0) ? (hotTopicsLimit - normalized.hotTopics.length) : 0;
        const needQuizzes = (includeQuizzes && Number.isInteger(quizLimit) && quizLimit > 0) ? (quizLimit - normalized.quizzes.length) : 0;
        let missingQuestionsPerQuiz = 0;
        // If there are quizzes present but some have fewer questions than requested, compute total missing questions
        if (includeQuizzes && Number.isInteger(questionsLimit) && questionsLimit > 0) {
          let totalMissing = 0;
          for (const q of normalized.quizzes) {
            if (q && Array.isArray(q.questions)) {
              totalMissing += Math.max(0, questionsLimit - q.questions.length);
            }
          }
          // plus quizzes to add * questionsLimit
          totalMissing += needQuizzes * questionsLimit;
          missingQuestionsPerQuiz = totalMissing;
        }

        if (needFlash <= 0 && needHot <= 0 && needQuizzes <= 0 && missingQuestionsPerQuiz <= 0) break;

        // Build a focused follow-up prompt requesting only the missing items
        const follow = `Please return only the JSON arrays needed to fill missing items.` +
          ` Return an object with any of these keys that you are asked to produce: flashcards (array), hotTopics (array), quizzes (array).` +
          ` For quizzes, produce complete quiz objects with questions arrays; ensure each quiz has exactly ${questionsLimit} questions.` +
          ` Produce up to ${needFlash} flashcards, up to ${needHot} hotTopics, and up to ${needQuizzes} quizzes each with ${questionsLimit} questions.` +
          ` Use the same flashcard/hotTopic/quiz item formats as before. Mark generated items with \"inferred\": true and include \"evidence\" snippets when possible.` +
          ` IMPORTANT: If you cannot find enough items in the provided document, synthesize the remaining items using your general knowledge about the document's topic (do NOT invent fake citations). For synthesized items set \"inferred\": true and set \"evidence\" to a short note like \"synthesized from model knowledge\" or a short supporting sentence. Return items written in ${language}.` +
          ` Prioritize these priority points: ${priorityPoints}. Use the extracted PDF text below when available:\n\n${extractedText || ''}`;

        const followBody = Object.assign({}, body, { input: follow });
        const followResp = await callOpenAI(followBody);

        // try to extract and parse JSON from followResp same as earlier
        let followJson = null;
        try {
          const outArr = followResp && followResp.output ? followResp.output : null;
          if (outArr && Array.isArray(outArr)) {
            for (const o of outArr) {
              if (o && Array.isArray(o.content)) {
                for (const c of o.content) {
                  if (typeof c === 'string') {
                    const parsed = tryParseJsonString(c);
                    if (parsed) { followJson = parsed; break; }
                  } else if (typeof c === 'object') {
                    const mayBePlan = (k) => Object.prototype.hasOwnProperty.call(c, k);
                    if (mayBePlan('flashcards') || mayBePlan('hotTopics') || mayBePlan('quizzes')) { followJson = c; break; }
                  }
                }
                if (followJson) break;
              }
            }
          }
        } catch (e) { /* ignore */ }

        if (!followJson) continue;

        // Normalize followJson and append items if valid
        try {
          if (followJson.flashcards && Array.isArray(followJson.flashcards)) {
            for (const f of followJson.flashcards) {
              if (f && typeof f === 'object') {
                const pregunta = f.pregunta || f.question || '';
                const respuesta = f.respuesta || f.answer || '';
                if (pregunta && respuesta) normalized.flashcards.push({ pregunta: String(pregunta), respuesta: String(respuesta), completada: false, inferred: true, evidence: (f.evidence || '').toString() });
              }
            }
          }
          if (followJson.hotTopics && Array.isArray(followJson.hotTopics)) {
            for (const h of followJson.hotTopics) {
              if (h && typeof h === 'object') {
                const title = h.title || h.titulo || '';
                const desc = h.description || h.descripcion || '';
                if (title) normalized.hotTopics.push({ title: String(title), description: String(desc || ''), inferred: true, evidence: (h.evidence || '').toString() });
              }
            }
          }
          if (followJson.quizzes && Array.isArray(followJson.quizzes)) {
            for (const q of followJson.quizzes) {
              if (q && typeof q === 'object') {
                const qid = q.id || uuidv4();
                const titulo = q.titulo || q.title || 'Quiz generado';
                const questions = [];
                if (Array.isArray(q.questions)) {
                  for (const qq of q.questions) {
                    const questionText = qq.question || qq.pregunta || '';
                    const answerText = qq.answer || qq.respuesta || '';
                    const options = Array.isArray(qq.options) ? qq.options : (qq.opciones || []);
                    if (questionText && (answerText || options.length > 0)) {
                      // ensure required fields exist for each generated question
                      const qAns = String(answerText || (options.length>0?options[0]:''));
                      const qOpts = (options || []).map(o=>String(o));
                      questions.push({ question: String(questionText), options: qOpts, answer: qAns, inferred: true, difficulty: (qq.difficulty || difficulty || 'Principiante'), evidence: (qq.evidence||'synthesized from model knowledge').toString() });
                    }
                  }
                }
                if (questions.length > 0) normalized.quizzes.push({ id: qid, titulo: String(titulo), typology: quizTypology, progreso: { currentIndex: 0, lastUpdatedMillis: 0, nota: -1, preguntasCorrectas: 0, preguntasIncorrectas: 0, preguntasRespondidas: 0 }, questions: questions });
              }
            }
          }
        } catch (appendErr) {
          console.warn('Failed to append follow-up generated items:', appendErr && appendErr.message ? appendErr.message : appendErr);
        }

        // After appending, trim again to requested sizes
        if (includeFlashcards && Number.isInteger(flashcardLimit) && flashcardLimit > 0) normalized.flashcards = normalized.flashcards.slice(0, flashcardLimit);
        if (includeHotTopics && Number.isInteger(hotTopicsLimit) && hotTopicsLimit > 0) normalized.hotTopics = normalized.hotTopics.slice(0, hotTopicsLimit);
        if (includeQuizzes && Number.isInteger(quizLimit) && quizLimit > 0) normalized.quizzes = normalized.quizzes.slice(0, quizLimit);
      }
    } catch (retryErr) {
      console.warn('Follow-up generation attempts failed:', retryErr && retryErr.message ? retryErr.message : retryErr);
    }

    // Validate the final normalized document against the STUDY_PLAN_SCHEMA and log differences
    try {
      // Ensure each quiz's questions have the expected number of options according to typology
      function typologyToCount(typ) {
        if (!typ) return 4; // default to 4 options
        const t = String(typ).toLowerCase();
        if (t.includes("true") || t.includes("false") || t.includes("tf") || t.includes("verdadero") || t.includes("falso")) return 2;
        if (t.indexOf(',') !== -1) {
          return t.split(',').map(s => s.trim()).filter(Boolean).length || 4;
        }
        // fallback: if contains letters like 'a b c d'
        const letters = (t.match(/[a-d]/g) || []).length;
        if (letters > 0) return letters;
        return 4;
      }

      // helper to pick additional distractors from extractedText sentences
      function pickDistractors(existing, needed, extractedText) {
        const outs = [];
        if (!extractedText || extractedText.length === 0) {
          // fallback: create generic distractors
          for (let i = 0; i < needed; i++) outs.push('Opción ' + (i+1));
          return outs;
        }
        const sents = sentencesFromText(extractedText);
        for (let i = 0; i < sents.length && outs.length < needed; i++) {
          const cand = sents[i];
          // avoid duplicates and existing answer text
          const low = cand.toLowerCase();
          let dup = false;
          for (const e of existing) if (e && low.includes(String(e).toLowerCase())) { dup = true; break; }
          if (!dup && cand.length > 10) outs.push(cand.length > 140 ? cand.substring(0,137)+'...' : cand);
        }
        // pad with generic options if still missing
        for (let i = outs.length; outs.length < needed; i++) outs.push('Opción ' + (i+1));
        return outs;
      }

      try {
        for (let qi = 0; qi < normalized.quizzes.length; qi++) {
          const quiz = normalized.quizzes[qi];
          const needOptions = typologyToCount(quiz.typology || quizTypology);
          if (!Array.isArray(quiz.questions)) continue;
          for (let qj = 0; qj < quiz.questions.length; qj++) {
            const ques = quiz.questions[qj];
            if (!ques) continue;
            // ensure options array exists
            if (!Array.isArray(ques.options)) ques.options = [];
            // ensure answer present
            if (!ques.answer || String(ques.answer).trim().length === 0) {
              // if no explicit answer, set the first option as answer if exists
              if (ques.options.length > 0) ques.answer = ques.options[0]; else ques.answer = 'Respuesta';
            }
            // If options fewer than needed, add distractors
            const existing = ques.options.slice();
            // ensure the correct answer is included in options
            const ans = String(ques.answer);
            if (!existing.find(o => String(o) === ans)) existing.unshift(ans);
            if (existing.length < needOptions) {
              const more = pickDistractors(existing, needOptions - existing.length, extractedText || '');
              existing.push(...more);
            }
            // truncate to exact needed count
            ques.options = existing.slice(0, needOptions);
            // ensure answer still present; if not, put it at index 0
            if (!ques.options.find(o => String(o) === ans)) {
              ques.options[0] = ans;
            }
          }
        }
      } catch (enf) { console.warn('Error enforcing quiz typology/options:', enf); }

      // Ensure flashcard answers are sufficiently descriptive; if short, try to expand using extractedText
      try {
        function extendFlashAnswer(answer, question, extractedText) {
          if (!extractedText) return answer;
          if (!question && answer && answer.length > 80) return answer;
          const sents = sentencesFromText(extractedText);
          // try to find sentences that contain key tokens from question or answer
          const tokens = (question || '').split(/\W+/).map(t => t.trim().toLowerCase()).filter(t => t.length > 3);
          let found = '';
          for (const s of sents) {
            const sl = s.toLowerCase();
            for (const tk of tokens) {
              if (sl.includes(tk) && s.length > (answer ? answer.length : 20)) { found = s; break; }
            }
            if (found) break;
          }
          if (!found && sents.length > 0) found = sents[0];
          if (found) {
            if (!answer || answer.length < 30) return (answer ? answer + ' ' : '') + found;
            // append for more depth
            return answer + ' ' + (found.length > 250 ? found.substring(0,250) : found);
          }
          return answer;
        }

        if (Array.isArray(normalized.flashcards)) {
          for (let i = 0; i < normalized.flashcards.length; i++) {
            const f = normalized.flashcards[i];
            if (!f) continue;
            const ans = String(f.respuesta || '');
            if (ans.length < 60) {
              const q = String(f.pregunta || '');
              const ext = extendFlashAnswer(ans, q, extractedText || '');
              f.respuesta = ext;
            }
          }
        }
      } catch (fe) { console.warn('Error extending flashcard answers:', fe); }

      const ajv = new Ajv({ allErrors: true, strict: false });
      const validate = ajv.compile(STUDY_PLAN_SCHEMA);
      const valid = validate(normalized);
      if (!valid) {
        console.warn('Plan validation failed. AJV errors:', validate.errors);
        // attach validation errors to the document for later inspection
        doc.validationErrors = validate.errors;
      } else {
        console.log('Plan validation passed against STUDY_PLAN_SCHEMA');
      }
    } catch (ve) {
      console.warn('Plan validation threw error:', ve && ve.message ? ve.message : ve);
    }

    // Log the full exchange for debugging: what we received, what we sent to the model,
    // the raw response, and the normalized plan that will be stored.
    console.log('generateStudyPlan - request metadata:', JSON.stringify(metadata));
    console.log('generateStudyPlan - finalPrompt (truncated):', finalPrompt.substring(0, 2000));
    console.log('generateStudyPlan - rawOpenAIResponse (truncated):', rawOpenAIResponse);
    console.log('generateStudyPlan - modelOutputSample (truncated):', modelOutputSample);
    try {
      console.log('generateStudyPlan - normalizedPlan:', JSON.stringify(normalized).substring(0, 20000));
    } catch (e) { console.log('generateStudyPlan - normalizedPlan: <could not stringify>'); }

    // Persist sanitized normalized plan to Firestore so the client and UI can observe it.
    // Prepare document to save, but do NOT include large raw OpenAI blobs (keep them in logs only)
    const saveDoc = Object.assign({}, metadata, normalized, {
      validationErrors: doc.validationErrors || null,
      createdAt: now
    });

    try {
      await admin.firestore().collection('users').doc(uid).collection('plans').doc(planId).set(saveDoc);
      console.log('generateStudyPlan - saved plan to Firestore at users/' + uid + '/plans/' + planId + ' (rawOpenAIResponse and modelOutputSample NOT persisted)');
    } catch (fsErr) {
      console.warn('generateStudyPlan - failed to save plan to Firestore:', fsErr && fsErr.message ? fsErr.message : fsErr);
    }

    return { id: planId, plan: normalized };
  } catch (err) {
    console.error('generateStudyPlan error:', err.response ? err.response.data : err.message || err);
    throw new functions.https.HttpsError('internal', 'Failed to generate study plan: ' + (err.message || 'unknown'));
  }
});
