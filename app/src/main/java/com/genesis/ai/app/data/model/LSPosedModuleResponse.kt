package com.genesis.ai.app.data.model

data class LSPosedModuleResponse(
    val status: String, // "success" or "failed"
    val message: String? = null,
    val packageName: String,
    val enabled: Boolean
)
