/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.launcher2.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WallpaperChooserDialogFragment extends DialogFragment {

    private static final String TAG = "Launcher.WallpaperChooserDialogFragment";
    private static final String EMBEDDED_KEY = "com.android.launcher2."
            + "WallpaperChooserDialogFragment.EMBEDDED_KEY";

    private boolean mEmbedded;

    private ArrayList<Integer> mThumbs;
    private ArrayList<Integer> mImages;
    private WallpaperLoader mLoader;
    private WallpaperDrawable mWallpaperDrawable = new WallpaperDrawable();
    private int mSelectedPosition = 0;
    private static final int REQUEST_PICK_PHOTO = 1;

    public static WallpaperChooserDialogFragment newInstance() {
        WallpaperChooserDialogFragment fragment = new WallpaperChooserDialogFragment();
        fragment.setCancelable(true);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(EMBEDDED_KEY)) {
            mEmbedded = savedInstanceState.getBoolean(EMBEDDED_KEY);
        } else {
            mEmbedded = isInLayout();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(EMBEDDED_KEY, mEmbedded);
    }

    private void cancelLoader() {
        if (mLoader != null && mLoader.getStatus() != WallpaperLoader.Status.FINISHED) {
            mLoader.cancel(true);
            mLoader = null;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        cancelLoader();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        cancelLoader();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        /* On orientation changes, the dialog is effectively "dismissed" so this is called
         * when the activity is no longer associated with this dying dialog fragment. We
         * should just safely ignore this case by checking if getActivity() returns null
         */
        Activity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    /* This will only be called when in XLarge mode, since this Fragment is invoked like
     * a dialog in that mode
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        findWallpapers();

        return null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        findWallpapers();

        /* If this fragment is embedded in the layout of this activity, then we should
         * generate a view to display. Otherwise, a dialog will be created in
         * onCreateDialog()
         */
        if (mEmbedded) {
            View view = inflater.inflate(R.layout.wallpaper_chooser, container, false);
            view.setBackground(mWallpaperDrawable);

            final LinearLayout gallery = (LinearLayout) view.findViewById(R.id.gallery);
            populateGallery(gallery);

            View setButton = view.findViewById(R.id.set);
            setButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectWallpaper(mSelectedPosition);
                }
            });
            return view;
        }
        return null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_PHOTO && resultCode == Activity.RESULT_OK
                && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                setWallpaperFromUri(imageUri);
            }
        }
    }

    private void setWallpaperFromUri(Uri imageUri) {
        WallpaperManager wpm = (WallpaperManager) getActivity().getSystemService(
                Context.WALLPAPER_SERVICE);
        try {
            startActivity(wpm.getCropAndSetWallpaperIntent(imageUri));
            return;
        } catch (Exception e) {
            Log.w(TAG, "System crop-and-set unavailable, falling back to direct set", e);
        }

        java.io.InputStream in = null;
        try {
            in = getActivity().getContentResolver().openInputStream(imageUri);
            wpm.setStream(in);
            Activity activity = getActivity();
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set wallpaper from picked photo", e);
            android.widget.Toast.makeText(getActivity(),
                    "Couldn't set wallpaper from that photo",
                    android.widget.Toast.LENGTH_SHORT).show();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void populateGallery(LinearLayout gallery) {
        LayoutInflater inflater = getActivity().getLayoutInflater();

        addPhotoPickerItem(gallery, inflater);
        addLiveWallpaperItems(gallery, inflater);

        for (int i = 0; i < mThumbs.size(); i++) {
            final int position = i;
            View itemView = inflater.inflate(R.layout.wallpaper_item, gallery, false);
            ImageView image = (ImageView) itemView.findViewById(R.id.wallpaper_image);

            int thumbRes = mThumbs.get(position);
            image.setImageResource(thumbRes);
            Drawable thumbDrawable = image.getDrawable();
            if (thumbDrawable != null) {
                thumbDrawable.setDither(true);
            } else {
                Log.e(TAG, "Error decoding thumbnail resId=" + thumbRes + " for wallpaper #"
                        + position);
            }

            itemView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectItem(position);
                }
            });

            gallery.addView(itemView);
        }
        if (mThumbs.size() > 0) {
            selectItem(0);
        }
    }

    private void addPhotoPickerItem(LinearLayout gallery, LayoutInflater inflater) {
        View itemView = inflater.inflate(R.layout.wallpaper_item, gallery, false);
        ImageView image = (ImageView) itemView.findViewById(R.id.wallpaper_image);
        image.setImageResource(android.R.drawable.ic_menu_gallery);
        itemView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_PICK_PHOTO);
            }
        });
        gallery.addView(itemView);
    }

    private void addLiveWallpaperItems(LinearLayout gallery, LayoutInflater inflater) {
        PackageManager pm = getActivity().getPackageManager();
        List<ResolveInfo> services = pm.queryIntentServices(
                new Intent(WallpaperService.SERVICE_INTERFACE), PackageManager.GET_META_DATA);

        for (ResolveInfo resolveInfo : services) {
            final WallpaperInfo info;
            try {
                info = new WallpaperInfo(getActivity(), resolveInfo);
            } catch (Exception e) {
                Log.w(TAG, "Skipping broken live wallpaper " + resolveInfo.serviceInfo, e);
                continue;
            }

            View itemView = inflater.inflate(R.layout.wallpaper_item, gallery, false);
            ImageView image = (ImageView) itemView.findViewById(R.id.wallpaper_image);
            Drawable preview = info.loadThumbnail(pm);
            if (preview == null) {
                preview = info.loadIcon(pm);
            }
            image.setImageDrawable(preview);

            itemView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
                    intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            info.getComponent());
                    startActivity(intent);
                }
            });

            gallery.addView(itemView);
        }
    }

    private void selectItem(int position) {
        mSelectedPosition = position;
        if (mLoader != null && mLoader.getStatus() != WallpaperLoader.Status.FINISHED) {
            mLoader.cancel();
        }
        mLoader = (WallpaperLoader) new WallpaperLoader().execute(position);
    }

    private void selectWallpaper(int position) {
        try {
            WallpaperManager wpm = (WallpaperManager) getActivity().getSystemService(
                    Context.WALLPAPER_SERVICE);
            wpm.setResource(mImages.get(position));
            Activity activity = getActivity();
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
        } catch (IOException e) {
            Log.e(TAG, "Failed to set wallpaper: " + e);
        }
    }

    private void findWallpapers() {
        mThumbs = new ArrayList<Integer>(24);
        mImages = new ArrayList<Integer>(24);

        final Resources resources = getResources();
        // Context.getPackageName() may return the "original" package name,
        // com.android.launcher2; Resources needs the real package name,
        // com.android.launcher. So we ask Resources for what it thinks the
        // package name should be.
        final String packageName = resources.getResourcePackageName(R.array.wallpapers);

        addWallpapers(resources, packageName, R.array.wallpapers);
        addWallpapers(resources, packageName, R.array.extra_wallpapers);
    }

    private void addWallpapers(Resources resources, String packageName, int list) {
        final String[] extras = resources.getStringArray(list);
        for (String extra : extras) {
            int res = resources.getIdentifier(extra, "drawable", packageName);
            if (res != 0) {
                int thumbRes = resources.getIdentifier(extra + "_small",
                        "drawable", packageName);

                //Log.d(TAG, "add: [" + packageName + "]: " + extra + " (res=" + res + " thumb=" + thumbRes + ")");
                if (thumbRes == 0) {
                    Log.w(TAG, "warning: built-in wallpaper " + extra
                            + " without " + extra + "_thumb");
                    thumbRes = R.mipmap.ic_launcher_wallpaper;
                }
                mThumbs.add(thumbRes);
                mImages.add(res);
            }
        }
    }

    class WallpaperLoader extends AsyncTask<Integer, Void, Bitmap> {
        WallpaperLoader() {
        }

        @Override
        protected Bitmap doInBackground(Integer... params) {
            if (isCancelled()) return null;
            try {
                final Drawable d = getResources().getDrawable(mImages.get(params[0]));
                if (d instanceof BitmapDrawable) {
                    return ((BitmapDrawable)d).getBitmap();
                }
                return null;
            } catch (OutOfMemoryError e) {
                Log.w(TAG, String.format(
                        "Out of memory trying to load wallpaper res=%08x", params[0]),
                        e);
                return null;
            } catch (Resources.NotFoundException e) {
                // Some OEM builds don't ship (or have renamed) the private framework
                // default_wallpaper resource that default_wallpaper.xml points at via
                // "@*android:drawable/default_wallpaper". Skip this entry instead of
                // crashing the whole app - the thumbnail just won't load.
                Log.w(TAG, String.format(
                        "Wallpaper resource not found res=%08x", params[0]), e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap b) {
            if (b == null) return;

            if (!isCancelled()) {
                View v = getView();
                if (v != null) {
                    mWallpaperDrawable.setBitmap(b);
                    v.postInvalidate();
                } else {
                    mWallpaperDrawable.setBitmap(null);
                }
                mLoader = null;
            } else {
               b.recycle();
            }
        }

        void cancel() {
            super.cancel(true);
        }
    }

    /**
     * Custom drawable that centers the bitmap fed to it.
     */
    static class WallpaperDrawable extends Drawable {

        Bitmap mBitmap;
        int mIntrinsicWidth;
        int mIntrinsicHeight;
        Matrix mMatrix;

        /* package */void setBitmap(Bitmap bitmap) {
            mBitmap = bitmap;
            if (mBitmap == null)
                return;
            mIntrinsicWidth = mBitmap.getWidth();
            mIntrinsicHeight = mBitmap.getHeight();
            mMatrix = null;
        }

        @Override
        public void draw(Canvas canvas) {
            if (mBitmap == null) return;
 
            if (mMatrix == null) {
                final int vwidth = canvas.getWidth();
                final int vheight = canvas.getHeight();
                final int dwidth = mIntrinsicWidth;
                final int dheight = mIntrinsicHeight;

                float scale = 1.0f;

                if (dwidth < vwidth || dheight < vheight) {
                    scale = Math.max((float) vwidth / (float) dwidth,
                            (float) vheight / (float) dheight);
                }

                float dx = (vwidth - dwidth * scale) * 0.5f + 0.5f;
                float dy = (vheight - dheight * scale) * 0.5f + 0.5f;

                mMatrix = new Matrix();
                mMatrix.setScale(scale, scale);
                mMatrix.postTranslate((int) dx, (int) dy);
            }

            canvas.drawBitmap(mBitmap, mMatrix, null);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }

        @Override
        public void setAlpha(int alpha) {
            // Ignore
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            // Ignore
        }
    }
}
