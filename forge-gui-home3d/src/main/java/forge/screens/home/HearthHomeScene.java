package forge.screens.home;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.function.IntConsumer;
import javafx.animation.*;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

/** Depth-buffered geometry, perspective, lighting and animated 3D menu keys. */
public final class HearthHomeScene {
    private final Scene scene;
    private final Group cabinet = new Group();
    private final Group[] keys = new Group[4];
    private final Rotate yaw = new Rotate(-3, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(-2, Rotate.X_AXIS);
    private final IntConsumer navigate;
    private final Runnable sound;
    private Timeline clickAnimation;
    private boolean active = true;
    private int pending = -1;
    private final PhongMaterial bronze = material("#a47a40", "#bda47a", 64);
    private final PhongMaterial edge = material("#5d361c", "#c39a5e", 24);
    private final PhongMaterial dark = material("#241409", "#684224", 12);
    private final PhongMaterial wood = material("#ffffff", "#302014", 24);
    private final PhongMaterial blue = material("#14253f", "#4d7390", 12);

    public HearthHomeScene(String[] labels, IntConsumer navigate, Runnable sound) {
        this.navigate = navigate; this.sound = sound;
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: radial-gradient(center 50% 35%, radius 80%, #573820, #100c09);");
        scene = new Scene(root, 1440, 900, true);
        if (!Platform.isSupported(ConditionalFeature.SCENE3D)) {
            VBox fallback = new VBox(16); fallback.setAlignment(javafx.geometry.Pos.CENTER);
            Label unavailable = new Label("3D 大厅暂不可用，请检查显卡加速设置。");
            unavailable.setTextFill(Color.WHEAT); fallback.getChildren().add(unavailable);
            for (int i = 0; i < labels.length; i++) {
                final int index = i; Button button = new Button(labels[i]);
                button.setOnAction(e -> { sound.run(); navigate.accept(index); });
                fallback.getChildren().add(button);
            }
            root.getChildren().add(fallback); return;
        }
        wood.setDiffuseMap(woodTexture()); wood.setBumpMap(grainNormals());
        buildCabinet(labels); cabinet.getTransforms().addAll(pitch, yaw);
        Group world = new Group(cabinet);
        AmbientLight ambient = new AmbientLight(Color.color(.23, .23, .22));
        PointLight keyLight = new PointLight(Color.color(.80, .69, .51));
        keyLight.setTranslateX(-430); keyLight.setTranslateY(-480); keyLight.setTranslateZ(-530);
        PointLight rimLight = new PointLight(Color.color(.18, .28, .43));
        rimLight.setTranslateX(560); rimLight.setTranslateY(-80); rimLight.setTranslateZ(-170);
        PointLight fillLight = new PointLight(Color.color(.20, .14, .08));
        fillLight.setTranslateX(60); fillLight.setTranslateY(450); fillLight.setTranslateZ(-420);
        world.getChildren().addAll(ambient, keyLight, rimLight, fillLight);
        SubScene sub = new SubScene(world, 1440, 900, true, SceneAntialiasing.BALANCED);
        sub.setFill(Color.TRANSPARENT);
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(.5); camera.setFarClip(5000); camera.setFieldOfView(34); camera.setTranslateZ(-1390);
        sub.setCamera(camera);
        sub.widthProperty().bind(root.widthProperty()); sub.heightProperty().bind(root.heightProperty());
        root.widthProperty().addListener((o, old, value) -> fitCamera(camera, root));
        root.heightProperty().addListener((o, old, value) -> fitCamera(camera, root));
        root.getChildren().add(sub);
    }

    public Scene getScene() { return scene; }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            if (clickAnimation != null) { clickAnimation.stop(); }
            pending = -1;
            for (Group key : keys) { if (key != null) { key.setTranslateZ(-100); key.setRotate(0); } }
        }
    }

    private static void fitCamera(PerspectiveCamera camera, Pane root) {
        double aspect = Math.max(.25, root.getWidth() / Math.max(1, root.getHeight()));
        double distance = Math.max(390, 600 / aspect) / Math.tan(Math.toRadians(17)) + 130;
        camera.setTranslateZ(-distance);
    }

