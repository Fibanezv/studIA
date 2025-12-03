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

const ANTHROPIC_URL = 'https://api.anthropic.com/v1/messages';

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

    // Generate organic title using Claude for better quality
    let organicTitle = inputTitle;
    if ((!organicTitle || organicTitle === 'Plan de Estudio' || organicTitle.length < 5) && extractedText && extractedText.length > 100) {
      try {
        console.log(`[${planId}] Generating organic title with Claude...`);
        const titlePrompt = `Read this document carefully and create a SHORT, specific title that captures the main topic.

**CRITICAL RULES:**
- Maximum 60 characters
- NO URLs, NO website names (www.), NO "fecha actualización"
- NO metadata like "TEMA 11", "Página X", copyright notices
- NO generic phrases: avoid "Plan de", "Temario de", "Curso de" at the start
- BE SPECIFIC: What is this document actually about?
- Natural language: Write like a human, not a file name
- In ${language === 'Inglés' ? 'English' : 'Spanish'}

Examples of GOOD titles:
- "Letrado de Administración de Justicia: Funciones"
- "Programación Java: Fundamentos y Estructuras"
- "Derecho Procesal: Recursos y Apelaciones"

Document content:
${extractedText.substring(0, 2500)}

Generate ONLY the title (max 60 chars):`;

        const titleResponse = await axios.post(
          'https://api.openai.com/v1/chat/completions',
          {
            model: 'gpt-4o',
            messages: [
              { role: 'system', content: 'You are an expert at creating concise, descriptive titles. Output ONLY the title text, nothing else.' },
              { role: 'user', content: titlePrompt }
            ],
            max_tokens: 40,
            temperature: 0.3
          },
          {
            headers: {
              'Authorization': `Bearer ${openaiKey}`,
              'Content-Type': 'application/json'
            },
            timeout: 20000
          }
        );

        if (titleResponse.data?.choices?.[0]?.message?.content) {
          let generatedTitle = titleResponse.data.choices[0].message.content.trim();
          
          // Aggressive cleanup
          generatedTitle = generatedTitle
            .replace(/^["'«»]|["'«»]$/g, '') // Remove quotes
            .replace(/^(Título|Title):\s*/i, '') // Remove prefix
            .replace(/www\.[^\s]+/gi, '') // Remove URLs
            .replace(/fecha actualización[^.!?]*/gi, '') // Remove dates
            .replace(/página \d+/gi, '') // Remove page numbers
            .replace(/TEMA \d+:?\s*/gi, '') // Remove "TEMA 11"
            .replace(/^[\d\.\-\s]+/, '') // Remove leading numbers/punctuation
            .replace(/\s+/g, ' ') // Normalize spaces
            .trim();
          
          // Strict validation
          const isValid = 
            generatedTitle.length >= 15 && 
            generatedTitle.length <= 70 && 
            !/^[A-Z\s]{15,}$/.test(generatedTitle) && // Not all caps
            !/www\.|http/i.test(generatedTitle) && // No URLs
            !/fecha|actualización/i.test(generatedTitle) && // No metadata
            !/^(plan de|temario|curso)\s/i.test(generatedTitle) && // No generic start
            /[a-záéíóúñA-ZÁÉÍÓÚÑ]{3,}/.test(generatedTitle); // Has real words
          
          if (isValid) {
            organicTitle = generatedTitle;
            console.log(`[${planId}] ✓ Generated title: "${organicTitle}"`);
          } else {
            console.warn(`[${planId}] ✗ Generated title rejected: "${generatedTitle}"`);
          }
        }
      } catch (err) {
        console.error(`[${planId}] Title generation failed:`, err.message);
      }
    }
    
    // Fallback to generic if still empty or invalid
    if (!organicTitle || organicTitle.length < 10) {
      organicTitle = 'Plan de Estudio';
    }
    
    console.log(`[${planId}] Final title: "${organicTitle}"`);

    // Generate organic description from PDF content (75 words max)
    let organicDescription = '';
    if (extractedText && extractedText.length > 100) {
      try {
        console.log(`[${planId}] Generating organic description...`);
        
        const descriptionPrompt = `Read this document and write a natural, engaging summary (MAXIMUM 35 words) about what it covers.

**CRITICAL RULES:**
- Write in ${language === 'Inglés' ? 'English' : 'Spanish'}
- Focus ONLY on the CONTENT: what topics, concepts, and subjects are covered
- Write like you're explaining to a student what they'll learn
- Use natural, conversational language
- NO configuration details (no mention of quizzes, flashcards, exercises, etc.)
- NO metadata: NO URLs (www.), NO "fecha actualización", NO page numbers, NO copyright
- NO document artifacts: NO "TEMA X", NO headers/footers
- NO generic phrases like "Este plan incluye" or "Aprenderás sobre"
- Maximum 35 words - be concise and impactful!

Examples of GOOD descriptions:
- "Explora las funciones del Letrado de la Administración de Justicia según la Ley Orgánica del Poder Judicial. Cubre competencias, ordenación del cuerpo jurídico, secretarios de gobierno y coordinadores provinciales. Incluye el régimen de sustituciones y habilitaciones."
- "Fundamentos de programación Java desde cero. Cubre sintaxis básica, estructuras de control, programación orientada a objetos, herencia, polimorfismo, manejo de excepciones y colecciones. Ideal para principiantes que quieren dominar Java."

Document content:
${extractedText.substring(0, 4000)}

Write ONLY the description (max 35 words), nothing else:`;

        const descriptionResponse = await axios.post(
          'https://api.openai.com/v1/chat/completions',
          {
            model: 'gpt-4o',
            messages: [
              { role: 'system', content: 'You are an expert at writing clear, engaging educational content. Output ONLY the description text, nothing else.' },
              { role: 'user', content: descriptionPrompt }
            ],
            max_tokens: 250,
            temperature: 0.6
          },
          {
            headers: {
              'Authorization': `Bearer ${openaiKey}`,
              'Content-Type': 'application/json'
            },
            timeout: 30000
          }
        );

        if (descriptionResponse.data?.choices?.[0]?.message?.content) {
          organicDescription = descriptionResponse.data.choices[0].message.content.trim();
          
          // Aggressive cleanup of artifacts
          organicDescription = organicDescription
            .replace(/^["'«»]|["'«»]$/g, '') // Remove quotes
            .replace(/www\.[^\s]+/gi, '') // Remove URLs
            .replace(/https?:\/\/[^\s]+/gi, '') // Remove URLs
            .replace(/fecha actualización[^.!?]*/gi, '') // Remove dates
            .replace(/copyright[^.!?]*/gi, '') // Remove copyright
            .replace(/página \d+ de \d+/gi, '') // Remove page numbers
            .replace(/\d+ de \w+ de \d{4}/gi, '') // Remove dates
            .replace(/TEMA \d+[:\s]*/gi, '') // Remove "TEMA 11:"
            .replace(/^(Descripción|Description):\s*/i, '') // Remove prefix
            .replace(/\s+/g, ' ') // Normalize spaces
            .trim();
          
          // Limit to 35 words
          const words = organicDescription.split(/\s+/);
          if (words.length > 35) {
            organicDescription = words.slice(0, 35).join(' ') + '...';
          }
          
          // Strict validation - must be real content
          const isValidDescription = 
            organicDescription.length >= 60 && // Minimum length
            organicDescription.length <= 600 && // Maximum characters
            !/^[A-Z\s]{30,}$/.test(organicDescription) && // Not all caps
            !/www\.|http/i.test(organicDescription) && // No URLs
            !/fecha|actualización|página|copyright/i.test(organicDescription) && // No metadata
            !/TEMA \d+/i.test(organicDescription) && // No "TEMA 11"
            !/incluye \d+ (quiz|flashcard|tema)/i.test(organicDescription) && // No config mentions
            /[a-záéíóúñA-ZÁÉÍÓÚÑ]{4,}/.test(organicDescription) && // Has real words
            /[.!?]\s*$/.test(organicDescription); // Ends with punctuation
          
          if (isValidDescription) {
            console.log(`[${planId}] ✓ Generated description: ${words.length} words, ${organicDescription.length} chars`);
          } else {
            console.warn(`[${planId}] ✗ Generated description rejected as invalid`);
            organicDescription = ''; // Force fallback
          }
        }
      } catch (err) {
        console.error(`[${planId}] Description generation failed:`, err.message);
        organicDescription = ''; // Force fallback
      }
    }
    
    // Simple fallback if generation failed
    if (!organicDescription || organicDescription.length < 50) {
      organicDescription = `Material de estudio sobre ${organicTitle}. Contenido para nivel ${difficulty || 'general'}.`;
      console.log(`[${planId}] Using fallback description`);
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
      generateSingleBatch(batch, extractedText, openaiKey, language, quizTypology, priorityPoints, difficulty, planId, membership)
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

// Helper function to build prompts for different item types
function buildPromptForType(type, batch, extractedText, language, quizTypology, priorityPoints, difficulty, planId, isRetry) {
  const { batchIndex } = batch; // Extract batchIndex from batch object
  const retryEmphasis = isRetry ? '\n\n**THIS IS A RETRY - CRITICAL:** Generate EXACTLY the requested number of items. Previous attempt failed to meet quota.' : '';
  
  let prompt = `You are an expert educational content generator. Create high-quality study materials in ${language}.${retryEmphasis}

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
    
    // For "Combinado" typology, generate instructions for mixed types
    const isCombinado = quizTypology === 'Combinado';
    
    let typologyInstructions = '';
    let optionsCount = 4; // Default
    let optionsExample = '["Opción A","Opción B","Opción C","Opción D"]';
    
    if (isCombinado) {
      typologyInstructions = `**COMBINADO MODE - CRITICAL:**
- Generate quizzes with MIXED typologies (random distribution)
- Each quiz must have ONE of these typologies: "A, B, C, D", "A, B, C", "True/False", "Palabras hueccas"
- Distribute roughly equally among the ${quizLimit} quizzes
- EACH quiz must specify its typology in the "typology" field

**TYPOLOGY FORMATS:**
1. "A, B, C, D": Standard 4-option multiple choice
2. "A, B, C": 3-option multiple choice
3. "True/False": 2 options ["Verdadero","Falso"]
4. "Palabras hueccas": Question is a sentence with a blank (use ____), 4 word options to complete it
   Example: "La capital de España es ____." Options: ["Madrid","Barcelona","Valencia","Sevilla"]`;
      
    } else if (quizTypology === 'True/False' || quizTypology === 'Verdadero/Falso' || quizTypology === 'V/F' || quizTypology === 'Verdadero o Falso') {
      optionsCount = 2;
      optionsExample = '["Verdadero","Falso"]';
      
    } else if (quizTypology === 'A, B, C') {
      optionsCount = 3;
      optionsExample = '["Opción A","Opción B","Opción C"]';
      
    } else if (quizTypology === 'A, B, C, D') {
      optionsCount = 4;
      optionsExample = '["Opción A","Opción B","Opción C","Opción D"]';
      
    } else if (quizTypology === 'Palabras hueccas') {
      optionsCount = 4;
      optionsExample = '["Madrid","Barcelona","Valencia","Sevilla"]';
      typologyInstructions = `**PALABRAS HUECCAS FORMAT - CRITICAL:**
- EACH QUIZ MUST CONTAIN EXACTLY ${questionsLimit} QUESTIONS (NOT just 1 question!)
- EVERY question MUST be a sentence with a BLANK represented by "____"
- The blank replaces ONE missing word in the sentence
- Example questions:
  Q1: "La capital de España es ____."
  Q2: "El ____ es el órgano que bombea sangre."
  Q3: "La fotosíntesis ocurre en las ____."
  [...continue until you have ${questionsLimit} questions total]
- Each question has 4 word options that could fill the blank
- Only ONE option correctly completes each sentence
- Options are single words or short phrases (max 3 words)

**CRITICAL REMINDER:** Generate ${questionsLimit} questions per quiz, NOT 1 question per quiz!`;
      
    } else {
      // Default to 4 options for unknown typologies
      optionsCount = 4;
      optionsExample = '["Opción A","Opción B","Opción C","Opción D"]';
    }
    
    prompt += `Generate EXACTLY ${quizLimit} quizzes, each with EXACTLY ${questionsLimit} questions.

**CRITICAL:** You MUST generate exactly ${quizLimit} quizzes. Do not generate fewer.

${typologyInstructions || `**CRITICAL TYPOLOGY RULE**: 
- ALL quizzes in this batch MUST use typology="${quizTypology}" (exact string)
- This is MANDATORY - any quiz with different typology will be rejected
- EVERY SINGLE quiz must have "${quizTypology}" as its typology value

**CRITICAL OPTIONS RULE**:
- True/False: EXACTLY 2 options ["Verdadero","Falso"]
- A, B, C: EXACTLY 3 options
- A, B, C, D: EXACTLY 4 options
- Palabras hueccas: Question with ____ blank, EXACTLY 4 word options`}

JSON FORMAT:
[{
  "id":"quiz_batch${batchIndex}_1",
  "titulo":"Descriptive title from PDF content (NOT generic like 'Quiz 1')",
  "typology":"${quizTypology}",
  "progreso":{"preguntasRespondidas":0},
  "questions":[
    {
      "question":"Specific, clear question (max 220 chars)",
      "options":${optionsExample},
      "answer":"Answer",
      "difficulty":"${difficulty}",
      "inferred":false,
      "evidence":"PDF snippet"
    }
    // ... repeat ${questionsLimit - 1} more question objects (total: ${questionsLimit} questions)
  ]
}]

RULES:
- Each quiz MUST have an array of EXACTLY ${questionsLimit} question objects
- Questions: Direct, specific (e.g., "¿Qué función cumple X en Y?")
- Options: EXACTLY ${optionsCount} options (${quizTypology}), max 140 chars each
- Answer MUST be one of the options
- Typology: MANDATORY "${quizTypology}" for ALL quizzes - NO EXCEPTIONS, NO VARIATIONS
- NO generic titles like "Quiz generado", "Test 1" - derive from PDF content
- Distractors must be plausible but incorrect`;

  } else if (type === 'flashcards') {
    const { flashcardLimit } = batch;
    prompt += `Generate EXACTLY ${flashcardLimit} flashcards for active recall.

**CRITICAL:** You MUST generate exactly ${flashcardLimit} flashcards. Do not generate fewer.

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
    prompt += `Generate EXACTLY ${hotTopicsLimit} hot topics - key concepts from the PDF.

**CRITICAL:** You MUST generate exactly ${hotTopicsLimit} topics. Do not generate fewer.

JSON FORMAT:
[{
  "title":"Concept name (max 100 chars)",
  "description":"Why it matters, what it covers (minimum 50 chars, ideally 60-75)",
  "inferred":false,
  "evidence":"PDF snippet"
}]

RULES:
- Title: Clear, specific (NOT "Tema importante")
- Description: Explain what, why important, how relates to others
- Focus on: ${priorityPoints || 'main themes'}`;
  }

  prompt += `\n\n**PDF CONTENT:**\n${extractedText}\n\n**OUTPUT JSON ARRAY:**`;
  return prompt;
}

async function generateSingleBatch(batch, extractedText, openaiKey, language, quizTypology, priorityPoints, difficulty, planId, membership) {
  const { type, batchIndex } = batch;
  
  // Enable retry logic for premium users or if explicitly requested
  const enableRetry = membership === 'premium' || membership === 'pro' || true; // true = enable for all users
  const maxRetries = enableRetry ? 2 : 0; // Max 2 retry attempts
  
  // Build prompt using ORIGINAL quality rules
  const prompt = buildPromptForType(type, batch, extractedText, language, quizTypology, priorityPoints, difficulty, planId, false);

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
    let validatedItems = validateBatchItems(items, type, batch, quizTypology, planId, batchIndex);
    
    if (validatedItems.length === 0) {
      console.error(`[${planId}] ❌ ALL ITEMS REJECTED: All ${items.length} items in batch ${batchIndex} failed validation`);
      throw new Error(`All ${items.length} items failed validation - batch rejected`);
    }
    
    // Check if we generated the expected number of items
    const expectedCount = type === 'quizzes' ? batch.quizLimit : 
                         type === 'flashcards' ? batch.flashcardLimit : 
                         type === 'hotTopics' ? batch.hotTopicsLimit : 0;
    
    // RETRY LOGIC: If we're missing items and retries are enabled, try to generate the missing ones
    if (validatedItems.length < expectedCount && enableRetry) {
      const missingCount = expectedCount - validatedItems.length;
      console.warn(`[${planId}] ⚠️ INSUFFICIENT ITEMS: Expected ${expectedCount} but got ${validatedItems.length}. Will retry to generate ${missingCount} missing items...`);
      
      for (let retryAttempt = 1; retryAttempt <= maxRetries && validatedItems.length < expectedCount; retryAttempt++) {
        const stillMissing = expectedCount - validatedItems.length;
        console.log(`[${planId}] 🔄 Retry attempt ${retryAttempt}/${maxRetries}: Generating ${stillMissing} missing ${type}...`);
        
        try {
          // Create a mini-batch for just the missing items
          const retryBatch = { ...batch, batchIndex: `${batchIndex}_retry${retryAttempt}` };
          if (type === 'quizzes') retryBatch.quizLimit = stillMissing;
          else if (type === 'flashcards') retryBatch.flashcardLimit = stillMissing;
          else if (type === 'hotTopics') retryBatch.hotTopicsLimit = stillMissing;
          
          // Build retry prompt with emphasis on generating EXACTLY the missing count
          const retryPrompt = buildPromptForType(type, retryBatch, extractedText, language, quizTypology, priorityPoints, difficulty, planId, true);
          
          const retryResponse = await axios.post(
            'https://api.openai.com/v1/chat/completions',
            {
              model: 'gpt-4o-mini',
              messages: [
                { role: 'system', content: 'You are an expert educational content creator. Output ONLY valid JSON arrays. Generate EXACTLY the requested number of items.' },
                { role: 'user', content: retryPrompt }
              ],
              temperature: 0.7,
              max_tokens: 6000
            },
            {
              headers: { 'Authorization': `Bearer ${openaiKey}`, 'Content-Type': 'application/json' },
              timeout: 120000
            }
          );
          
          let retryContent = retryResponse.data.choices[0].message.content.trim();
          retryContent = retryContent.replace(/^```json\n?/i, '').replace(/\n?```$/, '').trim();
          const retryItems = JSON.parse(retryContent);
          
          if (Array.isArray(retryItems) && retryItems.length > 0) {
            const retryValidated = validateBatchItems(retryItems, type, retryBatch, quizTypology, planId, `${batchIndex}_retry${retryAttempt}`);
            const addedCount = Math.min(retryValidated.length, stillMissing);
            validatedItems.push(...retryValidated.slice(0, addedCount));
            console.log(`[${planId}] ✅ Retry ${retryAttempt}: Added ${addedCount} more items. Total now: ${validatedItems.length}/${expectedCount}`);
          }
        } catch (retryErr) {
          console.error(`[${planId}] ❌ Retry ${retryAttempt} failed:`, retryErr.message);
        }
      }
      
      if (validatedItems.length < expectedCount) {
        console.warn(`[${planId}] ⚠️ After ${maxRetries} retries, still missing ${expectedCount - validatedItems.length} items. Final count: ${validatedItems.length}/${expectedCount}`);
      } else {
        console.log(`[${planId}] ✅ Successfully completed all ${expectedCount} items after retries!`);
      }
    } else if (validatedItems.length < expectedCount) {
      console.warn(`[${planId}] ⚠️ INSUFFICIENT ITEMS: Expected ${expectedCount} but got ${validatedItems.length} valid items (retries disabled)`);
    }
    
    if (validatedItems.length < items.length) {
      console.error(`[${planId}] ⚠️ PARTIAL REJECTION: Batch ${batchIndex} (${type}): ${items.length - validatedItems.length}/${items.length} items rejected by validation`);
    }

    console.log(`[${planId}] ✅ Batch ${batchIndex} (${type}) generated ${validatedItems.length}/${expectedCount} valid items`);
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
    for (const quiz of items) {
      if (!quiz || typeof quiz !== 'object') continue;
      
      // Validate quiz has questions array
      if (!Array.isArray(quiz.questions) || quiz.questions.length === 0) {
        console.warn(`[${planId}] Quiz "${quiz.titulo || quiz.id}" has no questions - REJECTED`);
        continue;
      }
      
      // Get the quiz's actual typology (what was generated)
      const quizActualTypology = quiz.typology || quizTypology;
      
      // Determine expected options count based on the quiz's actual typology
      let expectedOptionsCount;
      if (quizActualTypology === 'True/False' || 
          quizActualTypology === 'Verdadero/Falso' || 
          quizActualTypology === 'V/F' ||
          quizActualTypology === 'Verdadero o Falso') {
        expectedOptionsCount = 2;
      } else if (quizActualTypology === 'A, B, C') {
        expectedOptionsCount = 3;
      } else if (quizActualTypology === 'A, B, C, D') {
        expectedOptionsCount = 4;
      } else if (quizActualTypology === 'Palabras hueccas') {
        expectedOptionsCount = 4;
      } else if (quizActualTypology === 'Combinado') {
        // For Combinado, each quiz should have its own typology set
        // We'll validate based on the actual typology of each quiz, not "Combinado"
        expectedOptionsCount = null; // Will be determined per question
      } else {
        // Default to 4 options for unknown typologies
        expectedOptionsCount = 4;
      }
      
      // Special validation for "Palabras hueccas": check for blank in question
      if (quizActualTypology === 'Palabras hueccas') {
        const hasBlank = quiz.questions.some(q => 
          q.question && (q.question.includes('____') || q.question.includes('___') || q.question.includes('__'))
        );
        if (!hasBlank) {
          console.warn(`[${planId}] Quiz "${quiz.titulo}" marked as "Palabras hueccas" but questions don't contain blanks (____) - keeping anyway`);
        }
      }
      
      // Validate each question's options count
      const validQuestions = quiz.questions.filter(q => {
        if (!q || !Array.isArray(q.options)) return false;
        
        // For Combinado, determine expected count from the quiz's specific typology
        let questionExpectedCount = expectedOptionsCount;
        if (quizActualTypology === 'Combinado') {
          // The quiz should have been assigned a specific typology by the AI
          if (q.options.length === 2) questionExpectedCount = 2;
          else if (q.options.length === 3) questionExpectedCount = 3;
          else if (q.options.length === 4) questionExpectedCount = 4;
          else questionExpectedCount = 4; // default
        }
        
        // CRITICAL: Check options count matches the quiz's typology
        if (questionExpectedCount !== null && q.options.length !== questionExpectedCount) {
          console.error(`[${planId}] ❌ VALIDATION FAILED: Question "${q.question}" has ${q.options.length} options, expected ${questionExpectedCount} for quiz typology "${quizActualTypology}" - REJECTED`);
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
      
      // Update quiz with only valid questions and ensure typology is set
      quiz.questions = validQuestions;
      quiz.typology = quizActualTypology; // Preserve the actual typology
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
      
      // CRITICAL: Validate description minimum length (50 chars)
      if (topic.description.length < 50) {
        console.warn(`[${planId}] HotTopic "${topic.title}" description too short (${topic.description.length} < 50 chars) - REJECTED`);
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
