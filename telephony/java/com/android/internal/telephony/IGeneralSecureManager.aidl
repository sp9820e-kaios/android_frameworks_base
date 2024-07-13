
package com.android.internal.telephony;

import android.content.Intent;

interface IGeneralSecureManager {
   void setPackageBlockState(int packageUid, int networkType, boolean allowed);
   void deleteBlockPackage (int packageUid);
}