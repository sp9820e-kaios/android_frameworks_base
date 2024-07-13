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

package com.android.settingslib.wifi;

import android.app.AppGlobals;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.net.wifi.IWifiManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.SystemProperties;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TtsSpan;
import android.util.Log;
import android.util.LruCache;

import com.android.settingslib.R;

import java.util.ArrayList;
import java.util.Map;


public class AccessPoint implements Comparable<AccessPoint> {
    static final String TAG = "SettingsLib.AccessPoint";

    private static final boolean RESET_DEFAULT_HTTP_RESPONSE = SystemProperties.getBoolean("reset_default_http_response",true);

    /**
     * Lower bound on the 2.4 GHz (802.11b/g/n) WLAN channels
     */
    public static final int LOWER_FREQ_24GHZ = 2400;

    /**
     * Upper bound on the 2.4 GHz (802.11b/g/n) WLAN channels
     */
    public static final int HIGHER_FREQ_24GHZ = 2500;

    /**
     * Lower bound on the 5.0 GHz (802.11a/h/j/n/ac) WLAN channels
     */
    public static final int LOWER_FREQ_5GHZ = 4900;

    /**
     * Upper bound on the 5.0 GHz (802.11a/h/j/n/ac) WLAN channels
     */
    public static final int HIGHER_FREQ_5GHZ = 5900;


    /**
     * Experimental: we should be able to show the user the list of BSSIDs and bands
     *  for that SSID.
     *  For now this data is used only with Verbose Logging so as to show the band and number
     *  of BSSIDs on which that network is seen.
     */
    public LruCache<String, ScanResult> mScanResultCache = new LruCache<String, ScanResult>(32);

    private static final String KEY_NETWORKINFO = "key_networkinfo";
    private static final String KEY_WIFIINFO = "key_wifiinfo";
    private static final String KEY_SCANRESULT = "key_scanresult";
    private static final String KEY_SSID = "key_ssid";
    private static final String KEY_SECURITY = "key_security";
    private static final String KEY_PSKTYPE = "key_psktype";
    private static final String KEY_SCANRESULTCACHE = "key_scanresultcache";
    private static final String KEY_CONFIG = "key_config";

    /**
     * These values are matched in string arrays -- changes must be kept in sync
     */
    public static final int SECURITY_NONE = 0;
    public static final int SECURITY_WEP = 1;
    public static final int SECURITY_PSK = 2;
    public static final int SECURITY_EAP = 3;
    public static final int SECURITY_FT_PSK = 4;
    public static final int SECURITY_FT_EAP = 5;
    // Broadcom, WAPI
    public static final int SECURITY_WAPI_PSK = 6;
    public static final int SECURITY_WAPI_CERT = 7;
    // Broadcom, WAPI
    public static final int SECURITY_PSK_SHA256 = 8;
    public static final int SECURITY_EAP_SHA256 = 9;

    private static final int PSK_UNKNOWN = 0;
    private static final int PSK_WPA = 1;
    private static final int PSK_WPA2 = 2;
    private static final int PSK_WPA_WPA2 = 3;

    private static final int VISIBILITY_OUTDATED_AGE_IN_MILLI = 20000;
    private final Context mContext;

    private String ssid;
    private int security;
    private int networkId = WifiConfiguration.INVALID_NETWORK_ID;
    private boolean wpsAvailable;

    private int pskType = PSK_UNKNOWN;

    private WifiConfiguration mConfig;

    private int mRssi = Integer.MAX_VALUE;
    private long mSeen = 0;


    //NOTE: Add for SPRD Passpoint R1 Feature -->

    /**
     * If a configed passpoint cred match this accesspoint
     */
    private boolean mHasPasspointMatches = false;

    /**
     * corresponding HomeSP configkey
     */
    private String mPasspointCredIdentifier = null;

    /**
     * The BSSID for Passpoint cred matched
    */
    private String mPasspointMatchedBSSID = null;

    /**
     * The BSSID for Passpoint scanResult
    */
    private String mCachedPasspointBSSID = null;
    //<-- Add for SPRD Passpoint R1 Feature


    private WifiInfo mInfo;
    private NetworkInfo mNetworkInfo;
    private AccessPointListener mAccessPointListener;

    private Object mTag;

