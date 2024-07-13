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

package android.telephony;

import com.android.internal.telephony.ICarrierConfigLoader;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.content.Context;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;

/**
 * Provides access to telephony configuration values that are carrier-specific.
 * <p>
 * Users should obtain an instance of this class by calling
 * {@code mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);}
 * </p>
 *
 * @see Context#getSystemService
 * @see Context#CARRIER_CONFIG_SERVICE
 */
public class CarrierConfigManager {
    private final static String TAG = "CarrierConfigManager";

    /**
     * @hide
     */
    public CarrierConfigManager() {
    }

    /**
     * This intent is broadcast by the system when carrier config changes.
     */
    public static final String
            ACTION_CARRIER_CONFIG_CHANGED = "android.telephony.action.CARRIER_CONFIG_CHANGED";

    // Below are the keys used in carrier config bundles. To add a new variable, define the key and
    // give it a default value in sDefaults. If you need to ship a per-network override in the
    // system image, that can be added in packages/apps/CarrierConfig.

    /**
     * Flag indicating whether the Phone app should ignore EVENT_SIM_NETWORK_LOCKED
     * events from the Sim.
     * If true, this will prevent the IccNetworkDepersonalizationPanel from being shown, and
     * effectively disable the "Sim network lock" feature.
     */
    public static final String
            KEY_IGNORE_SIM_NETWORK_LOCKED_EVENTS_BOOL = "ignore_sim_network_locked_events_bool";

    /**
     * Flag indicating whether the Phone app should provide a "Dismiss" button on the SIM network
     * unlock screen. The default value is true. If set to false, there will be *no way* to dismiss
     * the SIM network unlock screen if you don't enter the correct unlock code. (One important
     * consequence: there will be no way to make an Emergency Call if your SIM is network-locked and
     * you don't know the PIN.)
     */
    public static final String
            KEY_SIM_NETWORK_UNLOCK_ALLOW_DISMISS_BOOL = "sim_network_unlock_allow_dismiss_bool";

    /** Flag indicating if the phone is a world phone */
    public static final String KEY_WORLD_PHONE_BOOL = "world_phone_bool";

    /**
     * If true, enable vibration (haptic feedback) for key presses in the EmergencyDialer activity.
     * The pattern is set on a per-platform basis using config_virtualKeyVibePattern. To be
     * consistent with the regular Dialer, this value should agree with the corresponding values
     * from config.xml under apps/Contacts.
     */
    public static final String
            KEY_ENABLE_DIALER_KEY_VIBRATION_BOOL = "enable_dialer_key_vibration_bool";

    /** Flag indicating if dtmf tone type is enabled */
    public static final String KEY_DTMF_TYPE_ENABLED_BOOL = "dtmf_type_enabled_bool";

    /** Flag indicating if auto retry is enabled */
    public static final String KEY_AUTO_RETRY_ENABLED_BOOL = "auto_retry_enabled_bool";

    /**
     * Determine whether we want to play local DTMF tones in a call, or just let the radio/BP handle
     * playing of the tones.
     */
    public static final String KEY_ALLOW_LOCAL_DTMF_TONES_BOOL = "allow_local_dtmf_tones_bool";

    /**
     * If true, show an onscreen "Dial" button in the dialer. In practice this is used on all
     * platforms, even the ones with hard SEND/END keys, but for maximum flexibility it's controlled
     * by a flag here (which can be overridden on a per-product basis.)
     */
    public static final String KEY_SHOW_ONSCREEN_DIAL_BUTTON_BOOL = "show_onscreen_dial_button_bool";

    /** Determines if device implements a noise suppression device for in call audio. */
    public static final String
            KEY_HAS_IN_CALL_NOISE_SUPPRESSION_BOOL = "has_in_call_noise_suppression_bool";

    /**
     * Determines if the current device should allow emergency numbers to be logged in the Call Log.
     * (Some carriers require that emergency calls *not* be logged, presumably to avoid the risk of
     * accidental redialing from the call log UI. This is a good idea, so the default here is
     * false.)
     */
    public static final String
            KEY_ALLOW_EMERGENCY_NUMBERS_IN_CALL_LOG_BOOL = "allow_emergency_numbers_in_call_log_bool";

    /** If true, removes the Voice Privacy option from Call Settings */
    public static final String KEY_VOICE_PRIVACY_DISABLE_UI_BOOL = "voice_privacy_disable_ui_bool";

    /** Control whether users can reach the carrier portions of Cellular Network Settings. */
    public static final String
            KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL = "hide_carrier_network_settings_bool";

    /** Control whether users can reach the SIM lock settings. */
    public static final String
            KEY_HIDE_SIM_LOCK_SETTINGS_BOOL = "hide_sim_lock_settings_bool";

    /** Control whether users can edit APNs in Settings. */
    public static final String KEY_APN_EXPAND_BOOL = "apn_expand_bool";

    /** Control whether users can choose a network operator. */
    public static final String KEY_OPERATOR_SELECTION_EXPAND_BOOL = "operator_selection_expand_bool";

    /** Used in Cellular Network Settings for preferred network type. */
    public static final String KEY_PREFER_2G_BOOL = "prefer_2g_bool";

    /** Show cdma network mode choices 1x, 3G, global etc. */
    public static final String KEY_SHOW_CDMA_CHOICES_BOOL = "show_cdma_choices_bool";

    /** CDMA activation goes through HFA */
    public static final String KEY_USE_HFA_FOR_PROVISIONING_BOOL = "use_hfa_for_provisioning_bool";

    /**
     * CDMA activation goes through OTASP.
     * <p>
     * TODO: This should be combined with config_use_hfa_for_provisioning and implemented as an enum
     * (NONE, HFA, OTASP).
     */
    public static final String KEY_USE_OTASP_FOR_PROVISIONING_BOOL = "use_otasp_for_provisioning_bool";

