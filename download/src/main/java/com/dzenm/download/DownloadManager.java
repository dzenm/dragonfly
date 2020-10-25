package com.dzenm.download;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class DownloadManager extends BroadcastReceiver {

    public static final String ACTION_DOWNLOAD_PROGRESS = "action_download_progress";
    public static final String ACTION_DOWNLOAD_SUCCESS = "action_download_success";
    public static final String ACTION_DOWNLOAD_FAILED = "action_download_failed";

    public static final String TOTAL_VALUE = "total_value";
    public static final String CURRENT_VALUE = "current_value";
    public static final String STAtUS_SUCCESS = "status_success";
    public static final String STATUS_FAILED = "status_failed";

    private Context mContext;
    private String mUrl;
    private String mFilePath;
    private long mDownloadId;
    private boolean mThreadFlag = false;
    private DownloadListener mDownloadListener;

    public DownloadManager(Context context) {
        mContext = context;
    }

    public DownloadManager setUrl(String url) {
        this.mUrl = url;
        return this;
    }

    public DownloadManager setFilePath(String filePath) {
        this.mFilePath = filePath;
        return this;
    }

    public DownloadManager setDownloadListener(DownloadListener listener) {
        this.mDownloadListener = listener;
        return this;
    }

    public long start() {
        if (!mThreadFlag) {
            mDownloadId = System.currentTimeMillis();
            mThreadFlag = true;
            registerDownloadBroadcast();
            Intent downloadServices = new Intent(mContext, DownloadService.class);
            downloadServices.putExtra(DownloadService.INTENT_DOWNLOAD_ID, mDownloadId);
            downloadServices.putExtra(DownloadService.INTENT_FILE_PATH, mFilePath);
            downloadServices.putExtra(DownloadService.INTENT_URL, mUrl);
            mContext.startService(downloadServices);
        }
        return mDownloadId;
    }

    public void stop() {
        if (mThreadFlag) {
            mThreadFlag = false;
            Intent intent = new Intent(DownloadService.ACTION_DOWNLOAD_PAUSE);
            intent.putExtra(DownloadService.INTENT_DOWNLOAD_ID, mDownloadId);
            mContext.sendBroadcast(intent);
            unregisterDownloadBroadcast();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case ACTION_DOWNLOAD_PROGRESS:
                    long totalValue = intent.getLongExtra(TOTAL_VALUE, 0);
                    long currentValue = intent.getLongExtra(CURRENT_VALUE, 0);
                    mDownloadListener.onProgress(totalValue, currentValue);
                    break;
                case ACTION_DOWNLOAD_SUCCESS:
                    String filePath = intent.getStringExtra(STAtUS_SUCCESS);
                    mDownloadListener.onSuccess(filePath);
                    break;
                case ACTION_DOWNLOAD_FAILED:
                    String errorMsg = intent.getStringExtra(STATUS_FAILED);
                    mDownloadListener.onError(errorMsg);
                    break;
            }
        }
    }

    private void registerDownloadBroadcast() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_DOWNLOAD_PROGRESS);
        intentFilter.addAction(ACTION_DOWNLOAD_SUCCESS);
        intentFilter.addAction(ACTION_DOWNLOAD_FAILED);
        mContext.registerReceiver(this, intentFilter);
    }

    private void unregisterDownloadBroadcast() {
        mContext.unregisterReceiver(this);
    }
}
