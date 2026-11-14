// 编译期垫片：运行时由系统里 android.net.ITetheringEventCallback 接管。
package android.net;

import android.net.Network;
import android.net.TetheredClient;
import android.net.TetheringCallbackStartedParcel;
import android.net.TetheringConfigurationParcel;
import android.net.TetherStatesParcel;

oneway interface ITetheringEventCallback {
    void onCallbackStarted(in TetheringCallbackStartedParcel parcel);
    void onCallbackStopped(int errorCode);
    void onUpstreamChanged(in Network network);
    void onConfigurationChanged(in TetheringConfigurationParcel config);
    void onTetherStatesChanged(in TetherStatesParcel states);
    void onTetherClientsChanged(in List<TetheredClient> clients);
    void onOffloadStatusChanged(int status);
    void onSupportedTetheringTypes(long supportedBitmap);
}