/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.internal.content;

import static android.net.TrafficStats.MB_IN_BYTES;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageParser.PackageLite;
import android.os.Environment;
import android.os.FileUtils;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.storage.IMountService;
import android.os.storage.StorageManager;
import android.os.storage.StorageResultCode;
import android.os.storage.StorageVolume;
import android.os.storage.VolumeInfo;
import android.util.ArraySet;
import android.util.Log;

import libcore.io.IoUtils;

import android.provider.Settings; //SPRD: suport double sdcard
import android.os.SystemProperties; // SPRD: add for package install
import com.android.internal.app.IMediaContainerService; //SPRD: suport double sdcard

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Constants used internally between the PackageManager
 * and media container service transports.
 * Some utility methods to invoke MountService api.
 */
public class PackageHelper {
    public static final int RECOMMEND_INSTALL_INTERNAL = 1;
    public static final int RECOMMEND_INSTALL_EXTERNAL = 2;
    /* SPRD: support double sdcard
     * Add support for install apk to internal sdcard @{
     */
    public static final int RECOMMEND_INSTALL_INTERNALSD = 3;
    /* @} */
    public static final int RECOMMEND_FAILED_INSUFFICIENT_STORAGE = -1;
    public static final int RECOMMEND_FAILED_INVALID_APK = -2;
    public static final int RECOMMEND_FAILED_INVALID_LOCATION = -3;
    public static final int RECOMMEND_FAILED_ALREADY_EXISTS = -4;
    public static final int RECOMMEND_MEDIA_UNAVAILABLE = -5;
    public static final int RECOMMEND_FAILED_INVALID_URI = -6;
    public static final int RECOMMEND_FAILED_VERSION_DOWNGRADE = -7;

    private static final boolean localLOGV = false;
    private static final String TAG = "PackageHelper";
    // App installation location settings values
    public static final int APP_INSTALL_AUTO = 0;
    public static final int APP_INSTALL_INTERNAL = 1;
    public static final int APP_INSTALL_EXTERNAL = 2;

    /* SPRD: support double sdcard
     * Add support for install apk to internal sdcard @{
     */
    public static final int APP_INSTALL_INTERNALSD = 3;
    /* @} */

    /* SPRD: support double sdcard
     * add for package install @{
     */
    private static final int NAND = 1;
    private static final int EMMC = 2;
    private static final int flashType = SystemProperties.getInt("ro.storage.flash_type",0);
    private static final boolean isInternalSDInstalld = SystemProperties.getInt("ro.storage.install2internal",0) == 1;
    /* @} */

    public static IMountService getMountService() throws RemoteException {
        IBinder service = ServiceManager.getService("mount");
        if (service != null) {
            return IMountService.Stub.asInterface(service);
        } else {
            Log.e(TAG, "Can't get mount service");
            throw new RemoteException("Could not contact mount service");
        }
    }

    public static String createSdDir(long sizeBytes, String cid, String sdEncKey, int uid,
            boolean isExternal) {
        // Round up to nearest MB, plus another MB for filesystem overhead
        final int sizeMb = (int) ((sizeBytes + MB_IN_BYTES) / MB_IN_BYTES) + 1;
    /* SPRD: support double sdcard
     * Add support for install apk to internal sdcard @{
     */
        return createSdDir(sizeMb, cid, sdEncKey, uid, isExternal, true);
    }

    public static String createSdDir(int sizeMb, String cid, String sdEncKey, int uid,
            boolean isExternal, boolean isForwardLocked) {
    /* @} */
        try {
            IMountService mountService = getMountService();

            if (localLOGV)
                Log.i(TAG, "Size of container " + sizeMb + " MB");

            /* SPRD: support double sdcard
             * Add support for install apk to internal sdcard @{
             */
            int rc = mountService.createInternalSdContainer(cid, sizeMb, "ext4", sdEncKey, uid,
                    isExternal, isForwardLocked);
            /* @} */
            if (rc != StorageResultCode.OperationSucceeded) {
                Log.e(TAG, "Failed to create secure container " + cid);
                return null;
            }
            String cachePath = mountService.getSecureContainerPath(cid);
            if (localLOGV) Log.i(TAG, "Created secure container " + cid +
                    " at " + cachePath);
                return cachePath;
        } catch (RemoteException e) {
            Log.e(TAG, "MountService running?");
        }
        return null;
    }

