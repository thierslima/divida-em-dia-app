package com.thierslima.dividaemdia;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 3101;
    private static final int FILE_CHOOSER_REQUEST = 3102;
    private static final int SYSTEM_SOUND_REQUEST = 3103;
    private static final int AUDIO_FILE_REQUEST = 3104;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#063B4D"));
        getWindow().setNavigationBarColor(Color.WHITE);
        NotificationHelper.ensureChannels(this);

        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (MainActivity.this.filePathCallback != null) MainActivity.this.filePathCallback.onReceiveValue(null);
                MainActivity.this.filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception ex) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Não foi possível abrir os arquivos.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
        webView.addJavascriptInterface(new AppBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView == null) { super.onBackPressed(); return; }
        webView.evaluateJavascript("window.appBack ? window.appBack() : false", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            return;
        }

        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == SYSTEM_SOUND_REQUEST) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                String name = "Som do celular";
                try {
                    Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
                    if (ringtone != null) name = ringtone.getTitle(this);
                } catch (Exception ignored) {}
                sendSoundToJs("system", uri, name);
            }
            return;
        }

        if (requestCode == AUDIO_FILE_REQUEST) {
            Uri source = data.getData();
            if (source == null) return;
            try {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try { getContentResolver().takePersistableUriPermission(source, flags); } catch (Exception ignored) {}
                String name = getDisplayName(source);
                Uri usable = importNotificationAudio(source, name);
                sendSoundToJs("file", usable != null ? usable : source, name);
            } catch (Exception ex) {
                Toast.makeText(this, "Não foi possível importar esse áudio.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void sendSoundToJs(String kind, Uri uri, String name) {
        if (webView == null || uri == null) return;
        final String js = "window.onAndroidSoundPicked && window.onAndroidSoundPicked(" +
                JSONObject.quote(kind) + "," + JSONObject.quote(uri.toString()) + "," + JSONObject.quote(name == null ? "Som personalizado" : name) + ")";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private String getDisplayName(Uri uri) {
        String name = "audio-notificacao";
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && cursor.getString(idx) != null) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return name;
    }

    private Uri importNotificationAudio(Uri source, String originalName) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return source;

        String mime = getContentResolver().getType(source);
        if (mime == null || !mime.startsWith("audio/")) mime = "audio/mpeg";
        String clean = (originalName == null ? "som" : originalName).replaceAll("[^a-zA-Z0-9._ áàâãéêíóôõúçÁÀÂÃÉÊÍÓÔÕÚÇ-]", "_");
        String display = "DED_" + System.currentTimeMillis() + "_" + clean;

        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, display);
        values.put(MediaStore.Audio.Media.MIME_TYPE, mime);
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_NOTIFICATIONS + "/DividaEmDia");
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, 1);

        Uri dest = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (dest == null) return source;
        try (InputStream in = getContentResolver().openInputStream(source);
             OutputStream out = getContentResolver().openOutputStream(dest)) {
            if (in == null || out == null) throw new IllegalStateException("Falha ao abrir áudio");
            byte[] buf = new byte[16 * 1024]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return dest;
    }

    private class AppBridge {
        @JavascriptInterface
        public void requestNotificationPermission() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
                } else {
                    Toast.makeText(MainActivity.this, "Notificações ativadas.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void syncReminders(String json) {
            String oldJson = getSharedPreferences("divida_em_dia", MODE_PRIVATE).getString("reminders_json", null);
            if (oldJson != null) ReminderScheduler.cancelAll(MainActivity.this, oldJson);
            getSharedPreferences("divida_em_dia", MODE_PRIVATE).edit().putString("reminders_json", json).apply();
            ReminderScheduler.scheduleAll(MainActivity.this, json);
        }

        @JavascriptInterface
        public void pickSystemNotificationSound() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Escolha um som de notificação");
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                    startActivityForResult(intent, SYSTEM_SOUND_REQUEST);
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "Não foi possível abrir os sons do celular.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void pickAudioFile() {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("audio/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(intent, AUDIO_FILE_REQUEST);
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "Não foi possível abrir seus arquivos de áudio.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void previewSound(String mode, String uri) {
            runOnUiThread(() -> NotificationHelper.previewSound(MainActivity.this, mode, uri));
        }

        @JavascriptInterface
        public void testNotification(String mode, String uri, boolean vibration) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
                    Toast.makeText(MainActivity.this, "Permita notificações e toque em testar novamente.", Toast.LENGTH_LONG).show();
                    return;
                }
                NotificationHelper.showTestNotification(MainActivity.this, mode, uri, vibration);
            });
        }

        @JavascriptInterface
        public void saveTextFile(String filename, String mimeType, String content) {
            try {
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                    values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DividaEmDia");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IllegalStateException("Falha ao criar arquivo");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) { if (out != null) out.write(bytes); }
                } else {
                    File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "DividaEmDia");
                    if (!dir.exists()) dir.mkdirs();
                    try (FileOutputStream out = new FileOutputStream(new File(dir, filename))) { out.write(bytes); }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Arquivo salvo na pasta Downloads/DividaEmDia.", Toast.LENGTH_LONG).show());
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Não foi possível salvar o arquivo.", Toast.LENGTH_LONG).show());
            }
        }
    }
}
