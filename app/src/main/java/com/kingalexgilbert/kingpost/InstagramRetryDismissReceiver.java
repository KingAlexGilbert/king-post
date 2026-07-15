package com.kingalexgilbert.kingpost;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class InstagramRetryDismissReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        MainActivity.cancelInstagramRetryNotification(context);
    }
}
