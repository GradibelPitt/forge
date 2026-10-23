package forge.arena;

import forge.interfaces.IDevModeCheats;
import forge.neo.match.NeoMatchUI;
import forge.neo.ui.TableScreen;
import javafx.scene.control.*;
import javafx.geometry.Side;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Original controller actions, accessible in either match presentation. */
public final class MatchTools implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t=new Thread(r,"Forge match tools");t.setDaemon(true);return t; });
    private final Button button = new Button("工具 ▾");
    private final ContextMenu menu = new ContextMenu();
    private final NeoMatchUI ui;
    private final TableScreen table;
    public MatchTools(TableScreen table, NeoMatchUI ui, Runnable toggle) {
        this.ui=ui;this.table=table;
        button.setManaged(false);button.setFocusTraversable(false);
        button.setStyle("-fx-background-color: #25232b; -fx-text-fill: #f1d8a0; -fx-border-color: #aa8350; -fx-background-radius: 6; -fx-border-radius: 6;");
        button.layoutXProperty().bind(table.widthProperty().subtract(102));button.setLayoutY(38);button.resize(88,30);
        add("切换新／旧局内界面",toggle);
        add("游戏菜单",()->table.getMenuButton().fire());
        add("对局记录",()->table.getLogButton().fire());
        add("本回合统计",()->table.getTurnButton().fire());
        add("法术力池",()->ui.showManaPool(ui.localPlayerView()));
        menu.getItems().add(new SeparatorMenuItem());
        cheat("产生法术力",IDevModeCheats::generateMana);
        cheat("添加牌到手牌",IDevModeCheats::addCardToHand);
        cheat("添加牌到战场",IDevModeCheats::addCardToBattlefield);
        cheat("添加衍生物",IDevModeCheats::addTokenToBattlefield);
        cheat("添加牌到牌库",IDevModeCheats::addCardToLibrary);
        cheat("添加牌到坟墓场",IDevModeCheats::addCardToGraveyard);
        cheat("添加牌到放逐区",IDevModeCheats::addCardToExile);
        cheat("重复上次添加",IDevModeCheats::repeatLastAddition);
        cheat("施放咒语",IDevModeCheats::castASpell);
        cheat("从牌库找牌",IDevModeCheats::tutorForCard);
        cheat("设置生命",IDevModeCheats::setPlayerLife);
        cheat("添加指示物",IDevModeCheats::addCountersToPermanent);
        cheat("移除指示物",IDevModeCheats::removeCountersFromPermanent);
        cheat("横置永久物",IDevModeCheats::tapPermanents);
        cheat("重置永久物",IDevModeCheats::untapPermanents);
        cheat("放逐手牌",IDevModeCheats::exileCardsFromHand);
        cheat("放逐战场牌",IDevModeCheats::exileCardsFromBattlefield);
        cheat("移出游戏",IDevModeCheats::removeCardsFromGame);
        cheat("保存游戏状态",IDevModeCheats::dumpGameState);
        cheat("加载游戏状态",IDevModeCheats::setupGameState);
        cheat("赢得游戏",IDevModeCheats::winGame);
        check("无限出地",IDevModeCheats::setCanPlayUnlimitedLands);
        check("查看所有牌",IDevModeCheats::setViewAllCards);
        cheat("询问 AI",c->c.askAI(false));
        cheat("询问模拟 AI",c->c.askAI(true));
        cheat("指定时空骰",IDevModeCheats::riggedPlanarRoll);
        cheat("时空行走",IDevModeCheats::planeswalkTo);
        menu.setOnShowing(e->{for(MenuItem item:menu.getItems())if(Boolean.TRUE.equals(item.getUserData()))item.setDisable(!allowed());});
        button.setOnAction(e->{if(menu.isShowing())menu.hide();else menu.show(button,Side.BOTTOM,0,0);});
        table.getChildren().add(button);
    }
    private boolean allowed(){return !ui.isNetGame()&&ui.getGameController()!=null&&ui.getGameController().cheat()!=IDevModeCheats.NO_CHEAT;}
    private void add(String text,Runnable action){MenuItem item=new MenuItem(text);item.setOnAction(e->action.run());menu.getItems().add(item);}
    private void cheat(String text,Consumer<IDevModeCheats> action){MenuItem item=new MenuItem(text);item.setUserData(true);item.setOnAction(e->run(action));menu.getItems().add(item);}
    private void check(String text,java.util.function.BiConsumer<IDevModeCheats,Boolean> action){CheckMenuItem item=new CheckMenuItem(text);item.setUserData(true);item.setOnAction(e->run(c->action.accept(c,item.isSelected())));menu.getItems().add(item);}
    private void run(Consumer<IDevModeCheats> action){if(!allowed())return;worker.execute(()->{try{action.accept(ui.getGameController().cheat());}catch(Exception e){javafx.application.Platform.runLater(()->table.showInfo("开发者工具",e.getMessage()));}});}
    public void toFront(){button.toFront();}
    Button button(){return button;}
    public void close(){menu.hide();worker.shutdownNow();table.getChildren().remove(button);}
}
