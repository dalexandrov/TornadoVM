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
package uk.ac.manchester.tornado.benchmarks.spmv;

import static uk.ac.manchester.tornado.benchmarks.LinearAlgebraArrays.spmv;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;
import uk.ac.manchester.tornado.matrix.SparseMatrixUtils.CSRMatrix;

public class SpmvJava extends BenchmarkDriver {

    private final CSRMatrix<float[]> matrix;

    private float[] v,y;

    public SpmvJava(int iterations, CSRMatrix<float[]> matrix) {
        super(iterations);
        this.matrix = matrix;
    }

    @Override
    public void setUp() {

        v = new float[matrix.size];
        y = new float[matrix.size];

        Benchmark.initData(v);

    }

    @Override
    public void tearDown() {
        v = null;
        y = null;

        super.tearDown();
    }

    @Override
    public void benchmarkMethod() {
        spmv(matrix.vals, matrix.cols, matrix.rows, v, matrix.size, y);
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
