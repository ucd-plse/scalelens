## This makefile expects 2 params: TARGET_FOLDER, which is where the .so file 
## will be copied to and JVMTI which is the location of the jvmti includes
## main file
MAIN_AG=heap_measurement_agent.cpp
## use g++
GCC=g++
## flags
FLAGS=-fPIC -std=c++11
## the output name
TARGET_AG=heap_measurement_agent.so
## notice that this is DERIVED from JVMTI
JVMTI_INC=$(JVMTI)
JVMTI_LIN=$(JVMTI)/linux
## and the targets
all					: measurement_agent clean

measurement_agent	:	
					$(GCC) -shared -o $(TARGET_AG) $(FLAGS) -I $(JVMTI_INC) -I $(JVMTI_LIN) $(MAIN_AG)
					mv $(TARGET_AG) $(TARGET_FOLDER)/$(TARGET_AG)

clean				:
					rm -f  *.o
					rm -f *.so