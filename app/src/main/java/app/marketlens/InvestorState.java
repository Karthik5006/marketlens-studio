package app.marketlens;
import java.util.*;
import org.json.*;

/** Local, exportable research state; independent of prices and provider caches. */
final class InvestorState {
 final JSONObject data;
 InvestorState(String raw)throws JSONException{data=new JSONObject(raw);}
 JSONObject section(String key)throws JSONException{JSONObject section=data.optJSONObject(key);if(section==null){section=new JSONObject();data.put(key,section);}return section;}
 void position(Research.Position p)throws Exception{if(!p.valid())throw new IllegalArgumentException("Enter whole shares, a non-negative average cost and fees.");section("positions").put(p.symbol,p.json());}
 List<Research.Position> positions(){List<Research.Position> out=new ArrayList<>();JSONObject all=data.optJSONObject("positions");if(all!=null)for(Iterator<String> it=all.keys();it.hasNext();){Research.Position p=Research.Position.from(all.optJSONObject(it.next()));if(p.valid())out.add(p);}out.sort((a,b)->a.symbol.compareTo(b.symbol));return out;}
 void removePosition(String symbol)throws JSONException{section("positions").remove(symbol);}
 String note(String symbol){JSONObject all=data.optJSONObject("notes");return all==null?"":all.optString(symbol,"");}
 void note(String symbol,String note)throws Exception{if(!Data.validSymbol(symbol)||note.length()>8000)throw new IllegalArgumentException("A research note can contain up to 8,000 characters.");section("notes").put(symbol,note);}
 void alert(String symbol,double below,double above)throws Exception{if(!Data.validSymbol(symbol)||(!Double.isFinite(below)&&!Double.isFinite(above))||Double.isFinite(below)&&below<=0||Double.isFinite(above)&&above<=0||Double.isFinite(below)&&Double.isFinite(above)&&below>=above)throw new IllegalArgumentException("Use a positive lower/upper price, with lower below upper.");JSONObject j=new JSONObject();Research.put(j,"below",below);Research.put(j,"above",above);section("alerts").put(symbol,j);}
 JSONObject alert(String symbol){JSONObject all=data.optJSONObject("alerts");return all==null?null:all.optJSONObject(symbol);}
 void removeAlert(String symbol)throws Exception{section("alerts").remove(symbol);}
 List<String> check(String symbol,double price,String sourceStamp,boolean fresh)throws Exception{List<String> messages=new ArrayList<>();JSONObject alert=alert(symbol);if(alert==null||!fresh||!Double.isFinite(price)||price<=0||sourceStamp.isEmpty())return messages;for(String side:new String[]{"above","below"}){double target=Parse.number(alert.opt(side));if(!Double.isFinite(target))continue;boolean condition=side.equals("above")?price>=target:price<=target;boolean before=alert.optBoolean("met_"+side,false);if(condition&&!before)messages.add(symbol+" · "+side+" "+target+" · quote "+price+" as of "+sourceStamp);alert.put("met_"+side,condition);}if(!messages.isEmpty()){JSONArray old=data.optJSONArray("alertLog"),next=new JSONArray();for(String message:messages)next.put(message);if(old!=null)for(int n=0;n<Math.min(49,old.length());n++)next.put(old.get(n));data.put("alertLog",next);}return messages;}
 List<String> alertLog(){List<String> out=new ArrayList<>();JSONArray list=data.optJSONArray("alertLog");if(list!=null)for(int n=0;n<list.length();n++)out.add(list.optString(n));return out;}
 void track(Research.Ipo issue,boolean tracked)throws Exception{if(tracked)section("ipos").put(issue.id(),issue.json());else section("ipos").remove(issue.id());}
 boolean tracked(String id){JSONObject issues=data.optJSONObject("ipos");return issues!=null&&issues.has(id);}
 List<Research.Ipo> tracked(){List<Research.Ipo> out=new ArrayList<>();JSONObject all=data.optJSONObject("ipos");if(all!=null)for(Iterator<String> it=all.keys();it.hasNext();){JSONObject j=all.optJSONObject(it.next());if(j!=null)out.add(Research.Ipo.from(j));}return out;}
 void observe(Research.Gmp quote,String pageStamp,long fetched)throws Exception{JSONObject histories=section("gmpHistory");JSONArray old=histories.optJSONArray(quote.id());JSONObject last=old!=null&&old.length()>0?old.optJSONObject(0):null;String stamp=quote.stamp.isEmpty()?pageStamp:quote.stamp;if(last!=null&&Double.compare(Parse.number(last.opt("premium")),quote.premium)==0&&last.optString("asOf").equals(stamp))return;JSONObject row=quote.json();row.put("asOf",stamp);row.put("retrieved",fetched);JSONArray next=new JSONArray();next.put(row);if(old!=null)for(int n=0;n<Math.min(29,old.length());n++)next.put(old.get(n));histories.put(quote.id(),next);}
 JSONArray history(Research.Gmp quote){JSONObject histories=data.optJSONObject("gmpHistory");JSONArray list=histories==null?null:histories.optJSONArray(quote.id());return list==null?new JSONArray():list;}
 @Override public String toString(){return data.toString();}
}
