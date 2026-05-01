#ifndef SCALE_AGENT
#define SCALE_AGENT

#include "jvmti.h"
#include <string>

//Globals
static jvmtiEnv* _thisEnv = NULL;
static JavaVM* _thisJVM = NULL;
static std::string _mainClassName = std::string("scaleview/agent/jvmti/HeapMeasurement");
static std::string _mainMethodName = std::string("processHeapObject");
static std::string _mainMethodSignature = std::string("(Ljava/lang/Object;I)V");
static std::string _logMethodName = std::string("logSomeInfo");
static std::string _logMethodSignature = std::string("(Ljava/lang/String;)V");
static jlong APPLICATION_CLASS = 1001;
static jlong APPLICATION_OBJECT = 2001;

// JVMTI functions
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved);
JNIEXPORT void JNICALL VMInit(jvmtiEnv *jvmti, JNIEnv* jni_env, jthread thread);
JNIEXPORT void JNICALL ClassPrepare(jvmtiEnv *jvmti, JNIEnv* env, jthread thread, jclass klass);

// utilities
void callUpperLog(JNIEnv* jni_env, std::string message);
void callProcessObject(JNIEnv* jni_env, jobject o, int run, jclass mClass, jmethodID mMethod);
std::string getClassName(jvmtiEnv *jvmti, jclass klass);
bool shouldTryToTag(JNIEnv* jni, jvmtiEnv *jvmti, jclass klass, std::string name);
jclass getClassByName(JNIEnv* jni, std::string name);

// Native methods
extern "C"
JNIEXPORT void JNICALL Java_scaleview_agent_jvmti_HeapMeasurement_internalDoHeapLookup(JNIEnv *env, jclass thisClass, jint run);
extern "C"
JNICALL jvmtiIterationControl tagApplicationObjects(jlong class_tag, jlong object_size, jlong* tag_ptr, void* user_data);


#endif