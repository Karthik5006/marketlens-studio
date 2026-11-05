package app.marketlens

import org.json.JSONObject
import org.w3c.dom.Element
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Extract only monetary facts from whole-company, matching XBRL contexts. No mixing of durations. */
internal object FinancialParser {
 data class Period(val start:String,val end:String,val basis:String,val facts:Map<String,Double>,val source:String,val imported:Boolean=false){
  val days get()=runCatching{ChronoUnit.DAYS.between(LocalDate.parse(start),LocalDate.parse(end))}.getOrDefault(0)
  fun value(vararg names:String)=names.firstNotNullOfOrNull{facts[it]?.takeIf(Double::isFinite)}?:Double.NaN
  val revenue get()=value("RevenueFromOperations","RevenueFromOperationsNet","Income","TotalIncome")
  val profit get()=value("ProfitLossForPeriod","ProfitLossForThePeriod","ProfitLoss")
  val ocf get()=value("CashFlowsFromUsedInOperatingActivities","NetCashFlowsFromUsedInOperatingActivities","NetCashFromOperatingActivities")
  val capex get()=value("PurchaseOfPropertyPlantAndEquipment","PaymentsToAcquirePropertyPlantAndEquipment","PurchaseOfTangibleAssets")
  val fcf get()=if(ocf.isFinite()&&capex.isFinite())ocf-kotlin.math.abs(capex) else Double.NaN
  val equity get()=value("Equity","TotalEquity")
  val assets get()=value("Assets","TotalAssets")
  val currentLiabilities get()=value("CurrentLiabilities")
  val openingEquity get()=value("OpeningEquity")
  val roeDerived get()=if(days>=330&&equity>0&&openingEquity>0&&profit.isFinite())profit/((equity+openingEquity)/2)*100 else Double.NaN
  val roceDerived get():Double {val openingAssets=value("OpeningAssets");val openingCurrent=value("OpeningCurrentLiabilities");val closingCapital=assets-currentLiabilities;val openingCapital=openingAssets-openingCurrent;return if(days>=330&&closingCapital>0&&openingCapital>0&&pbt.isFinite()&&interest.isFinite())(pbt+interest)/((closingCapital+openingCapital)/2)*100 else Double.NaN}
  val debt get()=value("Borrowings","TotalBorrowings")
  val roce get()=value("ReturnOnCapitalEmployed")
  val roe get()=value("ReturnOnEquityRatio","ReturnOnEquity")
  val interest get()=value("FinanceCosts","FinanceCost")
  val pbt get()=value("ProfitLossBeforeTax","ProfitBeforeTax")
  val interestCoverage get()=if(interest>0&&pbt.isFinite())(pbt+interest)/interest else Double.NaN
 }
 fun parse(raw:ByteArray,basis:String,source:String):List<Period>{
  val doc=Parse.xml(raw);val contexts=Parse.contexts(doc);val values=mutableMapOf<String,MutableMap<String,Double>>()
  val all=doc.getElementsByTagName("*")
  for(i in 0 until all.length){val e=all.item(i) as Element;val ref=e.getAttribute("contextRef");if(ref.isEmpty())continue
   val ctx=contexts[ref]?:continue
   if(Parse.elements(ctx,"explicitMember").isNotEmpty()||Parse.elements(ctx,"typedMember").isNotEmpty())continue
   val unit=e.getAttribute("unitRef");val name=Parse.name(e)
   // Monetary values use INR. Filed return ratios remain excluded unless units are explicitly understood.
   if(unit.isEmpty()||!Parse.financialUnit(doc,unit,name))continue
   val n=Parse.number(e.textContent);if(n.isFinite())values.getOrPut(ref){linkedMapOf()}[name]=n
  }
  val instant=mutableMapOf<String,Map<String,Double>>()
  values.forEach{(ref,v)->val d=Parse.first(contexts.getValue(ref),"instant");if(d.isNotEmpty())instant[d]=v}
  return values.mapNotNull { (ref, facts) ->
   val context = contexts.getValue(ref)
   val start = Parse.first(context, "startDate")
   val end = Parse.first(context, "endDate")
   if (start.isEmpty() || end.isEmpty()) null else {
    val opening = instant[LocalDate.parse(start).minusDays(1).toString()].orEmpty()
    val balances = mutableMapOf<String, Double>()
    opening["Equity"]?.let { balances["OpeningEquity"] = it }
    opening["Assets"]?.let { balances["OpeningAssets"] = it }
    opening["CurrentLiabilities"]?.let { balances["OpeningCurrentLiabilities"] = it }
    Period(start, end, basis, instant[end].orEmpty() + facts + balances, source)
   }
  }.filter { it.days in 50..370 }.distinctBy { it.start + it.end }.sortedByDescending { it.end + it.start }

 }
 fun json(p:Period)=JSONObject().put("start",p.start).put("end",p.end).put("basis",p.basis).put("source",p.source).put("facts",JSONObject(p.facts)).put("imported",p.imported)
 fun from(j:JSONObject):Period {val f=j.getJSONObject("facts");return Period(j.getString("start"),j.getString("end"),j.getString("basis"),f.keys().asSequence().associateWith{Parse.number(f.opt(it))},j.getString("source"),j.optBoolean("imported"))}
}
