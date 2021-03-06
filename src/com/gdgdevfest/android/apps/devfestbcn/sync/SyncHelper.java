/*
 * Copyright 2012 Google Inc.
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

package com.gdgdevfest.android.apps.devfestbcn.sync;

import static com.gdgdevfest.android.apps.devfestbcn.util.LogUtils.LOGD;
import static com.gdgdevfest.android.apps.devfestbcn.util.LogUtils.LOGI;
import static com.gdgdevfest.android.apps.devfestbcn.util.LogUtils.makeLogTag;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;

import com.gdgdevfest.android.apps.devfestbcn.Config;
import com.gdgdevfest.android.apps.devfestbcn.R;
import com.gdgdevfest.android.apps.devfestbcn.io.BlocksHandler;
import com.gdgdevfest.android.apps.devfestbcn.io.JSONHandler;
import com.gdgdevfest.android.apps.devfestbcn.io.MapPropertyHandler;
import com.gdgdevfest.android.apps.devfestbcn.io.RoomsHandler;
import com.gdgdevfest.android.apps.devfestbcn.io.SearchSuggestHandler;
import com.gdgdevfest.android.apps.devfestbcn.io.SessionsHandler;
import com.gdgdevfest.android.apps.devfestbcn.io.SpeakersHandler;
import com.gdgdevfest.android.apps.devfestbcn.io.TracksHandler;
import com.gdgdevfest.android.apps.devfestbcn.io.map.model.Tile;
import com.gdgdevfest.android.apps.devfestbcn.provider.ScheduleContract;
import com.gdgdevfest.android.apps.devfestbcn.util.AccountUtils;
import com.gdgdevfest.android.apps.devfestbcn.util.Lists;
import com.gdgdevfest.android.apps.devfestbcn.util.NetUtils;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.CommonGoogleClientRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.googledevelopers.Googledevelopers;
import com.larvalabs.svgandroid.SVGParseException;
import com.turbomanage.httpclient.BasicHttpClient;
import com.turbomanage.httpclient.ConsoleRequestLogger;
import com.turbomanage.httpclient.HttpResponse;
import com.turbomanage.httpclient.RequestLogger;

/**
 * A helper class for dealing with sync and other remote persistence operations.
 * All operations occur on the thread they're called from, so it's best to wrap
 * calls in an {@link android.os.AsyncTask}, or better yet, a
 * {@link android.app.Service}.
 */
public class SyncHelper {
    private static final String TAG = makeLogTag(SyncHelper.class);

    public static final int FLAG_SYNC_LOCAL = 0x1;

    private static final int LOCAL_VERSION_CURRENT = 46;
    private static final String LOCAL_MAPVERSION_CURRENT = "\"vlh7Ig\"";

    private Context mContext;

    public SyncHelper(Context context) {
        mContext = context;
    }

    public static void requestManualSync(Account mChosenAccount) {
        Bundle b = new Bundle();
        b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        ContentResolver.requestSync(
                mChosenAccount,
                ScheduleContract.CONTENT_AUTHORITY, b);
    }

