/*
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
 * Authors: Juan Fumero, Michalis Padimitriou
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLBinary;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkLocalArray;

@NodeInfo
public class LocalArrayNode extends FixedNode implements LIRLowerable, MarkLocalArray {

    public static final NodeClass<LocalArrayNode> TYPE = NodeClass.create(LocalArrayNode.class);

    @Input
    protected ConstantNode length;

    protected OCLKind elementKind;
    protected OCLArchitecture.OCLMemoryBase memoryRegister;
    protected ResolvedJavaType elemenType;
    protected OCLAssembler.OCLBinaryTemplate arrayTemplate;

    public LocalArrayNode(OCLArchitecture.OCLMemoryBase memoryRegister, ResolvedJavaType elementType, ConstantNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.elemenType = elementType;
        this.elementKind = OCLKind.fromResolvedJavaType(elementType);
        this.arrayTemplate = OCLKind.resolveTemplateType(elementType);
    }

    public OCLArchitecture.OCLMemoryBase getMemoryRegister() {
        return memoryRegister;
    }

    public ResolvedJavaType getElementType() {
        return elemenType;
    }

    public ConstantNode getLength() {
        return length;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        final Value lengthValue = gen.operand(length);

        LIRKind lirKind = LIRKind.value(gen.getLIRGeneratorTool().target().arch.getWordKind());
        final Variable variable = gen.getLIRGeneratorTool().newVariable(lirKind);
        final OCLBinary.Expr declaration = new OCLBinary.Expr(arrayTemplate, lirKind, variable, lengthValue);

        final OCLLIRStmt.ExprStmt expr = new OCLLIRStmt.ExprStmt(declaration);
        gen.getLIRGeneratorTool().append(expr);
        gen.setResult(this, variable);
    }
}
