package com.kingalexgilbert.kingpost;
import android.view.inputmethod.InputMethodManager;
import android.view.MotionEvent;
import android.graphics.Rect;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQUEST_OPEN_VIDEO = 1001;
    private static final int REQUEST_POST_NOTIFICATIONS = 1002;

    static final String ACTION_RETRY_INSTAGRAM =
            "com.kingalexgilbert.kingpost.action.RETRY_INSTAGRAM";
    static final String ACTION_DISMISS_INSTAGRAM_RETRY =
            "com.kingalexgilbert.kingpost.action.DISMISS_INSTAGRAM_RETRY";
    static final String INSTAGRAM_RETRY_CHANNEL_ID = "instagram_retry";
    static final int INSTAGRAM_RETRY_NOTIFICATION_ID = 3101;
    private static final String PREFS = "kingpost_prefs";
    private static final String KEY_VIDEO_URI = "video_uri";
    private static final String KEY_VIDEO_NAME = "video_name";
    private static final String KEY_CAPTION = "caption";
    private static final String KEY_INSTAGRAM_OPENED = "instagram_opened";
    private static final String KEY_TIKTOK_OPENED = "tiktok_opened";
    private static final String KEY_YOUTUBE_OPENED = "youtube_opened";
    private static final String KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested";

    private static final Platform INSTAGRAM = new Platform(
            "Instagram",
            KEY_INSTAGRAM_OPENED,
            Arrays.asList("com.instagram.android", "com.instagram.lite"),
            Collections.singletonList("instagram"),
            "Instagram or Instagram Lite is not installed."
    );

    private static final Platform TIKTOK = new Platform(
            "TikTok",
            KEY_TIKTOK_OPENED,
            Arrays.asList(
                    "com.zhiliaoapp.musically",
                    "com.zhiliaoapp.musically.go",
                    "com.ss.android.ugc.trill",
                    "com.ss.android.ugc.tiktok.lite",
                    "com.tiktok.lite.go"
            ),
            Arrays.asList("tiktok", "musically", "trill"),
            "TikTok or TikTok Lite is not installed."
    );

    private static final Platform YOUTUBE = new Platform(
            "YouTube",
            KEY_YOUTUBE_OPENED,
            Collections.singletonList("com.google.android.youtube"),
            Collections.singletonList("youtube"),
            "The regular YouTube app is not installed."
    );

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private SharedPreferences preferences;
    private Uri selectedVideoUri;
    private String selectedVideoName = "Selected video";
    private int thumbnailRequestId = 0;
    private int tiktokShareRequestId = 0;
    private int youtubeShareRequestId = 0;
    private boolean pendingInstagramShareAfterPermission = false;

    private ImageView videoThumbnail;
    private TextView thumbnailPlaceholder;
    private TextView videoName;
    private Button previewButton;
    private Button clearVideoButton;
    private EditText captionInput;
    private TextView captionCount;
    private TextView platformWarning;
    private Button instagramButton;
    private Button tiktokButton;
    private Button youtubeButton;
    private Button otherAppButton;
    private CheckBox instagramStatus;
    private CheckBox tiktokStatus;
    private CheckBox youtubeStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        createInstagramRetryNotificationChannel();
        bindViews();
        restoreSavedState();
        wireActions();
        updateShareButtons();
        updateStatuses();
        handleLaunchIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null || !ACTION_RETRY_INSTAGRAM.equals(intent.getAction())) {
            return;
        }

        // Clear the action so a later activity recreation does not retry again automatically.
        intent.setAction(null);
        shareToInstagramNow(true);
    }

    private void bindViews() {
        videoThumbnail = findViewById(R.id.videoThumbnail);
        thumbnailPlaceholder = findViewById(R.id.thumbnailPlaceholder);
        videoName = findViewById(R.id.videoName);
        previewButton = findViewById(R.id.previewButton);
        clearVideoButton = findViewById(R.id.clearVideoButton);
        captionInput = findViewById(R.id.captionInput);
        captionCount = findViewById(R.id.captionCount);
        platformWarning = findViewById(R.id.platformWarning);
        instagramButton = findViewById(R.id.instagramButton);
        tiktokButton = findViewById(R.id.tiktokButton);
        youtubeButton = findViewById(R.id.youtubeButton);
        otherAppButton = findViewById(R.id.otherAppButton);
        instagramStatus = findViewById(R.id.instagramStatus);
        tiktokStatus = findViewById(R.id.tiktokStatus);
        youtubeStatus = findViewById(R.id.youtubeStatus);
    }

    private void restoreSavedState() {
        captionInput.setText(preferences.getString(KEY_CAPTION, ""));
        updateCaptionCount();

        String savedUri = preferences.getString(KEY_VIDEO_URI, null);
        if (savedUri == null || savedUri.trim().isEmpty()) {
            showNoThumbnail("No video selected");
            return;
        }

        try {
            selectedVideoUri = Uri.parse(savedUri);
            selectedVideoName = preferences.getString(KEY_VIDEO_NAME, "Previously selected video");
            videoName.setText(selectedVideoName);
            showNoThumbnail("Saved video ready\nTap Play video preview to open it");
        } catch (RuntimeException exception) {
            clearUnavailableVideo(false);
        }
    }

    private void wireActions() {
        captionInput.setOnFocusChangeListener((view, hasFocus) -> captionInput.setCursorVisible(hasFocus));

        findViewById(R.id.selectVideoButton).setOnClickListener(v -> openVideoPicker());
        clearVideoButton.setOnClickListener(v -> clearSelectedVideo());
        previewButton.setOnClickListener(v -> openVideoPreview());
        findViewById(R.id.copyCaptionButton).setOnClickListener(v -> copyCaption());
        findViewById(R.id.clearCaptionButton).setOnClickListener(v -> captionInput.setText(""));

        captionInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                preferences.edit().putString(KEY_CAPTION, s.toString()).apply();
                updateCaptionCount();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        instagramButton.setOnClickListener(v -> beginInstagramShare());
        tiktokButton.setOnClickListener(v -> shareToTikTokFromCache());
        youtubeButton.setOnClickListener(v -> shareToYouTubeFromCache());
        otherAppButton.setOnClickListener(v -> shareUsingChooser());

        findViewById(R.id.resetProgressButton).setOnClickListener(v -> {
            preferences.edit()
                    .putBoolean(KEY_INSTAGRAM_OPENED, false)
                    .putBoolean(KEY_TIKTOK_OPENED, false)
                    .putBoolean(KEY_YOUTUBE_OPENED, false)
                    .apply();
            updateStatuses();
            Toast.makeText(this, "Opened checkmarks reset", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event != null
                    && event.getAction() == MotionEvent.ACTION_DOWN
                    && captionInput != null
                    && captionInput.hasFocus()) {
                Rect captionBounds = new Rect();
                boolean captionIsVisible = captionInput.getGlobalVisibleRect(captionBounds);
                int touchX = Math.round(event.getRawX());
                int touchY = Math.round(event.getRawY());

                if (!captionIsVisible || !captionBounds.contains(touchX, touchY)) {
                    dismissCaptionInput();
                }
            }

            return super.dispatchTouchEvent(event);
        }

        private void dismissCaptionInput() {
            if (captionInput == null) {
                return;
            }

            InputMethodManager keyboard =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.hideSoftInputFromWindow(captionInput.getWindowToken(), 0);
            }

            captionInput.clearFocus();
            captionInput.setCursorVisible(false);
        }

    private void openVideoPreview() {
        if (!ensureVideoSelected()) {
            return;
        }

        try {
            Intent previewIntent = new Intent(this, PreviewActivity.class);
            previewIntent.setData(selectedVideoUri);
            previewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(previewIntent);
        } catch (RuntimeException exception) {
            Toast.makeText(
                    this,
                    "The video preview could not be opened, but the video can still be shared.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void openVideoPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        try {
            startActivityForResult(intent, REQUEST_OPEN_VIDEO);
        } catch (RuntimeException exception) {
            Toast.makeText(this, "The Android video picker could not be opened.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_OPEN_VIDEO || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;

        try {
            if ((takeFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            }
        } catch (SecurityException ignored) {
            // Some providers offer only temporary access. The video remains usable this session.
        }

        if (!canReadVideo(uri)) {
            Toast.makeText(this, "That video could not be opened. Please choose another copy.", Toast.LENGTH_LONG).show();
            return;
        }

        Uri previousVideoUri = selectedVideoUri;
        selectedVideoUri = uri;
        tiktokShareRequestId++;
        youtubeShareRequestId++;
        if (previousVideoUri != null && !previousVideoUri.equals(uri)) {
            releasePersistedVideoPermission(previousVideoUri);
        }
        clearPlatformWarning();
        cancelInstagramRetryNotification(this);
        selectedVideoName = queryDisplayName(uri);
        videoName.setText(selectedVideoName);
        clearShareCache();

        preferences.edit()
                .putString(KEY_VIDEO_URI, uri.toString())
                .putString(KEY_VIDEO_NAME, selectedVideoName)
                .putBoolean(KEY_INSTAGRAM_OPENED, false)
                .putBoolean(KEY_TIKTOK_OPENED, false)
                .putBoolean(KEY_YOUTUBE_OPENED, false)
                .apply();

        loadThumbnailAsync(uri);
        updateShareButtons();
        updateStatuses();
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) {
                        return value;
                    }
                }
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "Selected video";
    }

    private long queryFileSize(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.SIZE},
                    null,
                    null,
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getLong(index);
                }
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1L;
    }

    private boolean canReadVideo(Uri uri) {
        if (uri == null) {
            return false;
        }
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            return stream != null;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private void loadThumbnailAsync(Uri uri) {
        final int requestId = ++thumbnailRequestId;
        showNoThumbnail("Loading thumbnail...");

        ioExecutor.execute(() -> {
            Bitmap thumbnail = extractThumbnail(uri);
            runOnUiThread(() -> {
                if (requestId != thumbnailRequestId || isFinishing() || isDestroyed()) {
                    if (thumbnail != null) {
                        thumbnail.recycle();
                    }
                    return;
                }

                if (thumbnail == null) {
                    showNoThumbnail("Thumbnail unavailable\nThe video can still be shared");
                } else {
                    videoThumbnail.setImageBitmap(thumbnail);
                    videoThumbnail.setVisibility(View.VISIBLE);
                    thumbnailPlaceholder.setVisibility(View.GONE);
                }
            });
        });
    }

    private Bitmap extractThumbnail(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                bitmap = retriever.getScaledFrameAtTime(
                        1_000_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        480,
                        270
                );
            } else {
                bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            if (bitmap == null) {
                bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            return bitmap;
        } catch (RuntimeException exception) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (IOException | RuntimeException ignored) {
                // Thumbnail cleanup failure should never stop the app or fail the build.
            }
        }
    }

    private void showNoThumbnail(String message) {
        videoThumbnail.setImageDrawable(null);
        videoThumbnail.setVisibility(View.GONE);
        thumbnailPlaceholder.setText(message);
        thumbnailPlaceholder.setVisibility(View.VISIBLE);
    }

    private void clearSelectedVideo() {
        if (selectedVideoUri == null) {
            return;
        }

        Uri videoUriToRelease = selectedVideoUri;
        selectedVideoUri = null;
        selectedVideoName = "Selected video";
        thumbnailRequestId++;
        tiktokShareRequestId++;
        youtubeShareRequestId++;

        showNoThumbnail("No video selected");
        videoName.setText("No video selected");
        clearPlatformWarning();
        cancelInstagramRetryNotification(this);

        preferences.edit()
                .remove(KEY_VIDEO_URI)
                .remove(KEY_VIDEO_NAME)
                .putBoolean(KEY_INSTAGRAM_OPENED, false)
                .putBoolean(KEY_TIKTOK_OPENED, false)
                .putBoolean(KEY_YOUTUBE_OPENED, false)
                .apply();

        releasePersistedVideoPermission(videoUriToRelease);
        setTikTokPreparing(false);
        setYouTubePreparing(false);
        updateShareButtons();
        updateStatuses();
        clearShareCache(true);
    }

    private void releasePersistedVideoPermission(Uri uri) {
        if (uri == null) {
            return;
        }

        try {
            for (UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
                if (!uri.equals(permission.getUri())) {
                    continue;
                }

                if (permission.isReadPermission()) {
                    getContentResolver().releasePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                }
                break;
            }
        } catch (RuntimeException ignored) {
            // Some document providers grant access without a persistable permission.
        }
    }

    private void clearUnavailableVideo(boolean showMessage) {
        Uri unavailableVideoUri = selectedVideoUri;
        selectedVideoUri = null;
        selectedVideoName = "Selected video";
        thumbnailRequestId++;
        tiktokShareRequestId++;
        youtubeShareRequestId++;
        showNoThumbnail("No video selected");
        videoName.setText("The saved video is no longer available. Choose it again.");
        preferences.edit().remove(KEY_VIDEO_URI).remove(KEY_VIDEO_NAME).apply();
        releasePersistedVideoPermission(unavailableVideoUri);
        cancelInstagramRetryNotification(this);
        clearShareCache();
        setTikTokPreparing(false);
        setYouTubePreparing(false);
        updateShareButtons();

        if (showMessage) {
            Toast.makeText(this, "The saved video permission expired. Choose the video again.", Toast.LENGTH_LONG).show();
        }
    }

    private void beginInstagramShare() {
        if (!ensureVideoSelected()) {
            return;
        }

        String packageName = findPlatformSharePackage(INSTAGRAM, selectedVideoUri, selectedVideoName);
        if (packageName == null) {
            showPlatformWarning(INSTAGRAM.missingMessage);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)) {
            pendingInstagramShareAfterPermission = true;
            preferences.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply();
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_POST_NOTIFICATIONS
            );
            return;
        }

        shareToInstagramNow(false);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_POST_NOTIFICATIONS && pendingInstagramShareAfterPermission) {
            pendingInstagramShareAfterPermission = false;
            shareToInstagramNow(false);
        }
    }

    private void shareToInstagramNow(boolean retry) {
        if (!ensureVideoSelected()) {
            cancelInstagramRetryNotification(this);
            return;
        }

        String packageName = findPlatformSharePackage(INSTAGRAM, selectedVideoUri, selectedVideoName);
        if (packageName == null) {
            cancelInstagramRetryNotification(this);
            showPlatformWarning(INSTAGRAM.missingMessage);
            return;
        }

        copyCaptionSilently();
        Intent targetedIntent = createVideoShareIntent(selectedVideoUri, selectedVideoName);
        targetedIntent.setPackage(packageName);

        try {
            startActivity(targetedIntent);
            clearPlatformWarning();
            markOpened(KEY_INSTAGRAM_OPENED);

            boolean notificationShown = showInstagramRetryNotification();
            String appName = installedAppLabel(packageName, "Instagram");
            String message;
            if (retry) {
                message = "Caption copied - trying " + appName
                        + " again. Retry before you begin editing.";
            } else if (notificationShown) {
                message = "Caption copied - opening " + appName
                        + ". If Reels does not open, use Try Again in the notification before editing.";
            } else {
                message = "Caption copied - opening " + appName
                        + ". Notifications are off, so return to King Post to retry.";
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException exception) {
            showPlatformWarning("Instagram is installed, but it could not accept this video.");
        } catch (RuntimeException exception) {
            showPlatformWarning("Android could not open Instagram for this video.");
        }
    }

    private void createInstagramRetryNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                INSTAGRAM_RETRY_CHANNEL_ID,
                "Instagram retry",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps a Try Again action available while sharing to Instagram.");
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    private boolean showInstagramRetryNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !canPostNotifications(manager)) {
            return false;
        }

        Intent retryIntent = new Intent(this, MainActivity.class);
        retryIntent.setAction(ACTION_RETRY_INSTAGRAM);
        retryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent retryPendingIntent = PendingIntent.getActivity(
                this,
                INSTAGRAM_RETRY_NOTIFICATION_ID,
                retryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent doneIntent = new Intent(this, InstagramRetryDismissReceiver.class);
        doneIntent.setAction(ACTION_DISMISS_INSTAGRAM_RETRY);
        PendingIntent donePendingIntent = PendingIntent.getBroadcast(
                this,
                INSTAGRAM_RETRY_NOTIFICATION_ID + 1,
                doneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String details = "Try again before editing, or tap Done when the video opens correctly.";
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, INSTAGRAM_RETRY_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
            builder.setPriority(Notification.PRIORITY_LOW);
            builder.setSound(null);
        }

        Notification notification = builder
                .setSmallIcon(R.drawable.ic_notification_kingpost)
                .setContentTitle("Instagram didn't open Reels?")
                .setContentText(details)
                .setStyle(new Notification.BigTextStyle().bigText(details))
                .setContentIntent(retryPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_notification_kingpost,
                        "Try Again",
                        retryPendingIntent
                ).build())
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_notification_kingpost,
                        "Done",
                        donePendingIntent
                ).build())
                .build();

        manager.notify(INSTAGRAM_RETRY_NOTIFICATION_ID, notification);
        return true;
    }

    private boolean canPostNotifications(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.N || manager.areNotificationsEnabled();
    }

    static void cancelInstagramRetryNotification(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(INSTAGRAM_RETRY_NOTIFICATION_ID);
        }
    }

    private void shareToPlatform(Platform platform) {
        if (!ensureVideoSelected()) {
            return;
        }

        String packageName = findPlatformSharePackage(platform, selectedVideoUri, selectedVideoName);
        if (packageName == null) {
            showPlatformWarning(platform.missingMessage);
            return;
        }

        copyCaptionSilently();
        Intent targetedIntent = createVideoShareIntent(selectedVideoUri, selectedVideoName);
        targetedIntent.setPackage(packageName);

        try {
            startActivity(targetedIntent);
            clearPlatformWarning();
            markOpened(platform.statusKey);
            Toast.makeText(
                    this,
                    "Caption copied - opening " + installedAppLabel(packageName, platform.displayName) + ".",
                    Toast.LENGTH_LONG
            ).show();
        } catch (ActivityNotFoundException exception) {
            showPlatformWarning(platform.displayName + " is installed, but it could not accept this video.");
        } catch (RuntimeException exception) {
            showPlatformWarning("Android could not open " + platform.displayName + " for this video.");
        }
    }

    

    private void shareToYouTubeFromCache() {
            if (!ensureVideoSelected()) {
                return;
            }

            final String packageName = "com.google.android.youtube";
            if (findFirstInstalledPackage(Collections.singletonList(packageName)) == null) {
                showPlatformWarning(
                        "The regular YouTube app is required to receive shared videos."
                );
                return;
            }

            copyCaptionSilently();
            clearPlatformWarning();
            setYouTubePreparing(true);
            Toast.makeText(this, "Preparing video...", Toast.LENGTH_SHORT).show();

            final Uri sourceUri = selectedVideoUri;
            final String sourceName = selectedVideoName;
            final int shareRequestId = ++youtubeShareRequestId;

            ioExecutor.execute(() -> {
                try {
                    File cachedVideo = copyVideoToShareCache(sourceUri, sourceName);
                    Uri sharedUri = ShareFileProvider.uriForFile(this, cachedVideo);

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (shareRequestId != youtubeShareRequestId
                                || selectedVideoUri == null
                                || !sourceUri.equals(selectedVideoUri)) {
                            return;
                        }

                        setYouTubePreparing(false);

                        // Do not pre-reject YouTube with resolveActivity or
                        // queryIntentActivities. Some YouTube builds accept the video
                        // even when Android's compatibility query reports no handler.
                        Intent targetedIntent = createVideoShareIntent(
                                sharedUri,
                                cachedVideo.getName()
                        );
                        targetedIntent.setType("video/*");
                        targetedIntent.setPackage(packageName);

                        try {
                            grantUriPermission(
                                    packageName,
                                    sharedUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                            startActivity(targetedIntent);
                            clearPlatformWarning();
                            markOpened(KEY_YOUTUBE_OPENED);
                            Toast.makeText(
                                    this,
                                    "Caption copied - opening YouTube.",
                                    Toast.LENGTH_LONG
                            ).show();
                        } catch (ActivityNotFoundException exception) {
                            showPlatformWarning(
                                    "The regular YouTube app is installed, but Android "
                                            + "could not open its video upload screen."
                            );
                        } catch (SecurityException exception) {
                            showPlatformWarning(
                                    "YouTube could not read King Post's temporary video copy."
                            );
                        } catch (RuntimeException exception) {
                            showPlatformWarning(
                                    "Android could not open YouTube for this video."
                            );
                        }
                    });
                } catch (IOException | RuntimeException exception) {
                    runOnUiThread(() -> {
                        if (!isFinishing()
                                && !isDestroyed()
                                && shareRequestId == youtubeShareRequestId) {
                            setYouTubePreparing(false);
                            showPlatformWarning(
                                    "The video could not be prepared for YouTube. "
                                            + "Choose it again and retry."
                            );
                        }
                    });
                }
            });
        }

    private void shareToTikTokFromCache() {
        if (!ensureVideoSelected()) {
            return;
        }

        final String packageName = findPlatformSharePackage(TIKTOK, selectedVideoUri, selectedVideoName);
        if (packageName == null) {
            showPlatformWarning(TIKTOK.missingMessage);
            return;
        }

        copyCaptionSilently();
        clearPlatformWarning();
        setTikTokPreparing(true);
        Toast.makeText(this, "Preparing video...", Toast.LENGTH_SHORT).show();

        final Uri sourceUri = selectedVideoUri;
        final String sourceName = selectedVideoName;
        final int shareRequestId = ++tiktokShareRequestId;
        ioExecutor.execute(() -> {
            try {
                File cachedVideo = copyVideoToShareCache(sourceUri, sourceName);
                Uri sharedUri = ShareFileProvider.uriForFile(this, cachedVideo);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (shareRequestId != tiktokShareRequestId
                            || selectedVideoUri == null
                            || !sourceUri.equals(selectedVideoUri)) {
                        clearShareCache();
                        return;
                    }
                    setTikTokPreparing(false);

                    Intent targetedIntent = createVideoShareIntent(sharedUri, cachedVideo.getName());
                    targetedIntent.setPackage(packageName);

                    try {
                        startActivity(targetedIntent);
                        clearPlatformWarning();
                        markOpened(KEY_TIKTOK_OPENED);
                        Toast.makeText(
                                this,
                                "Caption copied - opening " + installedAppLabel(packageName, "TikTok") + ".",
                                Toast.LENGTH_LONG
                        ).show();
                    } catch (ActivityNotFoundException exception) {
                        showPlatformWarning("TikTok is installed, but it could not accept this video.");
                    } catch (RuntimeException exception) {
                        showPlatformWarning("Android could not open TikTok for this video.");
                    }
                });
            } catch (IOException | RuntimeException exception) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed() && shareRequestId == tiktokShareRequestId) {
                        setTikTokPreparing(false);
                        showPlatformWarning("The video could not be prepared for TikTok. Choose it again and retry.");
                    }
                });
            }
        });
    }

    private File copyVideoToShareCache(Uri sourceUri, String originalName) throws IOException {
        File shareDirectory = new File(getCacheDir(), "share");
        if (!shareDirectory.exists() && !shareDirectory.mkdirs()) {
            throw new IOException("Could not create share cache");
        }

        String safeName = sanitizeVideoName(originalName, sourceUri);
        File outputFile = new File(shareDirectory, safeName);
        long sourceSize = queryFileSize(sourceUri);
        if (outputFile.isFile() && outputFile.length() > 0
                && (sourceSize <= 0 || outputFile.length() == sourceSize)) {
            return outputFile;
        }

        File tempFile = File.createTempFile("copy_", ".tmp", shareDirectory);
        boolean completed = false;
        try (InputStream input = getContentResolver().openInputStream(sourceUri);
             OutputStream output = new FileOutputStream(tempFile)) {
            if (input == null) {
                throw new IOException("Selected video is unavailable");
            }

            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            completed = true;
        } finally {
            if (!completed) {
                tempFile.delete();
            }
        }

        if (tempFile.length() == 0) {
            tempFile.delete();
            throw new IOException("Temporary video copy is empty");
        }

        if (outputFile.exists() && !outputFile.delete()) {
            tempFile.delete();
            throw new IOException("Could not replace old temporary video");
        }
        if (!tempFile.renameTo(outputFile)) {
            tempFile.delete();
            throw new IOException("Could not finish temporary video copy");
        }
        return outputFile;
    }

    private String sanitizeVideoName(String originalName, Uri sourceUri) {
        String name = originalName == null ? "video.mp4" : originalName.trim();
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isEmpty() || !name.contains(".")) {
            String mime = null;
            try {
                mime = getContentResolver().getType(sourceUri);
            } catch (RuntimeException ignored) {
            }
            name = "video" + extensionForMime(mime);
        }

        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : ".mp4";
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        return "shared_" + Integer.toHexString(sourceUri.toString().hashCode()) + "_" + base + extension;
    }

    private String extensionForMime(String mime) {
        if (mime == null) {
            return ".mp4";
        }
        switch (mime.toLowerCase(Locale.ROOT)) {
            case "video/webm":
                return ".webm";
            case "video/3gpp":
                return ".3gp";
            case "video/quicktime":
                return ".mov";
            case "video/x-matroska":
                return ".mkv";
            default:
                return ".mp4";
        }
    }

    private String findPlatformSharePackage(Platform platform, Uri videoUri, String displayName) {
        PackageManager packageManager = getPackageManager();
        Intent shareIntent = createVideoShareIntent(videoUri, displayName);
        String bestPackage = null;
        int bestScore = -1;

        try {
            List<ResolveInfo> handlers = packageManager.queryIntentActivities(
                    shareIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
            );
            for (ResolveInfo handler : handlers) {
                if (handler.activityInfo == null || !handler.activityInfo.enabled) {
                    continue;
                }

                String packageName = handler.activityInfo.packageName;
                CharSequence rawLabel = handler.loadLabel(packageManager);
                String appLabel = rawLabel == null ? "" : rawLabel.toString();
                int score = scorePlatformHandler(platform, packageName, appLabel);
                if (score > bestScore) {
                    bestScore = score;
                    bestPackage = packageName;
                }
            }
        } catch (RuntimeException ignored) {
        }

        if (bestPackage != null) {
            return bestPackage;
        }

        return findFirstInstalledPackage(platform.preferredPackages);
    }

    private int scorePlatformHandler(Platform platform, String packageName, String appLabel) {
        for (int index = 0; index < platform.preferredPackages.size(); index++) {
            if (platform.preferredPackages.get(index).equals(packageName)) {
                return 1000 - index;
            }
        }

        String searchable = (packageName + " " + appLabel).toLowerCase(Locale.ROOT);
        for (String keyword : platform.keywords) {
            if (searchable.contains(keyword.toLowerCase(Locale.ROOT))) {
                return 500;
            }
        }
        return -1;
    }

    private String findFirstInstalledPackage(List<String> packageNames) {
        PackageManager packageManager = getPackageManager();
        for (String packageName : packageNames) {
            try {
                if (packageManager.getApplicationInfo(packageName, 0).enabled) {
                    return packageName;
                }
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private String installedAppLabel(String packageName, String fallback) {
        try {
            CharSequence label = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0)
            );
            if (label != null && !label.toString().trim().isEmpty()) {
                return label.toString().trim();
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
        }
        return fallback;
    }

    private void showPlatformWarning(String message) {
        if (platformWarning != null) {
            platformWarning.setText(message);
            platformWarning.setVisibility(View.VISIBLE);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void clearPlatformWarning() {
        if (platformWarning != null) {
            platformWarning.setText("");
            platformWarning.setVisibility(View.GONE);
        }
    }

    private Intent createVideoShareIntent(Uri videoUri, String displayName) {
        String mimeType = "video/*";
        try {
            String detectedType = getContentResolver().getType(videoUri);
            if (detectedType != null && detectedType.startsWith("video/")) {
                mimeType = detectedType;
            }
        } catch (RuntimeException ignored) {
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, videoUri);
        intent.setClipData(ClipData.newUri(getContentResolver(), displayName, videoUri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    private void shareUsingChooser() {
        if (!ensureVideoSelected()) {
            return;
        }
        copyCaptionSilently();
        Toast.makeText(this, "Caption copied - choose an app, then paste it there.", Toast.LENGTH_LONG).show();
        openShareChooser(selectedVideoUri, selectedVideoName, "Share video with");
    }

    private void openShareChooser(Uri videoUri, String displayName, String title) {
        try {
            Intent shareIntent = createVideoShareIntent(videoUri, displayName);
            Intent chooser = Intent.createChooser(shareIntent, title);
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "No compatible sharing app was found.", Toast.LENGTH_LONG).show();
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Android could not open the share menu for this video.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean ensureVideoSelected() {
        if (selectedVideoUri == null) {
            Toast.makeText(this, "Choose a video first.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!canReadVideo(selectedVideoUri)) {
            clearUnavailableVideo(true);
            return false;
        }
        return true;
    }

    private void copyCaption() {
        if (copyCaptionSilently()) {
            Toast.makeText(this, "Caption copied.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "The caption could not be copied.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean copyCaptionSilently() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                return false;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("Post caption", captionInput.getText().toString()));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void setTikTokPreparing(boolean preparing) {
        tiktokButton.setEnabled(!preparing && selectedVideoUri != null);
        tiktokButton.setText(preparing ? "Preparing video..." : "TikTok");
    }

    private void setYouTubePreparing(boolean preparing) {
        youtubeButton.setEnabled(!preparing && selectedVideoUri != null);
        youtubeButton.setText(preparing ? "Preparing video..." : "YouTube");
    }


    private void clearShareCache() {
        clearShareCache(false);
    }

    private void clearShareCache(boolean showConfirmation) {
        ioExecutor.execute(() -> {
            File shareDirectory = new File(getCacheDir(), "share");
            boolean cleared = true;
            File[] files = shareDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.delete() && file.exists()) {
                        cleared = false;
                    }
                }
            }

            if (showConfirmation) {
                final boolean cacheCleared = cleared;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    String message = cacheCleared
                            ? "Video cleared. Temporary copy removed; your original video was not deleted."
                            : "Video cleared. Android may remove the remaining temporary copy later.";
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void markOpened(String key) {
        preferences.edit().putBoolean(key, true).apply();
        updateStatuses();
    }

    private void updateShareButtons() {
        boolean enabled = selectedVideoUri != null;
        instagramButton.setEnabled(enabled);
        tiktokButton.setEnabled(enabled);
        youtubeButton.setEnabled(enabled);
        otherAppButton.setEnabled(enabled);
        clearVideoButton.setEnabled(enabled);
        previewButton.setEnabled(true);
    }

    private void updateCaptionCount() {
        int length = captionInput.getText() == null ? 0 : captionInput.getText().length();
        captionCount.setText(length + (length == 1 ? " character" : " characters"));
    }

    private void updateStatuses() {
        updateStatus(instagramStatus, preferences.getBoolean(KEY_INSTAGRAM_OPENED, false));
        updateStatus(tiktokStatus, preferences.getBoolean(KEY_TIKTOK_OPENED, false));
        updateStatus(youtubeStatus, preferences.getBoolean(KEY_YOUTUBE_OPENED, false));
    }

    private void updateStatus(CheckBox box, boolean opened) {
        box.setChecked(opened);
        box.setText(opened ? "Opened ✓" : "Not opened yet");
    }

    @Override
    protected void onDestroy() {
        thumbnailRequestId++;
        tiktokShareRequestId++;
        youtubeShareRequestId++;
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private static final class Platform {
        final String displayName;
        final String statusKey;
        final List<String> preferredPackages;
        final List<String> keywords;
        final String missingMessage;

        Platform(
                String displayName,
                String statusKey,
                List<String> preferredPackages,
                List<String> keywords,
                String missingMessage
        ) {
            this.displayName = displayName;
            this.statusKey = statusKey;
            this.preferredPackages = preferredPackages;
            this.keywords = keywords;
            this.missingMessage = missingMessage;
        }
    }
}
