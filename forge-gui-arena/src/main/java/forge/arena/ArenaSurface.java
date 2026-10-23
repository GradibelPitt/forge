package forge.arena;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import forge.game.event.*;
import forge.game.spellability.StackItemView;
import forge.neo.match.NeoMatchUI;
import forge.neo.ui.TableScreen;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.web.WebView;
import javafx.concurrent.Worker;
import javafx.util.Duration;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** A loopback service renders the upstream React board over the existing match controller. */
public final class ArenaSurface implements AutoCloseable {
    private static final Gson JSON=new Gson();
    private final NeoMatchUI ui;
    private final TableScreen table;
    private final WebView web=new WebView();
    private final HttpServer server;
    private final ExecutorService http=Executors.newFixedThreadPool(4,r->{Thread t=new Thread(r,"Forge arena HTTP");t.setDaemon(true);return t;});
    private final String token=UUID.randomUUID().toString()+UUID.randomUUID();
    private final Timeline clock;
    private final MatchTools tools;
    private final BoardLayers layers;
    private final Map<String,CardView> cards=new HashMap<>();
    private Map<String,CardView> nextCards=new HashMap<>();
    private volatile Map<String,String> images=Map.of();
    private Map<String,String> nextImages=new HashMap<>();
    private final Map<String,byte[]> imageCache=Collections.synchronizedMap(new LinkedHashMap<>());
    private final Set<String> commands=new LinkedHashSet<>();
    private final List<Map<String,Object>> events=new ArrayList<>(), history=new ArrayList<>();
    private final Method cardClick,playerClick;
    private volatile byte[] state="[]".getBytes(StandardCharsets.UTF_8);
    private long revision,eventSequence,promptRevision;
    private int gameId=-1;
    private String lastView="",lastPrompt="";
    private String lastPublishFailure="";
    private boolean old,loaded,closed,fullControl;
    private long imageEpoch;
    private final forge.neo.card.CardImages.Listener imageListener=()->{imageCache.clear();imageEpoch++;};

