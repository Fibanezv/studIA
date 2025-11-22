package com.app.estudiaapp.utils;

public class OpenAiPromptBuilder {

    // System prompt for summarizing a document chunk
    public static String buildSummarySystemPrompt() {
        return "Eres un asistente que resume con precisión fragmentos de texto técnico o educativo. " +
                "Devuelve SOLO un resumen conciso en texto plano (no JSON), con frases claras y evitando explicaciones sobre el proceso.\n" +
                "Objetivo: extraer los puntos clave, temas, términos relevantes y posibles preguntas de estudio.";
    }

    // User prompt for a specific chunk
    public static String buildSummaryUserPrompt(int chunkIndex, int totalChunks, String chunkText) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fragmento ").append(chunkIndex).append(" de ").append(totalChunks).append(".\n");
        sb.append("Resume los puntos clave, temas y preguntas relevantes de este fragmento. Mantén el resumen en texto plano y en 5-10 líneas máximo.\n\n");
        sb.append("Texto del fragmento:\n");
        sb.append(chunkText);
        sb.append("\n\nDevuelve SOLO el resumen.");
        return sb.toString();
    }

    // System prompt for the final plan generation (concise and strict)
    public static String buildPlanSystemPrompt() {
        return "Eres un asistente que convierte información (resúmenes y metadatos) en un único objeto JSON que represente un plan de estudio. " +
            "DEVUELVE SOLO EL JSON. NO añadas texto, explicaciones, ni comentarios adicionales. El JSON debe cumplir EXACTAMENTE la estructura, tipos y requisitos indicados a continuación. " +
            "Si un campo no aplica, usa null o un array vacío según corresponda. Asegúrate de que los contadores (número de quizzes, preguntas por quiz, flashcards, hotTopics) respeten las restricciones proporcionadas en 'constraints'.";
    }

    // User prompt for final plan generation: receives constraints JSON and combined summaries
    public static String buildPlanUserPrompt(String title, String priorities, String constraintsJson, String combinedSummaries) {
        StringBuilder up = new StringBuilder();
        up.append("INSTRUCCIONES:\n");
        up.append("1) Genera UN SOLO OBJETO JSON válido (sin comentarios ni texto extra).\n");
        up.append("2) El JSON debe respetar la estructura exacta y tipos mostrados en 'ESQUEMA_EJEMPLO' más abajo.\n");
        up.append("3) Ajusta el número de quizzes, preguntas por quiz, flashcards y hotTopics para coincidir con las restricciones en 'constraints'. Si no se pueden cumplir exactamente, indica el máximo posible pero cumple siempre la validación mínima pedida.\n");
        up.append("4) Usa la información del título, prioridades y el resumen combinado para rellenar los contenidos.\n\n");

        up.append("METADATA:\n");
        up.append("titulo: ").append(title != null ? title : "").append("\n");
        up.append("prioridades: ").append(priorities != null ? priorities : "").append("\n");
        up.append("constraints: ").append(constraintsJson).append("\n\n");

        up.append("RESUMEN_CONSOLIDADO_DEL_DOCUMENTO:\n");
        up.append(combinedSummaries).append("\n\n");

        // Provide a strict JSON template. The model MUST replace the placeholder values
        // with factual content derived from 'RESUMEN_CONSOLIDADO_DEL_DOCUMENTO'.
        // DO NOT invent or fabricate content. If you cannot extract any factual
        // content from the provided summaries, do NOT return a populated plan: instead
        // return exactly the single token (no JSON, no extra text): NO_PLAN_POSSIBLE
        up.append("TEMPLATE_JSON_OBLIGATORIO (REEMPLAZA SOLO LOS VALORES ENTRE <>):\n");
        up.append("{\n");
        up.append("  \"id\": \"<ID_PLAN_O_NULL>\",\n");
        up.append("  \"nombre\": \"<NOMBRE_PLAN_O_NULL>\",\n");
        up.append("  \"idioma\": \"<ISO_IDIOMA_O_NULL>\",\n");
        up.append("  \"nivel\": \"<NIVEL_O_NULL>\",\n");
        up.append("  \"prioridades\": \"<PRIORIDADES_O_NULL>\",\n");
        up.append("  \"progreso\": {\n");
        up.append("    \"difficulty\": \"<difficulty_o_null>\",\n");
        up.append("    \"estimatedStudyTimeMinutes\": <estimatedStudyTimeMinutes_integer_or_0>\n");
        up.append("  },\n");
        up.append("  \"flashcards\": [\n");
        up.append("    /* Reemplaza con objetos: { \"pregunta\": \"...\", \"respuesta\": \"...\" } o deja el array vacío [] si no hay flashcards factuales */\n");
        up.append("  ],\n");
        up.append("  \"quizzes\": [\n");
        up.append("    /* Reemplaza con objetos de quiz o deja el array vacío */\n");
        up.append("  ],\n");
        up.append("  \"hotTopics\": [\n");
        up.append("    /* Reemplaza con objetos: { \"title\": \"...\", \"description\": \"...\" } o deja el array vacío */\n");
        up.append("  ]\n");
        up.append("}\n\n");

        up.append("RESPONDE SOLO CON EL JSON VALIDO QUE SIGUE EL TEMPLATE.\n");
        up.append("- Reemplaza UNICAMENTE las marcas indicadas (<>). No añadas claves ni eliminines las existentes.\n");
        up.append("- No inventes ni rellenes con información no presente en el RESUMEN_CONSOLIDADO_DEL_DOCUMENTO.\n");
        up.append("- Si NO HAY contenido factual para poblar el plan (por ejemplo el documento no contiene conceptos, definiciones, afirmaciones o suficientes datos para generar flashcards/quizzes/hotTopics), responde EXACTAMENTE con este token y NADA MÁS: NO_PLAN_POSSIBLE\n");
        up.append("- Si puedes generar contenido, devuelve únicamente el JSON completo siguiendo el template, con arrays vacíos [] si corresponde.\n");
        up.append("Comienza ahora.");
        up.append("Empieza ahora y devuelve únicamente el JSON final.");
        return up.toString();
    }
}
