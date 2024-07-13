/*
 * Copyright (c) 2013 The Android Open Source Project
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

package com.android.ims.internal;


/**
 * Provides the Ut interface interworking to get/set the supplementary service configuration.
 *
 * {@hide}
 */
interface IImsUtEx {

    /**
     * Retrieves the configuration of the call forward.
     */
    int setCallForwardingOption(int serviceId, int commandInterfaceCFAction,
            int commandInterfaceCFReason,int serviceClass, String dialingNumber,
            int timerSeconds, String ruleSet);

    /**
     * Updates the configuration of the call forward.
     */
    int getCallForwardingOption(int serviceId, int commandInterfaceCFReason, int serviceClass,
            String ruleSet);
}