    /** Display carrier settings menu if true */
    public static final String KEY_CARRIER_SETTINGS_ENABLE_BOOL = "carrier_settings_enable_bool";

    /** Does not display additional call seting for IMS phone based on GSM Phone */
    public static final String KEY_ADDITIONAL_CALL_SETTING_BOOL = "additional_call_setting_bool";

    /** Show APN Settings for some CDMA carriers */
    public static final String KEY_SHOW_APN_SETTING_CDMA_BOOL = "show_apn_setting_cdma_bool";

    /** After a CDMA conference call is merged, the swap button should be displayed. */
    public static final String KEY_SUPPORT_SWAP_AFTER_MERGE_BOOL = "support_swap_after_merge_bool";

    /**
     * Determine whether the voicemail notification is persistent in the notification bar. If true,
     * the voicemail notifications cannot be dismissed from the notification bar.
     */
    public static final String
            KEY_VOICEMAIL_NOTIFICATION_PERSISTENT_BOOL = "voicemail_notification_persistent_bool";

    /** For IMS video over LTE calls, determines whether video pause signalling is supported. */
    public static final String
            KEY_SUPPORT_PAUSE_IMS_VIDEO_CALLS_BOOL = "support_pause_ims_video_calls_bool";

    /**
     * Disables dialing "*228" (OTASP provisioning) on CDMA carriers where it is not supported or is
     * potentially harmful by locking the SIM to 3G.
     */
    public static final String
            KEY_DISABLE_CDMA_ACTIVATION_CODE_BOOL = "disable_cdma_activation_code_bool";

   /**
     * Override the platform's notion of a network operator being considered roaming.
     * Value is string array of MCCMNCs to be considered roaming for 3GPP RATs.
     */
    public static final String
            KEY_GSM_ROAMING_NETWORKS_STRING_ARRAY = "gsm_roaming_networks_string_array";

    /**
     * Override the platform's notion of a network operator being considered not roaming.
     * Value is string array of MCCMNCs to be considered not roaming for 3GPP RATs.
     */
    public static final String
            KEY_GSM_NONROAMING_NETWORKS_STRING_ARRAY = "gsm_nonroaming_networks_string_array";

    /**
     * Override the platform's notion of a network operator being considered roaming.
     * Value is string array of SIDs to be considered roaming for 3GPP2 RATs.
     */
    public static final String
            KEY_CDMA_ROAMING_NETWORKS_STRING_ARRAY = "cdma_roaming_networks_string_array";

    /**
     * Override the platform's notion of a network operator being considered non roaming.
     * Value is string array of SIDs to be considered not roaming for 3GPP2 RATs.
     */
    public static final String
            KEY_CDMA_NONROAMING_NETWORKS_STRING_ARRAY = "cdma_nonroaming_networks_string_array";

    /**
     * Override the platform's notion of a network operator being considered non roaming.
     * If true all networks are considered as home network a.k.a non-roaming.  When false,
     * the 2 pairs of CMDA and GSM roaming/non-roaming arrays are consulted.
     *
     * @see KEY_GSM_ROAMING_NETWORKS_STRING_ARRAY
     * @see KEY_GSM_NONROAMING_NETWORKS_STRING_ARRAY
     * @see KEY_CDMA_ROAMING_NETWORKS_STRING_ARRAY
     * @see KEY_CDMA_NONROAMING_NETWORKS_STRING_ARRAY
     */
    public static final String
            KEY_FORCE_HOME_NETWORK_BOOL = "force_home_network_bool";

    /**
     * Flag specifying whether VoLTE should be available for carrier, independent of carrier
     * provisioning. If false: hard disabled. If true: then depends on carrier provisioning,
     * availability, etc.
     */
    public static final String KEY_CARRIER_VOLTE_AVAILABLE_BOOL = "carrier_volte_available_bool";

    /**
     * Flag specifying whether video telephony is available for carrier. If false: hard disabled.
     * If true: then depends on carrier provisioning, availability, etc.
     */
    public static final String KEY_CARRIER_VT_AVAILABLE_BOOL = "carrier_vt_available_bool";

    /**
     * Flag specifying whether WFC over IMS should be available for carrier: independent of
     * carrier provisioning. If false: hard disabled. If true: then depends on carrier
     * provisioning, availability etc.
     */
    public static final String KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL = "carrier_wfc_ims_available_bool";

    /** Flag specifying whether provisioning is required for VOLTE. */
    public static final String KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL
            = "carrier_volte_provisioning_required_bool";

    /** Flag specifying whether VoLTE TTY is supported. */
    public static final String KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL
            = "carrier_volte_tty_supported_bool";

    /**
     * Flag specifying whether IMS service can be turned off. If false then the service will not be
     * turned-off completely, but individual features can be disabled.
     */
    public static final String KEY_CARRIER_ALLOW_TURNOFF_IMS_BOOL
            = "carrier_allow_turnoff_ims_bool";

    /**
     * If Voice Radio Technology is RIL_RADIO_TECHNOLOGY_LTE:14 or RIL_RADIO_TECHNOLOGY_UNKNOWN:0
     * this is the value that should be used instead. A configuration value of
     * RIL_RADIO_TECHNOLOGY_UNKNOWN:0 means there is no replacement value and that the default
     * assumption for phone type (GSM) should be used.
     */
    public static final String KEY_VOLTE_REPLACEMENT_RAT_INT = "volte_replacement_rat_int";

    /**
     * The default sim call manager to use when the default dialer doesn't implement one. A sim call
     * manager can control and route outgoing and incoming phone calls, even if they're placed
     * using another connection service (PSTN, for example).
     */
    public static final String KEY_DEFAULT_SIM_CALL_MANAGER_STRING = "default_sim_call_manager_string";

