package app.marketlens

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal class AlertWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
 override suspend fun doWork():Result {
  return try {val repo=MarketRepository.get(applicationContext);val settings=repo.dao.record("settings")?.let{JSONObject(it.json)}?:JSONObject()
   if(!settings.optBoolean("background",false))return Result.success()
   val state=repo.profile();val symbols=linkedSetOf<String>();state.data.optJSONObject("alerts")?.keys()?.forEach{symbols+=it};state.data.optJSONObject("relativeAlerts")?.keys()?.forEach{symbols+=it};state.positions().forEach{symbols+=it.symbol};state.data.optJSONArray("watchlist")?.let{a->for(i in 0 until a.length())symbols+=a.optString(i)}
   val offset=settings.optInt("cursor",0);val all=symbols.toList();val batch=if(all.isEmpty())emptyList()else (0 until minOf(10,all.size)).map{all[(offset+it)%all.size]}
   var successes=0;var failed=0
   for(symbol in batch){try{repo.quote(symbol,true);successes++}catch(e:Exception){failed++}
    if(settings.optBoolean("filings",true)){
     val feeds=repo.research.events(symbol,true)
     for(feed in feeds){if(feed.saved||feed.issue.isNotEmpty())continue;val key="seen:${symbol}:${feed.source.substringBefore('?')}";val old=repo.dao.record(key)?.let{JSONObject(it.json).optJSONArray("keys")};val seen=(0 until (old?.length()?:0)).map{old!!.optString(it)}.toSet();val now=mutableListOf<String>()
      feed.rows.forEach{event->val id="${event.date}|${event.title}|${event.url}";now+=id;if(old!=null&&id !in seen)repo.dao.log(AlertEntry("event:$symbol:$id","$symbol · ${event.kind}","${event.title}\n${event.date}\n${event.url}"))}
      repo.save("seen",key,JSONObject().put("keys",JSONArray(now)))
     }
    }
   }
   if(settings.optBoolean("ipo",true)){val today=LocalDate.now(ZoneId.of("Asia/Kolkata"));state.tracked().forEach{i->listOf("Opens" to i.start,"Closes" to i.end,"Listing" to i.listing).forEach{(label,date)->if(date.isNotEmpty()){val d=LocalDate.parse(date);if(d==today||d==today.plusDays(1))repo.dao.log(AlertEntry("ipo:${i.id()}:$label:$date","${i.symbol} · IPO $label","$date · original NSE schedule. Check source before acting.\n${i.url()}"))}}}}
   settings.put("cursor",if(all.isEmpty())0 else (offset+batch.size)%all.size).put("lastRun",System.currentTimeMillis()).put("lastStatus","$successes quotes checked · $failed unavailable")
   repo.save("settings","settings",settings);deliver(applicationContext,repo,settings);Result.success()
  }catch(e:Exception){Result.retry()}
 }
 companion object {
  fun schedule(context:Context,j:JSONObject){val manager=WorkManager.getInstance(context)
   if(!j.optBoolean("background")){manager.cancelUniqueWork("marketlens-alerts");return}
   val minutes=j.optLong("minutes",30).coerceIn(15,1440)
   val request=PeriodicWorkRequestBuilder<AlertWorker>(minutes,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(if(j.optBoolean("wifiOnly",false))NetworkType.UNMETERED else NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.MINUTES).build()
   manager.enqueueUniquePeriodicWork("marketlens-alerts",ExistingPeriodicWorkPolicy.UPDATE,request)
  }
  fun runNow(context:Context){WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<AlertWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())}
  suspend fun deliver(context:Context,repo:MarketRepository,settings:JSONObject){
   val now=LocalTime.now();val start=settings.optInt("quietStart",22);val end=settings.optInt("quietEnd",8);val quiet=settings.optBoolean("quiet",true)&&if(start==end)false else if(start<end)now.hour in start until end else now.hour>=start||now.hour<end
   if(quiet)return
   if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
   val manager=context.getSystemService(NotificationManager::class.java);if(!manager.areNotificationsEnabled())return
   manager.createNotificationChannel(NotificationChannel("investor-alerts","Investor alerts",NotificationManager.IMPORTANCE_DEFAULT))
   val pending=repo.dao.alerts().filter{!it.delivered}.take(5)
   for(entry in pending){val intent=Intent(context,MainActivity::class.java).putExtra("openAlerts",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val tap=PendingIntent.getActivity(context,entry.id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification=NotificationCompat.Builder(context,"investor-alerts").setSmallIcon(R.drawable.ic_notification).setContentTitle(entry.title).setContentText(entry.detail.lineSequence().first()).setStyle(NotificationCompat.BigTextStyle().bigText(entry.detail)).setContentIntent(tap).setAutoCancel(true).setGroup("marketlens").build()
    manager.notify(entry.id.hashCode(),notification);repo.dao.delivered(entry.id)
   }
  }
 }
}
