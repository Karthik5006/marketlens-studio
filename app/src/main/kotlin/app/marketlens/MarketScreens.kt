package app.marketlens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.*

@Composable internal fun MarketsScreen(s:LensState,vm:LensViewModel,a:UiActions,search:()->Unit){
 var mode by rememberSaveable{mutableStateOf("New highs")};var limit by rememberSaveable{mutableIntStateOf(25)}
 Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Label("Your market, in focus",Lime);Text("Find the\nnext opportunity.",fontSize=36.sp,lineHeight=40.sp,color=Ink,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp))};Box(Modifier.size(66.dp).background(Violet.copy(alpha=.12f),CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Rounded.TravelExplore,null,tint=Violet,modifier=Modifier.size(34.dp))}}
 Caption("Official exchange data. A clearer view of the extremes.")
 Surface(onClick=search,shape=RoundedCornerShape(17.dp),color=Panel,border=BorderStroke(1.dp,Line)){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Search,null,tint=Muted,modifier=Modifier.size(20.dp));Spacer(Modifier.width(12.dp));Text("Search stocks & companies",color=Muted,fontSize=14.sp);Spacer(Modifier.weight(1f));Icon(Icons.Rounded.NorthEast,null,tint=Lime,modifier=Modifier.size(18.dp))}}
 Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){ExtremeTile("52W HIGHS",s.highs?.hits?.size,Lime,Modifier.weight(1f)){mode="New highs"};ExtremeTile("52W LOWS",s.lows?.hits?.size,Pink,Modifier.weight(1f)){mode="New lows"}}
 ChoiceRow(listOf("New highs","New lows","Watchlist"),mode){mode=it;limit=25}
 val feed=if(mode=="New lows")s.lows else s.highs
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(if(mode=="Watchlist")"Your watchlist"else "At the year's edge",color=Ink,fontSize=20.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));IconButton({if(mode=="Watchlist")vm.refreshPortfolio()else vm.refreshMarkets()}){Icon(Icons.Rounded.Refresh,"Refresh market data",tint=Lime)}}
 if(mode=="Watchlist"){
  if(s.watch().isEmpty())EmptyState("Make it yours","Save a stock from its research page to follow it here.",Icons.Rounded.BookmarkBorder)
  s.watch().sorted().forEach{symbol->val q=s.quotes[symbol];StockTile(vm.repo.legacy.stock(symbol),q?.last?:Double.NaN,q?.change?:Double.NaN,q?.stamp?:"Open to fetch quote"){a.stock(symbol)}}
  ErrorLine(s,"portfolio")
 }else{
  ErrorLine(s,if(mode=="New lows")"lows"else "highs"){vm.refreshMarkets()}
  if(feed!=null){Caption("${feed.stamp} IST · ${feed.hits.size} stocks${if(feed.saved)" · saved download"else ""}");if(feed.issue.isNotEmpty())Caption(feed.issue,Pink)
   feed.hits.take(limit).forEach{h->StockTile(h.stock,h.last,h.change,"Touched ${rupees(h.level)}"){a.stock(h.stock.symbol)}}
   if(feed.hits.isEmpty())EmptyState("No extremes reported","The exchange returned an empty list for this session.")
   if(feed.hits.size>limit)Action("Show 25 more",{limit+=25},modifier=Modifier.fillMaxWidth())
  }
 }
 LensCard(accent=Violet){Label("Go deeper",Violet);Text("Research with context.",color=Ink,fontSize=22.sp,fontWeight=FontWeight.SemiBold);Caption("Screen fundamentals, inspect ownership and see the companies hidden inside your funds.");Action("Open research studio",{a.route("Research")},icon=Icons.Rounded.AutoGraph)}
}
@Composable private fun ExtremeTile(title:String,count:Int?,color:Color,modifier:Modifier,onClick:()->Unit){val n by animateIntAsState(count?:0,tween(650),label="market count");LensCard(modifier.clickable(onClick=onClick),accent=color){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Label(title,color);Icon(if(title.contains("HIGHS"))Icons.Rounded.NorthEast else Icons.Rounded.SouthEast,null,tint=color,modifier=Modifier.size(17.dp))};Text(if(count==null)"—"else n.toString(),color=color,fontSize=39.sp,fontWeight=FontWeight.Light);Caption("Latest NSE session")}}
@Composable internal fun StockTile(stock:Data.Stock,last:Double,change:Double,note:String,onClick:()->Unit){LensCard(Modifier.clickable(onClick=onClick)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(40.dp).background(Elevated,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Text(stock.symbol.take(2),color=Violet,fontWeight=FontWeight.Bold,fontSize=12.sp)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(stock.symbol,color=Ink,fontSize=15.sp,fontWeight=FontWeight.SemiBold);Text(stock.name,color=Muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)};Column(horizontalAlignment=Alignment.End){Text(rupees(last),color=Ink,fontSize=15.sp,fontWeight=FontWeight.Medium);Text(percent(change,true),color=if(change<0)Pink else Lime,fontSize=12.sp)}};Caption(note)}}

