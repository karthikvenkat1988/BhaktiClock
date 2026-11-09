package com.bhakticlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Android forgets alarms on reboot or clock changes; this re-arms every schedule. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Scheduler.rescheduleAll(c);
    }
}
