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
package uk.ac.manchester.tornado.benchmarks.sgemv;

import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.sgemv;

import java.util.Random;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class SgemvJava extends BenchmarkDriver {

    private final int m;
    private final int n;
    private float[] a;
    private float[] x;
    private float[] y;

    public SgemvJava(int iterations, int m, int n) {
        super(iterations);
        this.m = m;
        this.n = n;
    }

    @Override
    public void setUp() {
        a = new float[m * n];
        x = new float[n];
        y = new float[n];

        final Random random = new Random();

        for (int i = 0; i < m; i++) {
            a[i * (m + 1)] = 1;
        }

        for (int i = 0; i < n; i++) {
            x[i] = random.nextFloat();
        }

    }

    @Override
    public void tearDown() {
        a = null;
        x = null;
        y = null;
        super.tearDown();
    }

    @Override
    public void benchmarkMethod() {
        sgemv(m, n, a, x, y);
    }

    @Override
    public void barrier() {

    }

    @Override
    public boolean validate() {

        return true;
    }

    public void printSummary() {
        System.out.printf("id=java-serial, elapsed=%f, per iteration=%f\n", getElapsed(), getElapsedPerIteration());
    }

}
