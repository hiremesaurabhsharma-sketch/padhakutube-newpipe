package app;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.downloader.*;
import org.schabi.newpipe.extractor.stream.*;
import org.schabi.newpipe.extractor.channel.*;
import org.schabi.newpipe.extractor.comments.*;
import org.jsoup.Jsoup;

public class Main {
 static final Gson JSON=new Gson();
 static final String ORIGIN=System.getenv().getOrDefault("ALLOWED_ORIGIN","http://localhost:8080");
 static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
 static final HttpClient MEDIA_HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
 static final Map<String,Cache> CACHE=new ConcurrentHashMap<>();
 static final Map<String,Continuation> PAGES=new ConcurrentHashMap<>();
 static final Map<String,Media> MEDIA=new ConcurrentHashMap<>();
 static final Semaphore EXTRACTIONS=new Semaphore(6),MEDIA_SLOTS=new Semaphore(12);
 record Cache(long expires,Object data){}
 record Continuation(long expires,ListExtractor<?> extractor,Page page){}
 record Media(long expires,String url){}
 static class Fetcher extends Downloader {
  public Response execute(org.schabi.newpipe.extractor.downloader.Request req)throws IOException{
   var b=HttpRequest.newBuilder(URI.create(req.url())).timeout(Duration.ofSeconds(20));
   req.headers().forEach((k,vs)->{if(!Set.of("host","connection","content-length").contains(k.toLowerCase(Locale.ROOT)))vs.forEach(v->b.header(k,v));});
   b.method(req.httpMethod(),req.dataToSend()==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(req.dataToSend()));
   try{var r=HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString());return new Response(r.statusCode(),"",r.headers().map(),r.body(),r.uri().toString());}
   catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
  }
 }
 static Map<String,String> query(URI uri){Map<String,String> m=new HashMap<>();String q=uri.getRawQuery();if(q!=null)for(String p:q.split("&")){String[] a=p.split("=",2);m.put(URLDecoder.decode(a[0],StandardCharsets.UTF_8),a.length==2?URLDecoder.decode(a[1],StandardCharsets.UTF_8):"");}return m;}
 static String text(String s){return s==null?"":s;}
 static String plain(Description d){return d==null?"":d.getType()==Description.HTML?Jsoup.parse(d.getContent()).wholeText():text(d.getContent());}
 static String image(List<Image> images){return images==null||images.isEmpty()?"":images.get(images.size()-1).getUrl();}
 static String duration(long n){return n<0?"LIVE":n>=3600?String.format("%d:%02d:%02d",n/3600,n%3600/60,n%60):String.format("%d:%02d",n/60,n%60);}
 static String videoId(String url){try{URI u=URI.create(url);String id=query(u).get("v");if(id==null)id=u.getPath().replaceFirst("^/(shorts|embed)/","");return id.matches("[A-Za-z0-9_-]{11}")?id:"";}catch(Exception e){return "";}}
 static Map<String,Object> video(StreamInfoItem v){Map<String,Object> m=new LinkedHashMap<>();m.put("id",videoId(v.getUrl()));m.put("title",text(v.getName()));m.put("channel",text(v.getUploaderName()));m.put("channelUrl",text(v.getUploaderUrl()));m.put("avatar",image(v.getUploaderAvatars()));m.put("verified",v.isUploaderVerified());m.put("url",v.getUrl());m.put("thumbnail",image(v.getThumbnails()));m.put("duration",duration(v.getDuration()));m.put("views",v.getViewCount());m.put("uploaded",text(v.getTextualUploadDate()));m.put("description",text(v.getShortDescription()));return m;}
 static List<Object> videos(List<? extends InfoItem> items){List<Object> list=new ArrayList<>();if(items!=null)for(var item:items)if(item instanceof StreamInfoItem v&&!videoId(v.getUrl()).isEmpty())list.add(video(v));return list;}
 static Map<String,Object> listPage(ListExtractor<?> ex,ListExtractor.InfoItemsPage<?> page){String token="";if(Page.isValid(page.getNextPage())){prune();token=UUID.randomUUID().toString();PAGES.put(token,new Continuation(System.currentTimeMillis()+600000,ex,page.getNextPage()));}List<Object> items=new ArrayList<>();for(var item:page.getItems()){if(item instanceof CommentsInfoItem c){Map<String,Object> m=new LinkedHashMap<>();m.put("id",text(c.getCommentId()));m.put("author",text(c.getUploaderName()));m.put("avatar",image(c.getUploaderAvatars()));m.put("text",plain(c.getCommentText()));m.put("likes",c.getLikeCount());m.put("published",text(c.getTextualUploadDate()));m.put("pinned",c.isPinned());items.add(m);}else {var mapped=Catalog.item((InfoItem)item);if(mapped!=null)items.add(mapped);}}return new LinkedHashMap<>(Map.of("items",items,"next",token));}
 static Object more(String token)throws Exception{Continuation c=PAGES.get(token);if(c==null||c.expires()<System.currentTimeMillis())throw new IllegalArgumentException("This page expired. Run your search again.");synchronized(c.extractor()){return listPage(c.extractor(),c.extractor().getPage(c.page()));}}
 static String mediaPath(String url){if(!mediaAllowed(url))return "";prune();String token=UUID.randomUUID().toString();MEDIA.put(token,new Media(System.currentTimeMillis()+900000,url));return "/api/media?token="+token;}
 static boolean mediaAllowed(String url){try{URI u=URI.create(url);String h=u.getHost();return "https".equals(u.getScheme())&&h!=null&&(h.endsWith(".googlevideo.com")||h.equals("googlevideo.com"))&&(u.getPort()==-1||u.getPort()==443)&&u.getUserInfo()==null;}catch(Exception e){return false;}}
 static void prune(){long n=System.currentTimeMillis();CACHE.entrySet().removeIf(e->e.getValue().expires()<n);PAGES.entrySet().removeIf(e->e.getValue().expires()<n);MEDIA.entrySet().removeIf(e->e.getValue().expires()<n);if(CACHE.size()>500)CACHE.clear();if(PAGES.size()>1000)PAGES.clear();if(MEDIA.size()>4000)MEDIA.clear();}
 static void send(HttpExchange x,int status,Object value)throws IOException{byte[] b=JSON.toJson(value).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(status,b.length);try(var out=x.getResponseBody()){out.write(b);}}
 static void media(HttpExchange x,String token)throws Exception{
  Media m=MEDIA.get(token);if(m==null||m.expires()<System.currentTimeMillis()){send(x,410,Map.of("error","Stream expired. Reload the video."));return;}
  if(!MEDIA_SLOTS.tryAcquire()){send(x,503,Map.of("error","Player busy. Try again shortly."));return;}
  try{String url=m.url();HttpResponse<InputStream> r=null;for(int hop=0;hop<4;hop++){
   if(!mediaAllowed(url))throw new IOException("Unsafe media redirect");var b=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("User-Agent","Mozilla/5.0").GET();String range=x.getRequestHeaders().getFirst("Range");if(range!=null){if(!range.matches("bytes=\\d*-\\d*")){send(x,416,Map.of("error","Invalid range"));return;}b.header("Range",range);}r=MEDIA_HTTP.send(b.build(),HttpResponse.BodyHandlers.ofInputStream());if(r.statusCode()>=300&&r.statusCode()<400){r.body().close();url=URI.create(url).resolve(r.headers().firstValue("location").orElseThrow()).toString();continue;}break;}
   if(r==null||!(r.statusCode()==200||r.statusCode()==206)){if(r!=null)r.body().close();send(x,502,Map.of("error","Upstream playback unavailable. Try embedded player."));return;}
   for(String key:List.of("content-type","content-length","content-range","accept-ranges"))r.headers().firstValue(key).ifPresent(v->x.getResponseHeaders().set(key,v));x.getResponseHeaders().set("Cache-Control","private, max-age=0");long size=r.headers().firstValueAsLong("content-length").orElse(0);x.sendResponseHeaders(r.statusCode(),size);try(var in=r.body();var out=x.getResponseBody()){in.transferTo(out);}
  }finally{MEDIA_SLOTS.release();}
 }
 static boolean validId(String id){return id.matches("[A-Za-z0-9_-]{11}");}
 static void handle(HttpExchange x)throws IOException{
  try{String origin=x.getRequestHeaders().getFirst("Origin");if(origin!=null&&!origin.equals(ORIGIN)){send(x,403,Map.of("error","Origin not allowed"));return;}
   x.getResponseHeaders().set("Access-Control-Allow-Origin",ORIGIN);x.getResponseHeaders().set("Vary","Origin");x.getResponseHeaders().set("Access-Control-Expose-Headers","Content-Length, Content-Range, Accept-Ranges");x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
   if(x.getRequestMethod().equals("OPTIONS")){x.getResponseHeaders().set("Access-Control-Allow-Methods","GET, OPTIONS");x.getResponseHeaders().set("Access-Control-Allow-Headers","Range");x.sendResponseHeaders(204,-1);x.close();return;}
   if(!x.getRequestMethod().equals("GET")){send(x,405,Map.of("error","GET required"));return;}
   String path=x.getRequestURI().getPath();var p=query(x.getRequestURI());
   if(path.equals("/api/health")){send(x,200,Map.of("engine","NewPipeExtractor","version","v0.26.5","apiVersion",3,"features",List.of("search","pagination","streams","comments","channels","playlists","suggestions","kiosks","captions","chapters","audio","multi-service"),"youtubeDirect",false));return;}
   if(path.equals("/api/media")){media(x,p.getOrDefault("token",""));return;}
   if(!Set.of("/api/services","/api/search","/api/stream","/api/comments","/api/channel","/api/more","/api/playlist","/api/suggest","/api/kiosk","/api/tab").contains(path)){send(x,404,Map.of("error","Not found"));return;}
   String key=x.getRequestURI().toString();Cache cached=CACHE.get(key);if(cached!=null&&cached.expires()>System.currentTimeMillis()){send(x,200,cached.data());return;}
   if(!EXTRACTIONS.tryAcquire()){x.getResponseHeaders().set("Retry-After","5");send(x,429,Map.of("error","Too many requests. Try again in a few seconds."));return;}
   try{Object data=path.equals("/api/more")?more(p.getOrDefault("cursor","")):Catalog.handle(path,p);
    prune();CACHE.put(key,new Cache(System.currentTimeMillis()+60000,data));send(x,200,data);
   }finally{EXTRACTIONS.release();}
  }catch(Catalog.RestrictedException e){send(x,503,Map.of("code","UPSTREAM_RESTRICTED","error","YouTube direct streams and comments are unavailable on this server. Use the official player.","fallback","embed"));}catch(UnsupportedOperationException e){send(x,422,Map.of("error","This source does not support that feature."));}catch(IllegalArgumentException e){send(x,400,Map.of("error",text(e.getMessage())));}catch(Exception e){System.err.println(e.getClass().getSimpleName()+": "+e.getMessage());try{send(x,502,Map.of("error","This source is temporarily unavailable. Open it on the original site or try again later."));}catch(IOException ignored){x.close();}}
 }
 public static void main(String[] args)throws Exception{NewPipe.init(new Fetcher());var server=HttpServer.create(new InetSocketAddress(Integer.parseInt(System.getenv().getOrDefault("PORT","8080"))),64);server.createContext("/api/",Main::handle);server.setExecutor(new ThreadPoolExecutor(8,24,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.CallerRunsPolicy()));server.start();System.out.println("NewPipe API listening on "+server.getAddress().getPort());}
}
