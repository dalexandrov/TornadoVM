/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler.plugins;

import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.FMAX;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.FMIN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.POW;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.COS;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.EXP;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.FABS;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.LOG;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.SIN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation.MAX;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation.MIN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntUnaryIntrinsicNode.Operation.POPCOUNT;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.exceptions.Debug;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.PrintfNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.SlotsBaseAddressNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.TPrintfNode;
import uk.ac.manchester.tornado.runtime.directives.CompilerInternals;

public class OCLGraphBuilderPlugins {

    public static void registerInvocationPlugins(final Plugins ps, final InvocationPlugins plugins) {
        registerCompilerInstrinsicsPlugins(plugins);
        registerTornadoInstrinsicsPlugins(plugins);
        registerOpenCLBuiltinPlugins(plugins);

        TornadoMathPlugins.registerTornadoMathPlugins(plugins);
        VectorPlugins.registerPlugins(ps, plugins);
    }

    private static void registerCompilerInstrinsicsPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CompilerInternals.class);

        r.register0("getSlotsAddress", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new SlotsBaseAddressNode());
                return true;
            }
        });
    }

    private static void registerTornadoInstrinsicsPlugins(InvocationPlugins plugins) {

        final InvocationPlugin tprintfPlugin = new InvocationPlugin() {

            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {

                int idCount = 0;
                int index = 0;
                for (; index < 3; index++) {
                    ValueNode arg = args[index];
                    if (arg instanceof ConstantNode && arg.getStackKind().isObject()) {
                        break;
                    }
                    idCount++;
                }

                NewArrayNode newArrayNode = (NewArrayNode) args[index + 1];
                ConstantNode lengthNode = (ConstantNode) newArrayNode.dimension(0);
                int length = ((JavaConstant) lengthNode.getValue()).asInt();

                ValueNode[] actualArgs = new ValueNode[4 + length];
                for (int i = 0; i < idCount; i++) {
                    actualArgs[i] = args[i];
                }

                for (int i = idCount; i < 3; i++) {
                    actualArgs[i] = ConstantNode.forInt(0);
                }

                actualArgs[3] = args[index];

                int argIndex = 0;
                for (Node n : newArrayNode.usages()) {
                    if (n instanceof StoreIndexedNode) {
                        StoreIndexedNode storeNode = (StoreIndexedNode) n;
                        ValueNode value = storeNode.value();
                        if (value instanceof BoxNode) {
                            BoxNode box = (BoxNode) value;
                            value = box.getValue();
                            GraphUtil.unlinkFixedNode(box);
                            box.safeDelete();
                        }
                        actualArgs[4 + argIndex] = value;
                        argIndex++;
                    }

                }

                TPrintfNode printfNode = new TPrintfNode(actualArgs);

                b.add(b.append(printfNode));
                while (newArrayNode.hasUsages()) {
                    Node n = newArrayNode.usages().first();
                    // need to remove all nodes from the graph that operate on
                    // the new array,
                    // however, we cannot remove all inputs as they may be used
                    // by the currently
                    // unbuilt part of the graph. We also need to ensure that we
                    // do not leave any
                    // gaps inbetween fixed nodes
                    if (n instanceof FixedWithNextNode) {
                        GraphUtil.unlinkFixedNode((FixedWithNextNode) n);
                    }
                    n.clearInputs();
                    n.safeDelete();
                }

                GraphUtil.unlinkFixedNode(newArrayNode);
                newArrayNode.clearInputs();
                newArrayNode.safeDelete();

                return true;
            }
        };

        plugins.register(tprintfPlugin, Debug.class, "tprintf", String.class, Object[].class);
        plugins.register(tprintfPlugin, Debug.class, "tprintf", int.class, String.class, Object[].class);
        plugins.register(tprintfPlugin, Debug.class, "tprintf", int.class, int.class, String.class, Object[].class);
        plugins.register(tprintfPlugin, Debug.class, "tprintf", int.class, int.class, int.class, String.class, Object[].class);

        final InvocationPlugin printfPlugin = new InvocationPlugin() {

            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... args) {

                NewArrayNode newArrayNode = (NewArrayNode) args[1];
                ConstantNode lengthNode = (ConstantNode) newArrayNode.dimension(0);
                int length = ((JavaConstant) lengthNode.getValue()).asInt();

                ValueNode[] actualArgs = new ValueNode[length + 1];
                actualArgs[0] = args[0];

                int argIndex = 0;
                for (Node n : newArrayNode.usages()) {
                    if (n instanceof StoreIndexedNode) {
                        StoreIndexedNode storeNode = (StoreIndexedNode) n;
                        ValueNode value = storeNode.value();
                        if (value instanceof BoxNode) {
                            BoxNode box = (BoxNode) value;
                            value = box.getValue();
                            GraphUtil.unlinkFixedNode(box);
                            box.safeDelete();
                        }
                        actualArgs[argIndex + 1] = value;
                        argIndex++;
                    }

                }

                PrintfNode printfNode = new PrintfNode(actualArgs);
                b.add(b.append(printfNode));

                while (newArrayNode.hasUsages()) {
                    Node n = newArrayNode.usages().first();
                    // need to remove all nodes from the graph that operate on
                    // the new array,
                    // however, we cannot remove all inputs as they
                    // may be used by the currently unbuilt part of the graph.
                    // We also need to
                    // ensure that we do not leave any gaps inbetween fixed
                    // nodes
                    if (n instanceof FixedWithNextNode) {
                        GraphUtil.unlinkFixedNode((FixedWithNextNode) n);
                    }
                    n.clearInputs();
                    n.safeDelete();
                }

                GraphUtil.unlinkFixedNode(newArrayNode);
                newArrayNode.clearInputs();
                newArrayNode.safeDelete();
                return true;
            }
        };

        plugins.register(printfPlugin, Debug.class, "printf", String.class, Object[].class);

    }

    private static void registerOpenCLBuiltinPlugins(InvocationPlugins plugins) {

        Registration r = new Registration(plugins, java.lang.Math.class);
        registerOpenCLOverridesForType(r, Float.TYPE, JavaKind.Float);
        registerOpenCLOverridesForType(r, Double.TYPE, JavaKind.Double);
        registerOpenCLOverridesForType(r, Integer.TYPE, JavaKind.Int);
        registerOpenCLOverridesForType(r, Long.TYPE, JavaKind.Long);
        registerFPIntrinsics(r, Double.TYPE, JavaKind.Double);

        Registration longReg = new Registration(plugins, Long.class);
        longReg.register1("bitCount", Long.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(OCLIntUnaryIntrinsicNode.create(value, POPCOUNT, JavaKind.Long)));
                return true;
            }
        });

        Registration intReg = new Registration(plugins, Integer.class);
        intReg.register1("bitCount", Integer.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(OCLIntUnaryIntrinsicNode.create(value, POPCOUNT, JavaKind.Int)));
                return true;
            }
        });
    }

    private static void registerFPIntrinsics(Registration r, Class<?> type, JavaKind kind) {
        r.register2("pow", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(OCLFPBinaryIntrinsicNode.create(x, y, POW, kind)));
                return true;
            }
        });

        r.register1("sin", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, SIN, kind)));
                return true;
            }
        });

        r.register1("cos", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, COS, kind)));
                return true;
            }
        });

        r.register1("log", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, LOG, kind)));
                return true;
            }
        });

        r.register1("exp", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, EXP, kind)));
                return true;
            }
        });
    }

    private static void registerOpenCLOverridesForType(Registration r, Class<?> type, JavaKind kind) {
        r.register2("min", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(OCLFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                } else {
                    b.push(kind, b.append(OCLIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                }
                return true;
            }
        });

        r.register2("max", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(OCLFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                } else {
                    b.push(kind, b.append(OCLIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                }
                return true;
            }
        });

        r.register1("abs", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, FABS, kind)));
                }
                return true;
            }
        });

    }

    public static void registerClassInitializationPlugins(Plugins plugins) {

    }

    public static void registerNewInstancePlugins(Plugins plugins) {
        plugins.appendNodePlugin(new OCLVectorNodePlugin());
    }

    public static void registerParameterPlugins(Plugins plugins) {
        VectorPlugins.registerParameterPlugins(plugins);
    }
}
