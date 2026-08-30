package com.prabhix.operator.di

import android.content.Context
import androidx.room.Room
import com.prabhix.operator.data.local.ConversationDao
import com.prabhix.operator.data.local.MIGRATION_1_2
import com.prabhix.operator.data.local.MessageDao
import com.prabhix.operator.data.local.OutboundQueueDao
import com.prabhix.operator.data.local.PrabhixDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PrabhixDatabase =
        Room.databaseBuilder(context, PrabhixDatabase::class.java, "prabhix_operator.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun conversationDao(db: PrabhixDatabase): ConversationDao = db.conversationDao()
    @Provides fun messageDao(db: PrabhixDatabase): MessageDao = db.messageDao()
    @Provides fun outboundQueueDao(db: PrabhixDatabase): OutboundQueueDao = db.outboundQueueDao()
}
