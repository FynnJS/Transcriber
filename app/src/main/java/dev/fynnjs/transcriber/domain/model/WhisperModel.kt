package dev.fynnjs.transcriber.domain.model

/**
 * The quantized ggml models shipped by whisper.cpp. URLs, sizes and SHA-256 checksums are
 * pinned so a download can be verified before it is used.
 */
enum class WhisperModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val approxBytes: Long,
    val sha256: String,
    val downloadUrl: String,
) {
    SMALL(
        id = "small",
        displayName = "Small",
        fileName = "ggml-small-q5_1.bin",
        approxBytes = 190_085_487L,
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
    ),
    BASE(
        id = "base",
        displayName = "Base",
        fileName = "ggml-base-q5_1.bin",
        approxBytes = 59_707_625L,
        sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
    ),

    /**
     * Multilingual large-v3 with the distilled 4-layer decoder: much better German accuracy
     * than small at roughly three times the file size and noticeably more compute.
     */
    LARGE_V3_TURBO(
        id = "large-v3-turbo",
        displayName = "Large v3 Turbo",
        fileName = "ggml-large-v3-turbo-q5_0.bin",
        approxBytes = 574_041_195L,
        sha256 = "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
    ),
    ;

    companion object {
        val DEFAULT = SMALL

        fun fromId(id: String?): WhisperModel =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
