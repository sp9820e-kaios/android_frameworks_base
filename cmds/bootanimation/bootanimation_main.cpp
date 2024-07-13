/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "BootAnimation"

#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <cutils/properties.h>
#include <sys/resource.h>
#include <utils/Log.h>
#include <utils/threads.h>

#include "BootAnimation.h"

using namespace android;

// ---------------------------------------------------------------------------

int main(int argc, char** argv)
{
    setpriority(PRIO_PROCESS, 0, ANDROID_PRIORITY_DISPLAY);

    char value[PROPERTY_VALUE_MAX];
    property_get("debug.sf.nobootanimation", value, "0");
    int noBootAnimation = atoi(value);
    ALOGI_IF(noBootAnimation,  "boot animation disabled");
    if (!noBootAnimation) {
        /* SPRD: Removed for adding shutdown animation @{
        sp<ProcessState> proc(ProcessState::self());
        ProcessState::self()->startThreadPool();

        // create the boot animation object
        sp<BootAnimation> boot = new BootAnimation();

        IPCThreadState::self()->joinThreadPool();
        @} */

        /* SPRD: add shutdonw animation @{ */
        char argvtmp[2][BOOTANIMATION_PATHSET_MAX];

        memset(argvtmp[0],0,BOOTANIMATION_PATHSET_MAX);
        memset(argvtmp[1],0,BOOTANIMATION_PATHSET_MAX);

        __android_log_print(ANDROID_LOG_INFO,"BootAnimation", "argc : %d : argc: %s", argc, argv[0]);

        if (argc<2){ /* SPRD: if no param ,exe bootanimation, else exe shutdown animation*/
            strncpy(argvtmp[0],BOOTANIMATION_BOOT_FILM_PATH_USER,BOOTANIMATION_PATHSET_MAX);
            strncpy(argvtmp[1],BOOTANIMATION_BOOT_SOUND_PATH_USER,BOOTANIMATION_PATHSET_MAX);
        } else {
            strncpy(argvtmp[0],BOOTANIMATION_SHUTDOWN_FILM_PATH_USER,BOOTANIMATION_PATHSET_MAX);
            strncpy(argvtmp[1],BOOTANIMATION_SHUTDOWN_SOUND_PATH_USER,BOOTANIMATION_PATHSET_MAX);
        }

        __android_log_print(ANDROID_LOG_INFO,"BootAnimation", "begine bootanimation!");

        sp<ProcessState> proc(ProcessState::self());
        ProcessState::self()->startThreadPool();

        // SPRD: create the boot animation object
        BootAnimation *boota = new BootAnimation();
        //sp<BootAnimation> boota = new BootAnimation();
        String8 descname("desc.txt");

        if (argc<2){
            char animpath[BOOTANIMATION_PATHSET_MAX];
            char soundpath[BOOTANIMATION_PATHSET_MAX];
            int ret;
            ret = property_get("persist.sys.animpath", animpath,"");
            property_get("persist.sys.soundpath", soundpath,"");
            __android_log_print(ANDROID_LOG_INFO,"BootAnimation", "ret : %d : animpath: %s : soundpath: %s", ret, animpath, soundpath);
            // SPRD: Get regionconfig boot animation
            if (ret == BOOTANIMATION_REGIONCONFIG_PATH_USER) {
                String8 mpath_overlay(animpath);
                String8 spath_overlay(soundpath);
                boota->setmoviepath_default(mpath_overlay);
                boota->setsoundpath_default(spath_overlay);
            } else {
                String8 mpath_default(BOOTANIMATION_BOOT_FILM_PATH_DEFAULT);
                String8 spath_default(BOOTANIMATION_BOOT_SOUND_PATH_DEFAULT);
                boota->setmoviepath_default(mpath_default);
                boota->setsoundpath_default(spath_default);
            }
            boota->setShutdownAnimation(false);
        } else {
            String8 mpath_default(BOOTANIMATION_SHUTDOWN_FILM_PATH_DEFAULT);
            String8 spath_default(BOOTANIMATION_SHUTDOWN_SOUND_PATH_DEFAULT);
            boota->setmoviepath_default(mpath_default);
            boota->setsoundpath_default(spath_default);
            //boota->setdescname_default(descname_default);
            boota->setShutdownAnimation(true);
            __android_log_print(ANDROID_LOG_INFO,"BootAnimation","shutdown exe bootanimation!");
        }

        String8 mpath(argvtmp[0]);
        String8 spath(argvtmp[1]);

        boota->setmoviepath(mpath);
        boota->setsoundpath(spath);
        boota->setdescname(descname);

        __android_log_print(ANDROID_LOG_INFO,"BootAnimation","%s", mpath.string());
        __android_log_print(ANDROID_LOG_INFO,"BootAnimation","%s", spath.string());
        sp<BootAnimation> bootsp = boota;
        IPCThreadState::self()->joinThreadPool();
        /* @} */
    }
    return 0;
}
