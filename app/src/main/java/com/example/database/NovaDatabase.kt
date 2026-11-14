package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        TodoEntity::class,
        ModelingTemplateEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun todoDao(): TodoDao
    abstract fun modelingTemplateDao(): ModelingTemplateDao

    companion object {
        @Volatile
        private var INSTANCE: NovaDatabase? = null

        fun getInstance(context: Context): NovaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NovaDatabase::class.java,
                    "nova_notes.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun createInMemory(context: Context): NovaDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                NovaDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
