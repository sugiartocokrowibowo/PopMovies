package com.example.android.popularmoviesapp;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.example.android.popularmoviesapp.data.MovieContract.VideoEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Vector;

/**
 * Gets videos with a http request.
 * Parses data as a JSON string on background thread and
 * publishes the result on the UI.
 */
public class FetchVideosTask extends AsyncTask<Void, Void, Void> {
    private final String LOG_TAG = FetchVideosTask.class.getSimpleName();

    private final Context context;
    private String videosJsonStr;

    private long movie_id;
    private long movie_key;

    public FetchVideosTask(Context context, long movie_key, long movie_id) {
        this.context = context;
        this.movie_id = movie_id;
        this.movie_key = movie_key;
    }

    /* HTTP request on background thread. */
    @Override
    protected Void doInBackground(Void... params) {
        // Declared outside in order to be closed in finally block
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        try {
            // https://www.themoviedb.org/
            final String BASE_URL = "http://api.themoviedb.org/3/movie/";
            final String API_PARAM = "api_key";

            Uri builtUri = Uri.parse(BASE_URL).buildUpon()
                    .appendPath(String.valueOf(movie_id))
                    .appendPath("videos")
                    .appendQueryParameter(API_PARAM, BuildConfig.MOVIE_DB_API_KEY)
                    .build();

            URL url = new URL(builtUri.toString());

            Log.d(LOG_TAG, "movie_key: " + movie_key);
            Log.d(LOG_TAG, "videos url: " + url);

            // create the request to TMDb, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            // read the input stream into a string
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                // nothing to do.
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                // But it does make debugging a *lot* easier if you print out the completed
                // buffer for debugging.
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                // stream was empty
                return null;
            }
            videosJsonStr = buffer.toString();
            parseAndPersistVideoData(videosJsonStr);

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
            // no movie data found
            return null;
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }

        return null;
    }

    private void parseAndPersistVideoData(String videosJsonStr) throws JSONException {

        final String MD_RESULTS = "results";
        final String MD_KEY = "key";
        final String MD_ID = "id";

        final JSONObject data = new JSONObject(videosJsonStr);
        final JSONArray videos = data.getJSONArray(MD_RESULTS);

        Vector<ContentValues> contentValuesVector = new Vector<>(videos.length());

        for (int i = 0; i < videos.length(); i++) {

            // get data from JSON String
            final JSONObject videosData = videos.getJSONObject(i);
            final String id = videosData.getString(MD_ID);
            final String key = videosData.getString(MD_KEY);

            ContentValues videoValues = new ContentValues();

            videoValues.put(VideoEntry.COLUMN_MOVIE_KEY, movie_key);
            videoValues.put(VideoEntry.COLUMN_VIDEO_KEY, key);
            videoValues.put(VideoEntry.COLUMN_VIDEO_ID, id);

            contentValuesVector.add(videoValues);

        }

        // TODO not to be used so often in final version
        insertValues(contentValuesVector);
//        final Cursor cursor = context.getContentResolver().query(VideoEntry.CONTENT_URI, null, null, null, null);
//        Log.d(LOG_TAG, "video query: " + DatabaseUtils.dumpCursorToString(cursor));
    }

    private void insertValues(Vector<ContentValues> contentValuesVector) {
        if (contentValuesVector.size() > 0) {
            Uri uri = VideoEntry.CONTENT_URI;
            ContentValues[] contentValuesArray = new ContentValues[contentValuesVector.size()];
            contentValuesVector.toArray(contentValuesArray);
            for (ContentValues contentValues : contentValuesArray) {
                 context.getContentResolver().insert(uri, contentValues);
            }
        }
    }

}