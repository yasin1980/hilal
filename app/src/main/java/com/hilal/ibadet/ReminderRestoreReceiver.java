\
package com.hilal.ibadet;
import android.content.*;
public class ReminderRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){ ReminderScheduler.restoreAll(c); }
}
