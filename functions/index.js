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

// ============================================================================
// HELPER: Background generation with batching
// ============================================================================
async function generateInBackground(params) {
  const { planId, uid, pdfUrl, openaiKey, includeQuizzes, quizLimit, questionsLimit,
    includeFlashcards, flashcardLimit, includeHotTopics, hotTopicsLimit,
    language, quizTypology, priorityPoints, difficulty, inputTitle, membership,
    nivel, dias, objetivo, clientPlanId } = params;

  const db = admin.firestore();
  const now = admin.firestore.FieldValue.serverTimestamp();

  try {
    console.log(`[${planId}] Background generation started`);

    // Download and extract PDF
    let extractedText = '';
    try {
      const pdfResp = await axios.get(pdfUrl, { responseType: 'arraybuffer', timeout: 30000, maxContentLength: 50 * 1024 * 1024 });
      const buffer = Buffer.from(pdfResp.data);
      const parsed = await pdfParse(buffer);
      extractedText = parsed.text || '';
      if (extractedText.length > 30000) {
        extractedText = extractedText.substring(0, 30000) + '\n\n[...truncated for length...]';
      }
      console.log(`[${planId}] PDF extracted: ${extractedText.length} chars`);
    } catch (err) {
      console.error(`[${planId}] PDF extraction failed:`, err.message);
      extractedText = '';
    }

    // Extract organic title from PDF content - improved
    let organicTitle = inputTitle;
    if ((!organicTitle || organicTitle === 'Plan de Estudio' || organicTitle.length < 5) && extractedText) {
      // Get first 1000 chars for better title extraction
      const preview = extractedText.substring(0, 1000);
      const lines = preview.split('\n')
        .map(l => l.trim())
        .filter(l => {
          // Filter out junk: too short, all caps, numbers only, filenames, etc.
          if (l.length < 15 || l.length > 120) return false;
          if (/^[\d\s\.\-_]+$/.test(l)) return false; // Only numbers/punctuation
          if (/\.pdf$/i.test(l)) return false; // Filename
          if (/^[A-Z\s]{10,}$/.test(l)) return false; // ALL CAPS
          if (/tema[-\s]?\d+/i.test(l)) return false; // "tema-29", "Tema 29"
          return true;
        });
      
      if (lines.length > 0) {
        organicTitle = lines[0]
          .replace(/[^a-zA-ZáéíóúÁÉÍÓÚñÑ0-9\s:,.\-]/g, '') // Clean special chars
          .replace(/\s+/g, ' ') // Normalize spaces
          .trim();
        
        // Capitalize first letter
        if (organicTitle.length > 0) {
          organicTitle = organicTitle.charAt(0).toUpperCase() + organicTitle.slice(1).toLowerCase();
        }
        
        if (organicTitle.length > 70) organicTitle = organicTitle.substring(0, 67) + '...';
      }
    }
    
    // Fallback to generic if still empty or invalid
    if (!organicTitle || organicTitle.length < 5) {
      organicTitle = 'Plan de Estudio';
    }
    
    console.log(`[${planId}] Using title: "${organicTitle}"`);

    // Generate organic description from PDF content (150 words max)
    let organicDescription = '';
    if (extractedText && extractedText.length > 100) {
      try {
        console.log(`[${planId}] Generating organic description...`);
        const descriptionPrompt = `Based on the following document content, write a concise, engaging description (maximum 150 words) that summarizes what the document is about. Focus on the main topics, themes, and key concepts. Write in a natural, organic style as if describing the content to a student.

Document content:
${extractedText.substring(0, 3000)}

Generate ONLY the description text, nothing else:`;

        const descriptionResponse = await axios.post(
          OPENAI_URL,
          {
            model: 'claude-sonnet-4-20250514',
            max_tokens: 300,
            temperature: 0.7,
            messages: [
              {
                role: 'user',
                content: descriptionPrompt
              }
            ]
          },
          {
            headers: {
              'Authorization': `Bearer ${openaiKey}`,
              'Content-Type': 'application/json',
              'anthropic-version': '2023-06-01'
            },
            timeout: 30000
          }
        );

        if (descriptionResponse.data?.content?.[0]?.text) {
          organicDescription = descriptionResponse.data.content[0].text.trim();
          
          // Ensure it's not too long (limit to ~150 words)
          const words = organicDescription.split(/\s+/);
          if (words.length > 150) {
            organicDescription = words.slice(0, 150).join(' ') + '...';
          }
          
          console.log(`[${planId}] Generated description: ${organicDescription.length} chars`);
        }
      } catch (err) {
        console.error(`[${planId}] Description generation failed:`, err.message);
        // Fallback to simple excerpt
        organicDescription = extractedText.substring(0, 500).trim();
        if (organicDescription.length >= 500) organicDescription += '...';
      }
    }
    
    // Fallback if no description generated
    if (!organicDescription || organicDescription.length < 20) {
      organicDescription = `Plan de estudio sobre ${organicTitle}`;
    }

    // Build batches: max 3 quizzes per batch
    const QUIZZES_PER_BATCH = 3;
    const batches = [];
    
    if (includeQuizzes && quizLimit > 0) {
      const numBatches = Math.ceil(quizLimit / QUIZZES_PER_BATCH);
      for (let i = 0; i < numBatches; i++) {
        const batchQuizLimit = Math.min(QUIZZES_PER_BATCH, quizLimit - (i * QUIZZES_PER_BATCH));
        batches.push({ type: 'quizzes', quizLimit: batchQuizLimit, questionsLimit, batchIndex: i });
      }
    }
    
    if (includeFlashcards && flashcardLimit > 0) {
      batches.push({ type: 'flashcards', flashcardLimit, batchIndex: 0 });
    }
    
    if (includeHotTopics && hotTopicsLimit > 0) {
      batches.push({ type: 'hotTopics', hotTopicsLimit, batchIndex: 0 });
    }

    console.log(`[${planId}] Created ${batches.length} batches`);

    // Update with organic title, description and batch info
    await db.collection('users').doc(uid).collection('plans').doc(planId).update({
      nombre: organicTitle,
      description: organicDescription,
      'generationProgress.totalBatches': batches.length,
      'generationProgress.completedBatches': 0,
      'generationProgress.failedBatches': 0,
      'generationProgress.percentage': 0
    });

    // Execute batches in parallel with progress tracking
    const batchPromises = batches.map((batch, index) => 
      generateSingleBatch(batch, extractedText, openaiKey, language, quizTypology, priorityPoints, difficulty, planId)
        .then(result => {
          // Update progress after each batch completes
          return db.collection('users').doc(uid).collection('plans').doc(planId).get()
            .then(snapshot => {
              if (snapshot.exists) {
                const currentCompleted = (snapshot.data().generationProgress?.completedBatches || 0) + 1;
                const percentage = Math.round((currentCompleted / batches.length) * 100);
                return db.collection('users').doc(uid).collection('plans').doc(planId).update({
                  'generationProgress.completedBatches': currentCompleted,
                  'generationProgress.percentage': percentage
                }).then(() => {
                  console.log(`[${planId}] Batch ${index} completed. Progress: ${currentCompleted}/${batches.length} (${percentage}%)`);
                  return result;
                });
              }
              return result;
            });
        })
        .catch(err => {
          // Update failed count
          return db.collection('users').doc(uid).collection('plans').doc(planId).get()
            .then(snapshot => {
              if (snapshot.exists) {
                const currentFailed = (snapshot.data().generationProgress?.failedBatches || 0) + 1;
                return db.collection('users').doc(uid).collection('plans').doc(planId).update({
                  'generationProgress.failedBatches': currentFailed
                }).then(() => {
                  console.error(`[${planId}] Batch ${index} failed:`, err.message);
                  throw err;
                });
              }
              throw err;
            });
        })
    );

    const results = await Promise.allSettled(batchPromises);

    // Collect and normalize results using original logic
    const allQuizzes = [];
    const allFlashcards = [];
    const allHotTopics = [];
    let completedCount = 0;
    let failedCount = 0;

    results.forEach((result, index) => {
      if (result.status === 'fulfilled' && result.value) {
        completedCount++;
        const { type, items } = result.value;
        if (type === 'quizzes') allQuizzes.push(...items);
        if (type === 'flashcards') allFlashcards.push(...items);
        if (type === 'hotTopics') allHotTopics.push(...items);
        console.log(`[${planId}] Batch ${index} (${type}) completed: ${items.length} items`);
      } else {
        failedCount++;
        console.error(`[${planId}] Batch ${index} failed:`, result.reason?.message || result.reason);
      }
    });

    // Apply ALL original normalization and cleaning logic
    const normalized = await applyOriginalNormalization({
      quizzes: allQuizzes,
      flashcards: allFlashcards,
      hotTopics: allHotTopics
    }, extractedText, quizTypology, includeQuizzes, quizLimit, questionsLimit,
       includeFlashcards, flashcardLimit, includeHotTopics, hotTopicsLimit);

    const finalStatus = failedCount === batches.length ? 'error' : 
                        failedCount > 0 ? 'partial' : 'completed';

    const metadata = {
      language, quizTypology, priorityPoints, difficulty,
      nivel, dias, objetivo,
      includeQuizzes, quizLimit, questionsLimit,
      includeFlashcards, flashcardLimit,
      includeHotTopics, hotTopicsLimit,
      membership,
      sourcePdfUrl: pdfUrl,
      userId: uid
    };

    const saveDoc = Object.assign({}, metadata, normalized, {
      nombre: organicTitle || inputTitle || 'Plan de Estudio',
      description: organicDescription,
      status: finalStatus,
      'generationProgress.completedBatches': completedCount,
      'generationProgress.failedBatches': failedCount,
      'generationProgress.percentage': finalStatus === 'completed' ? 100 : Math.round((completedCount / batches.length) * 100),
      updatedAt: now
    });

    if (finalStatus === 'error') {
      saveDoc.errorMessage = 'All batches failed';
    } else if (finalStatus === 'partial') {
      saveDoc.errorMessage = `${failedCount} of ${batches.length} batches failed`;
    }

    await db.collection('users').doc(uid).collection('plans').doc(planId).update(saveDoc);

    console.log(`[${planId}] Generation completed. Status: ${finalStatus}`);

  } catch (err) {
    console.error(`[${planId}] Fatal error:`, err);
    await db.collection('users').doc(uid).collection('plans').doc(planId).update({
      status: 'error',
      errorMessage: err.message || 'Unknown error',
      updatedAt: now
    });
  }
}