    /**
     * The default flag specifying whether ETWS/CMAS test setting is forcibly disabled in
     * Settings->More->Emergency broadcasts menu even though developer options is turned on.
     * @hide
     */
    public static final String KEY_CARRIER_FORCE_DISABLE_ETWS_CMAS_TEST_BOOL =
            "carrier_force_disable_etws_cmas_test_bool";

    /* The following 3 fields are related to carrier visual voicemail. */

    /**
     * The carrier number mobile outgoing (MO) sms messages are sent to.
     */
    public static final String KEY_VVM_DESTINATION_NUMBER_STRING = "vvm_destination_number_string";

    /**
     * The port through which the mobile outgoing (MO) sms messages are sent through.
     */
    public static final String KEY_VVM_PORT_NUMBER_INT = "vvm_port_number_int";

    /**
     * The type of visual voicemail protocol the carrier adheres to. See {@link TelephonyManager}
     * for possible values. For example {@link TelephonyManager#VVM_TYPE_OMTP}.
     */
    public static final String KEY_VVM_TYPE_STRING = "vvm_type_string";

    /**
     * The package name of the carrier's visual voicemail app to ensure that dialer visual voicemail
     * and carrier visual voicemail are not active at the same time.
     */
    public static final String KEY_CARRIER_VVM_PACKAGE_NAME_STRING = "carrier_vvm_package_name_string";

    /**
     * Flag specifying whether an additional (client initiated) intent needs to be sent on System
     * update
     * @hide
     */
    public static final String KEY_CI_ACTION_ON_SYS_UPDATE_BOOL = "ci_action_on_sys_update_bool";

    /**
     * Intent to be sent for the additional action on System update
     * @hide
     */
    public static final String KEY_CI_ACTION_ON_SYS_UPDATE_INTENT_STRING =
            "ci_action_on_sys_update_intent_string";

    /**
     * Extra to be included in the intent sent for additional action on System update
     * @hide
     */
    public static final String KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_STRING =
            "ci_action_on_sys_update_extra_string";

    /**
     * Value of extra included in intent sent for additional action on System update
     * @hide
     */
    public static final String KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_VAL_STRING =
            "ci_action_on_sys_update_extra_val_string";

    /**
     * If this is true, the SIM card (through Customer Service Profile EF file) will be able to
     * prevent manual operator selection. If false, this SIM setting will be ignored and manual
     * operator selection will always be available. See CPHS4_2.WW6, CPHS B.4.7.1 for more
     * information
     */
    public static final String KEY_CSP_ENABLED_BOOL = "csp_enabled_bool";

    /* SPRD: bug#474283, add for IP-DIAL FEATURE @{ */
    public static final String KEY_FEATURE_IP_DIAL_ENABLED_BOOL = "ip_dial_enabled_bool";
    /* @{ */

    /* SPRD: modify by BUG 494075 @{ */
    public static final String KEY_HSPADATADISTINGUISHABLE_BOOL = "hspaDataDistinguishable";
    /* @} */

    /* SPRD: modify by BUG 494075 @{ */
    public static final String KEY_SYSTEMUI_CARRIER_LABEL_WITH_SIMCOLOR_BOOL =
            "systemui_carrier_label_with_simcolor_bool";
    /* @} */

    /* SPRD: modify by BUG 494075 @{ */
    public static final String KEY_SHOW_ECCBUTTON_LOCKSCREEN_BOOL =
            "show_eccbutton_lockscreen_bool";
    /* @} */

    // SPRD: screen off 5s after call connection. See bug #503700
    public static final String KEY_SCREEN_OFF_IN_ACTIVE_CALL_STATE_BOOL =
            "screen_off_in_active_call_state_bool";

    // SPRD: modify for bug 494056
    public static final String KEY_OPERATOR_SEPARATOR_LABEL = "operator_separator_label";

    // These variables are used by the MMS service and exposed through another API, {@link
    // SmsManager}. The variable names and string values are copied from there.
    public static final String KEY_MMS_ALIAS_ENABLED_BOOL = "aliasEnabled";
    public static final String KEY_MMS_ALLOW_ATTACH_AUDIO_BOOL = "allowAttachAudio";
    public static final String KEY_MMS_APPEND_TRANSACTION_ID_BOOL = "enabledTransID";
    public static final String KEY_MMS_GROUP_MMS_ENABLED_BOOL = "enableGroupMms";
    public static final String KEY_MMS_MMS_DELIVERY_REPORT_ENABLED_BOOL = "enableMMSDeliveryReports";
    public static final String KEY_MMS_MMS_ENABLED_BOOL = "enabledMMS";
    public static final String KEY_MMS_MMS_READ_REPORT_ENABLED_BOOL = "enableMMSReadReports";
    public static final String KEY_MMS_MULTIPART_SMS_ENABLED_BOOL = "enableMultipartSMS";
    public static final String KEY_MMS_NOTIFY_WAP_MMSC_ENABLED_BOOL = "enabledNotifyWapMMSC";
    public static final String KEY_MMS_SEND_MULTIPART_SMS_AS_SEPARATE_MESSAGES_BOOL = "sendMultipartSmsAsSeparateMessages";
    public static final String KEY_MMS_SHOW_CELL_BROADCAST_APP_LINKS_BOOL = "config_cellBroadcastAppLinks";
    public static final String KEY_MMS_SMS_DELIVERY_REPORT_ENABLED_BOOL = "enableSMSDeliveryReports";
    public static final String KEY_MMS_SUPPORT_HTTP_CHARSET_HEADER_BOOL = "supportHttpCharsetHeader";
    public static final String KEY_MMS_SUPPORT_MMS_CONTENT_DISPOSITION_BOOL = "supportMmsContentDisposition";
    public static final String KEY_MMS_ALIAS_MAX_CHARS_INT = "aliasMaxChars";
    public static final String KEY_MMS_ALIAS_MIN_CHARS_INT = "aliasMinChars";
    public static final String KEY_MMS_HTTP_SOCKET_TIMEOUT_INT = "httpSocketTimeout";
    public static final String KEY_MMS_MAX_IMAGE_HEIGHT_INT = "maxImageHeight";
    public static final String KEY_MMS_MAX_IMAGE_WIDTH_INT = "maxImageWidth";
    public static final String KEY_MMS_MAX_MESSAGE_SIZE_INT = "maxMessageSize";
    public static final String KEY_MMS_MESSAGE_TEXT_MAX_SIZE_INT = "maxMessageTextSize";
    public static final String KEY_MMS_RECIPIENT_LIMIT_INT = "recipientLimit";
    public static final String KEY_MMS_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD_INT = "smsToMmsTextLengthThreshold";
    public static final String KEY_MMS_SMS_TO_MMS_TEXT_THRESHOLD_INT = "smsToMmsTextThreshold";
    public static final String KEY_MMS_SUBJECT_MAX_LENGTH_INT = "maxSubjectLength";
    public static final String KEY_MMS_EMAIL_GATEWAY_NUMBER_STRING = "emailGatewayNumber";
    public static final String KEY_MMS_HTTP_PARAMS_STRING = "httpParams";
    public static final String KEY_MMS_NAI_SUFFIX_STRING = "naiSuffix";
    public static final String KEY_MMS_UA_PROF_TAG_NAME_STRING = "uaProfTagName";
    public static final String KEY_MMS_UA_PROF_URL_STRING = "uaProfUrl";
    public static final String KEY_MMS_USER_AGENT_STRING = "userAgent";

