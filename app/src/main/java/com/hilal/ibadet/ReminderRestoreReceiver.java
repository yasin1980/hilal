\
package com.nsp112.hilal;
import android.content.*;
public class ReminderRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){ ReminderScheduler.restoreAll(c); }
}
