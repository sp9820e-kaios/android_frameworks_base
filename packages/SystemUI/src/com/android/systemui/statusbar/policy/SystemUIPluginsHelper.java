
package com.android.systemui.statusbar.policy;

import android.app.AddonManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;

import android.util.Log;

import com.android.systemui.R;
import com.android.systemui.SystemUiConfig;
import com.android.internal.telephony.TeleUtils;

public class SystemUIPluginsHelper {
    static SystemUIPluginsHelper mInstance;

    public static final String TAG = "SystemUIPluginsHelper";
    public static final int[][] TELEPHONY_SIGNAL_STRENGTH =
            TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH;
    public static final int ABSENT_SIM_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_SIM_COLOR = 0xFF00796b;
    public static final int[] COLORS = {
            0xFF18FFFF, 0xFFFFEB3B
    };
    private CarrierConfigManager mCCM = null;
    /* SPRD: modify by BUG 474976 @{ */
    protected String RAT_4G = "4G";
    protected String RAT_3G = "3G";
    protected String RAT_2G = "2G";
    protected String RAT_LTE = "4GLTE";
    /* @} */

    public SystemUIPluginsHelper() {
    }

    public static SystemUIPluginsHelper getInstance() {
        if (mInstance != null)
            return mInstance;
        mInstance = (SystemUIPluginsHelper) AddonManager.getDefault()
                .getAddon(R.string.feature_display_for_operator, SystemUIPluginsHelper.class);
        return mInstance;
    }

    public String updateNetworkName(Context context, boolean showSpn, String spn, boolean showPlmn,
            String plmn) {
        Log.d(TAG, "updateNetworkName showSpn = " + showSpn + " spn = " + spn
                + " showPlmn = " + showPlmn + " plmn = " + plmn);
        return "";
    }

    public int[][] getSignalStrengthIcons(int phoneId) {
        return TELEPHONY_SIGNAL_STRENGTH;
    }

    public int getSignalStrengthColor(int subId){
        int phoneId = SubscriptionManager.getSlotId(subId);
        return phoneId == 0 ? COLORS[0] :  COLORS[1];
    }

    public int getNoSimIconId() {
        return R.drawable.stat_sys_no_sim_ex;
    }

    public int getNoServiceIconId() {
        return R.drawable.stat_sys_signal_null;
    }

    public int getSimCardIconId(int subId) {
        return 0;
    }

    public int getSimStandbyIconId() {
        // SPRD: modify for bug495410
        return R.drawable.stat_sys_signal_standby_ex;
    }

    /* SPRD: add for H+ icons for bug494075 @{ */
    public boolean hspaDataDistinguishable(Context context, int phoneId) {
        CarrierConfigManager configManager =(CarrierConfigManager)context.
               getSystemService(Context.CARRIER_CONFIG_SERVICE);
        boolean hspaDataDistinguishable = false;
        if (configManager.getConfigForPhoneId(phoneId) != null) {
            hspaDataDistinguishable = configManager.getConfigForPhoneId(phoneId)
                    .getBoolean(CarrierConfigManager.KEY_HSPADATADISTINGUISHABLE_BOOL);
        }
        return hspaDataDistinguishable;
    }
    /* @} */

    /**
     * SPRD:TELCEL Feature: DataTypeIcon disappear when data disabled. @{
     * @return
     */
    public boolean needShowDataTypeIcon() {
        Log.d(TAG, "needShowDataTypeIcon : true");
        return true;
    }
    /** @} */

    /* SPRD: Add for BUG 510725 to controll 4G switch show. @{ */
    public boolean show4GInQS() {
        return true;
    }
    /* @} */

    /* SPRD: Reliance UI spec 1.7. See bug #522899. @{ */
    public boolean isReliance() {
        return false;
    }

    public int getLteIconId() {
        return R.drawable.stat_sys_data_fully_connected_4g_ex;
    }

    public int getMobileGroupLayout() {
        return R.layout.mobile_signal_group_ex;
    }

    public int getRoamIcon() {
        return R.drawable.stat_sys_data_connected_roam_ex;
    }

    public int getLteIcon() {
        return R.drawable.stat_sys_data_fully_connected_lte_ex;
    }

    public int getGIcon() {
        return R.drawable.stat_sys_data_fully_connected_g_ex;
    }

    public int getEIcon() {
        return R.drawable.stat_sys_data_fully_connected_e_ex;
    }

    public int getHIcon() {
        return R.drawable.stat_sys_data_fully_connected_h_ex;
    }

    public int getHPIcon() {
        return R.drawable.stat_sys_data_fully_connected_hp_ex;
    }

    public int getThreeGIcon() {
        return R.drawable.stat_sys_data_fully_connected_3g_ex;
    }

    public int getFourGIcon() {
        return R.drawable.stat_sys_data_fully_connected_4g_ex;
    }

    public int getOneXIcon() {
        return R.drawable.stat_sys_data_fully_connected_1x_ex;
    }