    /* SPRD: [bug475223] Add global config variables. @{ */
    public static final String KEY_GLO_CONF_VOICEMAIL_NUMBER = "vmnumber";
    public static final String KEY_GLO_CONF_VOICEMAIL_TAG = "vmtag";
    public static final String KEY_GLO_CONF_ROAMING_VOICEMAIL_NUMBER = "roamingvm";
    public static final String KEY_GLO_CONF_ECC_LIST_NO_CARD = "ecclist_nocard";
    public static final String KEY_GLO_CONF_ECC_LIST_WITH_CARD = "ecclist_withcard";
    public static final String KEY_GLO_CONF_FAKE_ECC_LIST_WITH_CARD = "fake_ecclist_withcard";
    public static final String KEY_GLO_CONF_NUM_MATCH = "num_match";
    public static final String KEY_GLO_CONF_NUM_MATCH_RULE = "num_match_rule";
    public static final String KEY_GLO_CONF_NUM_MATCH_SHORT = "num_match_short";
    public static final String KEY_GLO_CONF_SPN = "spn";
    public static final String KEY_GLO_CONF_SMS_7BIT_ENABLED = "sms_7bit_enabled";
    public static final String KEY_GLO_CONF_SMS_CODING_NATIONAL = "sms_coding_national";
    public static final String KEY_GLO_CONF_MVNO = "mvno";
    /* @} */

    /**
     *Add for setting homepage via MCC\MNC
     *@ {
     */
    public static final String KEY_GLO_CONF_HOMEPAGE_URL = "homepage_url";
    /*@}*/

    /* SPRD: Add for STK feature. @{ */
    public static final String KEY_FEATURE_STK_SETUPCALL_NOCNF_BOOL = "stk_setupcall_nocnf_bool";
    public static final String KEY_FEATURE_STK_REFRESH_NOTOAST_BOOL = "stk_refresh_notoast_bool";
    public static final String KEY_FEATURE_STK_SENDSMS_NOTOAST_BOOL = "stk_sendsms_notoast_bool";
    public static final String KEY_FEATURE_STK_CALLCONTROL_SHOWID_BOOL = "stk_callcontrol_showid_bool";
    public static final String KEY_FEATURE_STK_ENDSESSION_TOMAINMENU_BOOL = "stk_endsession_tomainmenu_bool";
    public static final String KEY_FEATURE_STK_NAME_FROMSETUPMENU_BOOL = "stk_name_fromsetupmenu_bool";
    public static final String KEY_FEATURE_STK_NAMEANDICON_CONFIG_BOOL = "stk_nameandicon_config_bool";
    /* @} */

    /* SPRD: Flag specifying whether network mode setting is available for WCDMA only. @{ */
    public static final String KEY_NETWORK_SUPPORT_WCDMA_ONLY = "mobile_network_support_wcdma_only";
    /* @{ */

    /* SPRD: Flag specifying whether network mode setting is available for 3g only and 2g only. @{ */
    public static final String KEY_NETWORK_SUPPORT_3G_ONLY_AND_2G_ONLY_BOOL = "mobile_network_support_3g_only_2g_only";
    /* @{ */

    // SPRD: Flip to silence from incoming calls. See bug473877
    public static final String KEY_FEATURE_FLIP_SILENT_INCOMING_CALL_ENABLED_BOOL = "flip_to_silent_incoming_call_enabled_bool";

    /* SPRD: Add for mobile always online feature @{ */
    /**
     * Duration to keep data alive.
     * @hide
     */
    public static final String KEY_DCT_DATA_KEEP_ALIVE_DURATION_INT
            = "dct_data_keep_alive_duration_int";
    /* SPRD: Add for feature of APN and DATA Pop up.BugId: 509845. @{ */
    public static final String KEY_FEATURE_DATA_AND_APN_POP_BOOL =
            "feature_data_and_apn_pop_bool";
    public static final String KEY_FEATURE_DATA_AND_APN_POP_OPERATOR_STRING =
            "feature_data_and_apn_pop_operator_string";
    /* @} */

    // SPRD: modify for bug513637
    public static final String KEY_FEATURE_STE_DATA_AND_STE_PRIMARYCARD_MERGE_BOOL = "set_data_and_set_primarycard_merge_bool";
    /* @} */

    /* SPRD: National Data Roaming. @{ */
    public static final String KEY_NATIONAL_DATA_ROAMING_BOOL = "national_data_roaming_bool";
    /* @} */

