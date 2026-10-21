import java.lang.instrument.*;
import java.security.ProtectionDomain;
import jdk.internal.org.objectweb.asm.*;

/** Task-host fallback: unavailable AF_UNIX probes must report unsupported instead of failing on EPERM. */
public final class CaptureHostAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] bytes) {
                boolean unix = name.equals("sun/nio/ch/UnixDomainSockets");
                boolean loom = name.startsWith("net/fabricmc/loom/util/CurrentPlatform");
                if (!unix && !loom) return null;
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(reader, 0);
                boolean[] changed = {false};
                reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
                    @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                        if (descriptor.equals("()Z") && ((unix && method.equals("init")) || (loom && method.toLowerCase().contains("unix")))) {
                            changed[0] = true;
                            MethodVisitor mv = super.visitMethod(access & ~Opcodes.ACC_NATIVE & ~Opcodes.ACC_ABSTRACT,
                                    method, descriptor, signature, exceptions);
                            mv.visitCode(); mv.visitInsn(Opcodes.ICONST_0); mv.visitInsn(Opcodes.IRETURN);
                            mv.visitMaxs(1, (access & Opcodes.ACC_STATIC) == 0 ? 1 : 0); mv.visitEnd();
                            return null;
                        }
                        return super.visitMethod(access, method, descriptor, signature, exceptions);
                    }
                }, 0);
                if (changed[0]) {
                    System.err.println("Capture-host unsupported Unix socket probe: " + name);
                    return writer.toByteArray();
                }
                return null;
            }
        });
    }
}
