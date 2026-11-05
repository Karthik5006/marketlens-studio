package app.marketlens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal object Screener {
 val numeric=listOf("maxPE","maxDE","minMargin","minGrowth","nearHigh","nearLow","minVolume","minRSI","maxRSI","minCap")
 fun matches(j:JSONObject,q:Data.Quote?,f:Data.Financial?,bars:List<Bar>):Boolean {
  fun test(key:String,value:Double,max:Boolean)=!j.has(key)||(value.isFinite()&&if(max)value<=Analytics.num(j,key)else value>=Analytics.num(j,key))
  if(!test("maxPE",q?.pe?:Double.NaN,true)||!test("maxDE",f?.debtEquity?:Double.NaN,true)||!test("minMargin",f?.let{Data.margin(it.profit,it.revenue)}?:Double.NaN,false)||!test("minGrowth",f?.yearAgo?.let{Research.change(f.revenue,it.revenue)}?:Double.NaN,false))return false
  if(!test("nearHigh",q?.let{if(it.high>0)(1-it.last/it.high)*100 else Double.NaN}?:Double.NaN,true)||!test("nearLow",q?.let{if(it.low>0)(it.last/it.low-1)*100 else Double.NaN}?:Double.NaN,true)||!test("minCap",q?.cap?.div(1e7)?:Double.NaN,false))return false
  val rsi=Analytics.rsi(bars.map{it.close});return test("minVolume",bars.lastOrNull()?.volume?:Double.NaN,false)&&test("minRSI",rsi,false)&&test("maxRSI",rsi,true)
 }
}
@Composable internal fun ResearchScreen(s:LensState,vm:LensViewModel,a:UiActions){
 Label("Your research studio",Violet);Text("Good questions.\nBetter context.",color=Ink,fontSize=35.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold)
 var section by rememberSaveable{mutableStateOf("Screeners")};ChoiceRow(listOf("Screeners","Compare","Journal","Sources"),section){section=it}
 when(section){
 "Screeners"->{Action("Create a custom screener",{a.form(screenerForm(vm))},primary=true,icon=Icons.Rounded.FilterAlt)
 val saved=s.rows("screener");if(saved.isEmpty())EmptyState("Build your own lens","Combine valuation, growth, leverage, annual-range distance, volume and RSI. Screens evaluate only the loaded universe, with missing fields excluded.")
 saved.forEach{record->val j=JSONObject(record.json);var expanded by remember(record.id){mutableStateOf(false)};val universe=when(j.optString("universe")){"Watchlist"->s.watch();"New highs"->s.highs?.hits?.map{it.stock.symbol}?.toSet().orEmpty();"New lows"->s.lows?.hits?.map{it.stock.symbol}?.toSet().orEmpty();else->s.quotes.keys};val matches=universe.filter{Screener.matches(j,s.quotes[it],s.financials[it],s.histories[it].orEmpty())}
  LensCard(accent=Violet){Label("Saved screener",Violet);Text(j.optString("name"),color=Ink,fontSize=21.sp,fontWeight=FontWeight.SemiBold);Caption(j.optString("universe")+" · ${universe.size} candidates · ${universe.count{it in s.quotes}} quotes loaded · ${matches.size} matches");Caption(Screener.numeric.filter{j.has(it)}.joinToString(" · "){"${screenLabel(it)} ${number(Analytics.num(j,it))}"});Action(if("screen:${record.id}" in s.busy)"Loading data…"else "Load first 30 candidates",{vm.task("screen:${record.id}"){var done=0;universe.take(30).forEach{symbol->vm.loadStock(symbol);done++};vm.message("Started $done source lookups. Results update as fields arrive.")}},enabled="screen:${record.id}" !in s.busy);Caption("Each lookup loads quote, financials, ownership and daily history. Remaining candidates need a separate load; this is not an exchange-wide scan.");Action(if(expanded)"Hide matches"else "Show matches",{expanded=!expanded});Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action("Edit",{a.form(screenerForm(vm,record))});Action("Delete",{vm.delete(record.id)})}}
  if(expanded)matches.take(60).forEach{symbol->val q=s.quotes[symbol];StockTile(vm.repo.legacy.stock(symbol),q?.last?:Double.NaN,q?.change?:Double.NaN,q?.stamp?:"Source values incomplete"){a.stock(symbol)}}
 }
 }
 "Compare"->{var one by rememberSaveable{mutableStateOf("")};var two by rememberSaveable{mutableStateOf("")};OutlinedTextField(one,{one=it.uppercase()},label={Text("First NSE symbol")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(two,{two=it.uppercase()},label={Text("Second NSE symbol")},singleLine=true,modifier=Modifier.fillMaxWidth());Action("Load comparison",{if(Data.validSymbol(one)&&Data.validSymbol(two)){vm.loadStock(one);vm.loadStock(two)}else vm.message("Enter two valid symbols")},primary=true)
  if(one in s.quotes&&two in s.quotes){val q=s.quotes.getValue(one);val r=s.quotes.getValue(two);LensCard{MetricPair("Company",one,"Company",two);MetricPair("Market cap",crore(q.cap),"Market cap",crore(r.cap));MetricPair("Exchange P/E",number(q.pe),"Exchange P/E",number(r.pe));MetricPair("Net margin",percent(s.financials[one]?.let{Data.margin(it.profit,it.revenue)}?:Double.NaN),"Net margin",percent(s.financials[two]?.let{Data.margin(it.profit,it.revenue)}?:Double.NaN));MetricPair("Debt / equity",number(s.financials[one]?.debtEquity?:Double.NaN),"Debt / equity",number(s.financials[two]?.debtEquity?:Double.NaN));Caption("Quote dates: ${q.stamp} / ${r.stamp}. Financial periods may differ; inspect filings before comparing.");Metric("Daily-return correlation",number(Analytics.correlation(s.histories[one].orEmpty(),s.histories[two].orEmpty()),3));Caption("At least 20 matching return intervals required. Raw prices can be affected by corporate actions.")}}
 }
 "Journal"->{val notes=s.profile().data.optJSONObject("notes");val symbols=notes?.keys()?.asSequence()?.toList().orEmpty();if(symbols.isEmpty())EmptyState("Keep your reasoning","Open a stock and save a thesis, valuation assumptions and risks in Notes.");symbols.forEach{symbol->LensCard{Text(symbol,color=Lime,fontWeight=FontWeight.Bold);Caption(s.profile().note(symbol));Action("Review stock",{a.stock(symbol)})}}}
 else->{LensCard{Label("Original sources",Lime);Caption("NSE: quotes, annual extremes, daily history, integrated financial filings, ownership, announcements, corporate actions and IPO feeds. HDFC/PPFAS: supported monthly equity disclosures. Other AMCs: user-imported originals.");Caption("NSE/BSE public pages can delay or block access. BSE native data is not included in this build. No source date is replaced with a download date.");Caption("Cash-flow extraction supports recognized whole-company INR XBRL facts only. ROE/ROCE require matching annual profit and opening/closing balance-sheet figures; original-statement entry is available when extraction is incomplete. Broader governance coverage still depends on the original filings.");Caption("GMP: unofficial publisher reports, retained separately. Your transactions, dividends, IPO applications and fund facts are local manual records.");Action("NSE official website",{a.url(Repository.NSE)});Action("BSE official high/low",{a.url("https://www.bseindia.com/markets/equity/EQReports/HighLow.html")});Action("AMFI official disclosure directory",{a.url(Repository.AMFI)})}}
 }
}
private fun screenLabel(k:String)=mapOf("maxPE" to "P/E ≤","maxDE" to "D/E ≤","minMargin" to "Margin ≥","minGrowth" to "YoY revenue ≥","nearHigh" to "% below high ≤","nearLow" to "% above low ≤","minVolume" to "Volume ≥","minRSI" to "RSI ≥","maxRSI" to "RSI ≤","minCap" to "Cap Cr ≥")[k]?:k
private fun screenerForm(vm:LensViewModel,record:LocalRecord?=null):FormSpec{val j=record?.let{JSONObject(it.json)}?:JSONObject();return FormSpec("Custom screener","Leave optional filters empty. A missing source metric fails an active filter. These rules identify research candidates.",listOf(FormField("name","Screener name",j.optString("name")),FormField("universe","Universe",j.optString("universe","Watchlist"),listOf("Watchlist","New highs","New lows","Loaded quotes")))+Screener.numeric.map{FormField(it,screenLabel(it),j.optString(it),number=true)}){v->require(v.getValue("name").isNotBlank()){"Name is required"};val out=JSONObject().put("name",v.getValue("name")).put("universe",v.getValue("universe"));Screener.numeric.forEach{k->if(v.getValue(k).isNotBlank()){val n=v.getValue(k).toDouble();require(n.isFinite());if(k in listOf("nearHigh","nearLow","minVolume","maxPE","maxDE","minCap"))require(n>=0);if(k in listOf("minRSI","maxRSI"))require(n in 0.0..100.0);out.put(k,n)}};if(out.has("minRSI")&&out.has("maxRSI"))require(out.getDouble("minRSI")<=out.getDouble("maxRSI")){"Minimum RSI exceeds maximum"};vm.save("screener",record?.id?:java.util.UUID.randomUUID().toString(),out)}}
