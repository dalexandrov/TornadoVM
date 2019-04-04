/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.tasks;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.bytecode.Bytecodes;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.util.EconomicMap;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task10;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task15;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task6;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task7;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task8;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task9;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.domain.DomainTree;
import uk.ac.manchester.tornado.runtime.domain.IntDomain;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;

public class TaskUtils {

    public static CompilableTask scalaTask(String id, Object object, Object... args) {
        Class<?> type = object.getClass();
        Method entryPoint = null;
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals("apply") && !m.isSynthetic() && !m.isBridge()) {
                entryPoint = m;
                break;
            }
        }
        unimplemented("scala task");
        return createTask(null, id, entryPoint, object, false, args);
    }

    /**
     * When obtaining the method to be compiled it returns a lambda expression
     * that contains the invocation to the actual code. The actual code is an
     * INVOKE that is inside the apply method of the lambda. This method
     * searches for the nested method with the actual code to be compiled.
     * 
     * @param task
     *            code
     */
    public static Method resolveMethodHandle(Object task) {
        final Class<?> type = task.getClass();

        /*
         * task should implement one of the TaskX interfaces... ...so we look
         * for the apply function. Note: apply will perform some type casting
         * and then call the function we really want to use, so we need to
         * resolve the nested function.
         */
        Method entryPoint = null;
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals("apply")) {
                entryPoint = m;
            }
        }

        guarantee(entryPoint != null, "unable to find entry point");
        /*
         * Fortunately we can do a bit of JVMCI magic to resolve the function to
         * a Method.
         */
        final ResolvedJavaMethod resolvedMethod = TornadoCoreRuntime.getVMBackend().getMetaAccess().lookupJavaMethod(entryPoint);
        final ConstantPool cp = resolvedMethod.getConstantPool();
        final byte[] bc = resolvedMethod.getCode();

        for (int i = 0; i < bc.length; i++) {
            if (bc[i] == (byte) Bytecodes.INVOKESTATIC) {
                cp.loadReferencedType(bc[i + 2], Bytecodes.INVOKESTATIC);
                JavaMethod jm = cp.lookupMethod(bc[i + 2], Bytecodes.INVOKESTATIC);
                try {
                    Method toJavaMethod = jm.getClass().getDeclaredMethod("toJava");
                    toJavaMethod.setAccessible(true);
                    Method m = (Method) toJavaMethod.invoke(jm);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {

                    e.printStackTrace();
                }
                break;
            } else if (bc[i] == (byte) Bytecodes.INVOKEVIRTUAL) {
                cp.loadReferencedType(bc[i + 2], Bytecodes.INVOKEVIRTUAL);
                JavaMethod jm = cp.lookupMethod(bc[i + 2], Bytecodes.INVOKEVIRTUAL);
                switch (jm.getName()) {
                    case "floatValue":
                    case "doubleValue":
                    case "intValue":
                        continue;
                }
                try {
                    Method toJavaMethod = jm.getClass().getDeclaredMethod("toJava");
                    toJavaMethod.setAccessible(true);
                    Method m = (Method) toJavaMethod.invoke(jm);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
        shouldNotReachHere();
        return null;
    }

    public static <T1> CompilableTask createTask(Method method, ScheduleMetaData meta, String id, Task1<T1> code, T1 arg) {
        return createTask(meta, id, method, code, true, arg);
    }

    public static <T1, T2> CompilableTask createTask(Method method, ScheduleMetaData meta, String id, Task2<T1, T2> code, T1 arg1, T2 arg2) {
        return createTask(meta, id, method, code, true, arg1, arg2);
    }

    public static <T1, T2, T3> CompilableTask createTask(Method method, ScheduleMetaData meta, String id, Task3<T1, T2, T3> code, T1 arg1, T2 arg2, T3 arg3) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3);
    }

    public static <T1, T2, T3, T4> CompilableTask createTask(Method method, ScheduleMetaData meta, String id, Task4<T1, T2, T3, T4> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4);
    }

    public static <T1, T2, T3, T4, T5> CompilableTask createTask(Method method, ScheduleMetaData meta, String id, Task5<T1, T2, T3, T4, T5> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5);
    }

    public static <T1, T2, T3, T4, T5, T6> CompilableTask createTask(Method method, ScheduleMetaData meta, String id, Task6<T1, T2, T3, T4, T5, T6> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5,
            T6 arg6) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> CompilableTask createTask(Method method, ScheduleMetaData meta, String id, Task7<T1, T2, T3, T4, T5, T6, T7> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4,
            T5 arg5, T6 arg6, T7 arg7) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> CompilableTask createTask(Method method, ScheduleMetaData meta, String id, Task8<T1, T2, T3, T4, T5, T6, T7, T8> code, T1 arg1, T2 arg2, T3 arg3,
            T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> CompilableTask createTask(Method method, ScheduleMetaData meta, String id, Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> code, T1 arg1, T2 arg2,
            T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> CompilableTask createTask(Method method, ScheduleMetaData meta, String id, Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> code, T1 arg1,
            T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> CompilableTask createTask(Method method, ScheduleMetaData meta, String id,
            Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> code, T1 arg1, T2 arg2, T3 arg3, T4 arg4, T5 arg5, T6 arg6, T7 arg7, T8 arg8, T9 arg9, T10 arg10, T11 arg11,
            T12 arg12, T13 arg13, T14 arg14, T15 arg15) {
        return createTask(meta, id, method, code, true, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
    }

    public static Object[] extractCapturedVariables(Object code) {
        final Class<?> type = code.getClass();
        int count = 0;
        for (Field field : type.getDeclaredFields()) {
            if (!field.getType().getName().contains("$$Lambda$")) {
                count++;
            }
        }

        final Object[] cvs = new Object[count];
        int index = 0;
        for (Field field : type.getDeclaredFields()) {
            if (!field.getType().getName().contains("$$Lambda$")) {
                field.setAccessible(true);
                try {
                    cvs[index] = field.get(code);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    e.printStackTrace();
                }
                index++;
            }
        }
        return cvs;
    }

    public static PrebuiltTask createTask(ScheduleMetaData meta, String id, String entryPoint, String filename, Object[] args, Access[] accesses, TornadoDevice device, int[] dims) {
        final DomainTree domain = new DomainTree(dims.length);
        for (int i = 0; i < dims.length; i++) {
            domain.set(i, new IntDomain(0, 1, dims[i]));
        }

        return new PrebuiltTask(meta, id, entryPoint, filename, args, accesses, device, domain);
    }

    public static CompilableTask createTask(ScheduleMetaData meta, String id, Runnable runnable) {
        final Method method = resolveRunnableMethod(runnable);
        return createTask(meta, id, method, runnable, false);
    }

    private static CompilableTask createTask(ScheduleMetaData meta, String id, Method method, Object code, boolean extractCVs, Object... args) {
        final int numArgs;
        final Object[] cvs;

        if (extractCVs) {
            cvs = TaskUtils.extractCapturedVariables(code);
            numArgs = cvs.length + args.length;
        } else {
            cvs = null;
            numArgs = args.length;
        }

        final Object[] parameters = new Object[numArgs];
        int index = 0;
        if (extractCVs) {
            for (Object cv : cvs) {
                parameters[index] = cv;
                index++;
            }
        }

        for (Object arg : args) {
            parameters[index] = arg;
            index++;
        }
        return new CompilableTask(meta, id, method, parameters);
    }

    private static Method resolveRunnableMethod(Runnable runnable) {
        final Class<?> type = runnable.getClass();
        try {
            final Method method = type.getDeclaredMethod("run");
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static StructuredGraph buildHighLevelGraalGraph(Object taskInputCode) {
        Method methodToCompile = TaskUtils.resolveMethodHandle(taskInputCode);
        GraalJVMCICompiler graalCompiler = (GraalJVMCICompiler) JVMCI.getRuntime().getCompiler();
        RuntimeProvider capability = graalCompiler.getGraalRuntime().getCapability(RuntimeProvider.class);
        Backend backend = capability.getHostBackend();
        Providers providers = backend.getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaMethod resolvedJavaMethod = metaAccess.lookupJavaMethod(methodToCompile);
        CompilationIdentifier compilationIdentifier = backend.getCompilationIdentifier(resolvedJavaMethod);
        EconomicMap<OptionKey<?>, Object> opts = OptionValues.newOptionMap();
        opts.putAll(HotSpotGraalOptionValues.HOTSPOT_OPTIONS.getMap());
        OptionValues options = new OptionValues(opts);
        StructuredGraph graph = new StructuredGraph.Builder(options, AllowAssumptions.YES).method(resolvedJavaMethod).compilationId(compilationIdentifier).build();
        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(GraphBuilderConfiguration.getDefault(new Plugins(new InvocationPlugins()))));
        graphBuilderSuite.apply(graph, new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.ALL));
        return graph;
    }
}