    /**
     * Loads conference information (sessions, rooms, tracks, speakers, etc.)
     * from a local static cache data and then syncs down data from the
     * Conference API.
     *
     * @param syncResult Optional {@link SyncResult} object to populate.
     * @throws IOException
     */
    public void performSync(SyncResult syncResult, int flags) throws IOException {

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        final int localVersion = prefs.getInt("local_data_version", 0);
        // Bulk of sync work, performed by executing several fetches from
        // local and online sources.
        final ContentResolver resolver = mContext.getContentResolver();
        ArrayList<ContentProviderOperation> batch = new ArrayList<ContentProviderOperation>();

        LOGI(TAG, "Performing sync");

            final long startLocal = System.currentTimeMillis();
            final boolean localParse = localVersion < LOCAL_VERSION_CURRENT;
            LOGD(TAG, "found localVersion=" + localVersion + " and LOCAL_VERSION_CURRENT="
                    + LOCAL_VERSION_CURRENT);
            // Only run local sync if there's a newer version of data available
            // than what was last locally-sync'd.
            if (localParse) {
            	int json_rooms = R.raw.rooms_es;
            	int json_common_slots = R.raw.common_slots_es;
            	int json_tracks = R.raw.tracks_es;
            	int json_speakers = R.raw.speakers_es;
            	int json_sessions = R.raw.sessions_es;
            	int json_session_tracks = R.raw.session_tracks_es;
            	int json_search_suggest = R.raw.search_suggest_es;
            	int json_map = R.raw.map_es;
            	try{
                    Class res = R.raw.class;
                    json_rooms = res.getField(mContext.getResources().getString(R.string.json_rooms)).getInt(null);
                    json_common_slots = res.getField(mContext.getResources().getString(R.string.json_common_slots)).getInt(null);
                    json_tracks = res.getField(mContext.getResources().getString(R.string.json_tracks)).getInt(null);
                    json_speakers = res.getField(mContext.getResources().getString(R.string.json_speakers)).getInt(null);
                    json_sessions = res.getField(mContext.getResources().getString(R.string.json_sessions)).getInt(null);
                    json_session_tracks = res.getField(mContext.getResources().getString(R.string.json_session_tracks)).getInt(null);
                    json_search_suggest = res.getField(mContext.getResources().getString(R.string.json_search_suggest)).getInt(null);
                    json_map = res.getField(mContext.getResources().getString(R.string.json_map)).getInt(null);
            	} catch (Exception e){
            		LOGI(TAG, "Error al recuperar los raws localizados: "+e.getMessage());
            	}
            	
                // Load static local data
                LOGI(TAG, "Local syncing rooms");
                batch.addAll(new RoomsHandler(mContext).parse(
                        JSONHandler.parseResource(mContext, json_rooms)));
                LOGI(TAG, "Local syncing blocks");
                batch.addAll(new BlocksHandler(mContext).parse(
                        JSONHandler.parseResource(mContext, json_common_slots)));
                LOGI(TAG, "Local syncing tracks");
                batch.addAll(new TracksHandler(mContext).parse(
                        JSONHandler.parseResource(mContext, json_tracks)));
                LOGI(TAG, "Local syncing speakers");
                batch.addAll(new SpeakersHandler(mContext).parseString(
                        JSONHandler.parseResource(mContext, json_speakers)));
                LOGI(TAG, "Local syncing sessions");
                batch.addAll(new SessionsHandler(mContext).parseString(
                        JSONHandler.parseResource(mContext, json_sessions),
                        JSONHandler.parseResource(mContext, json_session_tracks)));
                LOGI(TAG, "Local syncing search suggestions");
                batch.addAll(new SearchSuggestHandler(mContext).parse(
                        JSONHandler.parseResource(mContext, json_search_suggest)));
                LOGI(TAG, "Local syncing map");
                MapPropertyHandler mapHandler = new MapPropertyHandler(mContext);
                batch.addAll(mapHandler.parse(
                        JSONHandler.parseResource(mContext, json_map)));
                //need to sync tile files before data is updated in content provider
                syncMapTiles(mapHandler.getTiles());

                prefs.edit().putInt("local_data_version", LOCAL_VERSION_CURRENT).commit();
                prefs.edit().putString("local_mapdata_version", LOCAL_MAPVERSION_CURRENT).commit();
                if (syncResult != null) {
                    ++syncResult.stats.numUpdates; // TODO: better way of indicating progress?
                    ++syncResult.stats.numEntries;
                }
            }

            LOGD(TAG, "Local sync took " + (System.currentTimeMillis() - startLocal) + "ms");

            try {
                // Apply all queued up batch operations for local data.
                resolver.applyBatch(ScheduleContract.CONTENT_AUTHORITY, batch);
            } catch (RemoteException e) {
                throw new RuntimeException("Problem applying batch operation", e);
            } catch (OperationApplicationException e) {
                throw new RuntimeException("Problem applying batch operation", e);
            }

            batch = new ArrayList<ContentProviderOperation>();
  
     

        }

    public void addOrRemoveSessionFromSchedule(Context context, String sessionId,
            boolean inSchedule) throws IOException {
        LOGI(TAG, "Updating session on user schedule: " + sessionId);
        Googledevelopers conferenceAPI = getConferenceAPIClient();
        try {
            sendScheduleUpdate(conferenceAPI, context, sessionId, inSchedule);
        } catch (GoogleJsonResponseException e) {
            if (e.getDetails().getCode() == 401) {
                LOGI(TAG, "Unauthorized; getting a new auth token.", e);
                AccountUtils.refreshAuthToken(mContext);
                // Try request one more time with new credentials before giving up
                conferenceAPI = getConferenceAPIClient();
                sendScheduleUpdate(conferenceAPI, context, sessionId, inSchedule);
            }
        }
    }

