package com.dt3264.deezloader;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebView;

import java.util.Objects;

public class BrowserActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        Objects.requireNonNull(getSupportActionBar()).hide();
        vcm.github.webkit.proview.ProWebView webView = findViewById(R.id.webView);
        webView.clearCache(true);
        webView.clearCookies();
        webView.clearHistory();
        webView.clearDatabase();
        webView.clearStorage();
        webView.loadUrl("http://localhost:1730");
    }
}
