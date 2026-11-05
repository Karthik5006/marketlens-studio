package app.marketlens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable internal fun AlertsScreen(s:LensState,vm:LensViewModel,a:UiActions){
 Label("Only what matters to you",Lime);Text("Your investor\ninbox.",color=Ink,fontSize=35.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold)
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action("Alert settings",{a.route("Settings")},primary=true,icon=Icons.Rounded.Tune);Action("Refresh holdings",{vm.refreshPortfolio()},icon=Icons.Rounded.Refresh)}
 val targets=s.profile().data.optJSONObject("alerts");val relatives=s.profile().data.optJSONObject("relativeAlerts");val symbols=(targets?.keys()?.asSequence()?.toList().orEmpty()+relatives?.keys()?.asSequence()?.toList().orEmpty()).distinct();if(symbols.isNotEmpty()){SectionTitle("Price thresholds");symbols.forEach{symbol->val j=targets?.optJSONObject(symbol)?:JSONObject();val relative=relatives?.optJSONObject(symbol);LensCard{Text(symbol,color=Ink,fontWeight=FontWeight.Bold);MetricPair("At / below",rupees(Analytics.num(j,"below")),"At / above",rupees(Analytics.num(j,"above")));if(relative!=null)MetricPair("Return at / below",percent(Analytics.num(relative,"below"),true),"Return at / above",percent(Analytics.num(relative,"above"),true));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action("Edit",{a.form(alertForm(symbol,s,vm))});Action("Remove",{vm.task("removeAlert:$symbol"){vm.profile{it.removeAlert(symbol);it.section("relativeAlerts").remove(symbol)}}})}}}}
 SectionTitle("Recent alerts","Quotes, new filings and tracked IPO dates")
 if(s.alerts.isEmpty())EmptyState("A quieter way to stay informed","Set a price threshold from a stock page. Enable background checks for new filings and tracked IPO dates. The first filing scan establishes a baseline.",Icons.Rounded.NotificationsNone)
 s.alerts.forEach{entry->LensCard(accent=if(entry.title.contains("price"))Lime else Violet){Text(entry.title,color=Ink,fontWeight=FontWeight.SemiBold,fontSize=17.sp);Caption(entry.detail);Caption(java.text.SimpleDateFormat("dd MMM yyyy · HH:mm",java.util.Locale.ENGLISH).format(java.util.Date(entry.createdAt))+if(entry.delivered)" · notification sent"else " · saved to inbox")}}
}
@Composable internal fun SettingsScreen(s:LensState,vm:LensViewModel,a:UiActions){
 val j=s.settings();val context=androidx.compose.ui.platform.LocalContext.current
 Label("Made for your focus",Violet);Text("Make it\nwork your way.",color=Ink,fontSize=35.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold)
 LensCard{Label("Background alerts",Lime)
  SettingSwitch("Background checks","Sends selected stock symbols to market sources while the app is closed. Personal positions and notes stay local.",j.optBoolean("background")){value->vm.task("settings"){val next=JSONObject(j.toString()).put("background",value);vm.settings(next)}}
  SettingSwitch("Company disclosures","New filings for watched / held stocks",j.optBoolean("filings",true)){value->vm.task("settings"){vm.settings(JSONObject(j.toString()).put("filings",value))}}
  SettingSwitch("Tracked IPO dates","Opening, closing and source listing dates",j.optBoolean("ipo",true)){value->vm.task("settings"){vm.settings(JSONObject(j.toString()).put("ipo",value))}}
  SettingSwitch("Unmetered connections only","Usually Wi-Fi; Android determines network metering",j.optBoolean("wifiOnly",false)){value->vm.task("settings"){vm.settings(JSONObject(j.toString()).put("wifiOnly",value))}}
  Caption("Checks are approximate Android WorkManager jobs, not tick-by-tick or exact-time alerts. Battery restrictions, network access and source delays can defer them. Each run rotates through up to 10 symbols.")
  Action("Allow Android notifications",a.permission,icon=Icons.Rounded.NotificationsActive)
  Action("Timing & quiet hours",{a.form(FormSpec("Background timing","Minimum 15 minutes. Quiet hours defer notifications; checks and the inbox continue. Equal start/end disables the quiet interval.",listOf(FormField("minutes","Check interval (minutes)",j.optString("minutes","30"),listOf("15","30","60","180","360","1440")),FormField("quiet","Quiet hours enabled",if(j.optBoolean("quiet",true))"Yes"else "No",listOf("Yes","No")),FormField("quietStart","Start hour · 0–23",j.optString("quietStart","22"),number=true),FormField("quietEnd","End hour · 0–23",j.optString("quietEnd","8"),number=true))){v->val start=v.getValue("quietStart").toInt();val end=v.getValue("quietEnd").toInt();require(start in 0..23&&end in 0..23);vm.settings(JSONObject(j.toString()).put("minutes",v.getValue("minutes").toLong()).put("quiet",v.getValue("quiet")=="Yes").put("quietStart",start).put("quietEnd",end))})})
  Action("Run a background check now",{if(j.optBoolean("background")) {AlertWorker.runNow(context);vm.message("Background check queued. Quiet hours and Android notification permission still apply.")}else vm.message("Enable background checks first")},enabled=j.optBoolean("background"))
  if(j.optLong("lastRun")>0)Caption("Last run "+java.text.SimpleDateFormat("dd MMM HH:mm",java.util.Locale.ENGLISH).format(java.util.Date(j.optLong("lastRun")))+" · "+j.optString("lastStatus"))
  Action("Android app & battery settings",{context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+context.packageName)))})
 }
 ErrorLine(s,"settings")
 LensCard(accent=Violet){Label("Your data stays yours",Violet);Caption("No mandatory account, broker credentials, email access, advertising or trading permissions. Investor records and source downloads stay in app-private storage.");Caption("This release adds notification permission and Android's boot receiver for scheduled checks. Data export is an unencrypted file you choose to save.");Action("Export investor records",{vm.task("export"){val records=vm.repo.dao.records();val body=JSONObject().put("app","MarketLens").put("version",2).put("exportedAt",System.currentTimeMillis()).put("records",JSONArray(records.map{JSONObject().put("id",it.id).put("kind",it.kind).put("json",JSONObject(it.json)).put("updatedAt",it.updatedAt)})).toString(2);a.export("MarketLens-Studio-investor-records.json",body,"application/json")}},icon=Icons.Rounded.Download);Caption("This is a records export. Automatic encrypted backup, restore and multi-device sync are not included.");Action("Import records from original MarketLens",{a.importFile("legacy")},icon=Icons.Rounded.UploadFile);Action("Privacy policy",{a.route("Privacy")},icon=Icons.Rounded.PrivacyTip)}
 LensCard{Text("MarketLens Studio 1.0",color=Ink,fontSize=23.sp,fontWeight=FontWeight.SemiBold);Caption("A separate app with its own private storage. Your original MarketLens app remains installed. Optional record transfer uses a file you select.");Action("Inspect sources & coverage",{a.route("Research")});Action("Contact support",{try{context.startActivity(Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:ykn5006@gmail.com")))}catch(e:Exception){vm.message("Email support at ykn5006@gmail.com")}});Action("Erase Studio data",{a.form(FormSpec("Erase all Studio data","This permanently deletes only MarketLens Studio records, imports and cached downloads on this device. Your original MarketLens app is unaffected. Export records first if needed.",listOf(FormField("confirm","Type ERASE to confirm")),"Erase Studio data"){v->require(v.getValue("confirm")=="ERASE"){"Type ERASE to confirm"};context.getSystemService(android.app.ActivityManager::class.java).clearApplicationUserData()})})}
}
@Composable private fun SettingSwitch(title:String,description:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,color=Ink,fontSize=14.sp,fontWeight=FontWeight.Medium);Caption(description)};Spacer(Modifier.width(10.dp));Switch(value,onChange)}}

@Composable internal fun PrivacyScreen(){
 val context=androidx.compose.ui.platform.LocalContext.current
 val policy=remember{context.assets.open("privacy.txt").bufferedReader().use{it.readText()}}
 Label("Privacy & your choices",Violet)
 Text("Your data. Your control.",color=Ink,fontSize=31.sp,fontWeight=FontWeight.Bold)
 LensCard{Text(policy,color=Ink,fontSize=14.sp,lineHeight=22.sp)}
}
