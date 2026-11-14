// 编译期垫片：运行时由系统里 android.net.ITetheringConnector 接管。
package android.net;

import android.net.IIntResultListener;
import android.net.ITetheringEventCallback;
import android.net.TetheringRequestParcel;

oneway interface ITetheringConnector {
    void startTethering(in TetheringRequestParcel request, String callerPkg,
            String callingAttributionTag, IIntResultListener receiver);

    void stopTethering(int type, String callerPkg, String callingAttributionTag,
            IIntResultListener receiver);

    void registerTetheringEventCallback(ITetheringEventCallback callback, String callerPkg);

    void unregisterTetheringEventCallback(ITetheringEventCallback callback, String callerPkg);
}