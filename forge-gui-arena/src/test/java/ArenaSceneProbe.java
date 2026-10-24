import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.Proxy;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiBase;
import forge.neo.ui.TableScreen;
import forge.neo.match.NeoMatchUI;
import forge.neo.diy.NativeMatchBinder;
import forge.game.card.Card;
import forge.game.zone.ZoneType;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import forge.localinstance.properties.ForgeConstants;

public class ArenaSceneProbe {
    static final String APP = System.getProperty("forge.test.app") + "/";
    static forge.util.ImageFetcher imageFetcher;
    static volatile boolean failProjection;
    public static void main(String[] args) throws Exception {
        GuiBase.setInterface((IGuiBase) Proxy.newProxyInstance(IGuiBase.class.getClassLoader(), new Class[]{IGuiBase.class}, (p,m,a) -> {
            if (m.getName().equals("getAssetsDir")) return APP;
            if (m.getName().equals("getImageFetcher")) return imageFetcher;
            if (m.getName().equals("isRunningOnDesktop")) return true;
            if (m.getName().equals("isGuiThread")) return javax.swing.SwingUtilities.isEventDispatchThread();
            if (m.getName().equals("invokeInEdtLater")) { javax.swing.SwingUtilities.invokeLater((Runnable)a[0]); return null; }
            if (m.getName().equals("invokeInEdtAndWait")) { if (javax.swing.SwingUtilities.isEventDispatchThread()) ((Runnable)a[0]).run(); else javax.swing.SwingUtilities.invokeAndWait((Runnable)a[0]); return null; }
            if (m.getName().equals("getCurrentVersion")) return "neo-integration-probe";
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == int.class) return 0;
            if (m.getReturnType() == float.class) return 1f;
            if (m.getReturnType() == double.class) return 1d;
            return null;
        }));
        GuiBase.setUsingAppDirectory(true);
        forge.util.Localizer.getInstance().initialize("en-US", APP + "res/languages/");
        forge.util.Lang.createInstance("en-US");
        forge.model.FModel.loadDynamicGamedata();
        // Load only real card-renderer sprites; FSkin.loadFull would create the desktop window.
        var setImage=forge.toolbox.FSkin.class.getDeclaredMethod("setImage",forge.localinstance.skin.FSkinProp.class,java.awt.image.BufferedImage.class);
        setImage.setAccessible(true);
        setImage.invoke(null,forge.localinstance.skin.FSkinProp.IMG_SETLOGO_MYTHIC,
                ImageIO.read(Path.of(ForgeConstants.DEFAULT_SKINS_DIR,ForgeConstants.SPRITE_SETLOGO_FILE).toFile()));
        var white=forge.localinstance.skin.FSkinProp.MANA_IMG.get("W");
        setImage.invoke(null,white,ImageIO.read(Path.of(ForgeConstants.DEFAULT_SKINS_DIR,ForgeConstants.SPRITE_MANAICONS_FILE).toFile()));
        var symbols=forge.toolbox.CardFaceSymbols.class.getDeclaredField("MANA_IMAGES"); symbols.setAccessible(true);
        ((Map)symbols.get(null)).put("W",forge.toolbox.FSkin.getImage(white));
        forge.model.FModel.getPreferences().setPref(forge.localinstance.properties.ForgePreferences.FPref.UI_ENABLE_ONLINE_IMAGE_FETCHER, false);
        Path fixtureCache = Path.of(args[0], "image-cache").toAbsolutePath();
        Files.createDirectories(fixtureCache);
        forge.ImageKeys.initializeDirs(fixtureCache.toString() + "/", ForgeConstants.CACHE_CARD_PICS_SUBDIR,
                ForgeConstants.CACHE_TOKEN_PICS_DIR, ForgeConstants.CACHE_ICON_PICS_DIR,
                ForgeConstants.CACHE_BOOSTER_PICS_DIR, ForgeConstants.CACHE_FATPACK_PICS_DIR,
                ForgeConstants.CACHE_BOOSTERBOX_PICS_DIR, ForgeConstants.CACHE_PRECON_PICS_DIR,
                ForgeConstants.CACHE_TOURNAMENTPACK_PICS_DIR);
        Path sample = Path.of(args[0], "sample-cards"); Files.createDirectories(sample);
        for (String name : List.of("l/lightning_bolt", "l/llanowar_elves", "f/forest", "i/island", "g/giant_growth", "s/shivan_dragon", "m/mountain", "d/drakuseth_maw_of_flames", "g/golgari_guildgate")) {
            Files.copy(Path.of(APP, "res/cardsfolder", name + ".txt"), sample.resolve(name.substring(2) + ".txt"), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(Path.of(APP,"managed/custom/cards/white/巨龙之魂.txt"),sample.resolve("dragon_soul.txt"),StandardCopyOption.REPLACE_EXISTING);
        Path empty = Path.of(args[0], "empty"); Files.createDirectories(empty);
        forge.StaticData data = new forge.StaticData(new forge.CardStorageReader(sample.toString(), null, false), null,
                APP + "res/editions/", APP+"managed/custom/editions/", empty.toString(), "Latest", true, true);
        var uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
        var unsafe = (sun.misc.Unsafe)uf.get(null);
        var db = forge.model.FModel.class.getDeclaredField("magicDb");
        unsafe.putObject(unsafe.staticFieldBase(db), unsafe.staticFieldOffset(db), (com.google.common.base.Supplier<forge.StaticData>)() -> data);
        // A private bitmap fixture keeps the image-service check independent of user caches.
        var imagePath = fixtureCache.resolve("arena-test/LightningBolt.full.jpg");
        Files.createDirectories(imagePath.getParent());
        var bitmap = new java.awt.image.BufferedImage(1000, 1400, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = bitmap.createGraphics();
        graphics.setColor(new java.awt.Color(75, 35, 25)); graphics.fillRect(0, 0, 1000, 1400); graphics.dispose();
        ImageIO.write(bitmap, "jpg", imagePath.toFile());
        if (args.length > 1 && args[1].equals("--images")) {
            try { throw new UnsupportedOperationException(); }
            catch (Throwable e) { e.printStackTrace(); Platform.exit(); System.exit(1); }
            return;
        }
        if (args.length > 1 && args[1].equals("--match")) {
            try { runMatch(data); }
            catch (Throwable e) { e.printStackTrace(); Platform.exit(); System.exit(1); }
            return;
        }
        var players = List.of(new forge.game.player.RegisteredPlayer(new forge.deck.Deck()).setPlayer(new forge.ai.LobbyPlayerAi("You", null)),
                new forge.game.player.RegisteredPlayer(new forge.deck.Deck()).setPlayer(new forge.ai.LobbyPlayerAi("Opponent", null)));
        var rules = new forge.game.GameRules(forge.game.GameType.Constructed);
        var game = new forge.game.Game(players, rules, new forge.game.Match(rules, players, "Neo integration"));
        var cards = new ArrayList<forge.game.card.CardView>();
        int i=0;
        for (String name : List.of("Drakuseth, Maw of Flames", "Golgari Guildgate", "Drakuseth, Maw of Flames", "Golgari Guildgate", "Lightning Bolt", "巨龙之魂")) {
            var paper = name.startsWith("Drakuseth") ? data.getCommonCards().getCard(name,"FDN",2) : name.equals("Lightning Bolt") ? data.getCommonCards().getCard(name,"PF25",1) : name.equals("Golgari Guildgate") ? data.getCommonCards().getCard(name,"FDN",1) : data.getCommonCards().getCard(name);
            Card c = Card.fromPaperCard(paper, game.getPlayers().get(i < 2 || i > 3 ? 0 : 1));
            if (name.equals("Lightning Bolt")) c.setImageKey("arena-test/LightningBolt.full");
            c.getOwner().getZone(i > 3 ? ZoneType.Hand : ZoneType.Battlefield).add(c);
            cards.add(c.getView()); i++;
        }
        CountDownLatch done = new CountDownLatch(1);
        if (Boolean.getBoolean("forge.test.performance")) {
            for (int n = 0; n < 60; n++) {
                var extra = Card.fromPaperCard(data.getCommonCards().getCard(n % 2 == 0 ? "Drakuseth, Maw of Flames" : "Golgari Guildgate", "FDN", n % 2 == 0 ? 2 : 1), game.getPlayers().get(n % 2));
                extra.getOwner().getZone(ZoneType.Battlefield).add(extra);
            }
        }
        var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Runnable createScene = () -> {
            try {
                Platform.setImplicitExit(false);
                var table = new TableScreen(150, 300, true);
                var scene = new Scene(table, 1440, 900);
                scene.getStylesheets().add(TableScreen.class.getResource("/forge/neo/neo.css").toExternalForm());
                forge.neo.platform.NeoFonts.apply(scene);
                var ui = new NeoMatchUI(NeoMatchUI.Mode.HUMAN, false) {
                    @Override public int actionableStrength(forge.game.card.CardView card) {
                        if (failProjection) throw new IllegalStateException("Injected snapshot failure");
                        return super.actionableStrength(card);
                    }
                };
                var binder = new NativeMatchBinder(table); binder.setMatchUi(ui); ui.setBinder(binder); ui.setTable(table);
                forge.neo.diy.DiyMatchControls.install(scene, table, ui);
                ui.setGameView(game.getView());
                table.setSelfBattlefield(cards.subList(0, 2)); table.setOpponentBattlefield(cards.subList(2, 4)); table.setHand(cards.subList(4, 6));
                var local = new forge.trackable.TrackableCollection<forge.game.player.PlayerView>();
                local.add(game.getPlayers().get(0).getView());
                ui.setGameController(local.get(0), (forge.interfaces.IGameController) Proxy.newProxyInstance(forge.interfaces.IGameController.class.getClassLoader(), new Class[]{forge.interfaces.IGameController.class}, (proxy,method,values)->{
                    if(method.getName().equals("cheat")) return ArenaBridgeCheck.cheats;
                    if(method.getReturnType()==boolean.class)return false;
                    if(method.getReturnType()==int.class)return 0;
                    if(method.getName().equals("toString"))return "Scene controller";
                    return null;
                }));
                ui.setCurrentPlayer(local.get(0));
                ui.openView(local);
                var loaded = forge.neo.diy.DiyCardImages.read(cards.get(4).getCurrentState().getImageKey());
                if (loaded == null) throw new AssertionError("Private image fixture was not loaded: " + imagePath);
                System.out.println("IMAGE_SERVICE_FIXTURE_OK " + loaded.getWidth() + "x" + loaded.getHeight());
                table.setPrompt("Native controller fixture — actual card scripts");
                // A native owner is required for ContextMenu; this is not a rendering test.
                var stage = new javafx.stage.Stage(javafx.stage.StageStyle.UTILITY);
                stage.setScene(scene);stage.setX(-12000);stage.setY(0);stage.show();
                var surface = (forge.arena.ArenaSurface) forge.arena.ArenaSurface.install(table,ui,scene);
                table.applyCss();table.layout();
                var checks = ArenaBridgeCheck.run(surface,ui,table);
                if (Boolean.getBoolean("forge.test.performance")) {
                    checks = checks.thenCompose(ignored -> ArenaPerformanceCheck.run(surface, table));
                }
                checks.whenComplete((ignored, failure) -> Platform.runLater(() -> {
                    try {
                        if (failure != null) throw new AssertionError("Bridge checks failed", failure);
                        table.refreshAll();
                        table.strike(cards.get(0), cards.get(2));
                        table.damagePop(cards.get(2), 3, true, false);
                        ArenaBridgeCheck.assertNoLegacyBoard(table);
                        System.out.println("ARENA_NATIVE_BRIDGE_OK legacyCards=0 rendererNotTested=true");
                    } catch (Throwable e) { error.set(e); e.printStackTrace(); }
                    finally {
                        stage.close();
                        surface.close();ui.shutdown();scene.setRoot(new javafx.scene.layout.Pane());done.countDown();
                    }
                }));
            } catch (Throwable e) { error.set(e); e.printStackTrace(); }
            finally { if (error.get() != null) done.countDown(); }
        };
        Platform.startup(createScene);
        if (!done.await(60, TimeUnit.SECONDS)) throw new AssertionError("Native bridge check timed out");
        Platform.exit();
        System.exit(error.get() == null ? 0 : 1);
    }

    static void runMatch(forge.StaticData data) throws Exception {
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread.setDefaultUncaughtExceptionHandler((t,e) -> { failure.compareAndSet(null,e); e.printStackTrace(); });
        var created = new CompletableFuture<NeoMatchUI>();
        Platform.startup(() -> {
            try {
                Platform.setImplicitExit(false);
                var table = new TableScreen(150,300,true);
                var scene = new Scene(table,1440,900);
                scene.getStylesheets().add(TableScreen.class.getResource("/forge/neo/neo.css").toExternalForm());
                var ui = new NeoMatchUI(NeoMatchUI.Mode.AUTO_PLAY,true) {
                    @Override public void afterGameFailure(String message) { failure.set(new AssertionError(message)); }
                };
                var binder = new NativeMatchBinder(table); binder.setMatchUi(ui); ui.setBinder(binder); ui.setTable(table);
                forge.arena.ArenaSurface.install(table,ui,scene);
                created.complete(ui);
            } catch(Throwable e) { created.completeExceptionally(e); }
        });
        var ui=created.get(10,TimeUnit.SECONDS);
        var prefs=forge.model.FModel.getPreferences();
        prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref.UI_ENABLE_SOUNDS,false);
        prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref.UI_ENABLE_MUSIC,false);
        prefs.setPref(forge.localinstance.properties.ForgePreferences.FPref.PLAYER_NAME,"Neo Test");
        forge.ai.AiProfileUtil.loadAllProfiles(APP+"res/ai/");
        var deck=new forge.deck.Deck("Integration");
        deck.getMain().add(data.getCommonCards().getCard("Mountain"),24);
        deck.getMain().add(data.getCommonCards().getCard("Lightning Bolt"),36);
        var human=new forge.game.player.RegisteredPlayer(deck).setPlayer(new forge.player.LobbyPlayerHuman("You")); human.setStartingLife(6);
        var ai=new forge.game.player.RegisteredPlayer(deck).setPlayer(new forge.ai.LobbyPlayerAi("Opponent",null)); ai.setStartingLife(6);
        var match=new forge.gamemodes.match.HostedMatch();
        var rules=new forge.game.GameRules(forge.game.GameType.Constructed); rules.setGamesPerMatch(1);
        match.startMatch(rules,Set.of(),List.of(human,ai),human,ui);
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
        while (failure.get()==null && !ui.finishGameCalled() && System.nanoTime()<end) Thread.sleep(50);
        if (failure.get()!=null) throw new AssertionError("Real match failed",failure.get());
        if (!ui.finishGameCalled()) throw new AssertionError("Real match timed out at turn "+match.getGame().getPhaseHandler().getTurn());
        System.out.println("REAL_HOSTED_MATCH_OK finished=true returned=true");
        ui.shutdown(); Platform.exit(); System.exit(0);
    }
}
