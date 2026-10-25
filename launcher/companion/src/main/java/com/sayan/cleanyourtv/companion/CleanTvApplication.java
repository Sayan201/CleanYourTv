package com.sayan.cleanyourtv.companion;
import android.app.Application;
import com.wirelessdebug.WdbContext;
public final class CleanTvApplication extends Application {
 @Override public void onCreate(){super.onCreate();WdbContext.init(this);}
}