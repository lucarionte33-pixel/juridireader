package com.juridireader.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class LawSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("LawSyncWorker", "Iniciando verificação periódica semanal de leis do Planalto...")
            // Simulação de verificação e atualização periódica em background
            // Em caso de nova versão do Planalto, sincroniza os metadados
            Result.success()
        } catch (e: Exception) {
            Log.e("LawSyncWorker", "Erro na sincronização de leis", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "weekly_laws_sync_work"

        fun scheduleWeeklySync(context: Context) {
            val syncRequest = PeriodicWorkRequestBuilder<LawSyncWorker>(
                7, TimeUnit.DAYS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.d("LawSyncWorker", "Sincronização semanal agendada com sucesso no WorkManager.")
        }
    }
}
