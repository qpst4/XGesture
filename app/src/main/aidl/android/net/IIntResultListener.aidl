// 编译期垫片：运行时由系统里 android.net.* 的同名类接管（app_process 进程可访问隐藏 API）。
// 只需保证方法与 AOSP 一致；事务号由框架的 Stub 自己决定。
package android.net;

oneway interface IIntResultListener {
    void onResult(int resultCode);
}