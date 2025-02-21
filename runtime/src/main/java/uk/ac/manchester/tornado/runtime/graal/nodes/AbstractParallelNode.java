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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.runtime.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;

@NodeInfo
public abstract class AbstractParallelNode extends FloatingNode implements Comparable<AbstractParallelNode> {

    public static final NodeClass<AbstractParallelNode> TYPE = NodeClass.create(AbstractParallelNode.class);

    protected int index;
    @Input protected ValueNode value;

    protected AbstractParallelNode(NodeClass<? extends AbstractParallelNode> type, int index, ValueNode value) {
        super(type, StampFactory.forKind(JavaKind.Int));
        assert stamp != null;
        this.index = index;
        this.value = value;
    }

    public ValueNode value() {
        return value;
    }

    public int index() {
        return index;
    }

    @Override
    public int compareTo(AbstractParallelNode o) {
        return Integer.compare(index, o.index);
    }

}
