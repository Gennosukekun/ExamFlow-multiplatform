package com.examflow.italia;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class StudyReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channel="examflow_study";
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel nc=new NotificationChannel(channel,"Promemoria studio",NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(nc);
        }
        Intent open=new Intent(context,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT>=26 ? new Notification.Builder(context,channel) : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
         .setContentTitle("ExamFlow Italia")
         .setContentText("Controlla il piano di oggi e completa il tuo prossimo blocco di studio.")
         .setAutoCancel(true)
         .setContentIntent(pi);
        nm.notify(9001,b.build());
    }
}
