package app.marketlens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import org.json.JSONObject
import java.time.LocalDate

@Composable internal fun FundsScreen(s:LensState,vm:LensViewModel,a:UiActions){
 var section by rememberSaveable{mutableStateOf("Explore")};Label("The companies inside your funds",Violet);Text("See beneath\nthe portfolio.",fontSize=35.sp,lineHeight=39.sp,color=Ink,fontWeight=FontWeight.Bold)
 ChoiceRow(listOf("Explore","Overlap","Changes","Fund facts"),section){section=it}
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Action(if("funds" in s.busy)"Syncing…"else "Sync official AMCs",{vm.syncFunds()},primary=true,enabled="funds" !in s.busy,icon=Icons.Rounded.Sync);Action("Import",{a.importFile("fund")},icon=Icons.Rounded.UploadFile)}
 ErrorLine(s,"funds"){vm.syncFunds()}
 Caption("Auto-sync: PPFAS and supported HDFC monthly equity portfolios. Other AMCs: import their XLSX/CSV disclosures. Reporting dates differ; derivatives and unrecognized rows are excluded.")
 val funds=s.funds.sortedBy{it.name}
 if(funds.isEmpty()){EmptyState("Start with the disclosures","Sync the official AMC pages or import a monthly portfolio. Real holdings will appear here.",Icons.Rounded.Layers);Action("AMFI disclosure directory",{a.url(Repository.AMFI)});return}
 when(section){
 "Explore"->{SectionTitle("Covered schemes","${funds.size} snapshots saved")
  funds.forEach{p->var expanded by remember(p.id){mutableStateOf(false)};LensCard(accent=Violet){Label(if(p.imported)"Imported disclosure"else "Official AMC disclosure",Violet);Text(p.name,color=Ink,fontSize=18.sp,fontWeight=FontWeight.SemiBold);MetricPair("Equity rows",p.holdings.size.toString(),"Equity weight recognized",percent(p.holdings.sumOf{it.weight}));Caption("Snapshot ${p.date}");Action("Add my fund value",{a.form(fundValueForm(p,s,vm))},icon=Icons.Rounded.Add);Action(if(expanded)"Hide holdings"else "View top holdings",{expanded=!expanded})
   if(expanded)p.holdings.sortedByDescending{it.weight}.take(15).forEach{h->AllocationBar(h.stock.ifBlank{h.isin},h.weight,100.0,Violet,number(h.shares,0)+" shares")}
   SourceNote(p.source,p.date,p.fetched,open=a.url);TextButton({vm.removeFund(p.id)}){Text("Remove saved snapshot",color=Muted,fontSize=11.sp)}
  }}
 }
 "Overlap"->{var first by rememberSaveable{mutableStateOf(funds.first().id)};var second by rememberSaveable{mutableStateOf(funds.getOrNull(1)?.id?:funds.first().id)}
  Label("First scheme");FundPicker(funds,first){first=it};Label("Second scheme");FundPicker(funds,second){second=it}
  val p=funds.firstOrNull{it.id==first};val q=funds.firstOrNull{it.id==second}
  if(p!=null&&q!=null){val aw=Analytics.weights(p);val bw=Analytics.weights(q);val common=aw.keys.intersect(bw.keys).sortedByDescending{minOf(aw.getValue(it),bw.getValue(it))};LensCard(accent=Violet){Label("Weighted equity overlap",Violet);Text(percent(Analytics.overlap(p,q)),color=Violet,fontSize=46.sp,fontWeight=FontWeight.Light);Caption("Sum of the smaller disclosed asset weight for each common ISIN. ${common.size} common stocks. Cash, debt and omitted holdings are excluded.");Caption("${p.date} versus ${q.date}. Different reporting dates can affect the comparison.")}
   common.take(60).forEach{isin->val h=p.holdings.first{it.isin==isin};LensCard{Text(h.stock.ifBlank{isin},color=Ink,fontWeight=FontWeight.Medium);MetricPair("First fund weight",percent(aw[isin]?:Double.NaN),"Second fund weight",percent(bw[isin]?:Double.NaN));val st=vm.repo.legacy.stocks.values.firstOrNull{it.isin==isin};if(st!=null)Action("Open stock",{a.stock(st.symbol)})}}
  }
 }
 "Changes"->{var selected by rememberSaveable{mutableStateOf(funds.first().id)};FundPicker(funds,selected){selected=it};val current=funds.firstOrNull{it.id==selected}
  val history=s.snapshots.filter{it.key.startsWith("fund:$selected:")}.mapNotNull{runCatching{Data.Portfolio.from(JSONObject(it.payload))}.getOrNull()}.sortedByDescending{it.date};val prior=history.firstOrNull{it.date<(current?.date?:"")}
  if(current==null||prior==null)EmptyState("History starts with saved disclosures","A second reporting period is needed. Future syncs preserve earlier snapshots; you can also inspect original AMC archives.")
  else{SectionTitle("Position changes","${prior.date} → ${current.date}");val old=prior.holdings.associateBy{it.isin};val now=current.holdings.associateBy{it.isin};(old.keys+now.keys).sortedByDescending{k->kotlin.math.abs((now[k]?.weight?:0.0)-(old[k]?.weight?:0.0))}.take(70).forEach{isin->val before=old[isin];val after=now[isin];LensCard{Label(if(before==null)"New in recognized rows"else if(after==null)"Absent from current recognized rows"else "Continuing position",Violet);Text((after?:before)!!.stock,color=Ink,fontWeight=FontWeight.Medium);MetricPair("Asset-weight change",number((after?.weight?:0.0)-(before?.weight?:0.0))+" pp","Share-count change",if(before!=null&&after!=null)number(after.shares-before.shares,0)else "—");Caption("Weight changes can reflect prices as well as transactions. Absence is limited to recognized disclosure rows.")}}}
 }
 else->{var selected by rememberSaveable{mutableStateOf(funds.first().id)};FundPicker(funds,selected){selected=it};val p=funds.firstOrNull{it.id==selected};if(p!=null){val j=s.json("facts:${p.id}");LensCard{Text(p.name,color=Ink,fontSize=20.sp,fontWeight=FontWeight.SemiBold);Caption("Facts entered from an original factsheet. They are not automatically verified.");MetricPair("Direct expense ratio",percent(Analytics.num(j,"direct")),"Regular expense ratio",percent(Analytics.num(j,"regular")));MetricPair("Fund return · specified period",percent(Analytics.num(j,"return")),"Benchmark · same period",percent(Analytics.num(j,"benchmarkReturn")));MetricPair("Tracking error · as reported",percent(Analytics.num(j,"trackingError")),"Period",j.optString("period").ifBlank{"—"});if(j.optString("date").isNotBlank())SourceNote(j.optString("source"),j.optString("date"),open=a.url);Action("Record factsheet metrics",{a.form(FormSpec("Fund factsheet","Enter values exactly as reported, in percent. Compare returns over the same period and methodology.",listOf(FormField("direct","Direct expense ratio (%)",j.optString("direct"),number=true),FormField("regular","Regular expense ratio (%)",j.optString("regular"),number=true),FormField("return","Fund return (%)",j.optString("return"),number=true),FormField("benchmarkReturn","Benchmark return (%)",j.optString("benchmarkReturn"),number=true),FormField("trackingError","Tracking error (%)",j.optString("trackingError"),number=true),FormField("period","Return period and method",j.optString("period")),FormField("date","Factsheet date · YYYY-MM-DD",j.optString("date")),FormField("source","Original HTTPS factsheet",j.optString("source")))){v->val out=JSONObject();v.forEach{(k,x)->if(k in listOf("direct","regular","return","benchmarkReturn","trackingError")){if(x.isNotBlank()){val n=x.toDouble();require(n.isFinite());if(k in listOf("direct","regular","trackingError"))require(n>=0);out.put(k,n)}}else out.put(k,x)};LocalDate.parse(v.getValue("date"));require(safeUrl(v.getValue("source"))){"An original HTTPS source is required"};vm.save("fundFacts","facts:${p.id}",out)})})}}}
 }
 Action("Official HDFC monthly portfolios",{a.url("https://www.hdfcfund.com/statutory-disclosure/portfolio/monthly-portfolio")});Action("Official PPFAS portfolios",{a.url(Repository.PPFAS)})
}
@Composable private fun FundPicker(funds:List<Data.Portfolio>,selected:String,onSelect:(String)->Unit){var expanded by remember{mutableStateOf(false)};Column{Action(funds.firstOrNull{it.id==selected}?.name?:"Select scheme",{expanded=!expanded},modifier=Modifier.fillMaxWidth(),icon=Icons.Rounded.ExpandMore);if(expanded)Column(verticalArrangement=Arrangement.spacedBy(3.dp)){funds.forEach{p->TextButton({onSelect(p.id);expanded=false}){Text(p.name,color=if(p.id==selected)Lime else Ink,fontSize=12.sp)}}}}}
private fun fundValueForm(p:Data.Portfolio,s:LensState,vm:LensViewModel):FormSpec{val old=s.json("fundValue:${p.id}");return FormSpec("My fund valuation",p.name+" · local manual valuation, not an AMC account import.",listOf(FormField("amount","Current fund market value (₹)",old.optString("amount"),number=true),FormField("date","Valuation date · YYYY-MM-DD",old.optString("date",LocalDate.now().toString())))){v->val n=v.getValue("amount").toDouble();require(n.isFinite()&&n>=0){"Enter a non-negative market value"};val d=LocalDate.parse(v.getValue("date"));require(!d.isAfter(LocalDate.now())){"Future valuation date"};vm.save("fundValue","fundValue:${p.id}",JSONObject().put("fundId",p.id).put("name",p.name).put("amount",n).put("date",d.toString()))}}
