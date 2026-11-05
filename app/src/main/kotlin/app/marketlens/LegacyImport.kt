package app.marketlens

import org.json.JSONArray
import org.json.JSONObject

/** Explicit file transfer only; separate Android app sandboxes are never accessed. */
internal object LegacyImport {
 private val objects=listOf("positions","notes","alerts","relativeAlerts","ipos","applicationStatus")
 fun parse(raw:String):InvestorState {
  require(raw.length<=4*1024*1024){"Investor export is too large"}
  val state=InvestorState(raw)
  require(objects.any{state.data.optJSONObject(it)!=null}){"Select a MarketLens 1.1 investor JSON export"}
  state.data.optJSONObject("positions")?.let{all->all.keys().forEach{key->val p=Research.Position.from(all.optJSONObject(key));require(p.valid()&&p.symbol==key){"Invalid acquisition-cost position: $key"}}}
  state.data.optJSONObject("notes")?.let{all->all.keys().forEach{key->require(Data.validSymbol(key)&&all.opt(key) is String&&all.optString(key).length<=8000){"Invalid research note"}}}
  state.data.optJSONObject("alerts")?.let{all->val validation=InvestorState("{}");all.keys().forEach{key->val j=all.getJSONObject(key);validation.alert(key,Analytics.num(j,"below"),Analytics.num(j,"above"))}}
  state.data.optJSONObject("relativeAlerts")?.let{all->all.keys().forEach{key->val j=all.getJSONObject(key);require(Data.validSymbol(key));val lower=Analytics.num(j,"below");val upper=Analytics.num(j,"above");require(lower.isFinite()||upper.isFinite());require(!lower.isFinite()||!upper.isFinite()||lower<upper)}}
  state.tracked().forEach{require(Data.validSymbol(it.symbol)){"Invalid tracked IPO"};listOf(it.start,it.end,it.listing).filter{it.isNotBlank()}.forEach{date->java.time.LocalDate.parse(date)}}
  return state
 }
 fun merge(current:InvestorState,incoming:InvestorState,replace:Boolean){
  objects.forEach{kind->val rows=incoming.data.optJSONObject(kind)?:return@forEach;val destination=current.section(kind);rows.keys().forEach{key->if(replace||!destination.has(key))destination.put(key,rows.get(key))}}
 }
}
