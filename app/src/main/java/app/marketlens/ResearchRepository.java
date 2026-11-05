package app.marketlens;
import java.util.*;
import java.time.*;
import java.time.format.*;
import org.json.*;

final class ResearchRepository {
 private final Net net;
 ResearchRepository(Net net){this.net=net;}
 static String current(){return Repository.NSE+"/api/ipo-current-issue";}static String upcoming(){return Repository.NSE+"/api/all-upcoming-issues?category=ipo";}static String past(){return Repository.NSE+"/api/public-past-issues";}
 List<Research.Feed<Research.Ipo>> issues(boolean force){List<Research.Feed<Research.Ipo>> all=new ArrayList<>();String[] urls={current(),upcoming(),past()},kinds={"current","upcoming","past"};for(int n=0;n<urls.length;n++){Research.Feed<Research.Ipo> feed;try{Net.Result result=net.get(urls[n],300000,force);feed=Research.issues(result.text(),kinds[n]);feed.meta(result);for(Research.Ipo i:feed.rows){i.fetched=result.fetched;i.saved=result.saved;}}catch(Exception e){feed=new Research.Feed<>();feed.source=urls[n];feed.issue=Net.message(e);}all.add(feed);}return all;}
 Research.IssueInfo issueInfo(Research.Ipo i,boolean force)throws Exception{String url=Repository.NSE+"/api/ipo-detail?symbol="+Repository.encoded(i.symbol)+"&series="+Repository.encoded(i.series);Net.Result r=net.get(url,300000,force);Research.IssueInfo info=Research.issueInfo(r.text(),i.symbol);info.source=url;info.fetched=r.fetched;info.saved=r.saved;info.issue=r.issue;try{Net.Result bids=net.get(Repository.NSE+"/api/ipo-bid-details?symbol="+Repository.encoded(i.symbol)+"&series="+Repository.encoded(i.series),300000,force);String stamp=Parse.str(new JSONObject(bids.text()),"updateTime");if(!stamp.isEmpty())info.stamp=stamp;if(!bids.issue.isEmpty())info.issue=bids.issue;info.saved|=bids.saved;}catch(Exception e){info.issue="Bid timestamp unavailable · "+Net.message(e);}return info;}
 Research.Feed<Research.Gmp> gmp(String provider,boolean force)throws Exception{String url=provider.equals("InvestorGain")?Research.GAIN:Research.WATCH;Net.Result r=net.get(url,300000,force);Research.Feed<Research.Gmp> feed=Research.gmp(r.text(),provider);String stamp=feed.stamp;feed.meta(r);feed.stamp=stamp;return feed;}
 List<Research.Feed<Research.Event>> events(String symbol,boolean force){List<Research.Feed<Research.Event>> out=new ArrayList<>();DateTimeFormatter f=DateTimeFormatter.ofPattern("dd-MM-uuuu");LocalDate now=LocalDate.now(ZoneId.of("Asia/Kolkata"));String dates="&from_date="+now.minusDays(90).format(f)+"&to_date="+now.format(f);for(boolean actions:new boolean[]{false,true}){String url=Repository.NSE+(actions?"/api/corporates-corporateActions?index=equities&symbol=":"/api/corporate-announcements?index=equities&symbol=")+Repository.encoded(symbol)+dates;Research.Feed<Research.Event> feed=new Research.Feed<>();try{Net.Result r=net.get(url,1800000,force);feed.rows=Research.events(r.text(),actions,symbol);feed.meta(r);}catch(Exception e){feed.source=url;feed.issue=Net.message(e);}out.add(feed);}return out;}
}
