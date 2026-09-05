android.widget.RemoteViews compact = new android.widget.RemoteViews(context.getPackageName(), R.layout.notification_hilal);
compact.setTextViewText(android.R.id.title, safeTitle);
compact.setTextViewText(android.R.id.text1, safeBody);
android.widget.RemoteViews expanded = new android.widget.RemoteViews(context.getPackageName(), R.layout.notification_hilal);
expanded.setTextViewText(android.R.id.title, safeTitle);
expanded.setTextViewText(android.R.id.text1, safeBody);

Notification.Builder note = new Notification.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(safeTitle)
        .setContentText(safeBody)
        .setCustomContentView(compact)
        .setCustomBigContentView(expanded)
        .setCustomHeadsUpContentView(compact)
        .setContentIntent(content).setAutoCancel(true).setPriority(Notification.PRIORITY_HIGH)
        .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PUBLIC)
        .setSound(null);
