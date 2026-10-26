package com.thierslima.dividaemdia;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
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
import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    private boolean areNotificationsEnabled() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            return manager == null || manager.areNotificationsEnabled();
        }
        return true;
    }

    private void sendNotificationPermissionToJs(boolean enabled) {
        if (webView == null) return;
        final String js = "window.onNotificationPermissionChanged && window.onNotificationPermissionChanged(" + (enabled ? "true" : "false") + ")";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            sendNotificationPermissionToJs(granted);
            if (granted) {
                String json = getSharedPreferences("divida_em_dia", MODE_PRIVATE).getString("reminders_json", null);
                if (json != null) ReminderScheduler.scheduleAll(this, json);
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

    private String fitText(String text, Paint paint, float maxWidth) {
        if (text == null) return "";
        if (paint.measureText(text) <= maxWidth) return text;
        String ellipsis = "…";
        float ellipsisWidth = paint.measureText(ellipsis);
        int end = text.length();
        while (end > 0 && paint.measureText(text.substring(0, end)) + ellipsisWidth > maxWidth) end--;
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }

    private void drawPdfHeader(Canvas canvas, Paint paint, int pageNumber, String generatedAt) {
        paint.setColor(Color.rgb(5, 92, 92));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(22f);
        canvas.drawText("Dívida em Dia", 36f, 48f, paint);

        paint.setColor(Color.rgb(70, 85, 100));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(9.5f);
        canvas.drawText("Relatório financeiro • Desenvolvido por Thiers Lima", 36f, 66f, paint);
        canvas.drawText("Gerado em " + generatedAt, 36f, 80f, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Página " + pageNumber, 559f, 66f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.rgb(210, 220, 228));
        paint.setStrokeWidth(1f);
        canvas.drawLine(36f, 90f, 559f, 90f, paint);
    }

    private void drawPdfTableHeader(Canvas canvas, Paint paint, float y) {
        paint.setColor(Color.rgb(236, 245, 245));
        canvas.drawRect(36f, y - 17f, 559f, y + 7f, paint);
        paint.setColor(Color.rgb(20, 45, 60));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(9f);
        canvas.drawText("Vencimento", 42f, y, paint);
        canvas.drawText("Conta / Parcela", 112f, y, paint);
        canvas.drawText("Categoria", 304f, y, paint);
        canvas.drawText("Valor", 409f, y, paint);
        canvas.drawText("Status", 486f, y, paint);
    }

    private void exportPdfNative(String reportJson) {
        new Thread(() -> {
            PdfDocument document = new PdfDocument();
            try {
                JSONObject report = new JSONObject(reportJson);
                JSONObject summary = report.optJSONObject("summary");
                JSONArray rows = report.optJSONArray("rows");
                String generatedAt = report.optString("generatedAt", new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()));
                String monthLabel = report.optString("monthLabel", "Mês atual");
                String userName = report.optString("userName", "");

                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

                int pageNumber = 1;
                PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create());
                Canvas canvas = page.getCanvas();
                drawPdfHeader(canvas, paint, pageNumber, generatedAt);

                float y = 120f;
                if (!userName.trim().isEmpty()) {
                    paint.setColor(Color.rgb(20, 45, 60));
                    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                    paint.setTextSize(11f);
                    canvas.drawText("Titular: " + fitText(userName, paint, 450f), 36f, y, paint);
                    y += 20f;
                }

                paint.setColor(Color.rgb(20, 45, 60));
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paint.setTextSize(12f);
                canvas.drawText("Resumo — " + monthLabel, 36f, y, paint);
                y += 20f;

                String monthPending = summary != null ? summary.optString("monthPending", "R$ 0,00") : "R$ 0,00";
                String totalPending = summary != null ? summary.optString("totalPending", "R$ 0,00") : "R$ 0,00";
                String overdue = summary != null ? summary.optString("overdue", "R$ 0,00") : "R$ 0,00";
                String paidMonth = summary != null ? summary.optString("paidMonth", "R$ 0,00") : "R$ 0,00";

                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                paint.setTextSize(10f);
                paint.setColor(Color.rgb(50, 65, 80));
                canvas.drawText("Pendente do mês: " + monthPending, 42f, y, paint);
                canvas.drawText("Total pendente: " + totalPending, 300f, y, paint);
                y += 18f;
                canvas.drawText("Atrasadas: " + overdue, 42f, y, paint);
                canvas.drawText("Pagas no mês: " + paidMonth, 300f, y, paint);
                y += 32f;

                paint.setColor(Color.rgb(20, 45, 60));
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paint.setTextSize(12f);
                canvas.drawText("Contas e parcelas", 36f, y, paint);
                y += 28f;
                drawPdfTableHeader(canvas, paint, y);
                y += 20f;

                if (rows == null || rows.length() == 0) {
                    paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                    paint.setTextSize(10f);
                    paint.setColor(Color.rgb(90, 100, 110));
                    canvas.drawText("Nenhuma dívida cadastrada.", 42f, y + 10f, paint);
                } else {
                    for (int i = 0; i < rows.length(); i++) {
                        if (y > 785f) {
                            document.finishPage(page);
                            pageNumber++;
                            page = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create());
                            canvas = page.getCanvas();
                            drawPdfHeader(canvas, paint, pageNumber, generatedAt);
                            y = 122f;
                            drawPdfTableHeader(canvas, paint, y);
                            y += 20f;
                        }

                        JSONObject row = rows.optJSONObject(i);
                        if (row == null) continue;
                        String due = row.optString("due", "");
                        String name = row.optString("name", "");
                        String category = row.optString("category", "");
                        String amount = row.optString("amount", "");
                        String status = row.optString("status", "");

                        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
                        paint.setTextSize(8.8f);
                        paint.setColor(Color.rgb(35, 48, 60));
                        canvas.drawText(fitText(due, paint, 62f), 42f, y, paint);
                        canvas.drawText(fitText(name, paint, 184f), 112f, y, paint);
                        canvas.drawText(fitText(category, paint, 96f), 304f, y, paint);
                        canvas.drawText(fitText(amount, paint, 70f), 409f, y, paint);

                        if ("Atrasado".equals(status)) paint.setColor(Color.rgb(190, 45, 55));
                        else if ("Pago".equals(status)) paint.setColor(Color.rgb(20, 135, 78));
                        else paint.setColor(Color.rgb(70, 85, 100));
                        canvas.drawText(fitText(status, paint, 70f), 486f, y, paint);

                        paint.setColor(Color.rgb(225, 231, 236));
                        canvas.drawLine(36f, y + 9f, 559f, y + 9f, paint);
                        y += 24f;
                    }
                }

                document.finishPage(page);

                String filename = "DividaEmDia-Relatorio-" + new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new Date()) + ".pdf";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DividaEmDia");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IllegalStateException("Falha ao criar PDF");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new IllegalStateException("Falha ao abrir PDF");
                        document.writeTo(out);
                    }
                } else {
                    File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "DividaEmDia");
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Falha ao criar pasta");
                    try (FileOutputStream out = new FileOutputStream(new File(dir, filename))) {
                        document.writeTo(out);
                    }
                }

                runOnUiThread(() -> Toast.makeText(MainActivity.this, "PDF salvo em Downloads/DividaEmDia.", Toast.LENGTH_LONG).show());
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Não foi possível gerar o PDF.", Toast.LENGTH_LONG).show());
            } finally {
                try { document.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private class AppBridge {
        @JavascriptInterface
        public boolean notificationsEnabled() {
            return areNotificationsEnabled();
        }

        @JavascriptInterface
        public void requestNotificationPermission() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
                } else {
                    sendNotificationPermissionToJs(true);
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
        public void exportPdf(String reportJson) {
            exportPdfNative(reportJson);
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