@Composable internal fun StockScreen(symbol:String,s:LensState,vm:LensViewModel,a:UiActions){
 LaunchedEffect(symbol){vm.loadStock(symbol)}
 val stock=vm.repo.legacy.stock(symbol);val q=s.quotes[symbol];var section by rememberSaveable(symbol){mutableStateOf("Overview")};var range by rememberSaveable(symbol){mutableStateOf("1Y")};var chart by rememberSaveable{mutableStateOf("Line")};var adjusted by rememberSaveable{mutableStateOf("Raw")}
 Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Label("$symbol · NSE ${q?.series?:stock.series}",Violet);Text(stock.name,color=Ink,fontSize=29.sp,lineHeight=34.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=9.dp))};IconButton({vm.toggleWatch(symbol)}){Icon(if(symbol in s.watch())Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,"Save stock",tint=Lime)}}
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action("Refresh",{vm.loadStock(symbol,true)},icon=Icons.Rounded.Refresh);Action("Add holding",{a.form(positionForm(symbol,s,vm))},icon=Icons.Rounded.Add)}
 ChoiceRow(listOf("Overview","Financials","Ownership","Filings","Notes"),section){section=it}
 when(section){
 "Overview"->{LensCard(accent=Lime){Label(q?.industry?:"Market quote");Text(rupees(q?.last?:Double.NaN),color=Ink,fontSize=43.sp,fontWeight=FontWeight.Light);Text(percent(q?.change?:Double.NaN,true)+" this session",color=if((q?.change?:0.0)<0)Pink else Lime,fontSize=15.sp);if(q!=null){Caption("${q.stamp} IST${if(q.saved)" · saved download"else ""}");if(q.issue.isNotBlank())Caption(q.issue,Pink)}
   ErrorLine(s,"quote:$symbol"){vm.loadStock(symbol,true)}
   ChoiceRow(listOf("1M","3M","6M","1Y"),range){range=it};ChoiceRow(listOf("Raw","Split / bonus adjusted"),adjusted){adjusted=it};val rawBars=s.histories[symbol].orEmpty();val factors=s.rows("adjustment").map{JSONObject(it.json)}.filter{it.optString("symbol")==symbol};val bars=if(adjusted=="Raw")rawBars else Analytics.adjusted(rawBars,factors.map{Analytics.Adjustment(it.getString("date"),it.getDouble("factor"))});val cutoff=LocalDate.now().minusDays(when(range){"1M"->31;"3M"->93;"6M"->186;else->365}.toLong()).toString();PriceChart(bars.filter{it.date>=cutoff},candles=chart=="Candles",sma=chart=="SMA 20")
   ChoiceRow(listOf("Line","Candles","SMA 20"),chart){chart=it};ErrorLine(s,"history:$symbol"){vm.loadHistory(symbol,true)}
   Caption(if(adjusted=="Raw")"NSE daily OHLC and volume · raw prices. SMA uses the displayed series; the technical snapshot below uses raw history."else "Adjusted only using your recorded split/bonus factors (${factors.size}). Prices before each ex-date divide by the factor; volume multiplies. Excludes dividends, rights and demergers. Verify every factor against the original filing.")
   if(adjusted!="Raw") {Action("Record split / bonus factor",{a.form(adjustmentForm(symbol,vm))});factors.forEach{f->Caption("${f.optString("date")} · factor ${f.optString("factor")}");TextButton({a.url(f.optString("source"))}){Text("Factor source")}}}
   val snap=s.snapshots.firstOrNull{it.key=="history:$symbol"};if(snap!=null)SourceNote(snap.source,snap.asOf,snap.retrieved,snap.issue,a.url)
  }
  if(q!=null){LensCard{Label("52-week position");val frac=Data.position(q.last,q.low,q.high);LinearProgressIndicator(progress={if(frac.isFinite())frac.toFloat()else 0f},modifier=Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),color=Lime,trackColor=Elevated);MetricPair("52-week low",rupees(q.low),"52-week high",rupees(q.high));MetricPair("From high",percent((q.last/q.high-1)*100),"Above low",percent((q.last/q.low-1)*100));MetricPair("Market cap",crore(q.cap),"Exchange P/E",number(q.pe)+if(q.pe.isFinite())"×"else "")}}
  val values=s.histories[symbol].orEmpty().map{it.close};LensCard{Label("Technical snapshot",Violet);MetricPair("RSI · Wilder 14",number(Analytics.rsi(values)),"SMA 50",rupees(Analytics.sma(values,50)));MetricPair("SMA 200",rupees(Analytics.sma(values,200)),"Max drawdown · loaded",percent(Analytics.drawdown(values)));Caption("Descriptive indicators over the available raw history.")}
  val valuations=s.snapshots.filter{it.key.startsWith("valuation:$symbol:")}.sortedByDescending{it.retrieved}.take(8)
  if(valuations.isNotEmpty()){SectionTitle("Observed valuations","Only snapshots downloaded by this app");LensCard{valuations.forEach{snap->val j=JSONObject(snap.payload);MetricPair(snap.asOf,rupees(Analytics.num(j,"price")),"Exchange P/E",number(Analytics.num(j,"pe")))}}}
  Action("Set price alert",{a.form(alertForm(symbol,s,vm))},primary=true,icon=Icons.Rounded.NotificationsActive,modifier=Modifier.fillMaxWidth())
 }
 "Financials"->FinancialsScreen(symbol,s,vm,a)
 "Ownership"->OwnershipScreen(symbol,s,vm,a)
 "Filings"->FilingsScreen(symbol,s,vm,a)
 else->{LensCard{Text("Your investment thesis",color=Ink,fontSize=21.sp,fontWeight=FontWeight.SemiBold);Caption(s.profile().note(symbol).ifBlank{"Write what the business does, why you own it, the valuation you expect and what would change your mind."});Action("Edit thesis & risks",{a.form(FormSpec("$symbol · investment thesis",fields=listOf(FormField("note","Thesis, valuation, risks and review criteria",s.profile().note(symbol),multiline=true))){v->vm.profile{it.note(symbol,v.getValue("note"))}})})};Action("Open original NSE quote",{a.url(Repository.NSE+"/get-quotes/equity?symbol="+Repository.encoded(symbol))})}
 }
}
@Composable private fun FinancialsScreen(symbol:String,s:LensState,vm:LensViewModel,a:UiActions){
 val f=s.financials[symbol];ErrorLine(s,"financial:$symbol"){vm.loadStock(symbol,true)}
 if(f!=null)LensCard{Label("Latest comparable filing",Violet);Caption("${f.basis} · ${f.start} → ${f.end}");MetricPair(f.revenueLabel,crore(f.revenue),"Profit after tax",crore(f.profit));MetricPair("Reported EPS · period",rupees(f.eps),"Net margin",percent(Data.margin(f.profit,f.revenue)));MetricPair("Debt / equity · as filed",number(f.debtEquity,4),"Revenue growth · YoY",percent(f.yearAgo?.let{Research.change(f.revenue,it.revenue)}?:Double.NaN,true));f.previous?.let{MetricPair("Revenue growth · prior",percent(Research.change(f.revenue,it.revenue),true),"Profit growth · prior",percent(Research.change(f.profit,it.profit),true))};SourceNote(f.source,f.end,f.fetched,f.issue,a.url)}
 SectionTitle("Financial history","Matching basis. Individual reporting durations.")
 Action("Add original statement figures",{a.form(statementForm(symbol,vm))},icon=Icons.Rounded.Add)
 Action(if("periods:$symbol" in s.busy)"Loading filings…"else "Load statements & cash flows",{vm.loadPeriods(symbol)},primary=true,enabled="periods:$symbol" !in s.busy)
 ErrorLine(s,"periods:$symbol"){vm.loadPeriods(symbol,true)}
 var periodType by rememberSaveable{mutableStateOf("Quarterly")};ChoiceRow(listOf("Quarterly","Annual / longer"),periodType){periodType=it}
 val automatic=(s.periods[symbol]?:s.snapshots.firstOrNull{it.key=="financial:$symbol"}?.let{snap->runCatching{val arr=org.json.JSONArray(snap.payload);(0 until arr.length()).map{FinancialParser.from(arr.getJSONObject(it))}}.getOrNull()}).orEmpty()
 val imported=s.rows("statement").filter{JSONObject(it.json).optString("symbol")==symbol}.mapNotNull{runCatching{FinancialParser.from(JSONObject(it.json))}.getOrNull()}
 val periods=(automatic+imported).sortedByDescending{it.end+it.start}.filter{if(periodType=="Quarterly")it.days in 50..110 else it.days>110}.take(16)
 if(periods.isEmpty())Caption("Load filings to inspect available history. Missing taxonomy fields remain unavailable.")
 periods.forEach{p->LensCard{Label("${p.end} · ${p.days+1} days",Violet);Caption("${p.basis} · starts ${p.start}"+if(p.imported)" · user-entered original statement"else " · extracted exchange XBRL");MetricPair("Revenue / income",crore(p.revenue),"Profit",crore(p.profit));MetricPair("Operating cash flow",crore(p.ocf),"Free cash flow · OCF − capex",crore(p.fcf));MetricPair("Assets",crore(p.value("Assets","TotalAssets")),"Equity",crore(p.equity));MetricPair("Borrowings",crore(p.debt),"Interest coverage",number(p.interestCoverage)+if(p.interestCoverage.isFinite())"×"else "");MetricPair("ROE · derived annual",percent(p.roeDerived),"ROCE · derived annual",percent(p.roceDerived));MetricPair("OCF / reported profit",number(if(p.profit>0)p.ocf/p.profit else Double.NaN),"Net margin",percent(Data.margin(p.profit,p.revenue)));Caption("FCF uses disclosed purchase-of-PPE cash outflow. Interest coverage is (PBT + finance cost) / finance cost for this duration. No annualization. Annual ROE uses average opening/closing equity; ROCE uses average assets minus current liabilities. Missing opening balances stay unavailable.");Action("Original statement",{a.url(p.source)})}}
 val q=s.quotes[symbol];val peers=s.quotes.filter{it.key!=symbol&&it.value.industry.isNotBlank()&&it.value.industry==q?.industry}.entries.take(8)
 SectionTitle("Peer context","Same exchange industry among loaded quotes")
 if(peers.isEmpty())Caption("Load another company in this industry to compare reported P/E and market cap.")
 peers.forEach{(sym,p)->StockTile(vm.repo.legacy.stock(sym),p.last,p.change,"P/E ${number(p.pe)}× · ${crore(p.cap)}"){a.stock(sym)}}
 Action("NSE financial filings",{a.url(Repository.NSE+"/companies-listing/corporate-integrated-filing")})
}
@Composable private fun OwnershipScreen(symbol:String,s:LensState,vm:LensViewModel,a:UiActions){
 var holderLimit by rememberSaveable(symbol){mutableIntStateOf(20)};val o=s.ownership[symbol];ErrorLine(s,"ownership:$symbol"){vm.loadStock(symbol,true)}
 if(o!=null){LensCard(accent=Violet){Label("Company ownership · ${o.date}",Violet);MetricPair("Promoter & group",percent(o.promoter),"Public incl. institutions",percent(o.publicPct));MetricPair("Mutual funds / UTI",percent(o.mutual),"Foreign portfolio investors",percent(o.foreign));MetricPair("Promoter shares pledged",percent(o.pledged),"MF / UTI filing holders",number(o.fundCount,0));o.previous?.let{p->SectionTitle("Quarterly movement",p.date+" → "+o.date);MetricPair("Promoter change",number(o.promoter-p.promoter)+" pp","MF / UTI change",number(o.mutual-p.mutual)+" pp");MetricPair("FPI change",number(o.foreign-p.foreign)+" pp","Public change",number(o.publicPct-p.publicPct)+" pp")};SourceNote(o.source,o.date,o.fetched,o.issue,a.url)}
 SectionTitle("Named holders","As disclosed by the company; coverage may be incomplete")
 o.holders.take(holderLimit).forEach{h->var expanded by remember(h.name){mutableStateOf(false)};val grouped=h.name.contains(',')||h.name.contains(';');LensCard{Label(if(grouped)"Combined disclosed position"else "Disclosed holder",Violet);Text(h.name,color=Ink,fontSize=14.sp,maxLines=if(expanded)Int.MAX_VALUE else 3,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);MetricPair("Company stake",percent(h.percent),"Shares",number(h.shares,0));if(grouped)Caption("The stake belongs to this full group; the filing does not split it between schemes.");TextButton({expanded=!expanded}){Text(if(expanded)"Collapse names"else "Read complete names")}}};Caption("Showing ${minOf(holderLimit,o.holders.size)} of ${o.holders.size} named filing positions");if(o.holders.size>holderLimit)Action("Show 20 more holders",{holderLimit+=20})}
 val isin=s.quotes[symbol]?.isin?.takeIf{Data.isin(it)}?:vm.repo.legacy.stock(symbol).isin
 SectionTitle("Scheme-level portfolios","Exact ISIN match · % of fund assets")
 val matched=s.funds.flatMap{it.holdings}.filter{it.isin==isin&&Data.isin(isin)}
 if(matched.isEmpty())EmptyState("No match in covered schemes","Sync AMC disclosures or import another official portfolio. No match does not establish that no fund holds this stock.",Icons.Rounded.Layers)
 matched.sortedByDescending{it.weight}.forEach{h->LensCard{Text(h.fund,color=Ink,fontWeight=FontWeight.SemiBold);MetricPair("% of fund assets",percent(h.weight),"Disclosed shares",number(h.shares,0));SourceNote(h.source,h.asOf,open=a.url)}}
 Action("Manage fund coverage",{a.route("Funds")})
}
@Composable internal fun FilingsScreen(symbol:String,s:LensState,vm:LensViewModel,a:UiActions){
 LaunchedEffect(symbol){if(symbol !in s.events)vm.loadEvents(symbol,false)}
 var filter by rememberSaveable{mutableStateOf("All")};ChoiceRow(listOf("All","Governance","Results","Actions"),filter){filter=it}
 Action("Refresh original disclosures",{vm.loadEvents(symbol)},icon=Icons.Rounded.Refresh);ErrorLine(s,"events:$symbol"){vm.loadEvents(symbol)}
 val keywords=listOf("auditor","resign","pledge","encumbr","related party","regulat","sebi","penalty","litigat","fraud","default","preferential","allotment")
 val feeds=s.events[symbol].orEmpty();var count=0
 feeds.forEach{feed->val list=feed.rows.filter{e->when(filter){"Governance"->keywords.any{e.title.contains(it,true)};"Results"->listOf("result","financial","earnings","investor presentation","annual report").any{e.title.contains(it,true)};"Actions"->e.kind=="Corporate action";else->true}}.take(60);count+=list.size
 list.forEach{e->LensCard(accent=if(keywords.any{e.title.contains(it,true)})Violet else null){Label(e.kind,Violet);Text(e.title,color=Ink,fontWeight=FontWeight.Medium,fontSize=15.sp);Caption(e.date+(if(e.record.isNotBlank())" · record ${e.record}"else ""));if(keywords.any{e.title.contains(it,true)})Caption("Keyword match for your review; inspect the filing to understand the event.");if(e.url.isNotBlank())Action("Read original filing",{a.url(e.url)})}}
 if(feed.issue.isNotBlank())Caption(feed.issue,Pink)
 }
 if(count==0&&"events:$symbol" !in s.busy)EmptyState("No matching disclosures","No matching items in the loaded 90-day source window. This is not a clean-governance certificate.")
 Action("Original announcements & documents",{a.url(Repository.NSE+"/companies-listing/corporate-filings-announcements")})
}

