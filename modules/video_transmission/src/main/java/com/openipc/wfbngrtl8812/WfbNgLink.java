package com.openipc.wfbngrtl8812;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/** Thin lifecycle-safe Java wrapper around PixelPilot's WFB-NG RTL8812 receiver. */
public final class WfbNgLink implements WfbNGStatsChanged, AutoCloseable {
    private static final String TAG = "AliusWfb";

    static { System.loadLibrary("WfbngRtl8812"); }

    private final long nativeInstance;
    private final Context context;
    private final Timer statsTimer = new Timer("wfb-stats", true);
    private final Map<UsbDevice, Thread> threads = new HashMap<>();
    private final Map<UsbDevice, UsbDeviceConnection> connections = new HashMap<>();
    private volatile WfbNGStatsChanged statsListener;
    private volatile ReceiverStateListener stateListener;
    private volatile boolean receiverReady;
    private volatile boolean receiverReceiving;

    public static native long nativeInitialize(Context context, WfbNgLink owner);
    public static native void nativeFinalize(long instance);
    public static native int nativeRun(long instance, Context context, int channel, int bandwidth, int fd,
                                       boolean rm500Rockchip);
    public static native void nativeStop(long instance, Context context, int fd);
    public static native void nativeCallBack(WfbNGStatsChanged callback, long instance);

