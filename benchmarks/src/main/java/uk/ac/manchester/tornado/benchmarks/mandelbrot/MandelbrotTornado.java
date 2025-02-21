/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package uk.ac.manchester.tornado.benchmarks.mandelbrot;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.benchmarks.ComputeKernels;

public class MandelbrotTornado extends BenchmarkDriver {
    int size;
    short[] output;
    TaskSchedule graph;

    public MandelbrotTornado(int iterations, int size) {
        super(iterations);
        this.size = size;
    }

    @Override
    public void setUp() {
        output = new short[size * size];
        graph = new TaskSchedule("benchmark");
        graph.task("t0", ComputeKernels::mandelbrot, size, output);
        graph.streamOut(output);
        graph.warmup();
    }

    @Override
    public void tearDown() {
        graph.dumpProfiles();

        output = null;

        graph.getDevice().reset();
        super.tearDown();
    }

    @Override
    public boolean validate() {
        boolean val = true;
        short[] result = new short[size * size];

        graph.syncObject(output);
        graph.clearProfiles();

        ComputeKernels.mandelbrot(size, result);

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (Math.abs(output[i * size + j] - result[i * size + j]) > 0.01) {
                    val = false;
                    break;
                }
            }
        }
        System.out.printf("Number validation: " + val + "\n");
        return val;
    }

    @Override
    public void benchmarkMethod() {
        graph.execute();
    }
}
