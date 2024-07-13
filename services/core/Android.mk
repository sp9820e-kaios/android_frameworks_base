LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := services.core

LOCAL_SRC_FILES += \
    $(call all-java-files-under,java) \
    java/com/android/server/EventLogTags.logtags \
    java/com/android/server/am/EventLogTags.logtags

LOCAL_JAVA_LIBRARIES := telephony-common
LOCAL_STATIC_JAVA_LIBRARIES := security \
    tzdata_update

# SPRD: secure start
SECURITY_SRC_FILES := $(call find-other-java-files, ../../secure/services)
$(warning  $(SECURITY_SRC_FILES))
LOCAL_SRC_FILES += $(SECURITY_SRC_FILES)
# SPRD: secure end

include $(BUILD_STATIC_JAVA_LIBRARY)

include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := security:classes-jarjar.jar
include $(BUILD_MULTI_PREBUILT)
