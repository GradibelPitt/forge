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
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;

/** Seat-scoped game bridge and native dialog host for the Chromium match window. */
public final class ArenaSurface implements AutoCloseable {
    private static final Gson JSON=new Gson();
    private final NeoMatchUI ui;
    private final TableScreen table;
    private final Scene scene;
    private final HttpServer server;
    private final ExecutorService http=Executors.newFixedThreadPool(4,r->{Thread t=new Thread(r,"Forge arena HTTP");t.setDaemon(true);return t;});
    private final ExecutorService streams=new ThreadPoolExecutor(0,2,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"Forge arena stream");t.setDaemon(true);return t;});
    private final String token=UUID.randomUUID().toString()+UUID.randomUUID();
    private final Timeline clock;
    private final MatchTools tools;
    private final ArenaDesktopHost desktop;
    private final java.util.function.BooleanSupplier fullscreenQuery;
    private final java.util.function.Consumer<Boolean> fullscreenRequest;
    private final VBox placeholder = new VBox(18);
    private final Label rendererStatus = new Label("Forge DIY · 对局");
    private volatile byte[] desktopState="{}".getBytes(StandardCharsets.UTF_8);
    private long desktopSequence,focusSequence;
    private boolean nativeDialog,rendererReady;
    private String desktopSignature="";
    private final Map<String,CardView> cards=new HashMap<>();
    private Map<String,CardView> nextCards=new HashMap<>();
    private volatile Map<String,ArenaImages.Reference> images=Map.of();
    private Map<String,String> nextImages=new HashMap<>();
    private final ArenaImages imageService=new ArenaImages();
    private final Map<String,java.lang.ref.WeakReference<javafx.scene.image.Image>> imageSources=new HashMap<>();
    private long imageVersion;
    private final Set<String> commands=new LinkedHashSet<>();
    private final List<Map<String,Object>> events=new ArrayList<>(), history=new ArrayList<>();
    private final Method cardClick,playerClick;
    private volatile byte[] state="[]".getBytes(StandardCharsets.UTF_8);
    private record Published(String etag,byte[] body) { }
    private volatile Published published=new Published("\"0\"",state);
    private final Object changes=new Object();
    private long revision,eventSequence,promptRevision;
    private int gameId=-1;
    private String lastView="",lastPrompt="";
    private String lastPublishFailure="";
    private boolean fullControl;
    private volatile boolean closed;

    public static AutoCloseable install(TableScreen table,NeoMatchUI ui,Scene scene) throws Exception {return new ArenaSurface(table,ui,scene);}
    private ArenaSurface(TableScreen table,NeoMatchUI ui,Scene scene) throws Exception {
        this.table=table;this.ui=ui;this.scene=scene;
        cardClick=NeoMatchUI.class.getDeclaredMethod("onCardClicked",CardView.class);cardClick.setAccessible(true);
        playerClick=NeoMatchUI.class.getDeclaredMethod("onPlayerClicked",PlayerView.class);playerClick.setAccessible(true);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(http);server.createContext("/",this::handle);server.start();
        tools=new MatchTools(table,ui,this::activateNative);
        desktop=new ArenaDesktopHost(url(),this::rendererStopped);
        fullscreenQuery=desktop::isFullscreen;fullscreenRequest=desktop::setFullscreen;
        scene.getProperties().put(forge.neo.diy.DiyMatchControls.FULLSCREEN_GET_PROPERTY,fullscreenQuery);
        scene.getProperties().put(forge.neo.diy.DiyMatchControls.FULLSCREEN_SET_PROPERTY,fullscreenRequest);
        var returnButton=new Button("打开对局窗口");returnButton.setOnAction(e->openDesktop());
        var reloadButton=new Button("重新载入对局画面");reloadButton.setOnAction(e->{desktop.reload();focusSequence++;refresh();});
        placeholder.getChildren().addAll(rendererStatus,returnButton,reloadButton);
        placeholder.setAlignment(javafx.geometry.Pos.CENTER);placeholder.setManaged(false);
        placeholder.setStyle("-fx-background-color:#101719;-fx-text-fill:#f1d8a0;");
        placeholder.prefWidthProperty().bind(table.widthProperty());placeholder.prefHeightProperty().bind(table.heightProperty());
        table.widthProperty().addListener((o,a,b)->placeholder.resize(table.getWidth(),table.getHeight()));
        table.heightProperty().addListener((o,a,b)->placeholder.resize(table.getWidth(),table.getHeight()));
        table.getChildren().add(0,placeholder);
        ui.setEventSpy(this::event);
        clock=new Timeline(new KeyFrame(Duration.millis(100),e->refresh()));clock.setCycleCount(Timeline.INDEFINITE);clock.play();
        refresh();
        if(!Boolean.getBoolean("forge.arena.noLaunch"))Platform.runLater(this::openDesktop);
    }
    public String url(){return "http://127.0.0.1:"+server.getAddress().getPort()+"/?embedded#"+token;}
    private void rendererStopped(){
        Platform.runLater(()->{if(closed)return;rendererReady=false;rendererStatus.setText(desktop.failure().isBlank()?"对局仍在进行，可重新打开窗口":desktop.failure());activateNative();refresh();});
    }
    private void openDesktop(){
        if(closed)return;
        focusSequence++;
        desktop.open();
        refresh();
    }
    private void activateNative(){
        javax.swing.SwingUtilities.invokeLater(()->{
            for(java.awt.Window w:java.awt.Window.getWindows())if(w instanceof javax.swing.JFrame&&w.isVisible()){
                if(w instanceof java.awt.Frame f)f.setExtendedState(f.getExtendedState()&~java.awt.Frame.ICONIFIED);
                w.toFront();w.requestFocus();break;
            }
        });
    }
    private void desktopEvent(JsonObject message){
        switch(message.get("type").getAsString()){
            case "ready" -> rendererReady=true;
            case "close-request" -> {forge.neo.diy.DiyMatchControls.askQuit(table,ui);activateNative();}
            case "renderer-failed", "unresponsive" -> {rendererReady=false;activateNative();}
            case "responsive" -> rendererReady=true;
            default -> { }
        }
        refresh();
    }
    private void refresh(){
        if(closed)return;
        boolean nextNative=table.isNativeDialogShowing()||tools.isBusy();
        if(nextNative!=nativeDialog)System.err.println("[arena] Native dialog="+nextNative);
        if(nextNative&&!nativeDialog)activateNative();
        if(!nextNative&&nativeDialog)focusSequence++;
        nativeDialog=nextNative;
        desktop.update(nativeDialog,focusSequence);
        placeholder.setVisible(!nativeDialog);
        placeholder.resize(table.getWidth(),table.getHeight());
        String signature=nativeDialog+"|"+focusSequence+"|"+rendererReady;
        if(!signature.equals(desktopSignature)){desktopSignature=signature;desktopSequence++;}
        desktopState=JSON.toJson(obj("sequence",desktopSequence,"visible",true,"nativeDialog",nativeDialog,"focus",focusSequence,"closed",false)).getBytes(StandardCharsets.UTF_8);
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
        if(game==null)return;
        if(table.isCurtainUp()){
            cards.clear();images=Map.of();imageSources.clear();events.clear();history.clear();
            Map<String,Object> room=obj("matchId",Integer.toString(game.getId()),"code","DIY","playerId","","seat",0,"status","starting","snapshot",null,"prompt",null,"fullControl",false,"nativeDialog",true);
            String canonical=JSON.toJson(room);
            if(lastView.equals(canonical))return;
            revision++;lastView=canonical;room.put("revision",revision);
            state=JSON.toJson(List.of(obj("type","view","room",room))).getBytes(StandardCharsets.UTF_8);
            synchronized(changes){published=new Published("\""+revision+"\"",state);changes.notifyAll();}
            return;
        }
        if(me==null||ui.getGameController()==null)return;
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
        Map<String,ArenaImages.Reference> nextArt=new HashMap<>();
        Map<String,Long> versions=new HashMap<>();
        for(var image:nextImages.entrySet()){
            var previous=images.get(image.getKey());
            var source=forge.neo.card.CardImages.peek(image.getValue());
            var previousSource=imageSources.get(image.getKey());
            // Native notifications also announce scaled copies. Only a changed original invalidates web art.
            boolean changed=previous==null||!previous.key().equals(image.getValue())||(source!=null&&(previousSource==null||source!=previousSource.get()));
            var reference=changed?new ArenaImages.Reference(image.getValue(),++imageVersion):previous;
            nextArt.put(image.getKey(),reference);versions.put(image.getKey(),reference.version());
            if(changed&&source!=null)imageSources.put(image.getKey(),new java.lang.ref.WeakReference<>(source));
        }
        imageSources.keySet().retainAll(nextArt.keySet());
        Map<String,Object> room=obj("matchId",Integer.toString(game.getId()),"code","DIY","playerId",id(me),"seat",0,"status","playing","snapshot",snapshot,"prompt",prompt,"fullControl",fullControl,"recentStackEvents",history,"imageVersions",versions,"nativeDialog",nativeDialog,"shortcuts",shortcuts());
        String canonical=JSON.toJson(room);
        images=Map.copyOf(nextArt);cards.clear();cards.putAll(nextCards);
        if(lastView.equals(canonical))return;
        revision++;lastView=canonical;room.put("revision",revision);
        List<Object> messages=new ArrayList<>();messages.add(obj("type","view","room",room));for(var event:events)messages.add(obj("type","event","matchId",Integer.toString(game.getId()),"event",event));
        byte[] nextState=JSON.toJson(messages).getBytes(StandardCharsets.UTF_8);
        state=nextState;
        synchronized(changes){published=new Published("\""+revision+"\"",nextState);changes.notifyAll();}
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
        String op=c.get("operation").getAsString();
        if(!Set.of("shortcut","tools").contains(op)&&c.get("expectedRevision").getAsLong()!=revision){receipt.put("status","resync");return obj("type","receipt","receipt",receipt);}
        if(table.isNativeDialogShowing()||tools.isBusy())return obj("type","receipt","receipt",receipt);
        JsonObject p=c.getAsJsonObject("parameters");boolean accepted=true;
        switch(op){
            case "tools" -> tools.show();
            case "shortcut" -> accepted=shortcut(p);
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

    private List<Object> shortcuts(){
        List<Object> list=new ArrayList<>();
        for(var action:forge.neo.NeoShortcuts.Action.values())for(var chord:forge.neo.NeoShortcuts.bindings(action))
            list.add(obj("action",action.name(),"key",chord.key()==null?null:chord.key().name(),"ctrl",chord.ctrl(),"shift",chord.shift(),"alt",chord.alt(),"repeats",action.repeats()));
        return list;
    }

    private boolean shortcut(JsonObject parameters){
        KeyCode code;
        try{code=KeyCode.valueOf(parameters.get("code").getAsString());}catch(Exception invalid){return false;}
        boolean shift=parameters.has("shift")&&parameters.get("shift").getAsBoolean();
        boolean ctrl=parameters.has("ctrl")&&parameters.get("ctrl").getAsBoolean();
        boolean alt=parameters.has("alt")&&parameters.get("alt").getAsBoolean();
        var event=new KeyEvent(KeyEvent.KEY_PRESSED,"","",code,shift,ctrl,alt,false);
        var action=forge.neo.NeoShortcuts.actionFor(event);
        if(code!=KeyCode.ESCAPE&&action==null)return false;
        if(action==forge.neo.NeoShortcuts.Action.FULL_CONTROL){Boolean value=ui.toggleFullControl();if(value==null)return false;fullControl=value;return true;}
        if(action==forge.neo.NeoShortcuts.Action.ZOOM_CARD){
            CardView card=parameters.has("cardId")?cards.get(parameters.get("cardId").getAsString()):null;
            if(card==null||!ui.mayView(card))return false;
            table.showZoom(card);return true;
        }
        table.requestFocus();
        if(scene.getOnKeyPressed()!=null)scene.getOnKeyPressed().handle(event);
        if(scene.getOnKeyReleased()!=null)scene.getOnKeyReleased().handle(new KeyEvent(KeyEvent.KEY_RELEASED,"","",code,shift,ctrl,alt,false));
        return true;
    }
    private void handle(HttpExchange exchange) throws IOException {
        boolean imageRequest=false;
        try{
            String path=exchange.getRequestURI().getPath();Map<String,String> query=new HashMap<>();String raw=exchange.getRequestURI().getRawQuery();if(raw!=null)for(String part:raw.split("&")){String[] pair=part.split("=",2);query.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),pair.length<2?"":URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}
            String origin=exchange.getRequestHeaders().getFirst("Origin");String local="http://127.0.0.1:"+server.getAddress().getPort();
            if(origin!=null&&!origin.equals(local)){reply(exchange,403,"text/plain",new byte[0]);return;}
            if(path.equals("/desktop/blank")&&exchange.getRequestMethod().equals("GET")){
                reply(exchange,200,"text/html","<!doctype html><meta charset=utf-8><title>Forge DIY · 对局</title><body style='background:#101719'></body>".getBytes(StandardCharsets.UTF_8));return;
            }
            if(path.equals("/state")||path.equals("/events")||path.equals("/command")||path.equals("/image")||path.startsWith("/desktop/")){
                String supplied=exchange.getRequestHeaders().getFirst("X-Forge-Token");if(supplied==null)supplied=query.get("token");
                if(!token.equals(supplied)){reply(exchange,403,"text/plain",new byte[0]);return;}
            }
            if(path.equals("/desktop/state")&&exchange.getRequestMethod().equals("GET")){reply(exchange,200,"application/json",desktopState);return;}
            if(path.equals("/desktop/event")&&exchange.getRequestMethod().equals("POST")){
                byte[] body=exchange.getRequestBody().readNBytes(4097);if(body.length>4096){reply(exchange,413,"text/plain",new byte[0]);return;}
                JsonObject message=JSON.fromJson(new String(body,StandardCharsets.UTF_8),JsonObject.class);
                Platform.runLater(()->desktopEvent(message));reply(exchange,200,"application/json","{}".getBytes(StandardCharsets.UTF_8));return;
            }
            if(path.equals("/state")&&exchange.getRequestMethod().equals("GET")){
                var current=published;exchange.getResponseHeaders().set("ETag",current.etag());
                if(current.etag().equals(exchange.getRequestHeaders().getFirst("If-None-Match"))){exchange.sendResponseHeaders(304,-1);return;}
                reply(exchange,200,"application/json",current.body());return;
            }
            if(path.equals("/events")&&exchange.getRequestMethod().equals("GET")){
                try{
                    streams.execute(()->{try{stream(exchange);}catch(IOException disconnected){}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}finally{exchange.close();}});
                    imageRequest=true;
                }catch(RejectedExecutionException full){reply(exchange,503,"text/plain",new byte[0]);}
                return;
            }
            if(path.equals("/command")&&exchange.getRequestMethod().equals("POST")){
                byte[] body=exchange.getRequestBody().readNBytes(16385);if(body.length>16384){reply(exchange,413,"text/plain",new byte[0]);return;}
                JsonObject message=JSON.fromJson(new String(body,StandardCharsets.UTF_8),JsonObject.class);
                FutureTask<Map<String,Object>> task=new FutureTask<>(()->command(message));Platform.runLater(task);
                reply(exchange,200,"application/json",JSON.toJson(task.get(8,TimeUnit.SECONDS)).getBytes(StandardCharsets.UTF_8));return;
            }
            if(path.equals("/image")){
                var reference=images.get(query.get("name"));if(reference==null){reply(exchange,404,"text/plain",new byte[0]);return;}
                imageRequest=true;imageService.serve(exchange,reference,"preview".equals(query.get("size")));return;
            }
            if(!exchange.getRequestMethod().equals("GET")||path.contains("..")){reply(exchange,404,"text/plain",new byte[0]);return;}
            if(path.equals("/"))path="/index.html";
            try(InputStream in=ArenaSurface.class.getResourceAsStream("/web"+path)){if(in==null){reply(exchange,404,"text/plain",new byte[0]);return;}String type=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":path.endsWith(".png")?"image/png":path.endsWith(".jpg")?"image/jpeg":"text/html";reply(exchange,200,type,in.readAllBytes());}
        }catch(Exception e){reply(exchange,500,"text/plain","Bridge request failed".getBytes(StandardCharsets.UTF_8));}finally{if(!imageRequest)exchange.close();}
    }
    private void stream(HttpExchange exchange)throws IOException,InterruptedException{
        exchange.getResponseHeaders().set("Content-Type","text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.sendResponseHeaders(200,0);
        String previous="";var output=exchange.getResponseBody();
        while(!closed){
            Published current;
            synchronized(changes){
                if(published.etag().equals(previous)&&!closed)changes.wait(2000);
                current=published;
            }
            if(closed)return;
            byte[] body=current.etag().equals(previous)?("[{\"type\":\"heartbeat\",\"revision\":"+current.etag().replace("\"","")+"}]").getBytes(StandardCharsets.UTF_8):current.body();
            output.write("data: ".getBytes(StandardCharsets.UTF_8));output.write(body);output.write('\n');output.write('\n');output.flush();
            previous=current.etag();
        }
    }
    private static void reply(HttpExchange x,int status,String type,byte[] data)throws IOException{x.getResponseHeaders().set("Content-Type",type+((type.startsWith("text/")||type.equals("application/json"))?"; charset=utf-8":""));x.getResponseHeaders().set("Cache-Control","no-store");x.getResponseHeaders().set("X-Content-Type-Options","nosniff");x.sendResponseHeaders(status,data.length);x.getResponseBody().write(data);}
    public void close(){closed=true;clock.stop();ui.setEventSpy(null);synchronized(changes){changes.notifyAll();}scene.getProperties().remove(forge.neo.diy.DiyMatchControls.FULLSCREEN_GET_PROPERTY,fullscreenQuery);scene.getProperties().remove(forge.neo.diy.DiyMatchControls.FULLSCREEN_SET_PROPERTY,fullscreenRequest);desktop.close();tools.close();table.getChildren().remove(placeholder);server.stop(0);streams.shutdownNow();http.shutdownNow();imageService.close();imageSources.clear();}
}