    public int getDataInOutIcon() {
        return R.drawable.stat_sys_data_inout_ex;
    }

    public int getDataInIcon() {
        return R.drawable.stat_sys_data_in_ex;
    }

    public int getDataOutIcon() {
        return R.drawable.stat_sys_data_out_ex;
    }

    public int getDataDefaultIcon() {
        return R.drawable.stat_sys_data_default_ex;
    }

    public int getSignalZeroIcon() {
        return R.drawable.stat_sys_signal_0_ex;
    }

    public int getSignalOneIcon() {
        return R.drawable.stat_sys_signal_1_ex;
    }

    public int getSignalTwoIcon() {
        return R.drawable.stat_sys_signal_2_ex;
    }

    public int getSignalThreeIcon() {
        return R.drawable.stat_sys_signal_3_ex;
    }

    public int getSignalFourIcon() {
        return R.drawable.stat_sys_signal_4_ex;
    }

    public int getVoLTEIcon() {
        return R.drawable.stat_sys_volte;
    }

    public int getSignalVoLTEIcon() {
        return 0;
    }
    /* @} */


    /* SPRD: add for BUG 474780 @{ */
    public boolean showDataUseInterface() {
        return true;
    }
    /* @} */

    /* SPRD: modify by BUG 474976 @{ */
    protected String getNetworkTypeToString(Context context, int ratInt, ServiceState state) {
        String ratClassName = "";
        switch (ratInt) {
            case TelephonyManager.NETWORK_CLASS_2_G:
                boolean showRat2G = getBoolShowRatFor2G();
                Log.d(TAG, "showRat2G : " + showRat2G);
                ratClassName = showRat2G ? RAT_2G : "";
                break;
            case TelephonyManager.NETWORK_CLASS_3_G:
                Log.d(TAG, "showRat3G : " + show3G(state));
                ratClassName = show3G(state) ? RAT_3G : "";
                break;
            case TelephonyManager.NETWORK_CLASS_4_G:
                Log.d(TAG, "showLteFor4G : " + showLteFor4G());
                ratClassName = showLteFor4G() ? RAT_LTE : RAT_4G;
                break;
        }
        return ratClassName;
    }

    protected String appendRatToNetworkName(Context context, ServiceState state, String operator) {
        String operatorName = operator;
        if (context == null || state == null) return operatorName;
        SystemUiConfig config = SystemUiConfig.getInstance(context);
        /* SPRD: modify by BUG 554872 @{ */
        String emergencyCall = Resources.getSystem()
                .getText(com.android.internal.R.string.emergency_calls_only).toString();
        /* @} */
        /* SPRD: modify by BUG 577539 @{ */
        String noService = Resources.getSystem()
                .getText(com.android.internal.R.string.lockscreen_carrier_default).toString();
        if (state.getVoiceRegState() == ServiceState.STATE_IN_SERVICE) {
            if (operator.equals(emergencyCall) || operator.equals(noService)) {
                if (state.getOperatorAlphaLong() != null) {
                    operatorName = TeleUtils.updateOperator(state.getOperatorAlphaLong(),
                            "operator");
                }
            }
        }
        /* @} */
        if (!getBoolAppendRAT() || operatorName.equals(emergencyCall)
                || operatorName.equals(noService)) {
            return operatorName;
        }

        /* SPRD: add for BUG 536878 @{ */
        if (operatorName != null && operatorName.matches(".*[2-4]G$")) {
            return operatorName;
        }
        /* @} */

        if (state.getDataRegState() == ServiceState.STATE_IN_SERVICE
                || state.getVoiceRegState() == ServiceState.STATE_IN_SERVICE) {
            int voiceNetType = state.getVoiceNetworkType();
            int dataNetType = state.getDataNetworkType();
            int chosenNetType = ((dataNetType == TelephonyManager.NETWORK_TYPE_UNKNOWN)
                    ? voiceNetType : dataNetType);
            TelephonyManager tm = (TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            int ratInt = tm.getNetworkClass(chosenNetType);
            String networktypeString = getNetworkTypeToString(context, ratInt, state);
            operatorName = new StringBuilder().append(operatorName).append(" ")
                    .append(networktypeString).toString();
            return operatorName;
        } else {
            operatorName = operator;
        }
        return operatorName;

    }

    public boolean getBoolAppendRAT() {
        return true;
    }

    public boolean getBoolShowRatFor2G() {
        return false;
    }

    protected boolean showLteFor4G() {
        return false;
    }
    /* @} */

    /* Bug 532196 modify for flight mode name display in reliance. @{ */
    public boolean showFlightMode() {
        Log.d(TAG, "ShowFlightMode = " + false);
        return false;
    }
    /* @} */

    /* SPRD: Add HD audio icon in cucc for bug 536924. @{ */
    public Drawable getHdVoiceDraw() {
        return null;
    }
    /* @} */

    /* SPRD: modify by BUG 522715 @{ */
    protected boolean show3G(ServiceState state) {
        return true;
    }
    /* @} */
}