async function generateSingleBatch(batch, extractedText, openaiKey, language, quizTypology, priorityPoints, difficulty, planId) {
  const { type, batchIndex } = batch;
  
  // Build prompt using ORIGINAL quality rules
  let prompt = `You are an expert educational content generator. Create high-quality study materials in ${language}.

**CRITICAL QUALITY REQUIREMENTS:**
1. Use ONLY the provided PDF text - extract real information
2. Remove ALL PDF artifacts:
   - Filenames (.pdf), URLs, hex tokens (a64b0469ff...)
   - User IDs (user_12345), copyright footers, license text
   - ALL-CAPS headings (convert to sentence case)
   - Isolated punctuation, meaningless fragments
3. Content must be ORGANIC and NATURAL - not robotic
4. Prioritize concepts mentioned in: ${priorityPoints || 'main document themes'}
5. For PDF-based content: set "inferred":false, "evidence":"<snippet from PDF max 250 chars>"
6. For synthesized content: set "inferred":true, "evidence":"synthesized from model knowledge"

**OUTPUT:** Valid JSON array ONLY. NO markdown, NO explanations, NO wrappers.

`;

  if (type === 'quizzes') {
    const { quizLimit, questionsLimit } = batch;
    const isTrueFalse = quizTypology === 'True/False' || quizTypology === 'Verdadero/Falso' || quizTypology === 'V/F';
    const optionsCount = isTrueFalse ? 2 : 4;
    const optionsExample = isTrueFalse ? '["True","False"]' : '["Answer","Distractor 1","Distractor 2","Distractor 3"]';
    
    prompt += `Generate ${quizLimit} quizzes, each with ${questionsLimit} questions.

**CRITICAL TYPOLOGY RULE**: 
- ALL quizzes in this batch MUST use typology="${quizTypology}" (exact string, NO variations like "A", "B", "C", etc.)
- This is MANDATORY - any quiz with different typology will be rejected
- EVERY SINGLE quiz must have "${quizTypology}" as its typology value

**CRITICAL OPTIONS RULE**:
- True/False quizzes: MUST have EXACTLY 2 options: ["True","False"] or ["Verdadero","Falso"]
- Multiple choice quizzes: MUST have EXACTLY 4 options
- NO extra distractor options like "Distractor 1", "Distractor 2" in True/False questions
- Quizzes with wrong number of options will be REJECTED

JSON FORMAT:
[{
  "id":"quiz_batch${batchIndex}_1",
  "titulo":"Descriptive title from PDF content (NOT generic like 'Quiz 1')",
  "typology":"${quizTypology}",
  "progreso":{"preguntasRespondidas":0},
  "questions":[{
    "question":"Specific, clear question (max 220 chars)",
    "options":${optionsExample},
    "answer":"Answer",
    "difficulty":"${difficulty}",
    "inferred":false,
    "evidence":"PDF snippet"
  }]
}]

RULES:
- Questions: Direct, specific (e.g., "¿Qué función cumple X en Y?")
- Options: EXACTLY ${optionsCount} options (${quizTypology}), max 140 chars each
- Answer MUST be one of the options
- Typology: MANDATORY "${quizTypology}" for ALL quizzes - NO EXCEPTIONS, NO VARIATIONS
- NO generic titles like "Quiz generado", "Test 1" - derive from PDF content
- Distractors must be plausible but incorrect`;

  } else if (type === 'flashcards') {
    const { flashcardLimit } = batch;
    prompt += `Generate ${flashcardLimit} flashcards for active recall.

JSON FORMAT:
[{
  "pregunta":"Focused question (max 150 chars)",
  "respuesta":"DETAILED explanation (minimum 120 chars, ideally 150-250). Include context and examples.",
  "completada":false,
  "inferred":false,
  "evidence":"PDF snippet"
}]

RULES:
- Pregunta: Target ONE specific concept
- Respuesta: Multi-sentence explanation with context
  ❌ BAD: "Es cuando una clase hereda de otra"
  ✅ GOOD: "La herencia es un principio POO donde una clase adquiere propiedades de otra, permitiendo reutilización. Por ejemplo, 'Coche' hereda de 'Vehículo'."`;

  } else if (type === 'hotTopics') {
    const { hotTopicsLimit } = batch;
    prompt += `Generate ${hotTopicsLimit} hot topics - key concepts from the PDF.

JSON FORMAT:
[{
  "title":"Concept name (max 100 chars)",
  "description":"Why it matters, what it covers (minimum 100 chars, ideally 120-200)",
  "inferred":false,
  "evidence":"PDF snippet"
}]

RULES:
- Title: Clear, specific (NOT "Tema importante")
- Description: Explain what, why important, how relates to others
- Focus on: ${priorityPoints || 'main themes'}`;
  }

  prompt += `\n\n**PDF CONTENT:**\n${extractedText}\n\n**OUTPUT JSON ARRAY:**`;

  try {
    const response = await axios.post(
      'https://api.openai.com/v1/chat/completions',
      {
        model: 'gpt-4o-mini',
        messages: [
          { role: 'system', content: 'You are an expert educational content creator. Output ONLY valid JSON arrays.' },
          { role: 'user', content: prompt }
        ],
        temperature: 0.7,
        max_tokens: 6000
      },
      {
        headers: { 'Authorization': `Bearer ${openaiKey}`, 'Content-Type': 'application/json' },
        timeout: 120000
      }
    );

    let content = response.data.choices[0].message.content.trim();
    content = content.replace(/^```json\n?/i, '').replace(/\n?```$/, '').trim();
    const items = JSON.parse(content);

    if (!Array.isArray(items)) throw new Error('Response not an array');

    // STRICT POST-GENERATION VALIDATION
    const validatedItems = validateBatchItems(items, type, batch, quizTypology, planId, batchIndex);
    
    if (validatedItems.length === 0) {
      console.error(`[${planId}] ❌ ALL ITEMS REJECTED: All ${items.length} items in batch ${batchIndex} failed validation`);
      throw new Error(`All ${items.length} items failed validation - batch rejected`);
    }
    
    if (validatedItems.length < items.length) {
      console.error(`[${planId}] ⚠️ PARTIAL REJECTION: Batch ${batchIndex} (${type}): ${items.length - validatedItems.length}/${items.length} items rejected by validation`);
    }

    console.log(`[${planId}] ✅ Batch ${batchIndex} (${type}) generated ${validatedItems.length}/${items.length} valid items`);
    return { type, items: validatedItems };

  } catch (err) {
    console.error(`[${planId}] Batch ${batchIndex} (${type}) failed:`, err.message);
    throw err;
  }
}

