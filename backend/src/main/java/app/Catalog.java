package app;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.channel.*;
import org.schabi.newpipe.extractor.playlist.*;
import org.schabi.newpipe.extractor.stream.*;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.peertube.*;

final class Catalog {
 static final Map<String,StreamingService> SERVICES=new LinkedHashMap<>();
 static final Map<String,Tab> TABS=new ConcurrentHashMap<>();
 static final String PEER_ORIGIN=System.getenv().getOrDefault("PEERTUBE_ORIGIN","https://framatube.org");
 record Tab(long expires,StreamingService service,ListLinkHandler handler){}
 static {
  URI p=URI.create(PEER_ORIGIN);
  if(!"https".equals(p.getScheme())||p.getHost()==null||p.getUserInfo()!=null||p.getPort()!=-1||!Set.of("","/").contains(p.getPath()))throw new IllegalArgumentException("PEERTUBE_ORIGIN must be an HTTPS origin");
  SERVICES.put("youtube",ServiceList.YouTube);SERVICES.put("soundcloud",ServiceList.SoundCloud);SERVICES.put("bandcamp",ServiceList.Bandcamp);
  SERVICES.put("peertube",new PeertubeService(ServiceList.PeerTube.getServiceId(),new PeertubeInstance(PEER_ORIGIN)));SERVICES.put("mediaccc",ServiceList.MediaCCC);
 }
 static String name(int id){return SERVICES.entrySet().stream().filter(e->e.getValue().getServiceId()==id).map(Map.Entry::getKey).findFirst().orElse("youtube");}
 static StreamingService service(Map<String,String> p){var s=SERVICES.get(p.getOrDefault("service","youtube"));if(s==null)throw new IllegalArgumentException("Unknown content source");return s;}
 static boolean host(String h,String d){return h.equals(d)||h.endsWith("."+d);}
 static String checkedUrl(StreamingService s,String url){
  URI u=URI.create(url);String h=Main.text(u.getHost()).toLowerCase(Locale.ROOT);
  boolean ok=switch(name(s.getServiceId())){
   case "youtube"->Set.of("youtube.com","www.youtube.com","m.youtube.com","youtu.be","www.youtube-nocookie.com").contains(h);
   case "soundcloud"->host(h,"soundcloud.com");case "bandcamp"->host(h,"bandcamp.com");
   case "mediaccc"->h.equals("media.ccc.de");case "peertube"->h.equals(URI.create(PEER_ORIGIN).getHost());default->false;};
  if(!ok||!"https".equals(u.getScheme())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))throw new IllegalArgumentException("URL does not belong to the selected source");return u.toString();
 }
 static String streamUrl(StreamingService s,Map<String,String> p){String u=p.getOrDefault("url","");if(u.isEmpty()&&s.getServiceId()==ServiceList.YouTube.getServiceId()){String id=p.getOrDefault("id","");if(!Main.validId(id))throw new IllegalArgumentException("Invalid video ID");u="https://www.youtube.com/watch?v="+id;}return checkedUrl(s,u);}
 static Map<String,Object> item(InfoItem i){
  String source=name(i.getServiceId());Map<String,Object> m=new LinkedHashMap<>();m.put("service",source);m.put("url",i.getUrl());m.put("title",i.getName());m.put("thumbnail",Main.image(i.getThumbnails()));
  if(i instanceof StreamInfoItem v){m.putAll(Main.video(v));m.put("id",source.equals("youtube")?Main.videoId(v.getUrl()):v.getUrl());m.put("kind","video");m.put("durationSeconds",v.getDuration());}
  else if(i instanceof ChannelInfoItem c){m.put("id",c.getUrl());m.put("kind","channel");m.put("channel",c.getName());m.put("channelUrl",c.getUrl());m.put("subscribers",c.getSubscriberCount());m.put("verified",c.isVerified());m.put("description",Main.text(c.getDescription()));}
  else if(i instanceof PlaylistInfoItem p){m.put("id",p.getUrl());m.put("kind","remotePlaylist");m.put("channel",p.getUploaderName());m.put("channelUrl",p.getUploaderUrl());m.put("streamCount",p.getStreamCount());m.put("description",Main.plain(p.getDescription()));}
  else return null;return m;
 }
 static Object services(){List<Object> out=new ArrayList<>();for(var e:SERVICES.entrySet()){var s=e.getValue();Map<String,Object> m=new LinkedHashMap<>();m.put("id",e.getKey());m.put("name",s.getServiceInfo().getName());m.put("baseUrl",s.getBaseUrl());m.put("filters",s.getSearchQHFactory().getAvailableContentFilter());m.put("sorts",s.getSearchQHFactory().getAvailableSortFilter());try{m.put("kiosks",s.getKioskList().getAvailableKiosks());}catch(Exception ex){m.put("kiosks",List.of());}m.put("directPlayback",!e.getKey().equals("youtube"));m.put("comments",!e.getKey().equals("youtube")&&s.getCommentsLHFactory()!=null);out.add(m);}return Map.of("services",out,"apiVersion",3);}
 static Object search(StreamingService s,Map<String,String> p)throws Exception{
  String q=p.getOrDefault("q","").trim(),filter=p.getOrDefault("filter",""),sort=p.getOrDefault("sort","");if(q.isEmpty()||q.length()>200)throw new IllegalArgumentException("Search must contain 1–200 characters");
  if(!filter.isEmpty()&&!Arrays.asList(s.getSearchQHFactory().getAvailableContentFilter()).contains(filter))throw new IllegalArgumentException("Unsupported search filter");
  if(!sort.isEmpty()&&!Arrays.asList(s.getSearchQHFactory().getAvailableSortFilter()).contains(sort))throw new IllegalArgumentException("Unsupported sort order");
  var ex=s.getSearchExtractor(q,filter.isEmpty()?List.of():List.of(filter),sort);ex.fetchPage();return Main.listPage(ex,ex.getInitialPage());
 }
 static Object channel(StreamingService s,String url)throws Exception{
  var info=ChannelInfo.getInfo(s,checkedUrl(s,url));Map<String,Object> m=new LinkedHashMap<>();m.put("name",info.getName());m.put("url",info.getUrl());m.put("service",name(s.getServiceId()));m.put("avatar",Main.image(info.getAvatars()));m.put("banner",Main.image(info.getBanners()));m.put("description",Main.text(info.getDescription()));m.put("subscribers",info.getSubscriberCount());m.put("verified",info.isVerified());m.put("feedUrl",Main.text(info.getFeedUrl()));
  long now=System.currentTimeMillis();TABS.entrySet().removeIf(e->e.getValue().expires()<now);if(TABS.size()>1000)TABS.clear();List<Object> tabs=new ArrayList<>();
  for(var h:info.getTabs()){String t=UUID.randomUUID().toString();TABS.put(t,new Tab(now+600000,s,h));tabs.add(Map.of("token",t,"name",String.join(" ",h.getContentFilters())));}m.put("tabs",tabs);
  if(!info.getTabs().isEmpty()){var ex=s.getChannelTabExtractor(info.getTabs().get(0));ex.fetchPage();m.putAll(Main.listPage(ex,ex.getInitialPage()));}else{m.put("items",List.of());m.put("next","");}return m;
 }
 static Object playlist(StreamingService s,String url)throws Exception{
  var ex=s.getPlaylistExtractor(checkedUrl(s,url));ex.fetchPage();var info=PlaylistInfo.getInfo(ex);Map<String,Object> m=Main.listPage(ex,ex.getInitialPage());m.put("title",info.getName());m.put("thumbnail",Main.image(info.getThumbnails()));m.put("description",Main.plain(info.getDescription()));m.put("channel",info.getUploaderName());m.put("url",info.getUrl());m.put("streamCount",info.getStreamCount());return m;
 }
 static Object stream(StreamingService s,String url)throws Exception{
  var i=StreamInfo.getInfo(s,checkedUrl(s,url));String source=name(s.getServiceId());Map<String,Object> m=new LinkedHashMap<>();m.put("service",source);m.put("url",i.getUrl());m.put("id",source.equals("youtube")?Main.videoId(i.getUrl()):i.getUrl());m.put("title",i.getName());m.put("channel",Main.text(i.getUploaderName()));m.put("channelUrl",Main.text(i.getUploaderUrl()));m.put("avatar",Main.image(i.getUploaderAvatars()));m.put("thumbnail",Main.image(i.getThumbnails()));m.put("description",Main.plain(i.getDescription()));m.put("subscribers",i.getUploaderSubscriberCount());m.put("views",i.getViewCount());m.put("uploaded",Main.text(i.getTextualUploadDate()));m.put("durationSeconds",i.getDuration());m.put("licence",Main.text(i.getLicence()));
  List<Object> streams=new ArrayList<>(),audio=new ArrayList<>(),captions=new ArrayList<>(),chapters=new ArrayList<>(),related=new ArrayList<>();
  for(var v:i.getVideoStreams())if(v.isUrl()&&v.getFormat()!=null)streams.add(Map.of("url",v.getContent(),"quality",v.getResolution(),"mimeType",v.getFormat().getMimeType()));
  for(var a:i.getAudioStreams())if(a.isUrl()&&a.getFormat()!=null)audio.add(Map.of("url",a.getContent(),"quality",Math.max(0,a.getAverageBitrate())+" kbps","mimeType",a.getFormat().getMimeType()));
  for(var c:i.getSubtitles())if(c.isUrl())captions.add(Map.of("url",c.getContent(),"language",c.getLanguageTag(),"name",c.getDisplayLanguageName(),"format",c.getExtension(),"auto",c.isAutoGenerated()));
  for(var c:i.getStreamSegments())chapters.add(Map.of("title",c.getTitle(),"start",c.getStartTimeSeconds()));
  for(var v:i.getRelatedItems()){var a=item(v);if(a!=null)related.add(a);}m.put("streams",streams);m.put("audioStreams",audio);m.put("captions",captions);m.put("chapters",chapters);m.put("related",related);m.put("hlsUrl",Main.text(i.getHlsUrl()));m.put("dashUrl",Main.text(i.getDashMpdUrl()));return m;
 }
 static Object handle(String path,Map<String,String> p)throws Exception{
  var s=service(p);switch(path){
   case "/api/services":return services();case "/api/search":return search(s,p);
   case "/api/suggest":{String q=p.getOrDefault("q","");if(q.length()>200)throw new IllegalArgumentException("Query too long");var ex=s.getSuggestionExtractor();return Map.of("items",ex==null||q.length()<2?List.of():ex.suggestionList(q));}
   case "/api/playlist":return playlist(s,p.getOrDefault("url",""));case "/api/channel":return channel(s,p.getOrDefault("url",""));
   case "/api/tab":{var t=TABS.get(p.getOrDefault("token",""));if(t==null||t.expires()<System.currentTimeMillis())throw new IllegalArgumentException("Channel tab expired. Reload channel.");var ex=t.service().getChannelTabExtractor(t.handler());ex.fetchPage();return Main.listPage(ex,ex.getInitialPage());}
   case "/api/kiosk":{var l=s.getKioskList();String k=p.getOrDefault("id",l.getDefaultKioskId());if(!l.getAvailableKiosks().contains(k))throw new IllegalArgumentException("Unsupported discovery feed");var ex=l.getExtractorById(k,null);ex.fetchPage();return Main.listPage(ex,ex.getInitialPage());}
   case "/api/stream":case "/api/comments":{String url=streamUrl(s,p);
    // This deployment has observed a YouTube login/bot-verification block. No retries or bypass.
    if(s.getServiceId()==ServiceList.YouTube.getServiceId())throw new RestrictedException();
    if(path.equals("/api/stream"))return stream(s,url);var ex=s.getCommentsExtractor(url);if(ex==null)return Map.of("items",List.of(),"next","");ex.fetchPage();return Main.listPage(ex,ex.getInitialPage());}
   default:throw new IllegalArgumentException("Unknown endpoint");}
 }
 static final class RestrictedException extends Exception{private static final long serialVersionUID=1L;}
}
