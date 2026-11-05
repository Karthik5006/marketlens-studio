package app.marketlens

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File

class AnalyticsTest {
 private fun fund(id:String,vararg holdings:Pair<String,Double>):Data.Portfolio=Data.Portfolio().also{p->p.id=id;p.name=id;p.holdings=holdings.map{(isin,w)->Data.Holding().also{it.isin=isin;it.stock=isin;it.weight=w}}}
 private val a="INE002A01018";private val b="INE009A01021";private val c="INE040A01034"
 @Test fun overlapUsesAssetWeightsAndExactIsin(){val one=fund("A",a to 10.0,b to 5.0);val two=fund("B",a to 6.0,c to 12.0);assertEquals(6.0,Analytics.overlap(one,two),1e-8);assertEquals(15.0,Analytics.overlap(one,one),1e-8)}
 @Test fun lookThroughRetainsUnknownAssetsAndDirectExposure(){val one=fund("A",a to 10.0,b to 20.0);val result=Analytics.lookThrough(listOf(Analytics.Exposure(a,"A",200.0,0.0)),listOf(one to 1000.0,null to 500.0));assertEquals(1700.0,result.total,1e-8);assertEquals(1200.0,result.unknown,1e-8);assertEquals(300.0,result.rows.first().total,1e-8);assertEquals(result.total,result.rows.sumOf{it.total}+result.unknown,1e-8)}
 @Test(expected=IllegalArgumentException::class) fun overfullFundWeightsRejected(){Analytics.lookThrough(emptyList(),listOf(fund("bad",a to 90.0,b to 20.0) to 100.0))}
 @Test fun fifoAllocatesFeesAcrossAcquisitionLots(){val trades=listOf(Analytics.Trade("a","self","TCS","2025-01-01","BUY",10.0,100.0,10.0),Analytics.Trade("b","self","TCS","2025-02-01","BUY",10.0,120.0,20.0),Analytics.Trade("c","self","TCS","2026-01-02","SELL",15.0,150.0,30.0));val out=Analytics.fifo(trades);assertEquals(2,out.disposals.size);assertEquals(600.0,out.disposals.sumOf{it.gain},1e-8);assertEquals(5.0,out.open.single().quantity,1e-8);assertEquals(122.0,out.open.single().unitCost,1e-8)}
 @Test(expected=IllegalArgumentException::class) fun accountsNeverShareLots(){Analytics.fifo(listOf(Analytics.Trade("1","A","TCS","2025-01-01","BUY",10.0,100.0,0.0),Analytics.Trade("2","B","TCS","2026-01-01","SELL",1.0,200.0,0.0)))}
 @Test(expected=IllegalArgumentException::class) fun fractionalEquitiesRejected(){Analytics.fifo(listOf(Analytics.Trade("1","A","TCS","2025-01-01","BUY",.5,100.0,0.0)))}
 @Test fun sameDaySequenceIsStable(){val buy=Analytics.Trade("z","A","TCS","2025-01-01","BUY",10.0,100.0,0.0,1);val sell=Analytics.Trade("a","A","TCS","2025-01-01","SELL",5.0,200.0,0.0,2);assertEquals(500.0,Analytics.fifo(listOf(sell,buy)).disposals.single().gain,1e-8)}
 @Test fun ipoBlockedCapitalAndPartialSale(){val j=JSONObject().put("applied",100).put("price",100).put("blocked",10000).put("released",10000).put("allotted",50).put("sold",20).put("salePrice",150).put("fees",20);val out=Analytics.ipo(j);assertEquals(0.0,out.blocked,0.0);assertEquals(5000.0,out.allottedCost,0.0);assertEquals(980.0,out.realized,0.0);assertEquals(30.0,out.remainingShares,0.0)}
 @Test(expected=IllegalArgumentException::class) fun ipoOverAllotmentRejected(){Analytics.ipo(JSONObject().put("applied",10).put("price",100).put("allotted",11))}
 @Test fun indicatorsAndDrawdown(){assertTrue(Analytics.sma(listOf(1.0),2).isNaN());assertEquals(2.0,Analytics.sma(listOf(1.0,2.0,3.0),3),0.0);assertEquals(100.0,Analytics.rsi((1..30).map{it.toDouble()}),0.0);assertEquals(0.0,Analytics.rsi((30 downTo 1).map{it.toDouble()}),0.0);assertEquals(50.0,Analytics.rsi(List(30){100.0}),0.0);assertEquals(-25.0,Analytics.drawdown(listOf(100.0,120.0,90.0,140.0)),1e-8);assertEquals(0.0,Analytics.volatility(List(30){100.0}),0.0)}
 @Test fun financialYearBoundary(){assertEquals("2025-26",Analytics.fy("2026-03-31"));assertEquals("2026-27",Analytics.fy("2026-04-01"))}
 @Test fun sourceHistoryChecksSymbolAndSeries(){val file=File("../research/v2/history.txt").let{if(it.exists())it else File("research/v2/history.txt")};val bars=Analytics.bars(file.readText(),"TCS");assertTrue(bars.size>=15);assertEquals("2026-11-04",bars.last().date);assertEquals(2436.0,bars.last().close,0.0);assertTrue(Analytics.bars(file.readText(),"OTHER").isEmpty());assertTrue(Analytics.bars(file.readText(),"TCS","BE").isEmpty())}
 @Test fun officialHdfcWorkbookMatchesRequestedIdentity(){val file=File("../research/v2/hdfc-sample.xlsx").let{if(it.exists())it else File("research/v2/hdfc-sample.xlsx")};val funds=Xlsx.portfolios(file.readBytes(),"HDFC Retirement Fund - Equity Plan","2026-09-30","https://www.hdfcfund.com/");assertEquals(1,funds.size);assertEquals("HDFC Retirement Fund - Equity Plan",funds.single().name);val icici=funds.single().holdings.first{it.isin=="INE090A01021"};assertEquals(7.36,icici.weight,1e-8);assertEquals(3_700_000.0,icici.shares,0.0)}
 @Test fun missingFilterDataDoesNotPass(){val rule=JSONObject().put("maxPE",20);assertFalse(Screener.matches(rule,null,null,emptyList()));val q=Data.Quote().also{it.pe=10.0};assertTrue(Screener.matches(rule,q,null,emptyList()));q.pe=30.0;assertFalse(Screener.matches(rule,q,null,emptyList()))}
 @Test fun historyChunksCoverFullYear(){val file=File("../research/v2/history-year-complete.json").let{if(it.exists())it else File("research/v2/history-year-complete.json")};val bars=Analytics.bars(file.readText(),"TCS");assertTrue(bars.size>=240);assertTrue(bars.first().date<"2025-12-01");assertEquals(bars.size,bars.map{it.date}.distinct().size)}
 @Test fun chartFactorsPreserveValueAndIgnoreFutureActions(){val raw=listOf(Bar("2026-01-01",100.0,110.0,90.0,100.0,10.0),Bar("2026-01-02",50.0,55.0,45.0,50.0,20.0));val out=Analytics.adjusted(raw,listOf(Analytics.Adjustment("2026-01-02",2.0),Analytics.Adjustment("2026-02-01",3.0)));assertEquals(50.0,out.first().close,0.0);assertEquals(20.0,out.first().volume,0.0);assertEquals(raw.first().close*raw.first().volume,out.first().close*out.first().volume,0.0);assertEquals(raw.last(),out.last())}
 @Test fun financialContextKeepsSegmentFactsOutAndCalculatesAnnualRatios(){
  val xml="""<xbrl xmlns="http://www.xbrl.org/2003/instance" xmlns:i="urn:investor" xmlns:xbrldi="http://xbrl.org/2006/xbrldi"><unit id="INR"><measure>iso4217:INR</measure></unit>
  <context id="FY"><entity><identifier scheme="urn:id">TCS</identifier></entity><period><startDate>2025-04-01</startDate><endDate>2026-03-31</endDate></period></context>
  <context id="C"><entity><identifier scheme="urn:id">TCS</identifier></entity><period><instant>2026-03-31</instant></period></context>
  <context id="O"><entity><identifier scheme="urn:id">TCS</identifier></entity><period><instant>2025-03-31</instant></period></context>
  <context id="S"><entity><identifier scheme="urn:id">TCS</identifier><segment><xbrldi:explicitMember dimension="i:Segment">i:One</xbrldi:explicitMember></segment></entity><period><startDate>2025-04-01</startDate><endDate>2026-03-31</endDate></period></context>
  <i:RevenueFromOperations contextRef="FY" unitRef="INR">100</i:RevenueFromOperations><i:RevenueFromOperations contextRef="S" unitRef="INR">9999</i:RevenueFromOperations>
  <i:ProfitLossForPeriod contextRef="FY" unitRef="INR">20</i:ProfitLossForPeriod><i:CashFlowsFromUsedInOperatingActivities contextRef="FY" unitRef="INR">30</i:CashFlowsFromUsedInOperatingActivities><i:PurchaseOfPropertyPlantAndEquipment contextRef="FY" unitRef="INR">10</i:PurchaseOfPropertyPlantAndEquipment><i:FinanceCosts contextRef="FY" unitRef="INR">5</i:FinanceCosts><i:ProfitLossBeforeTax contextRef="FY" unitRef="INR">25</i:ProfitLossBeforeTax>
  <i:Assets contextRef="C" unitRef="INR">200</i:Assets><i:CurrentLiabilities contextRef="C" unitRef="INR">40</i:CurrentLiabilities><i:Equity contextRef="C" unitRef="INR">100</i:Equity>
  <i:Assets contextRef="O" unitRef="INR">160</i:Assets><i:CurrentLiabilities contextRef="O" unitRef="INR">20</i:CurrentLiabilities><i:Equity contextRef="O" unitRef="INR">80</i:Equity></xbrl>"""
  val p=FinancialParser.parse(xml.toByteArray(),"Consolidated","https://www.nseindia.com/").single();assertEquals(100.0,p.revenue,0.0);assertEquals(20.0,p.fcf,0.0);assertEquals(20.0/90*100,p.roeDerived,1e-8);assertEquals(20.0,p.roceDerived,1e-8)
 }

}