    public AccessPoint(Context context, Bundle savedState) {
        mContext = context;
        mConfig = savedState.getParcelable(KEY_CONFIG);
        if (mConfig != null) {
            loadConfig(mConfig);
        }
        if (savedState.containsKey(KEY_SSID)) {
            ssid = savedState.getString(KEY_SSID);
        }
        if (savedState.containsKey(KEY_SECURITY)) {
            security = savedState.getInt(KEY_SECURITY);
        }
        if (savedState.containsKey(KEY_PSKTYPE)) {
            pskType = savedState.getInt(KEY_PSKTYPE);
        }
        mInfo = (WifiInfo) savedState.getParcelable(KEY_WIFIINFO);
        if (savedState.containsKey(KEY_NETWORKINFO)) {
            mNetworkInfo = savedState.getParcelable(KEY_NETWORKINFO);
        }
        if (savedState.containsKey(KEY_SCANRESULTCACHE)) {
            ArrayList<ScanResult> scanResultArrayList =
                    savedState.getParcelableArrayList(KEY_SCANRESULTCACHE);
            mScanResultCache.evictAll();
            for (ScanResult result : scanResultArrayList) {
                mScanResultCache.put(result.BSSID, result);
            }
        }
        update(mConfig, mInfo, mNetworkInfo);
        mRssi = getRssi();
        mSeen = getSeen();

        //NOTE: Add for SPRD Passpoint R1 Feature -->
        mHasPasspointMatches = false;
        mPasspointCredIdentifier = null;
        mCachedPasspointBSSID = null;
        mPasspointMatchedBSSID = null;
        //<-- Add for SPRD Passpoint R1 Feature

    }

    public AccessPoint(Context context, ScanResult result) {
        mContext = context;
        initWithScanResult(result);
    }

    AccessPoint(Context context, WifiConfiguration config) {
        mContext = context;
        loadConfig(config);

        //NOTE: Add for SPRD Passpoint R1 Feature -->
        mHasPasspointMatches = false;
        mPasspointCredIdentifier = null;
        mCachedPasspointBSSID = null;
        mPasspointMatchedBSSID = null;
        //<-- Add for SPRD Passpoint R1 Feature

    }

