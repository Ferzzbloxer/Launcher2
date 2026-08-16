/*
 * Bundled default icon pack mapping.
 *
 * Generated from a third-party icon pack's assets/appfilter.xml. Maps a
 * flattened ComponentName string ("packageName/className", matching
 * ComponentName.flattenToString()) to one of the iconpack_icon_XXXXX
 * drawables bundled in res/drawable-nodpi/. Only apps explicitly listed
 * here get a replacement icon; everything else falls back to its own
 * app-provided icon as normal.
 */

package com.android.launcher2;

import java.util.HashMap;

public class IconPackMap {

    public static final HashMap<String, Integer> MAP = new HashMap<String, Integer>();

    static {
        MAP.put("com.samsung.android.calendar/com.samsung.android.app.calendar.activity.MainActivity",
                R.drawable.iconpack_icon_10000);
        MAP.put("com.google.earth/com.google.android.apps.earth.flutter.EarthFlutterActivity",
                R.drawable.iconpack_icon_10018);
        MAP.put("com.sec.android.app.popupcalculator/com.sec.android.app.popupcalculator.Calculator",
                R.drawable.iconpack_icon_10017);
        MAP.put("com.sec.android.app.clockpackage/com.sec.android.app.clockpackage.ClockPackage",
                R.drawable.iconpack_icon_10005);
        MAP.put("com.samsung.android.app.contacts/com.samsung.android.contacts.contactslist.PeopleActivity",
                R.drawable.iconpack_icon_10006);
        MAP.put("com.google.android.apps.messaging/com.google.android.apps.messaging.ui.ConversationListActivity",
                R.drawable.iconpack_icon_10003);
        MAP.put("com.samsung.android.messaging/com.android.mms.ui.ConversationComposer",
                R.drawable.iconpack_icon_10003);
        MAP.put("com.samsung.android.dialer/com.samsung.android.dialer.DialtactsActivity",
                R.drawable.iconpack_icon_10004);
        MAP.put("com.chrome.beta/com.google.android.apps.chrome.Main",
                R.drawable.iconpack_icon_10015);
        MAP.put("com.marc.files/nl.marc_apps.files.MainActivity",
                R.drawable.iconpack_icon_10008);
        MAP.put("com.google.android.apps.docs/com.google.android.apps.docs.app.NewMainProxyActivity",
                R.drawable.iconpack_icon_10020);
        MAP.put("com.google.android.gm/com.google.android.gm.ConversationListActivityGmail",
                R.drawable.iconpack_icon_10009);
        MAP.put("com.google.android.googlequicksearchbox/com.google.android.googlequicksearchbox.SearchActivity",
                R.drawable.iconpack_icon_10010);
        MAP.put("com.google.android.apps.maps/com.google.android.maps.MapsActivity",
                R.drawable.iconpack_icon_10021);
        MAP.put("com.google.android.apps.photos/com.google.android.apps.photos.home.HomeActivity",
                R.drawable.iconpack_icon_10019);
        MAP.put("com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell$HomeActivity",
                R.drawable.iconpack_icon_10011);
        MAP.put("com.sec.android.app.camera/com.sec.android.app.camera.Camera",
                R.drawable.iconpack_icon_10013);
        MAP.put("com.android.settings/com.android.settings.Settings",
                R.drawable.iconpack_icon_10014);
        MAP.put("com.outfit7.mytalkingangelafree/com.outfit7.felis.SplashActivity",
                R.drawable.iconpack_icon_10023);
        MAP.put("com.outfit7.mytalkingtomfree/com.outfit7.felis.SplashActivity",
                R.drawable.iconpack_icon_10022);
        MAP.put("com.android.vending/com.android.vending.AssetBrowserActivity",
                R.drawable.iconpack_icon_10016);
    }

    private IconPackMap() {
    }
}
