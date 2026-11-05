package app.marketlens

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal class MarketRepository private constructor(private val context:Context) {
 val legacy=Repository(context)
 val research=ResearchRepository(legacy.net)
 val db=LensDatabase.get(context)
 val dao=db.dao()
 private val mutex=Mutex()
 companion object { @Volatile private var instance:MarketRepository?=null;private val initLock=Mutex()
  suspend fun get(context:Context):MarketRepository=withContext(Dispatchers.IO){instance?:initLock.withLock{instance?:MarketRepository(context.applicationContext).also{it.migrate();instance=it}}}
 }
 private suspend fun migrate(){if(dao.record("profile")!=null)return
  val state=InvestorState("{}");state.data.put("schema",1)
  val watch=emptySet<String>()
  state.data.put("watchlist",JSONArray(watch.toList()));dao.put(LocalRecord("profile","profile",state.toString()))
  archiveFunds()
 }
 suspend fun profile():InvestorState=InvestorState(dao.record("profile")?.json?:"{}")
 suspend fun saveExchangeIssues(issues:List<Research.Ipo>)=mutex.withLock{db.withTransaction{
  issues.forEach{dao.put(LocalRecord("exchangeIpo:${it.id()}","exchangeIpo",it.json().toString()))}
  val state=profile();var changed=false
  issues.forEach{if(state.tracked(it.id())){state.track(it,true);changed=true}}
  if(changed)dao.put(LocalRecord("profile","profile",state.toString()))
 }}
 suspend fun ipoFeeds(force:Boolean):List<Research.Feed<Research.Ipo>> = withContext(Dispatchers.IO){
  val nse=research.issues(force)
  val bse=Research.Feed<Research.Ipo>();bse.source=BseIssues.PAGE
  try{val result=legacy.net.get(BseIssues.PAGE,300_000,force);bse.rows=BseIssues.fromHtml(result.text(),result.url,result.fetched);bse.meta(result)}
  catch(e:Exception){bse.issue="BSE list unavailable · ${Net.message(e)}. Open the original BSE page and read its displayed IPO table; an NSE-only result does not cover BSE-only issues."}
  nse+bse
 }
 suspend fun edit(action:(InvestorState)->Unit)=mutex.withLock{db.withTransaction{val state=profile();action(state);dao.put(LocalRecord("profile","profile",state.toString()))}}
 suspend fun save(kind:String,id:String=java.util.UUID.randomUUID().toString(),j:JSONObject){dao.put(LocalRecord(id,kind,j.toString()))}
 suspend fun archiveFunds(){legacy.portfolios().forEach{p->val key="fund:${p.id}:${p.date}";if(dao.snapshot(key)==null)dao.snapshot(SourceSnapshot(key,p.json().toString(),p.source,p.date,p.fetched))}}
 suspend fun syncFunds(progress:(String)->Unit):String=withContext(Dispatchers.IO){
  archiveFunds();val messages=mutableListOf<String>()
  runCatching{legacy.syncFunds{progress(it)}}.onSuccess{messages+=it}.onFailure{messages+="PPFAS: ${Net.message(it as Exception)}"}
  runCatching{syncHdfc(progress)}.onSuccess{messages+=it}.onFailure{messages+="HDFC: ${Net.message(it as Exception)}"}
  archiveFunds();messages.joinToString("\n")
 }
 private suspend fun syncHdfc(progress:(String)->Unit):String {
  val url="https://www.hdfcfund.com/statutory-disclosure/portfolio/monthly-portfolio"
  val page=legacy.net.get(url,86_400_000,true)
  val content=Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>",RegexOption.DOT_MATCHES_ALL).find(page.text())?.groupValues?.get(1)?:error("Monthly disclosure directory format changed")
  val files=mutableListOf<JSONObject>()
  fun walk(o:Any?){when(o){is JSONObject->{if(o.optJSONObject("file")?.optString("url")?.contains(".xlsx")==true)files+=o;o.keys().forEach{walk(o.opt(it))}};is JSONArray->for(i in 0 until o.length())walk(o.opt(i))}}
  walk(JSONObject(content));var done=0;val failures=mutableListOf<String>()
  for(file in files.distinctBy{it.getJSONObject("file").optString("url")}.take(80)){
   val title=file.optString("title");val source=file.getJSONObject("file").optString("url")
   val date=Regex("(\\d{1,2} [A-Za-z]+ \\d{4})").find(title)?.value?.let{runCatching{LocalDate.parse(it,DateTimeFormatter.ofPattern("d MMMM uuuu",Locale.ENGLISH)).toString()}.getOrNull()}?:continue
   if(!source.startsWith("https://files.hdfcfund.com/"))continue
   progress("HDFC · ${done+1} / ${files.size} disclosures")
   try {val bytes=legacy.net.get(source,86_400_000,false);val name=title.replace(Regex("(?i)^Monthly\\s+"),"").replace(Regex(" - \\d{1,2} [A-Za-z]+ \\d{4}\\.xlsx$"),"")
    val parsed=Xlsx.portfolios(bytes.bytes,name,date,source)
    parsed.forEach{it.id="hdfc:"+it.name.lowercase(Locale.ROOT);it.imported=false;it.fetched=bytes.fetched;it.saved=bytes.saved}
    if(parsed.isNotEmpty()){legacy.add(parsed);done+=parsed.size}
   }catch(e:Exception){failures+=title.substringBefore(" - ")}
  };if(done==0)error("No supported HDFC equity portfolios could be read. Import files from the original page.")
  return "HDFC: $done scheme snapshots updated${if(failures.isNotEmpty())"; ${failures.size} workbooks unsupported (see source page)" else ""}. Equity rows only."
 }
 suspend fun history(symbol:String,force:Boolean=false):Pair<List<Bar>,SourceSnapshot> =withContext(Dispatchers.IO){
  val today=LocalDate.now(ZoneId.of("Asia/Kolkata"));val f=DateTimeFormatter.ofPattern("dd-MM-uuuu")
  val key="history:$symbol"
  try {
   val data=JSONArray();val sources=JSONArray();val issues=mutableListOf<String>();var fetched=Long.MAX_VALUE
   var start=today.minusDays(364)
   while(!start.isAfter(today)) {
    val end=minOf(start.plusDays(89),today)
    val url=Repository.NSE+"/api/historicalOR/generateSecurityWiseHistoricalData?from=${start.format(f)}&to=${end.format(f)}&symbol=${Repository.encoded(symbol)}&type=priceVolumeDeliverable&series=ALL"
    val result=legacy.net.get(url,3_600_000,force)
    val rows=JSONObject(result.text()).optJSONArray("data")?:error("Exchange history format changed")
    for(i in 0 until rows.length())data.put(rows.getJSONObject(i))
    sources.put(url);fetched=minOf(fetched,result.fetched);if(result.issue.isNotBlank())issues+=result.issue
    start=end.plusDays(1)
   }
   val payload=JSONObject().put("data",data).put("sources",sources).toString()
   val bars=Analytics.bars(payload,symbol,legacy.stock(symbol).series)
   require(bars.isNotEmpty()){"No recognized history for this stock series"}
   val snap=SourceSnapshot(key,payload,Repository.NSE+"/report-detail/eq_security",bars.last().date,fetched,issues.distinct().joinToString("; "))
   dao.snapshot(snap);bars to snap
  }catch(e:Exception){val old=dao.snapshot(key)?:throw e;Analytics.bars(old.payload,symbol,legacy.stock(symbol).series) to old.copy(issue="Saved history · "+Net.message(e))}

 }
 suspend fun financialHistory(symbol:String,force:Boolean=false):List<FinancialParser.Period> =withContext(Dispatchers.IO){
  val r=legacy.net.get(Repository.financialUrl(symbol),86_400_000,force);val latest=Parse.latest(r.text(),symbol,true);val basis=latest.optString("consolidated")
  val raw=org.json.JSONTokener(r.text()).nextValue();val array=if(raw is JSONArray)raw else (raw as JSONObject).getJSONArray("data")
  val rows=(0 until array.length()).mapNotNull{array.optJSONObject(it)}.filter{it.optString("symbol")==symbol&&it.optString("consolidated")==basis&&Net.allowed(it.optString("xbrl"))}.distinctBy{it.optString("qe_Date")}.take(12)
  val periods=mutableListOf<FinancialParser.Period>();var errors=0
  for(row in rows)runCatching{val source=row.getString("xbrl");val doc=legacy.net.get(source,31_536_000_000,false);periods+=FinancialParser.parse(doc.bytes,basis,source)}.onFailure{errors++}
  require(periods.isNotEmpty()){"No supported whole-company financial contexts returned"}
  val unique=periods.distinctBy{it.start+it.end+it.basis}.sortedByDescending{it.end+it.start}
  dao.snapshot(SourceSnapshot("financial:$symbol",JSONArray(unique.map{FinancialParser.json(it)}).toString(),Repository.financialUrl(symbol),unique.maxOf{it.end},r.fetched,if(errors>0)"$errors filings unavailable" else r.issue));unique
 }
 suspend fun quote(symbol:String,force:Boolean=false):Data.Quote=withContext(Dispatchers.IO){legacy.quote(symbol,force).also{q->
  val messages=mutableListOf<String>();edit{state->messages+=state.check(symbol,q.last,q.stamp,!q.saved&&q.issue.isEmpty());if(!q.saved&&q.issue.isEmpty()){
   val relative=state.data.optJSONObject("relativeAlerts")?.optJSONObject(symbol);val position=state.positions().firstOrNull{it.symbol==symbol&&it.cost()>0}
   if(relative!=null&&position!=null){val ret=(position.value(q.last)/position.cost()-1)*100
    for(side in listOf("below","above")){val threshold=Analytics.num(relative,side);if(!threshold.isFinite())continue;val met=if(side=="below")ret<=threshold else ret>=threshold;if(met&&!relative.optBoolean("met_$side"))messages+="$symbol · return $side $threshold% of acquisition cost · ${percent(ret,true)} as of ${q.stamp}";relative.put("met_$side",met)}
   }
  }}
  if(!q.saved&&q.issue.isEmpty()){val j=JSONObject().put("price",q.last).put("stamp",q.stamp);Research.put(j,"pe",q.pe);Research.put(j,"cap",q.cap);dao.snapshot(SourceSnapshot("valuation:$symbol:${q.stamp}",j.toString(),Repository.quoteUrl(symbol,q.series),q.stamp,q.fetched))}
  messages.forEach{message->val id="price:$symbol:${q.stamp}:$message";dao.log(AlertEntry(id,"$symbol price alert",message))}
 }}
 suspend fun importPortfolio(bytes:ByteArray,name:String,date:String,url:String):Int=withContext(Dispatchers.IO){
  val parsed=if(bytes.size>2&&bytes[0]=='P'.code.toByte()&&bytes[1]=='K'.code.toByte())Xlsx.portfolios(bytes,name,date,url)else Parse.importCsv(bytes.toString(Charsets.UTF_8),name,date,url)
  archiveFunds();legacy.add(parsed);archiveFunds();parsed.size
 }
 suspend fun saveTrade(id:String,j:JSONObject){mutex.withLock{val rows=dao.records().filter{it.kind=="trade"&&it.id!=id}+LocalRecord(id,"trade",j.toString());Analytics.fifo(Analytics.trades(rows));dao.put(rows.last())}}
 suspend fun deleteTrade(id:String){mutex.withLock{Analytics.fifo(Analytics.trades(dao.records().filter{it.id!=id}));dao.delete(id)}}
 suspend fun importTrades(text:String):Int=mutex.withLock{
  val rows=Parse.csv(text);require(rows.size>1){"CSV is empty"};val h=Parse.headers(rows[0]);require(listOf("date","symbol","side","quantity","price","fees","account").all{h.containsKey(it)}){"Required columns: date,symbol,side,quantity,price,fees,account; optional id"}
  val existing=dao.records().filter{it.kind=="trade"};val incoming=rows.drop(1).map{r->val j=JSONObject();listOf("date","symbol","side","quantity","price","fees","account").forEach{k->val value=Parse.col(r,h,k);if(k in listOf("quantity","price","fees")){val n=Parse.number(value);require(n.isFinite()){"Invalid $k"};j.put(k,n)}else j.put(k,if(k in listOf("symbol","side"))value.uppercase(Locale.ROOT)else value)}
   require(!LocalDate.parse(j.getString("date")).isAfter(LocalDate.now())){"Future transactions cannot be booked"}
   val supplied=Parse.col(r,h,"id");val id=if(supplied.isNotEmpty())"trade:$supplied"else "trade:"+java.security.MessageDigest.getInstance("SHA-256").digest(j.toString().toByteArray()).joinToString(""){"%02x".format(it)}
   LocalRecord(id,"trade",j.toString(),System.currentTimeMillis()+rows.indexOf(r))
  }.distinctBy{it.id};val added=incoming.filter{n->existing.none{it.id==n.id}}
  Analytics.fifo(Analytics.trades(existing+added));db.withTransaction{dao.putAll(added)};added.size
 }
}
