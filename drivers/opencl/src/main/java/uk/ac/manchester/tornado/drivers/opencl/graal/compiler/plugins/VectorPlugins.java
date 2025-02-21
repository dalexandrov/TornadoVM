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

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.runtime.common.Tornado.ENABLE_VECTORS;
import static uk.ac.manchester.tornado.runtime.common.Tornado.TORNADO_ENABLE_BIFS;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.type.annotations.Vector;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStampFactory;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.LoadIndexedVectorNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorAddNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorLoadElementNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorStoreElementProxyNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;

public final class VectorPlugins {

    public static final void registerPlugins(final Plugins ps, final InvocationPlugins plugins) {

        if (ENABLE_VECTORS) {
            ps.appendNodePlugin(new NodePlugin() {
                @Override
                public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                    OCLKind vectorKind = OCLKind.resolveToVectorKind(method.getDeclaringClass());
                    if (vectorKind == OCLKind.ILLEGAL) {
                        return false;
                    }
                    if (method.getName().equals("<init>")) {
                        final VectorValueNode vector = resolveReceiver(b, vectorKind, args[0]);
                        if (args.length > 1) {
                            int offset = (vector == args[0]) ? 1 : 0;
                            for (int i = offset; i < args.length; i++) {
                                vector.setElement(i - offset, args[i]);
                            }
                        } else {
                            // BUG check whether this should be <= 8
                            if (vectorKind.getVectorLength() < 8) {
                                vector.initialiseToDefaultValues(vector.graph());
                            }
                        }
                        return true;
                    }
                    return false;
                }

            });

            // Adding floats
            registerVectorPlugins(ps, plugins, OCLKind.FLOAT2, float[].class, float.class);
            registerVectorPlugins(ps, plugins, OCLKind.FLOAT3, float[].class, float.class);
            registerVectorPlugins(ps, plugins, OCLKind.FLOAT4, float[].class, float.class);
            registerVectorPlugins(ps, plugins, OCLKind.FLOAT8, float[].class, float.class);

            // Adding ints
            registerVectorPlugins(ps, plugins, OCLKind.INT2, int[].class, int.class);
            registerVectorPlugins(ps, plugins, OCLKind.INT3, int[].class, int.class);
            registerVectorPlugins(ps, plugins, OCLKind.INT4, int[].class, int.class);

            // Adding shorts
            registerVectorPlugins(ps, plugins, OCLKind.SHORT2, short[].class, short.class);

            // Adding char
            registerVectorPlugins(ps, plugins, OCLKind.CHAR3, byte[].class, byte.class);
            registerVectorPlugins(ps, plugins, OCLKind.CHAR4, byte[].class, byte.class);

            // Adding double
            registerVectorPlugins(ps, plugins, OCLKind.DOUBLE2, double[].class, double.class);
            registerVectorPlugins(ps, plugins, OCLKind.DOUBLE3, double[].class, double.class);
            registerVectorPlugins(ps, plugins, OCLKind.DOUBLE4, double[].class, double.class);
            registerVectorPlugins(ps, plugins, OCLKind.DOUBLE8, double[].class, double.class);

            /*
             * Geometric BIFS for floating point vectors
             */
            if (TORNADO_ENABLE_BIFS) {
                registerGeometricBIFS(plugins, OCLKind.FLOAT3, float[].class, float.class);
                registerGeometricBIFS(plugins, OCLKind.FLOAT4, float[].class, float.class);
            }
        }

    }

    private static VectorValueNode resolveReceiver(GraphBuilderContext b, OCLKind vectorKind, ValueNode thisObject) {
        VectorValueNode vector = null;
        if (thisObject instanceof PiNode) {
            thisObject = ((PiNode) thisObject).getOriginalNode();
        }
        if (thisObject instanceof VectorValueNode) {
            vector = (VectorValueNode) thisObject;
        }
        guarantee(vector != null, "unable to resolve vector");
        return vector;
    }

    private static VectorValueNode resolveReceiver(GraphBuilderContext b, OCLKind vectorKind, Receiver receiver) {
        ValueNode thisObject = receiver.get();
        return resolveReceiver(b, vectorKind, thisObject);
    }

    private static void registerVectorPlugins(final Plugins ps, final InvocationPlugins plugins, final OCLKind vectorKind, final Class<?> storageType, final Class<?> elementType) {

        final Class<?> declaringClass = vectorKind.getJavaClass();
        final JavaKind javaElementKind = vectorKind.getElementKind().asJavaKind();

        final Registration r = new Registration(plugins, declaringClass);

        final Class<?>[] argumentTypes = new Class<?>[vectorKind.getVectorLength()];
        for (int i = 0; i < vectorKind.getVectorLength(); i++) {
            argumentTypes[i] = elementType;
        }
        final InvocationPlugin initialiser = new InvocationPlugin() {
            @Override
            public boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode[] args) {
                final VectorValueNode vector = resolveReceiver(b, vectorKind, receiver);
                if (args.length > 0) {
                    int offset = (vector == args[0]) ? 1 : 0;

                    for (int i = offset; i < args.length; i++) {
                        vector.setElement(i - offset, args[i]);
                    }
                } else {
                    vector.initialiseToDefaultValues(vector.graph());
                }
                return true;
            }

            // @Override
            // public boolean defaultHandler(GraphBuilderContext b,
            // ResolvedJavaMethod targetMethod,
            // Receiver receiver, ValueNode... args) {
            // final VectorValueNode vector = resolveReceiver(b, vectorKind,
            // receiver);
            // if (args.length > 0) {
            //
            // int offset = (vector == args[0]) ? 1 : 0;
            //
            // for (int i = offset; i < args.length; i++) {
            // vector.setElement(i - offset, args[i]);
            // }
            // } else {
            // vector.initialiseToDefaultValues(vector.graph());
            // }
            //
            // return true;
            // }
        };

        r.register0("<init>", initialiser);

        r.register2("get", Receiver.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode laneId) {
                final VectorLoadElementNode loadElement = new VectorLoadElementNode(vectorKind.getElementKind(), receiver.get(), laneId);
                b.push(javaElementKind, b.append(loadElement));
                return true;
            }
        });

        r.register3("set", Receiver.class, int.class, elementType, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode laneId, ValueNode value) {
                final VectorStoreElementProxyNode store = new VectorStoreElementProxyNode(vectorKind.getElementKind(), receiver.get(), laneId, value);
                b.add(b.append(store));
                return true;
            }
        });

        r.register3("add", Receiver.class, declaringClass, declaringClass, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver reciever, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                OCLKind kind = OCLKind.fromResolvedJavaType(resolvedType);
                VectorAddNode addNode = new VectorAddNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.append(addNode));
                return true;
            }
        });

        r.register2("loadFromArray", storageType, int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver reciever, ValueNode array, ValueNode index) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                OCLKind kind = OCLKind.fromResolvedJavaType(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                // node needed to enforce the value of the nodes stamp
                LoadIndexedVectorNode indexedLoad = new LoadIndexedVectorNode(kind, array, index, elementKind);
                b.push(JavaKind.Object, b.append(indexedLoad));
                return true;
            }
        });

        r.register3("storeToArray", Receiver.class, storageType, int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver reciever, ValueNode array, ValueNode index) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);
                ValueNode value = reciever.get();
                OCLKind kind = OCLKind.fromResolvedJavaType(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                // No need to set stamp as it is inferred from the stamp of the incoming value
                StoreIndexedNode indexedStore = new StoreIndexedNode(array, index, null, null, elementKind, value);
                b.append(b.append(indexedStore));
                return true;
            }
        });
    }

    private static void registerGeometricBIFS(final InvocationPlugins plugins, final OCLKind vectorKind, final Class<?> storageType, final Class<?> elementType) {
        final Class<?> declaringClass = vectorKind.getJavaClass();
        final Registration r = new Registration(plugins, declaringClass);
        r.register2("dot", declaringClass, declaringClass, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input1, ValueNode input2) {
                TornadoInternalError.unimplemented();
                return true;
            }
        });

    }

    static void registerParameterPlugins(Plugins plugins) {
        plugins.appendParameterPlugin((GraphBuilderTool tool, int index, StampPair stampPair) -> {
            if (stampPair.getTrustedStamp() instanceof ObjectStamp) {
                ObjectStamp objStamp = (ObjectStamp) stampPair.getTrustedStamp();
                if (objStamp.type().getAnnotation(Vector.class) != null) {
                    OCLKind kind = OCLKind.fromResolvedJavaType(objStamp.type());
                    return new ParameterNode(index, StampPair.createSingle(OCLStampFactory.getStampFor(kind)));
                }
            }
            return null;
        });
    }
}