    private void sendScheduleUpdate(Googledevelopers conferenceAPI,
            Context context, String sessionId, boolean inSchedule) throws IOException {
        if (inSchedule) {
            conferenceAPI.users().events().sessions().update(Config.EVENT_ID, sessionId, null).execute();
        } else {
            conferenceAPI.users().events().sessions().delete(Config.EVENT_ID, sessionId).execute();
        }
    }

    private ArrayList<ContentProviderOperation> remoteSyncMapData(String urlString,
            SharedPreferences preferences) throws IOException {
        final String localVersion = preferences.getString("local_mapdata_version", null);

        ArrayList<ContentProviderOperation> batch = Lists.newArrayList();

        BasicHttpClient httpClient = new BasicHttpClient();
        httpClient.setRequestLogger(mQuietLogger);
        httpClient.addHeader("If-None-Match", localVersion);

        LOGD(TAG,"Local map version: "+localVersion);
        HttpResponse response = httpClient.get(urlString, null);
        final int status = response.getStatus();

        if (status == HttpURLConnection.HTTP_OK) {
            // Data has been updated, otherwise would have received HTTP_NOT_MODIFIED
            LOGI(TAG, "Remote syncing map data");
            final List<String> etag = response.getHeaders().get("ETag");
            if (etag != null && etag.size() > 0) {
                MapPropertyHandler handler = new MapPropertyHandler(mContext);
                batch.addAll(handler.parse(response.getBodyAsString()));
                syncMapTiles(handler.getTiles());

                // save new etag as version
                preferences.edit().putString("local_mapdata_version", etag.get(0)).commit();
            }
        } //else: no update

        return batch;
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null &&
                cm.getActiveNetworkInfo().isConnectedOrConnecting();
    }

    /**
     * Synchronise the map overlay files either from the local assets (if available) or from a remote url.
     *
     * @param collection Set of tiles containing a local filename and remote url.
     * @throws IOException
     */
    private void syncMapTiles(Collection<Tile> collection) throws IOException, SVGParseException {
    }
        //keep track of used files, unused files are removed
   //     ArrayList<String> usedTiles = Lists.newArrayList();
     /*   for(Tile tile : collection){
            final String filename = tile.filename;
            final String url = tile.url;

            usedTiles.add(filename);

            if (!MapUtils.hasTile(mContext, filename)) {
                // copy or download the tile if it is not stored yet
                if (MapUtils.hasTileAsset(mContext, filename)) {
                    // file already exists as an asset, copy it
                    MapUtils.copyTileAsset(mContext, filename);
                } else {
                    // download the file
                    File tileFile = MapUtils.getTileFile(mContext, filename);
                    BasicHttpClient httpClient = new BasicHttpClient();
                    httpClient.setRequestLogger(mQuietLogger);
                    HttpResponse httpResponse = httpClient.get(url, null);
                    writeFile(httpResponse.getBody(), tileFile);

                    // ensure the file is valid SVG
                    InputStream is = new FileInputStream(tileFile);
                    SVG svg = SVGParser.getSVGFromInputStream(is);
                    is.close();
                }
            }
        }

        MapUtils.removeUnusedTiles(mContext, usedTiles);
    }

    /**
     * Write the byte array directly to a file.
     * @throws IOException
     */
    private void writeFile(byte[] data, File file) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file, false));
        bos.write(data);
        bos.close();
    }

    /**
     * A type of ConsoleRequestLogger that does not log requests and responses.
     */
    private RequestLogger mQuietLogger = new ConsoleRequestLogger(){
        @Override
        public void logRequest(HttpURLConnection uc, Object content) throws IOException { }

        @Override
        public void logResponse(HttpResponse res) { }
    };

    private Googledevelopers getConferenceAPIClient() {
        HttpTransport httpTransport = new NetHttpTransport();
        JsonFactory jsonFactory = new GsonFactory();
        GoogleCredential credential =
                new GoogleCredential().setAccessToken(AccountUtils.getAuthToken(mContext));
        // Note: The Googledevelopers API is unique, in that it requires an API key in addition to the client
        //       ID normally embedded an an OAuth token. Most apps will use one or the other.
        return new Googledevelopers.Builder(httpTransport, jsonFactory, null)
                .setApplicationName(NetUtils.getUserAgent(mContext))
                .setGoogleClientRequestInitializer(new
                        CommonGoogleClientRequestInitializer(Config.API_KEY))
                .setHttpRequestInitializer(credential)
                .build();
    }
}
