package com.dt3264.deezloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.HandlerThread;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.Objects;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MyService extends Service {

    int NOTIFICATION_ID = 100;
    final int MAIN_NOTIFICATION_ID = 10;
    public static boolean pageAlreadyOpen = false;
    public static boolean isServiceRunning = false;
    public static boolean hasRequestedNewPath = false;
    static Socket socket;
    Context context;
    SharedPreferences sharedPreferences;
    final String CHANNEL_ID = "com.dt3264.Deezloader";

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        HandlerThread thread = new HandlerThread("Thread Name");
        //Start the thread//
        thread.start();
        preparaHandler();
        createNotificationChannel();
        preparaNodeServerListeners();
        startServiceWithNotification();
        startServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Objects.requireNonNull(intent.getAction());
            getString(R.string.serviceName);
        }
        return START_NOT_STICKY;
    }

    void stopMyService() {
        if(serverThread!=null)
            serverThread.interrupt();
        serverThread = null;
        stopForeground(true);
        stopSelf();
        isServiceRunning = false;
    }

    // In case the service is deleted or crashes some how
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMyService();
        transmitMessage(new Message(4, "Exit app"));
        //EventBus.getDefault().postSticky(new Message(4, "Exit app"));
        //EventBus.getDefault().unregister(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case of bound services.
        return null;
    }

    Thread serverThread;
    void startServer(){
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                //The path where we expect the node project to be at runtime.
                String nodeDir = getApplicationContext().getFilesDir().getAbsolutePath() + "/deezerLoader";
                MainActivity.startNodeWithArguments(new String[]{"node", nodeDir + "/app.js"});
            }
        });
        serverThread.start();
    }

    /**
     * Notification methods
     */
    void startServiceWithNotification() {
        if (isServiceRunning) return;
        isServiceRunning = true;

        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setTicker(getResources().getString(R.string.app_name))
                .setContentText(getResources().getString(R.string.notificationService))
                .setSmallIcon(R.mipmap.ic_notification)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setOngoing(false)
                //.setDeleteIntent(getDeleteIntent())  // if needed
                .build();
        startForeground(MAIN_NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Service notifications";
            String description = "Chanel when deezloader server is running";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setSound(null, null);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            Objects.requireNonNull(notificationManager).createNotificationChannel(channel);
        }
    }

    void notificaDescarga(int progress, String songName){
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle("Downloading: " + songName)
                .setSubText(progress > 0 ? progress + "%" : "")
                .setProgress(100, progress, (progress==0))
                .setTimeoutAfter(60000)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        mBuilder.setOnlyAlertOnce(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        // notificationId is a unique int for each notification that you must define
        if(progress<100) notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    enum songLastStatus{
        READY,
        CANCELLED,
        ALREADY_DOWNLOADED
    }

    void acabaNotificacion(String songName, songLastStatus lastStatus){
        String message = "";
        switch (lastStatus){
            case READY:
                message = "Downloaded: " + songName;
                break;
            case CANCELLED:
                message = "Cancelled: " + songName;
                break;

            case ALREADY_DOWNLOADED:
                message = "Already downloaded: " + songName;
                break;
        }
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle(message)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        mBuilder.setOnlyAlertOnce(true);
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, mBuilder.build());
        NOTIFICATION_ID++;
    }

    /**
     * NodeJs socket methods
     */
    final String url = "http://localhost:1730";
    void preparaNodeServerListeners() {
        try {
            socket = IO.socket(url);
        } catch (URISyntaxException ignored) { }
        socket.on("siteReady", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                transmitMessage(new Message(1, getString(R.string.serverReady)));
            }
        });
        socket.on("openLink", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String url = (String)args[0];
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });
        socket.on("progressData", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Integer progress = 0;
                String songName;
                try {
                    progress = (Integer) args[0];
                }
                catch(NullPointerException ignored){}
                songName = (String)args[1];
                notificaDescarga(progress, songName);
            }
        });
        socket.on("downloadCancelled", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String songName = (String)args[0];
                acabaNotificacion(songName, songLastStatus.CANCELLED);
            }
        });
        socket.on("downloadAlreadyExists", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String songName = (String)args[0];
                acabaNotificacion(songName, songLastStatus.ALREADY_DOWNLOADED);
            }
        });
        socket.on("downloadReady", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String internalPath = (String)args[0];
                String songName = (String)args[1];
                File internalFile;
                internalFile = new File(internalPath);
                scanNewSongInternal(Uri.fromFile(internalFile));
                acabaNotificacion(songName, songLastStatus.READY);
            }
        });
        socket.connect();
    }

    /**
     * Rx2 components
     */
    public static Observer<Message> serviceDataHandler;

    void preparaHandler(){
        serviceDataHandler = new Observer<Message>() {
            @Override
            public void onSubscribe(Disposable d) { }
            @Override
            public void onNext(Message msg) {
                Log.d("asd", msg.getMessage());
                if(msg.getType()==3){
                    //this is the new downloads location
                    //socket.emit("newPath", msg.getMessage());
                }
            }
            @Override
            public void onError(Throwable e) { }
            @Override
            public void onComplete() { }
        };
    }

    void transmitMessage(Message msg){
        if(MainActivity.mainDataHandler!=null)
        Message.transmitMessage(msg)
                // Run on a background thread
                .subscribeOn(Schedulers.io())
                // Be notified on the main thread
                .observeOn(AndroidSchedulers.mainThread())
                // The method that handles the data
                .subscribe(MainActivity.mainDataHandler);
    }

    /**
     * File methods (and a class)
     */
    public void copyFile(String inputPath, String inputFile, String outputPath, DocumentFile pickedDir) {

        InputStream in = null;
        OutputStream out = null;
        try {
            //create output directory if it doesn't exist
            File dir = new File(outputPath);
            try {if (!dir.exists()) {
                dir.mkdirs();
            }} catch (Exception e) {
                Log.e("tag", e.getMessage());
                socket.emit("log", "Exception: " + e.getMessage());
            }

            in = new FileInputStream(inputPath + inputFile);
            //out = new FileOutputStream(outputPath + inputFile);

            DocumentFile file = null;
            if(outputPath.endsWith("mp3")) file = pickedDir.createFile("audio/mpeg3", outputPath);
            else file = pickedDir.createFile("audio/flac", outputPath);
            out = getContentResolver().openOutputStream(Objects.requireNonNull(file).getUri());
            try {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    Objects.requireNonNull(out).write(buffer, 0, read);
                }
            } catch (IOException e){
                socket.emit("log", "IOException: " + e.getMessage());
            }
            in.close();
            // write the output file (You have now copied the file)
            Objects.requireNonNull(out).flush();
            out.close();
        } catch (FileNotFoundException fnfe1) {
            /* I get the error here */
            Log.e("tag", fnfe1.getMessage());
            socket.emit("log", "FileNotFoundException: " + fnfe1.getMessage());
        } catch (Exception e) {
            Log.e("tag", e.getMessage());
            socket.emit("log", "Exception: " + e.getMessage());
        }
    }

    void scanNewSongInternal(Uri fileUri){
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(fileUri);
        sendBroadcast(intent);
    }

    void scanNewSongExternal(File externalFile){
        new MediaScannerWrapper(context, externalFile.toString()).scan();
    }

    public class MediaScannerWrapper implements MediaScannerConnection.MediaScannerConnectionClient {
        private final MediaScannerConnection mConnection;
        private final String mPath;
        private final String mMimeType;

        // filePath - where to scan;
        // mime type of media to scan i.e. "image/jpeg".
        // use "*/*" for any media
        public MediaScannerWrapper(Context _ctx, String _filePath){
            mPath = _filePath;
            mMimeType = "*/*";
            mConnection = new MediaScannerConnection(_ctx, this);
        }

        // do the scanning
        public void scan() {
            mConnection.connect();
        }
        // start the scan when scanner is ready
        public void onMediaScannerConnected() {
            mConnection.scanFile(mPath, mMimeType);
            Log.w("MediaScannerWrapper", "media file scanned: " + mPath);
        }
        public void onScanCompleted(String path, Uri uri) { }
    }
}
