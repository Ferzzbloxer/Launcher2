/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher2;

import android.app.Application;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.android.launcher2.R;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LauncherApplication extends Application {
    static final String TAG = "LauncherApplication";
    // TEMP DEBUG LOGGING: crash details get appended here so they can be pulled
    // with `adb shell run-as <pkg> cat files/crash_log.txt` (or `adb pull` on a
    // debuggable/rooted build). Remove this whole block once the crash is fixed.
    private static final String CRASH_LOG_FILE_NAME = "crash_log.txt";
    private LauncherModel mModel;
    private IconCache mIconCache;
    private WidgetPreviewLoader.CacheDb mWidgetPreviewCacheDb;
    private static boolean sIsScreenLarge;
    private static float sScreenDensity;
    private static int sLongPressTimeout = 300;
    private static final String sSharedPreferencesKey = "com.android.launcher2.prefs";
    WeakReference<LauncherProvider> mLauncherProvider;

    @Override
    public void onCreate() {
        // TEMP DEBUG LOGGING: install this first, before anything else can throw.
        installCrashLogger();

        try {
            super.onCreate();

            // set sIsScreenXLarge and sScreenDensity *before* creating icon cache
            sIsScreenLarge = getResources().getBoolean(R.bool.is_large_screen);
            sScreenDensity = getResources().getDisplayMetrics().density;

            recreateWidgetPreviewDb();
            mIconCache = new IconCache(this);
            mModel = new LauncherModel(this, mIconCache);
            LauncherApps launcherApps = (LauncherApps)
                    getSystemService(Context.LAUNCHER_APPS_SERVICE);
            launcherApps.registerCallback(mModel.getLauncherAppsCallback());

            // Register intent receivers
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            registerReceiver(mModel, filter);
            filter = new IntentFilter();
            filter.addAction(SearchManager.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED);
            registerReceiver(mModel, filter);
            filter = new IntentFilter();
            filter.addAction(SearchManager.INTENT_ACTION_SEARCHABLES_CHANGED);
            registerReceiver(mModel, filter);

            // Register for changes to the favorites
            ContentResolver resolver = getContentResolver();
            resolver.registerContentObserver(LauncherSettings.Favorites.CONTENT_URI, true,
                    mFavoritesObserver);
        } catch (Throwable t) {
            // TEMP DEBUG LOGGING: Application.onCreate() itself is a common place
            // for an instant-crash to originate, so log it explicitly here too,
            // in addition to the global handler below.
            logCrashToFile("Application.onCreate", t);
            throw t;
        }
    }

    /**
     * TEMP DEBUG LOGGING: writes every uncaught exception (on any thread) to
     * files/crash_log.txt in the app's private data dir, then hands off to
     * whatever default handler was already installed (system default, or
     * Play/vendor crash reporter) so normal crash behavior is unaffected.
     * Remove once the instant-crash is diagnosed and fixed.
     */
    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                logCrashToFile("thread=" + thread.getName(), ex);
                if (previous != null) {
                    previous.uncaughtException(thread, ex);
                }
            }
        });
    }

    private void logCrashToFile(String context, Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);

            String timestamp = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            String entry = "----- " + timestamp + " (" + context + ") -----\n"
                    + "Android SDK " + Build.VERSION.SDK_INT
                    + " / " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                    + sw.toString() + "\n";

            // files/ is the app's private data dir (getFilesDir()), always
            // writable without extra permissions on any Android version.
            File outFile = new File(getFilesDir(), CRASH_LOG_FILE_NAME);
            FileWriter fw = new FileWriter(outFile, /* append */ true);
            fw.write(entry);
            fw.flush();
            fw.close();

            Log.e(TAG, "Crash logged to " + outFile.getAbsolutePath(), t);
        } catch (Throwable loggingFailure) {
            // Never let the logger itself cause a secondary crash or mask
            // the original exception.
            Log.e(TAG, "Failed to write crash log", loggingFailure);
        }
    }

    public void recreateWidgetPreviewDb() {
        mWidgetPreviewCacheDb = new WidgetPreviewLoader.CacheDb(this);
    }

    /**
     * There's no guarantee that this function is ever called.
     */
    @Override
    public void onTerminate() {
        super.onTerminate();

        unregisterReceiver(mModel);

        ContentResolver resolver = getContentResolver();
        resolver.unregisterContentObserver(mFavoritesObserver);
    }

    /**
     * Receives notifications whenever the user favorites have changed.
     */
    private final ContentObserver mFavoritesObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            // If the database has ever changed, then we really need to force a reload of the
            // workspace on the next load
            mModel.resetLoadedState(false, true);
            mModel.startLoaderFromBackground();
        }
    };

    LauncherModel setLauncher(Launcher launcher) {
        mModel.initialize(launcher);
        return mModel;
    }

    IconCache getIconCache() {
        return mIconCache;
    }

    LauncherModel getModel() {
        return mModel;
    }

    WidgetPreviewLoader.CacheDb getWidgetPreviewCacheDb() {
        return mWidgetPreviewCacheDb;
    }

    void setLauncherProvider(LauncherProvider provider) {
        mLauncherProvider = new WeakReference<LauncherProvider>(provider);
    }

    LauncherProvider getLauncherProvider() {
        return mLauncherProvider.get();
    }

    public static String getSharedPreferencesKey() {
        return sSharedPreferencesKey;
    }

    public static boolean isScreenLarge() {
        return sIsScreenLarge;
    }

    public static boolean isScreenLandscape(Context context) {
        return context.getResources().getConfiguration().orientation ==
            Configuration.ORIENTATION_LANDSCAPE;
    }

    public static float getScreenDensity() {
        return sScreenDensity;
    }

    public static int getLongPressTimeout() {
        return sLongPressTimeout;
    }
}
