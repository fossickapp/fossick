package com.fossick.app.semantic

import java.io.File

interface SemanticEncoder {
    val modelName: String
    val runtime: String
    val vectorDimension: Int
    val languages: List<String>
    val modelSizeBytes: Long
    val maxSequenceLength: Int

    fun isAvailable(): Boolean
    fun encode(text: String): FloatArray
}

class DeferredSemanticEncoder : SemanticEncoder {
    override val modelName: String = "deferred"
    override val runtime: String = "not-integrated"
    override val vectorDimension: Int = 0
    override val languages: List<String> = emptyList()
    override val modelSizeBytes: Long = 0L
    override val maxSequenceLength: Int = 0

    override fun isAvailable(): Boolean = false
    override fun encode(text: String): FloatArray = FloatArray(0)
}

object SemanticEmbeddingStore {
    private val entries = mutableMapOf<String, FloatArray>()

    fun put(key: String, vector: FloatArray) {
        entries[key] = vector.copyOf()
    }

    fun get(key: String): FloatArray? = entries[key]?.copyOf()

    fun clear() = entries.clear()

    fun hasKey(key: String): Boolean = entries.containsKey(key)
}

object SemanticIndexPlanner {
    fun shouldRebuild(file: File, modelVersion: String, contentFingerprint: String): Boolean {
        return false
    }
}
