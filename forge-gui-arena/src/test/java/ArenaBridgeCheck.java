import com.google.gson.*;
import forge.arena.ArenaSurface;
import forge.neo.match.NeoMatchUI;
import forge.neo.ui.TableScreen;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.control.*;

public final class ArenaBridgeCheck {
    static final AtomicInteger mana=new AtomicInteger(),hand=new AtomicInteger();
    static final forge.interfaces.IDevModeCheats cheats=(forge.interfaces.IDevModeCheats) java.lang.reflect.Proxy.newProxyInstance(forge.interfaces.IDevModeCheats.class.getClassLoader(),new Class[]{forge.interfaces.IDevModeCheats.class},(p,m,a)->{
        if(m.getName().equals("generateMana"))mana.incrementAndGet();
        if(m.getName().equals("addCardToHand"))hand.incrementAndGet();
        return null;
    });
    static void fx(Runnable r)throws Exception {FutureTask<Void> f=new FutureTask<>(r,null);Platform.runLater(f);f.get(5,TimeUnit.SECONDS);}
    static CompletableFuture<Void> run(ArenaSurface surface,NeoMatchUI ui,TableScreen table){return CompletableFuture.runAsync(()->{
        try{
            Thread.sleep(1600);
            URI url=URI.create(surface.url());String root="http://"+url.getAuthority(),token=url.getFragment();
            HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            var denied=client.send(HttpRequest.newBuilder(URI.create(root+"/state")).build(),HttpResponse.BodyHandlers.ofString());
            if(denied.statusCode()!=403)throw new AssertionError("State accessible without token");
            var badImage=client.send(HttpRequest.newBuilder(URI.create(root+"/image?name=Unknown&token="+token)).build(),HttpResponse.BodyHandlers.ofString());
            if(badImage.statusCode()!=404)throw new AssertionError("Unprojected image was accessible");
            AtomicInteger calls=new AtomicInteger();fx(()->{table.getActionBar().setButtons("Confirm","Cancel",true,true);table.getActionBar().setOnOk(calls::incrementAndGet);});
            Thread.sleep(250);
            String raw=client.send(HttpRequest.newBuilder(URI.create(root+"/state")).header("X-Forge-Token",token).build(),HttpResponse.BodyHandlers.ofString()).body();
            JsonObject room=JsonParser.parseString(raw).getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("room");
            if(room.getAsJsonObject("snapshot").getAsJsonArray("players").get(1).getAsJsonObject().getAsJsonArray("hand").size()!=0)throw new AssertionError("Opponent hand exposed");
            JsonObject command=new JsonObject();command.addProperty("protocolVersion",1);command.add("matchId",room.get("matchId"));command.add("playerId",room.get("playerId"));command.addProperty("commandId",UUID.randomUUID().toString());command.add("expectedRevision",room.get("revision"));command.addProperty("operation","ok");command.add("parameters",new JsonObject());
            JsonObject envelope=new JsonObject();envelope.addProperty("type","command");envelope.add("command",command);
            HttpRequest request=HttpRequest.newBuilder(URI.create(root+"/command")).header("X-Forge-Token",token).POST(HttpRequest.BodyPublishers.ofString(envelope.toString())).build();
            var receipt=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(!receipt.body().contains("accepted"))throw new AssertionError("Input not accepted: "+receipt.body());
            client.send(request,HttpResponse.BodyHandlers.ofString());if(calls.get()!=1)throw new AssertionError("Duplicate command executed: "+calls);
            command.addProperty("commandId",UUID.randomUUID().toString());command.addProperty("expectedRevision",-1);
            var stale=client.send(HttpRequest.newBuilder(URI.create(root+"/command")).header("X-Forge-Token",token).POST(HttpRequest.BodyPublishers.ofString(envelope.toString())).build(),HttpResponse.BodyHandlers.ofString());
            if(!stale.body().contains("resync")||calls.get()!=1)throw new AssertionError("Stale command executed");
            fx(()->{try{
                var f=ArenaSurface.class.getDeclaredField("tools");f.setAccessible(true);Object tools=f.get(surface);
                var mf=tools.getClass().getDeclaredField("menu");mf.setAccessible(true);ContextMenu menu=(ContextMenu)mf.get(tools);
                for(MenuItem item:menu.getItems())if(item.getText()!=null&&(item.getText().equals("产生法术力")||item.getText().equals("添加牌到手牌")))item.fire();
                for(MenuItem item:menu.getItems())if(item.getText()!=null&&item.getText().startsWith("切换")){item.fire();if(surface.webView().isVisible())throw new AssertionError("Legacy switch failed");item.fire();if(!surface.webView().isVisible())throw new AssertionError("Arena switch failed");}
            }catch(Exception e){throw new RuntimeException(e);}});
            for(int i=0;i<40&&(mana.get()!=1||hand.get()!=1);i++)Thread.sleep(25);
            if(mana.get()!=1||hand.get()!=1)throw new AssertionError("Native cheat callbacks were swallowed");
            System.out.println("BRIDGE_AUTH_COMMAND_DEDUP_STALE_VISIBILITY_TOOLS_AND_FALLBACK_OK");
        }catch(Exception e){throw new CompletionException(e);}
    });}
}
