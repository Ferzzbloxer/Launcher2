/*
 * Persists the user's custom app-drawer tabs (name only, for now - which apps belong to
 * each tab is added in a later slice). Backed by SharedPreferences, tab names stored as a
 * JSON array to preserve order without needing a database table for something this small.
 */

package com.android.launcher2;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class CustomTabsStore {

    private static final String PREFS_NAME = "custom_tabs";
    private static final String KEY_TAB_NAMES = "tab_names";

    // Total tabs including the built-in Apps/Widgets tabs. "Up to 10 tabs" -> 2 built-in +
    // up to 8 custom ones. Easy to change if the intent was 10 additional custom tabs instead.
    public static final int MAX_TOTAL_TABS = 10;
    public static final int MAX_CUSTOM_TABS = MAX_TOTAL_TABS - 2;

    public enum AddTabResult {
        SUCCESS,
        ALREADY_EXISTS,
        LIMIT_REACHED,
        EMPTY_NAME
    }

    private final SharedPreferences mPrefs;

    public CustomTabsStore(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<String> getTabNames() {
        List<String> names = new ArrayList<String>();
        String raw = mPrefs.getString(KEY_TAB_NAMES, null);
        if (raw == null) {
            return names;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                names.add(arr.getString(i));
            }
        } catch (JSONException e) {
            // Corrupt/unexpected data - treat as empty rather than crash.
        }
        return names;
    }

    public AddTabResult addTabName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return AddTabResult.EMPTY_NAME;
        }
        name = name.trim();

        List<String> names = getTabNames();
        for (String existing : names) {
            if (existing.equalsIgnoreCase(name)) {
                return AddTabResult.ALREADY_EXISTS;
            }
        }
        if (names.size() >= MAX_CUSTOM_TABS) {
            return AddTabResult.LIMIT_REACHED;
        }

        names.add(name);
        saveTabNames(names);
        return AddTabResult.SUCCESS;
    }

    private void saveTabNames(List<String> names) {
        JSONArray arr = new JSONArray();
        for (String name : names) {
            arr.put(name);
        }
        mPrefs.edit().putString(KEY_TAB_NAMES, arr.toString()).apply();
    }
}
