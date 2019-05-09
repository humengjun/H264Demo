package co.jp.snjp.x264demo.app;

import android.app.Application;

public class H264Application extends Application {
    private static Application application;

    public static Application getInstance() {
        return application;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;
    }
}
