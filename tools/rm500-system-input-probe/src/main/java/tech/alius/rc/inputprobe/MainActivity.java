package tech.alius.rc.inputprobe;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
public final class MainActivity extends Activity {
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        android.util.Log.i("TeleautoInputProbe", "ANDROID_KEY time=" + event.getEventTime()
                + " action=" + event.getAction() + " keyCode=" + event.getKeyCode()
                + " scanCode=" + event.getScanCode() + " repeat=" + event.getRepeatCount()
                + " device=" + event.getDeviceId() + " source=" + event.getSource());
        return super.dispatchKeyEvent(event);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText("Teleauto 系统输入诊断\n仅订阅遥控器输入，不发送控制命令。\n诊断结果：adb logcat -s TeleautoInputProbe");
        setContentView(text);
        startService(new Intent(this, InputProbeService.class));
    }
}
