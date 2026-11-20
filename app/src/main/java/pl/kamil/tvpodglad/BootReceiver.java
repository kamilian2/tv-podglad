package pl.kamil.tvpodglad;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Po starcie TV otwiera MainActivity, która od razu prosi o zgodę i uruchamia usługę. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Config.AUTOSTART || CaptureService.running) return;
        Intent i = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_AUTOSTART, true);
        context.startActivity(i);
    }
}
