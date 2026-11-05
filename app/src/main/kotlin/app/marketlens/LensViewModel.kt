package app.marketlens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

internal data class LensState(
 val ready:Boolean=false,val records:List<LocalRecord> =emptyList(),val snapshots:List<SourceSnapshot> =emptyList(),val alerts:List<AlertEntry> =emptyList(),
 val highs:Data.Screen?=null,val lows:Data.Screen?=null,val quotes:Map<String,Data.Quote> =emptyMap(),val financials:Map<String,Data.Financial> =emptyMap(),val ownership:Map<String,Data.Ownership> =emptyMap(),
 val histories:Map<String,List<Bar>> =emptyMap(),val periods:Map<String,List<FinancialParser.Period>> =emptyMap(),val events:Map<String,List<Research.Feed<Research.Event>>> =emptyMap(),val funds:List<Data.Portfolio> =emptyList(),
 val ipos:List<Research.Ipo> =emptyList(),val ipoFeeds:List<Research.Feed<Research.Ipo>> =emptyList(),val info:Map<String,Research.IssueInfo> =emptyMap(),val gmp:Map<String,Research.Feed<Research.Gmp>> =emptyMap(),
 val busy:Set<String> =emptySet(),val errors:Map<String,String> =emptyMap(),val message:String=""
){
 fun json(id:String)=records.firstOrNull{it.id==id}?.let{runCatching{JSONObject(it.json)}.getOrNull()}?:JSONObject()
 fun profile()=InvestorState(records.firstOrNull{it.id=="profile"}?.json?:"{}")
 fun watch():Set<String>{val a=profile().data.optJSONArray("watchlist")?:return emptySet();return (0 until a.length()).map{a.optString(it)}.toSet()}
 fun rows(kind:String)=records.filter{it.kind==kind}
 fun settings()=json("settings")
}
internal class LensViewModel(app:Application):AndroidViewModel(app){
 private val _state=MutableStateFlow(LensState());val state=_state.asStateFlow()
 private val requests=Semaphore(4)
 lateinit var repo:MarketRepository;private set
 init {viewModelScope.launch{try{repo=MarketRepository.get(app)
   launch{repo.dao.observeRecords().collect{rows->_state.update{it.copy(records=rows)}}}
   launch{repo.dao.observeSnapshots().collect{rows->_state.update{it.copy(snapshots=rows)}}}
   launch{repo.dao.observeAlerts().collect{rows->_state.update{it.copy(alerts=rows)}}}
   _state.update{it.copy(ready=true,funds=repo.legacy.portfolios())};refreshMarkets(false)
  }catch(e:Exception){_state.update{it.copy(errors=mapOf("startup" to (e.message?:"Could not open local records")))}}}}
 fun message(text:String){_state.update{it.copy(message=text)}}
 fun clearMessage(){_state.update{it.copy(message="")}}
 fun task(key:String,action:suspend ()->Unit){if(key in _state.value.busy)return
  _state.update{it.copy(busy=it.busy+key,errors=it.errors-key)}
  viewModelScope.launch{try{requests.withPermit{action()}}catch(e:CancellationException){throw e}catch(e:Exception){_state.update{it.copy(errors=it.errors+(key to (e.message?:"Source unavailable")))}}finally{_state.update{it.copy(busy=it.busy-key)}}}
 }
 fun refreshMarkets(force:Boolean=true){for(high in listOf(true,false)){val key=if(high)"highs" else "lows";task(key){val value=withContext(Dispatchers.IO){repo.legacy.screen(high,force)};_state.update{if(high)it.copy(highs=value)else it.copy(lows=value)}}}}
 fun loadStock(symbol:String,force:Boolean=false){
  task("quote:$symbol"){val q=repo.quote(symbol,force);_state.update{it.copy(quotes=it.quotes+(symbol to q))}}
  task("financial:$symbol"){val f=withContext(Dispatchers.IO){repo.legacy.financial(symbol,force)};_state.update{it.copy(financials=it.financials+(symbol to f))}}
  task("ownership:$symbol"){val o=withContext(Dispatchers.IO){repo.legacy.ownership(symbol,force)};_state.update{it.copy(ownership=it.ownership+(symbol to o))}}
  loadHistory(symbol,force)
 }
 fun loadHistory(symbol:String,force:Boolean=false)=task("history:$symbol"){val (bars,_)=repo.history(symbol,force);_state.update{it.copy(histories=it.histories+(symbol to bars))}}
 fun loadPeriods(symbol:String,force:Boolean=false)=task("periods:$symbol"){val values=repo.financialHistory(symbol,force);_state.update{it.copy(periods=it.periods+(symbol to values))}}
 fun loadEvents(symbol:String,force:Boolean=true)=task("events:$symbol"){val feeds=withContext(Dispatchers.IO){repo.research.events(symbol,force)};_state.update{it.copy(events=it.events+(symbol to feeds))}}
 fun toggleWatch(symbol:String)=task("watch"){repo.edit{val a=it.data.optJSONArray("watchlist")?:JSONArray();val list=(0 until a.length()).map{a.optString(it)}.toMutableSet();if(!list.remove(symbol))list+=symbol;it.data.put("watchlist",JSONArray(list.toList()))}}
 fun refreshPortfolio(){val symbols=(_state.value.watch()+_state.value.profile().positions().map{it.symbol}).toList();task("portfolio"){var count=0;var failed=0;symbols.take(100).forEach{symbol->runCatching{repo.quote(symbol,true)}.onSuccess{q->_state.update{it.copy(quotes=it.quotes+(symbol to q))};count++}.onFailure{failed++}};message("$count quotes refreshed · $failed unavailable");AlertWorker.deliver(getApplication(),repo,_state.value.settings())}}
 fun syncFunds()=task("funds"){val status=repo.syncFunds{message(it)};_state.update{it.copy(funds=repo.legacy.portfolios())};message(status)}
 fun loadIpos(force:Boolean=false)=task("ipos"){val feeds=withContext(Dispatchers.IO){repo.research.issues(force)};_state.update{it.copy(ipoFeeds=feeds,ipos=Research.merge(feeds))}}
 fun issueInfo(i:Research.Ipo,force:Boolean=false)=task("issue:${i.id()}"){val value=withContext(Dispatchers.IO){repo.research.issueInfo(i,force)};if(!value.saved){val summary=value.bids.joinToString("\n"){it[0]+": "+it[1]+" shares / "+it[2]+" applications"};repo.dao.snapshot(SourceSnapshot("ipo-bids:${i.id()}:${value.fetched}",JSONObject().put("summary",summary).toString(),value.source,value.stamp,value.fetched,value.issue))};_state.update{it.copy(info=it.info+(i.id() to value))}}
 fun loadGmp(provider:String,force:Boolean=false)=task("gmp:$provider"){val feed=withContext(Dispatchers.IO){repo.research.gmp(provider,force)};repo.edit{s->feed.rows.forEach{s.observe(it,feed.stamp,feed.fetched)}};_state.update{it.copy(gmp=it.gmp+(provider to feed))}}
 fun ensureQuotes(){if(!::repo.isInitialized)return
  val symbols=(_state.value.watch()+_state.value.profile().positions().map{it.symbol}).filter{it !in _state.value.quotes}
  if(symbols.isEmpty())return
  task("cachedQuotes"){val cached=withContext(Dispatchers.IO){symbols.mapNotNull{symbol->repo.legacy.peekQuote(symbol)?.let{symbol to it}}.toMap()};_state.update{it.copy(quotes=it.quotes+cached)}}
 }

 suspend fun save(kind:String,id:String,j:JSONObject){repo.save(kind,id,j)}
 suspend fun profile(action:(InvestorState)->Unit){repo.edit(action)}
 suspend fun importFunds(bytes:ByteArray,name:String,date:String,url:String){val count=repo.importPortfolio(bytes,name,date,url);_state.update{it.copy(funds=repo.legacy.portfolios())};message("Imported $count scheme snapshots")}
 suspend fun settings(j:JSONObject){repo.save("settings","settings",j);AlertWorker.schedule(getApplication(),j)}
 fun delete(id:String)=task("delete:$id"){if(_state.value.records.firstOrNull{it.id==id}?.kind=="trade")repo.deleteTrade(id) else repo.dao.delete(id)}
 fun removeFund(id:String)=task("removeFund"){withContext(Dispatchers.IO){repo.legacy.remove(id)};_state.update{it.copy(funds=repo.legacy.portfolios())}}
}
