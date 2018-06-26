package com.dt3264.deezloader;

import android.app.Application;

import com.singhajit.sherlock.core.Sherlock;

public class AppClass extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Sherlock.init(this); //Initializing Sherlock
    }
}
