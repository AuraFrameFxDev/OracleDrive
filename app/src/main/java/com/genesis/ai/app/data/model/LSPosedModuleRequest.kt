package com.genesis.ai.app.data.model

import com.google.gson.annotations.SerializedName

data class LSPosedModuleRequest(
    @SerializedName("package_name") val packageName: String,
    @SerializedName("enable") val enable: Boolean
)
