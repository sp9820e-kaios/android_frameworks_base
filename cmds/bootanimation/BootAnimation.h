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

#ifndef ANDROID_BOOTANIMATION_H
#define ANDROID_BOOTANIMATION_H

#include <stdint.h>
#include <sys/types.h>

#include <androidfw/AssetManager.h>
#include <utils/Thread.h>

#include <EGL/egl.h>
#include <GLES/gl.h>

/* SPRD: added boot and shutdown animation @{ */
#include <fcntl.h>
#include <media/mediaplayer.h>
#include <cutils/properties.h>
/* @} */

class SkBitmap;

namespace android {
/* SPRD: added boot and shutdown animation ,define  path here @{ */
#define BOOTANIMATION_BOOT_FILM_PATH_DEFAULT    "/system/media/bootanimation.zip"
#define BOOTANIMATION_SHUTDOWN_FILM_PATH_DEFAULT    "/system/media/shutdownanimation.zip"

#define BOOTANIMATION_BOOT_SOUND_PATH_DEFAULT	    "/system/media/bootsound.mp3"
#define BOOTANIMATION_SHUTDOWN_SOUND_PATH_DEFAULT    "/system/media/shutdownsound.mp3"

/* SPRD:added boot and shutdown animation,user path for bug243780 */
#define BOOTANIMATION_BOOT_FILM_PATH_USER       "/data/theme/overlay/bootanimation.zip"
#define BOOTANIMATION_SHUTDOWN_FILM_PATH_USER   "/data/theme/overlay/shutdownanimation.zip"

#define BOOTANIMATION_BOOT_SOUND_PATH_USER      "/data/theme/overlay/bootsound.mp3"
#define BOOTANIMATION_SHUTDOWN_SOUND_PATH_USER  "/data/theme/overlay/shutdownsound.mp3"
#define BOOTANIMATION_PATHSET_MAX    PROP_VALUE_MAX
/* @} */

/* SPRD:added Region config boot animation,user path for bug518916 */
#define BOOTANIMATION_REGIONCONFIG_PATH_USER    40
/* @} */

class AudioPlayer;
class Surface;
class SurfaceComposerClient;
class SurfaceControl;

// ---------------------------------------------------------------------------

class BootAnimation : public Thread, public IBinder::DeathRecipient
{
public:
                BootAnimation();
    virtual     ~BootAnimation();

    sp<SurfaceComposerClient> session() const;
    /* SPRD: add shutdown animation. @{ */
    bool setsoundpath(String8 path);
    bool setmoviepath(String8 path);
    bool setdescname(String8 path);

    bool setsoundpath_default(String8 path);
    bool setmoviepath_default(String8 path);
    bool setdescname_default(String8 path);
    // SPRD: add for bug 279818, only draw black frame in shutdown animation
    void setShutdownAnimation(bool isShutdownAnimation);
    /* @} */

private:
    virtual bool        threadLoop();
    virtual status_t    readyToRun();
    virtual void        onFirstRef();
    virtual void        binderDied(const wp<IBinder>& who);

    struct Texture {
        GLint   w;
        GLint   h;
        GLuint  name;
    };

    struct Animation {
        struct Frame {
            String8 name;
            FileMap* map;
            mutable GLuint tid;
            bool operator < (const Frame& rhs) const {
                return name < rhs.name;
            }
        };
        struct Part {
            int count;
            int pause;
            String8 path;
            SortedVector<Frame> frames;
            bool playUntilComplete;
            float backgroundColor[3];
            FileMap* audioFile;
        };
        int fps;
        int width;
        int height;
        Vector<Part> parts;
    };

    status_t initTexture(Texture* texture, AssetManager& asset, const char* name);
    status_t initTexture(const Animation::Frame& frame);
    bool android();
    bool readFile(const char* name, String8& outString);
    bool movie();

    /* SPRD: added boot and shutdown animation ,next function and param is metioned @{ */
    bool soundplay();
    bool soundstop();
    sp<MediaPlayer> mp;
    String8    soundpath;
    String8    moviepath;
    String8    descname;
    String8    movie_default_path;
    String8    sound_default_path;
    String8    descname_default;
    /* @} */

    void checkExit();

    sp<SurfaceComposerClient>       mSession;
    sp<AudioPlayer>                 mAudioPlayer;
    AssetManager mAssets;
    Texture     mAndroid[2];
    int         mWidth;
    int         mHeight;
    // SPRD: add shutdown sound
    int         mfd;
    EGLDisplay  mDisplay;
    EGLDisplay  mContext;
    EGLDisplay  mSurface;
    sp<SurfaceControl> mFlingerSurfaceControl;
    sp<Surface> mFlingerSurface;
    ZipFileRO   *mZip;
    // SPRD: add for bug 279818, only draw black frame in shutdown animation
    bool        mShutdownAnimation;
};

// ---------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_BOOTANIMATION_H
