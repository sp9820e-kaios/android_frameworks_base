/**
 * Copyright (c) 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.carrier;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Used to pass info to CarrierConfigService implementations so they can decide what values to
 * return.
 */
public class CarrierIdentifier implements Parcelable {

    /** Used to create a {@link CarrierIdentifier} from a {@link Parcel}. */
    public static final Creator<CarrierIdentifier> CREATOR = new Creator<CarrierIdentifier>() {
            @Override
        public CarrierIdentifier createFromParcel(Parcel parcel) {
            return new CarrierIdentifier(parcel);
        }

            @Override
        public CarrierIdentifier[] newArray(int i) {
            return new CarrierIdentifier[i];
        }
    };

    private String mMcc;
    private String mMnc;
    private String mSpn;
    private String mImsi;
    private String mGid1;
    private String mGid2;
    // SPRD: [bug475223] add mvno match type PNN
    private String mPnn;
    // SPRD: [bug475223] add network preferred config variable
    private String mIsNetworkPreferred;
    // SPRD: [bug475223] add feature config variable
    private String mFeature;

    public CarrierIdentifier(String mcc, String mnc, String spn, String imsi, String gid1,
            String gid2) {
        mMcc = mcc;
        mMnc = mnc;
        mSpn = spn;
        mImsi = imsi;
        mGid1 = gid1;
        mGid2 = gid2;
    }

    /* SPRD: [bug475223] add for mvno match type PNN @{ */
    public CarrierIdentifier(String mcc, String mnc, String spn, String imsi, String gid1,
            String gid2, String pnn) {
        this(mcc, mnc, spn, imsi, gid1, gid2);
        mPnn = pnn;
    }
    /* @} */

    /* SPRD: [bug475223] add for network preferred config and feature configs @{ */
    public CarrierIdentifier(String mcc, String mnc, String isNetworkPreferred, String feature) {
        mMcc = mcc;
        mMnc = mnc;
        mIsNetworkPreferred = isNetworkPreferred;
        mFeature = feature;
    }
    /* @} */

    /** @hide */
    public CarrierIdentifier(Parcel parcel) {
        readFromParcel(parcel);
    }

    /** Get the mobile country code. */
    public String getMcc() {
        return mMcc;
    }

    /** Get the mobile network code. */
    public String getMnc() {
        return mMnc;
    }

    /** Get the service provider name. */
    public String getSpn() {
        return mSpn;
    }

    /** Get the international mobile subscriber identity. */
    public String getImsi() {
        return mImsi;
    }

    /** Get the group identifier level 1. */
    public String getGid1() {
        return mGid1;
    }

    /** Get the group identifier level 2. */
    public String getGid2() {
        return mGid2;
    }

    /* SPRD: [bug475223] add mvno match type,net config,feature config @{ */
    /** Get the home pnn name. */
    public String getPnn() {
        return mPnn;
    }

    public boolean isNetworkPreferred() {
        return "true".equals(mIsNetworkPreferred);
    }

    /** Get the feature config info. */
    public String getFeature() {
        return mFeature;
    }
    /* @} */

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mMcc);
        out.writeString(mMnc);
        out.writeString(mSpn);
        out.writeString(mImsi);
        out.writeString(mGid1);
        out.writeString(mGid2);
        out.writeString(mPnn);
        out.writeString(mIsNetworkPreferred);
        out.writeString(mFeature);
    }

    /** @hide */
    public void readFromParcel(Parcel in) {
        mMcc = in.readString();
        mMnc = in.readString();
        mSpn = in.readString();
        mImsi = in.readString();
        mGid1 = in.readString();
        mGid2 = in.readString();
        mPnn = in.readString();
        mIsNetworkPreferred = in.readString();
        mFeature = in.readString();
    }
}
