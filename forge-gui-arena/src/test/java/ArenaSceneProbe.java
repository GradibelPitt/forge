import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.Proxy;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiBase;
import forge.neo.ui.TableScreen;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
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
    static boolean failProjection;
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
        System.out.println("CARD_CACHE="+ForgeConstants.CACHE_CARD_PICS_DIR);
        forge.ImageKeys.initializeDirs(ForgeConstants.CACHE_CARD_PICS_DIR, ForgeConstants.CACHE_CARD_PICS_SUBDIR,
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
        var embeddedPanel = new javafx.embed.swing.JFXPanel[1];
        var embeddedFrame = new javax.swing.JFrame[1];
        if (Boolean.getBoolean("forge.test.embedded")) {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                embeddedPanel[0] = new javafx.embed.swing.JFXPanel();
                embeddedFrame[0] = new javax.swing.JFrame("Arena rendering probe");
                embeddedFrame[0].setUndecorated(true);
                embeddedFrame[0].add(embeddedPanel[0]);
                embeddedFrame[0].setBounds(-12000, 0, 2560, 1440);
                embeddedFrame[0].setVisible(true);
            });
        }
        Runnable createScene = () -> {
            try {
                Platform.setImplicitExit(false);
                var table = new TableScreen(150, 300);
                var scene = new Scene(table, 1440, 900);
                scene.getStylesheets().add(TableScreen.class.getResource("/forge/neo/neo.css").toExternalForm());
                forge.neo.platform.NeoFonts.apply(scene);
                var ui = new NeoMatchUI(NeoMatchUI.Mode.HUMAN, false) {
                    @Override public int actionableStrength(forge.game.card.CardView card) {
                        if (failProjection) throw new IllegalStateException("Injected snapshot failure");
                        return super.actionableStrength(card);
                    }
                };
                var binder = new TableBinder(table); binder.setMatchUi(ui); ui.setBinder(binder); ui.setTable(table);
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
                if (loaded == null) throw new AssertionError("Existing Lightning Bolt card art was not loaded");
                System.out.println("LOCAL_CARD_ART_OK " + loaded.getWidth() + "x" + loaded.getHeight());
                forge.neo.diy.DiyCardImages.read(cards.get(5).getCurrentState().getImageKey());
                table.setPrompt("NeoForge original match UI — actual card scripts");
                var stage=embeddedPanel[0]==null?new javafx.stage.Stage(javafx.stage.StageStyle.UTILITY):null;
                if(stage!=null){stage.setScene(scene);stage.setX(-12000);stage.setY(0);stage.show();}
                else embeddedPanel[0].setScene(scene);
                var surface = (forge.arena.ArenaSurface) forge.arena.ArenaSurface.install(table,ui,scene);
                table.applyCss();table.layout();
                var bridgeChecks=Boolean.getBoolean("forge.test.performance") ? ArenaPerformanceCheck.run(surface,table) : ArenaBridgeCheck.run(surface,ui,table);
                var effects = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(250), event -> {
                    table.strike(cards.get(0), cards.get(2));
                    table.damagePop(cards.get(2), 3, true, false);
                }));
                effects.setCycleCount(javafx.animation.Timeline.INDEFINITE);
                if(embeddedPanel[0]!=null)effects.play();
                var delay = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(embeddedPanel[0]==null?9:30));
                delay.setOnFinished(ev -> {
                    try {
                        table.refreshAll(); table.applyCss(); table.layout();
                        for (var c : cards) {
                            String key=c.getCurrentState().getImageKey();
                            if (!forge.neo.card.CardImages.isCached(key)) throw new AssertionError("Missing rendered card art: "+key);
                        }
                        if (table.selfFieldNodes().size() != 2 || table.oppFieldNodes().size() != 2 || table.handNodes().size() != 2)
                            throw new AssertionError("Real player zones not bound: " + table.selfFieldNodes().size()+"/"+table.oppFieldNodes().size()+"/"+table.handNodes().size());
                        table.setCombatLinks(List.of(
                            new Object[]{cards.get(0), cards.get(2), forge.neo.ui.CombatOverlay.Kind.ATTACK},
                            new Object[]{cards.get(2), cards.get(0), forge.neo.ui.CombatOverlay.Kind.BLOCK},
                            new Object[]{cards.get(4), cards.get(2), forge.neo.ui.CombatOverlay.Kind.TARGET}));
                        if (table.combatLinkCount() != 3) throw new AssertionError("Missing combat links");
                        table.strike(cards.get(0), cards.get(2));
                        table.damagePop(cards.get(2), 3, true, false);
                        var impact = TableScreen.class.getDeclaredField("impactLayer"); impact.setAccessible(true);
                        if (((forge.neo.ui.ImpactLayer)impact.get(table)).liveCount() == 0) throw new AssertionError("No impact animation");
                        if (javafx.stage.Window.getWindows().size()!=1) throw new AssertionError("Unexpected window");
                        table.redrawCombat();
                        if(!surface.webView().isVisible()||surface.webView().getWidth()<1000)throw new AssertionError("Browser surface hidden or not sized: "+surface.webView().isVisible()+" "+surface.webView().getWidth());
                        String text = String.valueOf(surface.webView().getEngine().executeScript("document.body.innerText"));
                        System.out.println("WEB_TEXT="+text);
                        if (!text.contains("Forge DIY") || !text.contains("巨龙之魂")) throw new AssertionError("React board not bound: "+text);
                        Object images = surface.webView().getEngine().executeScript("Array.from(document.querySelectorAll('.forge-hand img')).every(i=>i.complete&&i.naturalWidth>0)");
                        if (!Boolean.TRUE.equals(images)) throw new AssertionError("Local card images did not render");
                        bridgeChecks.join();
                        System.out.println("REACT_DOM_AND_DIY_IMAGES_OK");
                        ImageIO.write(SwingFXUtils.fromFXImage(scene.snapshot(null), null), "png", Path.of(args[0], "mtgapro-scene.png").toFile());
                        System.out.println("ORIGINAL_NEO_SCENE_OK cards=" + cards.size() + " arrows=3 impacts=active");
                    } catch (Throwable e) { error.set(e); e.printStackTrace(); }
                    finally {
                        effects.stop();
                        if(stage!=null)stage.close();
                        else { embeddedPanel[0].setScene(null);javax.swing.SwingUtilities.invokeLater(embeddedFrame[0]::dispose); }
                        surface.close();ui.shutdown();scene.setRoot(new javafx.scene.layout.Pane());done.countDown();
                    }
                });
                delay.play();
            } catch (Throwable e) { error.set(e); e.printStackTrace(); }
            finally { if (error.get() != null) done.countDown(); }
        };
        if(embeddedPanel[0]==null)Platform.startup(createScene);else Platform.runLater(createScene);
        if (!done.await(45, TimeUnit.SECONDS)) throw new AssertionError("Scene check timed out");
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
                var table = new TableScreen(150,300);
                var scene = new Scene(table,1440,900);
                scene.getStylesheets().add(TableScreen.class.getResource("/forge/neo/neo.css").toExternalForm());
                var ui = new NeoMatchUI(NeoMatchUI.Mode.AUTO_PLAY,true) {
                    @Override public void afterGameFailure(String message) { failure.set(new AssertionError(message)); }
                };
                var binder = new TableBinder(table); binder.setMatchUi(ui); ui.setBinder(binder); ui.setTable(table);
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
