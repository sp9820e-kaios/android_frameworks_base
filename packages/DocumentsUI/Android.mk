LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4 guava
LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_PACKAGE_NAME := DocumentsUI
LOCAL_CERTIFICATE := platform
include $(wildcard vendor/sprd/platform/frameworks/support/featurebar/apply_featurebar.mk)
include $(BUILD_PACKAGE)

include $(LOCAL_PATH)/tests/Android.mk
