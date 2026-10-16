import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** A conservative distribution filter: no renaming, optimization, or gameplay-method rewriting. */
public final class PrototypeJar {
    private static final String ROOT = "dev/paul/simpleradiorepeaters/";
    private record Method(String owner, String name, String descriptor) {
        @Override public String toString() { return owner + "." + name + descriptor; }
    }
    private record Info(int access, Set<Method> calls) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Input JAR, output JAR, distribution fabric.mod.json, report JSON required");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (input.toAbsolutePath().equals(output.toAbsolutePath())) throw new IllegalArgumentException("Prototype must be a separate file");
        Map<String, byte[]> entries = new LinkedHashMap<>(); Map<String, Long> times = new HashMap<>();
        List<String> excluded = new ArrayList<>(); Map<Method, Info> methods = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(input.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                if (name.startsWith(ROOT + "gametest/")) { if (name.endsWith(".class")) excluded.add(name); continue; }
                if (name.matches("META-INF/[^/]+\\.(SF|RSA|DSA)")) throw new IllegalArgumentException("Signed input requires a separately signed build");
                byte[] data = zip.getInputStream(entry).readAllBytes(); entries.put(name, data); times.put(name, entry.getTime());
                if (name.endsWith(".class")) inspect(data, methods);
            }
        }
        Set<Method> removed = new LinkedHashSet<>();
        for (Method method : methods.keySet()) if (testOnlyOverload(method)) removed.add(method);
        if (removed.size() != 11) throw new IllegalStateException("Expected 11 audited test-only methods, found " + removed.size());
        // Also remove synthetic lambda bodies whose only callers were the removed test constructors.
        boolean changed;
        do {
            changed = false;
            for (var item : methods.entrySet()) {
                Method method = item.getKey(); int flags = item.getValue().access();
                if (removed.contains(method) || (flags & (Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC))
                        != (Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC)) continue;
                List<Method> callers = methods.entrySet().stream().filter(e -> e.getValue().calls().contains(method)).map(Map.Entry::getKey).toList();
                if (!callers.isEmpty() && removed.containsAll(callers)) { removed.add(method); changed = true; }
            }
        } while (changed);
        for (var method : methods.entrySet()) if (!removed.contains(method.getKey())) for (Method called : method.getValue().calls()) {
            if (removed.contains(called)) throw new IllegalStateException("Live method still calls removed helper: " + method.getKey() + " -> " + called);
            if (called.owner().startsWith(ROOT + "gametest/")) throw new IllegalStateException("Gameplay depends on a test: " + method.getKey());
        }
        List<String> modified = new ArrayList<>();
        for (var entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            ClassReader reader = new ClassReader(entry.getValue());
            if (removed.stream().noneMatch(method -> method.owner().equals(reader.getClassName()))) continue;
            ClassWriter writer = new ClassWriter(reader, 0);
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return removed.contains(new Method(reader.getClassName(), name, descriptor)) ? null
                            : super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            entry.setValue(writer.toByteArray()); modified.add(entry.getKey());
        }
        entries.put("fabric.mod.json", Files.readAllBytes(Path.of(args[2])));
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            zip.setLevel(9);
            for (var entry : entries.entrySet()) {
                ZipEntry item = new ZipEntry(entry.getKey()); item.setTime(times.getOrDefault(entry.getKey(), 0L));
                zip.putNextEntry(item); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("input_sha256", sha256(input)); report.put("output_sha256", sha256(output));
        report.put("input_bytes", Files.size(input)); report.put("output_bytes", Files.size(output));
        report.put("excluded_test_classes", excluded); report.put("removed_test_only_methods", removed.stream().map(Object::toString).toList());
        report.put("changed_classes", modified); report.put("renaming_or_obfuscation", false);
        report.put("gameplay_methods_rewritten", false); report.put("debug_information_stripped", false);
        report.put("remaining_code_has_no_calls_to_removed_methods", true);
        Files.createDirectories(Path.of(args[3]).toAbsolutePath().getParent()); Files.writeString(Path.of(args[3]), json(report) + "\n");
        System.out.println("Prototype: " + excluded.size() + " test classes and " + removed.size() + " unused helper methods removed; "
                + Files.size(input) + " -> " + Files.size(output) + " bytes. Own names and debug information retained.");
    }
    private static boolean testOnlyOverload(Method m) {
        int count = Type.getArgumentTypes(m.descriptor()).length;
        return switch (m.owner().substring(ROOT.length())) {
            case "radio/RelayGraph" -> (m.name().equals("<init>") && (count == 2 || count == 3))
                    || (m.name().equals("alternatives") && (count == 2 || count == 3));
            case "radio/RelayGraph$Reach" -> m.name().equals("<init>") && count == 2;
            case "network/TowerRecord" -> m.name().equals("<init>") && (count == 9 || count == 10);
            case "radio/NativeRadioAdapter" -> (m.name().equals("receiveAtTower") || m.name().equals("receiveAtReceiver")) && count == 6;
            case "radio/RadioLinkMath" -> m.name().equals("receive") && count == 9;
            case "network/TowerManager" -> m.name().equals("records") && count == 0;
            default -> false;
        };
    }
    private static void inspect(byte[] data, Map<Method, Info> methods) {
        ClassReader reader = new ClassReader(data);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                Method key = new Method(reader.getClassName(), name, descriptor); Set<Method> calls = new LinkedHashSet<>();
                methods.put(key, new Info(access, calls));
                return new MethodVisitor(Opcodes.ASM9) {
                    private void handle(Object value) {
                        if (value instanceof Handle h && h.getTag() >= Opcodes.H_INVOKEVIRTUAL)
                            calls.add(new Method(h.getOwner(), h.getName(), h.getDesc()));
                        if (value instanceof ConstantDynamic c) {
                            handle(c.getBootstrapMethod());
                            for (int i = 0; i < c.getBootstrapMethodArgumentCount(); i++) handle(c.getBootstrapMethodArgument(i));
                        }
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String called, String desc, boolean itf) { calls.add(new Method(owner, called, desc)); }
                    @Override public void visitInvokeDynamicInsn(String name, String desc, Handle bootstrap, Object... arguments) {
                        handle(bootstrap); for (Object argument : arguments) handle(argument);
                    }
                    @Override public void visitLdcInsn(Object value) { handle(value); }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
    private static String sha256(Path file) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))); }
    private static String json(Object value) {
        if (value instanceof Map<?, ?> map) return "{" + String.join(",", map.entrySet().stream().map(e -> json(e.getKey().toString()) + ":" + json(e.getValue())).toList()) + "}";
        if (value instanceof Collection<?> list) return "[" + String.join(",", list.stream().map(PrototypeJar::json).toList()) + "]";
        if (value instanceof String text) return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        return String.valueOf(value);
    }
}
