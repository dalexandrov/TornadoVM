/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class tornado_drivers_opencl_OpenCL */

#ifndef _Included_tornado_drivers_opencl_OpenCL
#define _Included_tornado_drivers_opencl_OpenCL
#ifdef __cplusplus
extern "C" {
#endif
#undef tornado_drivers_opencl_OpenCL_CL_TRUE
#define tornado_drivers_opencl_OpenCL_CL_TRUE 1L
#undef tornado_drivers_opencl_OpenCL_CL_FALSE
#define tornado_drivers_opencl_OpenCL_CL_FALSE 0L
/*
 * Class:     tornado_drivers_opencl_OpenCL
 * Method:    registerCallback
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_tornado_drivers_opencl_OpenCL_registerCallback
  (JNIEnv *, jclass);

/*
 * Class:     tornado_drivers_opencl_OpenCL
 * Method:    clGetPlatformCount
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_tornado_drivers_opencl_OpenCL_clGetPlatformCount
  (JNIEnv *, jclass);

/*
 * Class:     tornado_drivers_opencl_OpenCL
 * Method:    clGetPlatformIDs
 * Signature: ([J)I
 */
JNIEXPORT jint JNICALL Java_tornado_drivers_opencl_OpenCL_clGetPlatformIDs
  (JNIEnv *, jclass, jlongArray);

#ifdef __cplusplus
}
#endif
#endif
