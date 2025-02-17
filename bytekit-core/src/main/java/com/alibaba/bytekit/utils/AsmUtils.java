package com.alibaba.bytekit.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.alibaba.deps.org.objectweb.asm.ClassReader;
import com.alibaba.deps.org.objectweb.asm.ClassVisitor;
import com.alibaba.deps.org.objectweb.asm.ClassWriter;
import com.alibaba.deps.org.objectweb.asm.Label;
import com.alibaba.deps.org.objectweb.asm.MethodVisitor;
import com.alibaba.deps.org.objectweb.asm.Opcodes;
import com.alibaba.deps.org.objectweb.asm.Type;
import com.alibaba.deps.org.objectweb.asm.commons.ClassRemapper;
import com.alibaba.deps.org.objectweb.asm.commons.JSRInlinerAdapter;
import com.alibaba.deps.org.objectweb.asm.commons.Remapper;
import com.alibaba.deps.org.objectweb.asm.tree.AbstractInsnNode;
import com.alibaba.deps.org.objectweb.asm.tree.ClassNode;
import com.alibaba.deps.org.objectweb.asm.tree.FieldNode;
import com.alibaba.deps.org.objectweb.asm.tree.LabelNode;
import com.alibaba.deps.org.objectweb.asm.tree.LocalVariableNode;
import com.alibaba.deps.org.objectweb.asm.tree.MethodInsnNode;
import com.alibaba.deps.org.objectweb.asm.tree.MethodNode;
import com.alibaba.deps.org.objectweb.asm.tree.TryCatchBlockNode;
import com.alibaba.deps.org.objectweb.asm.tree.TypeInsnNode;
import com.alibaba.deps.org.objectweb.asm.util.ASMifier;
import com.alibaba.deps.org.objectweb.asm.util.TraceClassVisitor;
import com.alibaba.bytekit.asm.ClassLoaderAwareClassWriter;

/**
 * 
 * @author hengyunabc
 *
 */
public class AsmUtils {
    static int MAJOR_VERSION = -1;

    public static ClassNode loadClass(Class<?> clazz) throws IOException {
        String resource = clazz.getName().replace('.', '/') + ".class";
        InputStream is = clazz.getClassLoader().getResourceAsStream(resource);
        ClassReader cr = new ClassReader(is);
        ClassNode classNode = new ClassNode();
        cr.accept(classNode, ClassReader.SKIP_FRAMES);
        return classNode;
    }

    public static ClassNode toClassNode(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode result = new ClassNode(Opcodes.ASM9);
        reader.accept(result, ClassReader.SKIP_FRAMES);
        return result;
    }

    public static ClassReader toClassNode(byte[] classBytes, ClassNode classNode) {
        ClassReader reader = new ClassReader(classBytes);
        reader.accept(classNode, ClassReader.SKIP_FRAMES);
        return reader;
    }

