package app.marketlens;
import java.time.*;
import org.json.*;

final class Comparisons {
 static JSONObject filing(String raw,String symbol,boolean financial,String currentEnd,String basis,boolean yearAgo)throws Exception{Object parsed=new JSONTokener(raw).nextValue();JSONArray all=parsed instanceof JSONArray?(JSONArray)parsed:((JSONObject)parsed).getJSONArray("data"),rows=new JSONArray();String target=yearAgo?LocalDate.parse(currentEnd).minusYears(1).toString():"";for(int n=0;n<all.length();n++){JSONObject row=all.getJSONObject(n);String end=Parse.canonical(Parse.str(row,financial?"qe_Date":"date"));if(end.isEmpty()||end.compareTo(currentEnd)>=0||yearAgo&&!end.equals(target)||financial&&!basis.equalsIgnoreCase(Parse.str(row,"consolidated")))continue;rows.put(row);}if(rows.length()==0)return null;return Parse.latest(rows.toString(),symbol,financial);}
 static boolean fits(Data.Financial current,Data.Financial prior){if(current==null||prior==null||!current.basis.equals(prior.basis)||!current.revenueLabel.equals(prior.revenueLabel))return false;try{long a=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(current.start),LocalDate.parse(current.end)),b=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(prior.start),LocalDate.parse(prior.end));return Math.abs(a-b)<=10;}catch(Exception e){return false;}}
}
