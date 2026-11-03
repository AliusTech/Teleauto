package tech.alius.rc.inputprobe;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.util.Log;
import java.lang.reflect.*;

/** Feasibility probe: receive only; no IPC exports, network, control packets or identity spoofing. */
public final class InputProbeService extends Service {
    private static final String TAG = "TeleautoInputProbe";
    private static final String DESCRIPTOR = "com.dji.protocol.IPackListener";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile long frames;
    private volatile long allFrames;
    private final java.util.Map<Integer, Long> counts = new java.util.TreeMap<>();
    private final java.util.Map<Integer, String> previousData = new java.util.HashMap<>();
    private long logWindow;
    private int windowLogs;
    private volatile long suppressedChanges;
    private Field cmdSetField, cmdIdField;
    private int previousBits = -1;
    private Object manager, listener;
    private Class<?> listenerClass;
    private Field dataField;
    private Parcelable.Creator<?> creator;
    private final Runnable connect = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try { subscribe(); }
            catch (Exception error) {
                Log.e(TAG, "SUBSCRIBE_FAILED", error);
                release();
                if (running) handler.postDelayed(this, 10000);
            }
        }
    };
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (!running) return;
            Log.i(TAG, "STATUS subscribed=" + (manager != null) + " frames=" + frames + " packets=" + allFrames + " suppressed=" + suppressedChanges + " commands=" + commandCounts());
            handler.postDelayed(this, 10000);
        }
    };
    @Override public void onCreate() {
        super.onCreate(); running = true;
        int flags = getApplicationInfo().flags;
        Log.i(TAG, "START package=" + getPackageName() + " uid=" + android.os.Process.myUid()
                + " system=" + ((flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                + " updatedSystem=" + ((flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0));
        handler.post(connect); handler.post(heartbeat);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    private void subscribe() throws Exception {
        Class<?> managerType = Class.forName("com.dji.protocol.ProtocolManager");
        Class<?> filterType = Class.forName("com.dji.protocol.PackFilter");
        Class<?> ruleType = Class.forName("com.dji.protocol.PackRule");
        Class<?> builderType = Class.forName("com.dji.protocol.PackRule$Builder");
        Class<?> packType = Class.forName("com.dji.protocol.Pack");
        listenerClass = Class.forName(DESCRIPTOR);
        dataField = packType.getDeclaredField("data");
        cmdSetField = packType.getDeclaredField("cmdSet");
        cmdIdField = packType.getDeclaredField("cmdId");
        creator = (Parcelable.Creator<?>) packType.getDeclaredField("CREATOR").get(null);
        Binder binder = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(DESCRIPTOR); return true; }
                if (code != 1 && code != 2) return super.onTransact(code, data, reply, flags);
                data.enforceInterface(DESCRIPTOR);
                if (code == 1) receive(data.readInt() != 0 ? creator.createFromParcel(data) : null);
                else Log.w(TAG, "CALLBACK_FAILURE");
                if (reply != null && (flags & FLAG_ONEWAY) == 0) reply.writeNoException();
                return true;
            }
        };
        listener = Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class<?>[]{listenerClass}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "asBinder": return binder;
                case "onSuccess": receive(args == null ? null : args[0]); return null;
                case "onFailure": Log.w(TAG, "CALLBACK_FAILURE"); return null;
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return args != null && proxy == args[0];
                case "toString": return TAG;
                default: return null;
            }
        });
        binder.attachInterface((IInterface) listener, DESCRIPTOR);
        Object instance = managerType.getDeclaredMethod("getDefault").invoke(null);
        Object filter = filterType.getDeclaredMethod("obtain").invoke(null);
        // Read-only subscription: all RC commands, plus known status/heartbeat controls.
        for (int id = 0; id < 256; id++) addRule(filter, filterType, ruleType, builderType, 6, id);
        addRule(filter, filterType, ruleType, builderType, 0, 0x42);
        addRule(filter, filterType, ruleType, builderType, 33, 6);
        managerType.getDeclaredMethod("addPackListener", filterType, listenerClass).invoke(instance, filter, listener);
        manager = instance;
        Log.i(TAG, "SUBSCRIBED cmd=06/*,00/42,21/06");
    }
    private void addRule(Object filter, Class<?> filterType, Class<?> ruleType, Class<?> builderType, int set, int id) throws Exception {
        Object builder = builderType.getDeclaredConstructor().newInstance();
        builderType.getDeclaredMethod("cmd", int.class, int.class).invoke(builder, set, id);
        Object rule = builderType.getDeclaredMethod("build").invoke(builder);
        filterType.getDeclaredMethod("addRule", ruleType).invoke(filter, rule);
    }
    private synchronized String commandCounts() { return counts.toString(); }
    private synchronized void receive(Object pack) {
        if (!running || pack == null) return;
        try {
            byte[] data = (byte[]) dataField.get(pack);
            int set = ((Number) cmdSetField.get(pack)).intValue() & 255;
            int id = ((Number) cmdIdField.get(pack)).intValue() & 255;
            int key = (set << 8) | id;
            allFrames++;
            counts.put(key, counts.getOrDefault(key, 0L) + 1);
            StringBuilder hex = new StringBuilder();
            if (data != null && set == 6) for (int i = 0; i < Math.min(data.length, 32); i++) hex.append(String.format(java.util.Locale.US, "%02x", data[i] & 255));
            String current = hex.toString();
            String old = previousData.get(key);
            long now = SystemClock.elapsedRealtime();
            if (now - logWindow >= 1000) { logWindow = now; windowLogs = 0; }
            if ((old == null || !old.equals(current)) && windowLogs < 20) {
                windowLogs++;
                previousData.put(key, current);
                Log.i(TAG, "PACKET elapsedNs=" + SystemClock.elapsedRealtimeNanos() + " seq=" + allFrames + " cmd=" + Integer.toHexString(set) + "/" + Integer.toHexString(id)
                    + " length=" + (data == null ? 0 : data.length) + " truncated=" + (data != null && data.length > 32)
                    + " data=" + current);
            } else if (old == null || !old.equals(current)) {
                suppressedChanges++;
            }
            if (set != 6 || id != 0xAE) return;
            if (data == null || data.length < 17) { Log.w(TAG, "SHORT_RC_PACKET"); return; }
            frames++;
            int bits = (data[0]&255) | ((data[1]&255)<<8) | ((data[2]&255)<<16) | ((data[3]&255)<<24);
            if (frames == 1 || previousBits != bits) {
                previousBits = bits;
                Log.i(TAG, "INPUT frames=" + frames + " bits=" + Integer.toHexString(bits)
                        + " C1=" + ((bits>>>18)&1) + " C2=" + ((bits>>>19)&1) + " C3=" + ((bits>>>20)&1)
                        + " RTH=" + ((bits>>>5)&1) + " mode=" + ((bits>>>16)&3));
            }
        } catch (Exception error) { Log.e(TAG, "DECODE_FAILED", error); }
    }
    private void release() {
        if (manager != null && listener != null) {
            try { manager.getClass().getDeclaredMethod("removePackListener", listenerClass).invoke(manager, listener); }
            catch (Exception error) { Log.w(TAG, "UNSUBSCRIBE_FAILED", error); }
        }
        manager = null; listener = null;
    }
    @Override public void onDestroy() {
        running = false; handler.removeCallbacksAndMessages(null); release(); super.onDestroy();
    }
}
