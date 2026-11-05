package com.juridireader.app

import android.app.Application
import android.util.Log
import com.juridireader.app.data.local.AppDatabase
import com.juridireader.app.data.repository.LawRepository
import com.juridireader.app.worker.LawSyncWorker

class JuridiReaderApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: LawRepository
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            database = AppDatabase.getInstance(this)
            repository = LawRepository(database)
        } catch (e: Exception) {
            Log.e("JuridiReaderApp", "Erro ao inicializar banco de dados", e)
        }

        try {
            // Agendar sincronização semanal via WorkManager
            LawSyncWorker.scheduleWeeklySync(this)
        } catch (e: Exception) {
            Log.e("JuridiReaderApp", "Erro ao agendar WorkManager", e)
        }
    }
}
