package com.sodogku.libraries.config.impl.data

import com.sodogku.libraries.config.AppConfigMap
import com.sodogku.libraries.core.Catching

interface RemoteConfigDataSource {
    suspend fun getConfig(): Catching<AppConfigMap>
}