    public static boolean resizeSdDir(long sizeBytes, String cid, String sdEncKey) {
        // Round up to nearest MB, plus another MB for filesystem overhead
        final int sizeMb = (int) ((sizeBytes + MB_IN_BYTES) / MB_IN_BYTES) + 1;
        try {
            IMountService mountService = getMountService();
            int rc = mountService.resizeSecureContainer(cid, sizeMb, sdEncKey);
            if (rc == StorageResultCode.OperationSucceeded) {
                return true;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "MountService running?");
        }
        Log.e(TAG, "Failed to create secure container " + cid);
        return false;
    }

    public static String mountSdDir(String cid, String key, int ownerUid) {
        return mountSdDir(cid, key, ownerUid, true);
    }

    public static String mountSdDir(String cid, String key, int ownerUid, boolean readOnly) {
        try {
            int rc = getMountService().mountSecureContainer(cid, key, ownerUid, readOnly);
            if (rc != StorageResultCode.OperationSucceeded) {
                Log.i(TAG, "Failed to mount container " + cid + " rc : " + rc);
                return null;
            }
            return getMountService().getSecureContainerPath(cid);
        } catch (RemoteException e) {
            Log.e(TAG, "MountService running?");
        }
        return null;
    }

   public static boolean unMountSdDir(String cid) {
    try {
        int rc = getMountService().unmountSecureContainer(cid, true);
        if (rc != StorageResultCode.OperationSucceeded) {
            Log.e(TAG, "Failed to unmount " + cid + " with rc " + rc);
            return false;
        }
        return true;
    } catch (RemoteException e) {
        Log.e(TAG, "MountService running?");
    }
        return false;
   }

   public static boolean renameSdDir(String oldId, String newId) {
       try {
           int rc = getMountService().renameSecureContainer(oldId, newId);
           if (rc != StorageResultCode.OperationSucceeded) {
               Log.e(TAG, "Failed to rename " + oldId + " to " +
                       newId + "with rc " + rc);
               return false;
           }
           return true;
       } catch (RemoteException e) {
           Log.i(TAG, "Failed ot rename  " + oldId + " to " + newId +
                   " with exception : " + e);
       }
       return false;
   }