    public static AutoCloseable install(TableScreen table,NeoMatchUI ui,Scene scene) throws Exception {return new ArenaSurface(table,ui,scene);}
    private ArenaSurface(TableScreen table,NeoMatchUI ui,Scene scene) throws Exception {
        this.table=table;this.ui=ui;
        cardClick=NeoMatchUI.class.getDeclaredMethod("onCardClicked",CardView.class);cardClick.setAccessible(true);
        playerClick=NeoMatchUI.class.getDeclaredMethod("onPlayerClicked",PlayerView.class);playerClick.setAccessible(true);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(http);server.createContext("/",this::handle);server.start();
        web.setManaged(false);web.setContextMenuEnabled(false);
        web.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,e->{
            boolean editing=loaded&&Boolean.TRUE.equals(web.getEngine().executeScript("!!document.activeElement?.closest('input,textarea,select,[contenteditable]')"));
            if(!editing&&scene.getOnKeyPressed()!=null)scene.getOnKeyPressed().handle(e);
        });
        web.addEventFilter(javafx.scene.input.KeyEvent.KEY_RELEASED,e->{if(scene.getOnKeyReleased()!=null)scene.getOnKeyReleased().handle(e);});
        web.prefWidthProperty().bind(table.widthProperty());web.prefHeightProperty().bind(table.heightProperty());
        web.minWidthProperty().bind(table.widthProperty());web.maxWidthProperty().bind(table.widthProperty());
        web.minHeightProperty().bind(table.heightProperty());web.maxHeightProperty().bind(table.heightProperty());
        table.widthProperty().addListener((o,a,b)->web.resize(table.getWidth(),table.getHeight()));
        table.heightProperty().addListener((o,a,b)->web.resize(table.getWidth(),table.getHeight()));
        web.resize(table.getWidth(),table.getHeight());
        table.getChildren().add(table.getChildren().indexOf(table.getPromptBanner()),web);
        tools=new MatchTools(table,ui,this::toggle);
        layers=new BoardLayers(table,web,tools.button());
        old="neo".equalsIgnoreCase(System.getProperty("forge.match.ui"))||forge.neo.NeoSettings.getBool("diy.arena.legacy",false);
        web.setVisible(false);
        layers.setActive(!old);
        web.getEngine().getLoadWorker().stateProperty().addListener((o,a,b)->{
            if(b==Worker.State.SUCCEEDED){loaded=true;refresh();}
            if(b==Worker.State.FAILED){loaded=false;old=true;refresh();table.showInfo("局内界面","网页界面加载失败，已保留旧界面。可从工具菜单重试。");}
        });
        web.getEngine().load("http://127.0.0.1:"+server.getAddress().getPort()+"/?embedded#"+token);
        ui.setEventSpy(this::event);
        forge.neo.card.CardImages.addListener(imageListener);
        clock=new Timeline(new KeyFrame(Duration.millis(100),e->refresh()));clock.setCycleCount(Timeline.INDEFINITE);clock.play();
    }
    public String url(){return "http://127.0.0.1:"+server.getAddress().getPort()+"/?embedded#"+token;}
    public WebView webView(){return web;}
    private void toggle(){old=!old;forge.neo.NeoSettings.setBool("diy.arena.legacy",old);if(!loaded&&!old)web.getEngine().reload();refresh();}
    private void refresh(){
        if(closed)return;
        // Renderer selection is independent of transient snapshots and native notices.
        boolean supported=ui.getGameView()==null||ui.getGameView().getPlayers().size()<=2;
        boolean active=!old&&supported;
        layers.setActive(active);
        web.setVisible(loaded&&active&&!table.isCurtainUp());
        tools.toFront();
        try{
            publish();lastPublishFailure="";
        }catch(Exception e){
            if(!e.toString().equals(lastPublishFailure)){
                lastPublishFailure=e.toString();
                System.err.println("[arena] Keeping the last complete snapshot: "+e);
            }
        }
    }
    private static Map<String,Object> obj(Object... values){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)out.put((String)values[i],values[i+1]);return out;}
    private static String id(PlayerView p){return p==null?null:"p"+p.getId();}
    private static String id(CardView c){return c==null?null:"c"+c.getId();}
    private List<Object> zone(PlayerView p,ZoneType zone){List<Object> out=new ArrayList<>();if(p.getCards(zone)!=null)for(CardView c:p.getCards(zone))out.add(card(c));return out;}
    private Map<String,Object> card(CardView c){
        boolean visible=ui.mayView(c);var s=c.getCurrentState();
        Map<String,Object> out=obj("id",id(c),"name",visible?s.getName():"牌面朝下","ownerId",id(c.getOwner()),"controllerId",id(c.getController()),"hidden",!visible,"kind",visible&&s.isCreature()?"creature":visible&&s.isLand()?"land":"permanent","tapped",c.isTapped(),"actionable",visible&&ui.actionableStrength(c)>0,"token",visible&&c.isToken());
        // Only cards present in this viewer's projection can be selected or requested as images.
        nextCards.put(id(c),c);
        if(visible){if(s.getName()!=null&&s.getImageKey()!=null)nextImages.put(s.getName(),s.getImageKey());if(s.isCreature()){out.put("power",s.getPower());out.put("toughness",s.getToughness());}
            if(c.getAttachedTo()!=null)out.put("attachedTo",id(c.getAttachedTo()));
            Map<String,Integer> counters=new LinkedHashMap<>();if(c.getCounters()!=null)c.getCounters().elementSet().forEach(k->counters.put(k.toString(),c.getCounters(k)));out.put("counters",counters);
        }
        return out;
    }
    private void publish(){
        GameView game=ui.getGameView();PlayerView me=ui.localPlayerView();
        if(game==null||me==null||ui.getGameController()==null||table.isCurtainUp())return;
        if(gameId!=game.getId()){gameId=game.getId();events.clear();history.clear();commands.clear();eventSequence=0;lastView="";}
        nextCards=new HashMap<>();nextImages=new HashMap<>();List<Object> players=new ArrayList<>();
        for(PlayerView p:game.getPlayers()){
            List<Object> hand=new ArrayList<>();if(p.equals(me))hand=zone(p,ZoneType.Hand);else if(p.getHand()!=null)for(CardView c:p.getHand())if(ui.mayView(c))hand.add(card(c));
            players.add(obj("id",id(p),"name",p.getName(),"life",p.getLife(),"handCount",p.getZoneSize(ZoneType.Hand),"libraryCount",p.getZoneSize(ZoneType.Library),"hand",hand,"battlefield",zone(p,ZoneType.Battlefield),"graveyard",zone(p,ZoneType.Graveyard),"exile",zone(p,ZoneType.Exile),"command",zone(p,ZoneType.Command)));
        }
        List<Object> stack=new ArrayList<>();for(StackItemView s:game.getStack()){CardView c=s.getSourceCard();if(c==null)continue;Map<String,Object> item=card(c);item.put("stackId",s.getKey());item.put("ability",s.isAbility());item.put("description",ui.mayView(c)?s.getText():"");stack.add(item);}
        List<Object> combat=new ArrayList<>();if(game.getCombat()!=null)for(CardView a:game.getCombat().getAttackers()){
            List<String> blockers=new ArrayList<>();if(game.getCombat().getBlockers(a)!=null)for(CardView b:game.getCombat().getBlockers(a))blockers.add(id(b));
            var defender=game.getCombat().getDefender(a);Map<String,Object> row=obj("attackerId",id(a),"blockerIds",blockers);
            if(defender instanceof PlayerView p)row.put("defenderPlayerId",id(p));else if(defender instanceof CardView c)row.put("defenderCardId",id(c));combat.add(row);
        }
        List<Button> buttons=new ArrayList<>();for(Node n:table.getActionBar().lookupAll(".button"))if(n instanceof Button b&&b.isVisible())buttons.add(b);
        // ActionBar's primary/secondary nodes preserve the exact enabled state and captions chosen by Forge.
        Button ok=buttons.stream().filter(b->b.getStyleClass().contains("btn-primary")).findFirst().orElse(null);
        Button cancel=buttons.stream().filter(b->b.getStyleClass().contains("btn-secondary")&&!b.getStyleClass().contains("resolve-all")&&!b.getStyleClass().contains("player-choice")).findFirst().orElse(null);
        String signature=table.getLastPrompt()+"|"+(ok==null?"":ok.getText()+ok.isDisabled())+"|"+(cancel==null?"":cancel.getText()+cancel.isDisabled())+"|"+table.isModalShowing();
        if(!signature.equals(lastPrompt)){lastPrompt=signature;promptRevision++;}
        Map<String,Object> prompt=obj("seat",0,"requestId",Long.toString(promptRevision),"kind","input","message",table.getLastPrompt(),"okEnabled",ok!=null&&!ok.isDisabled(),"cancelEnabled",cancel!=null&&!cancel.isDisabled(),"okLabel",ok==null?"确认":ok.getText(),"cancelLabel",cancel==null?"取消":cancel.getText());
        Map<String,Object> snapshot=obj("players",players,"stack",stack,"combat",combat,"phase",game.getPhase()==null?"null":game.getPhase().name(),"activePlayerId",id(game.getPlayerTurn()),"gameOver",game.isGameOver(),"lastEventSequence",eventSequence);
        Map<String,Object> room=obj("matchId",Integer.toString(game.getId()),"code","DIY","playerId",id(me),"seat",0,"status","playing","snapshot",snapshot,"prompt",prompt,"fullControl",fullControl,"recentStackEvents",history,"imageEpoch",imageEpoch);
        String canonical=JSON.toJson(room);if(!lastView.equals(canonical)){revision++;lastView=canonical;}room.put("revision",revision);
        List<Object> messages=new ArrayList<>();messages.add(obj("type","view","room",room));for(var event:events)messages.add(obj("type","event","matchId",Integer.toString(game.getId()),"event",event));
        byte[] nextState=JSON.toJson(messages).getBytes(StandardCharsets.UTF_8);
        images=Map.copyOf(nextImages);
        cards.clear();cards.putAll(nextCards);
        state=nextState;
    }
    private void event(GameEvent event){
        if(table.isCurtainUp()||ui.getGameController()==null)return;
        String kind=null;Map<String,Object> data=null;
        if(event instanceof GameEventCardTapped e&&ui.mayView(e.card())){kind="tap";data=obj("cardId",id(e.card()),"tapped",e.tapped());}
        if(event instanceof GameEventTurnPhase e){kind="phase";data=obj("playerId",id(e.playerTurn()),"phase",e.phase().name());}
        if(event instanceof GameEventCardChangeZone e){
            kind="zone";data=obj("playerId",id(e.card().getOwner()),"from",e.from()==null?null:e.from().zoneType().name(),"to",e.to()==null?null:e.to().zoneType().name());
            if(ui.mayView(e.card())){data.put("cardId",id(e.card()));data.put("name",e.card().getCurrentState().getName());}
        }
        if(event instanceof GameEventPlayerLivesChanged e){kind="life";data=obj("playerId",id(e.player()),"before",e.oldLives(),"after",e.newLives(),"amount",e.newLives()-e.oldLives());}
        if(event instanceof GameEventCardDamaged e&&ui.mayView(e.card())){kind="damage";data=obj("cardId",id(e.card()),"amount",e.amount());}
        if(event instanceof GameEventSpellAbilityCast e&&e.si().getSourceCard()!=null&&ui.mayView(e.si().getSourceCard())){kind="cast";data=obj("cardId",id(e.si().getSourceCard()),"name",e.si().getSourceCard().getCurrentState().getName(),"description",e.si().getText(),"playerId",id(e.si().getActivatingPlayer()),"ability",e.si().isAbility());}
        if(event instanceof GameEventSpellResolved e&&e.spell().getHostCard()!=null&&ui.mayView(e.spell().getHostCard())){kind="resolve";data=obj("cardId",id(e.spell().getHostCard()),"name",e.spell().getHostCard().getCurrentState().getName(),"description",e.stackDescription(),"fizzled",e.hasFizzled());}
        if(kind==null)return;
        Map<String,Object> item=obj("sequence",++eventSequence,"kind",kind,"data",data);events.add(item);while(events.size()>48)events.remove(0);
        if(kind.equals("cast")||kind.equals("resolve")){history.add(item);while(history.size()>16)history.remove(0);}
    }
    private Map<String,Object> command(JsonObject message) throws Exception {
        if(message.get("type").getAsString().equals("leave")){table.getMenuButton().fire();return obj("type","view","room",JSON.fromJson(new String(state,StandardCharsets.UTF_8),JsonArray.class).get(0).getAsJsonObject().get("room"));}
        JsonObject c=message.getAsJsonObject("command");String key=c.get("commandId").getAsString();
        Map<String,Object> receipt=obj("commandId",key,"status","rejected","revision",revision);
        if(commands.contains(key)){receipt.put("status","accepted");return obj("type","receipt","receipt",receipt);}
        if(ui.getGameView()==null||!Integer.toString(ui.getGameView().getId()).equals(c.get("matchId").getAsString())||!Objects.equals(id(ui.localPlayerView()),c.get("playerId").getAsString()))return obj("type","receipt","receipt",receipt);
        if(c.get("expectedRevision").getAsLong()!=revision){receipt.put("status","resync");return obj("type","receipt","receipt",receipt);}
        if(table.isModalShowing()||table.isCurtainUp()||table.getPromptBanner().isAlerting())return obj("type","receipt","receipt",receipt);
        String op=c.get("operation").getAsString();JsonObject p=c.getAsJsonObject("parameters");boolean accepted=true;
        switch(op){
            case "ok" -> accepted=table.getActionBar().pressPrimary();
            case "cancel" -> accepted=table.getActionBar().pressSecondary();
            case "control" -> {Boolean value=ui.toggleFullControl();if(value!=null)fullControl=value;else accepted=false;}
            case "selectCard" -> {CardView card=cards.get(p.get("cardId").getAsString());if(card!=null)cardClick.invoke(ui,card);else accepted=false;}
            case "selectPlayer" -> {PlayerView player=null;for(PlayerView v:ui.getGameView().getPlayers())if(id(v).equals(p.get("playerId").getAsString()))player=v;if(player!=null)playerClick.invoke(ui,player);else accepted=false;}
            case "pass" -> {String k=p.get("key").getAsString();var mode=switch(k){case "1"->forge.gamemodes.match.PriorityPass.Mode.OFF;case "2"->forge.gamemodes.match.PriorityPass.Mode.UNTIL_RESPONSE;case "6"->forge.gamemodes.match.PriorityPass.Mode.WHOLE_TURN;case "0"->forge.gamemodes.match.PriorityPass.Mode.UNTIL_END_STEP;default->null;};accepted=mode!=null&&ui.passPriorityShortcut(mode);}
            default -> accepted=false;
        }
        if(accepted){commands.add(key);while(commands.size()>512)commands.remove(commands.iterator().next());receipt.put("status","accepted");}
        return obj("type","receipt","receipt",receipt);
    }
    private void handle(HttpExchange exchange) throws IOException {
        try{
            String path=exchange.getRequestURI().getPath();Map<String,String> query=new HashMap<>();String raw=exchange.getRequestURI().getRawQuery();if(raw!=null)for(String part:raw.split("&")){String[] pair=part.split("=",2);query.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),pair.length<2?"":URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}
            String origin=exchange.getRequestHeaders().getFirst("Origin");String local="http://127.0.0.1:"+server.getAddress().getPort();
            if(origin!=null&&!origin.equals(local)){reply(exchange,403,"text/plain",new byte[0]);return;}
            if(path.equals("/state")||path.equals("/command")||path.equals("/image")){
                String supplied=exchange.getRequestHeaders().getFirst("X-Forge-Token");if(supplied==null)supplied=query.get("token");
                if(!token.equals(supplied)){reply(exchange,403,"text/plain",new byte[0]);return;}
            }
            if(path.equals("/state")&&exchange.getRequestMethod().equals("GET")){reply(exchange,200,"application/json",state);return;}
            if(path.equals("/command")&&exchange.getRequestMethod().equals("POST")){
                byte[] body=exchange.getRequestBody().readNBytes(16385);if(body.length>16384){reply(exchange,413,"text/plain",new byte[0]);return;}
                JsonObject message=JSON.fromJson(new String(body,StandardCharsets.UTF_8),JsonObject.class);
                FutureTask<Map<String,Object>> task=new FutureTask<>(()->command(message));Platform.runLater(task);
                reply(exchange,200,"application/json",JSON.toJson(task.get(8,TimeUnit.SECONDS)).getBytes(StandardCharsets.UTF_8));return;
            }
            if(path.equals("/image")){
                String imageKey=images.get(query.get("name"));if(imageKey==null){reply(exchange,404,"text/plain",new byte[0]);return;}
                byte[] bytes=imageCache.get(imageKey);if(bytes==null){FutureTask<BufferedImage> task=new FutureTask<>(()->forge.ImageCache.getOriginalImage(imageKey,true,null));javax.swing.SwingUtilities.invokeLater(task);BufferedImage face=task.get(6,TimeUnit.SECONDS);if(face==null){reply(exchange,404,"text/plain",new byte[0]);return;}ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(face,"png",out);bytes=out.toByteArray();synchronized(imageCache){if(imageCache.size()>128)imageCache.clear();imageCache.put(imageKey,bytes);}}
                reply(exchange,200,"image/png",bytes);return;
            }
            if(!exchange.getRequestMethod().equals("GET")||path.contains("..")){reply(exchange,404,"text/plain",new byte[0]);return;}
            if(path.equals("/"))path="/index.html";
            try(InputStream in=ArenaSurface.class.getResourceAsStream("/web"+path)){if(in==null){reply(exchange,404,"text/plain",new byte[0]);return;}String type=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":path.endsWith(".png")?"image/png":path.endsWith(".jpg")?"image/jpeg":"text/html";reply(exchange,200,type,in.readAllBytes());}
        }catch(Exception e){reply(exchange,500,"text/plain","Bridge request failed".getBytes(StandardCharsets.UTF_8));}finally{exchange.close();}
    }
    private static void reply(HttpExchange x,int status,String type,byte[] data)throws IOException{x.getResponseHeaders().set("Content-Type",type+((type.startsWith("text/")||type.equals("application/json"))?"; charset=utf-8":""));x.getResponseHeaders().set("Cache-Control","no-store");x.getResponseHeaders().set("X-Content-Type-Options","nosniff");x.sendResponseHeaders(status,data.length);x.getResponseBody().write(data);}
    public void close(){closed=true;clock.stop();ui.setEventSpy(null);forge.neo.card.CardImages.removeListener(imageListener);layers.close();tools.close();web.getEngine().load(null);table.getChildren().remove(web);server.stop(0);http.shutdownNow();imageCache.clear();}
}
