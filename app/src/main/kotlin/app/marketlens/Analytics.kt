package app.marketlens

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.*

internal data class Bar(val date:String,val open:Double,val high:Double,val low:Double,val close:Double,val volume:Double)
internal object Analytics {
 fun bars(raw:String,symbol:String,series:String="EQ"):List<Bar> {
  val a=JSONObject(raw).optJSONArray("data") ?: error("The exchange history format has changed")
  val out=mutableListOf<Bar>()
  for(i in 0 until a.length()) { val j=a.getJSONObject(i);if(j.optString("CH_SYMBOL")!=symbol || j.optString("CH_SERIES")!=series)continue
   val d=Parse.canonical(j.optString("mTIMESTAMP")); val c=num(j,"CH_CLOSING_PRICE")
   if(d.isNotEmpty() && c.isFinite() && c>0)out+=Bar(d,num(j,"CH_OPENING_PRICE"),num(j,"CH_TRADE_HIGH_PRICE"),num(j,"CH_TRADE_LOW_PRICE"),c,num(j,"CH_TOT_TRADED_QTY"))
  };return out.distinctBy{it.date}.sortedBy{it.date}
 }
 data class Adjustment(val date:String,val factor:Double)
 fun adjusted(bars:List<Bar>,actions:List<Adjustment>):List<Bar> {
  actions.forEach{LocalDate.parse(it.date);require(it.factor.isFinite()&&it.factor>0)}
  val last=bars.lastOrNull()?.date?:return bars
  return bars.map{b->val factor=actions.filter{it.date>b.date&&it.date<=last}.fold(1.0){x,a->x*a.factor};require(factor.isFinite()&&factor>0);b.copy(open=b.open/factor,high=b.high/factor,low=b.low/factor,close=b.close/factor,volume=b.volume*factor)}
 }
 fun num(j:JSONObject,key:String)=Parse.number(j.opt(key))
 fun sma(values:List<Double>,period:Int):Double=if(values.size>=period && period>0)values.takeLast(period).average() else Double.NaN
 /** Wilder RSI, returns unavailable until a full initial window exists. */
 fun rsi(values:List<Double>,period:Int=14):Double {
  if(values.size<=period || period<1)return Double.NaN
  var gain=0.0;var loss=0.0
  for(i in 1..period){val d=values[i]-values[i-1];gain+=max(d,0.0)/period;loss+=max(-d,0.0)/period}
  for(i in period+1 until values.size){val d=values[i]-values[i-1];gain=(gain*(period-1)+max(d,0.0))/period;loss=(loss*(period-1)+max(-d,0.0))/period}
  return if(loss==0.0)if(gain==0.0)50.0 else 100.0 else 100.0-100.0/(1+gain/loss)
 }
 fun drawdown(values:List<Double>):Double {if(values.size<2)return Double.NaN;var peak=values.first();var worst=0.0;values.forEach{peak=max(peak,it);if(peak>0)worst=min(worst,(it/peak-1)*100)};return worst}
 fun volatility(values:List<Double>):Double {if(values.size<21)return Double.NaN;val r=values.zipWithNext{a,b->ln(b/a)};val mean=r.average();return sqrt(r.sumOf{(it-mean).pow(2)}/(r.size-1))*sqrt(252.0)*100}
 fun correlation(a:List<Bar>,b:List<Bar>):Double {
  fun returns(x:List<Bar>)=x.zipWithNext{p,c->(p.date+"|"+c.date) to ln(c.close/p.close)}.toMap()
  val ar=returns(a);val br=returns(b);val keys=ar.keys.intersect(br.keys);if(keys.size<20)return Double.NaN
  val av=keys.map{ar.getValue(it)};val bv=keys.map{br.getValue(it)};val am=av.average();val bm=bv.average()
  val den=sqrt(av.sumOf{(it-am).pow(2)}*bv.sumOf{(it-bm).pow(2)});return if(den>0)av.zip(bv).sumOf{(x,y)->(x-am)*(y-bm)}/den else Double.NaN
 }
 fun weights(p:Data.Portfolio):Map<String,Double> = p.holdings.filter{Data.isin(it.isin)&&it.weight.isFinite()&&it.weight>=0}.groupBy{it.isin}.mapValues{(_,v)->v.sumOf{it.weight}}
 fun overlap(a:Data.Portfolio,b:Data.Portfolio):Double {val aw=weights(a);val bw=weights(b);return aw.keys.intersect(bw.keys).sumOf{min(aw.getValue(it),bw.getValue(it))}}
 data class Exposure(val isin:String,val name:String,val direct:Double,val funds:Double){val total get()=direct+funds}
 data class LookThrough(val rows:List<Exposure>,val total:Double,val unknown:Double)
 fun lookThrough(direct:List<Exposure>,funds:List<Pair<Data.Portfolio?,Double>>):LookThrough {
  val all=linkedMapOf<String,Exposure>();direct.forEach{all[it.isin]=(all[it.isin]?.let{old->old.copy(direct=old.direct+it.direct)} ?: it)}
  var unknown=0.0
  for((p,amount) in funds){if(p==null){unknown+=amount;continue};val w=weights(p);require(w.values.sum()<=100.5){"Equity weights exceed 100%; inspect the source snapshot"};unknown+=amount*max(0.0,100-w.values.sum())/100
   w.forEach{(isin,weight)->val old=all[isin]?:Exposure(isin,p.holdings.first{it.isin==isin}.stock,0.0,0.0);all[isin]=old.copy(funds=old.funds+amount*weight/100)}
  }
  return LookThrough(all.values.sortedByDescending{it.total},direct.sumOf{it.direct}+funds.sumOf{it.second},unknown)
 }
 data class Trade(val id:String,val account:String,val symbol:String,val date:String,val side:String,val quantity:Double,val price:Double,val fees:Double,val order:Long=0)
 data class Disposal(val account:String,val symbol:String,val acquired:String,val sold:String,val quantity:Double,val cost:Double,val proceeds:Double){val gain get()=proceeds-cost;val days get()=ChronoUnit.DAYS.between(LocalDate.parse(acquired),LocalDate.parse(sold))}
 data class Lot(val account:String,val symbol:String,val date:String,var quantity:Double,val unitCost:Double)
 data class Ledger(val disposals:List<Disposal>,val open:List<Lot>)
 fun fifo(trades:List<Trade>):Ledger {
  val lots=mutableListOf<Lot>();val sells=mutableListOf<Disposal>()
  trades.sortedWith(compareBy<Trade>{it.date}.thenBy{it.order}.thenBy{it.id}).forEach{t->
   require(Data.validSymbol(t.symbol)&&t.account.isNotBlank()&&t.side in listOf("BUY","SELL")&&t.quantity>0&&t.quantity==floor(t.quantity)&&t.quantity.isFinite()&&t.price>=0&&t.price.isFinite()&&t.fees>=0&&t.fees.isFinite()){ "Invalid transaction" };LocalDate.parse(t.date);require((t.quantity*t.price+t.fees).isFinite()){"Transaction value is too large"}
   if(t.side=="BUY")lots+=Lot(t.account,t.symbol,t.date,t.quantity,t.price+t.fees/t.quantity)
   else {val available=lots.filter{it.account==t.account&&it.symbol==t.symbol&&it.quantity>0};require(available.sumOf{it.quantity}+1e-8>=t.quantity){"Sale exceeds recorded shares for ${t.account} / ${t.symbol}. Add missing acquisition lots first."};var left=t.quantity
    for(l in available){val n=min(left,l.quantity);if(n<=1e-8)continue;sells+=Disposal(t.account,t.symbol,l.date,t.date,n,n*l.unitCost,n*(t.price-t.fees/t.quantity));l.quantity-=n;left-=n}
   }
  };return Ledger(sells,lots.filter{it.quantity>1e-8})
 }
 fun trades(rows:List<LocalRecord>)=rows.filter{it.kind=="trade"}.map{val j=JSONObject(it.json);Trade(it.id,j.optString("account"),j.optString("symbol"),j.optString("date"),j.optString("side"),num(j,"quantity"),num(j,"price"),num(j,"fees"),it.updatedAt)}
 data class IpoCapital(val blocked:Double,val allottedCost:Double,val saleProceeds:Double,val realized:Double,val remainingShares:Double)
 fun ipo(j:JSONObject):IpoCapital {
  fun n(k:String)=num(j,k).let{if(it.isNaN())0.0 else it}
  val applied=n("applied");val price=n("price");val allotted=n("allotted");val sold=n("sold");val sale=n("salePrice");val fees=n("fees");val mandate=n("blocked");val released=n("released")
  require(listOf(applied,price,allotted,sold,sale,fees,mandate,released).all{it.isFinite()&&it>=0}){"Amounts must be finite and non-negative"}
  require(applied==floor(applied)&&allotted==floor(allotted)&&sold==floor(sold)&&allotted<=applied&&sold<=allotted&&released<=mandate+1e-8){"Check applied, allotted, sold and released quantities/amounts"}
  require(sold>0||fees==0.0){"Sale fees require sold shares"}
  require(price>0&&applied>0){"Application shares and issue price must be positive"}
  // 'released' includes funds converted from the mandate into allotted shares.
  return IpoCapital(mandate-released,allotted*price,sold*sale-fees,if(sold>0)sold*(sale-price)-fees else 0.0,allotted-sold)
 }
 fun fy(date:String):String {val d=LocalDate.parse(date);val y=if(d.monthValue>=4)d.year else d.year-1;return "$y-${(y+1)%100}"}
}
