package com.alibaba.bytekit.asm.location;

import com.alibaba.bytekit.asm.MethodProcessor;
import com.alibaba.bytekit.asm.binding.Binding;
import com.alibaba.bytekit.asm.interceptor.InterceptorProcessor;
import com.alibaba.bytekit.asm.interceptor.annotation.InterceptorParserUtils;
import com.alibaba.bytekit.asm.interceptor.annotation.None;
import com.alibaba.bytekit.utils.*;
import com.alibaba.deps.org.objectweb.asm.tree.ClassNode;
import com.alibaba.deps.org.objectweb.asm.tree.MethodNode;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.rule.OutputCapture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class LineBeforeLocationMatcherEnhanceTest {

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


    /**
     * 使用了Binding.LocalVars增强时，会报错
     * 反编译可以看到报了：Invisible function parameters on a non-constructor (or reads of uninitialised local variables).
     * 直接进行Retransform时，也会报错.
     * 源码是：/bytekit-core/src/test/resources/TestManager.kt
     * class文件是：/bytekit-core/src/test/resources/TestManager.class
     * 编译用的是 kotlin 1.4
     */
    @Test
    public void testLocalVarsEnhanceError() throws Exception {

        LineBeforeLocationMatcher locationMatcher = new LineBeforeLocationMatcher(7);
        Method method = ReflectionUtils.findMethod(SpyLookInterceptor.class, "atLineBefore", null);
        InterceptorProcessor lineBeforeInterceptorProcessor = InterceptorParserUtils.createInterceptorProcessor(method, locationMatcher, true, None.class, Void.class);

        String fileName = this.getClass().getClassLoader().getResource("TestManager.class").getFile();
        byte[] fileBytes = readFileBytes(fileName);
        ClassNode classNode = AsmUtils.toClassNode(fileBytes);

        MethodNode methodNode = AsmUtils.findFirstMethod(classNode.methods, "testKotlinMap");

        MethodProcessor methodProcessor = new MethodProcessor(classNode, methodNode);
        lineBeforeInterceptorProcessor.process(methodProcessor);

        byte[] bytes = AsmUtils.toBytes(classNode);

        System.out.println(Decompiler.decompile(bytes));
    }

    private byte[] readFileBytes(String fileName) throws IOException {
        File file = new File(fileName);
        long size = file.length();
        byte[] fileBytes = new byte[(int) size];
        FileInputStream fis = new FileInputStream(file);
        fis.read(fileBytes);
        fis.close();
        return fileBytes;
    }

}
