// 编译期垫片：运行时由系统里 android.net.TetheringRequestParcel 接管。
package android.net;

import android.net.LinkAddress;

parcelable TetheringRequestParcel {
    int tetheringType;
    LinkAddress localIPv4Address;
    LinkAddress staticClientAddress;
    boolean exemptFromEntitlementCheck;
    boolean showProvisioningUi;
    int connectivityScope;
}