    /* SPRD: Vibrate when call connected or disconnected feature. @{ */
    /**
     * Flag specifying whether Vibrate when call connected or disconnected feature is supported.
     */
    public static final String KEY_FEATURE_VIBRATE_FOR_CALL_CONNECTION_BOOL =
            "vibrate_for_call_connection_bool";
    /* SPRD: Add for double press on the headset key feature. @{ */
    /**
     * Flag specifying whether Double press on the headset key feature is supported.
     */
    public static final String KEY_FEATURE_DOUBLE_PRESS_ON_HEADSET_KEY_BOOL =
            "double_press_on_headset_key_bool";
    /* @} */

    /* SPRD: Add switch for automatic call record feature. @{ */
    /**
     * Flag specifying whether automatic call record feature is supported.
     */
    public static final  String KEY_FEATURE_AUTOMATIC_CALL_RECORD_ENABLED_BOOL =
            "automatic_call_record_enabled_bool";
    /* @} */

    /* SPRD: Add for fade-in feature */
    /**
     * Flag specifying fade-in feature s supported.
     */
    public static final String KEY_FEATURE_FADE_IN_ENABLED_BOOL = "fade_in_enabled_bool";
    /* @} */

    /**
     * SPRD: [Bug475782] Flag for fixed primary slot. -1 means not support fixed primary slot.
     */
    /** {@hide} */
    public static final String KEY_FIXED_PRIMARY_SLOT_INT = "fixed_primary_slot_int";

    // SPRD Add for Bug 570658: Heartbeat interval operator network adaptation.
    public static final String KEY_NETWORK_NAT_OVERTIME_2G = "network_nat_overtime_2g";
    public static final String KEY_NETWORK_NAT_OVERTIME_3G = "network_nat_overtime_3g";
    public static final String KEY_NETWORK_NAT_OVERTIME_4G = "network_nat_overtime_4g";
    //SPRD: bug576180 check whether is reliance sim card when sim records loaded by plugin
    public static final String KEY_PDP_ACTIVE_PLMN_MATCH = "pdp_active_plmn_match";

    /** The default value for every variable. */
    private final static PersistableBundle sDefaults;

