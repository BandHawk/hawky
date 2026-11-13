package com.example.data.guide

data class LiteRtModelSpec(
    val id: String,
    val name: String,
    val fileName: String,
    val downloadUrl: String,
    val approxSizeBytes: Long,
    val sizeLabel: String,
    val minRamGb: Int,
    val quantization: String,
    val contextWindow: String,
    val summary: String,
    val strengths: List<String>,
)

data class OfflineModelRecommendation(
    val name: String,
    val family: String,
    val parameters: String,
    val quantizedFootprint: String,
    val minDeviceRam: String,
    val runtimeOptions: String,
    val privacyLicense: String,
    val bestFor: List<String>,
    val tradeoffs: String,
)

data class OfflineFeatureGuide(
    val title: String,
    val category: String,
    val worksWithoutModelDownload: Boolean,
    val offlineImplementation: String,
    val limitationsVsCloud: String,
)

object OfflineAiCatalog {

    val downloadableLiteRtModels = listOf(
        LiteRtModelSpec(
            id = "qwen3_0_6b",
            name = "Qwen3 0.6B (LiteRT-LM)",
            fileName = "Qwen3-0.6B.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            approxSizeBytes = 600_000_000L,
            sizeLabel = "~600 MB",
            minRamGb = 4,
            quantization = "INT4 / Dynamic",
            contextWindow = "4K tokens",
            summary = "Ultra-lightweight open-weight model ideal for fast offline chat, concise summarization, and basic Q&A on mid-range Android phones.",
            strengths = listOf("Fast CPU & GPU speed", "Low RAM footprint", "Great for short Q&A & drafts"),
        ),
        LiteRtModelSpec(
            id = "gemma_4_e2b",
            name = "Gemma 4 E2B Instruct",
            fileName = "gemma-4-E2B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            approxSizeBytes = 2_600_000_000L,
            sizeLabel = "~2.6 GB",
            minRamGb = 8,
            quantization = "INT4",
            contextWindow = "8K+ tokens",
            summary = "Higher-reasoning Google open model packaged for LiteRT-LM CPU/GPU execution with strong instruction following and summarization quality.",
            strengths = listOf("Stronger reasoning", "Clean formatting", "Longer context summaries"),
        ),
    )

    val recommendedModels = listOf(
        OfflineModelRecommendation(
            name = "Gemini Nano (via AICore)",
            family = "Google DeepMind",
            parameters = "~1.8B – 3.25B (device-tier)",
            quantizedFootprint = "OS-managed (0 MB in APK)",
            minDeviceRam = "8–12 GB (supported Android devices)",
            runtimeOptions = "ML Kit GenAI Prompt / Summarization / Proofread / Rewrite APIs",
            privacyLicense = "Private Compute Core (isolated, no app data retained)",
            bestFor = listOf("Summarization", "Proofreading", "Rewriting", "Short Q&A"),
            tradeoffs = "Zero APK bloat and NPU-accelerated, but only supported on AICore-enabled Android hardware with a 4,000-token prompt limit.",
        ),
        OfflineModelRecommendation(
            name = "Gemma 4 E2B / Gemma 3 1B",
            family = "Google Gemma Open Weights",
            parameters = "1B – 2B effective",
            quantizedFootprint = "~650 MB (1B) to ~2.6 GB (E2B)",
            minDeviceRam = "4–8 GB RAM",
            runtimeOptions = "LiteRT-LM (.litertlm) / MediaPipe LLM Inference",
            privacyLicense = "Gemma Terms (100% local weights, zero telemetry)",
            bestFor = listOf("Text Generation", "Summarization", "Instruction Q&A"),
            tradeoffs = "Excellent reasoning per megabyte and clean instruction tuning; 2B+ variants require modern flagship RAM to avoid OS memory kills.",
        ),
        OfflineModelRecommendation(
            name = "Qwen3 0.6B & Qwen2.5 1.5B",
            family = "Alibaba Qwen (Apache 2.0)",
            parameters = "0.6B / 1.5B",
            quantizedFootprint = "~600 MB (0.6B) / ~1.0 GB (1.5B)",
            minDeviceRam = "4–6 GB RAM",
            runtimeOptions = "LiteRT-LM / llama.cpp (GGUF Q4_K_M)",
            privacyLicense = "Apache 2.0 (permissive open source)",
            bestFor = listOf("Fast Q&A", "Code Snippets", "Structured Extraction"),
            tradeoffs = "0.6B runs smoothly even on CPU-only devices; smaller parameter count has shallower world trivia than 3B+ models.",
        ),
        OfflineModelRecommendation(
            name = "Llama 3.2 1B / 3B Instruct",
            family = "Meta Llama",
            parameters = "1B / 3B",
            quantizedFootprint = "~750 MB (1B) / ~1.9 GB (3B)",
            minDeviceRam = "4–8 GB RAM",
            runtimeOptions = "ExecuTorch / llama.cpp / MLC LLM",
            privacyLicense = "Llama 3.2 Community License",
            bestFor = listOf("Conversational Chat", "Rewriting", "Local RAG Synthesis"),
            tradeoffs = "Strong multilingual and chat fluency; 3B variant gives solid summaries but warms the battery under long CPU sessions.",
        ),
        OfflineModelRecommendation(
            name = "Phi-3.5 Mini / Phi-4 Mini",
            family = "Microsoft Phi",
            parameters = "3.8B",
            quantizedFootprint = "~2.2 GB (INT4)",
            minDeviceRam = "8+ GB RAM",
            runtimeOptions = "ONNX Runtime GenAI / llama.cpp",
            privacyLicense = "MIT License (permissive)",
            bestFor = listOf("Logical Reasoning", "Math & Coding Q&A", "Dense Summarization"),
            tradeoffs = "Punches far above its weight in logic and synthesis, at the cost of higher memory pressure and slower token/s on mid-range SoCs.",
        ),
        OfflineModelRecommendation(
            name = "SmolLM2 360M / 1.7B",
            family = "Hugging Face SmolLM",
            parameters = "360M / 1.7B",
            quantizedFootprint = "~250 MB (360M) / ~1.0 GB (1.7B)",
            minDeviceRam = "3–6 GB RAM",
            runtimeOptions = "llama.cpp / LiteRT / MLC",
            privacyLicense = "Apache 2.0",
            bestFor = listOf("Low-end Devices", "Quick Text Completion", "Note Tagging"),
            tradeoffs = "360M fits almost any budget phone with instant load times, though complex multi-step reasoning should be avoided.",
        ),
    )

