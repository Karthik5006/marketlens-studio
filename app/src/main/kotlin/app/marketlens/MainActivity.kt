@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.animation.ExperimentalAnimationApi::class)
package app.marketlens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import org.json.JSONObject

class MainActivity:ComponentActivity(){
 private var showAlerts by mutableStateOf(false)
 override fun onCreate(state:Bundle?){super.onCreate(state);enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT));showAlerts=intent.getBooleanExtra("openAlerts",false);setContent{LensTheme{LensApp(showAlerts,onAlertsHandled={showAlerts=false})}}}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);showAlerts=intent.getBooleanExtra("openAlerts",false)}
}
internal class UiActions(val form:(FormSpec)->Unit,val stock:(String)->Unit,val ipo:(Research.Ipo)->Unit,val url:(String)->Unit,val route:(String)->Unit,val importFile:(String)->Unit,val export:(String,String,String)->Unit,val permission:()->Unit)
@Composable private fun LensApp(openAlerts:Boolean,onAlertsHandled:()->Unit,vm:LensViewModel=viewModel()){
 val s by vm.state.collectAsStateWithLifecycle();val context=androidx.compose.ui.platform.LocalContext.current
 var tab by rememberSaveable{mutableStateOf("Markets")};var stock by rememberSaveable{mutableStateOf<String?>(null)};var issue by remember{mutableStateOf<Research.Ipo?>(null)};var extra by rememberSaveable{mutableStateOf("")};var search by rememberSaveable{mutableStateOf(false)}
 var editor by remember{mutableStateOf<FormSpec?>(null)};var importKind by rememberSaveable{mutableStateOf("fund")};var exportBody by remember{mutableStateOf("")}
 val scope=rememberCoroutineScope();val snack=remember{SnackbarHostState()}
 val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed->vm.message(if(allowed)"Notifications allowed. Enable background checks in alert settings."else "Notifications are disabled; the alert inbox still works.")}
 val exportPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->if(uri!=null)scope.launch{try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(exportBody.toByteArray())}?:error("Cannot open destination")};vm.message("Export saved")}catch(e:Exception){vm.message(e.message?:"Export failed")}}}
 val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{try{val bytes=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{Net.read(it,Net.MAX)}?:error("Cannot read file")}
  if(importKind=="legacy"){val imported=LegacyImport.parse(bytes.toString(Charsets.UTF_8));editor=FormSpec("Transfer MarketLens records","${imported.positions().size} equity positions and ${imported.tracked().size} tracked IPOs, plus available notes and thresholds. Your original app is unchanged. Older exports do not include its watchlist.",listOf(FormField("mode","On conflicts","Keep Studio records",listOf("Keep Studio records","Replace conflicting records"))),"Import selected file"){v->vm.profile{current->LegacyImport.merge(current,imported,v.getValue("mode")=="Replace conflicting records")};vm.message("Investor records imported locally")}}
  else if(importKind=="trades"){val n=withContext(Dispatchers.IO){vm.repo.importTrades(bytes.toString(Charsets.UTF_8))};vm.message("Imported $n new transactions; duplicates skipped")}
  else editor=FormSpec("Import fund disclosure","Import the AMC's XLSX or the documented holdings CSV. Imported files remain labelled as supplied by you.",listOf(FormField("name","Fund name (optional for XLSX)"),FormField("date","Reporting date · YYYY-MM-DD"),FormField("url","Original HTTPS source (optional)")),"Import"){v->val url=v.getValue("url");require(url.isBlank()||safeUrl(url)){"Enter a valid HTTPS source"};vm.importFunds(bytes,v.getValue("name"),v.getValue("date"),url)}
 }catch(e:Exception){vm.message(e.message?:"Import failed")}}}
 val actions=UiActions(form={editor=it},stock={stock=it;issue=null;extra=""},ipo={issue=it;stock=null;extra=""},url={url->try{require(safeUrl(url));context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(e:Exception){vm.message("This source link cannot be opened")}},route={if(it in listOf("Markets","Portfolio","Funds","IPOs","Research")){tab=it;extra=""}else extra=it;stock=null;issue=null},importFile={importKind=it;importPicker.launch(if(it=="legacy")arrayOf("application/json","text/*","application/octet-stream")else if(it=="trades")arrayOf("text/*","application/octet-stream")else arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","text/*","application/octet-stream"))},export={name,body,_->exportBody=body;exportPicker.launch(name)},permission={if(android.os.Build.VERSION.SDK_INT>=33)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)else vm.message("Manage notifications in Android app settings")})
 LaunchedEffect(openAlerts){if(openAlerts){extra="Alerts";stock=null;issue=null;onAlertsHandled()}}
 LaunchedEffect(s.message){if(s.message.isNotBlank()){snack.showSnackbar(s.message);vm.clearMessage()}}
 LaunchedEffect(s.ready,s.records.firstOrNull{it.id=="profile"}?.updatedAt){if(s.ready)vm.ensureQuotes()}
 BackHandler(stock!=null||issue!=null||extra.isNotEmpty()){stock=null;issue=null;extra=""}
 Scaffold(containerColor=Night,snackbarHost={SnackbarHost(snack)},topBar={
  Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
   if(stock!=null||issue!=null||extra.isNotEmpty())IconButton({stock=null;issue=null;extra=""}){Icon(Icons.Rounded.ArrowBack,"Back",tint=Ink)}else Box(Modifier.size(32.dp).background(Lime,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.Insights,null,tint=Night,modifier=Modifier.size(22.dp))}
   Spacer(Modifier.width(10.dp));Text("market",fontSize=23.sp,color=Ink,fontWeight=FontWeight.Bold);Text("lens",fontSize=23.sp,color=Lime,fontWeight=FontWeight.Light);Text(" STUDIO",fontSize=8.sp,color=Violet,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f))
   IconButton({extra="Alerts";stock=null;issue=null},Modifier.size(42.dp)){BadgedBox(badge={if(s.alerts.any{!it.delivered})Badge(containerColor=Violet)}){Icon(Icons.Rounded.NotificationsNone,"Alerts",tint=Ink)}}
   IconButton({extra="Settings";stock=null;issue=null},Modifier.size(42.dp)){Icon(Icons.Rounded.Tune,"Settings",tint=Ink)}
  }
 },bottomBar={Surface(color=Night,shadowElevation=12.dp){Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=8.dp,vertical=8.dp),horizontalArrangement=Arrangement.SpaceAround){
  val icons=listOf(Icons.Rounded.Explore,Icons.Rounded.PieChart,Icons.Rounded.Layers,Icons.Rounded.RocketLaunch,Icons.Rounded.AutoGraph)
  listOf("Markets","Portfolio","Funds","IPOs","Research").forEachIndexed{i,name->val selected=tab==name&&extra.isEmpty();Column(Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).clickable{tab=name;stock=null;issue=null;extra=""}.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if(selected)Lime.copy(alpha=.13f)else Color.Transparent).padding(horizontal=16.dp,vertical=4.dp)){Icon(icons[i],name,tint=if(selected)Lime else Muted,modifier=Modifier.size(21.dp))};Text(name,fontSize=10.sp,color=if(selected)Lime else Muted,fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal)}}
 }}}
 ){padding->
  if(!s.ready)Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),verticalArrangement=Arrangement.Center){Text("A clearer view\nof your investments.",fontSize=35.sp,color=Ink,fontWeight=FontWeight.Bold);Spacer(Modifier.height(20.dp));if(s.errors["startup"]!=null)Caption(s.errors.getValue("startup"),Pink)else CircularProgressIndicator(color=Lime)}
  else AnimatedContent(targetState=stock?.let{"stock:$it"}?:issue?.let{"ipo:${it.id()}"}?:extra.ifBlank{tab},modifier=Modifier.padding(padding),transitionSpec={fadeIn(animationSpec=androidx.compose.animation.core.tween(220))+slideInHorizontally(initialOffsetX={it/18}) togetherWith fadeOut(animationSpec=androidx.compose.animation.core.tween(120))},label="screen"){route->
   val scroll=rememberScrollState();Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
    when {
     route.startsWith("stock:")->StockScreen(route.substringAfter(":"),s,vm,actions)
     route.startsWith("ipo:")->issue?.let{IpoDetail(it,s,vm,actions)}
     route=="Markets"->MarketsScreen(s,vm,actions){search=true}
     route=="Portfolio"->PortfolioScreen(s,vm,actions)
     route=="Funds"->FundsScreen(s,vm,actions)
     route=="IPOs"->IposScreen(s,vm,actions)
     route=="BSE source"->BseSourceScreen(vm,actions)
     route=="Research"->ResearchScreen(s,vm,actions)
     route=="Privacy"->PrivacyScreen()
     route=="Alerts"->AlertsScreen(s,vm,actions)
     else->SettingsScreen(s,vm,actions)
    }
   }
  }
 }
 if(editor!=null)EditorSheet(editor!!){editor=null}
 if(search&&s.ready)ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest={search=false},containerColor=Panel){var query by remember{mutableStateOf("")};Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal=20.dp)){Text("Find your next idea",color=Ink,fontSize=26.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(15.dp));OutlinedTextField(query,{query=it},label={Text("Company or NSE symbol")},modifier=Modifier.fillMaxWidth(),singleLine=true,leadingIcon={Icon(Icons.Rounded.Search,null)},shape=RoundedCornerShape(16.dp));Spacer(Modifier.height(12.dp));val stocks=remember(query){vm.repo.legacy.stocks.values.filter{(it.name+it.symbol).contains(query,true)}.take(60)};LazyColumn{items(stocks,key={it.symbol}){item->ListItem(headlineContent={Text(item.symbol)},supportingContent={Text(item.name,maxLines=2)},trailingContent={Icon(Icons.Rounded.NorthEast,null,tint=Lime)},colors=ListItemDefaults.colors(containerColor=Panel),modifier=Modifier.clickable{search=false;actions.stock(item.symbol)})}}}}
}
internal fun safeUrl(raw:String):Boolean=runCatching{val uri=Uri.parse(raw);uri.scheme=="https"&&!uri.host.isNullOrBlank()&&uri.userInfo==null}.getOrDefault(false)
