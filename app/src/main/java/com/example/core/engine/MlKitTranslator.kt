package com.example.core.engine

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitTranslator : TranslatorEngine {

    companion object {
        private const val TAG = "TolkenTranslator"
    }

    private val _modelState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    override val modelState = _modelState.asStateFlow()

    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.DANISH)
        .setTargetLanguage(TranslateLanguage.RUSSIAN)
        .build()

    private val translator: Translator = Translation.getClient(options)
    private val modelManager = RemoteModelManager.getInstance()
    private val danishModel = TranslateRemoteModel.Builder(TranslateLanguage.DANISH).build()
    private val russianModel = TranslateRemoteModel.Builder(TranslateLanguage.RUSSIAN).build()

    override suspend fun checkOrDownloadModels(): Boolean = withContext(Dispatchers.IO) {
        try {
            _modelState.value = ModelDownloadState.Downloading(10, "Проверка автономных языковых пакетов...")

            val daDownloaded = checkModelDownloaded(danishModel)
            val ruDownloaded = checkModelDownloaded(russianModel)

            if (daDownloaded && ruDownloaded) {
                Log.i(TAG, "Both Danish and Russian models are already downloaded offline.")
                _modelState.value = ModelDownloadState.Ready
                return@withContext true
            }

            _modelState.value = ModelDownloadState.Downloading(30, "Загрузка моделей датского и русского языка (однократно)...")
            val conditions = DownloadConditions.Builder().build()

            awaitTask(translator.downloadModelIfNeeded(conditions))

            _modelState.value = ModelDownloadState.Downloading(90, "Инициализация автономного переводчика...")
            Log.i(TAG, "Models successfully downloaded and verified.")
            _modelState.value = ModelDownloadState.Ready
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading ML Kit models", e)
            _modelState.value = ModelDownloadState.Error(e.localizedMessage ?: "Не удалось загрузить языковые пакеты")
            false
        }
    }

    override suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""
        try {
            awaitTask(translator.translate(text))
        } catch (e: Exception) {
            Log.e(TAG, "Translation error for text: '$text'", e)
            // If offline model hasn't finished or transient failure, return fallback or throw
            throw e
        }
    }

    override fun close() {
        try {
            translator.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing translator", e)
        }
    }

    private suspend fun checkModelDownloaded(model: TranslateRemoteModel): Boolean {
        return try {
            awaitTask(modelManager.isModelDownloaded(model))
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { result ->
            cont.resume(result)
        }.addOnFailureListener { error ->
            cont.resumeWithException(error)
        }
    }
}
