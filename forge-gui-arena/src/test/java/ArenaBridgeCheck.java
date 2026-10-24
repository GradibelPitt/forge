import com.google.gson.*;
import forge.arena.ArenaSurface;
import forge.neo.match.NeoMatchUI;
import forge.neo.ui.TableScreen;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.control.*;

/** Native interaction and loopback protocol checks; does not certify screen presentation. */
public final class ArenaBridgeCheck {
    static final AtomicInteger toolCalls = new AtomicInteger();
    static final Map<String, AtomicInteger> cheatCalls = new ConcurrentHashMap<>();
    static final forge.interfaces.IDevModeCheats cheats = (forge.interfaces.IDevModeCheats)
            java.lang.reflect.Proxy.newProxyInstance(forge.interfaces.IDevModeCheats.class.getClassLoader(),
                    new Class[]{forge.interfaces.IDevModeCheats.class}, (proxy, method, values) -> {
                        toolCalls.incrementAndGet();
                        cheatCalls.computeIfAbsent(method.getName(), ignored -> new AtomicInteger()).incrementAndGet();
                        return null;
                    });

    static void fx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(8, TimeUnit.SECONDS);
    }

    static void refresh(ArenaSurface surface) throws Exception {
        fx(() -> {
            try {
                var method = ArenaSurface.class.getDeclaredMethod("refresh");
                method.setAccessible(true);
                method.invoke(surface);
            } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
        });
    }

    static final class Session {
        final String root, token;
        final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        Session(ArenaSurface surface) {
            URI url = URI.create(surface.url());
            root = "http://" + url.getAuthority(); token = url.getFragment();
        }
        HttpRequest.Builder request(String path) {
            return HttpRequest.newBuilder(URI.create(root + path)).timeout(Duration.ofSeconds(10)).header("X-Forge-Token", token);
        }
        HttpResponse<String> get(String path) throws Exception {
            return client.send(request(path).build(), HttpResponse.BodyHandlers.ofString());
        }
        JsonObject room() throws Exception {
            return JsonParser.parseString(get("/state").body()).getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("room");
        }
        JsonObject desktop() throws Exception { return JsonParser.parseString(get("/desktop/state").body()).getAsJsonObject(); }
        JsonObject command(String operation, JsonObject parameters) throws Exception {
            var room = room();
            var command = new JsonObject();
            command.addProperty("protocolVersion", 1);
            command.add("matchId", room.get("matchId")); command.add("playerId", room.get("playerId"));
            command.addProperty("commandId", UUID.randomUUID().toString());
            command.add("expectedRevision", room.get("revision"));
            command.addProperty("operation", operation); command.add("parameters", parameters);
            return command;
        }
        String send(JsonObject command) throws Exception {
            var envelope = new JsonObject(); envelope.addProperty("type", "command"); envelope.add("command", command);
            var reply = client.send(request("/command").POST(HttpRequest.BodyPublishers.ofString(envelope.toString())).build(), HttpResponse.BodyHandlers.ofString());
            if (reply.statusCode() != 200) throw new AssertionError("Command HTTP failure: " + reply.body());
            return JsonParser.parseString(reply.body()).getAsJsonObject().getAsJsonObject("receipt").get("status").getAsString();
        }
        void event(String type) throws Exception {
            var body = new JsonObject(); body.addProperty("type", type);
            var response = client.send(request("/desktop/event").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new AssertionError("Desktop event rejected");
        }
    }

    static CompletableFuture<Void> run(ArenaSurface surface, NeoMatchUI ui, TableScreen table) {
        return CompletableFuture.runAsync(() -> {
            try {
                refresh(surface);
                Session session = new Session(surface);
                for (String path : List.of("/state", "/events", "/desktop/state", "/desktop/event")) {
                    var denied = session.client.send(HttpRequest.newBuilder(URI.create(session.root + path)).build(), HttpResponse.BodyHandlers.ofString());
                    if (denied.statusCode() != 403) throw new AssertionError("Unauthenticated endpoint: " + path);
                }
                var foreign = session.client.send(session.request("/events").header("Origin", "https://example.invalid").build(), HttpResponse.BodyHandlers.ofString());
                if (foreign.statusCode() != 403) throw new AssertionError("Foreign origin can read state");
                if (session.get("/image?name=Unknown").statusCode() != 404) throw new AssertionError("Unprojected image exposed");
                JsonObject room = session.room();
                if (!room.has("nativeDialog") || !room.has("shortcuts")) throw new AssertionError("Desktop protocol missing");
                if (room.getAsJsonObject("snapshot").getAsJsonArray("players").get(1).getAsJsonObject().getAsJsonArray("hand").size() != 0)
                    throw new AssertionError("Opponent hand exposed");
                checkShortcutBindings(room);
                AtomicInteger calls = new AtomicInteger();
                fx(() -> { table.getActionBar().setButtons("Confirm", "Cancel", true, true); table.getActionBar().setOnOk(calls::incrementAndGet); });
                refresh(surface);
                var command = session.command("ok", new JsonObject());
                if (!session.send(command).equals("accepted") || !session.send(command).equals("accepted") || calls.get() != 1)
                    throw new AssertionError("Command deduplication failed");
                var stale = session.command("ok", new JsonObject()); stale.addProperty("expectedRevision", -1);
                if (!session.send(stale).equals("resync") || calls.get() != 1) throw new AssertionError("Stale command executed");
                for (String field : List.of("matchId", "playerId")) {
                    var wrong = session.command("ok", new JsonObject()); wrong.addProperty(field, "foreign");
                    if (!session.send(wrong).equals("rejected") || calls.get() != 1) throw new AssertionError("Wrong identity accepted: " + field);
                }
                var streamA = session.client.send(session.request("/events").build(), HttpResponse.BodyHandlers.ofInputStream());
                var streamB = session.client.send(session.request("/events").build(), HttpResponse.BodyHandlers.ofInputStream());
                try (var a = streamA.body(); var b = streamB.body()) {
                    if (streamA.statusCode() != 200 || streamB.statusCode() != 200 || a.read() != 'd' || b.read() != 'd')
                        throw new AssertionError("Push stream unavailable");
                    if (!session.send(session.command("ok", new JsonObject())).equals("accepted") || calls.get() != 2)
                        throw new AssertionError("Streams blocked command delivery");
                }
                checkNativeChoices(surface, table, session, calls);
                checkTools(surface, table, session);
                checkShortcuts(surface, table, session);
                checkDesktop(surface, table, session);
                String previous = session.get("/state").body();
                ArenaSceneProbe.failProjection = true;
                try {
                    refresh(surface);
                    if (!session.get("/state").body().equals(previous)) throw new AssertionError("Partial projection replaced complete state");
                } finally { ArenaSceneProbe.failProjection = false; }
                refresh(surface);
                fx(() -> assertNoLegacyBoard(table));
                System.out.println("BRIDGE_AUTH_IDENTITY_DEDUP_STALE_NATIVE_TOOLS_SHORTCUTS_DESKTOP_OK");
            } catch (Exception error) { throw new CompletionException(error); }
        });
    }

    private static void checkNativeChoices(ArenaSurface surface, TableScreen table, Session session, AtomicInteger calls) throws Exception {
        fx(() -> table.getOverlay().show(new javafx.scene.layout.VBox(new Label("Native card selection"))));
        refresh(surface);
        if (!session.room().get("nativeDialog").getAsBoolean() || !session.desktop().get("nativeDialog").getAsBoolean())
            throw new AssertionError("Native decision not published");
        int before = calls.get();
        if (!session.send(session.command("ok", new JsonObject())).equals("rejected") || calls.get() != before)
            throw new AssertionError("Board command bypassed native decision");
        fx(() -> table.getOverlay().hide()); refresh(surface);
        AtomicInteger acknowledged = new AtomicInteger();
        fx(() -> table.getPromptBanner().showAlert("Engine notice", "Continue", () -> { acknowledged.incrementAndGet(); table.getPromptBanner().hide(); }));
        refresh(surface);
        if (!session.desktop().get("nativeDialog").getAsBoolean()) throw new AssertionError("Native alert missing");
        fx(() -> { if (!table.getPromptBanner().acknowledge()) throw new AssertionError("Cannot acknowledge notice"); });
        refresh(surface);
        if (acknowledged.get() != 1 || session.desktop().get("nativeDialog").getAsBoolean()) throw new AssertionError("Native notice stuck");
        JsonObject beforeCurtain = session.room();
        var previousSeatCommand = session.command("ok", new JsonObject());
        fx(() -> table.showCurtain("Opponent")); refresh(surface);
        if (!session.desktop().get("nativeDialog").getAsBoolean()) throw new AssertionError("Hotseat curtain ignored");
        JsonObject curtain = session.room();
        if (!curtain.get("nativeDialog").getAsBoolean() || !curtain.get("status").getAsString().equals("starting")
                || (curtain.has("snapshot") && !curtain.get("snapshot").isJsonNull())
                || (curtain.has("imageVersions") && curtain.getAsJsonObject("imageVersions").size() != 0)
                || !curtain.get("playerId").getAsString().isEmpty())
            throw new AssertionError("Previous seat state exposed through curtain: " + curtain);
        if (session.get("/image?name=Lightning%20Bolt").statusCode() != 404)
            throw new AssertionError("Previous seat image remains accessible during curtain");
        fx(() -> {
            try {
                for (String name : List.of("cards", "images")) {
                    var field = ArenaSurface.class.getDeclaredField(name); field.setAccessible(true);
                    if (!((Map<?, ?>)field.get(surface)).isEmpty()) throw new AssertionError("Curtain retained " + name);
                }
            } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
        });
        if (!session.send(session.command("ok", new JsonObject())).equals("rejected")) throw new AssertionError("Hotseat curtain bypassed");
        previousSeatCommand.addProperty("expectedRevision", curtain.get("revision").getAsLong());
        if (!session.send(previousSeatCommand).equals("rejected")) throw new AssertionError("Previous seat can act behind curtain");
        fx(table::hideCurtain); refresh(surface);
        JsonObject revealed = session.room();
        if (revealed.get("nativeDialog").getAsBoolean() || !revealed.get("playerId").equals(beforeCurtain.get("playerId"))
                || !revealed.has("snapshot") || revealed.get("snapshot").isJsonNull()
                || revealed.getAsJsonObject("imageVersions").size() == 0)
            throw new AssertionError("Projection did not recover after hotseat reveal");
    }

    private static ContextMenu menu(ArenaSurface surface) {
        try {
            var field = ArenaSurface.class.getDeclaredField("tools"); field.setAccessible(true); var tools = field.get(surface);
            var menu = tools.getClass().getDeclaredField("menu"); menu.setAccessible(true); return (ContextMenu) menu.get(tools);
        } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    private static void checkTools(ArenaSurface surface, TableScreen table, Session session) throws Exception {
        if (!session.send(session.command("tools", new JsonObject())).equals("accepted")) throw new AssertionError("Tools protocol rejected");
        refresh(surface);
        if (!session.desktop().get("nativeDialog").getAsBoolean()) throw new AssertionError("Tools did not bring up native host");
        AtomicInteger expected = new AtomicInteger();
        fx(() -> {
            ContextMenu menu = menu(surface);
            if (!menu.isShowing()) throw new AssertionError("Tools menu not visible");
            menu.hide();
            for (MenuItem item : menu.getItems()) {
                if (item.getText() != null && (item.getText().startsWith("切换") || item.getText().contains("旧局内")))
                    throw new AssertionError("Legacy board switch retained");
                if (Boolean.TRUE.equals(item.getUserData())) { expected.incrementAndGet(); item.fire(); }
            }
        });
        for (int i = 0; i < 80 && toolCalls.get() < expected.get(); i++) Thread.sleep(25);
        if (expected.get() != 27 || toolCalls.get() != expected.get() || cheatCalls.getOrDefault("askAI", new AtomicInteger()).get() != 2)
            throw new AssertionError("Developer tools lost: expected=" + expected + " called=" + toolCalls);
        fx(() -> menu(surface).getItems().stream().filter(item -> "法术力池".equals(item.getText())).findFirst().orElseThrow().fire());
        refresh(surface);
        if (!session.desktop().get("nativeDialog").getAsBoolean()) throw new AssertionError("Mana pool has no native presentation");
        fx(() -> table.getMenuOverlay().hide()); refresh(surface);
        System.out.println("NATIVE_DEVELOPER_TOOLS_OK count=" + expected.get());
    }

    private static void checkShortcutBindings(JsonObject room) {
        var actions = new HashSet<String>();
        for (var item : room.getAsJsonArray("shortcuts")) actions.add(item.getAsJsonObject().get("action").getAsString());
        if (!actions.containsAll(List.of("PASS_PRIORITY", "PASS_TURN", "ZOOM_CARD", "SHOW_SHORTCUTS")))
            throw new AssertionError("Configured shortcut bindings missing");
    }

    private static void checkShortcuts(ArenaSurface surface, TableScreen table, Session session) throws Exception {
        for (String action : List.of("SHOW_SHORTCUTS", "ZOOM_CARD")) {
            JsonObject binding = null;
            for (JsonElement item : session.room().getAsJsonArray("shortcuts")) {
                var row = item.getAsJsonObject();
                if (row.get("action").getAsString().equals(action)) { binding = row; break; }
            }
            if (binding == null) throw new AssertionError("Shortcut unbound: " + action);
            var parameters = new JsonObject(); parameters.add("code", binding.get("key"));
            for (String modifier : List.of("ctrl", "shift", "alt")) parameters.add(modifier, binding.get(modifier));
            if (action.equals("ZOOM_CARD")) parameters.add("cardId", session.room().getAsJsonObject("snapshot").getAsJsonArray("players").get(0).getAsJsonObject().getAsJsonArray("hand").get(0).getAsJsonObject().get("id"));
            if (!session.send(session.command("shortcut", parameters)).equals("accepted")) throw new AssertionError("Shortcut rejected: " + action);
            refresh(surface);
            if (!session.desktop().get("nativeDialog").getAsBoolean()) throw new AssertionError("Shortcut did not open native UI: " + action);
            fx(() -> { table.hideZoom(); table.getMenuOverlay().hide(); }); refresh(surface);
        }
        var invalid = new JsonObject(); invalid.addProperty("code", "NOT_A_KEY");
        if (!session.send(session.command("shortcut", invalid)).equals("rejected")) throw new AssertionError("Invalid shortcut accepted");
        var escape = new JsonObject(); escape.addProperty("code", "ESCAPE");
        if (!session.send(session.command("shortcut", escape)).equals("accepted")) throw new AssertionError("Escape rejected");
        refresh(surface);
        if (!session.desktop().get("nativeDialog").getAsBoolean()) throw new AssertionError("Escape did not open pause menu");
        fx(() -> table.getMenuOverlay().hide()); refresh(surface);
    }

    private static void checkDesktop(ArenaSurface surface, TableScreen table, Session session) throws Exception {
        long before = session.desktop().get("sequence").getAsLong();
        session.event("ready"); refresh(surface);
        if (session.desktop().get("sequence").getAsLong() <= before) throw new AssertionError("Desktop ready event ignored");
        session.event("close-request"); refresh(surface);
        if (!session.desktop().get("nativeDialog").getAsBoolean()) throw new AssertionError("Desktop close bypassed quit dialog");
        fx(() -> table.getMenuOverlay().hide()); refresh(surface);
        session.event("renderer-failed"); refresh(surface);
        session.event("responsive"); refresh(surface);
        if (session.desktop().get("closed").getAsBoolean()) throw new AssertionError("Renderer failure abandoned game");
    }

    static void assertNoLegacyBoard(TableScreen table) {
        if (!table.isNativeOnly() || table.getSelfField().getScene() != null || table.getOpponentField().getScene() != null
                || !table.selfFieldNodes().isEmpty() || !table.oppFieldNodes().isEmpty() || !table.handNodes().isEmpty()
                || table.combatLinkCount() != 0 || table.impactCount() != 0)
            throw new AssertionError("Legacy board nodes or effects are still active");
    }
}