    val essentialOfflineFeatures = listOf(
        OfflineFeatureGuide(
            title = "Conversational Text Generation & Drafting",
            category = "Core Generation",
            worksWithoutModelDownload = false,
            offlineImplementation = "Run a 4-bit quantized SLM (0.6B–3B) via LiteRT-LM or AICore (Gemini Nano) on a dedicated single-thread background executor with streaming token output.",
            limitationsVsCloud = "Smaller context window (2K–8K tokens vs 1M+ in cloud), slower throughput (8–35 tok/s depending on SoC), and shallower obscure factual recall.",
        ),
        OfflineFeatureGuide(
            title = "Article & Meeting Summarization",
            category = "Comprehension",
            worksWithoutModelDownload = true,
            offlineImplementation = "Two-tier approach: use Gemini Nano / LiteRT-LM for abstractive bullet summaries, or deterministic TextRank + TF-IDF sentence scoring for instant zero-model extractive summaries.",
            limitationsVsCloud = "Long documents must be chunked (map-reduce summarization) to fit 4K token limits; extractive NLP preserves original phrasing rather than paraphrasing.",
        ),
        OfflineFeatureGuide(
            title = "Local Knowledge Vault (Offline RAG Q&A)",
            category = "Personal Knowledge",
            worksWithoutModelDownload = true,
            offlineImplementation = "Store private notes/docs in Room SQLite, retrieve top passages locally via BM25/TF-IDF or on-device embeddings (e.g., Gecko/MiniLM in LiteRT), and optionally pass top chunks to the local LLM.",
            limitationsVsCloud = "No live web search or post-cutoff news; retrieval quality depends on clean note chunking and device storage for vector indexes.",
        ),
        OfflineFeatureGuide(
            title = "Proofreading, Rewriting & Tone Control",
            category = "Writing Aid",
            worksWithoutModelDownload = false,
            offlineImplementation = "Use specialized LoRA adapters (ML Kit GenAI Proofread/Rewrite APIs) or concise system prompts with low temperature (0.2–0.4) and top-K sampling.",
            limitationsVsCloud = "Best on paragraphs under 500–1,000 words; less nuanced style mimicry than frontier 100B+ cloud models.",
        ),
        OfflineFeatureGuide(
            title = "Zero-Network Privacy & Local History",
            category = "Privacy Architecture",
            worksWithoutModelDownload = true,
            offlineImplementation = "Persist conversations in encrypted/sandboxed Room SQLite, enforce a strict offline-mode network gate, and load external .litertlm weights via Storage Access Framework without broad storage permissions.",
            limitationsVsCloud = "No automatic multi-device cloud sync unless the user exports/imports local backups manually.",
        ),
        OfflineFeatureGuide(
            title = "Model Lifecycle, Quantization & Thermal Control",
            category = "Engine Engineering",
            worksWithoutModelDownload = true,
            offlineImplementation = "Never bundle GB-scale weights inside the APK. Download/import INT4 quantized models into app filesDir, cache compiled GPU kernels in cacheDir, and fall back from GPU (OpenCL) to CPU automatically.",
            limitationsVsCloud = "Requires 600 MB–3 GB of free user storage, 5–15 seconds cold-load warmup, and sustained inference heats the device and drains battery faster than cloud API calls.",
        ),
        OfflineFeatureGuide(
            title = "Offline Voice Input & Read-Aloud (STT / TTS)",
            category = "Multimodal I/O",
            worksWithoutModelDownload = true,
            offlineImplementation = "Pair Android on-device SpeechRecognizer / Whisper-tiny (LiteRT) for offline transcription with Android's built-in TextToSpeech engine.",
            limitationsVsCloud = "Offline voice packs vary by device language setup and produce more robotic prosody than neural cloud voices.",
        ),
    )
}
