package app.marketlens;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Direct HTTPS requests to explicitly allowlisted exchanges, AMCs and labelled publishers. Cache time never becomes the source date. */
final class Net {
 static final int MAX=16*1024*1024;
 private final File directory;
 Net(File d){directory=d;d.mkdirs();}
 static final class Result {byte[] bytes;String url,issue="";long fetched;boolean saved;String text(){return new String(bytes,StandardCharsets.UTF_8);}}
 static boolean allowed(String raw){try{URI u=new URI(raw);String h=u.getHost();return "https".equalsIgnoreCase(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&Arrays.asList("www.hdfcfund.com","files.hdfcfund.com","www.nseindia.com","archives.nseindia.com","nsearchives.nseindia.com","amc.ppfas.com","www.investorgain.com","investorgain.com","ipowatch.in","www.ipowatch.in").contains(h);}catch(Exception e){return false;}}
 private File file(String url)throws Exception{byte[] hash=MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));StringBuilder name=new StringBuilder();for(byte b:hash)name.append(String.format(Locale.ROOT,"%02x",b&255));return new File(directory,name.toString());}
 Result peek(String url)throws Exception{if(!allowed(url))throw new IOException("Unrecognized source");File f=file(url);if(!f.exists()||f.length()==0||f.length()>MAX)return null;Result r=new Result();r.url=url;r.bytes=Files.readAllBytes(f.toPath());r.fetched=f.lastModified();r.saved=true;return r;}
 Result get(String url,long ttl,boolean force)throws Exception{
  if(!allowed(url))throw new IOException("Unrecognized source");File f=file(url);if(!force&&f.exists()&&System.currentTimeMillis()-f.lastModified()<ttl)return peek(url);
  try{byte[] data=download(url,0);Result r=new Result();r.url=url;r.bytes=data;r.fetched=System.currentTimeMillis();File temp=new File(directory,f.getName()+"."+Thread.currentThread().getId()+".tmp");Files.write(temp.toPath(),data);Files.move(temp.toPath(),f.toPath(),StandardCopyOption.REPLACE_EXISTING);f.setLastModified(r.fetched);return r;}
  catch(Exception e){Result r=peek(url);if(r!=null){r.issue="Refresh unavailable · "+message(e);return r;}throw new IOException(message(e),e);}
 }
 static String message(Exception e){if(e instanceof UnknownHostException)return "Network unavailable. Check your connection and refresh.";if(e instanceof java.net.SocketTimeoutException)return "The source timed out. Try refreshing again.";if(e instanceof javax.net.ssl.SSLException)return "A secure connection to the source could not be established.";String m=e.getMessage();return m==null?"Connection unavailable":m.length()>130?m.substring(0,130):m;}
 private byte[] download(String raw,int redirects)throws Exception{
  if(!allowed(raw)||redirects>3)throw new IOException("Source redirect was not accepted");HttpURLConnection c=(HttpURLConnection)new URL(raw).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestProperty("User-Agent","Mozilla/5.0 (Android) MarketLensStudio/1.0");c.setRequestProperty("Accept","application/json, application/xml, text/csv, */*");c.setRequestProperty("Accept-Encoding","gzip");c.setRequestProperty("Referer",raw.contains("ppfas.com")?"https://amc.ppfas.com/":raw.contains("investorgain.com")?"https://www.investorgain.com/":raw.contains("ipowatch.in")?"https://ipowatch.in/":"https://www.nseindia.com/");
  try{int status=c.getResponseCode();if(status>=300&&status<400){String target=new URL(new URL(raw),c.getHeaderField("Location")).toString();return download(target,redirects+1);}if(status!=200)throw new IOException("Source returned HTTP "+status);try(InputStream input="gzip".equalsIgnoreCase(c.getContentEncoding())?new GZIPInputStream(c.getInputStream()):c.getInputStream()){byte[] data=read(input,MAX);if(data.length==0)throw new IOException("Source returned no data");String head=new String(data,0,Math.min(data.length,160),StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);if(raw.contains("/api/")&&!(head.startsWith("{")||head.startsWith("[")))throw new IOException("Source returned a web page instead of data");if(raw.contains("/xbrl/")&&head.contains("<html"))throw new IOException("Filing is temporarily unavailable");return data;}}finally{c.disconnect();}
 }
 static byte[] read(InputStream in,int limit)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("File exceeds "+(limit/1024/1024)+" MB limit");out.write(b,0,n);}return out.toByteArray();}
}
