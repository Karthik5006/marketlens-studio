package app.marketlens;
import java.io.*;import java.nio.file.*;import java.util.*;import org.json.*;
public final class LiveChecks {
 public static void main(String[] args)throws Exception {
  File directory=new File("build/live-cache");Net net=new Net(directory);JSONArray snapshots=new JSONArray();
  for(boolean high:new boolean[]{true,false}){Net.Result r=net.get(Repository.NSE+"/api/live-analysis-52Week?index="+(high?"high":"low"),0,true);Data.Screen screen=Parse.screen(r.text());System.out.println("PASS NSE "+(high?"highs":"lows")+": "+screen.hits.size()+" rows; source "+screen.stamp);}
  Net.Result quote=net.get(Repository.quoteUrl("RELIANCE"),0,true);Data.Quote q=Parse.quote(quote.text(),"RELIANCE");System.out.println("PASS native HTTP client + current quote schema: "+q.stamp);
  Net.Result list=net.get(Repository.financialUrl("RELIANCE"),0,true);JSONObject filing=Parse.latest(list.text(),"RELIANCE",true);Net.Result xml=net.get(Parse.str(filing,"xbrl"),0,true);Data.Financial f=Parse.financial(xml.bytes,filing);System.out.println("PASS current integrated filing: "+f.basis+" "+f.end);
  list=net.get(Repository.ownershipUrl("RELIANCE"),0,true);filing=Parse.latest(list.text(),"RELIANCE",false);xml=net.get(Parse.str(filing,"xbrl"),0,true);Data.Ownership o=Parse.ownership(xml.bytes,filing);System.out.println("PASS company MF ownership: "+o.date+"; "+o.holders.size()+" named holders");
  Net.Result page=net.get(Repository.PPFAS,0,true);Map<String,String[]> links=Repository.fundLinks(page.text());if(links.size()!=6)throw new AssertionError("Expected six covered PPFAS links, got "+links.size());
  for(Map.Entry<String,String[]> entry:links.entrySet()){String[] source=entry.getValue();Net.Result r=net.get(source[0],0,true);List<Data.Portfolio> parsed=Xlsx.portfolios(r.bytes,"",source[1],source[0]);if(parsed.size()!=1)throw new AssertionError("Unexpected workbook count");Data.Portfolio p=parsed.get(0);p.id="ppfas:"+entry.getKey();p.imported=false;p.fetched=r.fetched;snapshots.put(p.json());System.out.println("PASS official AMC workbook: "+p.name+"; "+p.date+"; "+p.holdings.size()+" rows");}
  Files.write(Paths.get("build/live-portfolios.json"),snapshots.toString().getBytes("UTF-8"));System.out.println("PASS: 11 live source/schema checks, no fabricated fallback data.");
 }
}
