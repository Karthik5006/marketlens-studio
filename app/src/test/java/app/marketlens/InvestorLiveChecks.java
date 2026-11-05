package app.marketlens;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

public final class InvestorLiveChecks {
 public static void main(String[] args)throws Exception{
  Net net=new Net(new File("build/v11-live-cache"));ResearchRepository sources=new ResearchRepository(net);int checks=0;
  List<Research.Feed<Research.Ipo>> feeds=sources.issues(true);for(Research.Feed<Research.Ipo> feed:feeds){if(!feed.issue.isEmpty())throw new AssertionError(feed.source+": "+feed.issue);System.out.println("PASS live official IPO feed: "+feed.source+"; "+feed.rows.size()+" source rows");checks++;}
  List<Research.Ipo> issues=Research.merge(feeds);Research.Ipo issue=issues.stream().filter(i->i.symbol.equals("VPFL")).findFirst().get();Research.IssueInfo info=sources.issueInfo(issue,true);if(info.lot!=1200||info.high!=94||info.stamp.isEmpty())throw new AssertionError("IPO detail or bid date");System.out.println("PASS live issue detail: VPFL; lot 1200; upper band 94; "+info.stamp);checks++;
  for(String provider:new String[]{"InvestorGain","IPOWatch"}){Research.Feed<Research.Gmp> feed=sources.gmp(provider,true);if(feed.rows.size()<6||feed.stamp.isEmpty())throw new AssertionError("GMP source fields");System.out.println("PASS original GMP publisher: "+provider+"; "+feed.rows.size()+" rows; page "+feed.stamp);checks++;}
  Net.Result master=net.get(Repository.ownershipUrl("SEDEMAC"),0,true);JSONObject filing=Parse.latest(master.text(),"SEDEMAC",false);Net.Result document=net.get(Parse.str(filing,"xbrl"),0,true);Data.Ownership owner=Parse.ownership(document.bytes,filing);if(Math.abs(owner.mutual-20.57)>.001||Math.abs(owner.foreign-10.3)>.001)throw new AssertionError("SEDEMAC source audit");JSONObject older=Comparisons.filing(master.text(),"SEDEMAC",false,owner.date,"",false);Net.Result previous=net.get(Parse.str(older,"xbrl"),0,true);Data.Ownership prior=Parse.ownership(previous.bytes,older);if(Math.abs(Research.percentagePoints(owner.foreign,prior.foreign)-6.03)>.001)throw new AssertionError("Ownership comparison");System.out.println("PASS live SEDEMAC holdings audit: "+owner.date+"; MF 20.57%; FPI 10.30%; FPI change +6.03 pp");checks++;
  Net.Result quote=net.get(Repository.quoteUrl("SEDEMAC"),0,true);Data.Quote price=Parse.quote(quote.text(),"SEDEMAC");System.out.println("PASS live selected-stock quote: "+price.stamp);checks++;
  for(Research.Feed<Research.Event> feed:sources.events("SEDEMAC",true)){if(!feed.issue.isEmpty())throw new AssertionError(feed.issue);System.out.println("PASS official disclosure feed: "+feed.rows.size()+" events");checks++;}
  // Capture complete reporting documents used by the new comparison path.
  Net.Result results=net.get(Repository.financialUrl("SEDEMAC"),0,true);JSONObject latest=Parse.latest(results.text(),"SEDEMAC",true);Net.Result xml=net.get(Parse.str(latest,"xbrl"),0,true);Data.Financial financial=Parse.financial(xml.bytes,latest);System.out.println("PASS latest selected-stock financials: "+financial.basis+" "+financial.end);checks++;
  for(boolean year:new boolean[]{false,true}){JSONObject f=Comparisons.filing(results.text(),"SEDEMAC",true,financial.end,financial.basis,year);if(f!=null){Net.Result old=net.get(Parse.str(f,"xbrl"),0,true);Data.Financial comparable=Parse.financial(old.bytes,f);if(Comparisons.fits(financial,comparable))System.out.println("PASS source comparison document: "+comparable.end);}}
  for(boolean high:new boolean[]{true,false})net.get(Repository.NSE+"/api/live-analysis-52Week?index="+(high?"high":"low"),0,true);
  System.out.println("PASS: "+checks+" fresh source checks. No orders or applications.");
 }
}
