package com.example

import android.content.Context
import com.example.core.engine.PipelineCoordinator

object TolkenManager {
    @Volatile
    private var instance: PipelineCoordinator? = null

    fun getCoordinator(context: Context): PipelineCoordinator {
        return instance ?: synchronized(this) {
            instance ?: PipelineCoordinator(context.applicationContext).also {
                instance = it
            }
        }
    }
}