// STRICT VALIDATION: Ensure items meet minimum quality standards
function validateBatchItems(items, type, batch, quizTypology, planId, batchIndex) {
  const validated = [];
  
  if (type === 'quizzes') {
    const isTrueFalse = quizTypology === 'True/False' || quizTypology === 'Verdadero/Falso' || quizTypology === 'V/F';
    const expectedOptionsCount = isTrueFalse ? 2 : 4;
    
    for (const quiz of items) {
      if (!quiz || typeof quiz !== 'object') continue;
      
      // Validate quiz has questions array
      if (!Array.isArray(quiz.questions) || quiz.questions.length === 0) {
        console.warn(`[${planId}] Quiz "${quiz.titulo || quiz.id}" has no questions - REJECTED`);
        continue;
      }
      
      // Validate each question's options count
      const validQuestions = quiz.questions.filter(q => {
        if (!q || !Array.isArray(q.options)) return false;
        
        // CRITICAL: Check options count matches typology
        if (q.options.length !== expectedOptionsCount) {
          console.error(`[${planId}] ❌ VALIDATION FAILED: Question "${q.question}" has ${q.options.length} options, expected ${expectedOptionsCount} for ${quizTypology} - REJECTED`);
          return false;
        }
        
        // Validate answer is one of the options
        if (!q.answer || !q.options.includes(q.answer)) {
          console.error(`[${planId}] ❌ VALIDATION FAILED: Question "${q.question}" answer "${q.answer}" not in options - REJECTED`);
          return false;
        }
        
        return true;
      });
      
      if (validQuestions.length === 0) {
        console.error(`[${planId}] ❌ QUIZ REJECTED: "${quiz.titulo || quiz.id}" has no valid questions after validation`);
        continue;
      }
      
      // Update quiz with only valid questions
      quiz.questions = validQuestions;
      validated.push(quiz);
    }
    
  } else if (type === 'flashcards') {
    for (const flashcard of items) {
      if (!flashcard || typeof flashcard !== 'object') continue;
      
      // Validate flashcard has both question and answer
      if (!flashcard.pregunta || !flashcard.respuesta) {
        console.warn(`[${planId}] Flashcard missing pregunta or respuesta - REJECTED`);
        continue;
      }
      
      // CRITICAL: Validate respuesta minimum length (120 chars)
      if (flashcard.respuesta.length < 120) {
        console.warn(`[${planId}] Flashcard "${flashcard.pregunta}" respuesta too short (${flashcard.respuesta.length} < 120 chars) - REJECTED`);
        continue;
      }
      
      validated.push(flashcard);
    }
    
  } else if (type === 'hotTopics') {
    for (const topic of items) {
      if (!topic || typeof topic !== 'object') continue;
      
      // Validate topic has both title and description
      if (!topic.title || !topic.description) {
        console.warn(`[${planId}] HotTopic missing title or description - REJECTED`);
        continue;
      }
      
      // CRITICAL: Validate description minimum length (100 chars)
      if (topic.description.length < 100) {
        console.warn(`[${planId}] HotTopic "${topic.title}" description too short (${topic.description.length} < 100 chars) - REJECTED`);
        continue;
      }
      
      validated.push(topic);
    }
  }
  
  return validated;
}