    public WfbNgLink(Context context) {
        this.context = context.getApplicationContext();
        nativeInstance = nativeInitialize(this.context, this);
        statsTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                if (isRunning()) nativeCallBack(WfbNgLink.this, nativeInstance);
            }
        }, 1000, 1000);
    }

    public synchronized boolean isRunning() { return !threads.isEmpty(); }

    public void setStatsListener(WfbNGStatsChanged listener) { statsListener = listener; }

    public void setStateListener(ReceiverStateListener listener) { stateListener = listener; }

    public synchronized boolean start(int channel, int bandwidth, UsbDevice device,
                                      WfbUsbCompatibilityProfile profile) {
        if (threads.containsKey(device)) return true;
        receiverReady = false;
        receiverReceiving = false;
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) {
            Log.e(TAG, "Unable to open " + device.getDeviceName());
            return false;
        }
        // Some Android 7 vendor kernels reject USBFS vendor control transfers
        // made through a wrapped fd unless the framework has claimed the
        // interface first. Newer Qualcomm controllers tolerate native-only
        // claiming, which hid this on RC Plus 2; RM500/Rockchip requires the
        // explicit framework claim.
        UsbInterface usbInterface = device.getInterfaceCount() > 0 ? device.getInterface(0) : null;
        if (usbInterface == null) {
            connection.close();
            return false;
        }
        // Always use the Framework's disconnect-and-claim path. On RM500 the
        // clean enumeration keeps usb_aoa away, but this Android 7 usbfs
        // implementation still rejects force=false for an unbound vendor
        // interface. force=true succeeds for both the clean RM500 interface
        // and the standard RM700 kernel-driver detach path.
        if (!connection.claimInterface(usbInterface, true)) {
            if (profile != WfbUsbCompatibilityProfile.RM500_ROCKCHIP) {
                Log.e(TAG, "Unable to claim interface for " + device.getDeviceName());
                connection.close();
                return false;
            }
            // Some RM500 Android 7 builds return false here even though the
            // Framework has opened and authorized the USBFS fd. Keep that fd
            // alive; the native libusb wrapper claims interface 0 immediately
            // after the control-plane probe.
            Log.w(TAG, "RM500 Framework claim unavailable; deferring claim to native libusb");
        }
        Log.i(TAG, "USB compatibility profile=" + profile);
        int fd = connection.getFileDescriptor();
        Thread thread = new Thread(() -> {
            try {
                if (profile == WfbUsbCompatibilityProfile.RM500_ROCKCHIP
                        && !awaitRm500ControlPlane(connection)) {
                    // Android 7 Rockchip builds can reject the Framework
                    // controlTransfer even when the authorized usbfs fd is
                    // valid and native libusb can initialize the adapter.
                    // Treat this request as a diagnostic warm-up only; the
                    // native Realtek initialization below is authoritative.
                    Log.w(TAG, "RM500 Framework control probe unavailable; continuing with native initialization");
                }
                int result = INIT_NATIVE_FAILED;
                int attempts = profile == WfbUsbCompatibilityProfile.RM500_ROCKCHIP
                        ? RM500_NATIVE_ATTEMPTS : 1;
                for (int attempt = 1; attempt <= attempts; attempt++) {
                    result = nativeRun(nativeInstance, context, channel, bandwidth, fd,
                            profile == WfbUsbCompatibilityProfile.RM500_ROCKCHIP);
                    if (result >= 0 || !isDeviceAttached(device)) break;
                    Log.w(TAG, "Native initialization failed profile=" + profile
                            + " attempt=" + attempt + "/" + attempts + " rc=" + result);
                    if (attempt < attempts) {
                        try { Thread.sleep(RM500_NATIVE_RETRY_MS); }
                        catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                if (result < 0) {
                    notifyState(ReceiverState.ERROR_USB_PROTOCOL,
                            profile == WfbUsbCompatibilityProfile.RM500_ROCKCHIP
                                    ? "请使用 DJI RC Assistant 准备图传接收端"
                                    : "图传接收端初始化失败");
                }
            } finally {
                synchronized (WfbNgLink.this) {
                    threads.remove(device);
                    UsbDeviceConnection completed = connections.remove(device);
                    if (completed != null) {
                        if (device.getInterfaceCount() > 0) completed.releaseInterface(device.getInterface(0));
                        completed.close();
                    }
                }
                Log.w(TAG, "Receiver stopped for " + device.getDeviceName());
            }
        }, "wfb-rx-" + device.getDeviceId());
        connections.put(device, connection);
        threads.put(device, thread);
        thread.start();
        Log.i(TAG, String.format("Started %04x:%04x channel=%d bandwidth=%d",
                device.getVendorId(), device.getProductId(), channel, bandwidth));
        return true;
    }

    private boolean awaitRm500ControlPlane(UsbDeviceConnection connection) {
        byte[] value = new byte[1];
        for (int attempt = 0; attempt < RM500_PROBE_DELAYS_MS.length; attempt++) {
            int result = connection.controlTransfer(0xC0, 5, 0x000A, 0, value, 1, USB_CONTROL_TIMEOUT_MS);
            Log.i(TAG, "RM500 control probe attempt=" + (attempt + 1) + "/"
                    + RM500_PROBE_DELAYS_MS.length + " result=" + result);
            if (result == 1) return true;
            try { Thread.sleep(RM500_PROBE_DELAYS_MS[attempt]); }
            catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean isDeviceAttached(UsbDevice device) {
        return ((UsbManager) context.getSystemService(Context.USB_SERVICE))
                .getDeviceList().containsKey(device.getDeviceName());
    }

    /** Called by JNI after radio/channel setup and before RX submissions. */
    @SuppressWarnings("unused")
    public void onNativeReceiverReady() {
        receiverReady = true;
        Log.i(TAG, "RECEIVER_READY");
        notifyState(ReceiverState.READY, "图传接收端已就绪");
    }

    private void notifyState(ReceiverState state, String detail) {
        ReceiverStateListener listener = stateListener;
        if (listener != null) listener.onReceiverStateChanged(state, detail);
    }

    public synchronized void stop(UsbDevice device) {
        stopInternal(device, true);
    }

    /** Called only from ACTION_USB_DEVICE_DETACHED. Native RX owns teardown. */
    public synchronized void stopDetached(UsbDevice device) {
        stopInternal(device, false);
    }

    private void stopInternal(UsbDevice device, boolean requestNativeStop) {
        // Remove ownership before entering native code. USB detach broadcasts,
        // Activity teardown and explicit stop can arrive together; only the
        // first caller may touch the native device and its mutexes.
        UsbDeviceConnection connection = connections.remove(device);
        Thread thread = threads.remove(device);
        if (connection == null) return;
        // When Android has already removed the device, the native RX loop will
        // observe NO_DEVICE and unwind itself. Calling nativeStop concurrently
        // with that unwind can race the driver's mutex destruction.
        boolean stillAttached = requestNativeStop && ((UsbManager) context.getSystemService(Context.USB_SERVICE))
                .getDeviceList().containsKey(device.getDeviceName());
        if (stillAttached) {
            nativeStop(nativeInstance, context, connection.getFileDescriptor());
        }
        if (thread != null) {
            try { thread.join(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (device.getInterfaceCount() > 0) connection.releaseInterface(device.getInterface(0));
        connection.close();
    }

    public synchronized void stopAll() {
        for (UsbDevice device : threads.keySet().toArray(new UsbDevice[0])) stop(device);
    }

    @Override public void onWfbNgStatsChanged(WfbNGStats data) {
        if (receiverReady && !receiverReceiving && data.count_p_all > 0) {
            receiverReceiving = true;
            Log.i(TAG, "RECEIVER_RECEIVING");
            notifyState(ReceiverState.RECEIVING, "正在接收 WFB 视频数据");
        }
        WfbNGStatsChanged listener = statsListener;
        if (listener != null) listener.onWfbNgStatsChanged(data);
    }

    @Override public synchronized void close() {
        stopAll();
        statsTimer.cancel();
        nativeFinalize(nativeInstance);
    }

    public interface ReceiverStateListener {
        void onReceiverStateChanged(ReceiverState state, String detail);
    }

    public enum ReceiverState { READY, RECEIVING, ERROR_USB_PROTOCOL }

    private static final int INIT_NATIVE_FAILED = -1002;
    private static final int RM500_NATIVE_ATTEMPTS = 2;
    private static final long RM500_NATIVE_RETRY_MS = 250L;
    private static final int USB_CONTROL_TIMEOUT_MS = 300;
    private static final long[] RM500_PROBE_DELAYS_MS = {20L, 40L, 80L, 160L, 320L, 500L};
}
