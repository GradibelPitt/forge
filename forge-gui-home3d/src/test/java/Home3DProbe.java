import forge.screens.home.HearthHomeScene;
import forge.screens.home.HearthHomePanel;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javafx.application.*;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.*;
import javafx.scene.input.*;
import javafx.scene.layout.Pane;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;

public final class Home3DProbe {
    static HearthHomeScene menu;
    static AtomicInteger destination=new AtomicInteger(-1), sounds=new AtomicInteger();
    static void fx(Runnable work) throws Exception {
        FutureTask<Void> task=new FutureTask<>(work,null); Platform.runLater(task); task.get(30,TimeUnit.SECONDS);
    }
    static void activate(int i) {
        Node button=((SubScene)((Pane)menu.getScene().getRoot()).getChildren().get(0)).getRoot().lookup("#home-menu-"+i);
        if(button==null)throw new AssertionError("Missing 3D key "+i);
        button.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.ENTER,false,false,false,false));
    }
    static int geometry(Node n) {
        int count=n instanceof Shape3D?1:0;
        if(n instanceof MeshView v && v.getMesh() instanceof TriangleMesh m) {
            int points=m.getPoints().size()/3, tex=m.getTexCoords().size()/2;
            for(int i=0;i<m.getFaces().size();i+=2) {
                if(m.getFaces().get(i)<0||m.getFaces().get(i)>=points||m.getFaces().get(i+1)<0||m.getFaces().get(i+1)>=tex)
                    throw new AssertionError("Invalid mesh index");
            }
        }
        if(n instanceof SubScene s)count+=geometry(s.getRoot());
        if(n instanceof Parent p)for(Node child:p.getChildrenUnmodifiable())count+=geometry(child);
        return count;
    }
    public static void main(String[] args) throws Exception {
        try {
            CountDownLatch started=new CountDownLatch(1); Platform.startup(started::countDown); started.await(15,TimeUnit.SECONDS);
            fx(()->{
                Platform.setImplicitExit(false);
                if(!Platform.isSupported(ConditionalFeature.SCENE3D))throw new AssertionError("Hardware 3D unavailable");
                menu=new HearthHomeScene(new String[]{"单人游戏","多人联机","套牌编辑","游戏设置"},destination::set,sounds::incrementAndGet);
                if(geometry(menu.getScene().getRoot())<50)throw new AssertionError("Not real 3D geometry");
                try {
                    ImageIO.write(SwingFXUtils.fromFXImage(menu.getScene().snapshot(null),null),"png",new File(args[0],"lobby-3d-1440.png"));
                    SubScene sub=(SubScene)((Pane)menu.getScene().getRoot()).getChildren().get(0);
                    if(!sub.isDepthBuffer() || !(sub.getCamera() instanceof PerspectiveCamera))throw new AssertionError("Missing 3D projection");
                    Group cabinet=(Group)sub.getRoot().getChildrenUnmodifiable().get(0);
                    Rotate yaw=(Rotate)cabinet.getTransforms().get(1); yaw.setAngle(16);
                    ImageIO.write(SwingFXUtils.fromFXImage(menu.getScene().snapshot(null),null),"png",new File(args[0],"lobby-3d-angle.png"));
                    yaw.setAngle(-3);
                }catch(Exception e){throw new RuntimeException(e);}
            });
            for(int i=0;i<4;i++) {
                final int index=i; int before=sounds.get(); destination.set(-1);
                fx(()->{activate(index);activate((index+1)%4);if(destination.get()!=-1)throw new AssertionError("No animation delay");});
                Thread.sleep(100);
                fx(()->{Node b=((SubScene)((Pane)menu.getScene().getRoot()).getChildren().get(0)).getRoot().lookup("#home-menu-"+index);if(b.getTranslateZ()==-100 && b.getRotate()==0)throw new AssertionError("No 3D motion");});
                Thread.sleep(420);
                fx(()->{if(destination.get()!=index || sounds.get()!=before+1)throw new AssertionError("Wrong route or double activation");});
            }
            destination.set(-1);fx(()->{activate(0);menu.setActive(false);});Thread.sleep(450);
            if(destination.get()!=-1)throw new AssertionError("Hidden scene navigated");
            fx(()->{
                menu.setActive(true);
                for(int[] size:new int[][]{{800,600},{1024,640},{1920,1080}}) {
                    HearthHomeScene sized=new HearthHomeScene(new String[]{"单人游戏","多人联机","套牌编辑","游戏设置"},i->{},()->{});
                    Parent root=sized.getScene().getRoot();sized.getScene().setRoot(new Pane());Scene shot=new Scene(root,size[0],size[1],true);
                    try{
                        ImageIO.write(SwingFXUtils.fromFXImage(shot.snapshot(null),null),"png",new File(args[0],"lobby-3d-"+size[0]+".png"));
                        SubScene sub=(SubScene)((Pane)root).getChildren().get(0);
                        for(int i=0;i<4;i++) {
                            Node key=sub.getRoot().lookup("#home-menu-"+i);
                            var bounds=key.localToScene(key.getBoundsInLocal(),true);
                            if(bounds.getMinX()<0||bounds.getMinY()<0||bounds.getMaxX()>size[0]||bounds.getMaxY()>size[1])
                                throw new AssertionError("Clipped 3D key "+size[0]+" "+bounds);
                        }
                    }catch(Exception e){throw new RuntimeException(e);}
                }
            });
            HearthHomePanel[] bridge=new HearthHomePanel[1];
            javax.swing.JFrame[] frame=new javax.swing.JFrame[1];
            SwingUtilities.invokeAndWait(()->{
                bridge[0]=new HearthHomePanel(new String[]{"单人游戏","多人联机","套牌编辑","游戏设置"},i->{},()->{});
                bridge[0].setPreferredSize(new java.awt.Dimension(1024,640));
                frame[0]=new javax.swing.JFrame();frame[0].setContentPane(bridge[0]);frame[0].pack();
            });
            fx(()->{});
            SwingUtilities.invokeAndWait(()->frame[0].setContentPane(new javax.swing.JPanel()));fx(()->{});
            SwingUtilities.invokeAndWait(()->{frame[0].setContentPane(bridge[0]);frame[0].pack();});fx(()->{});
            SwingUtilities.invokeAndWait(()->frame[0].dispose());fx(()->{});
            for(java.awt.Window window:java.awt.Window.getWindows())if(window.isVisible())throw new AssertionError("Probe opened a visible window");
            System.out.println("HOME_3D_OK: hardware SCENE3D, valid meshes, perspective/depth, 4 routes, depth/rotation animation, sound dispatch, duplicate guard, cancellation, 4 sizes, Swing/FX detach/reattach without visible window");
            Platform.exit();System.exit(0);
        }catch(Throwable t){t.printStackTrace();System.exit(1);}
    }
}
