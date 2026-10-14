package app.scheduleplus;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.content.pm.PackageManager;
import static app.scheduleplus.Models.*;

public final class Reminders {
    public static final String CHANNEL="deadlines";
    private Reminders() {}
    public static void ensureChannel(Context c) {
        c.getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"任务提醒",NotificationManager.IMPORTANCE_HIGH));
    }
    static PendingIntent pending(Context c,Task t) {
        Intent i=new Intent(c,ReminderReceiver.class).setAction("app.scheduleplus.REMIND")
            .setData(Uri.parse("scheduleplus://task/"+t.id)).putExtra("id",t.id).putExtra("at",t.reminder);
        return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void sync(Context c,State old,State next) {
        ensureChannel(c);
        AlarmManager am=c.getSystemService(AlarmManager.class);
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        for(Task t:old.allTasks()) {
            Task n=next.findTask(t.id);
            if(n==null || n.done || n.reminded || n.reminder==null || !java.util.Objects.equals(n.reminder,t.reminder))am.cancel(pending(c,t));
            if(n==null || n.done || !java.util.Objects.equals(n.reminder,t.reminder))nm.cancel(t.id,1);
        }
        int scheduled=0,failed=0;long now=System.currentTimeMillis();
        for(Task t:next.allTasks()) {
            long trigger=nextReminderTrigger(t,now);
            if(trigger<0)continue;
            PendingIntent pi=pending(c,t);
            try {
                try {
                    if(Build.VERSION.SDK_INT<31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi);
                    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi);
                } catch(SecurityException denied) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi); }
                scheduled++;
            } catch(RuntimeException error) {failed++;record(c,"安排提醒失败："+error.getClass().getSimpleName());}
        }
        c.getSharedPreferences("reminder-diagnostics",Context.MODE_PRIVATE).edit().putInt("scheduled",scheduled).putInt("failed",failed).apply();
    }
    public static boolean allowed(Context c) {
        NotificationManager nm=c.getSystemService(NotificationManager.class);NotificationChannel channel=nm.getNotificationChannel(CHANNEL);
        return (Build.VERSION.SDK_INT<33 || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED)
            && nm.areNotificationsEnabled() && channel!=null && channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    public static String status(Context c) {
        ensureChannel(c);NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return "通知权限未授予";
        if(!nm.areNotificationsEnabled())return "应用通知已关闭";
        if(nm.getNotificationChannel(CHANNEL).getImportance()==NotificationManager.IMPORTANCE_NONE)return "任务提醒渠道已关闭";
        return "应用通知与任务提醒渠道已开启";
    }
    public static void record(Context c,String event) {
        c.getSharedPreferences("reminder-diagnostics",Context.MODE_PRIVATE).edit().putString("lastEvent",java.time.LocalDateTime.now().withNano(0)+"\n"+event).apply();
    }
    public static String diagnostics(Context c) {
        android.content.SharedPreferences p=c.getSharedPreferences("reminder-diagnostics",Context.MODE_PRIVATE);
        return "已安排 "+p.getInt("scheduled",0)+" 条提醒 · 失败 "+p.getInt("failed",0)+" 条\n最近记录："+p.getString("lastEvent","暂无触发记录");
    }
    static void notifyTask(Context c,Semester s,Task t) {
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        ensureChannel(c);
        Course course=s.course(t.courseId);
        String detail=(course==null?"个人任务":course.name)+" · "+t.date+(t.time==null?" 截止":" "+t.time+" 截止");
        Intent open=new Intent(c,MainActivity.class).putExtra("taskId",t.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.setData(Uri.parse("scheduleplus://task/"+t.id));
        PendingIntent tap=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        nm.notify(t.id,1,new Notification.Builder(c,CHANNEL).setSmallIcon(app.scheduleplus.R.drawable.ic_app)
            .setContentTitle(t.title).setContentText(detail).setContentIntent(tap).setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE).build());
    }
    public static void testNotification(Context c) {
        ensureChannel(c);if(!allowed(c))return;
        Intent open=new Intent(c,MainActivity.class).putExtra("reminderSettings",true);
        PendingIntent tap=PendingIntent.getActivity(c,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        c.getSystemService(NotificationManager.class).notify("diagnostic",1,new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_app)
            .setContentTitle("Schedule++ 测试通知").setContentText("通知渠道正常。再测试一分钟后提醒，检查定时调度。")
            .setContentIntent(tap).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build());
        record(c,"已发送测试通知");
    }
}
