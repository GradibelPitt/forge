import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.tools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/** Fail-closed Java member and resolved dependency guard. Run with a Java 17 JDK. */
public final class DiyProtection {
    private static final List<String> MODULES = List.of("forge-core", "forge-game", "forge-ai", "forge-gui", "forge-gui-desktop");
    private record Member(String id, String syntax, TreePath path) { }
    private static final class Model implements AutoCloseable {
        final StandardJavaFileManager manager;
        final JavacTask task;
        final Trees trees;
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        final Map<String, Member> members = new TreeMap<>();
        final Map<String, String> files = new TreeMap<>();
        final IdentityHashMap<Tree, String> declarations = new IdentityHashMap<>();
        final Map<String, Set<String>> edges = new TreeMap<>();
        final Map<String, String> bindings = new TreeMap<>();
        Model(Path root, String classpath, boolean resolve) throws Exception {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) throw new IllegalStateException("A full Java 17 JDK is required");
            manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
            List<Path> sources = new ArrayList<>();
            for (String module : MODULES) {
                Path dir = root.resolve(module + "/src/main/java");
                if (Files.exists(dir)) try (var walk = Files.walk(dir)) {
                    sources.addAll(walk.filter(p -> p.toString().endsWith(".java")).sorted().toList());
                }
            }
            if (sources.isEmpty()) throw new IllegalStateException("No desktop Java sources: " + root);
            for (Path p : sources) files.put(relative(root, p), hash(Files.readString(p)));
            List<String> options = new ArrayList<>(List.of("-proc:none", "--release", "17", "-encoding", "UTF-8", "-implicit:none", "-Xlint:none"));
            if (resolve) options.addAll(List.of("-classpath", classpath, "-sourcepath", ""));
            task = (JavacTask) compiler.getTask(null, manager, diagnostics, options, null,
                    manager.getJavaFileObjectsFromPaths(sources));
            trees = Trees.instance(task);
            for (CompilationUnitTree unit : task.parse()) {
                String file = relative(root, Path.of(unit.getSourceFile().toUri()));
                TreePath unitPath = new TreePath(unit);
                for (Tree type : unit.getTypeDecls()) {
                    if (type instanceof ClassTree c) addType(file, unit.getPackageName() + "." + c.getSimpleName(), new TreePath(unitPath, c));
                }
            }
            checkErrors("parse");
            // Capture normalized syntax BEFORE javac inserts implicit super calls/default constructors.
            if (resolve) {
                task.analyze();
                checkErrors("type/variable binding");
                resolveBindings();
            }
        }
        void add(String id, String syntax, TreePath path) {
            if (members.putIfAbsent(id, new Member(id, hash(syntax), path)) != null)
                throw new IllegalStateException("Ambiguous Java member: " + id);
            if (!id.endsWith("#initialization-order")) declarations.put(path.getLeaf(), id);
        }
        void addType(String file, String owner, TreePath path) {
            ClassTree type = (ClassTree) path.getLeaf();
            String prefix = file + "|" + owner;
            add(prefix + "#type", type.getKind() + " " + type.getModifiers() + " " + type.getSimpleName()
                    + type.getTypeParameters() + " extends " + type.getExtendsClause() + " implements "
                    + type.getImplementsClause() + " permits " + type.getPermitsClause(), path);
            int initializer = 0;
            // Initializer order matters even when the individual initializer bodies stay unchanged.
            List<String> initializationOrder = new ArrayList<>();
            for (Tree tree : type.getMembers()) {
                TreePath child = new TreePath(path, tree);
                if (tree instanceof ClassTree nested) {
                    addType(file, owner + "." + nested.getSimpleName(), child);
                } else if (tree instanceof MethodTree method) {
                    String params = method.getParameters().stream().map(v -> v.getType().toString()).collect(Collectors.joining(","));
                    add(prefix + "#method:" + method.getName() + "(" + params + ")", method.toString(), child);
                } else if (tree instanceof VariableTree field) {
                    add(prefix + "#field:" + field.getName(), field.toString(), child);
                    if (field.getInitializer() != null) initializationOrder.add(field.getName().toString());
                } else if (tree instanceof BlockTree block) {
                    String id = prefix + "#initializer:" + initializer++;
                    add(id, block.toString(), child);
                    initializationOrder.add(id);
                } else if (tree.getKind() != Tree.Kind.EMPTY_STATEMENT) {
                    throw new IllegalStateException("Unsupported member syntax: " + prefix + ": " + tree.getKind());
                }
            }
            add(prefix + "#initialization-order", initializationOrder.toString(), path);
        }
        void checkErrors(String phase) {
            List<String> errors = diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(Object::toString).limit(12).toList();
            if (!errors.isEmpty()) throw new IllegalStateException("Unresolved " + phase + "; refusing update:\n" + String.join("\n", errors));
        }
        String symbol(Element e, Map<Element, String> locals) {
            if (e == null) throw new IllegalStateException("Missing resolved symbol");
            if (locals.containsKey(e)) return locals.get(e) + ":" + e.asType();
            TreePath declaration = trees.getPath(e);
            if (declaration != null && declarations.containsKey(declaration.getLeaf()))
                return declarations.get(declaration.getLeaf()) + ":" + e.asType();
            if (e instanceof TypeElement type) return type.getQualifiedName().toString();
            Element parent = e.getEnclosingElement();
            return (parent == null ? "" : symbol(parent, locals) + "#") + e.getKind() + ":" + e + ":" + e.asType();
        }
        void resolveBindings() {
            Map<String, List<ExecutableElement>> methods = new HashMap<>();
            for (Member m : members.values()) {
                Element e = trees.getElement(m.path);
                if (e instanceof ExecutableElement method && e.getKind() == ElementKind.METHOD)
                    methods.computeIfAbsent(e.getSimpleName().toString(), ignored -> new ArrayList<>()).add(method);
            }
            Map<ExecutableElement, List<ExecutableElement>> dispatch = new IdentityHashMap<>();
            for (Member member : members.values()) {
                if (member.id.endsWith("#initialization-order")) continue;
                Set<String> dependencies = new TreeSet<>();
                Map<Element, String> locals = new IdentityHashMap<>();
                List<String> resolved = new ArrayList<>();
                TreePathScanner<Void, Void> scanner = new TreePathScanner<>() {
                    int ordinal;
                    @Override public Void visitVariable(VariableTree node, Void unused) {
                        Element e = trees.getElement(getCurrentPath());
                        if (e != null && !e.getKind().isField()) locals.put(e, "local:" + ordinal++ + ":" + node.getName());
                        return super.visitVariable(node, unused);
                    }
                    private void reference() {
                        Element e = trees.getElement(getCurrentPath());
                        if (e == null) throw new IllegalStateException("Unresolved reference in " + member.id + ": " + getCurrentPath().getLeaf());
                        if (e.asType().getKind() == TypeKind.ERROR) throw new IllegalStateException("Error type in " + member.id);
                        resolved.add(symbol(e, locals));
                        if (e instanceof ExecutableElement method && e.getKind() == ElementKind.METHOD
                                && !e.getModifiers().contains(Modifier.STATIC)) {
                            List<ExecutableElement> overrides = dispatch.computeIfAbsent(method, target ->
                                    methods.getOrDefault(target.getSimpleName().toString(), List.of()).stream()
                                            .filter(other -> other != target && other.getEnclosingElement() instanceof TypeElement owner
                                                    && task.getElements().overrides(other, target, owner)).toList());
                            List<String> targets = new ArrayList<>();
                            for (ExecutableElement override : overrides) {
                                targets.add(symbol(override, locals));
                                TreePath implementation = trees.getPath(override);
                                if (implementation != null && declarations.containsKey(implementation.getLeaf()))
                                    dependencies.add(declarations.get(implementation.getLeaf()));
                            }
                            Collections.sort(targets);
                            resolved.add("virtual-dispatch:" + targets);
                        }
                        TreePath p = trees.getPath(e);
                        if (p != null) {
                            String id = declarations.get(p.getLeaf());
                            if (id != null && !id.equals(member.id)) {
                                dependencies.add(id);
                                String owner = id.substring(0, id.indexOf('#'));
                                dependencies.add(owner + "#type");
                                dependencies.add(owner + "#initialization-order");
                            }
                        }
                    }
                    @Override public Void visitIdentifier(IdentifierTree node, Void unused) { reference(); return super.visitIdentifier(node, unused); }
                    @Override public Void visitMemberSelect(MemberSelectTree node, Void unused) { reference(); return super.visitMemberSelect(node, unused); }
                    @Override public Void visitMemberReference(MemberReferenceTree node, Void unused) { reference(); return super.visitMemberReference(node, unused); }
                    @Override public Void visitNewClass(NewClassTree node, Void unused) { reference(); return super.visitNewClass(node, unused); }
                    @Override public Void visitClass(ClassTree node, Void unused) {
                        // A type header has its own gate; don't conflate it with all class bodies.
                        if (member.id.endsWith("#type") && node == member.path.getLeaf()) {
                            scan(node.getModifiers(), unused); scan(node.getTypeParameters(), unused);
                            scan(node.getExtendsClause(), unused); scan(node.getImplementsClause(), unused);
                            scan(node.getPermitsClause(), unused); return null;
                        }
                        return super.visitClass(node, unused);
                    }
                };
                scanner.scan(member.path, null);
                Element declaration = trees.getElement(member.path);
                if (declaration != null) resolved.add("declaration:" + symbol(declaration, locals));
                bindings.put(member.id, hash(String.join("\n", resolved)));
                edges.put(member.id, dependencies);
            }
        }
        @Override public void close() throws IOException { manager.close(); }
    }
    private static String relative(Path root, Path p) { return root.toAbsolutePath().normalize().relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/'); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String encoded(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String decoded(String value) { return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8); }
    private static void save(Path output, List<String> rows) throws IOException { Files.write(output, rows, StandardCharsets.UTF_8); }
    private static String row(String kind, String id, String value) { return kind + "\t" + encoded(id) + "\t" + encoded(value); }
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        if (mode.equals("catalog")) {
            try (Model baseline = new Model(Path.of(args[1]), "", false); Model official = new Model(Path.of(args[2]), "", false)) {
                List<String> rows = new ArrayList<>(List.of("DIY-PROTECTION-2"));
                Set<String> touchedTypes = new TreeSet<>();
                for (Member m : baseline.members.values()) {
                    Member old = official.members.get(m.id);
                    if (old == null || !old.syntax.equals(m.syntax)) {
                        rows.add(row("member", m.id, m.syntax));
                        touchedTypes.add(m.id.substring(0, m.id.indexOf('#')));
                    }
                }
                // Also preserve headers and field initialization order around every DIY member.
                for (String type : touchedTypes) for (String suffix : List.of("#type", "#initialization-order")) {
                    Member m = baseline.members.get(type + suffix);
                    if (m != null) rows.add(row("member", m.id, m.syntax));
                }
                for (var f : baseline.files.entrySet()) if (!official.files.containsKey(f.getKey())) rows.add(row("file", f.getKey(), f.getValue()));
                // Explicit user contract: keep the complete desktop card-name search path,
                // even if a future upstream baseline happens to acquire similarly named code.
                if ((args.length > 4 && args[4].equals("--require-card-name-search"))
                        || baseline.members.keySet().stream().anyMatch(id -> id.contains("forge.gui.ListChooser#"))) {
                    for (String file : List.of(
                            "forge-gui/src/main/java/forge/gui/CardNameSearchIndex.java",
                            "forge-gui/src/main/java/forge/gui/LatestSearchGeneration.java",
                            "forge-gui-desktop/src/main/java/forge/gui/ListChooser.java",
                            "forge-gui-desktop/src/main/java/forge/gui/GuiChoose.java",
                            "forge-game/src/main/java/forge/game/card/CardFaceView.java")) {
                        if (!baseline.files.containsKey(file)) throw new IllegalStateException("Required fuzzy search component missing: " + file);
                        for (Member m : baseline.members.values()) if (m.id.startsWith(file + "|")) rows.add(row("member", m.id, m.syntax));
                    }
                    for (String name : List.of("chooseCardName", "chooseCardNameFromCandidates", "chooseOptionalCardNameFace")) {
                        List<Member> found = baseline.members.values().stream().filter(m -> m.id.contains("forge.player.PlayerControllerHuman#method:" + name + "(")).toList();
                        if (found.isEmpty()) throw new IllegalStateException("Required fuzzy search entry missing: " + name);
                        for (Member m : found) rows.add(row("member", m.id, m.syntax));
                    }
                }
                // Missing official members can be intentional DIY removals. Don't resurrect them.
                for (String id : official.members.keySet()) if (!baseline.members.containsKey(id)) rows.add(row("absent", id, ""));
                if (rows.size() < 2) throw new IllegalStateException("Empty DIY protection catalog");
                save(Path.of(args[3]), rows.stream().distinct().toList());
                System.out.println("DIY_CATALOG=OK; rules=" + (rows.size() - 1));
            }
        } else if (mode.equals("verify")) {
            try (Model candidate = new Model(Path.of(args[1]), "", false)) {
                verifyCatalog(candidate, Path.of(args[2]));
            }
        } else if (mode.equals("bindings")) {
            try (Model baseline = new Model(Path.of(args[1]), args[3], true)) {
                List<String> rows = new ArrayList<>(List.of("DIY-BINDINGS-2"));
                for (Member m : baseline.members.values()) rows.add(row("syntax", m.id, m.syntax));
                for (var b : baseline.bindings.entrySet()) rows.add(row("binding", b.getKey(), b.getValue()));
                for (var e : baseline.edges.entrySet()) for (String to : e.getValue()) rows.add(row("edge", e.getKey(), to));
                save(Path.of(args[2]), rows);
                System.out.println("DIY_BINDINGS=OK; members=" + baseline.members.size());
            }
        } else if (mode.equals("verify-bindings")) {
            try (Model candidate = new Model(Path.of(args[1]), args[4], true)) {
                verifyCatalog(candidate, Path.of(args[2]));
                Map<String, String> syntax = new TreeMap<>(), bindings = new TreeMap<>();
                Map<String, Set<String>> dependencies = new TreeMap<>();
                for (String line : Files.readAllLines(Path.of(args[3]))) {
                    String[] cols = line.split("\t", -1); if (cols.length != 3) continue;
                    String id = decoded(cols[1]), value = decoded(cols[2]);
                    switch (cols[0]) {
                        case "syntax" -> syntax.put(id, value);
                        case "binding" -> bindings.put(id, value);
                        case "edge" -> dependencies.computeIfAbsent(id, ignored -> new TreeSet<>()).add(value);
                        default -> throw new IllegalStateException("Unknown binding record");
                    }
                }
                if (syntax.isEmpty() || bindings.isEmpty()) throw new IllegalStateException("Incomplete binding baseline");
                Set<String> protectedIds = new TreeSet<>();
                for (String line : Files.readAllLines(Path.of(args[2]))) {
                    String[] cols = line.split("\t", -1);
                    if (cols.length == 3 && cols[0].equals("member")) protectedIds.add(decoded(cols[1]));
                }
                // Calls in either direction are part of the contract. Recursive callee dependencies
                // are conservative: a changed shared helper requires review even if it still compiles.
                Set<String> guarded = new TreeSet<>(protectedIds);
                for (var e : dependencies.entrySet()) if (e.getValue().stream().anyMatch(protectedIds::contains)) guarded.add(e.getKey());
                for (var e : candidate.edges.entrySet()) if (e.getValue().stream().anyMatch(protectedIds::contains)) guarded.add(e.getKey());
                Deque<String> queue = new ArrayDeque<>(guarded);
                while (!queue.isEmpty()) for (String target : dependencies.getOrDefault(queue.removeFirst(), Set.of()))
                    if (guarded.add(target)) queue.addLast(target);
                List<String> failures = new ArrayList<>();
                for (String id : guarded) {
                    Member current = candidate.members.get(id);
                    if (current == null || !Objects.equals(syntax.get(id), current.syntax)
                            || !Objects.equals(bindings.get(id), candidate.bindings.get(id))) failures.add(id);
                }
                if (!failures.isEmpty()) throw new IllegalStateException("DIY dependency or variable binding changed; review required:\n" + String.join("\n", failures.stream().limit(30).toList()));
                System.out.println("DIY_BOUND_PROTECTION=OK; guarded=" + guarded.size());
            }
        } else throw new IllegalArgumentException("Unknown mode " + mode);
    }
    private static void verifyCatalog(Model candidate, Path catalog) throws IOException {
        List<String> lines = Files.readAllLines(catalog);
        if (lines.size() < 2 || !lines.get(0).equals("DIY-PROTECTION-2")) throw new IllegalStateException("Missing/unsupported protection catalog");
        List<String> failures = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] cols = line.split("\t", -1);
            if (cols.length != 3) throw new IllegalStateException("Malformed protection rule");
            String id = decoded(cols[1]), value = decoded(cols[2]);
            boolean valid = switch (cols[0]) {
                case "member" -> candidate.members.containsKey(id) && candidate.members.get(id).syntax.equals(value);
                case "file" -> Objects.equals(candidate.files.get(id), value);
                case "absent" -> !candidate.members.containsKey(id);
                default -> throw new IllegalStateException("Unknown protection rule " + cols[0]);
            };
            if (!valid) failures.add(cols[0] + ": " + id);
        }
        if (!failures.isEmpty()) throw new IllegalStateException("Protected DIY declaration/context changed:\n" + String.join("\n", failures.stream().limit(30).toList()));
        System.out.println("DIY_MEMBER_PROTECTION=OK; rules=" + (lines.size() - 1));
    }
}
