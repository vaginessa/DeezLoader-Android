package com.dt3264.deezloader;

import android.Manifest;

import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.*;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.plugins.RxAndroidPlugins;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import io.socket.client.Socket;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("node");
    }

    /**
     * actualCompileNumber saves in the users phone
     * their last update and if it was a previous version, it updates the files
     * easy peasy ggg
     */
    final int actualCompileNumber = 212;
    /**
     * reloadNodeAppData when true, updates the node folder in the phone on each start
     * */
    final boolean reloadNodeAppData = true;
    final String actualVersion = "2.1.2";
    final String url = "http://localhost:1730";
    final String telegramUrl = "https://t.me/deezloaderandroidportal";
    int lastCompile;
    SharedPreferences sharedPreferences;

    /**
     * UI Elements
     */
    Context context;
    Snackbar snackbar;
    @BindView(R.id.openAppExternalButton)
    Button mainExternalButton;
    @BindView(R.id.openAppInternalButton)
    Button mainInternalButton;
    @BindView(R.id.telegramButton)
    Button telegramButton;
    @BindView(R.id.updateButton)
    Button updateButton;
    @BindView(R.id.infoView)
    TextView infoView;
    @BindView(R.id.updateTxt)
    TextView updateTxt;
    @BindView(R.id.faq)
    TextView faqTxt;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        Objects.requireNonNull(getSupportActionBar()).hide();
        preparaHandler();
        infoView.setText("Actual version: " + actualVersion + '\n' + getText(R.string.serverLoadingText)  + '\n');
        telegramButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(telegramUrl));
                startActivity(i);
            }
        });
        mainExternalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });
        mainInternalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(getBaseContext(), BrowserActivity.class);
                startActivity(i);
            }
        });
        snackbar = Snackbar.make(infoView, "Preparing server data", Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        run();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        lastCompile = sharedPreferences.getInt("lastCompile",0);
        if (savedInstanceState == null) compruebaPermisos();
        else muestraPagina();
    }

    /**
     * Main methods
     */

    void compruebaPermisos(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        } else {
            compruebaServidor();
        }
    }

    void compruebaServidor() {
        if (!MyService.isServiceRunning) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    checkIsApkUpdated();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            snackbar.setText("Starting server");
                        }
                    });
                    Intent startIntent = new Intent(getApplicationContext(), MyService.class);
                    startIntent.setAction(getString(R.string.serviceName));
                    startService(startIntent);
                }
            }).start();
        } else {
            muestraPagina();
        }
    }

    void muestraPagina() {
        context = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Button telegramButton = findViewById(R.id.telegramButton);
                Button externalButton = findViewById(R.id.openAppExternalButton);
                Button internalButton = findViewById(R.id.openAppInternalButton);
                TextView faqTxt = findViewById(R.id.faq);
                telegramButton.setVisibility(View.VISIBLE);
                externalButton.setVisibility(View.VISIBLE);
                internalButton.setVisibility(View.VISIBLE);
                faqTxt.setVisibility(View.VISIBLE);
                snackbar.dismiss();
                infoView.setText("Actual version: " + actualVersion + '\n' + getText(R.string.serverReady) + '\n');
            }
        });
    }

    String[] pasteResult;
    OkHttpClient client = new OkHttpClient();
    void run() {
        Request request = new Request.Builder().url("https://pastebin.com/raw/rEubX2Lu").build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, IOException e) { }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                pasteResult = Objects.requireNonNull(response.body()).string().split("\n");
                if(!actualVersion.equals(pasteResult[0].trim())) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateTxt.setText("A new update (" + pasteResult[0].trim() + ") is available.\nChanges: \n" + pasteResult[2]);
                            updateButton.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    Intent i = new Intent(Intent.ACTION_VIEW);
                                    i.setData(Uri.parse(pasteResult[1]));
                                    startActivity(i);
                                }
                            });
                            updateTxt.setVisibility(View.VISIBLE);
                            updateButton.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        });
    }

    /**
     * Rx2 Components
     */
    //Observer which recieves and handles the data
    public static Observer<Message> mainDataHandler;
    private Observer<Message> _mainDataHandler = new Observer<Message>() {
        @Override
        public void onSubscribe(Disposable d) { }
        @Override
        public void onNext(Message msg) {
            if(msg.getType()==1 && !MyService.pageAlreadyOpen) {
                muestraPagina();
                MyService.pageAlreadyOpen = true;
                Toast.makeText(getBaseContext(), "Server ready", Toast.LENGTH_SHORT).show();
            }
            else if(msg.getType()==2 && MyService.hasRequestedNewPath){
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1234);
                MyService.hasRequestedNewPath = false;
            }
            else if(msg.getType()==4){
                finishAndRemoveTask();
            }
        }
        @Override
        public void onError(Throwable e) { }
        @Override
        public void onComplete() { }
    };

    void preparaHandler(){
        mainDataHandler = _mainDataHandler;
    }

    //And the method which transmits the data to the Service
    void transmitMessage(Message msg){
        Message.transmitMessage(msg)
                // Run on a background thread
                .subscribeOn(Schedulers.io())
                // Be notified on the main thread
                .observeOn(AndroidSchedulers.mainThread())
                // The method that handles the data
                .subscribe(MyService.serviceDataHandler);
    }

    boolean doubleBackToExitPressedOnce = false;

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }
        this.doubleBackToExitPressedOnce = true;
        String message = "Click BACK again to exit the app (all remaining downloads will be removed) or Home to exit without close";
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                doubleBackToExitPressedOnce=false;
            }
        }, 2000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 100: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    compruebaServidor();

                } else {
                    // permission denied, boo!
                    Toast.makeText(this, "You should give the permission to use the app", Toast.LENGTH_SHORT).show();
                    this.finish();
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    @SuppressWarnings("JniMissingFunction")
    public static native Integer startNodeWithArguments(String[] arguments);

    /**
     * Check server files methods
     */
    private void checkIsApkUpdated(){
        if (reloadNodeAppData || lastCompile != actualCompileNumber) {
            //Recursively delete any existing nodejs-project.
            String nodeDir = getApplicationContext().getFilesDir().getAbsolutePath() + "/deezerLoader";
            File nodeDirReference = new File(nodeDir);
            if (nodeDirReference.exists()) {
                deleteFolderRecursively(new File(nodeDir));
            }
            //Copy the node project from assets into the application's data path.
            copyAssetFolder(getApplicationContext().getAssets(), "deezerLoader", nodeDir);
            saveLastUpdateTime();
        }
    }

    private void saveLastUpdateTime() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("lastCompile", actualCompileNumber);
        editor.apply();
    }

    private static boolean deleteFolderRecursively(File file) {
        try {
            boolean res = true;
            for (File childFile : file.listFiles()) {
                if (childFile.isDirectory()) {
                    res &= deleteFolderRecursively(childFile);
                } else {
                    res &= childFile.delete();
                }
            }
            res &= file.delete();
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            boolean res = true;

            if (Objects.requireNonNull(files).length == 0) {
                //If it's a file, it won't have any assets "inside" it.
                res = copyAsset(assetManager, fromAssetPath, toPath);
            } else {
                new File(toPath).mkdirs();
                for (String file : files)
                    res &= copyAssetFolder(assetManager, fromAssetPath + "/" + file, toPath + "/" + file);
            }
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(fromAssetPath);
            new File(toPath).createNewFile();
            out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
            return true;
        } catch (Exception e) {
            e.printStackTrace();

            return false;
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}