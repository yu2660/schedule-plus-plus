package app.scheduleplus;

import android.content.*;
import static app.scheduleplus.Models.*;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        // Protected system broadcasts only; this receiver is not exported to other apps.
        try {
            Store store=new Store(c); State state=store.load();Reminders.ensureChannel(c);
            if(!"app.scheduleplus.REMIND".equals(intent.getAction())) { Reminders.sync(c,state,state); return; }
            Task t=state.findTask(intent.getStringExtra("id"));
            if(t==null || t.done || t.reminder==null || t.reminded || !t.reminder.equals(intent.getStringExtra("at"))) return;
            if(t.reminderMillis()>System.currentTimeMillis()+1000) return;
            if(Reminders.allowed(c)) {
                Reminders.notifyTask(c,state.taskOwner(t.id),t);
                t.reminded=true; store.save(state);
                Reminders.record(c,"定时提醒已触发并发送通知");
            } else Reminders.record(c,"提醒已触发，但通知权限或渠道未开启");
        } catch(Exception e) {
            // No personal data is logged; the app surfaces unreadable data on the next launch.
            android.util.Log.e("SchedulePlus","Reminder processing failed: "+e.getClass().getSimpleName());
            Reminders.record(c,"提醒处理失败："+e.getClass().getSimpleName());
        }
    }
}
