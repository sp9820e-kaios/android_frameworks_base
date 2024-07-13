/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.hardware.fingerprint;

import android.hardware.fingerprint.IFingerprintDaemonCallback;

/**
 * Communication channel from FingerprintService to FingerprintDaemon (fingerprintd)
 * @hide
 */

interface IFingerprintDaemon {
    int authenticate(long sessionId, int groupId);
    int cancelAuthentication();
    int enroll(in byte [] token, int groupId, int timeout);
    int cancelEnrollment();
    long preEnroll();
    int remove(int fingerId, int groupId);
    long getAuthenticatorId();
    int setActiveGroup(int groupId, in byte[] path);
    long openHal();
    int closeHal();
    void init(IFingerprintDaemonCallback callback);
    int postEnroll();
    // Add by silead begin
    int setFPScreenStatus(int screenStatus);
    int setFPEnableCredential(int index, int enable);
    int getFPEnableCredential(int index);
    int getFPVirtualKeyCode();
    int setFPVirtualKeyCode(int virtualKeyCode);
    int getFPLongPressVirtualKeyCode();
    int setFPLongPressVirtualKeyCode(int virtualKeyCode);
    int getFPDouClickVirtualKeyCode();
    int setFPDouClickVirtualKeyCode(int virtualKeyCode);
    int getFPVirtualKeyState();
    int setFPVirtualKeyState(int virtualKeyState);
    int getFPWakeUpState();
    int setFPWakeUpState(int wakeUpState);
    int getFingerPrintState();
    int setFingerPrintState(int fingerPrintState);
    int setFPPowerFuncKeyState(int funcKeyState);
    int getFPPowerFuncKeyState();
    int setFPIdleFuncKeyState(int funcKeyState);
    int getFPIdleFuncKeyState();
    int setFPWholeFuncKeyState(int funcKeyState);
    int setFPFunctionKeyState(int index, int enable);
    int getFPFunctionKeyState(int index);
    // Add by silead end
}
