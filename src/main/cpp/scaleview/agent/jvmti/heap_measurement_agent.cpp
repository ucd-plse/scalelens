#include "heap_measurement_agent.hpp"
#include <iostream>
#include <stdexcept>
#include <cstring>
#include <string>

using namespace std;

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved){
    std::cout << "[JVMTI] Loading agent..."  << std::endl;
	jint error;
	jvmtiEventCallbacks callbacks;
    jvmtiCapabilities capa;
    // set my jvm for later...
	_thisJVM = jvm; 
	// put a jvmtiEnv instance at jvmti.
	(_thisJVM)->GetEnv((void **) &_thisEnv, JVMTI_VERSION_1_2);
	if (_thisEnv == NULL) {
		std::cout << "[JVMTI] ERROR: Could not access JVMTI, exiting..." << std::endl;
		return JNI_ERR;
	}
    // set capabilities
    memset(&capa, 0, sizeof(jvmtiCapabilities));
	capa.can_tag_objects = 1;
	error = (_thisEnv)->AddCapabilities(&capa);
	if (error != JVMTI_ERROR_NONE) {
		std::cout << "[JVMTI] Could not add capabilities so this wont work, exiting..." << std::endl;
		return JNI_ERR;
	}
    // set up the callbacks..
    std::cout << "[JVMTI] Setting up callbacks..." << std::endl;
    memset(&callbacks,0, sizeof(callbacks));
    callbacks.VMInit = &VMInit;
    callbacks.ClassPrepare = &ClassPrepare;
    error = (_thisEnv)->SetEventCallbacks(&callbacks, (jint)sizeof(callbacks));
	if(error != JVMTI_ERROR_NONE){
		std::cout << "[JVMTI] Could not add callbacks so this wont work, exiting..." << std::endl;
		return JNI_ERR;
	}
    (_thisEnv)->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, NULL);
    (_thisEnv)->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, NULL);
    // and done, this should not be more complex than this...
    return JVMTI_ERROR_NONE;
}

// here, we need to load some basic params, specially the upper call id
JNIEXPORT void JNICALL VMInit(jvmtiEnv *jvmti, JNIEnv* jni_env, jthread thread){
    // here, we can tag the array classes...
    std::cout << "[JVMTI] VM initialized correclty...." << std::endl;
}

std::string getClassName(jvmtiEnv *jvmti, jclass klass){
    char* name;
    (jvmti)->GetClassSignature(klass, &name, NULL);
    if(name != NULL){
        return std::string(name);
    }
    throw std::runtime_error("Could not get class name, something is wrong...");
}

jclass getClassByName(JNIEnv* jni, std::string name){
    return (jni)->FindClass(name.c_str());
}

bool shouldTryToTag(JNIEnv* jni, jvmtiEnv *jvmti, jclass klass, std::string name){
    // nothing in these two packages should be tagged
    if(name.rfind("Lscaleview", 0) == 0 || name.rfind("Ljavassist", 0) == 0) return false;
    // nothing loaded by the bootclassloader should be tagged
    jobject cl;
    (jvmti)->GetClassLoader(klass, &cl);
    if(cl == NULL) return false;
    return true;
}

JNIEXPORT void JNICALL ClassPrepare(jvmtiEnv *jvmti, JNIEnv* env, jthread thread, jclass klass){
    // These are application classes, here i want to tag them to follow them
    std::string name = getClassName(jvmti, klass);
    if(shouldTryToTag(env, jvmti, klass, name)){
        // std::cout << "[JVMTI] Prepared class marked for following [" << name << "]" << std::endl;
        (jvmti)->SetTag(klass, APPLICATION_CLASS);
        // also handle the static fields here?
    }
    // done...
}

// utilities...
void callUpperLog(JNIEnv* jni_env, std::string message){
    jclass mClass = getClassByName(jni_env, _mainClassName);
    if(mClass != NULL){
        jmethodID lMethod = (jni_env)->GetStaticMethodID(mClass, _logMethodName.c_str(), _logMethodSignature.c_str());
        jstring msg = (jni_env)->NewStringUTF(message.c_str());
        if(lMethod != NULL && msg != NULL) (jni_env)->CallStaticVoidMethod(mClass, lMethod, msg);
    }
}

// The class and method here are passed, this one has to be faster...
void callProcessObject(JNIEnv* jni_env, jobject o, int run, jclass mClass, jmethodID mMethod){
    (jni_env)->CallStaticVoidMethod(mClass, mMethod, o, run);
}

extern "C"
JNICALL jvmtiIterationControl tagApplicationObjects(jlong class_tag, jlong object_size, jlong* tag_ptr, void* user_data){
	// just tag
    if(class_tag == APPLICATION_CLASS && *tag_ptr != APPLICATION_OBJECT && *tag_ptr != APPLICATION_CLASS){
		*tag_ptr = APPLICATION_OBJECT;
		int *count = (int*)user_data;
		*count += 1;
	}
	return JVMTI_ITERATION_CONTINUE;
}

// and this is the trigger...
extern "C"
JNIEXPORT void JNICALL Java_scaleview_agent_jvmti_HeapMeasurement_internalDoHeapLookup(JNIEnv *jni_env, jclass thisClass, jint run){
    callUpperLog(jni_env, std::string("[JVMTI] Iterating over heap, run [" + std::to_string(run) + "]"));
    jvmtiError error;
    int objectsTagged = 0;
    // now, we should be able to iterate all the objects in the heap...
    error = (_thisEnv)->IterateOverHeap(JVMTI_HEAP_OBJECT_EITHER, &tagApplicationObjects, (void*)&objectsTagged);
    if (error == JVMTI_ERROR_NONE) {
        callUpperLog(jni_env, std::string("[JVMTI] Tagged [" + std::to_string(objectsTagged) + "] objects..."));
        jint count;
        jobject* instances;
        jint tagCount = 1;
        error = (_thisEnv)->GetObjectsWithTags(tagCount, &APPLICATION_OBJECT, &count, &instances, NULL);
        if(error == JVMTI_ERROR_NONE){
            callUpperLog(jni_env, std::string("[JVMTI] Iterating over [" + std::to_string(count) + "] objects..."));
            jclass mClass = getClassByName(jni_env, _mainClassName);
            if(mClass != NULL){
                jmethodID mMethod = (jni_env)->GetStaticMethodID(mClass, _mainMethodName.c_str(), _mainMethodSignature.c_str());
                if(mMethod != NULL){
                    for(int i = 0; i < count; ++i){
                        callProcessObject(jni_env, instances[i], run, mClass, mMethod);
                    }
                    callUpperLog(jni_env, std::string("[JVMTI] Done with run [" + std::to_string(run) + "]"));
                    (_thisEnv)->Deallocate((unsigned char*)instances);    
                }
                else{
                    throw std::runtime_error("Could not find object processing method, this is an issue...");
                }
            }
            else{
                throw std::runtime_error("Could not find object processing class, this is an issue...");
            }
            
        }
        else{
            callUpperLog(jni_env, std::string("[JVMTI] Error when iterating over heap, could not get tagged objects, run [" + std::to_string(run) + "]"));
        }
    }
    else{
        callUpperLog(jni_env, std::string("[JVMTI] Error when iterating over heap, run [" + std::to_string(run) + "]"));
    }
   
}