// Apply ALL original normalization logic
async function applyOriginalNormalization(rawData, extractedText, quizTypology, includeQuizzes, quizLimit, questionsLimit, includeFlashcards, flashcardLimit, includeHotTopics, hotTopicsLimit) {
  
  // Original helper functions from the code
  function isJunkText(s) {
    if (!s || typeof s !== 'string') return true;
    const t = s.trim();
    if (t.length < 3) return true;
    if (/^[\.,-\s]+$/.test(t)) return true;
    if (/^[A-Z\s]{4,}$/.test(t) && t.length < 15) return true;
    return false;
  }

  function isMostlyUpper(s) {
    if (!s || typeof s !== 'string') return false;
    const letters = s.replace(/[^A-Za-zÁÉÍÓÚÜÑáéíóúüñ]/g, '');
    if (letters.length < 4) return false;
    const upp = letters.replace(/[^A-ZÁÉÍÓÚÜÑ]/g, '').length;
    return (upp / letters.length) > 0.6;
  }

  function removePdfArtifacts(s) {
    if (!s || typeof s !== 'string') return '';
    let t = s;
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

  function sentenceCase(s) {
    if (!s || typeof s !== 'string') return '';
    let t = s.trim().toLowerCase();
    return t.replace(/(^|[\.\!\?]\s+)([a-záéíóúüñ])/g, (m, p, ch) => p + ch.toUpperCase());
  }

  function normalizeText(s) {
    if (!s && s !== 0) return '';
    let t = String(s).trim();
    t = removePdfArtifacts(t);
    t = t.replace(/\s+/g, ' ').trim();
    if (/^[\W_]+$/.test(t)) return '';
    if (/^[\.,\-\s]+$/.test(t)) return '';
    if (isMostlyUpper(t)) t = sentenceCase(t);
    if (!/[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]/.test(t)) return '';
    return t;
  }

  const doc = { quizzes: [], flashcards: [], hotTopics: [], progreso: { flashcardsEstudiadas: 0, quizzesCompletados: 0 } };

  // Normalize flashcards with strict validation
  if (Array.isArray(rawData.flashcards)) {
    doc.flashcards = rawData.flashcards.map(f => {
      if (!f || typeof f !== 'object') return null;
      let pregunta = normalizeText(f.pregunta || f.question || '');
      let respuesta = normalizeText(f.respuesta || f.answer || '');
      if (isJunkText(pregunta) || isJunkText(respuesta)) return null;
      const lowerQ = (pregunta || '').toLowerCase();
      const isGeneric = lowerQ.includes('generado') || /pregunta\s*\d+/i.test(lowerQ);
      if (!pregunta || !respuesta || isGeneric) return null;
      
      // STRICT: Reject flashcards with answers shorter than 120 chars
      if (respuesta.length < 120) {
        console.warn(`Flashcard "${pregunta}" has short answer (${respuesta.length} chars) - REJECTED`);
        return null;
      }
      
      const inferred = (typeof f.inferred === 'boolean') ? f.inferred : false;
      const evidence = normalizeText(f.evidence || '') || (inferred ? 'synthesized from model knowledge' : '');
      return { pregunta, respuesta, completada: false, inferred, evidence };
    }).filter(Boolean);
  }

  // Normalize hotTopics with strict validation
  if (Array.isArray(rawData.hotTopics)) {
    doc.hotTopics = rawData.hotTopics.map(t => {
      if (!t || typeof t !== 'object') return null;
      let title = normalizeText(t.title || '');
      let description = normalizeText(t.description || '');
      if (!title) return null;
      
      // STRICT: Reject topics with descriptions shorter than 100 chars
      if (description.length < 100) {
        console.warn(`HotTopic "${title}" has short description (${description.length} chars) - REJECTED`);
        return null;
      }
      
      const inferred = (typeof t.inferred === 'boolean') ? t.inferred : false;
      const evidence = normalizeText(t.evidence || '') || (inferred ? 'synthesized from model knowledge' : '');
      return { title, description, inferred, evidence };
    }).filter(Boolean);
  }

  // Normalize quizzes
  if (Array.isArray(rawData.quizzes)) {
    doc.quizzes = rawData.quizzes.map((q, quizIndex) => {
      if (!q || typeof q !== 'object') return null;
      
      // Enforce consistent typology - fix if model provided wrong one
      const actualTypology = q.typology || quizTypology;
      if (actualTypology !== quizTypology) {
        console.warn(`Quiz "${q.titulo || q.id}" has wrong typology "${actualTypology}", forcing to "${quizTypology}"`);
      }
      
      const isTrueFalse = quizTypology === 'True/False' || quizTypology === 'Verdadero/Falso' || quizTypology === 'V/F';
      const expectedOptionsCount = isTrueFalse ? 2 : 4;
      
      const quiz = {
        id: q.id || `quiz_${Date.now()}_${quizIndex}`,
        titulo: normalizeText(q.titulo || q.title || 'Quiz'),
        typology: quizTypology, // Always use the requested typology
        progreso: q.progreso || { preguntasRespondidas: 0 },
        questions: []
      };
      
      if (Array.isArray(q.questions)) {
        quiz.questions = q.questions.map(ques => {
          if (!ques || typeof ques !== 'object') return null;
          let question = normalizeText(ques.question || '');
          let answer = normalizeText(ques.answer || '');
          if (!question || !answer) return null;
          
          let options = [];
          if (Array.isArray(ques.options)) {
            options = ques.options.map(opt => normalizeText(opt)).filter(Boolean);
          }
          
          // CRITICAL: Validate options count matches typology
          if (options.length !== expectedOptionsCount) {
            console.warn(`Question "${question}" has ${options.length} options, expected ${expectedOptionsCount} for ${quizTypology} - REJECTED`);
            return null;
          }
          
          // CRITICAL: Validate answer is in options
          if (!options.includes(answer)) {
            console.warn(`Question "${question}" answer "${answer}" not in options - REJECTED`);
            return null;
          }
          
          const inferred = (typeof ques.inferred === 'boolean') ? ques.inferred : false;
          const evidence = normalizeText(ques.evidence || '') || (inferred ? 'synthesized from model knowledge' : '');
          
          return {
            question,
            options,
            answer,
            difficulty: ques.difficulty || 'Medium',
            inferred,
            evidence
          };
        }).filter(Boolean);
      }
      
      return quiz.questions.length > 0 ? quiz : null;
    }).filter(Boolean);
  }

  return doc;
}

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

  // ============================================================================
  // NEW: Immediate response + background generation
  // ============================================================================
  
  const planId = clientPlanId || uuidv4();
  const now = admin.firestore.FieldValue.serverTimestamp();
  const db = admin.firestore();

  console.log(`[${planId}] Creating basic plan for immediate response...`);

  // Create basic plan document with status='generating'
  const basicPlan = {
    id: planId,
    nombre: inputTitle || 'Plan de Estudio',
    description: '',
    userId: uid,
    language,
    quizTypology,
    priorityPoints,
    difficulty,
    sourcePdfUrl: pdfUrl,
    status: 'generating',
    quizzes: [],
    flashcards: [],
    hotTopics: [],
    progreso: {
      flashcardsEstudiadas: 0,
      quizzesCompletados: 0,
      preguntasCorrectas: 0,
      preguntasRespondidas: 0,
      estimatedStudyTimeMinutes: 0,
      nivel: 1
    },
    generationProgress: {
      totalBatches: 0,
      completedBatches: 0,
      failedBatches: 0,
      percentage: 0
    },
    estimatedStudyTimeMinutes: 0,
    flashcardsEstudiadas: 0,
    nivel,
    dias,
    objetivo,
    membership,
    includeQuizzes,
    quizLimit,
    questionsLimit,
    includeFlashcards,
    flashcardLimit,
    includeHotTopics,
    hotTopicsLimit,
    createdAt: now,
    updatedAt: now
  };

  try {
    await db.collection('users').doc(uid).collection('plans').doc(planId).set(basicPlan);
    console.log(`[${planId}] Basic plan created in users/${uid}/plans/${planId}`);
  } catch (err) {
    console.error(`[${planId}] Failed to create basic plan:`, err);
    throw new functions.https.HttpsError('internal', 'Failed to create plan: ' + err.message);
  }

  console.log(`[${planId}] Basic plan created. Firestore trigger will handle generation.`);
  
  // Return immediately - Firestore trigger will pick up the generation
  return {
    id: planId,
    status: 'generating',
    message: 'Plan creation started. Content generating in background.'
  };
});

