package com.example.ai

import com.example.data.local.KnowledgeNoteEntity
import kotlin.math.ln
import kotlin.math.sqrt

data class ExtractiveSummaryResult(
    val bullets: List<String>,
    val keyTerms: List<String>,
    val originalWordCount: Int,
    val summaryWordCount: Int,
    val compressionPercent: Int,
)

data class NoteRetrievalMatch(
    val noteTitle: String,
    val excerpt: String,
    val score: Double,
    val matchedTerms: List<String>,
)

/**
 * Pure on-device deterministic NLP engine (TextRank + TF-IDF + BM25 passage retrieval).
 * Runs 100% offline with zero external model weights or network calls.
 */
class ExtractiveNlpEngine {

    private val stopWords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "been", "being", "but", "by",
        "can", "could", "did", "do", "does", "doing", "done", "down", "during",
        "each", "few", "for", "from", "further", "had", "has", "have", "having",
        "he", "her", "here", "hers", "herself", "him", "himself", "his", "how",
        "i", "if", "in", "into", "is", "it", "its", "itself", "just", "me",
        "more", "most", "my", "myself", "no", "nor", "not", "now", "of", "off",
        "on", "once", "only", "or", "other", "our", "ours", "ourselves", "out",
        "over", "own", "same", "she", "should", "so", "some", "such", "than",
        "that", "the", "their", "theirs", "them", "themselves", "then", "there",
        "these", "they", "this", "those", "through", "to", "too", "under",
        "until", "up", "very", "was", "we", "were", "what", "when", "where",
        "which", "while", "who", "whom", "why", "will", "with", "would", "you",
        "your", "yours", "yourself", "yourselves", "also", "about", "after",
        "before", "between", "both", "however", "like", "make", "many", "much",
        "need", "one", "two", "use", "using", "used", "way", "well",
    )

    fun summarize(rawText: String, bulletCount: Int = 4): ExtractiveSummaryResult {
        val cleaned = rawText.trim()
        if (cleaned.isEmpty()) {
            return ExtractiveSummaryResult(emptyList(), emptyList(), 0, 0, 0)
        }

        val sentences = splitSentences(cleaned)
        val allTokens = tokenize(cleaned)
        val originalWords = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }.size

        if (sentences.size <= 1) {
            val terms = extractTopKeywords(allTokens, limit = 5)
            return ExtractiveSummaryResult(
                bullets = sentences,
                keyTerms = terms,
                originalWordCount = originalWords,
                summaryWordCount = originalWords,
                compressionPercent = 0,
            )
        }

        val sentenceTokens = sentences.map { tokenize(it).toSet() }
        val termFreq = mutableMapOf<String, Int>()
        for (token in allTokens) {
            termFreq[token] = (termFreq[token] ?: 0) + 1
        }
        val maxFreq = termFreq.values.maxOrNull()?.coerceAtLeast(1) ?: 1

        // Build TextRank similarity graph + TF + position bias
        val n = sentences.size
        val similarity = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val sim = cosineJaccardOverlap(sentenceTokens[i], sentenceTokens[j])
                similarity[i][j] = sim
                similarity[j][i] = sim
            }
        }

        var scores = DoubleArray(n) { 1.0 / n }
        val damping = 0.85
        repeat(12) {
            val next = DoubleArray(n)
            for (i in 0 until n) {
                var sum = 0.0
                for (j in 0 until n) {
                    if (i != j) {
                        val rowSum = similarity[j].sum()
                        if (rowSum > 0.0) {
                            sum += (similarity[j][i] / rowSum) * scores[j]
                        }
                    }
                }
                val tfBoost = sentenceTokens[i].sumOf { (termFreq[it] ?: 0).toDouble() / maxFreq } /
                    sentenceTokens[i].size.coerceAtLeast(1)
                val positionBoost = if (i == 0) 0.15 else if (i == n - 1) 0.08 else 0.0
                next[i] = (1.0 - damping) / n + damping * sum + (tfBoost * 0.25) + positionBoost
            }
            scores = next
        }

        val targetCount = bulletCount.coerceIn(1, sentences.size)
        val topIndices = scores.indices
            .sortedByDescending { scores[it] }
            .take(targetCount)
            .sorted()

        val selectedBullets = topIndices.map { sentences[it].trim() }
        val summaryWords = selectedBullets.joinToString(" ").split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val compression = if (originalWords > 0) {
            (((originalWords - summaryWords).toDouble() / originalWords) * 100).toInt().coerceIn(0, 99)
        } else 0

        return ExtractiveSummaryResult(
            bullets = selectedBullets,
            keyTerms = extractTopKeywords(allTokens, limit = 6),
            originalWordCount = originalWords,
            summaryWordCount = summaryWords,
            compressionPercent = compression,
        )
    }

    fun answerFromNotes(
        query: String,
        notes: List<KnowledgeNoteEntity>,
        maxMatches: Int = 3,
    ): List<NoteRetrievalMatch> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty() || notes.isEmpty()) return emptyList()

        val passages = mutableListOf<Triple<String, String, List<String>>>()
        for (note in notes) {
            val noteSentences = splitSentences("${note.title}. ${note.content}")
            // Group into 2-sentence sliding windows for richer passage context
            if (noteSentences.size <= 2) {
                val combined = noteSentences.joinToString(" ")
                passages.add(Triple(note.title, combined, tokenize(combined)))
            } else {
                for (i in 0 until noteSentences.size - 1) {
                    val window = "${noteSentences[i]} ${noteSentences[i + 1]}"
                    passages.add(Triple(note.title, window, tokenize("${note.title} ${note.tags} $window")))
                }
            }
        }

        if (passages.isEmpty()) return emptyList()

        val avgLen = passages.map { it.third.size }.average().coerceAtLeast(1.0)
        val docFreq = mutableMapOf<String, Int>()
        for (term in queryTokens.distinct()) {
            docFreq[term] = passages.count { (_, _, tokens) -> term in tokens }
        }

        val k1 = 1.5
        val b = 0.75
        val totalDocs = passages.size.toDouble()

        return passages.mapNotNull { (title, excerpt, docTokens) ->
            if (docTokens.isEmpty()) return@mapNotNull null
            val tfMap = docTokens.groupingBy { it }.eachCount()
            val matched = mutableListOf<String>()
            var bm25 = 0.0

            for (term in queryTokens.distinct()) {
                val tf = tfMap[term] ?: 0
                if (tf > 0) {
                    matched.add(term)
                    val df = docFreq[term] ?: 0
                    val idf = ln((totalDocs - df + 0.5) / (df + 0.5) + 1.0).coerceAtLeast(0.1)
                    val numerator = tf * (k1 + 1.0)
                    val denominator = tf + k1 * (1.0 - b + b * (docTokens.size / avgLen))
                    bm25 += idf * (numerator / denominator)
                }
            }

            if (matched.isEmpty()) null
            else NoteRetrievalMatch(
                noteTitle = title,
                excerpt = excerpt.trim(),
                score = bm25,
                matchedTerms = matched,
            )
        }
            .sortedByDescending { it.score }
            .distinctBy { it.excerpt }
            .take(maxMatches)
    }

    fun formatOfflineAnswer(query: String, notes: List<KnowledgeNoteEntity>): String {
        if (notes.isEmpty()) {
            return "Your Local Knowledge Vault is empty. Add private notes in the Tools → Notes Vault tab, then ask questions about them 100% offline."
        }
        val matches = answerFromNotes(query, notes, maxMatches = 3)
        if (matches.isEmpty()) {
            return "No matching passages found in your ${notes.size} local note(s) for \"$query\". Try different keywords or load a generative model (Gemini Nano / LiteRT-LM) in the Models tab for open-ended chat."
        }

        return buildString {
            appendLine("Retrieved from your local offline vault (BM25 ranking):")
            appendLine()
            matches.forEachIndexed { index, match ->
                appendLine("${index + 1}. [${match.noteTitle}]")
                appendLine(match.excerpt)
                appendLine("Matched terms: ${match.matchedTerms.joinToString(", ")}")
                if (index < matches.lastIndex) appendLine()
            }
        }.trim()
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length > 8 }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in stopWords }

    private fun extractTopKeywords(tokens: List<String>, limit: Int): List<String> =
        tokens.groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }

    private fun cosineJaccardOverlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val overlap = a.count { it in b }.toDouble()
        return overlap / (sqrt(a.size.toDouble()) * sqrt(b.size.toDouble()))
    }
}
