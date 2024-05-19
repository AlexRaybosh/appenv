
# no more javah
#JNI_H:=$(SRC_NATIVE)/APIStub.h
#JAVAH:=javah
UNAME=$(shell uname)
MARCH=$(shell uname -m)
MYARCH=$(UNAME)-$(MARCH)

export NATIVE_BUILD:=$(ROOT)/native_build

#all : $(JNI_H)
$(info MYARCH $(MYARCH))

all:
ifeq ($(MYARCH),Linux-aarch64)
	$(MAKE) -f arch.mk MYARCH=$(MYARCH) ARCH="Linux-aarch64-64" ROOT=$(ROOT)
	#$(MAKE) -f arch.mk MYARCH=$(MYARCH) ARCH="Linux-armv7l-32" ROOT=$(ROOT)
else ifeq ($(MYARCH), Linux-x86_64)
	$(MAKE) -f arch.mk MYARCH=$(MYARCH) ARCH="Linux-x86_64-64" ROOT=$(ROOT)
endif

#$(JNI_H): $(JAVA_BUILD)/appenv/jni/APIStub.class
#	@echo GENERATE JNI HEADER
#	@ls -l $(JAVA_BUILD)/appenv/jni/APIStub.class
#	@ls -l $(JNI_H)
#	$(JAVAH) -classpath $(JAVA_BUILD) -o $(JNI_H) appenv.jni.APIStub
#	touch $(JNI_H)


clean:
	@rm -rf $(ROOT)/native_build
	$(MAKE) -f arch.mk ARCH="Linux-x86_64-64" ROOT=$(ROOT) clean
	$(MAKE) -f arch.mk ARCH="Linux-x86_64-32" ROOT=$(ROOT) clean
	$(MAKE) -f arch.mk ARCH="Linux-aarch64-64" ROOT=$(ROOT) clean
	$(MAKE) -f arch.mk ARCH="Linux-armv7l-32" ROOT=$(ROOT) clean

.PHONY: all clean
