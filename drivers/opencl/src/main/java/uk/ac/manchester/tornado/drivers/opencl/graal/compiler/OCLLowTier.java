/*
 * Copyright (c) 2018, 2019, APT Group, School of Computer Science,
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

import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.common.AddressLoweringPhase.AddressLowering;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.common.UseTrappingNullChecksPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoLowTier;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoFeatureExtraction;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoLoopCanonicalization;

import static org.graalvm.compiler.core.common.GraalOptions.ConditionalElimination;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;

public class OCLLowTier extends TornadoLowTier {

    public OCLLowTier(OptionValues options, AddressLowering addressLowering) {
        CanonicalizerPhase canonicalizer = null;
        if (ImmutableCode.getValue(options)) {
//            canonicalizer.disableReadCanonicalization();
            canonicalizer = CanonicalizerPhase.createWithoutReadCanonicalization();
        } else {
            canonicalizer = CanonicalizerPhase.create();
        }

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER));

        appendPhase(new RemoveValueProxyPhase());

        // appendPhase(new ExpandLogicPhase());

        /*
         * Cleanup IsNull checks resulting from MID_TIER/LOW_TIER lowering and
         * ExpandLogic phase.
         */
        if (ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
            /*
             * Canonicalizer may create some new ShortCircuitOrNodes so clean them up.
             */
            // appendPhase(new ExpandLogicPhase());
        }

        appendPhase(new AddressLoweringPhase(addressLowering));

        appendPhase(new UseTrappingNullChecksPhase());

        appendPhase(new DeadCodeEliminationPhase(Required));

        appendPhase(new TornadoLoopCanonicalization());
//        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.FINAL_SCHEDULE));
//        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST));
        appendPhase(new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS));

        if (TornadoOptions.FEATURE_EXTRACTION) {
            appendPhase(new TornadoFeatureExtraction());
        }

    }
}
