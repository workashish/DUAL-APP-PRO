package com.utility.toolbox.di

import android.content.Context
import com.utility.toolbox.data.local.AppDatabase
import com.utility.toolbox.data.local.dao.ClonedAppDao
import com.utility.toolbox.data.local.dao.LogDao
import com.utility.toolbox.service.AntiDetectionManager
import com.utility.toolbox.service.BlackBoxEngine
import com.utility.toolbox.service.LogManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideClonedAppDao(database: AppDatabase): ClonedAppDao {
        return database.clonedAppDao()
    }

    @Provides
    fun provideLogDao(database: AppDatabase): LogDao {
        return database.logDao()
    }

    @Provides
    @Singleton
    fun provideLogManager(
        logDao: LogDao,
        @ApplicationContext context: Context
    ): LogManager {
        return LogManager.getInstance(context, logDao)
    }

    @Provides
    @Singleton
    fun provideAntiDetectionManager(@ApplicationContext context: Context): AntiDetectionManager {
        return AntiDetectionManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideBlackBoxEngine(@ApplicationContext context: Context): BlackBoxEngine {
        return BlackBoxEngine.getInstance(context)
    }
}