    @Override
    public int compareTo(AccessPoint other) {
        // Active one goes first.
        if (isActive() && !other.isActive()) return -1;
        if (!isActive() && other.isActive()) return 1;

        // Reachable one goes before unreachable one.
        if (mRssi != Integer.MAX_VALUE && other.mRssi == Integer.MAX_VALUE) return -1;
        if (mRssi == Integer.MAX_VALUE && other.mRssi != Integer.MAX_VALUE) return 1;

        // Configured one goes before unconfigured one.
        if (networkId != WifiConfiguration.INVALID_NETWORK_ID
                && other.networkId == WifiConfiguration.INVALID_NETWORK_ID) return -1;
        if (networkId == WifiConfiguration.INVALID_NETWORK_ID
                && other.networkId != WifiConfiguration.INVALID_NETWORK_ID) return 1;

        // Sort by signal strength.
        int difference = WifiManager.compareSignalLevel(other.mRssi, mRssi);
        if (difference != 0) {
            return difference;
        }
        // Sort by ssid.
        return ssid.compareToIgnoreCase(other.ssid);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AccessPoint)) return false;
        return (this.compareTo((AccessPoint) other) == 0);
    }

    @Override
    public int hashCode() {
        int result = 0;
        if (mInfo != null) result += 13 * mInfo.hashCode();
        result += 19 * mRssi;
        result += 23 * networkId;
        result += 29 * ssid.hashCode();
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder().append("AccessPoint(")
                .append(ssid);
        if (isSaved()) {
            builder.append(',').append("saved");
        }
        if (isActive()) {
            builder.append(',').append("active");
        }
        if (isEphemeral()) {
            builder.append(',').append("ephemeral");
        }
        if (isConnectable()) {
            builder.append(',').append("connectable");
        }
        if (security != SECURITY_NONE) {
            builder.append(',').append(securityToString(security, pskType));
        }
        return builder.append(')').toString();
    }

    public boolean matches(ScanResult result) {
        return ssid.equals(result.SSID) && security == getSecurity(result);
    }

    public boolean matches(WifiConfiguration config) {
        if (config.isPasspoint() && mConfig != null && mConfig.isPasspoint())
            return config.FQDN.equals(mConfig.providerFriendlyName);
        else
            return ssid.equals(removeDoubleQuotes(config.SSID)) && security == getSecurity(config);
    }

    public WifiConfiguration getConfig() {
        return mConfig;
    }

    public void clearConfig() {
        mConfig = null;
        networkId = WifiConfiguration.INVALID_NETWORK_ID;
    }

    public void clearRssi() {
        mRssi = Integer.MAX_VALUE;
    }

    public WifiInfo getInfo() {
        return mInfo;
    }

    public int getLevel() {
        if (mRssi == Integer.MAX_VALUE) {
            return -1;
        }
        return WifiManager.calculateSignalLevel(mRssi, 4);
    }

    public int getRssi() {
        int rssi = Integer.MIN_VALUE;
        for (ScanResult result : mScanResultCache.snapshot().values()) {
            if (result.level > rssi) {
                rssi = result.level;
            }
        }

        return rssi;
    }

    public long getSeen() {
        long seen = 0;
        for (ScanResult result : mScanResultCache.snapshot().values()) {
            if (result.timestamp > seen) {
                seen = result.timestamp;
            }
        }

        return seen;
    }

    public NetworkInfo getNetworkInfo() {
        return mNetworkInfo;
    }

    public int getSecurity() {
        return security;
    }

    public String getSecurityString(boolean concise) {
        Context context = mContext;
        if (mConfig != null && mConfig.isPasspoint()) {
            return concise ? context.getString(R.string.wifi_security_short_eap) :
                context.getString(R.string.wifi_security_eap);
        }
        switch(security) {
            case SECURITY_EAP_SHA256:
                return context.getString(R.string.wifi_security_eap_sha256);
            case SECURITY_PSK_SHA256:
                return context.getString(R.string.wifi_security_psk_sha256);
            case SECURITY_FT_EAP:
                return context.getString(R.string.wifi_security_ft_eap);
            case SECURITY_FT_PSK:
                return context.getString(R.string.wifi_security_ft_psk);
            case SECURITY_EAP:
                return concise ? context.getString(R.string.wifi_security_short_eap) :
                    context.getString(R.string.wifi_security_eap);
            case SECURITY_PSK:
                switch (pskType) {
                    case PSK_WPA:
                        return concise ? context.getString(R.string.wifi_security_short_wpa) :
                            context.getString(R.string.wifi_security_wpa);
                    case PSK_WPA2:
                        return concise ? context.getString(R.string.wifi_security_short_wpa2) :
                            context.getString(R.string.wifi_security_wpa2);
                    case PSK_WPA_WPA2:
                        return concise ? context.getString(R.string.wifi_security_short_wpa_wpa2) :
                            context.getString(R.string.wifi_security_wpa_wpa2);
                    case PSK_UNKNOWN:
                    default:
                        return concise ? context.getString(R.string.wifi_security_short_psk_generic)
                                : context.getString(R.string.wifi_security_psk_generic);
                }
            case SECURITY_WEP:
                return concise ? context.getString(R.string.wifi_security_short_wep) :
                    context.getString(R.string.wifi_security_wep);
            // Broadcom, WAPI
            case SECURITY_WAPI_PSK:
                return context.getString(R.string.wifi_security_wapi_psk);
            case SECURITY_WAPI_CERT:
                return context.getString(R.string.wifi_security_wapi_cert);
            // Broadcom, WAPI
            case SECURITY_NONE:
            default:
                return concise ? "" : context.getString(R.string.wifi_security_none);
        }
    }

    public String getSsidStr() {
        return ssid;
    }

    public CharSequence getSsid() {
        SpannableString str = new SpannableString(ssid);
        str.setSpan(new TtsSpan.VerbatimBuilder(ssid).build(), 0, ssid.length(),
                Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        return str;
    }

    public String getConfigName() {
        if (mConfig != null && mConfig.isPasspoint()) {
            return mConfig.providerFriendlyName;
        } else {
            return ssid;
        }
    }

    public DetailedState getDetailedState() {
        return mNetworkInfo != null ? mNetworkInfo.getDetailedState() : null;
    }

    public String getSavedNetworkSummary() {
        if (mConfig != null) {
            PackageManager pm = mContext.getPackageManager();
            String systemName = pm.getNameForUid(android.os.Process.SYSTEM_UID);
            int userId = UserHandle.getUserId(mConfig.creatorUid);
            ApplicationInfo appInfo = null;
            if (mConfig.creatorName != null && mConfig.creatorName.equals(systemName)) {
                appInfo = mContext.getApplicationInfo();
            } else {
                try {
                    IPackageManager ipm = AppGlobals.getPackageManager();
                    appInfo = ipm.getApplicationInfo(mConfig.creatorName, 0 /* flags */, userId);
                } catch (RemoteException rex) {
                }
            }
            if (appInfo != null &&
                    !appInfo.packageName.equals(mContext.getString(R.string.settings_package)) &&
                    !appInfo.packageName.equals(
                    mContext.getString(R.string.certinstaller_package))) {
                return mContext.getString(R.string.saved_network, appInfo.loadLabel(pm));
            }
        }
        return "";
    }

    public String getSummary() {
        return getSettingsSummary();
    }

    public String getSettingsSummary() {
        // Update to new summary
        StringBuilder summary = new StringBuilder();

        if (isActive() && mConfig != null && mConfig.isPasspoint()) {
            // This is the active connection on passpoint
            summary.append(getSummary(mContext, getDetailedState(),
                    false, mConfig.providerFriendlyName));
        } else if (isActive()) {
            // This is the active connection on non-passpoint network
            summary.append(getSummary(mContext, getDetailedState(),
                    mInfo != null && mInfo.isEphemeral()));
        } else if (mConfig != null && mConfig.isPasspoint()) {
            String format = mContext.getString(R.string.available_via_passpoint);
            summary.append(String.format(format, mConfig.providerFriendlyName));
        } else if (mConfig != null && mConfig.hasNoInternetAccess()) {
            summary.append(mContext.getString(R.string.wifi_no_internet));
        } else if (mConfig != null && ((mConfig.status == WifiConfiguration.Status.DISABLED &&
                mConfig.disableReason != WifiConfiguration.DISABLED_UNKNOWN_REASON)
               || mConfig.autoJoinStatus
                >= WifiConfiguration.AUTO_JOIN_DISABLED_ON_AUTH_FAILURE)) {
            if (mConfig.autoJoinStatus
                    >= WifiConfiguration.AUTO_JOIN_DISABLED_ON_AUTH_FAILURE) {
                if (mConfig.disableReason == WifiConfiguration.DISABLED_DHCP_FAILURE) {
                    summary.append(mContext.getString(R.string.wifi_disabled_network_failure));
                } else if (mConfig.disableReason == WifiConfiguration.DISABLED_AUTH_FAILURE) {
                    summary.append(mContext.getString(R.string.wifi_disabled_password_failure));
                } else if (mConfig.disableReason == WifiConfiguration.DISABLED_BY_WIFI_MANAGER) {
                    summary.append(mContext.getString(R.string.wifi_disabled_generic));
                } else {
                    summary.append(mContext.getString(R.string.wifi_disabled_wifi_failure));
                }
            } else {
                switch (mConfig.disableReason) {
                    case WifiConfiguration.DISABLED_AUTH_FAILURE:
                        summary.append(mContext.getString(R.string.wifi_disabled_password_failure));
                        break;
                    case WifiConfiguration.DISABLED_DHCP_FAILURE:
                    case WifiConfiguration.DISABLED_DNS_FAILURE:
                        summary.append(mContext.getString(R.string.wifi_disabled_network_failure));
                        break;
                    case WifiConfiguration.DISABLED_UNKNOWN_REASON:
                    case WifiConfiguration.DISABLED_ASSOCIATION_REJECT:
                        summary.append(mContext.getString(R.string.wifi_disabled_generic));
                        break;
                }
            }
        } else if (mRssi == Integer.MAX_VALUE) { // Wifi out of range
            summary.append(mContext.getString(R.string.wifi_not_in_range));
        } else { // In range, not disabled.
            if (mConfig != null) { // Is saved network
                summary.append(mContext.getString(R.string.wifi_remembered));
            }
            /* SPRD: add ap summary for cmcc wifi features @{ */
            if (mConfig == null && WifiManager.SUPPORT_CMCC) {
                if (security != SECURITY_NONE) {
                    String securityStrFormat;
                    if (summary.length() == 0) {
                        securityStrFormat = mContext.getString(R.string.wifi_secured_first_item);
                    } else {
                        securityStrFormat = mContext.getString(R.string.wifi_secured_second_item);
                    }
                    summary.append(String.format(securityStrFormat, getSecurityString(true)));
                }
                // Only list WPS available for unsaved networks
                if (wpsAvailable) {
                    if (summary.length() == 0) {
                        summary.append(mContext.getString(R.string.wifi_wps_available_first_item));
                    } else {
                        summary.append(mContext.getString(R.string.wifi_wps_available_second_item));
                    }
                }
            }
            /* @} */
        }

        if (WifiTracker.sVerboseLogging > 0) {
            // Add RSSI/band information for this config, what was seen up to 6 seconds ago
            // verbose WiFi Logging is only turned on thru developers settings
            if (mInfo != null && mNetworkInfo != null) { // This is the active connection
                summary.append(" f=" + Integer.toString(mInfo.getFrequency()));
            }
            summary.append(" " + getVisibilityStatus());
            if (mConfig != null && mConfig.autoJoinStatus > 0) {
                summary.append(" (" + mConfig.autoJoinStatus);
                if (mConfig.blackListTimestamp > 0) {
                    long now = System.currentTimeMillis();
                    long diff = (now - mConfig.blackListTimestamp)/1000;
                    long sec = diff%60; //seconds
                    long min = (diff/60)%60; //minutes
                    long hour = (min/60)%60; //hours
                    summary.append(", ");
                    if (hour > 0) summary.append(Long.toString(hour) + "h ");
                    summary.append( Long.toString(min) + "m ");
                    summary.append( Long.toString(sec) + "s ");
                }
                summary.append(")");
            }
            if (mConfig != null && mConfig.numIpConfigFailures > 0) {
                summary.append(" ipf=").append(mConfig.numIpConfigFailures);
            }
            if (mConfig != null && mConfig.numConnectionFailures > 0) {
                summary.append(" cf=").append(mConfig.numConnectionFailures);
            }
            if (mConfig != null && mConfig.numAuthFailures > 0) {
                summary.append(" authf=").append(mConfig.numAuthFailures);
            }
            if (mConfig != null && mConfig.numNoInternetAccessReports > 0) {
                summary.append(" noInt=").append(mConfig.numNoInternetAccessReports);
            }
        }
        return summary.toString();
    }

    /**
     * Returns the visibility status of the WifiConfiguration.
     *
     * @return autojoin debugging information
     * TODO: use a string formatter
     * ["rssi 5Ghz", "num results on 5GHz" / "rssi 5Ghz", "num results on 5GHz"]
     * For instance [-40,5/-30,2]
     */
    private String getVisibilityStatus() {
        StringBuilder visibility = new StringBuilder();
        StringBuilder scans24GHz = null;
        StringBuilder scans5GHz = null;
        String bssid = null;

        long now = System.currentTimeMillis();

        if (mInfo != null) {
            bssid = mInfo.getBSSID();
            if (bssid != null) {
                visibility.append(" ").append(bssid);
            }
            visibility.append(" rssi=").append(mInfo.getRssi());
            visibility.append(" ");
            visibility.append(" score=").append(mInfo.score);
            visibility.append(String.format(" tx=%.1f,", mInfo.txSuccessRate));
            visibility.append(String.format("%.1f,", mInfo.txRetriesRate));
            visibility.append(String.format("%.1f ", mInfo.txBadRate));
            visibility.append(String.format("rx=%.1f", mInfo.rxSuccessRate));
        }

        int rssi5 = WifiConfiguration.INVALID_RSSI;
        int rssi24 = WifiConfiguration.INVALID_RSSI;
        int num5 = 0;
        int num24 = 0;
        int numBlackListed = 0;
        int n24 = 0; // Number scan results we included in the string
        int n5 = 0; // Number scan results we included in the string
        Map<String, ScanResult> list = mScanResultCache.snapshot();
        // TODO: sort list by RSSI or age
        for (ScanResult result : list.values()) {
            if (result.seen == 0)
                continue;

            if (result.autoJoinStatus != ScanResult.ENABLED) numBlackListed++;

            if (result.frequency >= LOWER_FREQ_5GHZ
                    && result.frequency <= HIGHER_FREQ_5GHZ) {
                // Strictly speaking: [4915, 5825]
                // number of known BSSID on 5GHz band
                num5 = num5 + 1;
            } else if (result.frequency >= LOWER_FREQ_24GHZ
                    && result.frequency <= HIGHER_FREQ_24GHZ) {
                // Strictly speaking: [2412, 2482]
                // number of known BSSID on 2.4Ghz band
                num24 = num24 + 1;
            }

            // Ignore results seen, older than 20 seconds
            if (now - result.seen > VISIBILITY_OUTDATED_AGE_IN_MILLI) continue;

            if (result.frequency >= LOWER_FREQ_5GHZ
                    && result.frequency <= HIGHER_FREQ_5GHZ) {
                if (result.level > rssi5) {
                    rssi5 = result.level;
                }
                if (n5 < 4) {
                    if (scans5GHz == null) scans5GHz = new StringBuilder();
                    scans5GHz.append(" \n{").append(result.BSSID);
                    if (bssid != null && result.BSSID.equals(bssid)) scans5GHz.append("*");
                    scans5GHz.append("=").append(result.frequency);
                    scans5GHz.append(",").append(result.level);
                    if (result.autoJoinStatus != 0) {
                        scans5GHz.append(",st=").append(result.autoJoinStatus);
                    }
                    if (result.numIpConfigFailures != 0) {
                        scans5GHz.append(",ipf=").append(result.numIpConfigFailures);
                    }
                    scans5GHz.append("}");
                    n5++;
                }
            } else if (result.frequency >= LOWER_FREQ_24GHZ
                    && result.frequency <= HIGHER_FREQ_24GHZ) {
                if (result.level > rssi24) {
                    rssi24 = result.level;
                }
                if (n24 < 4) {
                    if (scans24GHz == null) scans24GHz = new StringBuilder();
                    scans24GHz.append(" \n{").append(result.BSSID);
                    if (bssid != null && result.BSSID.equals(bssid)) scans24GHz.append("*");
                    scans24GHz.append("=").append(result.frequency);
                    scans24GHz.append(",").append(result.level);
                    if (result.autoJoinStatus != 0) {
                        scans24GHz.append(",st=").append(result.autoJoinStatus);
                    }
                    if (result.numIpConfigFailures != 0) {
                        scans24GHz.append(",ipf=").append(result.numIpConfigFailures);
                    }
                    scans24GHz.append("}");
                    n24++;
                }
            }
        }
        visibility.append(" [");
        if (num24 > 0) {
            visibility.append("(").append(num24).append(")");
            if (n24 <= 4) {
                if (scans24GHz != null) {
                    visibility.append(scans24GHz.toString());
                }
            } else {
                visibility.append("max=").append(rssi24);
                if (scans24GHz != null) {
                    visibility.append(",").append(scans24GHz.toString());
                }
            }
        }
        visibility.append(";");
        if (num5 > 0) {
            visibility.append("(").append(num5).append(")");
            if (n5 <= 4) {
                if (scans5GHz != null) {
                    visibility.append(scans5GHz.toString());
                }
            } else {
                visibility.append("max=").append(rssi5);
                if (scans5GHz != null) {
                    visibility.append(",").append(scans5GHz.toString());
                }
            }
        }
        if (numBlackListed > 0)
            visibility.append("!").append(numBlackListed);
        visibility.append("]");

        return visibility.toString();
    }

    /**
     * Return whether this is the active connection.
     * For ephemeral connections (networkId is invalid), this returns false if the network is
     * disconnected.
     */
    public boolean isActive() {
        return mNetworkInfo != null &&
                (networkId != WifiConfiguration.INVALID_NETWORK_ID ||
                 mNetworkInfo.getState() != State.DISCONNECTED);
    }

    public boolean isConnectable() {
        return getLevel() != -1 && getDetailedState() == null;
    }

    public boolean isEphemeral() {
        return mInfo != null && mInfo.isEphemeral() &&
                mNetworkInfo != null && mNetworkInfo.getState() != State.DISCONNECTED;
    }

    public boolean isPasspoint() {
        return (mConfig != null && mConfig.isPasspoint()) || (mCachedPasspointBSSID != null); //
    }

    /**
     * Return whether the given {@link WifiInfo} is for this access point.
     * If the current AP does not have a network Id then the config is used to
     * match based on SSID and security.
     */
    private boolean isInfoForThisAccessPoint(WifiConfiguration config, WifiInfo info) {
        if (isPasspoint() == false && networkId != WifiConfiguration.INVALID_NETWORK_ID) {
            return networkId == info.getNetworkId();
        } else if (config != null) {
            return matches(config);
        }
        else {
            // Might be an ephemeral connection with no WifiConfiguration. Try matching on SSID.
            // (Note that we only do this if the WifiConfiguration explicitly equals INVALID).
            // TODO: Handle hex string SSIDs.
            return ssid.equals(removeDoubleQuotes(info.getSSID()));
        }
    }

    public boolean isSaved() {
        return networkId != WifiConfiguration.INVALID_NETWORK_ID;
    }

    public Object getTag() {
        return mTag;
    }

    public void setTag(Object tag) {
        mTag = tag;
    }

    /**
     * Generate and save a default wifiConfiguration with common values.
     * Can only be called for unsecured networks.
     */
    public void generateOpenNetworkConfig() {
        if (security != SECURITY_NONE)
            throw new IllegalStateException();
        if (mConfig != null)
            return;
        mConfig = new WifiConfiguration();
        mConfig.SSID = AccessPoint.convertToQuotedString(ssid);
        mConfig.allowedKeyManagement.set(KeyMgmt.NONE);
    }



    //NOTE: Add for SPRD Passpoint R1 Feature -->

    /**
     * Generate and save a default wifiConfiguration with common values.
     * Can only for a passpoint network.
     */
    public boolean generateSimplePasspointNetworkConfig() {
        if (security != SECURITY_EAP || !mHasPasspointMatches || mPasspointCredIdentifier == null)
            return false;
        if (mConfig != null)
            return true;
        mConfig = new WifiConfiguration();
        mConfig.SSID = AccessPoint.convertToQuotedString(ssid);
        //this is probably wrong, as we don't have a way to enter the enterprise config
        mConfig.allowedKeyManagement.set(KeyMgmt.WPA_EAP);
        //mConfig.allowedKeyManagement.set(KeyMgmt.IEEE8021X);
        mConfig.createdFromPasspointCred = true;
        mConfig.passpointCredIdentifier = mPasspointCredIdentifier;
        return true;
    }

    public boolean hasPasspointMatch() {
        Log.w(TAG, "SSID:" + ssid + " hasPasspointMatch: (security: " + security + " mHasPasspointMatches:" + " id:" + mPasspointCredIdentifier + ")");

        return (security == SECURITY_EAP && mHasPasspointMatches  && mPasspointCredIdentifier != null);
    }

    public void clearPasspointMatch() {
        mHasPasspointMatches = false;
        mPasspointCredIdentifier = null;
    }

    public String getPasspointCredID() {
        return mPasspointCredIdentifier;
    }
    //<-- Add for SPRD Passpoint R1 Feature



    void loadConfig(WifiConfiguration config) {
        if (config.isPasspoint())
            ssid = config.providerFriendlyName;
        else
            ssid = (config.SSID == null ? "" : removeDoubleQuotes(config.SSID));

        security = getSecurity(config);
        networkId = config.networkId;
        mConfig = config;
    }

    private void initWithScanResult(ScanResult result) {
        ssid = result.SSID;
        security = getSecurity(result);
        wpsAvailable = security != SECURITY_EAP && result.capabilities.contains("WPS");
        if (security == SECURITY_PSK)
            pskType = getPskType(result);
        mRssi = result.level;
        mSeen = result.timestamp;

        //NOTE: Add for SPRD Passpoint R1 Feature -->
        mHasPasspointMatches = result.hasPasspointMatches;
        mPasspointCredIdentifier = result.passpointCredIdentifier;
        if (result.hasPasspointMatches)
            mPasspointMatchedBSSID = result.BSSID;
        if (result.isPasspointNetwork())
            mCachedPasspointBSSID = result.BSSID;
        //<-- Add for SPRD Passpoint R1 Feature

    }

    public void saveWifiState(Bundle savedState) {
        if (ssid != null) savedState.putString(KEY_SSID, getSsidStr());
        savedState.putInt(KEY_SECURITY, security);
        savedState.putInt(KEY_PSKTYPE, pskType);
        if (mConfig != null) savedState.putParcelable(KEY_CONFIG, mConfig);
        savedState.putParcelable(KEY_WIFIINFO, mInfo);
        savedState.putParcelableArrayList(KEY_SCANRESULTCACHE,
                new ArrayList<ScanResult>(mScanResultCache.snapshot().values()));
        if (mNetworkInfo != null) {
            savedState.putParcelable(KEY_NETWORKINFO, mNetworkInfo);
        }
    }

    public void setListener(AccessPointListener listener) {
        mAccessPointListener = listener;
    }

    boolean update(ScanResult result) {
        if (matches(result)) {
            /* Update the LRU timestamp, if BSSID exists */
            mScanResultCache.get(result.BSSID);

            /* Add or update the scan result for the BSSID */
            mScanResultCache.put(result.BSSID, result);

            int oldLevel = getLevel();
            int oldRssi = getRssi();
            mSeen = getSeen();
            mRssi = (getRssi() + oldRssi)/2;
            int newLevel = getLevel();

            if (newLevel > 0 && newLevel != oldLevel && mAccessPointListener != null) {
                mAccessPointListener.onLevelChanged(this);
            }
            // This flag only comes from scans, is not easily saved in config
            if (security == SECURITY_PSK) {
                pskType = getPskType(result);
            }

            if (mAccessPointListener != null) {
                mAccessPointListener.onAccessPointChanged(this);
            }

            //NOTE: Add for SPRD Passpoint R1 Feature -->
            if (result.hasPasspointMatches && !mHasPasspointMatches) {
                mHasPasspointMatches = result.hasPasspointMatches;
                mPasspointCredIdentifier = result.passpointCredIdentifier;
                mPasspointMatchedBSSID = result.BSSID;
                Log.w(TAG, "Update SSID:" + ssid + " hasPasspointMatch: (security: " + security + " mHasPasspointMatches:" +mHasPasspointMatches + " id:" + mPasspointCredIdentifier + ")");
            } else if (mPasspointMatchedBSSID != null && mPasspointMatchedBSSID.equals(result.BSSID)
                && !result.hasPasspointMatches) {
                mHasPasspointMatches = false;
                mPasspointCredIdentifier = null;
                mPasspointMatchedBSSID = null;
            }

            if (result.isPasspointNetwork()) {
                mCachedPasspointBSSID = result.BSSID;
                Log.w(TAG, "Update SSID:" + ssid + " isPasspointAP ");
            } else if (mCachedPasspointBSSID != null && mCachedPasspointBSSID.equals(result.BSSID)
                && !result.isPasspointNetwork()) {
                mCachedPasspointBSSID = null;
            }
            //<-- Add for SPRD Passpoint R1 Feature

            return true;
        }
        return false;
    }

    boolean update(WifiConfiguration config, WifiInfo info, NetworkInfo networkInfo) {
        boolean reorder = false;
        if (info != null && isInfoForThisAccessPoint(config, info)) {
            reorder = (mInfo == null);
            mRssi = info.getRssi();
            mInfo = info;
            mNetworkInfo = networkInfo;
            if (mAccessPointListener != null) {
                mAccessPointListener.onAccessPointChanged(this);
            }
        } else if (mInfo != null) {
            reorder = true;
            mInfo = null;
            mNetworkInfo = null;
            if (mAccessPointListener != null) {
                mAccessPointListener.onAccessPointChanged(this);
            }
        }
        return reorder;
    }

    void update(WifiConfiguration config) {
        mConfig = config;
        networkId = config.networkId;
        if (mAccessPointListener != null) {
            mAccessPointListener.onAccessPointChanged(this);
        }
    }

    public static String getSummary(Context context, String ssid, DetailedState state,
            boolean isEphemeral, String passpointProvider) {
        if(state == null){
            return "";
        }

        if (state == DetailedState.CONNECTED && ssid == null) {
            if (TextUtils.isEmpty(passpointProvider) == false) {
                // Special case for connected + passpoint networks.
                String format = context.getString(R.string.connected_via_passpoint);
                return String.format(format, passpointProvider);
            } else if (isEphemeral) {
                // Special case for connected + ephemeral networks.
                return context.getString(R.string.connected_via_wfa);
            }
        }

        // Case when there is wifi connected without internet connectivity.
        final ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (state == DetailedState.CONNECTED && !RESET_DEFAULT_HTTP_RESPONSE) {
            IWifiManager wifiManager = IWifiManager.Stub.asInterface(
                    ServiceManager.getService(Context.WIFI_SERVICE));
            Network nw;

            try {
                nw = wifiManager.getCurrentNetwork();
            } catch (RemoteException e) {
                nw = null;
            }
            NetworkCapabilities nc = cm.getNetworkCapabilities(nw);
            if (nc != null && !nc.hasCapability(nc.NET_CAPABILITY_VALIDATED)) {
                return context.getString(R.string.wifi_connected_no_internet);
            }
        }

        String[] formats = context.getResources().getStringArray((ssid == null)
                ? R.array.wifi_status : R.array.wifi_status_with_ssid);
        int index = state.ordinal();

        if (index >= formats.length || formats[index].length() == 0) {
            return "";
        }
        return String.format(formats[index], ssid);
    }

    public static String getSummary(Context context, DetailedState state, boolean isEphemeral) {
        return getSummary(context, null, state, isEphemeral, null);
    }

    public static String getSummary(Context context, DetailedState state, boolean isEphemeral,
            String passpointProvider) {
        return getSummary(context, null, state, isEphemeral, passpointProvider);
    }

    public static String convertToQuotedString(String string) {
        return "\"" + string + "\"";
    }

    private static int getPskType(ScanResult result) {
        boolean wpa = result.capabilities.contains("WPA-PSK");
        boolean wpa2 = result.capabilities.contains("WPA2-PSK");
        if (wpa2 && wpa) {
            return PSK_WPA_WPA2;
        } else if (wpa2) {
            return PSK_WPA2;
        } else if (wpa) {
            return PSK_WPA;
        } else {
            Log.w(TAG, "Received abnormal flag string: " + result.capabilities);
            return PSK_UNKNOWN;
        }
    }

    private static int getSecurity(ScanResult result) {
        if (result.capabilities.contains("PSK-SHA256")) {
            return SECURITY_PSK_SHA256;
        }
        if (result.capabilities.contains("EAP-SHA256")) {
            return SECURITY_EAP_SHA256;
        }
        if (result.capabilities.contains("FT/PSK")) {
            return SECURITY_FT_PSK;
        }
        if (result.capabilities.contains("FT/EAP")) {
            return SECURITY_FT_EAP;
        }
        // Broadcom, WAPI
        if (result.capabilities.contains("WAPI-PSK")) {
            return SECURITY_WAPI_PSK;
        } else if (result.capabilities.contains("WAPI-CERT")) {
            return SECURITY_WAPI_CERT;
        } else
        // Broadcom, WAPI

        if (result.capabilities.contains("WEP")) {
            return SECURITY_WEP;
        } else if (result.capabilities.contains("PSK")) {
            return SECURITY_PSK;
        } else if (result.capabilities.contains("EAP")) {
            return SECURITY_EAP;
        }
        return SECURITY_NONE;
    }

    static int getSecurity(WifiConfiguration config) {
        if (config.allowedKeyManagement.get(KeyMgmt.PSK_SHA256)) {
            return SECURITY_PSK_SHA256;
        }
        if (config.allowedKeyManagement.get(KeyMgmt.EAP_SHA256)) {
            return SECURITY_EAP_SHA256;
        }
        if (config.allowedKeyManagement.get(KeyMgmt.FT_PSK)) {
            return SECURITY_FT_PSK;
        }
        if (config.allowedKeyManagement.get(KeyMgmt.FT_EAP)) {
            return SECURITY_FT_EAP;
        }
        if (config.allowedKeyManagement.get(KeyMgmt.WPA_PSK)) {
            return SECURITY_PSK;
        }
        if (config.allowedKeyManagement.get(KeyMgmt.WPA_EAP) ||
                config.allowedKeyManagement.get(KeyMgmt.IEEE8021X)) {
            return SECURITY_EAP;
        }

        // Broadcom, WAPI
        if (config.allowedKeyManagement.get(KeyMgmt.WAPI_PSK)) {
            return SECURITY_WAPI_PSK;
        }
        if (config.allowedKeyManagement.get(KeyMgmt.WAPI_CERT)) {
            return SECURITY_WAPI_CERT;
        }
        // Broadcom, WAPI

        for (int i = 0; i < config.wepKeys.length; i++) {
            if (config.wepKeys[i] != null) {
                return SECURITY_WEP;
            }
        }
        return SECURITY_NONE;
    }

    public static String securityToString(int security, int pskType) {
        if (security == SECURITY_FT_PSK) {
            return "FT-PSK";
        } else if (security == SECURITY_FT_EAP) {
            return "FT-EAP";
        } else
        // Broadcom, WAPI
        if (security == SECURITY_WAPI_PSK) {
            return "WAPI-PSK";
        } else if (security == SECURITY_WAPI_CERT) {
            return "WAPI-CERT";
        } else
        // Broadcom, WAPI

        if (security == SECURITY_WEP) {
            return "WEP";
        } else if (security == SECURITY_PSK) {
            if (pskType == PSK_WPA) {
                return "WPA";
            } else if (pskType == PSK_WPA2) {
                return "WPA2";
            } else if (pskType == PSK_WPA_WPA2) {
                return "WPA_WPA2";
            }
            return "PSK";
        } else if (security == SECURITY_EAP) {
            return "EAP";
        }
        return "NONE";
    }

    static String removeDoubleQuotes(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"')
                && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    public interface AccessPointListener {
        void onAccessPointChanged(AccessPoint accessPoint);
        void onLevelChanged(AccessPoint accessPoint);
    }
}
