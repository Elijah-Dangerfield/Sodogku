package com.sodogku.libraries.core

import com.sodogku.buildinfo.SodogkuBuildConfig

object SupabaseInfo {
    val projectId: String
        get() = SodogkuBuildConfig.SUPABASE_PROJECT_ID

    val url: String
        get() = SodogkuBuildConfig.SUPABASE_URL

    val anonKey: String
        get() = SodogkuBuildConfig.SUPABASE_ANON_KEY
}