private fun statementForm(symbol:String,vm:LensViewModel):FormSpec {
 val tags=listOf("RevenueFromOperations" to "Revenue (₹)","ProfitLossForPeriod" to "Net profit (₹)","CashFlowsFromUsedInOperatingActivities" to "Operating cash flow (₹)","PurchaseOfPropertyPlantAndEquipment" to "Capital expenditure cash outflow (₹)","Assets" to "Closing assets (₹)","Equity" to "Closing total equity (₹)","CurrentLiabilities" to "Closing current liabilities (₹)","OpeningAssets" to "Opening assets (₹)","OpeningEquity" to "Opening total equity (₹)","OpeningCurrentLiabilities" to "Opening current liabilities (₹)","Borrowings" to "Total borrowings (₹)","FinanceCosts" to "Finance costs (₹)","ProfitLossBeforeTax" to "Profit before tax (₹)")
 return FormSpec("$symbol · original statement","Optional figures stay unavailable when blank. Enter full rupees, not crores/lakhs. All figures must use the same statement, reporting basis and duration.",listOf(FormField("start","Period start · YYYY-MM-DD"),FormField("end","Period end · YYYY-MM-DD"),FormField("basis","Basis","Consolidated",listOf("Consolidated","Standalone")),FormField("source","Original statement HTTPS URL"))+tags.map{FormField(it.first,it.second,number=true)}){v->val start=LocalDate.parse(v.getValue("start"));val end=LocalDate.parse(v.getValue("end"));val days=java.time.temporal.ChronoUnit.DAYS.between(start,end);require(days in 50..370&&!end.isAfter(LocalDate.now())){"Use a completed reporting period of 51–371 days"};require(safeUrl(v.getValue("source"))){"Original source required"};val facts=tags.mapNotNull{(k,_)->val text=v.getValue(k);if(text.isBlank())null else k to text.toDouble().also{require(it.isFinite())}}.toMap();require(facts.isNotEmpty());val p=FinancialParser.Period(start.toString(),end.toString(),v.getValue("basis"),facts,v.getValue("source"),true);val j=FinancialParser.json(p).put("symbol",symbol);vm.save("statement","statement:$symbol:$start:$end:${p.basis}",j)}
}

private fun adjustmentForm(symbol:String,vm:LensViewModel)=FormSpec("$symbol · split / bonus factor","Factor = shares after / shares before. A 1:1 bonus uses 2; a 10→1 face-value split uses 10. This adjusts charts only; it does not change portfolio positions or tax lots.",listOf(FormField("date","Ex-date · YYYY-MM-DD"),FormField("factor","Share multiplication factor",number=true),FormField("source","Original exchange filing HTTPS URL"))){v->val date=LocalDate.parse(v.getValue("date"));require(!date.isAfter(LocalDate.now()));val factor=v.getValue("factor").toDouble();require(factor.isFinite()&&factor>0&&factor<=10000);require(safeUrl(v.getValue("source")));vm.save("adjustment","adjustment:$symbol:$date",JSONObject().put("symbol",symbol).put("date",date.toString()).put("factor",factor).put("source",v.getValue("source")))}
