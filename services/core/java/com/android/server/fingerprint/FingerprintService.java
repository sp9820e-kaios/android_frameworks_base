/**
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.fingerprint;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AppOpsManager;
import android.app.IUserSwitchObserver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SELinux;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Slog;

import com.android.server.SystemService;

import android.hardware.fingerprint.Fingerprint;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.IFingerprintService;
import android.hardware.fingerprint.IFingerprintDaemon;
import android.hardware.fingerprint.IFingerprintDaemonCallback;
import android.hardware.fingerprint.IFingerprintServiceReceiver;

import static android.Manifest.permission.MANAGE_FINGERPRINT;
import static android.Manifest.permission.USE_FINGERPRINT;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
// Add by silead begin
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
// Add by silead end

/**
 * A service to manage multiple clients that want to access the fingerprint HAL API.
 * The service is responsible for maintaining a list of clients and dispatching all
 * fingerprint -related events.
 *
 * @hide
 */
public class FingerprintService extends SystemService implements IBinder.DeathRecipient {
    private static final String TAG = "FingerprintService";
    private static final boolean DEBUG = true;
    private static final String FP_DATA_DIR = "fpdata";
    private static final String FINGERPRINTD = "android.hardware.fingerprint.IFingerprintDaemon";
    private static final int MSG_USER_SWITCHING = 10;
    private static final int ENROLLMENT_TIMEOUT_MS = 60 * 1000; // 1 minute

    private ClientMonitor mAuthClient = null;
    private ClientMonitor mEnrollClient = null;
    private ClientMonitor mRemoveClient = null;
    private final AppOpsManager mAppOps;

    private static final long MS_PER_SEC = 1000;
    private static final long FAIL_LOCKOUT_TIMEOUT_MS = 30*1000;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int FINGERPRINT_ACQUIRED_GOOD = 0;

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case MSG_USER_SWITCHING:
                    handleUserSwitching(msg.arg1);
                    break;

