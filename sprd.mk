
include $(CLEAR_VARS)

LOCAL_SRC_FILES := core/java/android/app/AddonManager.java

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := sprd-support-addon

include $(BUILD_JAVA_LIBRARY)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := core/java/com/android/internal/app/WindowDecorActionBar.java

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := sprd-support-windowdecoractionbar-disableanimation

include $(BUILD_JAVA_LIBRARY)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := core/java/android/app/LowmemoryUtils.java

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := sprd-support-lowmemory

include $(BUILD_JAVA_LIBRARY)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := core/java/android/content/pm/AppCloneUserInfo.java

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := app-clone

include $(BUILD_JAVA_LIBRARY)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := core/java/android/print/PrintManagerHelper.java

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := sprd-support-print

include $(BUILD_STATIC_JAVA_LIBRARY)

