package com.juridireader.app

import android.app.Application
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
        database = AppDatabase.getInstance(this)
        repository = LawRepository(database)

        // Agendar sincronização semanal via WorkManager
        LawSyncWorker.scheduleWeeklySync(this)
    }
}