                default:
                    Slog.w(TAG, "Unknown message:" + msg.what);
            }
        }
    };

    private final FingerprintUtils mFingerprintUtils = FingerprintUtils.getInstance();
    private Context mContext;
    private long mHalDeviceId;
    private int mFailedAttempts;
    private IFingerprintDaemon mDaemon;
    private PowerManager mPowerManager;

    private final Runnable mLockoutReset = new Runnable() {
        @Override
        public void run() {
            resetFailedAttempts();
        }
    };

    public FingerprintService(Context context) {
        super(context);
        mContext = context;
        mAppOps = context.getSystemService(AppOpsManager.class);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
    }

    @Override
    public void binderDied() {
        Slog.v(TAG, "fingerprintd died");
        mDaemon = null;
        dispatchError(mHalDeviceId, FingerprintManager.FINGERPRINT_ERROR_HW_UNAVAILABLE);
    }

    public IFingerprintDaemon getFingerprintDaemon() {
        if (mDaemon == null) {
            mDaemon = IFingerprintDaemon.Stub.asInterface(ServiceManager.getService(FINGERPRINTD));
            if (mDaemon != null) {
                try {
                    mDaemon.asBinder().linkToDeath(this, 0);
                    mDaemon.init(mDaemonCallback);
                    mHalDeviceId = mDaemon.openHal();
                    if (mHalDeviceId != 0) {
                        updateActiveGroup(ActivityManager.getCurrentUser());
                    } else {
                        Slog.w(TAG, "Failed to open Fingerprint HAL!");
                        mDaemon = null;
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to open fingeprintd HAL", e);
                    mDaemon = null; // try again later!
                }
            } else {
                Slog.w(TAG, "fingerprint service not available");
            }
        }
        return mDaemon;
    }

    protected void dispatchEnumerate(long deviceId, int[] fingerIds, int[] groupIds) {
        if (fingerIds.length != groupIds.length) {
            Slog.w(TAG, "fingerIds and groupIds differ in length: f[]="
                    + fingerIds + ", g[]=" + groupIds);
            return;
        }
        if (DEBUG) Slog.w(TAG, "Enumerate: f[]=" + fingerIds + ", g[]=" + groupIds);
        // TODO: update fingerprint/name pairs
    }

    protected void dispatchRemoved(long deviceId, int fingerId, int groupId) {
        final ClientMonitor client = mRemoveClient;
        if (fingerId != 0) {
            removeTemplateForUser(mRemoveClient, fingerId);
        }
        if (client != null && client.sendRemoved(fingerId, groupId)) {
            removeClient(mRemoveClient);
        }
    }

    protected void dispatchError(long deviceId, int error) {
        if (mEnrollClient != null) {
            final IBinder token = mEnrollClient.token;
            if (mEnrollClient.sendError(error)) {
                stopEnrollment(token, false);
            }
        } else if (mAuthClient != null) {
            final IBinder token = mAuthClient.token;
            if (mAuthClient.sendError(error)) {
                stopAuthentication(token, false);
            }
        } else if (mRemoveClient != null) {
            if (mRemoveClient.sendError(error)) removeClient(mRemoveClient);
        }
    }

    protected void dispatchAuthenticated(long deviceId, int fingerId, int groupId) {
        if (mAuthClient != null) {
            final IBinder token = mAuthClient.token;
            if (mAuthClient.sendAuthenticated(fingerId, groupId)) {
                stopAuthentication(token, false);
                removeClient(mAuthClient);
            }
        }
    }

    protected void dispatchAcquired(long deviceId, int acquiredInfo) {
        if (mEnrollClient != null) {
            if (mEnrollClient.sendAcquired(acquiredInfo)) {
                removeClient(mEnrollClient);
            }
        } else if (mAuthClient != null) {
            if (mAuthClient.sendAcquired(acquiredInfo)) {
                removeClient(mAuthClient);
            }
        }
    }

    private void userActivity() {
        long now = SystemClock.uptimeMillis();
        mPowerManager.userActivity(now, PowerManager.USER_ACTIVITY_EVENT_TOUCH, 0);
    }

    void handleUserSwitching(int userId) {
        updateActiveGroup(userId);
    }

    protected void dispatchEnrollResult(long deviceId, int fingerId, int groupId, int remaining) {
        if (mEnrollClient != null) {
            if (mEnrollClient.sendEnrollResult(fingerId, groupId, remaining)) {
                if (remaining == 0) {
                    addTemplateForUser(mEnrollClient, fingerId);
                    removeClient(mEnrollClient);
                }
            }
        }
    }

    private void removeClient(ClientMonitor client) {
        if (client == null) return;
        client.destroy();
        if (client == mAuthClient) {
            mAuthClient = null;
        } else if (client == mEnrollClient) {
            mEnrollClient = null;
        } else if (client == mRemoveClient) {
            mRemoveClient = null;
        }
    }

    private boolean inLockoutMode() {
        return mFailedAttempts > MAX_FAILED_ATTEMPTS;
    }

    private void resetFailedAttempts() {
        if (DEBUG && inLockoutMode()) {
            Slog.v(TAG, "Reset fingerprint lockout");
        }
        mFailedAttempts = 0;
    }

    private boolean handleFailedAttempt(ClientMonitor clientMonitor) {
        mFailedAttempts++;
        if (mFailedAttempts > MAX_FAILED_ATTEMPTS) {
            // Failing multiple times will continue to push out the lockout time.
            mHandler.removeCallbacks(mLockoutReset);
            mHandler.postDelayed(mLockoutReset, FAIL_LOCKOUT_TIMEOUT_MS);
            if (clientMonitor != null
                    && !clientMonitor.sendError(FingerprintManager.FINGERPRINT_ERROR_LOCKOUT)) {
                Slog.w(TAG, "Cannot send lockout message to client");
            }
            return true;
        }
        return false;
    }

    private void removeTemplateForUser(ClientMonitor clientMonitor, int fingerId) {
        mFingerprintUtils.removeFingerprintIdForUser(mContext, fingerId, clientMonitor.userId);
    }

    private void addTemplateForUser(ClientMonitor clientMonitor, int fingerId) {
        mFingerprintUtils.addFingerprintForUser(mContext, fingerId, clientMonitor.userId);
    }

    void startEnrollment(IBinder token, byte[] cryptoToken, int groupId,
            IFingerprintServiceReceiver receiver, int flags, boolean restricted) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "enroll: no fingeprintd!");
            return;
        }
        stopPendingOperations(true);
        mEnrollClient = new ClientMonitor(token, receiver, groupId, restricted);
        final int timeout = (int) (ENROLLMENT_TIMEOUT_MS / MS_PER_SEC);
        try {
            final int result = daemon.enroll(cryptoToken, groupId, timeout);
            if (result != 0) {
                Slog.w(TAG, "startEnroll failed, result=" + result);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startEnroll failed", e);
        }
    }

    public long startPreEnroll(IBinder token) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startPreEnroll: no fingeprintd!");
            return 0;
        }
        try {
            return daemon.preEnroll();
        } catch (RemoteException e) {
            Slog.e(TAG, "startPreEnroll failed", e);
        }
        return 0;
    }

    public int startPostEnroll(IBinder token) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startPostEnroll: no fingeprintd!");
            return 0;
        }
        try {
            return daemon.postEnroll();
        } catch (RemoteException e) {
            Slog.e(TAG, "startPostEnroll failed", e);
        }
        return 0;
    }

    private void stopPendingOperations(boolean initiatedByClient) {
        if (mEnrollClient != null) {
            stopEnrollment(mEnrollClient.token, initiatedByClient);
        }
        if (mAuthClient != null) {
            stopAuthentication(mAuthClient.token, initiatedByClient);
        }
        // mRemoveClient is allowed to continue
    }

    /**
     * Stop enrollment in progress and inform client if they initiated it.
     *
     * @param token token for client
     * @param initiatedByClient if this call is the result of client action (e.g. calling cancel)
     */
    void stopEnrollment(IBinder token, boolean initiatedByClient) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "stopEnrollment: no fingeprintd!");
            return;
        }
        final ClientMonitor client = mEnrollClient;
        if (client == null || client.token != token) return;
        if (initiatedByClient) {
            try {
                int result = daemon.cancelEnrollment();
                if (result != 0) {
                    Slog.w(TAG, "startEnrollCancel failed, result = " + result);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "stopEnrollment failed", e);
            }
            client.sendError(FingerprintManager.FINGERPRINT_ERROR_CANCELED);
        }
        removeClient(mEnrollClient);
    }

    void startAuthentication(IBinder token, long opId, int groupId,
            IFingerprintServiceReceiver receiver, int flags, boolean restricted) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startAuthentication: no fingeprintd!");
            return;
        }
        stopPendingOperations(true);
        mAuthClient = new ClientMonitor(token, receiver, groupId, restricted);
        if (inLockoutMode()) {
            Slog.v(TAG, "In lockout mode; disallowing authentication");
            if (!mAuthClient.sendError(FingerprintManager.FINGERPRINT_ERROR_LOCKOUT)) {
                Slog.w(TAG, "Cannot send timeout message to client");
            }
            mAuthClient = null;
            return;
        }
        try {
            final int result = daemon.authenticate(opId, groupId);
            if (result != 0) {
                Slog.w(TAG, "startAuthentication failed, result=" + result);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startAuthentication failed", e);
        }
    }

    /**
     * Stop authentication in progress and inform client if they initiated it.
     *
     * @param token token for client
     * @param initiatedByClient if this call is the result of client action (e.g. calling cancel)
     */
    void stopAuthentication(IBinder token, boolean initiatedByClient) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "stopAuthentication: no fingeprintd!");
            return;
        }
        final ClientMonitor client = mAuthClient;
        if (client == null || client.token != token) return;
        if (initiatedByClient) {
            try {
                int result = daemon.cancelAuthentication();
                if (result != 0) {
                    Slog.w(TAG, "stopAuthentication failed, result=" + result);
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "stopAuthentication failed", e);
            }
            client.sendError(FingerprintManager.FINGERPRINT_ERROR_CANCELED);
        }
        removeClient(mAuthClient);
    }

    void startRemove(IBinder token, int fingerId, int userId,
            IFingerprintServiceReceiver receiver, boolean restricted) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon == null) {
            Slog.w(TAG, "startRemove: no fingeprintd!");
            return;
        }

        mRemoveClient = new ClientMonitor(token, receiver, userId, restricted);
        // The fingerprint template ids will be removed when we get confirmation from the HAL
        try {
            final int result = daemon.remove(fingerId, userId);
            if (result != 0) {
                Slog.w(TAG, "startRemove with id = " + fingerId + " failed, result=" + result);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "startRemove failed", e);
        }
    }

    public List<Fingerprint> getEnrolledFingerprints(int userId) {
        return mFingerprintUtils.getFingerprintsForUser(mContext, userId);
    }

    public boolean hasEnrolledFingerprints(int userId) {
        return mFingerprintUtils.getFingerprintsForUser(mContext, userId).size() > 0;
    }

    boolean hasPermission(String permission) {
        return getContext().checkCallingOrSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    void checkPermission(String permission) {
        getContext().enforceCallingOrSelfPermission(permission,
                "Must have " + permission + " permission.");
    }

    int getEffectiveUserId(int userId) {
        UserManager um = UserManager.get(mContext);
        if (um != null) {
            final long callingIdentity = Binder.clearCallingIdentity();
            userId = um.getCredentialOwnerProfile(userId);
            Binder.restoreCallingIdentity(callingIdentity);
        } else {
            Slog.e(TAG, "Unable to acquire UserManager");
        }
        return userId;
    }

    boolean isCurrentUserOrProfile(int userId) {
        UserManager um = UserManager.get(mContext);

        // Allow current user or profiles of the current user...
        List<UserInfo> profiles = um.getEnabledProfiles(userId);
        final int n = profiles.size();
        for (int i = 0; i < n; i++) {
            if (profiles.get(i).id == userId) {
                return true;
            }
        }
        return false;
    }

    private boolean canUseFingerprint(String opPackageName) {
        checkPermission(USE_FINGERPRINT);
        return mAppOps.noteOp(AppOpsManager.OP_USE_FINGERPRINT, Binder.getCallingUid(),
                opPackageName) == AppOpsManager.MODE_ALLOWED;
    }

    private class ClientMonitor implements IBinder.DeathRecipient {
        IBinder token;
        IFingerprintServiceReceiver receiver;
        int userId;
        boolean restricted; // True if client does not have MANAGE_FINGERPRINT permission

        public ClientMonitor(IBinder token, IFingerprintServiceReceiver receiver, int userId,
                boolean restricted) {
            this.token = token;
            this.receiver = receiver;
            this.userId = userId;
            this.restricted = restricted;
            try {
                token.linkToDeath(this, 0);
            } catch (RemoteException e) {
                Slog.w(TAG, "caught remote exception in linkToDeath: ", e);
            }
        }

        public void destroy() {
            if (token != null) {
                try {
                    token.unlinkToDeath(this, 0);
                } catch (NoSuchElementException e) {
                    // TODO: remove when duplicate call bug is found
                    Slog.e(TAG, "destroy(): " + this + ":", new Exception("here"));
                }
                token = null;
            }
            receiver = null;
        }

        @Override
        public void binderDied() {
            token = null;
            removeClient(this);
            receiver = null;
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                if (token != null) {
                    if (DEBUG) Slog.w(TAG, "removing leaked reference: " + token);
                    removeClient(this);
                }
            } finally {
                super.finalize();
            }
        }

        /*
         * @return true if we're done.
         */
        private boolean sendRemoved(int fingerId, int groupId) {
            if (receiver == null) return true; // client not listening
            try {
                receiver.onRemoved(mHalDeviceId, fingerId, groupId);
                return fingerId == 0;
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to notify Removed:", e);
            }
            return false;
        }

        /*
         * @return true if we're done.
         */
        private boolean sendEnrollResult(int fpId, int groupId, int remaining) {
            if (receiver == null) return true; // client not listening
            FingerprintUtils.vibrateFingerprintSuccess(getContext());
            try {
                receiver.onEnrollResult(mHalDeviceId, fpId, groupId, remaining);
                return remaining == 0;
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to notify EnrollResult:", e);
                return true;
            }
        }

        /*
         * @return true if we're done.
         */
        private boolean sendAuthenticated(int fpId, int groupId) {
            boolean result = false;
            boolean authenticated = fpId != 0;
            if (receiver != null) {
                try {
                    if (!authenticated) {
                        receiver.onAuthenticationFailed(mHalDeviceId);
                    } else {
                        Fingerprint fp = !restricted ?
                                new Fingerprint("" /* TODO */, groupId, fpId, mHalDeviceId) : null;
                        receiver.onAuthenticationSucceeded(mHalDeviceId, fp);
                    }
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to notify Authenticated:", e);
                    result = true; // client failed
                }
            } else {
                result = true; // client not listening
	    }
	    if (fpId == 0) {
                FingerprintUtils.vibrateFingerprintError(getContext());
                result |= handleFailedAttempt(this);
            } else {
                FingerprintUtils.vibrateFingerprintSuccess(getContext());
                result |= true; // we have a valid fingerprint
                mLockoutReset.run();
            }
            return result;
        }

        /*
         * @return true if we're done.
         */
        private boolean sendAcquired(int acquiredInfo) {
            if (receiver == null) return true; // client not listening
            try {
                receiver.onAcquired(mHalDeviceId, acquiredInfo);
                return false; // acquisition continues...
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to invoke sendAcquired:", e);
                return true; // client failed
            }
            finally {
                // Good scans will keep the device awake
                if (acquiredInfo == FINGERPRINT_ACQUIRED_GOOD) {
                    userActivity();
                }
            }
        }

        /*
         * @return true if we're done.
         */
        private boolean sendError(int error) {
            if (receiver != null) {
                try {
                    receiver.onError(mHalDeviceId, error);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to invoke sendError:", e);
                }
            }
            return true; // errors always terminate progress
        }
    }

    private IFingerprintDaemonCallback mDaemonCallback = new IFingerprintDaemonCallback.Stub() {

        @Override
        public void onEnrollResult(long deviceId, int fingerId, int groupId, int remaining) {
            dispatchEnrollResult(deviceId, fingerId, groupId, remaining);
        }

        @Override
        public void onAcquired(long deviceId, int acquiredInfo) {
            dispatchAcquired(deviceId, acquiredInfo);
        }

        @Override
        public void onAuthenticated(long deviceId, int fingerId, int groupId) {
            dispatchAuthenticated(deviceId, fingerId, groupId);
        }

        @Override
        public void onError(long deviceId, int error) {
            dispatchError(deviceId, error);
        }

        @Override
        public void onRemoved(long deviceId, int fingerId, int groupId) {
            dispatchRemoved(deviceId, fingerId, groupId);
        }

        @Override
        public void onEnumerate(long deviceId, int[] fingerIds, int[] groupIds) {
            dispatchEnumerate(deviceId, fingerIds, groupIds);
        }

    };

    private final class FingerprintServiceWrapper extends IFingerprintService.Stub {
        private static final String KEYGUARD_PACKAGE = "com.android.systemui";

        @Override // Binder call
        public long preEnroll(IBinder token) {
            checkPermission(MANAGE_FINGERPRINT);
            return startPreEnroll(token);
        }

        @Override // Binder call
        public int postEnroll(IBinder token) {
            checkPermission(MANAGE_FINGERPRINT);
            return startPostEnroll(token);
        }

        @Override // Binder call
        public void enroll(final IBinder token, final byte[] cryptoToken, final int groupId,
                final IFingerprintServiceReceiver receiver, final int flags) {
            checkPermission(MANAGE_FINGERPRINT);
            final int limit =  mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_fingerprintMaxTemplatesPerUser);
            final int callingUid = Binder.getCallingUid();
            final int userId = UserHandle.getUserId(callingUid);
            final int enrolled = FingerprintService.this.getEnrolledFingerprints(userId).size();
            if (enrolled >= limit) {
                Slog.w(TAG, "Too many fingerprints registered");
                return;
            }
            final byte [] cryptoClone = Arrays.copyOf(cryptoToken, cryptoToken.length);

            // Group ID is arbitrarily set to parent profile user ID. It just represents
            // the default fingerprints for the user.
            final int effectiveGroupId = getEffectiveUserId(groupId);

            final boolean restricted = isRestricted();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    startEnrollment(token, cryptoClone, effectiveGroupId, receiver, flags, restricted);
                }
            });
        }
        // Add by silead begin
        @Override
        public int setScreenStatus(int screenStatus) {
            return sileadSetScreenStatus(screenStatus);
        }

        @Override
        public int setFPEnableCredential(int index, int enable){
            return sileadSetFPEnableCredential(index,enable);
        }

        @Override
        public int getFPEnableCredential(int index){
            return sileadGetFPEnableCredential(index);
        }

        @Override
        public int getFPVirtualKeyCode(){
            return sileadGetFPVirtualKeyCode();
        }

        @Override
        public int setFPVirtualKeyCode(int virtualKeyCode){
            return sileadSetFPVirtualKeyCode(virtualKeyCode);
        }

        @Override
        public int getFPLongPressVirtualKeyCode(){
            return sileadGetFPLongPressVirtualKeyCode();
        }

        @Override
        public int setFPLongPressVirtualKeyCode(int virtualKeyCode){
            return sileadSetFPLongPressVirtualKeyCode(virtualKeyCode);
        }

        @Override
        public int getFPDouClickVirtualKeyCode(){
            return sileadGetFPDouClickVirtualKeyCode();
        }

        @Override
        public int setFPDouClickVirtualKeyCode(int virtualKeyCode){
            return sileadSetFPDouClickVirtualKeyCode(virtualKeyCode);
        }

        @Override
        public int getFPVirtualKeyState(){
            return sileadGetFPVirtualKeyState();
        }

        @Override
        public int setFPVirtualKeyState(int virtualKeyState){
            return sileadSetFPVirtualKeyState(virtualKeyState);
        }

        @Override
        public int getFPWakeUpState(){
            return sileadGetFPWakeUpState();
        }

        @Override
        public int setFPWakeUpState(int wakeUpState){
            return sileadSetFPWakeUpState(wakeUpState);
        }

        @Override
        public int getFingerPrintState(){
            return sileadGetFingerPrintState();
        }

        @Override
        public int setFingerPrintState(int fingerPrintState){
            return sileadSetFingerPrintState(fingerPrintState);
        }

        @Override
        public int setFPPowerFuncKeyState(int funcKeyState){
            return sileadSetFPPowerFuncKeyState(funcKeyState);
        }

        @Override
        public int getFPPowerFuncKeyState(){
            return sileadGetFPPowerFuncKeyState();
        }

        @Override
        public int setFPIdleFuncKeyState(int funcKeyState){
            return sileadSetFPIdleFuncKeyState(funcKeyState);
        }

        @Override
        public int getFPIdleFuncKeyState(){
            return sileadGetFPIdleFuncKeyState();
        }

        @Override
        public int setFPWholeFuncKeyState(int funcKeyState){
            return sileadSetFPWholeFuncKeyState(funcKeyState);
        }

        @Override
        public int setFPFunctionKeyState(int index, int enable){
            return sileadSetFPFunctionKeyState(index,enable);
        }

        @Override
        public int getFPFunctionKeyState(int index){
            return sileadGetFPFunctionKeyState(index);
        }
        // Add by silead end
        private boolean isRestricted() {
            // Only give privileged apps (like Settings) access to fingerprint info
            final boolean restricted = !hasPermission(MANAGE_FINGERPRINT);
            return restricted;
        }

        @Override // Binder call
        public void cancelEnrollment(final IBinder token) {
            checkPermission(MANAGE_FINGERPRINT);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    stopEnrollment(token, true);
                }
            });
        }

        @Override // Binder call
        public void authenticate(final IBinder token, final long opId, final int groupId,
                final IFingerprintServiceReceiver receiver, final int flags,
                final String opPackageName) {
            if (!isCurrentUserOrProfile(UserHandle.getCallingUserId())) {
                Slog.w(TAG, "Can't authenticate non-current user");
                return;
            }
            if (!canUseFingerprint(opPackageName)) {
                Slog.w(TAG, "Calling not granted permission to use fingerprint");
                return;
            }

            // Group ID is arbitrarily set to parent profile user ID. It just represents
            // the default fingerprints for the user.
            final int effectiveGroupId = getEffectiveUserId(groupId);

            final boolean restricted = isRestricted();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    startAuthentication(token, opId, effectiveGroupId, receiver, flags, restricted);
                }
            });
        }

        @Override // Binder call
        public void cancelAuthentication(final IBinder token, String opPackageName) {
            if (!canUseFingerprint(opPackageName)) {
                return;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    stopAuthentication(token, true);
                }
            });
        }

        @Override // Binder call
        public void remove(final IBinder token, final int fingerId, final int groupId,
                final IFingerprintServiceReceiver receiver) {
            checkPermission(MANAGE_FINGERPRINT); // TODO: Maybe have another permission
            final boolean restricted = isRestricted();

            // Group ID is arbitrarily set to parent profile user ID. It just represents
            // the default fingerprints for the user.
            final int effectiveGroupId = getEffectiveUserId(groupId);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    startRemove(token, fingerId, effectiveGroupId, receiver, restricted);
                }
            });

        }

        @Override // Binder call
        public boolean isHardwareDetected(long deviceId, String opPackageName) {
            if (!canUseFingerprint(opPackageName)) {
                return false;
            }
            return mHalDeviceId != 0;
        }

        @Override // Binder call
        public void rename(final int fingerId, final int groupId, final String name) {
            checkPermission(MANAGE_FINGERPRINT);

            // Group ID is arbitrarily set to parent profile user ID. It just represents
            // the default fingerprints for the user.
            final int effectiveGroupId = getEffectiveUserId(groupId);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mFingerprintUtils.renameFingerprintForUser(mContext, fingerId,
                            effectiveGroupId, name);
                }
            });
        }

        @Override // Binder call
        public List<Fingerprint> getEnrolledFingerprints(int userId, String opPackageName) {
            if (!canUseFingerprint(opPackageName)) {
                return Collections.emptyList();
            }
            int effectiveUserId = getEffectiveUserId(userId);

            return FingerprintService.this.getEnrolledFingerprints(effectiveUserId);
        }

        @Override // Binder call
        public boolean hasEnrolledFingerprints(int userId, String opPackageName) {
            if (!canUseFingerprint(opPackageName)) {
                return false;
            }

            int effectiveUserId  = getEffectiveUserId(userId);
            return FingerprintService.this.hasEnrolledFingerprints(effectiveUserId);
        }

        @Override // Binder call
        public long getAuthenticatorId(String opPackageName) {
            // In this method, we're not checking whether the caller is permitted to use fingerprint
            // API because current authenticator ID is leaked (in a more contrived way) via Android
            // Keystore (android.security.keystore package): the user of that API can create a key
            // which requires fingerprint authentication for its use, and then query the key's
            // characteristics (hidden API) which returns, among other things, fingerprint
            // authenticator ID which was active at key creation time.
            //
            // Reason: The part of Android Keystore which runs inside an app's process invokes this
            // method in certain cases. Those cases are not always where the developer demonstrates
            // explicit intent to use fingerprint functionality. Thus, to avoiding throwing an
            // unexpected SecurityException this method does not check whether its caller is
            // permitted to use fingerprint API.
            //
            // The permission check should be restored once Android Keystore no longer invokes this
            // method from inside app processes.

            return FingerprintService.this.getAuthenticatorId();
        }
    }
    // Add by silead begin
    private int sileadSetScreenStatus(int screenStatus) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPScreenStatus(screenStatus);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE sileadSetScreenStatus occurs exception: ", e);
            }
        }
        return -1;
    }

    private int sileadSetFPEnableCredential(int index, int enable){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPEnableCredential(index,enable);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPEnableCredential occurs exception: ", e);
            }
        }
        return -1;
    }

    private int sileadGetFPEnableCredential(int index){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPEnableCredential(index);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPEnableCredential occurs exception: ", e);
            }
        }
        return  0;
    }

    private int sileadGetFPVirtualKeyCode(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPVirtualKeyCode();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPVirtualKeyCode occurs exception: ", e);
            }
        }
        return -1;
    }

    private int sileadSetFPVirtualKeyCode(int virtualKeyCode){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPVirtualKeyCode(virtualKeyCode);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPVirtualKeyCode occurs exception: ", e);
            }
        }

         return -1;
    }

    private int sileadGetFPLongPressVirtualKeyCode(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPLongPressVirtualKeyCode();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPLongPressVirtualKeyCode occurs exception: ", e);
            }
        }

         return 0;
    }

    private int sileadSetFPLongPressVirtualKeyCode(int virtualKeyCode){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPLongPressVirtualKeyCode(virtualKeyCode);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPLongPressVirtualKeyCode occurs exception: ", e);
            }
        }

         return -1;
    }

    private int sileadGetFPDouClickVirtualKeyCode(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPDouClickVirtualKeyCode();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPDouClickVirtualKeyCode occurs exception: ", e);
            }
        }

         return 0;
    }

    private int sileadSetFPDouClickVirtualKeyCode(int virtualKeyCode){
       IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPDouClickVirtualKeyCode(virtualKeyCode);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPDouClickVirtualKeyCode occurs exception: ", e);
            }
        }

         return -1;
    }

    private int sileadGetFPVirtualKeyState(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPVirtualKeyState();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPVirtualKeyState occurs exception: ", e);
            }
        }

         return 0;
    }

    private int sileadSetFPVirtualKeyState(int virtualKeyState){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPVirtualKeyState(virtualKeyState);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPVirtualKeyState occurs exception: ", e);
            }
        }

         return -1;
    }

    private int sileadGetFPWakeUpState(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPWakeUpState();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPWakeUpState occurs exception: ", e);
            }
        }

         return 0;
    }

    private int sileadSetFPWakeUpState(int wakeUpState){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPWakeUpState(wakeUpState);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPWakeUpState occurs exception: ", e);
            }
        }

         return -1;
    }

    private int sileadGetFingerPrintState(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFingerPrintState();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFingerPrintState occurs exception: ", e);
            }
        }

         return 0;
    }

    private int sileadSetFingerPrintState(int fingerPrintState){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFingerPrintState(fingerPrintState);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFingerPrintState occurs exception: ", e);
            }
        }

         return 0;
    }

   private int sileadSetFPPowerFuncKeyState(int funcKeyState){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPPowerFuncKeyState(funcKeyState);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPPowerFuncKeyState occurs exception: ", e);
            }
        }
        return -1;
    }

    private int sileadGetFPPowerFuncKeyState(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPPowerFuncKeyState();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPPowerFuncKeyState occurs exception: ", e);
            }
        }
        return 0;
    }

    private int sileadSetFPIdleFuncKeyState(int funcKeyState){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPIdleFuncKeyState(funcKeyState);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPPowerFuncKeyState occurs exception: ", e);
            }
        }
        return 0;
    }

    private int sileadGetFPIdleFuncKeyState(){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPIdleFuncKeyState();
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPIdleFuncKeyState occurs exception: ", e);
            }
        }
        return 0;
    }

    private int sileadSetFPWholeFuncKeyState(int funcKeyState){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPWholeFuncKeyState(funcKeyState);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPWholeFuncKeyState occurs exception: ", e);
            }
        }
        return 0;
    }

    private int sileadSetFPFunctionKeyState(int index, int enable){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.setFPFunctionKeyState(index,enable);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE setFPFunctionKeyState occurs exception: ", e);
            }
        }
        return -1;
    }

    private int sileadGetFPFunctionKeyState(int index){
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getFPFunctionKeyState(index);
            } catch(RemoteException e) {
                Slog.e(TAG, "SLCODE getFPFunctionKeyState occurs exception: ", e);
            }
        }
        return  0;
    }

    private BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Slog.v(TAG, " SLCODE screenReceiver recv action: " + action);
            if(Intent.ACTION_SCREEN_OFF.equals(action)) {
                //setScreenStatus(0);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                //setScreenStatus(1);
            }
        }
    };
    // Add by silead end
    @Override
    public void onStart() {
        publishBinderService(Context.FINGERPRINT_SERVICE, new FingerprintServiceWrapper());
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (DEBUG) Slog.v(TAG, "Fingerprint HAL id: " + mHalDeviceId);
        listenForUserSwitches();
    }

    private void updateActiveGroup(int userId) {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                userId = getEffectiveUserId(userId);
                final File systemDir = Environment.getUserSystemDirectory(userId);
                final File fpDir = new File(systemDir, FP_DATA_DIR);
                if (!fpDir.exists()) {
                    if (!fpDir.mkdir()) {
                        Slog.v(TAG, "Cannot make directory: " + fpDir.getAbsolutePath());
                        return;
                    }
                    // Calling mkdir() from this process will create a directory with our
                    // permissions (inherited from the containing dir). This command fixes
                    // the label.
                    if (!SELinux.restorecon(fpDir)) {
                        Slog.w(TAG, "Restorecons failed. Directory will have wrong label.");
                        return;
                    }
                }
                daemon.setActiveGroup(userId, fpDir.getAbsolutePath().getBytes());
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to setActiveGroup():", e);
            }
        }
    }

    private void listenForUserSwitches() {
        try {
            ActivityManagerNative.getDefault().registerUserSwitchObserver(
                new IUserSwitchObserver.Stub() {
                    @Override
                    public void onUserSwitching(int newUserId, IRemoteCallback reply) {
                        mHandler.obtainMessage(MSG_USER_SWITCHING, newUserId, 0 /* unused */)
                                .sendToTarget();
                    }
                    @Override
                    public void onUserSwitchComplete(int newUserId) throws RemoteException {
                        // Ignore.
                    }
                    @Override
                    public void onForegroundProfileSwitch(int newProfileId) {
                        // Ignore.
                    }
                });
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to listen for user switching event" ,e);
        }
    }

    public long getAuthenticatorId() {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        if (daemon != null) {
            try {
                return daemon.getAuthenticatorId();
            } catch (RemoteException e) {
                Slog.e(TAG, "getAuthenticatorId failed", e);
            }
        }
        return 0;
    }

}