    static {
        sDefaults = new PersistableBundle();
        sDefaults.putBoolean(KEY_ADDITIONAL_CALL_SETTING_BOOL, true);
        sDefaults.putBoolean(KEY_ALLOW_EMERGENCY_NUMBERS_IN_CALL_LOG_BOOL, false);
        sDefaults.putBoolean(KEY_ALLOW_LOCAL_DTMF_TONES_BOOL, true);
        sDefaults.putBoolean(KEY_APN_EXPAND_BOOL, true);
        sDefaults.putBoolean(KEY_AUTO_RETRY_ENABLED_BOOL, false);
        sDefaults.putBoolean(KEY_CARRIER_SETTINGS_ENABLE_BOOL, false);
        sDefaults.putBoolean(KEY_CARRIER_VOLTE_AVAILABLE_BOOL, false);
        sDefaults.putBoolean(KEY_CARRIER_VT_AVAILABLE_BOOL, false);
        sDefaults.putBoolean(KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, false);
        sDefaults.putBoolean(KEY_CARRIER_FORCE_DISABLE_ETWS_CMAS_TEST_BOOL, false);
        sDefaults.putBoolean(KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL, false);
        sDefaults.putBoolean(KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL, true);
        sDefaults.putBoolean(KEY_CARRIER_ALLOW_TURNOFF_IMS_BOOL, true);
        sDefaults.putBoolean(KEY_DISABLE_CDMA_ACTIVATION_CODE_BOOL, false);
        sDefaults.putBoolean(KEY_DTMF_TYPE_ENABLED_BOOL, false);
        sDefaults.putBoolean(KEY_ENABLE_DIALER_KEY_VIBRATION_BOOL, true);
        sDefaults.putBoolean(KEY_HAS_IN_CALL_NOISE_SUPPRESSION_BOOL, false);
        sDefaults.putBoolean(KEY_HIDE_CARRIER_NETWORK_SETTINGS_BOOL, false);
        sDefaults.putBoolean(KEY_HIDE_SIM_LOCK_SETTINGS_BOOL, false);
        sDefaults.putBoolean(KEY_IGNORE_SIM_NETWORK_LOCKED_EVENTS_BOOL, false);
        sDefaults.putBoolean(KEY_OPERATOR_SELECTION_EXPAND_BOOL, true);
        sDefaults.putBoolean(KEY_PREFER_2G_BOOL, true);
        sDefaults.putBoolean(KEY_SHOW_APN_SETTING_CDMA_BOOL, false);
        sDefaults.putBoolean(KEY_SHOW_CDMA_CHOICES_BOOL, false);
        sDefaults.putBoolean(KEY_SHOW_ONSCREEN_DIAL_BUTTON_BOOL, true);
        sDefaults.putBoolean(KEY_SIM_NETWORK_UNLOCK_ALLOW_DISMISS_BOOL, true);
        sDefaults.putBoolean(KEY_SUPPORT_PAUSE_IMS_VIDEO_CALLS_BOOL, true);
        sDefaults.putBoolean(KEY_SUPPORT_SWAP_AFTER_MERGE_BOOL, true);
        sDefaults.putBoolean(KEY_USE_HFA_FOR_PROVISIONING_BOOL, false);
        sDefaults.putBoolean(KEY_USE_OTASP_FOR_PROVISIONING_BOOL, false);
        sDefaults.putBoolean(KEY_VOICEMAIL_NOTIFICATION_PERSISTENT_BOOL, false);
        sDefaults.putBoolean(KEY_VOICE_PRIVACY_DISABLE_UI_BOOL, false);
        sDefaults.putBoolean(KEY_WORLD_PHONE_BOOL, false);
        sDefaults.putInt(KEY_VOLTE_REPLACEMENT_RAT_INT, 0);
        sDefaults.putString(KEY_DEFAULT_SIM_CALL_MANAGER_STRING, "");
        sDefaults.putString(KEY_VVM_DESTINATION_NUMBER_STRING, "");
        sDefaults.putInt(KEY_VVM_PORT_NUMBER_INT, 0);
        sDefaults.putString(KEY_VVM_TYPE_STRING, "");
        sDefaults.putString(KEY_CARRIER_VVM_PACKAGE_NAME_STRING, "");
        sDefaults.putBoolean(KEY_CI_ACTION_ON_SYS_UPDATE_BOOL, false);
        sDefaults.putString(KEY_CI_ACTION_ON_SYS_UPDATE_INTENT_STRING, "");
        sDefaults.putString(KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_STRING, "");
        sDefaults.putString(KEY_CI_ACTION_ON_SYS_UPDATE_EXTRA_VAL_STRING, "");
        sDefaults.putBoolean(KEY_CSP_ENABLED_BOOL, false);
        /* SPRD: bug#474283, add for IP-DIAL FEATURE @{ */
        sDefaults.putBoolean(KEY_FEATURE_IP_DIAL_ENABLED_BOOL, true);
        /* @} */

        /* SPRD: modify by BUG 494075 @{ */
        sDefaults.putBoolean(KEY_HSPADATADISTINGUISHABLE_BOOL, false);
        /* @} */

        /* SPRD: modify by BUG 494075 @{ */
        sDefaults.putBoolean(KEY_SYSTEMUI_CARRIER_LABEL_WITH_SIMCOLOR_BOOL, false);
        /* @} */

        /* SPRD: modify by BUG 494075 @{ */
        sDefaults.putBoolean(KEY_SHOW_ECCBUTTON_LOCKSCREEN_BOOL, true);
        /* @} */

        // SPRD: screen off 5s after call connection. See bug #503700
        // SPRD: modify for Bug595127
        sDefaults.putBoolean(KEY_SCREEN_OFF_IN_ACTIVE_CALL_STATE_BOOL, true);

        // SPRD: modify for bug 494056
        sDefaults.putString(KEY_OPERATOR_SEPARATOR_LABEL, "|");

        sDefaults.putStringArray(KEY_GSM_ROAMING_NETWORKS_STRING_ARRAY, null);
        sDefaults.putStringArray(KEY_GSM_NONROAMING_NETWORKS_STRING_ARRAY, null);
        sDefaults.putStringArray(KEY_CDMA_ROAMING_NETWORKS_STRING_ARRAY, null);
        sDefaults.putStringArray(KEY_CDMA_NONROAMING_NETWORKS_STRING_ARRAY, null);
        sDefaults.putBoolean(KEY_FORCE_HOME_NETWORK_BOOL, false);

        // MMS defaults
        sDefaults.putBoolean(KEY_MMS_ALIAS_ENABLED_BOOL, false);
        sDefaults.putBoolean(KEY_MMS_ALLOW_ATTACH_AUDIO_BOOL, true);
        sDefaults.putBoolean(KEY_MMS_APPEND_TRANSACTION_ID_BOOL, false);
        sDefaults.putBoolean(KEY_MMS_GROUP_MMS_ENABLED_BOOL, true);
        sDefaults.putBoolean(KEY_MMS_MMS_DELIVERY_REPORT_ENABLED_BOOL, false);
        sDefaults.putBoolean(KEY_MMS_MMS_ENABLED_BOOL, true);
        sDefaults.putBoolean(KEY_MMS_MMS_READ_REPORT_ENABLED_BOOL, false);
        sDefaults.putBoolean(KEY_MMS_MULTIPART_SMS_ENABLED_BOOL, true);
        sDefaults.putBoolean(KEY_MMS_NOTIFY_WAP_MMSC_ENABLED_BOOL, false);
        sDefaults.putBoolean(KEY_MMS_SEND_MULTIPART_SMS_AS_SEPARATE_MESSAGES_BOOL, false);
        sDefaults.putBoolean(KEY_MMS_SHOW_CELL_BROADCAST_APP_LINKS_BOOL, true);
        sDefaults.putBoolean(KEY_MMS_SMS_DELIVERY_REPORT_ENABLED_BOOL, true);
        sDefaults.putBoolean(KEY_MMS_SUPPORT_HTTP_CHARSET_HEADER_BOOL, false);
        sDefaults.putBoolean(KEY_MMS_SUPPORT_MMS_CONTENT_DISPOSITION_BOOL, true);
        sDefaults.putInt(KEY_MMS_ALIAS_MAX_CHARS_INT, 48);
        sDefaults.putInt(KEY_MMS_ALIAS_MIN_CHARS_INT, 2);
        sDefaults.putInt(KEY_MMS_HTTP_SOCKET_TIMEOUT_INT, 60 * 1000);
        sDefaults.putInt(KEY_MMS_MAX_IMAGE_HEIGHT_INT, 480);
        sDefaults.putInt(KEY_MMS_MAX_IMAGE_WIDTH_INT, 640);
        sDefaults.putInt(KEY_MMS_MAX_MESSAGE_SIZE_INT, 300 * 1024);
        sDefaults.putInt(KEY_MMS_MESSAGE_TEXT_MAX_SIZE_INT, -1);
        sDefaults.putInt(KEY_MMS_RECIPIENT_LIMIT_INT, Integer.MAX_VALUE);
        sDefaults.putInt(KEY_MMS_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD_INT, -1);
        sDefaults.putInt(KEY_MMS_SMS_TO_MMS_TEXT_THRESHOLD_INT, 10);
        sDefaults.putInt(KEY_MMS_SUBJECT_MAX_LENGTH_INT, 40);
        sDefaults.putString(KEY_MMS_EMAIL_GATEWAY_NUMBER_STRING, "");
        sDefaults.putString(KEY_MMS_HTTP_PARAMS_STRING, "");
        sDefaults.putString(KEY_MMS_NAI_SUFFIX_STRING, "");
        sDefaults.putString(KEY_MMS_UA_PROF_TAG_NAME_STRING, "x-wap-profile");
        sDefaults.putString(KEY_MMS_UA_PROF_URL_STRING, "");
        sDefaults.putString(KEY_MMS_USER_AGENT_STRING, "");

        /* SPRD: [bug475223] Add global config defaults. @{ */
        sDefaults.putString(KEY_GLO_CONF_VOICEMAIL_NUMBER, "");
        sDefaults.putString(KEY_GLO_CONF_VOICEMAIL_TAG, "");
        sDefaults.putString(KEY_GLO_CONF_ROAMING_VOICEMAIL_NUMBER, "");
        sDefaults.putString(KEY_GLO_CONF_ECC_LIST_NO_CARD, "");
        sDefaults.putString(KEY_GLO_CONF_ECC_LIST_WITH_CARD, "");
        sDefaults.putString(KEY_GLO_CONF_FAKE_ECC_LIST_WITH_CARD, "");
        sDefaults.putInt(KEY_GLO_CONF_NUM_MATCH, 7);
        sDefaults.putInt(KEY_GLO_CONF_NUM_MATCH_RULE, -1);
        sDefaults.putInt(KEY_GLO_CONF_NUM_MATCH_SHORT, -1);
        sDefaults.putString(KEY_GLO_CONF_SPN, "");
        sDefaults.putBoolean(KEY_GLO_CONF_SMS_7BIT_ENABLED, false);
        sDefaults.putString(KEY_GLO_CONF_SMS_CODING_NATIONAL, "");
        sDefaults.putBoolean(KEY_GLO_CONF_MVNO, false);
        /* @} */
        /**
         *Add for setting homepage via MCC\MNC
         *@{
         */
        sDefaults.putString(KEY_GLO_CONF_HOMEPAGE_URL, "");
        /*@}*/

        /* SPRD: Add STK feature config defaults. @{ */
        sDefaults.putBoolean(KEY_FEATURE_STK_SETUPCALL_NOCNF_BOOL, false);
        sDefaults.putBoolean(KEY_FEATURE_STK_REFRESH_NOTOAST_BOOL, false);
        sDefaults.putBoolean(KEY_FEATURE_STK_SENDSMS_NOTOAST_BOOL, false);
        sDefaults.putBoolean(KEY_FEATURE_STK_CALLCONTROL_SHOWID_BOOL, false);
        sDefaults.putBoolean(KEY_FEATURE_STK_ENDSESSION_TOMAINMENU_BOOL, false);
        sDefaults.putBoolean(KEY_FEATURE_STK_NAME_FROMSETUPMENU_BOOL, false);
        sDefaults.putBoolean(KEY_FEATURE_STK_NAMEANDICON_CONFIG_BOOL, false);
        /* @} */

        /* SPRD:Flag specifying whether network mode setting is available for WCDMA only. @{ */
        sDefaults.putBoolean(KEY_NETWORK_SUPPORT_WCDMA_ONLY, false);
        /* @} */
        /* SPRD: Flag specifying whether network mode setting is available for 3g only and 2g only. @{ */
        sDefaults.putBoolean(KEY_NETWORK_SUPPORT_3G_ONLY_AND_2G_ONLY_BOOL, false);
        /* @} */
        // SPRD: Flip to silence from incoming calls. See bug473877
        sDefaults.putBoolean(KEY_FEATURE_FLIP_SILENT_INCOMING_CALL_ENABLED_BOOL, true);
        /* SPRD: Add for VoLTE. @{ */
        sDefaults.putBoolean(KEY_CARRIER_VOLTE_PROVISIONING_REQUIRED_BOOL, true);
        if(SystemProperties.getBoolean("persist.sys.volte.enable", false)){
            sDefaults.putBoolean(KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true);
            sDefaults.putBoolean(KEY_CARRIER_VT_AVAILABLE_BOOL, true);
            sDefaults.putBoolean(KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true);
            sDefaults.putBoolean(KEY_CARRIER_VOLTE_TTY_SUPPORTED_BOOL, false);
        }
        /* SPRD: National Data Roaming. @{ */
        sDefaults.putBoolean(KEY_NATIONAL_DATA_ROAMING_BOOL, false);
        /* @} */

        /* SPRD: Add for mobile always online feature @{ */
        sDefaults.putInt(KEY_DCT_DATA_KEEP_ALIVE_DURATION_INT, 30 * 60 * 1000);
        /* SPRD: Add for feature of APN and DATA Pop up.BugId: 509845. @{ */
        sDefaults.putBoolean(KEY_FEATURE_DATA_AND_APN_POP_BOOL, false);
        sDefaults.putString(KEY_FEATURE_DATA_AND_APN_POP_OPERATOR_STRING, "");
        /* @} */

        // SPRD: modify for bug513637
        sDefaults.putBoolean(KEY_FEATURE_STE_DATA_AND_STE_PRIMARYCARD_MERGE_BOOL, false);

        // SPRD: Vibrate when call connected or disconnected feature.
        sDefaults.putBoolean(KEY_FEATURE_VIBRATE_FOR_CALL_CONNECTION_BOOL, true);

        // SPRD: Add for double press on the headset key feature.
        sDefaults.putBoolean(KEY_FEATURE_DOUBLE_PRESS_ON_HEADSET_KEY_BOOL, true);

        // SPRD: Add switch for automatic call record feature.
        sDefaults.putBoolean(KEY_FEATURE_AUTOMATIC_CALL_RECORD_ENABLED_BOOL, true);
        // SPRD: Add for fade-in feature
        sDefaults.putBoolean(KEY_FEATURE_FADE_IN_ENABLED_BOOL, true);

        // SPRD: [Bug475782] Add for fixed primary slot feature.
        sDefaults.putInt(KEY_FIXED_PRIMARY_SLOT_INT, SubscriptionManager.INVALID_PHONE_INDEX);

        // SPRD Add for Bug 570658: Heartbeat interval operator network adaptation.
        sDefaults.putInt(KEY_NETWORK_NAT_OVERTIME_2G, 5);
        sDefaults.putInt(KEY_NETWORK_NAT_OVERTIME_3G, 15);
        sDefaults.putInt(KEY_NETWORK_NAT_OVERTIME_4G, 30);
        //SPRD: bug576180 check whether is reliance sim card when sim records loaded by plugin
        sDefaults.putString(KEY_PDP_ACTIVE_PLMN_MATCH, "");
    }

