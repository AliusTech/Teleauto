package tech.alius.rc.inputprobe;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i("TeleautoInputProbe", "BOOT_RECEIVED");
            context.startService(new Intent(context, InputProbeService.class));
        }
    }
}
