/* 最小 jni.h 假头，只用于 `clang -fsyntax-only` 语法自检，不参与真实构建。
 * 结构与 NDK 的 C 接口一致：JNIEnv / JavaVM 都是「函数表指针」的 typedef。 */
#ifndef FAKE_JNI_H
#define FAKE_JNI_H

#include <stdint.h>

typedef int8_t jboolean;
typedef int32_t jint;
typedef void *jobject;
typedef jobject jclass;
typedef jobject jstring;
typedef jobject jthrowable;
typedef void *jmethodID;
typedef void *jfieldID;

#define JNI_OK 0
#define JNI_TRUE 1
#define JNI_FALSE 0

#define JNIEXPORT
#define JNICALL

struct JNINativeInterface;
typedef const struct JNINativeInterface *JNIEnv;

struct JNIInvokeInterface;
typedef const struct JNIInvokeInterface *JavaVM;

struct JNINativeInterface {
    void *reserved0;
    void *reserved1;
    void *reserved2;
    void *reserved3;

    jclass (*FindClass)(JNIEnv *, const char *);
    jmethodID (*GetStaticMethodID)(JNIEnv *, jclass, const char *, const char *);
    jobject (*NewGlobalRef)(JNIEnv *, jobject);
    void (*DeleteLocalRef)(JNIEnv *, jobject);
    void (*DeleteGlobalRef)(JNIEnv *, jobject);
    jstring (*NewStringUTF)(JNIEnv *, const char *);
    const char *(*GetStringUTFChars)(JNIEnv *, jstring, jboolean *);
    void (*ReleaseStringUTFChars)(JNIEnv *, jstring, const char *);
    void (*CallStaticVoidMethod)(JNIEnv *, jclass, jmethodID, ...);
    jboolean (*ExceptionCheck)(JNIEnv *);
    void (*ExceptionClear)(JNIEnv *);
    jint (*GetJavaVM)(JNIEnv *, JavaVM **);
};

struct JNIInvokeInterface {
    void *reserved0;
    void *reserved1;
    void *reserved2;

    jint (*DestroyJavaVM)(JavaVM *);
    jint (*AttachCurrentThread)(JavaVM *, JNIEnv **, void *);
    jint (*DetachCurrentThread)(JavaVM *);
    jint (*GetEnv)(JavaVM *, void **, jint);
};

#endif