    /**
     * Generate class bytes from class node. <br>
     * <B>NOTE: must pass origin classReader for bytecode optimizations, avoiding
     * JVM metaspace OOM.</B>
     * 
     * @param classNode
     * @param classLoader
     * @param classReader origin class reader
     * @return
     */
    public static byte[] toBytes(ClassNode classNode, ClassLoader classLoader, ClassReader classReader) {
        int flags = ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS;
        ClassWriter writer = new ClassLoaderAwareClassWriter(classReader, flags, classLoader);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public static byte[] toBytes(ClassNode classNode) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public static byte[] renameClass(byte[] classBytes, final String newClassName) {
        final String internalName = newClassName.replace('.', '/');

        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(0);

        class RenameRemapper extends Remapper {
            private String className;

            @Override
            public String map(String typeName) {
                if (typeName.equals(className)) {
                    return internalName;
                }
                return super.map(typeName);
            }

            public void setClassName(String className) {
                this.className = className;
            }
        }

        final RenameRemapper renameRemapper = new RenameRemapper();
        ClassRemapper adapter = new ClassRemapper(writer, renameRemapper) {
            @Override
            public void visit(final int version, final int access, final String name, final String signature,
                    final String superName, final String[] interfaces) {
                renameRemapper.setClassName(name);
                super.visit(version, access, name, signature, superName, interfaces);
            }
        };
        reader.accept(adapter, ClassReader.EXPAND_FRAMES);
        writer.visitEnd();
        return writer.toByteArray();
    }

    public static void replaceMethod(ClassNode classNode, MethodNode methodNode) {
        for (int index = 0; index < classNode.methods.size(); ++index) {
            MethodNode tmp = classNode.methods.get(index);
            if (tmp.name.equals(methodNode.name) && tmp.desc.equals(methodNode.desc)) {
                classNode.methods.set(index, methodNode);
            }
        }
    }

    public static String toASMCode(byte[] bytecode) throws IOException {
        return toASMCode(bytecode, true);
    }

    public static String toASMCode(byte[] bytecode, boolean debug) throws IOException {
        int flags = ClassReader.SKIP_DEBUG;

        if (debug) {
            flags = 0;
        }

        ClassReader cr = new ClassReader(new ByteArrayInputStream(bytecode));
        StringWriter sw = new StringWriter();
        cr.accept(new TraceClassVisitor(null, new ASMifier(), new PrintWriter(sw)), flags);
        return sw.toString();
    }

    public static String toASMCode(ClassNode classNode) {
        StringWriter sw = new StringWriter();
        classNode.accept(new TraceClassVisitor(null, new ASMifier(), new PrintWriter(sw)));
        return sw.toString();
    }

    public static String toASMCode(MethodNode methodNode) {
        ClassNode classNode = new ClassNode();
        classNode.methods.add(methodNode);
        return toASMCode(classNode);
    }

    public static MethodNode newMethodNode(MethodNode source) {
        return new MethodNode(Opcodes.ASM9, source.access, source.name, source.desc, source.signature,
                source.exceptions.toArray(new String[source.exceptions.size()]));
    }

    public static MethodNode removeJSRInstructions(MethodNode subjectMethod) {
        MethodNode result = newMethodNode(subjectMethod);
        subjectMethod.accept(new JSRInlinerAdapter(result, subjectMethod.access, subjectMethod.name, subjectMethod.desc,
                subjectMethod.signature,
                subjectMethod.exceptions.toArray(new String[subjectMethod.exceptions.size()])));
        return result;
    }

    public static ClassNode removeJSRInstructions(ClassNode classNode) {
        ClassNode result = new ClassNode(Opcodes.ASM9);
        classNode.accept(new ClassVisitor(Opcodes.ASM9, result) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                    String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                return new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);
            }
        });
        return result;
    }

    public static MethodNode removeLineNumbers(MethodNode methodNode) {
        MethodNode result = newMethodNode(methodNode);
        methodNode.accept(new MethodVisitor(Opcodes.ASM9, result) {
            public void visitLineNumber(int line, Label start) {
            }
        });
        return result;
    }

    public static void removeLineNumbers(ClassNode classNode) {
        for (MethodNode methodNode : classNode.methods) {
            MethodNode result = newMethodNode(methodNode);
            methodNode.accept(new MethodVisitor(Opcodes.ASM9, result) {
                public void visitLineNumber(int line, Label start) {
                }
            });
            replaceMethod(classNode, methodNode);
        }
    }

    public static MethodNode findFirstMethod(Collection<MethodNode> methodNodes, String name) {
        for (MethodNode methodNode : methodNodes) {
            if (methodNode.name.equals(name)) {
                return methodNode;
            }
        }
        return null;
    }

    public static List<MethodNode> findMethods(Collection<MethodNode> methodNodes, String name) {
        List<MethodNode> result = new ArrayList<MethodNode>();
        for (MethodNode methodNode : methodNodes) {
            if (methodNode.name.equals(name)) {
                result.add(methodNode);
            }
        }
        return result;
    }

    public static MethodNode findMethod(Collection<MethodNode> methodNodes, MethodNode target) {
        return findMethod(methodNodes, target.name, target.desc);
    }

    public static MethodNode findMethod(Collection<MethodNode> methodNodes, String name, String desc) {
        for (MethodNode methodNode : methodNodes) {
            if (methodNode.name.equals(name) && methodNode.desc.equals(desc)) {
                return methodNode;
            }
        }
        return null;
    }

    public static AbstractInsnNode findInitConstructorInstruction(MethodNode methodNode) {
        int nested = 0;
        for (AbstractInsnNode insnNode = methodNode.instructions.getFirst(); insnNode != null; insnNode = insnNode
                .getNext()) {
            if (insnNode instanceof TypeInsnNode) {
                if (insnNode.getOpcode() == Opcodes.NEW) {
                    // new object().
                    nested++;
                }
            } else if (insnNode instanceof MethodInsnNode) {
                final MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                if (methodInsnNode.getOpcode() == Opcodes.INVOKESPECIAL && methodInsnNode.name.equals("<init>")) {
                    if (--nested < 0) {
                        // find this() or super().
                        return insnNode.getNext();
                    }
                }
            }
        }

        return null;
    }

    public static List<MethodInsnNode> findMethodInsnNodeWithPrefix(MethodNode methodNode, String prefix) {
        List<MethodInsnNode> result = new ArrayList<MethodInsnNode>();
        for (AbstractInsnNode insnNode = methodNode.instructions.getFirst(); insnNode != null; insnNode = insnNode
                .getNext()) {
            if (insnNode instanceof MethodInsnNode) {
                final MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                if (methodInsnNode.name.startsWith(prefix)) {
                    result.add(methodInsnNode);
                }
            }
        }
        return result;
    }

    public static List<MethodInsnNode> findMethodInsnNode(MethodNode methodNode, String owner, String name) {
        List<MethodInsnNode> result = new ArrayList<MethodInsnNode>();
        for (AbstractInsnNode insnNode = methodNode.instructions.getFirst(); insnNode != null; insnNode = insnNode
                .getNext()) {
            if (insnNode instanceof MethodInsnNode) {
                final MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                if (methodInsnNode.owner.equals(owner) && methodInsnNode.name.equals(name)) {
                    result.add(methodInsnNode);
                }
            }
        }
        return result;
    }

    public static List<MethodInsnNode> findMethodInsnNode(MethodNode methodNode, String owner, String name,
            String desc) {
        List<MethodInsnNode> result = new ArrayList<MethodInsnNode>();
        for (AbstractInsnNode insnNode = methodNode.instructions.getFirst(); insnNode != null; insnNode = insnNode
                .getNext()) {
            if (insnNode instanceof MethodInsnNode) {
                final MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                if (methodInsnNode.owner.equals(owner) && methodInsnNode.name.equals(name)
                        && methodInsnNode.desc.equals(desc)) {
                    result.add(methodInsnNode);
                }
            }
        }
        return result;
    }

    public static boolean containsMethodInsnNode(MethodNode methodNode, String owner, String name) {
        for (AbstractInsnNode insnNode = methodNode.instructions.getFirst(); insnNode != null; insnNode = insnNode
                .getNext()) {
            if (insnNode instanceof MethodInsnNode) {
                final MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                if (methodInsnNode.owner.equals(owner) && methodInsnNode.name.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isStatic(MethodNode methodNode) {
        return (methodNode.access & Opcodes.ACC_STATIC) != 0;
    }

    public static boolean isStatic(MethodInsnNode methodInsnNode) {
        return methodInsnNode.getOpcode() == Opcodes.INVOKESTATIC;
    }

    public static boolean isAbstract(MethodNode methodNode) {
        return (methodNode.access & Opcodes.ACC_ABSTRACT) != 0;
    }

    public static boolean isConstructor(MethodNode methodNode) {
        return methodNode.name != null && methodNode.name.equals("<init>");
    }

    public static boolean isNative(MethodNode methodNode) {
        return (methodNode.access & Opcodes.ACC_NATIVE) != 0;
    }

    public String[] getParameterNames(MethodNode methodNode) {
        Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
        if (argumentTypes.length == 0) {
            return new String[0];
        }

        final List<LocalVariableNode> localVariableNodes = methodNode.localVariables;
        int localVariableStartIndex = 1;
        if (isStatic(methodNode)) {
            // static method is none this.
            localVariableStartIndex = 0;
        }

        if (localVariableNodes == null || localVariableNodes.size() <= localVariableStartIndex
                || (argumentTypes.length + localVariableStartIndex) > localVariableNodes.size()) {
            // make simple argument names.
            final String[] names = new String[argumentTypes.length];
            for (int i = 0; i < argumentTypes.length; i++) {
                final String className = argumentTypes[i].getClassName();
                if (className != null) {
                    final int findIndex = className.lastIndexOf('.');
                    if (findIndex == -1) {
                        names[i] = className;
                    } else {
                        names[i] = className.substring(findIndex + 1);
                    }
                } else {
                    names[i] = argumentTypes[i].getDescriptor();
                }
            }
            return names;
        }

        // sort by index.
        Collections.sort(localVariableNodes, new Comparator<LocalVariableNode>() {

            @Override
            public int compare(LocalVariableNode o1, LocalVariableNode o2) {
                return o1.index - o2.index;
            }
        });
        String[] names = new String[argumentTypes.length];

        for (int i = 0; i < argumentTypes.length; i++) {
            final String name = localVariableNodes.get(localVariableStartIndex++).name;
            if (name != null) {
                names[i] = name;
            } else {
                names[i] = "";
            }
        }

        return names;
    }

    public static MethodNode copy(MethodNode source) {
        MethodNode result = newMethodNode(source);
        source.accept(result);
        return result;
    }

    public static ClassNode copy(ClassNode source) {
        ClassNode result = new ClassNode(Opcodes.ASM9);
        source.accept(new ClassVisitor(Opcodes.ASM9, result) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                    String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                return new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);
            }
        });
        return result;
    }

    public static String methodDeclaration(MethodInsnNode methodInsnNode) {
        StringBuilder sb = new StringBuilder(128);

        int opcode = methodInsnNode.getOpcode();
        if (opcode == Opcodes.INVOKESTATIC) {
            sb.append("static ");
        }
        Type methodType = Type.getMethodType(methodInsnNode.desc);
        Type ownerType = Type.getObjectType(methodInsnNode.owner);

        // skip constructor return type
        if (methodInsnNode.name.equals("<init>")) {
            sb.append(ownerType.getClassName());
        } else {
            sb.append(methodType.getReturnType().getClassName()).append(' ');
            sb.append(methodInsnNode.name);
        }

        sb.append('(');
        Type[] argumentTypes = methodType.getArgumentTypes();
        for (int i = 0; i < argumentTypes.length; ++i) {
            sb.append(argumentTypes[i].getClassName());
            if (i != argumentTypes.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(')');
        return sb.toString();

    }

    public static String methodDeclaration(Type owner, MethodNode methodNode) {
        int access = methodNode.access;
        StringBuilder sb = new StringBuilder(128);

//        int ACC_PUBLIC = 0x0001; // class, field, method
//        int ACC_PRIVATE = 0x0002; // class, field, method
//        int ACC_PROTECTED = 0x0004; // class, field, method
//        int ACC_STATIC = 0x0008; // field, method
//        int ACC_FINAL = 0x0010; // class, field, method, parameter
//        int ACC_SUPER = 0x0020; // class
//        int ACC_SYNCHRONIZED = 0x0020; // method
//        int ACC_OPEN = 0x0020; // module
//        int ACC_TRANSITIVE = 0x0020; // module requires
//        int ACC_VOLATILE = 0x0040; // field
//        int ACC_BRIDGE = 0x0040; // method
//        int ACC_STATIC_PHASE = 0x0040; // module requires
//        int ACC_VARARGS = 0x0080; // method
//        int ACC_TRANSIENT = 0x0080; // field
//        int ACC_NATIVE = 0x0100; // method
//        int ACC_INTERFACE = 0x0200; // class
//        int ACC_ABSTRACT = 0x0400; // class, method
//        int ACC_STRICT = 0x0800; // method
//        int ACC_SYNTHETIC = 0x1000; // class, field, method, parameter, module *
//        int ACC_ANNOTATION = 0x2000; // class
//        int ACC_ENUM = 0x4000; // class(?) field inner
//        int ACC_MANDATED = 0x8000; // parameter, module, module *
//        int ACC_MODULE = 0x8000; // class

        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            sb.append("public ");
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            sb.append("private ");
        }
        if ((access & Opcodes.ACC_PROTECTED) != 0) {
            sb.append("protected ");
        }
        if ((access & Opcodes.ACC_STATIC) != 0) {
            sb.append("static ");
        }

        if ((access & Opcodes.ACC_FINAL) != 0) {
            sb.append("final ");
        }
        if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            sb.append("synchronized ");
        }
        if ((access & Opcodes.ACC_NATIVE) != 0) {
            sb.append("native ");
        }
        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            sb.append("abstract ");
        }

        Type methodType = Type.getMethodType(methodNode.desc);

        // skip constructor return type
        if (methodNode.name.equals("<init>")) {
            sb.append(owner.getClassName());
        } else {
            sb.append(methodType.getReturnType().getClassName()).append(' ');
            sb.append(methodNode.name);
        }

        sb.append('(');
        Type[] argumentTypes = methodType.getArgumentTypes();
        for (int i = 0; i < argumentTypes.length; ++i) {
            sb.append(argumentTypes[i].getClassName());
            if (i != argumentTypes.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(')');
        if (methodNode.exceptions != null) {
            int exceptionSize = methodNode.exceptions.size();
            if (exceptionSize > 0) {
                sb.append(" throws");
                for (int i = 0; i < exceptionSize; ++i) {
                    sb.append(' ');
                    sb.append(Type.getObjectType(methodNode.exceptions.get(i)).getClassName());
                    if (i != exceptionSize - 1) {
                        sb.append(',');
                    }
                }
            }

        }

        return sb.toString();
    }

    public static FieldNode findField(List<FieldNode> fields, String name) {
        for (FieldNode field : fields) {
            if (field.name.equals(name)) {
                return field;
            }
        }
        return null;
    }

    public static void addField(ClassNode classNode, FieldNode fieldNode) {
        // TODO 检查是否有重复？
        classNode.fields.add(fieldNode);
    }

    public static void addMethod(ClassNode classNode, MethodNode methodNode) {
        classNode.methods.add(methodNode);
    }

    // TODO 是否真的 unique 了？
    public static String uniqueNameForMethod(String className, String methodName, String desc) {
        StringBuilder result = new StringBuilder(128);
        result.append(cleanClassName(className)).append('_').append(methodName);
        for (Type arg : Type.getMethodType(desc).getArgumentTypes()) {
            result.append('_').append(cleanClassName(arg.getClassName()));
        }
        return result.toString();
    }

    private static String cleanClassName(String className) {
        char[] charArray = className.toCharArray();
        int length = charArray.length;
        for (int i = 0; i < length; ++i) {
            switch (charArray[i]) {
            case '[':
            case ']':
            case '<':
            case '>':
            case ';':
            case '/':
            case '.':
                charArray[i] = '_';
                break;
            }
        }
        return new String(charArray);
    }

    /**
     * Java ClassFile versions (the minor version is stored in the 16 most
     * significant bits, and the major version in the 16 least significant bits).
     * 
     * @see com.alibaba.deps.org.objectweb.asm.Opcodes#V_PREVIEW
     * @param version
     * @return
     */
    public static int getMajorVersion(int version) {
        return 0x0000FFFF & version;
    }

    /**
     * 替换掉完整 version里的 major version
     * 
     * @param version
     * @param majorVersion
     * @return
     */
    public static int setMajorVersion(int version, int majorVersion) {
        return (version & 0xFFFF0000) | majorVersion;
    }

    public static String className(byte[] classBytes) {
        return new ClassReader(classBytes).getClassName();
    }

    public static boolean isEnhancerByCGLIB(String className) {
        return className.contains("$$EnhancerBySpringCGLIB$$") || className.contains("$$EnhancerByCGLIB$$");
    }

    private static boolean isBeforeNode(AbstractInsnNode aheadNode, AbstractInsnNode rearNode) {
        AbstractInsnNode node = rearNode;
        while (node != null) {
            if (node == aheadNode) {
                return true;
            }
            node = node.getPrevious();
        }
        return false;
    }

    public static void fixConstructorExceptionTable(MethodNode methodNode) {
        // fix try-catch block start position in constructor for SpringCGLIB
        if (isConstructor(methodNode) && methodNode.tryCatchBlocks.size() > 0) {
            AbstractInsnNode enterInsnNode = findInitConstructorInstruction(methodNode);
            if (enterInsnNode == null) {
                return;
            }
            LabelNode newStarLabel = new LabelNode();
            methodNode.instructions.insertBefore(enterInsnNode, newStarLabel);
            // if any try-catch block start before newStarLabel, then reset it
            for (TryCatchBlockNode tryCatchBlock : methodNode.tryCatchBlocks) {
                if (isBeforeNode(tryCatchBlock.start, newStarLabel)) {
                    tryCatchBlock.start = newStarLabel;
                }
            }
        }
    }

    public static void fixMajorVersion(ClassNode classNode) {
        // https://github.com/alibaba/arthas/issues/1223 , V1_5 的major version是49
        if (AsmUtils.getMajorVersion(classNode.version) < 49) {
            classNode.version = AsmUtils.setMajorVersion(classNode.version, 49);
        }
    }

    /**
     * 比如两份字节码的 major version，如果 from 的比 to 的要高，则设置 to 的 major version 为 from的 major
     * version
     * 
     * @param from
     * @param to
     */
    public static void updateMajorVersion(ClassNode from, ClassNode to) {
        int fromVersion = AsmUtils.getMajorVersion(from.version);
        int toVersion = AsmUtils.getMajorVersion(to.version);
        if (fromVersion > toVersion) {
            to.version = AsmUtils.setMajorVersion(to.version, fromVersion);
        }
    }

    /**
     * 如果成功获取，返回大于 0 的值
     * 
     * @return
     */
    public static int currentJvmMajorVersion() {
        if (MAJOR_VERSION > 0) {
            return MAJOR_VERSION;
        }
        if (MAJOR_VERSION < 0) {
            try {
                InputStream resourceAsStream = ClassLoader.getSystemClassLoader()
                        .getResourceAsStream(String.class.getName().replace('.', '/') + ".class");
                byte[] bytes = IOUtils.getBytes(resourceAsStream);
                ClassNode classNode = AsmUtils.toClassNode(bytes);
                MAJOR_VERSION = AsmUtils.getMajorVersion(classNode.version);
            } catch (Throwable e) {
                // ignore
                MAJOR_VERSION = 0;
            }
        }
        return MAJOR_VERSION;
    }

    /**
     * 检查当前的字节码版本是否适配
     * 
     * @param classNode
     * @return
     */
    public static boolean fitCurrentJvmMajorVersion(ClassNode classNode) {
        int currentJvmMajorVersion = currentJvmMajorVersion();
        if (currentJvmMajorVersion > 0 && currentJvmMajorVersion < AsmUtils.getMajorVersion(classNode.version)) {
            return false;
        }
        return true;
    }

    public static String internalClassName(String className) {
        // TODO 支持原始类型，数组等
        return className.replace('.', '/');
    }

    public static String internalClassName(Class<?> clazz) {
        // TODO 支持原始类型，数组等
        return clazz.getName().replace('.', '/');
    }
}