    private static PhongMaterial material(String diffuse, String specular, double power) {
        PhongMaterial m = new PhongMaterial(Color.web(diffuse));
        m.setSpecularColor(Color.web(specular)); m.setSpecularPower(power); return m;
    }

    private void buildCabinet(String[] labels) {
        Group banner = new Group();
        banner.getChildren().add(box(1160, 620, 16, 0, 20, 120, blue));
        banner.getChildren().add(box(1160, 9, 20, 0, -286, 112, bronze));
        banner.getChildren().add(box(1160, 9, 20, 0, 326, 112, bronze));
        banner.setRotationAxis(Rotate.Z_AXIS); banner.setRotate(-4); cabinet.getChildren().add(banner);
        cabinet.getChildren().add(beveled(804, 664, 94, 12, edge, 0, 0, 45));
        cabinet.getChildren().add(beveled(780, 640, 80, 14, wood, 0, 0, 20));
        cabinet.getChildren().add(box(732, 592, 26, 0, 0, -26, dark));
        cabinet.getChildren().add(box(350, 552, 36, -176, 0, -36, wood));
        cabinet.getChildren().add(box(350, 552, 36, 176, 0, -36, wood));
        frame(772, 632, 13, 40, -37, bronze); frame(718, 578, 10, 26, -53, edge); frame(696, 556, 5, 14, -62, bronze);
        for (int side : new int[]{-1, 1}) { for (int end : new int[]{-1, 1}) {
            cabinet.getChildren().add(beveled(136, 37, 34, 6, bronze, side * 325, end * 306, -56));
            cabinet.getChildren().add(beveled(37, 108, 34, 6, bronze, side * 374, end * 270, -56));
            Sphere rivet = new Sphere(7, 16); rivet.setMaterial(edge);
            rivet.setTranslateX(side * 373); rivet.setTranslateY(end * 305); rivet.setTranslateZ(-79);
            cabinet.getChildren().add(rivet);
            for (int line = 0; line < 3; line++) {
                MeshView scroll = ring(92 + line * 18, 9 - line * 2, 0, Math.PI / 2, bronze);
                scroll.getTransforms().add(new Scale(-side, -end, 1, 0, 0, 0));
                scroll.setTranslateX(side * 344); scroll.setTranslateY(end * 272); scroll.setTranslateZ(-72);
                cabinet.getChildren().add(scroll);
            }
        }}
        MeshView ringShadow = ring(232, 22, 0, 2 * Math.PI, dark);
        ringShadow.setTranslateY(22); ringShadow.setTranslateZ(-64); cabinet.getChildren().add(ringShadow);
        MeshView outerRing = ring(229, 13, 0, 2 * Math.PI, bronze);
        outerRing.setTranslateY(18); outerRing.setTranslateZ(-83); cabinet.getChildren().add(outerRing);
        MeshView innerRing = ring(209, 3, 0, 2 * Math.PI, bronze);
        innerRing.setTranslateY(18); innerRing.setTranslateZ(-79); cabinet.getChildren().add(innerRing);
        cabinet.getChildren().add(beveled(274, 55, 17, 8, edge, 0, -257, -77));
        cabinet.getChildren().add(textPlate("DIY FORGE", 252, 41, 0, -257, -87, true));
        for (int i = 0; i < keys.length; i++) {
            final int index = i; Group button = new Group();
            button.setId("home-menu-" + i); button.setAccessibleRole(AccessibleRole.BUTTON); button.setAccessibleText(labels[i]);
            button.setFocusTraversable(true); button.setCursor(Cursor.HAND);
            button.getChildren().add(beveled(358, 62, 25, 9, dark, 0, 3, 12));
            button.getChildren().add(beveled(356, 60, 24, 8, bronze, 0, 0, 0));
            button.getChildren().add(beveled(330, 39, 8, 4, edge, 0, -3, -15));
            button.getChildren().add(textPlate(labels[i], 322, 33, 0, -3, -20, false));
            button.setTranslateY(-109 + i * 79); button.setTranslateZ(-100); button.setRotationAxis(Rotate.X_AXIS);
            button.setOnMouseEntered(e -> { if (active && pending < 0) { button.setTranslateZ(-105); } });
            button.setOnMouseExited(e -> { if (pending < 0) { button.setTranslateZ(-100); } });
            button.setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY && active && pending < 0) {
                    button.requestFocus(); button.setTranslateZ(-91); button.setRotate(-2);
                }
            });
            button.setOnMouseReleased(e -> { if (pending < 0) { button.setTranslateZ(-100); button.setRotate(0); } });
            button.setOnMouseClicked(e -> { if (e.getButton() == MouseButton.PRIMARY) { activate(index); e.consume(); } });
            button.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) { activate(index); e.consume(); }
                else if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.UP) {
                    keys[(index + (e.getCode() == KeyCode.DOWN ? 1 : 3)) % 4].requestFocus(); e.consume();
                }
            });
            button.focusedProperty().addListener((o, old, focused) -> { if (pending < 0) { button.setTranslateZ(focused ? -106 : -100); } });
            keys[i] = button; cabinet.getChildren().add(button);
        }
        Sphere gem = new Sphere(14, 6); gem.setScaleY(1.25); gem.setMaterial(material("#2b95ba", "#d9f9ff", 80));
        gem.setTranslateY(213); gem.setTranslateZ(-95);
        MeshView socket = ring(20, 4, 0, Math.PI * 2, bronze); socket.setTranslateY(213); socket.setTranslateZ(-87);
        cabinet.getChildren().addAll(socket, gem);
    }

    private void activate(int index) {
        if (!active || pending >= 0) { return; }
        pending = index; sound.run(); Group button = keys[index];
        clickAnimation = new Timeline(keyframe(button, 0, -92, -3), keyframe(button, 80, -111, 7),
                keyframe(button, 170, -98, -4), keyframe(button, 260, -104, 2), keyframe(button, 380, -100, 0));
        clickAnimation.setOnFinished(e -> { pending = -1; if (active) { navigate.accept(index); } }); clickAnimation.play();
    }

    private static KeyFrame keyframe(Group button, int millis, double z, double angle) {
        return new KeyFrame(Duration.millis(millis), new KeyValue(button.translateZProperty(), z, Interpolator.EASE_BOTH),
                new KeyValue(button.rotateProperty(), angle, Interpolator.EASE_BOTH));
    }

    private void frame(double width, double height, double thickness, double depth, double z, PhongMaterial m) {
        cabinet.getChildren().addAll(box(width, thickness, depth, 0, -height / 2, z, m),
                box(width, thickness, depth, 0, height / 2, z, m),
                box(thickness, height, depth, -width / 2, 0, z, m), box(thickness, height, depth, width / 2, 0, z, m));
    }

    private static Box box(double w, double h, double d, double x, double y, double z, PhongMaterial m) {
        Box box = new Box(w, h, d); box.setMaterial(m); box.setTranslateX(x); box.setTranslateY(y); box.setTranslateZ(z); return box;
    }

    private static MeshView beveled(double w, double h, double depth, double bevel, PhongMaterial material,
                                    double x, double y, double z) {
        double[][] outline = {{-w/2+bevel,-h/2},{w/2-bevel,-h/2},{w/2,-h/2+bevel},{w/2,h/2-bevel},
                {w/2-bevel,h/2},{-w/2+bevel,h/2},{-w/2,h/2-bevel},{-w/2,-h/2+bevel}};
        TriangleMesh mesh = new TriangleMesh();
        for (int layer = 0; layer < 3; layer++) {
            double inset = layer == 0 ? Math.min(bevel*.55, 4) : 0;
            for (double[] p : outline) {
                double px = p[0] * (w - inset*2)/w, py = p[1]*(h-inset*2)/h;
                mesh.getPoints().addAll((float)px,(float)py,(float)(layer == 0 ? -depth/2 : layer == 1 ? -depth/2+bevel*.6 : depth/2));
                mesh.getTexCoords().addAll((float)(px/w+.5),(float)(py/h+.5));
            }
        }
        for (int i = 1; i < 7; i++) { face(mesh,0,i+1,i); face(mesh,16,16+i,17+i); }
        for (int layer = 0; layer < 2; layer++) { for (int i = 0; i < 8; i++) {
            int a=layer*8+i,b=layer*8+(i+1)%8,c=b+8,d=a+8; face(mesh,a,b,c); face(mesh,a,c,d);
        }}
        MeshView view = new MeshView(mesh); view.setMaterial(material); view.setCullFace(CullFace.NONE);
        view.setTranslateX(x); view.setTranslateY(y); view.setTranslateZ(z); return view;
    }

    private static void face(TriangleMesh mesh, int a, int b, int c) {
        mesh.getFaces().addAll(a,a,b,b,c,c); mesh.getFaceSmoothingGroups().addAll(0);
    }

    private static MeshView ring(double radius, double tube, double start, double length, PhongMaterial material) {
        int steps = Math.max(24, (int)(100 * length/(2*Math.PI))), sides = 12; TriangleMesh mesh = new TriangleMesh();
        for (int i = 0; i <= steps; i++) {
            double a = start + length*i/steps;
            for (int j = 0; j <= sides; j++) {
                double b = j*Math.PI*2/sides, r = radius + tube*Math.cos(b);
                mesh.getPoints().addAll((float)(Math.cos(a)*r),(float)(Math.sin(a)*r),(float)(tube*Math.sin(b)));
                mesh.getTexCoords().addAll((float)i/steps,(float)j/sides);
            }
        }
        for (int i=0;i<steps;i++) { for(int j=0;j<sides;j++) {
            int a=i*(sides+1)+j,b=a+sides+1;
            mesh.getFaces().addAll(a,a,b,b,a+1,a+1,a+1,a+1,b,b,b+1,b+1); mesh.getFaceSmoothingGroups().addAll(1,1);
        }}
        MeshView view = new MeshView(mesh); view.setMaterial(material); view.setCullFace(CullFace.NONE); return view;
    }

    private static Box textPlate(String text, int w, int h, double x, double y, double z, boolean title) {
        BufferedImage image = new BufferedImage(w*3,h*3,BufferedImage.TYPE_INT_RGB); Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new java.awt.GradientPaint(0,0,title?new java.awt.Color(98,61,28):new java.awt.Color(226,210,175),
                0,h*3,title?new java.awt.Color(49,29,14):new java.awt.Color(170,139,91))); g.fillRect(0,0,w*3,h*3);
        g.setFont(new Font(title?"Georgia":"Microsoft YaHei",Font.BOLD,title?78:66));
        int left=(w*3-g.getFontMetrics().stringWidth(text))/2, baseline=(h*3-g.getFontMetrics().getHeight())/2+g.getFontMetrics().getAscent();
        g.setColor(title?new java.awt.Color(20,10,3):new java.awt.Color(249,230,191)); g.drawString(text,left,baseline+3);
        g.setColor(title?new java.awt.Color(238,196,121):new java.awt.Color(48,29,13)); g.drawString(text,left,baseline);
        g.dispose(); PhongMaterial m=material("#ffffff",title?"#c1954c":"#8e7d60",20);
        m.setDiffuseMap(SwingFXUtils.toFXImage(image,null)); return box(w,h,1,x,y,z,m);
    }

    private static Image woodTexture() {
        BufferedImage image = new BufferedImage(768,768,BufferedImage.TYPE_INT_RGB); Random random=new Random(71);
        for(int y=0;y<768;y++) { for(int x=0;x<768;x++) {
            double bend = Math.sin(x*.006)*9 + Math.sin(x*.023+y*.003)*2;
            double wave = Math.sin((y+bend)*.35)+.35*Math.sin(y*1.7+x*.018);
            double v = wave*5 + random.nextDouble()*5 + 4*Math.sin(y*.032+x*.005);
            int r=(int)(83+v),g=(int)(49+v*.6),b=(int)(27+v*.3); image.setRGB(x,y,(r<<16)|(g<<8)|b);
        }}
        return SwingFXUtils.toFXImage(image,null);
    }

    private static Image grainNormals() {
        BufferedImage image=new BufferedImage(256,256,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<256;y++) { for(int x=0;x<256;x++) {
            int green=(int)(128+Math.cos(y*.7+Math.sin(x*.02))*7); image.setRGB(x,y,(128<<16)|(green<<8)|255);
        }}
        return SwingFXUtils.toFXImage(image,null);
    }
}
