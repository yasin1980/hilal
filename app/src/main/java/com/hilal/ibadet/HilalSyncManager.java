package com.hilal.ibadet;

import android.app.Activity;
import android.webkit.WebView;
import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tek native Firebase omurgasi: tum Hilal modulleri /shared altinda ayni motoru kullanir. */
public final class HilalSyncManager {
    private static final String DB_URL = "https://hilal-2b1a5-default-rtdb.europe-west1.firebasedatabase.app";
    private static final String ROOT = "shared";
    private final Activity activity;
    private final WebView webView;
    private final FirebaseDatabase db;
    private final DatabaseReference shared;
    private final Map<String, ValueEventListener> listeners = new ConcurrentHashMap<>();

    public HilalSyncManager(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        FirebaseApp app;
        if (FirebaseApp.getApps(activity).isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApplicationId("1:587953030833:android:hilal-native-sync")
                    .setApiKey("AIzaSyDsfRMY_i7caTyn6rRAqZBgJPfeKgFmKko")
                    .setProjectId("hilal-2b1a5")
                    .setDatabaseUrl(DB_URL)
                    .build();
            app = FirebaseApp.initializeApp(activity, options, "hilal-native");
        } else {
            app = FirebaseApp.getApps(activity).get(0);
        }
        db = FirebaseDatabase.getInstance(app, DB_URL);
        try { db.setPersistenceEnabled(true); } catch (Exception ignored) {}
        shared = db.getReference(ROOT);
        shared.keepSynced(true);
        watchConnection();
    }

    private String clean(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.isEmpty() || "undefined".equals(s) || "null".equals(s)) return "";
        return s.replace("/", "_").replace(".", "_").replace("#", "_").replace("$", "_").replace("[", "_").replace("]", "_");
    }

    private DatabaseReference ref(String channel, String id) {
        String raw = channel == null ? "" : channel.trim();
        if (raw.isEmpty() || "undefined".equals(raw) || "null".equals(raw)) throw new IllegalArgumentException("invalid channel");
        DatabaseReference r = shared;
        for (String part : raw.split("/")) {
            String c = clean(part);
            if (c.isEmpty()) throw new IllegalArgumentException("invalid channel");
            r = r.child(c);
        }
        String k = clean(id);
        return k.isEmpty() ? r : r.child(k);
    }

    public void subscribe(String channel) {
        final String c = clean(channel);
        if (c.isEmpty() || listeners.containsKey(c)) return;
        DatabaseReference r = ref(c, null);
        ValueEventListener l = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                emit("data", c, "", toJson(snapshot.getValue()));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                emit("error", c, "", JSONObject.quote(error.getMessage()));
            }
        };
        listeners.put(c, l);
        r.addValueEventListener(l);
        r.keepSynced(true);
    }

    public void write(String channel, String id, String json) {
        try {
            Object value = parseJson(json);
            ref(channel, id).setValue(value)
                    .addOnSuccessListener(v -> emit("write-ok", clean(channel), clean(id), "null"))
                    .addOnFailureListener(e -> emit("error", clean(channel), clean(id), JSONObject.quote(e.getMessage())));
        } catch (Exception e) {
            emit("error", clean(channel), clean(id), JSONObject.quote(e.getMessage()));
        }
    }

    public void patch(String channel, String id, String json) {
        try {
            JSONObject o = new JSONObject(json == null ? "{}" : json);
            Map<String,Object> m = jsonObjectToMap(o);
            ref(channel, id).updateChildren(m)
                    .addOnSuccessListener(v -> emit("write-ok", clean(channel), clean(id), "null"))
                    .addOnFailureListener(e -> emit("error", clean(channel), clean(id), JSONObject.quote(e.getMessage())));
        } catch (Exception e) {
            emit("error", clean(channel), clean(id), JSONObject.quote(e.getMessage()));
        }
    }

    public void remove(String channel, String id) {
        try {
            ref(channel, id).removeValue()
                    .addOnFailureListener(e -> emit("error", clean(channel), clean(id), JSONObject.quote(e.getMessage())));
        } catch (Exception e) { emit("error", clean(channel), clean(id), JSONObject.quote(e.getMessage())); }
    }

    private void watchConnection() {
        db.getReference(".info/connected").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean b = snapshot.getValue(Boolean.class);
                emit("connection", "system", "", b != null && b ? "true" : "false");
                if (Boolean.TRUE.equals(b)) shared.child("_presence").child("lastConnectedAt").setValue(ServerValue.TIMESTAMP);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void emit(String type, String channel, String id, String jsonLiteral) {
        final String js = "window.HilalNativeSync&&window.HilalNativeSync._receive(" +
                JSONObject.quote(type) + "," + JSONObject.quote(channel) + "," + JSONObject.quote(id) + "," +
                (jsonLiteral == null ? "null" : jsonLiteral) + ");";
        activity.runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(js, null); });
    }

    private static Object parseJson(String s) throws Exception {
        if (s == null || s.trim().isEmpty() || "null".equals(s.trim())) return null;
        String t=s.trim();
        if (t.startsWith("{")) return jsonObjectToMap(new JSONObject(t));
        if (t.startsWith("[")) return jsonArrayToList(new JSONArray(t));
        return t;
    }
    private static Map<String,Object> jsonObjectToMap(JSONObject o) throws Exception {
        Map<String,Object> m=new HashMap<>();
        java.util.Iterator<String> it=o.keys();
        while(it.hasNext()){ String k=it.next(); m.put(k, convert(o.get(k))); }
        return m;
    }
    private static java.util.List<Object> jsonArrayToList(JSONArray a) throws Exception {
        java.util.List<Object> l=new java.util.ArrayList<>();
        for(int i=0;i<a.length();i++) l.add(convert(a.get(i)));
        return l;
    }
    private static Object convert(Object v) throws Exception {
        if(v==JSONObject.NULL)return null;
        if(v instanceof JSONObject)return jsonObjectToMap((JSONObject)v);
        if(v instanceof JSONArray)return jsonArrayToList((JSONArray)v);
        return v;
    }
    private static String toJson(Object v) {
        if(v==null)return "null";
        if(v instanceof Map)return new JSONObject((Map<?,?>)v).toString();
        if(v instanceof java.util.Collection)return new JSONArray((java.util.Collection<?>)v).toString();
        if(v instanceof Number || v instanceof Boolean)return String.valueOf(v);
        return JSONObject.quote(String.valueOf(v));
    }
}
