/*
 * Copyright (c) 2018, APT Group, School of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler;

import static uk.ac.manchester.tornado.graal.TornadoLIRGenerator.trace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.lir.InstructionValueProcedure;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.SwitchNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.common.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLControlFlow;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLControlFlow.LoopConditionOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLControlFlow.LoopInitOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLControlFlow.LoopPostOp;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;

public class OCLCompilationResultBuilder extends CompilationResultBuilder {

    private static final boolean USE_EXPERIMENTAL_TRAVERSE = true;
    protected LIR lir;
    protected int currentBlockIndex;
    protected final Set<ResolvedJavaMethod> nonInlinedMethods;
    protected boolean isKernel;

    public OCLCompilationResultBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
            OCLCompilationResult compilationResult, OptionValues options) {
        super(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, compilationResult);
        nonInlinedMethods = new HashSet<>();
    }

    public OCLCompilationResult getResult() {
        return (OCLCompilationResult) compilationResult;
    }

    public void setKernel(boolean value) {
        isKernel = value;
    }

    public boolean isKernel() {
        return isKernel;
    }

    public OCLAssembler getAssembler() {
        return (OCLAssembler) asm;
    }

    public void addNonInlinedMethod(ResolvedJavaMethod method) {
        if (!nonInlinedMethods.contains(method)) {
            nonInlinedMethods.add(method);
        }
    }

    public Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return nonInlinedMethods;
    }

    /**
     * Emits code for {@code lir} in its {@linkplain LIR#codeEmittingOrder()
     * code emitting order}.
     */
    @Override
    public void emit(LIR lir) {
        assert this.lir == null;
        assert currentBlockIndex == 0;
        this.lir = lir;
        this.currentBlockIndex = 0;
        frameContext.enter(this);

        final ControlFlowGraph cfg = (ControlFlowGraph) lir.getControlFlowGraph();
        trace("Traversing CFG: ", cfg.graph.name);
        cfg.computePostdominators();
        traverseControlFlowGraph(cfg, new OCLBlockVisitor(this));

        trace("Finished traversing CFG");
        this.lir = null;
        this.currentBlockIndex = 0;

    }

    @Override
    public void finish() {
        int position = asm.position();
        compilationResult.setTargetCode(asm.close(true), position);

        // closeCompilationResult();
    }

    private String toString(Collection<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ ");
        for (Block b : blocks) {
            sb.append(b.getId()).append(" ");
        }
        sb.append("]");
        return sb.toString();
    }

    private String toString(Block[] blocks) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ ");
        for (Block b : blocks) {
            sb.append(b.getId()).append(" ");
        }
        sb.append("]");
        return sb.toString();
    }

    private void printBlock(Block b) {
        System.out.printf("Block %d:\n", b.getId());
        for (FixedNode node : b.getNodes()) {
            System.out.printf("  node: %s\n", node);
        }
        System.out.println();
    }

    private void patchDefaultCaseBlock(LIR lir, Block block) {
        final List<LIRInstruction> insns = lir.getLIRforBlock(block);
        if (insns.get(1) instanceof OCLControlFlow.CaseOp) {
            insns.remove(1);
            insns.remove(insns.size() - 1);
        }

        insns.add(1, new OCLControlFlow.DefaultCaseOp());
    }

    private void patchCaseBlock(LIR lir, Constant key, Block block) {
        final List<LIRInstruction> insns = lir.getLIRforBlock(block);
        insns.add(1, new OCLControlFlow.CaseOp(key));
        insns.add(new OCLControlFlow.CaseBreakOp());

    }

    private static boolean isIfBlock(Block block) {
        return block.getEndNode() instanceof IfNode;
    }

    private static boolean isSwitchBlock(Block block) {
        return block.getEndNode() instanceof SwitchNode;
    }

    private static SwitchNode getSwitchNode(Block block) {
        return (SwitchNode) block.getEndNode();
    }

    private static boolean isMergeBlock(Block block) {
        return block.getBeginNode() instanceof AbstractMergeNode;
    }

    @Deprecated
    private void patchLoopStms(Block header, Block body, Block backedge) {

        final List<LIRInstruction> headerInsns = lir.getLIRforBlock(header);
        final List<LIRInstruction> bodyInsns = lir.getLIRforBlock(backedge);

        formatLoopHeader(headerInsns);

        migrateInsnToBody(headerInsns, bodyInsns);

    }

    @Deprecated
    private void migrateInsnToBody(List<LIRInstruction> header, List<LIRInstruction> body) {

        // build up set of IVs
        // final Set<AllocatableValue> ivs = new HashSet<AllocatableValue>();
        //
        // for (int i = 1; i < header.size(); i++) {
        // final LIRInstruction insn = header.get(i);
        // if (insn instanceof LoopInitOp) {
        // break;
        // } else if (insn instanceof OCLLIRStmt.AssignStmt) {
        // final OCLLIRStmt.AssignStmt assign =
        // (OCLLIRStmt.AssignStmt) insn;
        // ivs.add(assign.getResult());
        // }
        // }
        // move all insns past the loop expression into the loop body
        int index = header.size() - 1;
        int insertAt = body.size() - 1;

        LIRInstruction current = header.get(index);
        while (!(current instanceof LoopConditionOp)) {
            // System.out.printf("moving: %s from block %s to block
            // %s...\n",current,
            // header, body);
            if (!(current instanceof LoopPostOp)) {
                body.add(insertAt, header.remove(index));
            }

            index--;
            current = header.get(index);
        }

        // move all insns which do not update ivs into the loop body
        // current = header.get(index);
        // while(!(current instanceof LoopConditionOp)){
        // if(current instanceof OCLLIRStmt.AssignStmt){
        // final OCLLIRStmt.AssignStmt assign =
        // (OCLLIRStmt.AssignStmt) current;
        // if(!ivs.contains(assign.getResult())){
        // body.add(insertAt, header.remove(index));
        // }
        // }
        // index--;
        // current = header.get(index);
        // }
    }

    @Deprecated
    private static class DepFinder implements InstructionValueProcedure {

        private final Set<Value> dependencies;

        public DepFinder(final Set<Value> dependencies) {
            this.dependencies = dependencies;
        }

        @Override
        public Value doValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            // System.out.printf("dep: insn=%s,
            // emitValue=%s\n",instruction,emitValue);
            if (value instanceof Variable) {
                dependencies.add(value);
            }

            return value;
        }

        public Set<Value> getDependencies() {
            return dependencies;
        }

    }

    public static void formatLoopHeader(List<LIRInstruction> instructions) {
        int index = instructions.size() - 1;

        LIRInstruction condition = instructions.get(index);
        while (!(condition instanceof LoopConditionOp)) {
            index--;
            condition = instructions.get(index);
        }

        instructions.remove(index);

        final Set<Value> dependencies = new HashSet<>();
        DepFinder df = new DepFinder(dependencies);
        condition.forEachInput(df);

        // for(Value insn : dependencies){
        // System.out.printf("dep: %s\n",insn);
        // }
        index--;
        final List<LIRInstruction> moved = new ArrayList<>();
        LIRInstruction insn = instructions.get(index);
        while (!(insn instanceof LoopPostOp)) {
            if (insn instanceof AssignStmt) {
                AssignStmt assign = (AssignStmt) insn;
                if (assign.getResult() instanceof Variable) {
                    Variable var = (Variable) assign.getResult();
                    if (dependencies.contains(var)) {
                        moved.add(instructions.remove(index));
                    }
                }
            }
            index--;
            insn = instructions.get(index);
        }

        LIRInstruction loopInit = instructions.get(instructions.size() - 1);
        while (!(loopInit instanceof LoopInitOp)) {
            index--;
            loopInit = instructions.get(index);
        }

        instructions.add(index + 1, condition);
        instructions.addAll(index - 1, moved);

    }

    public void emitLoopHeader(Block block) {
        final List<LIRInstruction> headerInsns = lir.getLIRforBlock(block);

        formatLoopHeader(headerInsns);
        emitBlock(block);
    }

    public void emitBlock(Block block) {
        if (block == null) {
            return;
        }

        trace("block: %d", block.getId());
        if (isMergeBlock(block)) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (Block pred : block.getPredecessors()) {
                sb.append(pred.getId()).append(" ");
            }
            sb.append("]");
            ((OCLAssembler) asm).emitLine("// BLOCK %d MERGES %s", block.getId(), sb.toString());

        } else {
            ((OCLAssembler) asm).emitLine("// BLOCK %d", block.getId());
        }

        if (PrintLIRWithAssembly.getValue(getOptions())) {
            blockComment(String.format("block B%d %s", block.getId(), block.getLoop()));
        }

        LIRInstruction breakInst = null;
        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            if (op == null) {
                continue;
            } else if (op instanceof OCLControlFlow.LoopBreakOp) {
                breakInst = op;
                continue;
            }

            if (PrintLIRWithAssembly.getValue(getOptions())) {
                blockComment(String.format("%d %s", op.id(), op));
            }

            try {
                emitOp(this, op);
            } catch (TornadoInternalError e) {
                throw e.addContext("lir instruction", block + "@" + op.id() + " " + op + "\n");
            }
        }

        // because of the way Graal handles Phi nodes, we generate the break
        // instruction
        // before any phi nodes are updated, therefore we need to ensure that
        // the break
        // is emitted as the end of the block.
        if (breakInst != null) {
            try {
                emitOp(this, breakInst);
            } catch (TornadoInternalError e) {
                throw e.addContext("lir instruction", block + "@" + breakInst.id() + " " + breakInst + "\n");
            }
        }

    }

    private static void emitOp(CompilationResultBuilder crb, LIRInstruction op) {
        try {
            trace("op: " + op);
            op.emitCode(crb);
        } catch (AssertionError | RuntimeException t) {
            throw new TornadoInternalError(t);
        }
    }

    private static void traverseControlFlowGraph(ControlFlowGraph cfg, OCLBlockVisitor visitor) {
        if (USE_EXPERIMENTAL_TRAVERSE) {
            traverseControlFlowGraph(cfg.getStartBlock(), visitor, new HashSet<>());
        } else {
            traverseCurrentCfg(cfg.getStartBlock(), visitor);
        }
    }

    private static void traverseCurrentCfg(Block b, OCLBlockVisitor visitor) {
        visitor.enter(b);
        Block firstDominated = b.getFirstDominated();
        while (firstDominated != null) {
            traverseCurrentCfg(firstDominated, visitor);
            firstDominated = firstDominated.getDominatedSibling();
        }
        visitor.exit(b, null);
    }

    private static void traverseControlFlowGraph(Block b, OCLBlockVisitor visitor, HashSet<Block> visited) {

        visitor.enter(b);
        visited.add(b);

        Block firstDominated = b.getFirstDominated();
        LinkedList<Block> queue = new LinkedList<>();
        queue.add(firstDominated);

        if (b.isLoopHeader()) {
            Block[] successors = b.getSuccessors();
            ArrayList<Block> last = new ArrayList<>();
            for (Block block : successors) {
                boolean isInnerLoop = isLoopBlock(block, b);
                if (!isInnerLoop) {
                    last.add(block);
                } else {
                    queue.addLast(block);
                }
            }
            for (Block l : last) {
                queue.addLast(l);
            }
            queue.removeFirst();
            System.out.println(queue);
        }

        for (Block block : queue) {
            firstDominated = block;
            while (firstDominated != null) {
                if (!visited.contains(firstDominated)) {
                    traverseControlFlowGraph(firstDominated, visitor, visited);
                }
                firstDominated = firstDominated.getDominatedSibling();
            }
        }
        visitor.exit(b, null);
    }

    private static boolean isLoopBlock(Block block, Block loopHeader) {

        Set<Block> visited = new HashSet<>();
        Stack<Block> stack = new Stack<>();
        stack.push(block);

        while (!stack.isEmpty()) {

            Block b = stack.pop();
            visited.add(b);

            if (b.getId() < loopHeader.getId()) {
                return false;
            } else if (b == loopHeader) {
                return true;
            } else {
                Block[] successors = b.getSuccessors();
                for (Block bl : successors) {
                    if (!visited.contains(bl)) {
                        stack.push(bl);
                    }
                }
            }
        }

        return false;
    }

}