// ============================================================================
// Firestore Trigger: Process study plan generation in background
// ============================================================================
exports.processStudyPlanGeneration = functions.runWith({ 
  memory: '8GB', 
  timeoutSeconds: 540,
  secrets: ['OPENAI_KEY']
}).firestore
  .document('users/{userId}/plans/{planId}')
  .onCreate(async (snapshot, context) => {
    const planData = snapshot.data();
    const userId = context.params.userId;
    const planId = context.params.planId;
    
    // Only process plans with status='generating'
    if (planData.status !== 'generating') {
      console.log(`[${planId}] Skipping - status is not 'generating'`);
      return null;
    }
    
    console.log(`[${planId}] Firestore trigger activated. Starting background generation...`);
    
    const openaiKey = process.env.OPENAI_KEY;
    if (!openaiKey) {
      console.error(`[${planId}] No OPENAI_KEY in environment`);
      await snapshot.ref.update({
        status: 'error',
        errorMessage: 'Missing OPENAI_KEY',
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      return null;
    }
    
    try {
      await generateInBackground({
        planId,
        uid: userId,
        pdfUrl: planData.sourcePdfUrl,
        openaiKey,
        includeQuizzes: planData.includeQuizzes,
        quizLimit: planData.quizLimit,
        questionsLimit: planData.questionsLimit,
        includeFlashcards: planData.includeFlashcards,
        flashcardLimit: planData.flashcardLimit,
        includeHotTopics: planData.includeHotTopics,
        hotTopicsLimit: planData.hotTopicsLimit,
        language: planData.language,
        quizTypology: planData.quizTypology,
        priorityPoints: planData.priorityPoints,
        difficulty: planData.difficulty,
        inputTitle: planData.nombre,
        membership: planData.membership,
        nivel: planData.nivel,
        dias: planData.dias,
        objetivo: planData.objetivo,
        clientPlanId: planId
      });
      
      console.log(`[${planId}] Background generation completed via trigger`);
    } catch (err) {
      console.error(`[${planId}] Background generation failed in trigger:`, err);
      await snapshot.ref.update({
        status: 'error',
        errorMessage: err.message || 'Generation failed',
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    }
    
    return null;
  });
