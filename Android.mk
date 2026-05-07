LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_SRC_FILES += \
	src/org/davical/acal/dataservice/DataRequestCallBack.aidl \
	src/org/davical/acal/dataservice/DataRequest.aidl \
	src/org/davical/acal/service/ServiceRequest.aidl \

LOCAL_PACKAGE_NAME := Acal
LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)