   public static String getSdDir(String cid) {
       try {
            return getMountService().getSecureContainerPath(cid);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get container path for " + cid +
                " with exception " + e);
        }
        return null;
   }

   public static String getSdFilesystem(String cid) {
       try {
            return getMountService().getSecureContainerFilesystemPath(cid);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get container path for " + cid +
                " with exception " + e);
        }
        return null;
   }

    public static boolean finalizeSdDir(String cid) {
        try {
            int rc = getMountService().finalizeSecureContainer(cid);
            if (rc != StorageResultCode.OperationSucceeded) {
                Log.i(TAG, "Failed to finalize container " + cid);
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to finalize container " + cid +
                    " with exception " + e);
        }
        return false;
    }

    public static boolean destroySdDir(String cid) {
        try {
            if (localLOGV) Log.i(TAG, "Forcibly destroying container " + cid);
            int rc = getMountService().destroySecureContainer(cid, true);
            if (rc != StorageResultCode.OperationSucceeded) {
                Log.i(TAG, "Failed to destroy container " + cid);
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to destroy container " + cid +
                    " with exception " + e);
        }
        return false;
    }

    /* SPRD: support double sdcard
     * add for avoiding system dump when firing UMS @{
     * {@hide}
     */
     public static String[] getSecureContainerList(boolean externalStorage, boolean isInternlaSd) {
        try {
            return getMountService().getSecureContainerList(externalStorage,isInternlaSd);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get secure container list with exception" +
                    e);
        }
        return null;
    }
    /* @} */

    public static String[] getSecureContainerList() {
        try {
            return getMountService().getSecureContainerList();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get secure container list with exception" +
                    e);
        }
        return null;
    }

   public static boolean isContainerMounted(String cid) {
       try {
           return getMountService().isSecureContainerMounted(cid);
       } catch (RemoteException e) {
           Log.e(TAG, "Failed to find out if container " + cid + " mounted");
       }
       return false;
   }

    /**
     * Extract public files for the single given APK.
     */
    public static long extractPublicFiles(File apkFile, File publicZipFile)
            throws IOException {
        final FileOutputStream fstr;
        final ZipOutputStream publicZipOutStream;

        if (publicZipFile == null) {
            fstr = null;
            publicZipOutStream = null;
        } else {
            fstr = new FileOutputStream(publicZipFile);
            publicZipOutStream = new ZipOutputStream(fstr);
            Log.d(TAG, "Extracting " + apkFile + " to " + publicZipFile);
        }

        long size = 0L;

        try {
            final ZipFile privateZip = new ZipFile(apkFile.getAbsolutePath());
            try {
                // Copy manifest, resources.arsc and res directory to public zip
                for (final ZipEntry zipEntry : Collections.list(privateZip.entries())) {
                    final String zipEntryName = zipEntry.getName();
                    if ("AndroidManifest.xml".equals(zipEntryName)
                            || "resources.arsc".equals(zipEntryName)
                            || zipEntryName.startsWith("res/")) {
                        size += zipEntry.getSize();
                        if (publicZipFile != null) {
                            copyZipEntry(zipEntry, privateZip, publicZipOutStream);
                        }
                    }
                }
            } finally {
                try { privateZip.close(); } catch (IOException e) {}
            }

            if (publicZipFile != null) {
                publicZipOutStream.finish();
                publicZipOutStream.flush();
                FileUtils.sync(fstr);
                publicZipOutStream.close();
                FileUtils.setPermissions(publicZipFile.getAbsolutePath(), FileUtils.S_IRUSR
                        | FileUtils.S_IWUSR | FileUtils.S_IRGRP | FileUtils.S_IROTH, -1, -1);
            }
        } finally {
            IoUtils.closeQuietly(publicZipOutStream);
        }

        return size;
    }

    private static void copyZipEntry(ZipEntry zipEntry, ZipFile inZipFile,
            ZipOutputStream outZipStream) throws IOException {
        byte[] buffer = new byte[4096];
        int num;

        ZipEntry newEntry;
        if (zipEntry.getMethod() == ZipEntry.STORED) {
            // Preserve the STORED method of the input entry.
            newEntry = new ZipEntry(zipEntry);
        } else {
            // Create a new entry so that the compressed len is recomputed.
            newEntry = new ZipEntry(zipEntry.getName());
        }
        outZipStream.putNextEntry(newEntry);

        final InputStream data = inZipFile.getInputStream(zipEntry);
        try {
            while ((num = data.read(buffer)) > 0) {
                outZipStream.write(buffer, 0, num);
            }
            outZipStream.flush();
        } finally {
            IoUtils.closeQuietly(data);
        }
    }

    public static boolean fixSdPermissions(String cid, int gid, String filename) {
        try {
            int rc = getMountService().fixPermissionsSecureContainer(cid, gid, filename);
            if (rc != StorageResultCode.OperationSucceeded) {
                Log.i(TAG, "Failed to fixperms container " + cid);
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to fixperms container " + cid + " with exception " + e);
        }
        return false;
    }

    /**
     * Given a requested {@link PackageInfo#installLocation} and calculated
     * install size, pick the actual volume to install the app. Only considers
     * internal and private volumes, and prefers to keep an existing package on
     * its current volume.
     *
     * @return the {@link VolumeInfo#fsUuid} to install onto, or {@code null}
     *         for internal storage.
     */
    public static String resolveInstallVolume(Context context, String packageName,
            int installLocation, long sizeBytes) throws IOException {
        // TODO: handle existing apps installed in ASEC; currently assumes
        // they'll end up back on internal storage
        ApplicationInfo existingInfo = null;
        try {
            existingInfo = context.getPackageManager().getApplicationInfo(packageName,
                    PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (NameNotFoundException ignored) {
        }

        final StorageManager storageManager = context.getSystemService(StorageManager.class);
        final boolean fitsOnInternal = fitsOnInternal(context, sizeBytes);

        final ArraySet<String> allCandidates = new ArraySet<>();
        VolumeInfo bestCandidate = null;
        long bestCandidateAvailBytes = Long.MIN_VALUE;
        for (VolumeInfo vol : storageManager.getVolumes()) {
            if (vol.type == VolumeInfo.TYPE_PRIVATE && vol.isMountedWritable()) {
                final long availBytes = storageManager.getStorageBytesUntilLow(new File(vol.path));
                if (availBytes >= sizeBytes) {
                    allCandidates.add(vol.fsUuid);
                }
                if (availBytes >= bestCandidateAvailBytes) {
                    bestCandidate = vol;
                    bestCandidateAvailBytes = availBytes;
                }
            }
        }

        // System apps always forced to internal storage
        if (existingInfo != null && existingInfo.isSystemApp()) {
            installLocation = PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY;
        }

        // If app expresses strong desire for internal space, honor it
        if (installLocation == PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY) {
            if (fitsOnInternal) {
                return null;
            } else {
                throw new IOException("Requested internal only, but not enough space");
            }
        }

        // If app already exists somewhere, prefer to stay on that volume
        if (existingInfo != null) {
            if (existingInfo.volumeUuid == null && fitsOnInternal) {
                return null;
            }
            if (allCandidates.contains(existingInfo.volumeUuid)) {
                return existingInfo.volumeUuid;
            }
        }

        // We're left with either preferring external or auto, so just pick
        // volume with most space
        if (bestCandidate != null) {
            return bestCandidate.fsUuid;
        } else if (fitsOnInternal) {
            return null;
        } else {
            throw new IOException("No special requests, but no room anywhere");
        }
    }

    public static boolean fitsOnInternal(Context context, long sizeBytes) {
        final StorageManager storage = context.getSystemService(StorageManager.class);
        final File target = Environment.getDataDirectory();
        return (sizeBytes <= storage.getStorageBytesUntilLow(target));
    }

    public static boolean fitsOnExternal(Context context, long sizeBytes) {
        final StorageManager storage = context.getSystemService(StorageManager.class);
        /* SPRD: support double sdcard @{
         * @orig
        final StorageVolume primary = storage.getPrimaryVolume();
        return (sizeBytes > 0) && !primary.isEmulated()
                && Environment.MEDIA_MOUNTED.equals(primary.getState())
                && sizeBytes <= storage.getStorageBytesUntilLow(primary.getPathFile());
                */
        final File target = Environment.getExternalStoragePath();
        return ((sizeBytes > 0) && (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStoragePathState()))
               && (sizeBytes <= storage.getStorageBytesUntilLow(target)));
        /* @} */
    }

    /* SPRD: support double sdcard @{*/
    public static boolean fitsOnInternalSd(Context context, long sizeBytes) {
        final StorageManager storage = context.getSystemService(StorageManager.class);
        final File target = Environment.getInternalStoragePath();
        return ((sizeBytes > 0) && (Environment.MEDIA_MOUNTED.equals(Environment.getInternalStoragePathState()))
                && (sizeBytes <= storage.getStorageBytesUntilLow(target)));
        }
    /* @} */

    /**
     * Given a requested {@link PackageInfo#installLocation} and calculated
     * install size, pick the actual location to install the app.
     */
    public static int resolveInstallLocation(Context context, String packageName,
            int installLocation, long sizeBytes, int installFlags) {
        ApplicationInfo existingInfo = null;
        try {
            existingInfo = context.getPackageManager().getApplicationInfo(packageName,
                    PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (NameNotFoundException ignored) {
        }

        final int prefer;
        final boolean checkBoth;
        /* SPRD: support double sdcard
         * add install location @{
         */
        int installPreference = Settings.Global.getInt(context.getApplicationContext()
                 .getContentResolver(),
                 Settings.Global.DEFAULT_INSTALL_LOCATION,
                 PackageHelper.APP_INSTALL_AUTO);
        Log.w(TAG, "Settings defaultInstallLocation :"+installPreference);

        if ("android.deviceadmin.cts".equals(packageName)){
            // make package android.deviceadmin.cts only install internal
            prefer = RECOMMEND_INSTALL_INTERNAL;
            checkBoth = false;
        } else if (installPreference == PackageHelper.APP_INSTALL_INTERNAL) {
            prefer = RECOMMEND_INSTALL_INTERNAL;
            checkBoth = false;
        } else if (installPreference == PackageHelper.APP_INSTALL_EXTERNAL) {
            prefer = RECOMMEND_INSTALL_EXTERNAL;
            checkBoth = false;
        } else if (isInternalSDInstalld && installPreference == PackageHelper.APP_INSTALL_INTERNALSD) {
            prefer = RECOMMEND_INSTALL_INTERNALSD;
            checkBoth = false;
        } else if (installPreference == PackageHelper.APP_INSTALL_AUTO && flashType == NAND) {
            //default state install apk to external sd card if the flash type is nand
            prefer = RECOMMEND_INSTALL_EXTERNAL;
            checkBoth = false;
        } else if (isInternalSDInstalld && (installFlags & PackageManager.INSTALL_INTERNALSD) != 0) {
            prefer = RECOMMEND_INSTALL_INTERNALSD;
            checkBoth = false;
        } else
        /* @} */
        if ((installFlags & PackageManager.INSTALL_INTERNAL) != 0) {
            prefer = RECOMMEND_INSTALL_INTERNAL;
            checkBoth = false;
        } else if ((installFlags & PackageManager.INSTALL_EXTERNAL) != 0) {
            prefer = RECOMMEND_INSTALL_EXTERNAL;
            checkBoth = false;
        } else if (installLocation == PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY) {
            prefer = RECOMMEND_INSTALL_INTERNAL;
            checkBoth = false;
        } else if (installLocation == PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL) {
            prefer = RECOMMEND_INSTALL_EXTERNAL;
            checkBoth = true;
        } else if (installLocation == PackageInfo.INSTALL_LOCATION_AUTO) {
            // When app is already installed, prefer same medium
            if (existingInfo != null) {
                // TODO: distinguish if this is external ASEC
                if ((existingInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0) {
                    prefer = RECOMMEND_INSTALL_EXTERNAL;
                /* SPRD: support double sdcard
                 * add install location @{
                 */
                } else if ((existingInfo.flags & ApplicationInfo.FLAG_INTERNALSD_STORAGE) != 0) {
                    prefer = RECOMMEND_INSTALL_INTERNALSD;
                /* @} */
                } else {
                    prefer = RECOMMEND_INSTALL_INTERNAL;
                }
            } else {
                prefer = RECOMMEND_INSTALL_INTERNAL;
            }
            checkBoth = true;
        } else {
            prefer = RECOMMEND_INSTALL_INTERNAL;
            checkBoth = false;
        }

        /* SPRD: @{ */
        Log.w(TAG, "prefer installLocation:"+prefer);
        /* @} */
        /* SPRD: Add support for install apk to internal sdcard @{
         */
        final boolean internalSdemulated = Environment.internalIsEmulated();
        /* @} */
        boolean fitsOnInternal = false;
        if (checkBoth || prefer == RECOMMEND_INSTALL_INTERNAL) {
            fitsOnInternal = fitsOnInternal(context, sizeBytes);
        }

        boolean fitsOnExternal = false;
        if (checkBoth || prefer == RECOMMEND_INSTALL_EXTERNAL) {
            fitsOnExternal = fitsOnExternal(context, sizeBytes);
        }

        /* SPRD: support double sdcard
         * Add support for install apk to internal sdcard @{
         */
        boolean fitsOnInternalSd = false;
        if (isInternalSDInstalld && !internalSdemulated && (checkBoth || prefer == RECOMMEND_INSTALL_INTERNALSD)) {
            try {
                 fitsOnInternalSd = fitsOnInternalSd(context, sizeBytes);
            } catch (Exception e) {
                return PackageHelper.RECOMMEND_FAILED_INVALID_URI;
            }
        }
        /* @} */

        if (prefer == RECOMMEND_INSTALL_INTERNAL) {
            if (fitsOnInternal) {
                return PackageHelper.RECOMMEND_INSTALL_INTERNAL;
            }
        } else if (prefer == RECOMMEND_INSTALL_EXTERNAL) {
            if (fitsOnExternal) {
                return PackageHelper.RECOMMEND_INSTALL_EXTERNAL;
            }
        /* SPRD: support double sdcard @{ */
        } else if (!internalSdemulated && prefer == RECOMMEND_INSTALL_INTERNALSD) {
            if (fitsOnInternalSd) {
                return PackageHelper.RECOMMEND_INSTALL_INTERNALSD;
            }
        /* @} */
        }
        if (checkBoth) {
            if (fitsOnInternal) {
                return PackageHelper.RECOMMEND_INSTALL_INTERNAL;
            } else if (fitsOnExternal) {
                return PackageHelper.RECOMMEND_INSTALL_EXTERNAL;
            /* SPRD: support double sdcard @{ */
            } else if (!internalSdemulated && fitsOnInternalSd) {
                return PackageHelper.RECOMMEND_INSTALL_INTERNALSD;
            /* @} */
            }
        }

        return PackageHelper.RECOMMEND_FAILED_INSUFFICIENT_STORAGE;
    }

    public static long calculateInstalledSize(PackageLite pkg, boolean isForwardLocked,
            String abiOverride) throws IOException {
        NativeLibraryHelper.Handle handle = null;
        try {
            handle = NativeLibraryHelper.Handle.create(pkg);
            return calculateInstalledSize(pkg, handle, isForwardLocked, abiOverride);
        } finally {
            IoUtils.closeQuietly(handle);
        }
    }

    public static long calculateInstalledSize(PackageLite pkg, NativeLibraryHelper.Handle handle,
            boolean isForwardLocked, String abiOverride) throws IOException {
        long sizeBytes = 0;

        // Include raw APKs, and possibly unpacked resources
        for (String codePath : pkg.getAllCodePaths()) {
            final File codeFile = new File(codePath);
            sizeBytes += codeFile.length();

            if (isForwardLocked) {
                sizeBytes += PackageHelper.extractPublicFiles(codeFile, null);
            }
        }

        // Include all relevant native code
        sizeBytes += NativeLibraryHelper.sumNativeBinariesWithOverride(handle, abiOverride);
        /* SPRD: support double sdcard
         * space not enough,sizeMb var add one Mb @{
         */
        sizeBytes+=sizeBytes/10;
        /* @} */

        return sizeBytes;
    }

    public static String replaceEnd(String str, String before, String after) {
        if (!str.endsWith(before)) {
            throw new IllegalArgumentException(
                    "Expected " + str + " to end with " + before);
        }
        return str.substring(0, str.length() - before.length()) + after;
    }
}
