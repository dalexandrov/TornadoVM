# 1. TornadoVM

🌪️ TornadoVM is a plug-in to OpenJDK and GraalVM that allows programmers to automatically run Java programs on heterogeneous hardware. TornadoVM currently targets OpenCL-compatible devices and it runs on multi-core CPUs, GPUs (NVIDIA and AMD), Intel integrated GPUs, and FPGAs (Intel and Xilinx).

For a quick introduction please read the following [FAQ](assembly/src/docs/14_FAQ.md).

**Current Release:** TornadoVM 0.6  - 21/02/2020 : See [CHANGELOG](assembly/src/docs/CHANGELOG.md#tornadovm-06)

Previous Releases can be found [here](assembly/src/docs/Releases.md)

# 2. Installation

TornadoVM can be installed either [from scratch](INSTALL.md) or by [using Docker](assembly/src/docs/12_INSTALL_WITH_DOCKER.md).

You can also run TornadoVM on Amazon AWS CPUs, GPUs, and FPGAs following the instructions [here](assembly/src/docs/16_AWS.md).

# 3. Usage Instructions

TornadoVM is currently being used to accelerate machine learning and deep learning applications, computer vision, physics simulations, financial applications, computational photography, and signal processing.

We have a use-case, [kfusion-tornadovm](https://github.com/beehive-lab/kfusion-tornadovm), for accelerating a computer-vision application implemented in Java using the Tornado-API to run on GPUs.

We also have a set of [examples](https://github.com/beehive-lab/TornadoVM/tree/master/examples/src/main/java/uk/ac/manchester/tornado/examples) that includes NBody, DFT, KMeans computation and matrix computations.

**Additional Information**

[Benchmarks](assembly/src/docs/4_BENCHMARKS.md)

[Reductions](assembly/src/docs/5_REDUCTIONS.md)

[Execution Flags](assembly/src/docs/6_TORNADO_FLAGS.md)

[FPGA execution](assembly/src/docs/7_FPGA.md)

[Profiler Usage](assembly/src/docs/9_PROFILER.md)

# 4. Programming Model

TornadoVM exposes to the programmer task-level, data-level and pipeline-level parallelism via a light Application Programming Interface (API). TornadoVM uses single-source property, in which the code to be accelerated and the host code live in the same Java program.

The following code snippet shows a full example to accelerate Matrix-Multiplication using TornadoVM.

```java
public class Compute {
    private static void mxm(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        for (@Parallel int i = 0; i < size; i++) {
            for (@Parallel int j = 0; j < size; j++) {
                float sum = 0.0f;
                for (int k = 0; k < size; k++) {
                    sum += A.get(i, k) * B.get(k, j);
                }
                C.set(i, j, sum);
            }
        }
    }

    public void run(Matrix2DFloat A, Matrix2DFloat B, Matrix2DFloat C, final int size) {
        TaskSchedule ts = new TaskSchedule("s0")
                .streamIn(A, B)                            // Stream data from host to device
                .task("t0", Compute::mxm, A, B,  C, size)  // Each task points to an existing Java method
                .streamOut(C);                             // sync arrays with the host side
        ts.execute();   // It will execute the code on the default device (e.g. a GPU)
    }
}
```


# 5. Dynamic Reconfiguration

Dynamic reconfiguration is the ability of TornadoVM to perform live task migration between devices, which means that TornadoVM decides where to execute the code to increase performance (if possible). In other words, TornadoVM switches devices if it knows the new device offers better performance. With the task-migration, the TornadoVM's approach is to only switch device if it detects an application can be executed faster than the CPU execution using the code compiled by C2 or Graal-JIT, otherwise it will stay on the CPU. So TornadoVM can be seen as a complement to C2 and Graal. This is because there is no single hardware to best execute all workloads efficiently. GPUs are very good at exploiting SIMD applications, and FPGAs are very good at exploiting pipeline applications. If your applications follow those models, TornadoVM will likely select heterogeneous hardware. Otherwise, it will stay on the CPU using the default compilers (C2 or Graal).

To use the dynamic reconfiguration, you can execute using TornadoVM policies. For example:

```java
// TornadoVM will execute the code in the best accelerator.
ts.execute(Policy.PERFORMANCE);
```

Further details and instructions on how to enable this feature can be found here.

* Dynamic reconfiguration: [https://dl.acm.org/doi/10.1145/3313808.3313819](https://dl.acm.org/doi/10.1145/3313808.3313819)

# 6. Additional Resources

[Here](assembly/src/docs/15_RESOURCES.md) you can find videos, presentations, and articles and artefacts describing TornadoVM and how to use it.

# 7. Academic Publications

Selected publications and citations can be found [here](assembly/src/docs/13_PUBLICATIONS.md).

# 8. Acknowledgments

This work was initially supported by the EPSRC grants [PAMELA EP/K008730/1](http://apt.cs.manchester.ac.uk/projects/PAMELA/) and [AnyScale Apps EP/L000725/1](http://anyscale.org), and now it is funded by the [EU Horizon 2020 E2Data 780245](https://e2data.eu) and the [EU Horizon 2020 ACTiCLOUD 732366](https://acticloud.eu) grants.

# 9. Contributions and Collaborations

We welcome collaborations! Please see how to contribute in the [CONTRIBUTIONS](CONTRIBUTIONS.md).

A mailing list is also available to discuss TornadoVM related issues:

tornado-support@googlegroups.com

For collaborations please contact [Christos Kotselidis](https://www.kotselidis.net).

# 10. TornadoVM Team

This work was originated by James Clarkson under the joint supervision of [Mikel Luján](https://www.linkedin.com/in/mikellujan/) and [Christos Kotselidis](https://www.kotselidis.net).
Currently, this project is maintained and updated by the following contributors:

* [Juan Fumero](https://jjfumero.github.io/)
* [Michail Papadimitriou](https://mikepapadim.github.io)
* [Maria Xekalaki](https://github.com/mairooni)
* [Athanasios Stratikopoulos](https://personalpages.manchester.ac.uk/staff/athanasios.stratikopoulos)
* [Florin Blanaru](https://github.com/gigiblender)
* [Christos Kotselidis](https://www.kotselidis.net)

# 11. License

To use TornadoVM, you can link the TornadoVM API to your application which is under the CLASSPATH Exception of GPLv2.0.

Each TornadoVM module is licensed as follows:

|  Module | License  |
|---|---|
| Tornado-Runtime  | [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) + CLASSPATH Exception  |
| Tornado-Assembly  | [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) + CLASSPATH Exception |
| Tornado-Drivers |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) + CLASSPATH Exception |
| Tornado-API  | [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) + CLASSPATH Exception |
| Tornado-Drivers-OpenCL-Headers |  [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/KhronosGroup/OpenCL-Headers/blob/master/LICENSE) |
| Tornado-scripts |  [![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) |
| Tornado-Annotation |  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) |
| Tornado-Unittests |  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) |
| Tornado-Benchmarks | [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)  |
| Tornado-Examples |  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) |
| Tornado-Matrices  |  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) |
