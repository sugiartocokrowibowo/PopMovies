package com.example.android.popularmoviesapp.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.example.android.popularmoviesapp.R;
import com.example.android.popularmoviesapp.data.MovieContract.MovieEntry;

/**
 * Created by David on 25/09/16.
 */
public class PopMoviesSyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String LOG_TAG = PopMoviesSyncAdapter.class.getSimpleName();

    public static final int SYNC_INTERVAL = 24 * 60 * 60; // 24hrs
    public static final int SYNC_FLEXTIME = SYNC_INTERVAL / 3; // give or take

    public PopMoviesSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    @Override
    // Called every time a sync is performed - defined in android manifest
    public void onPerformSync(Account account, Bundle extras, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        Log.d(LOG_TAG, "onPerformSync");
        Syncer.newInstance(getContext().getContentResolver(), Syncer.SOURCE_POPULAR).sync();
        Syncer.newInstance(getContext().getContentResolver(), Syncer.SOURCE_TOP_RATED).sync();
        syncVideosAndReviews();
    }

    private void syncVideosAndReviews() {
        final Cursor cursor = queryAllMovies();

        while (cursor.moveToNext()) {
            long movieRowId = cursor.getLong(cursor.getColumnIndex(MovieEntry._ID));
            long movieId = cursor.getLong(cursor.getColumnIndex(MovieEntry.COLUMN_MOVIE_ID));

            Syncer.newInstance(
                    getContext().getContentResolver(), movieRowId, movieId, Syncer.SOURCE_VIDEOS)
                    .sync();

            Syncer.newInstance(
                    getContext().getContentResolver(), movieRowId, movieId, Syncer.SOURCE_REVIEWS)
                    .sync();

        }
    }

    private Cursor queryAllMovies() {
        final String[] columns = {MovieEntry._ID, MovieEntry.COLUMN_MOVIE_ID};
        final String selection = null;
        final String[] selectionArgs = null;
        final String sortOrder = null;

        return getContext().getContentResolver().query(
                MovieEntry.CONTENT_URI,
                columns,
                selection,
                selectionArgs,
                sortOrder);

    }

    public static void initializeSyncAdapter(Context context) {
        getSyncAccount(context);
    }

    /**
     * Helper method to have the sync adapter sync immediately
     *
     * @param context The context used to access the account service
     */
    public static void syncImmediately(Context context) {
        Log.d(LOG_TAG, "syncImmediately");
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        ContentResolver.requestSync(getSyncAccount(context),
                context.getString(R.string.content_authority), bundle);
    }

    /**
     * Helper method to get the fake account to be used with SyncAdapter, or make a new one
     * if the fake account doesn't exist yet.  If we make a new account, we call the
     * onAccountCreated method so we can initialize things.
     *
     * @param context The context used to access the account service
     * @return a fake account.
     */
    public static Account getSyncAccount(Context context) {
        // Get an instance of the Android account manager
        AccountManager accountManager =
                (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

        // Create the account type and default account
        Account newAccount = new Account(
                context.getString(R.string.app_name), context.getString(R.string.sync_account_type));

        // If the password doesn't exist, the account doesn't exist
        if (null == accountManager.getPassword(newAccount)) {

        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
            if (!accountManager.addAccountExplicitly(newAccount, "", null)) {
                return null;
            }
            onAccountCreated(newAccount, context);
        }
        return newAccount;
    }

    private static void onAccountCreated(Account newAccount, Context context) {
        /* Since we've created an account */
        PopMoviesSyncAdapter.configurePeriodicSync(context, SYNC_INTERVAL, SYNC_FLEXTIME);

        /* Without calling setSyncAutomatically, our periodic sync will not be enabled. */
        ContentResolver.setSyncAutomatically(
                newAccount, context.getString(R.string.content_authority), true);
    }

    /**
     * Helper method to schedule the sync adapter periodic execution
     */
    public static void configurePeriodicSync(Context context, int syncInterval, int flexTime) {
        Account account = getSyncAccount(context);
        String authority = context.getString(R.string.content_authority);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // we can enable inexact timers in our periodic sync
            SyncRequest request = new SyncRequest.Builder().
                    syncPeriodic(syncInterval, flexTime).
                    setSyncAdapter(account, authority).
                    setExtras(new Bundle()).build();
            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(account, authority, new Bundle(), syncInterval);
        }
    }
}
