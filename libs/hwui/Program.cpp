/*
 * Copyright (C) 2010 The Android Open Source Project
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

#define LOG_TAG "OpenGLRenderer"
#define ATRACE_TAG ATRACE_TAG_VIEW

#include <utils/Trace.h>

#include "Program.h"
#include "Vertex.h"

#ifdef USE_OFFLINE_COMPILER_SHADER
#define PROGRAM_BINARY_MAX_SIZE (16*1024)
#define PROGRAM_BINARY_FORMAT 36705
#endif

namespace android {
namespace uirenderer {

///////////////////////////////////////////////////////////////////////////////
// Base program
///////////////////////////////////////////////////////////////////////////////

#ifdef USE_OFFLINE_COMPILER_SHADER
static void SaveProgramBinary(GLuint programId, programid key, char* FileName, void *programBinary)
{
    GLsizei length = 0;
    GLenum binaryFormat = 0;
    FILE *fp = fopen(FileName, "wb+");

    if (NULL != fp)
    {
        glGetProgramBinaryOES(programId, PROGRAM_BINARY_MAX_SIZE, &length, &binaryFormat, programBinary);
        if (length > 0 && length < PROGRAM_BINARY_MAX_SIZE && binaryFormat == PROGRAM_BINARY_FORMAT)
        {
            fwrite(programBinary, length, 1, fp);
            //ALOGD("james save %s programId=%d, length=%d, binaryFormat=%d, key=%" PRIx64 "", FileName, programId, length, binaryFormat, key);
        }
        else
        {
            ALOGE("SaveProgramBinary failed %s, size=%d, binaryFormat=%d", FileName, length, binaryFormat);
        }
        fclose(fp);
    }
    else
    {
        ALOGE("SaveProgramBinary failed %s", FileName);
    }
}
#endif

Program::Program(const ProgramDescription& description, const char* vertex, const char* fragment) {
    mInitialized = false;
    mHasColorUniform = false;
    mHasSampler = false;
    mUse = false;

#ifdef USE_OFFLINE_COMPILER_SHADER
    bool pbRet = false;
    programid key = description.key();
    char FileName[512] = {0};
    char cmdline[512] = {0};
    void *programBinary = malloc(PROGRAM_BINARY_MAX_SIZE);
    FILE *cmdline_file = fopen("/proc/self/cmdline", "r");
    GLsizei length;
    GLint status;

    // glProgramBinaryOES need program id
    mProgramId = glCreateProgram();

    if (NULL != cmdline_file)
    {
        size_t size = fread(cmdline, 1, 512, cmdline_file);
        fclose(cmdline_file);

        sprintf(FileName, "/data/data/%s/PB_%x_%" PRIx64 "", cmdline, PROGRAM_BINARY_FORMAT, key);
        FILE *fp = fopen(FileName, "rb+");
        if (NULL != fp)
        {
            length = fread(programBinary, 1, PROGRAM_BINARY_MAX_SIZE, fp);
            if (length > 0 && length < PROGRAM_BINARY_MAX_SIZE)
            {
                glProgramBinaryOES(mProgramId, PROGRAM_BINARY_FORMAT, programBinary, length);
                glGetProgramiv(mProgramId, GL_LINK_STATUS, &status);
                if (status == GL_TRUE)
                {
                        pbRet = true;
                }
                else
                {
                    ALOGE("glProgramBinaryOES failed %s, size=%d", FileName, length);
                }
            }
            else
            {
                ALOGE("open failed %s, size=%d", FileName, length);
            }
            fclose(fp);
        }
    }

    // No need to cache compiled shaders, rely instead on Android's
    // persistent shaders cache
    if (!pbRet)
    {
        mVertexShader = buildShader(vertex, GL_VERTEX_SHADER);
        mFragmentShader = buildShader(fragment, GL_FRAGMENT_SHADER);
        glAttachShader(mProgramId, mVertexShader);
        glAttachShader(mProgramId, mFragmentShader);
    }

    bindAttrib("position", kBindingPosition);
    if (description.hasTexture || description.hasExternalTexture) {
        texCoords = bindAttrib("texCoords", kBindingTexCoords);
    } else {
        texCoords = -1;
    }

    if (!pbRet)
    {
        ATRACE_BEGIN("linkProgram");
        glLinkProgram(mProgramId);
        ATRACE_END();
    }

    glGetProgramiv(mProgramId, GL_LINK_STATUS, &status);
    if (status != GL_TRUE) {
        GLint infoLen = 0;
        glGetProgramiv(mProgramId, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 1) {
            GLchar log[infoLen];
            glGetProgramInfoLog(mProgramId, infoLen, nullptr, &log[0]);
            ALOGE("%s", log);
        }
        LOG_ALWAYS_FATAL("Error while linking shaders");
    } else {
        mInitialized = true;
        if (!pbRet)
        {
            SaveProgramBinary(mProgramId, key, FileName, programBinary);
        }
    }

    free(programBinary);
#else
    // No need to cache compiled shaders, rely instead on Android's
    // persistent shaders cache
    mVertexShader = buildShader(vertex, GL_VERTEX_SHADER);
    if (mVertexShader) {
        mFragmentShader = buildShader(fragment, GL_FRAGMENT_SHADER);
        if (mFragmentShader) {
            mProgramId = glCreateProgram();

            glAttachShader(mProgramId, mVertexShader);
            glAttachShader(mProgramId, mFragmentShader);

            bindAttrib("position", kBindingPosition);
            if (description.hasTexture || description.hasExternalTexture) {
                texCoords = bindAttrib("texCoords", kBindingTexCoords);
            } else {
                texCoords = -1;
            }

            ATRACE_BEGIN("linkProgram");
            glLinkProgram(mProgramId);
            ATRACE_END();

            GLint status;
            glGetProgramiv(mProgramId, GL_LINK_STATUS, &status);
            if (status != GL_TRUE) {
                GLint infoLen = 0;
                glGetProgramiv(mProgramId, GL_INFO_LOG_LENGTH, &infoLen);
                if (infoLen > 1) {
                    GLchar log[infoLen];
                    glGetProgramInfoLog(mProgramId, infoLen, nullptr, &log[0]);
                    ALOGE("%s", log);
                }
                LOG_ALWAYS_FATAL("Error while linking shaders");
            } else {
                mInitialized = true;
            }
        } else {
            glDeleteShader(mVertexShader);
        }
    }
#endif

    if (mInitialized) {
        transform = addUniform("transform");
        projection = addUniform("projection");
    }
}

Program::~Program() {
    if (mInitialized) {
        // This would ideally happen after linking the program
        // but Tegra drivers, especially when perfhud is enabled,
        // sometimes crash if we do so
        glDetachShader(mProgramId, mVertexShader);
        glDetachShader(mProgramId, mFragmentShader);

        glDeleteShader(mVertexShader);
        glDeleteShader(mFragmentShader);

        glDeleteProgram(mProgramId);
    }
}

int Program::addAttrib(const char* name) {
    int slot = glGetAttribLocation(mProgramId, name);
    mAttributes.add(name, slot);
    return slot;
}

int Program::bindAttrib(const char* name, ShaderBindings bindingSlot) {
    glBindAttribLocation(mProgramId, bindingSlot, name);
    mAttributes.add(name, bindingSlot);
    return bindingSlot;
}

int Program::getAttrib(const char* name) {
    ssize_t index = mAttributes.indexOfKey(name);
    if (index >= 0) {
        return mAttributes.valueAt(index);
    }
    return addAttrib(name);
}

int Program::addUniform(const char* name) {
    int slot = glGetUniformLocation(mProgramId, name);
    mUniforms.add(name, slot);
    return slot;
}

int Program::getUniform(const char* name) {
    ssize_t index = mUniforms.indexOfKey(name);
    if (index >= 0) {
        return mUniforms.valueAt(index);
    }
    return addUniform(name);
}

GLuint Program::buildShader(const char* source, GLenum type) {
    ATRACE_NAME("Build GL Shader");

    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);

    GLint status;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (status != GL_TRUE) {
        ALOGE("Error while compiling this shader:\n===\n%s\n===", source);
        // Some drivers return wrong values for GL_INFO_LOG_LENGTH
        // use a fixed size instead
        GLchar log[512];
        glGetShaderInfoLog(shader, sizeof(log), nullptr, &log[0]);
        LOG_ALWAYS_FATAL("Shader info log: %s", log);
        return 0;
    }

    return shader;
}

void Program::set(const mat4& projectionMatrix, const mat4& modelViewMatrix,
        const mat4& transformMatrix, bool offset) {
    if (projectionMatrix != mProjection || offset != mOffset) {
        if (CC_LIKELY(!offset)) {
            glUniformMatrix4fv(projection, 1, GL_FALSE, &projectionMatrix.data[0]);
        } else {
            mat4 p(projectionMatrix);
            // offset screenspace xy by an amount that compensates for typical precision
            // issues in GPU hardware that tends to paint hor/vert lines in pixels shifted
            // up and to the left.
            // This offset value is based on an assumption that some hardware may use as
            // little as 12.4 precision, so we offset by slightly more than 1/16.
            p.translate(Vertex::GeometryFudgeFactor(), Vertex::GeometryFudgeFactor());
            glUniformMatrix4fv(projection, 1, GL_FALSE, &p.data[0]);
        }
        mProjection = projectionMatrix;
        mOffset = offset;
    }

    mat4 t(transformMatrix);
    t.multiply(modelViewMatrix);
    glUniformMatrix4fv(transform, 1, GL_FALSE, &t.data[0]);
}

void Program::setColor(FloatColor color) {
    if (!mHasColorUniform) {
        mColorUniform = getUniform("color");
        mHasColorUniform = true;
    }
    glUniform4f(mColorUniform, color.r, color.g, color.b, color.a);
}

void Program::use() {
    glUseProgram(mProgramId);
    if (texCoords >= 0 && !mHasSampler) {
        glUniform1i(getUniform("baseSampler"), 0);
        mHasSampler = true;
    }
    mUse = true;
}

void Program::remove() {
    mUse = false;
}

}; // namespace uirenderer
}; // namespace android
