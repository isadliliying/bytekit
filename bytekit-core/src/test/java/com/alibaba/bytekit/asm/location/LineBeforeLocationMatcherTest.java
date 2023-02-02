package com.alibaba.bytekit.asm.location;

import com.alibaba.bytekit.asm.MethodProcessor;
import com.alibaba.bytekit.asm.binding.Binding;
import com.alibaba.bytekit.asm.interceptor.InterceptorProcessor;
import com.alibaba.bytekit.asm.interceptor.annotation.InterceptorParserUtils;
import com.alibaba.bytekit.asm.interceptor.annotation.None;
import com.alibaba.bytekit.utils.*;
import com.alibaba.deps.org.objectweb.asm.Opcodes;
import com.alibaba.deps.org.objectweb.asm.tree.*;
import org.benf.cfr.reader.util.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.util.DigestUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

public class LineBeforeLocationMatcherTest {

    @Rule
    public OutputCapture capture = new OutputCapture();

    public static class SpyLookInterceptor {

        public static void atLineBefore(@Binding.This Object target, @Binding.Class Class<?> clazz,
                                        @Binding.MethodInfo String methodInfo, @Binding.Args Object[] args,
                                        @Binding.LineBefore int line,
                                        @Binding.LocalVars Object[] vars,
                                        @Binding.LocalVarNames String[] varNames) {
            System.out.println("atLineBefore-args:" + clazz + methodInfo + target + args + line + vars + varNames);
        }

    }

    static class LineBeforeSample {

        public int testLineBefore() {
            int varInt = 123;
            long varLong = 1234L;
            byte varByte = 127;
            short varShort = 1223;
            boolean varBoolean = false;
            float varFloat = 0.8f;
            double varDouble = 0.3d;
            String varString = "str-wingli";
            int[] varIArray = new int[]{2, 3, 5};
            Object varObject = new Object();
            Object[] varObjectArray = new Object[]{new Object(), new Object()};

            double total = varInt + varLong + varShort;
            System.out.println(varString + total);

            return varInt * 3;
        }

    }

    @Test
    public void testMatched() throws Exception {
        beforeLine(46);
        assertThat(capture.toString()).contains("atLineBefore-args:");
    }

    @Test
    public void testUnMatched() throws Exception {
        beforeLine(1000);
        assertThat(capture.toString()).doesNotContain("atLineBefore-args:");
    }

    @Test
    public void testMethodExit() throws Exception {
        beforeLine(-1);
        assertThat(capture.toString()).contains("atLineBefore-args:");
    }

    @Test
    public void testFile() throws Exception {
        String path = "/Users/wingli/IdeaProjects/dev/study-minder-server/study-minder-ui/target/classes/com/seewo/study/minder/ui/MainApplication.class";
        //String path = "/Users/wingli/logs/arthas/classdump/sun.misc.Launcher$AppClassLoader-18b4aac2/com/seewo/study/minder/ui/MainApplication.class";
        ClassNode classNode1 = AsmUtils.toClassNode(FileUtils.readFileToByteArray(new File(path)));

        MethodNode methodNode1 = classNode1.methods.get(0);

        //String res = LookUtils.render(methodNode1);
        //System.out.println(res);

        //AbstractInsnNode node = LookUtils.findInsnNode(methodNode1, "03e3-3");
        //System.out.println(node);

        //System.out.println(Decompiler.decompile(path));
    }

    private List<Location> beforeLine(int beforeLine) throws Exception {
        LineBeforeLocationMatcher locationMatcher = new LineBeforeLocationMatcher(beforeLine);
        Method method = ReflectionUtils.findMethod(SpyLookInterceptor.class, "atLineBefore", null);
        InterceptorProcessor lineBeforeInterceptorProcessor = InterceptorParserUtils.createInterceptorProcessor(method, locationMatcher, true, None.class, Void.class);

        ClassNode classNode = AsmUtils.loadClass(LineBeforeSample.class);

        String methodMatcher = "testLineBefore";
        List<MethodNode> matchedMethods = new ArrayList<MethodNode>();
        for (MethodNode methodNode : classNode.methods) {
            if (MatchUtils.wildcardMatch(methodNode.name, methodMatcher)) {
                matchedMethods.add(methodNode);
            }
        }

        List<Location> locations = new LinkedList<Location>();
        for (MethodNode methodNode : matchedMethods) {
            MethodProcessor methodProcessor = new MethodProcessor(classNode, methodNode);
            locations.addAll(lineBeforeInterceptorProcessor.process(methodProcessor));
        }

        byte[] bytes = AsmUtils.toBytes(classNode);
        System.out.println(Decompiler.decompile(bytes));
        return locations;
    }

}
