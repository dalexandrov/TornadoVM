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
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler;

import static org.graalvm.compiler.core.common.GraalOptions.ConditionalElimination;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.core.common.GraalOptions.OptConvertDeoptsToGuards;
import static org.graalvm.compiler.core.common.GraalOptions.PartialEscapeAnalysis;
import static org.graalvm.compiler.core.phases.HighTier.Options.Inline;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import org.graalvm.compiler.loop.DefaultLoopPolicies;
import org.graalvm.compiler.loop.LoopPolicies;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;

import jdk.vm.ci.meta.MetaAccessProvider;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoOpenCLIntrinsicsReplacements;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoParallelScheduler;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoPragmaUnroll;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoTaskSpecialisation;
import uk.ac.manchester.tornado.drivers.opencl.graal.phases.TornadoThreadScheduler;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoHighTier;
import uk.ac.manchester.tornado.runtime.graal.phases.ExceptionSuppression;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoInliningPolicy;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoLocalMemoryAllocation;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoShapeAnalysis;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoValueTypeCleanup;

public class OCLHighTier extends TornadoHighTier {

    public OCLHighTier(OptionValues options, TornadoDeviceContext deviceContext, CanonicalizerPhase.CustomCanonicalization customCanonicalizer, MetaAccessProvider metaAccessProvider) {
        super(customCanonicalizer);

        CanonicalizerPhase canonicalizer;
        if (ImmutableCode.getValue(options)) {
            canonicalizer = CanonicalizerPhase.createWithoutReadCanonicalization();
        } else {
            canonicalizer = CanonicalizerPhase.create();
        }

        canonicalizer = canonicalizer.copyWithCustomCanonicalization(customCanonicalizer);

        appendPhase(canonicalizer);

        if (Inline.getValue(options)) {
            appendPhase(new InliningPhase(new TornadoInliningPolicy(), canonicalizer));

            appendPhase(new DeadCodeEliminationPhase(Optional));

            if (ConditionalElimination.getValue(options)) {
                appendPhase(canonicalizer);
                appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
            }
        }

        appendPhase(new TornadoTaskSpecialisation(canonicalizer));
        appendPhase(canonicalizer);
        appendPhase(new DeadCodeEliminationPhase(Optional));

        appendPhase(canonicalizer);

        if (PartialEscapeAnalysis.getValue(options)) {
            appendPhase(new PartialEscapePhase(true, canonicalizer, options));
        }
        appendPhase(new TornadoValueTypeCleanup());

        if (OptConvertDeoptsToGuards.getValue(options)) {
            appendPhase(new ConvertDeoptimizeToGuardPhase());
        }

        appendPhase(new TornadoShapeAnalysis());
        appendPhase(canonicalizer);
        appendPhase(new TornadoParallelScheduler());
        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST));
        if (deviceContext.isPlatformFPGA()) {
            appendPhase(new TornadoPragmaUnroll());
            appendPhase(new TornadoThreadScheduler());
        } else {
            LoopPolicies loopPolicies = new DefaultLoopPolicies();
            appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));
        }

        appendPhase(canonicalizer);
        appendPhase(new RemoveValueProxyPhase());
        appendPhase(canonicalizer);
        appendPhase(new DeadCodeEliminationPhase(Optional));

        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST));
        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER));

        // After the first Lowering, Tornado replaces reductions with snippets
        // that contains method calls to barriers.

        appendPhase(new TornadoOpenCLIntrinsicsReplacements(metaAccessProvider));

        appendPhase(new TornadoLocalMemoryAllocation());

        appendPhase(new ExceptionSuppression());

    }
}
