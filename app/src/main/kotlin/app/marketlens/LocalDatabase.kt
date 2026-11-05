package app.marketlens

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="records",indices=[Index("kind")])
internal data class LocalRecord(@PrimaryKey val id:String,val kind:String,val json:String,val updatedAt:Long=System.currentTimeMillis())
@Entity(tableName="snapshots")
internal data class SourceSnapshot(@PrimaryKey val key:String,val payload:String,val source:String,val asOf:String,val retrieved:Long,val issue:String="")
@Entity(tableName="notifications")
internal data class AlertEntry(@PrimaryKey val id:String,val title:String,val detail:String,val createdAt:Long=System.currentTimeMillis(),val delivered:Boolean=false)
@Dao internal interface LensDao {
 @Query("SELECT * FROM records ORDER BY updatedAt DESC")fun observeRecords():Flow<List<LocalRecord>>
 @Query("SELECT * FROM records")suspend fun records():List<LocalRecord>
 @Query("SELECT * FROM records WHERE id=:id LIMIT 1")suspend fun record(id:String):LocalRecord?
 @Insert(onConflict=OnConflictStrategy.REPLACE)suspend fun put(record:LocalRecord)
 @Insert(onConflict=OnConflictStrategy.REPLACE)suspend fun putAll(records:List<LocalRecord>)
 @Query("DELETE FROM records WHERE id=:id")suspend fun delete(id:String)
 @Query("SELECT * FROM snapshots")fun observeSnapshots():Flow<List<SourceSnapshot>>
 @Query("SELECT * FROM snapshots WHERE `key`=:key LIMIT 1")suspend fun snapshot(key:String):SourceSnapshot?
 @Insert(onConflict=OnConflictStrategy.REPLACE)suspend fun snapshot(value:SourceSnapshot)
 @Query("SELECT * FROM notifications ORDER BY createdAt DESC LIMIT 100")fun observeAlerts():Flow<List<AlertEntry>>
 @Query("SELECT * FROM notifications ORDER BY createdAt DESC LIMIT 100")suspend fun alerts():List<AlertEntry>
 @Insert(onConflict=OnConflictStrategy.IGNORE)suspend fun log(entry:AlertEntry):Long
 @Query("UPDATE notifications SET delivered=1 WHERE id=:id")suspend fun delivered(id:String)
}
@Database(entities=[LocalRecord::class,SourceSnapshot::class,AlertEntry::class],version=1,exportSchema=false)
internal abstract class LensDatabase:RoomDatabase(){abstract fun dao():LensDao
 companion object { @Volatile private var instance:LensDatabase?=null
  fun get(context:Context)=instance?:synchronized(this){instance?:Room.databaseBuilder(context.applicationContext,LensDatabase::class.java,"marketlens-v2.db").build().also{instance=it}}
 }
}