    /**
     * Gets the configuration values for a particular subscription, which is associated with a
     * specific SIM card. If an invalid subId is used, the returned config will contain default
     * values.
     *
     * <p>Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @param subId the subscription ID, normally obtained from {@link SubscriptionManager}.
     * @return A {@link PersistableBundle} containing the config for the given subId, or default
     *         values for an invalid subId.
     */
    @Nullable
    public PersistableBundle getConfigForSubId(int subId) {
        try {
            return getICarrierConfigLoader().getConfigForSubId(subId);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "Error getting config for subId " + Integer.toString(subId) + ": "
                    + ex.toString());
        } catch (NullPointerException ex) {
            Rlog.e(TAG, "Error getting config for subId " + Integer.toString(subId) + ": "
                    + ex.toString());
        }
        return null;
    }

    /**
     * SPRD: [bug475223] Gets the configuration values for a particular phoneId.
     * The biggest difference with "getConfigForSubId(int subId)" is that network preferred
     * config will still be returned as long as register any network no matter whether sim
     * card inserted or not.If an invalid phoneId is used, the returned config will contain
     * default values.
     *
     * <p>Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @return A {@link PersistableBundle} containing the config for the given phoneId, or default
     *         values for an invalid phoneId.
     */
    @Nullable
    public PersistableBundle getConfigForPhoneId(int phoneId) {
        try {
            return getICarrierConfigLoader().getConfigForPhoneId(phoneId);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "Error getting config for phoneId " + Integer.toString(phoneId) + ": "
                    + ex.toString());
        } catch (NullPointerException ex) {
            Rlog.e(TAG, "Error getting config for phoneId " + Integer.toString(phoneId) + ": "
                    + ex.toString());
        }
        return null;
    }

    /**
     * SPRD: [bug475223] Gets the configuration values for the default phoneId which will be phone 0 if no sim exit.
     * It is especially used for getting feature config which has nothing to do with a particular phoneId or subId.
     *
     * <p>Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @return A {@link PersistableBundle} containing the config for the default phoneId.
     */
    @Nullable
    public PersistableBundle getConfigForDefaultPhone() {
        int defaultPhoneId = SubscriptionManager.getPhoneId(SubscriptionManager.getDefaultSubId());
        if (!SubscriptionManager.isValidPhoneId(defaultPhoneId)) {
            defaultPhoneId = 0;
        }
        try {
            return getICarrierConfigLoader().getConfigForPhoneId(defaultPhoneId);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "Error getting config for default phone " + Integer.toString(defaultPhoneId) + ": "
                    + ex.toString());
        } catch (NullPointerException ex) {
            Rlog.e(TAG, "Error getting config for default phone " + Integer.toString(defaultPhoneId) + ": "
                    + ex.toString());
        }
        return null;
    }

    /**
     * Gets the configuration values for the default subscription.
     *
     * <p>Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @see #getConfigForSubId
     */
    @Nullable
    public PersistableBundle getConfig() {
        return getConfigForSubId(SubscriptionManager.getDefaultSubId());
    }

    /**
     * Calling this method triggers telephony services to fetch the current carrier configuration.
     * <p>
     * Normally this does not need to be called because the platform reloads config on its own.
     * This should be called by a carrier service app if it wants to update config at an arbitrary
     * moment.
     * </p>
     * <p>Requires that the calling app has carrier privileges.
     * @see #hasCarrierPrivileges
     * <p>
     * This method returns before the reload has completed, and
     * {@link android.service.carrier.CarrierService#onLoadConfig} will be called from an
     * arbitrary thread.
     * </p>
     */
    public void notifyConfigChangedForSubId(int subId) {
        try {
            getICarrierConfigLoader().notifyConfigChangedForSubId(subId);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "Error reloading config for subId=" + subId + ": " + ex.toString());
        } catch (NullPointerException ex) {
            Rlog.e(TAG, "Error reloading config for subId=" + subId + ": " + ex.toString());
        }
    }

    /**
     * Request the carrier config loader to update the cofig for phoneId.
     * <p>
     * Depending on simState, the config may be cleared or loaded from config app. This is only used
     * by SubscriptionInfoUpdater.
     * </p>
     *
     * @hide
     */
    @SystemApi
    public void updateConfigForPhoneId(int phoneId, String simState) {
        try {
            getICarrierConfigLoader().updateConfigForPhoneId(phoneId, simState);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "Error updating config for phoneId=" + phoneId + ": " + ex.toString());
        } catch (NullPointerException ex) {
            Rlog.e(TAG, "Error updating config for phoneId=" + phoneId + ": " + ex.toString());
        }
    }

    /**
     * Returns a new bundle with the default value for every supported configuration variable.
     *
     * @hide
     */
    @NonNull
    @SystemApi
    public static PersistableBundle getDefaultConfig() {
        return new PersistableBundle(sDefaults);
    }

    /** @hide */
    private ICarrierConfigLoader getICarrierConfigLoader() {
        return ICarrierConfigLoader.Stub
                .asInterface(ServiceManager.getService(Context.CARRIER_CONFIG_SERVICE));
    }
}
