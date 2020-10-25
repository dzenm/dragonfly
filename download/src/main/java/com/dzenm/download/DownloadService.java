package com.dzenm.download;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class DownloadService extends Service {

    private static final String TAG = DownloadService.class.getSimpleName();

    /**
     * 暂停下载广播
     */
    static final String ACTION_DOWNLOAD_PAUSE = "action_download_pause";

    static final String INTENT_FILE_PATH = "intent_file_path";
    static final String INTENT_DOWNLOAD_ID = "intent_download_id";
    static final String INTENT_URL = "intent_url";

    /**
     * 下载任务缓存, 服务启动后进行的所有下载任务
     */
    private Map<Long, DownloadTask> mDownloadTaskCache = new HashMap<>();
    private DownloadListenerBroadcast mDownloadListenerBroadcast;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate and register download broadcast");

        registerDownloadBroadcast();
    }

    private void registerDownloadBroadcast() {
        mDownloadListenerBroadcast = new DownloadListenerBroadcast();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_DOWNLOAD_PAUSE);
        registerReceiver(mDownloadListenerBroadcast, intentFilter);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        String filePath = intent.getStringExtra(INTENT_FILE_PATH);
        String url = intent.getStringExtra(INTENT_URL);
        Long downloadId = intent.getLongExtra(INTENT_DOWNLOAD_ID, -1);
        Log.d(TAG, "onStartCommand download id: " + downloadId);

        // 创建下载任务，添加到缓存，并启动
        final DownloadTask downloadTask = createDownloadTask(filePath, url, downloadId);
        mDownloadTaskCache.put(downloadId, downloadTask);
        new Thread(new Runnable() {
            @Override
            public void run() {
                downloadTask.start();
            }
        }).start();
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 创建下载任务
     *
     * @param filePath   下载文件路径
     * @param url        下载文件地址
     * @param downloadId 下载文件ID
     * @return 下载任务
     */
    private DownloadTask createDownloadTask(String filePath, String url, final Long downloadId) {
        DownloadTask delegate = new DownloadTask();
        if (!TextUtils.isEmpty(filePath)) {
            delegate.setFilePath(filePath);
        }

        delegate.setUrl(url);
        delegate.setOnDownloadListener(new DownloadListener() {
            @Override
            public void onProgress(long totalValue, long currentValue) {
                Intent broadcast = new Intent(DownloadManager.ACTION_DOWNLOAD_PROGRESS);
                broadcast.putExtra(DownloadManager.TOTAL_VALUE, totalValue);
                broadcast.putExtra(DownloadManager.CURRENT_VALUE, currentValue);
                sendBroadcast(broadcast);
            }

            @Override
            public void onError(@Nullable String errorMsg) {
                DownloadTask delegate = mDownloadTaskCache.get(downloadId);
                if (delegate != null) {
                    delegate.stop();
                }

                Intent broadcast = new Intent(DownloadManager.ACTION_DOWNLOAD_FAILED);
                broadcast.putExtra(DownloadManager.STATUS_FAILED, errorMsg);
                sendBroadcast(broadcast);
            }

            @Override
            public void onSuccess(@NonNull String filePath) {
                DownloadTask delegate = mDownloadTaskCache.get(downloadId);
                if (delegate != null) {
                    delegate.stop();
                }

                Intent broadcast = new Intent(DownloadManager.ACTION_DOWNLOAD_SUCCESS);
                broadcast.putExtra(DownloadManager.STAtUS_SUCCESS, filePath);
                sendBroadcast(broadcast);
            }
        });
        return delegate;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy and unregister download broadcast");

        unregisterDownloadBroadcast();
    }

    private void unregisterDownloadBroadcast() {
        for (Map.Entry<Long, DownloadTask> entry : mDownloadTaskCache.entrySet()) {
            if (entry.getValue() != null) {
                entry.getValue().stop();
            }
        }

        unregisterReceiver(mDownloadListenerBroadcast);
    }

    private class DownloadListenerBroadcast extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(ACTION_DOWNLOAD_PAUSE)) {
                long downloadId = intent.getLongExtra("", -1);
                DownloadTask delegate = mDownloadTaskCache.get(downloadId);
                if (delegate != null) {
                    delegate.stop();
                }
            }
        }
    }

    /**
     * <pre>
     * new DownloadTask()
     *        .setUrl(url)
     *        .setOnDownloadListener(new DownloadTask.DownloadListener() {
     *            public void onProgress(long totalValue, long currentValue, long percent) {
     *                append(percent + "%  ");
     *                Log.d("TAG", "下载的百分比: " + percent + "%  ");
     *            }
     *            public void onError(@Nullable String errorMsg) {
     *                append("\n下载错误: " + errorMsg);
     *            }
     *            public void onSuccess(@NonNull File file) {
     *                append("\n下载成功: 100%");
     *            }
     *        }).start();
     * </pre>
     */
    private static class DownloadTask {

        private final String TAG = "DownloadTask";

        private static final String GET = "GET";
        private static final String POST = "POST";

        private DownloadListener mDownloadListener;
        private File mDownloadFile;

        private boolean isDownloadRunning = false;
        private String mFilePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        private String mUrl;

        public void start() {
            download();
        }

        public void stop() {
            if (isDownloadRunning) {
                isDownloadRunning = false;
            }
        }

        public void setUrl(String url) {
            this.mUrl = url;
        }

        public void setFilePath(String filePath) {
            this.mFilePath = filePath;
        }

        public void setOnDownloadListener(DownloadListener listener) {
            this.mDownloadListener = listener;
        }

        public String getFilePath() {
            return mFilePath;
        }

        public boolean isDownloadRunning() {
            return isDownloadRunning;
        }

        /**
         * 开始下载
         */
        private void download() {
            if (isDownloadRunning) return;
            else isDownloadRunning = true;

            File parent = new File(mFilePath);
            if (!parent.exists()) {
                parent.mkdirs();
            }

            String fileName = mUrl.substring(mUrl.lastIndexOf("/") + 1);
            if (!fileName.endsWith(".apk")) {
                fileName = fileName + ".apk";
            }
            mDownloadFile = new File(parent, fileName);

            Log.d(TAG, "下载文件路径: " + mDownloadFile.getAbsolutePath());

            HttpURLConnection connection = null;
            try {
                long alreadyDownloadFileSize = mDownloadFile.length();
                Log.d(TAG, "已下载文件大小: " + alreadyDownloadFileSize);

                // 设置请求信息
                Map<String, String> requestHeaders = new HashMap<>();
                requestHeaders.put("Range", "bytes=" + alreadyDownloadFileSize + "-");
                requestHeaders.put("Charset", "UTF-8");
                connection = createRequest(mUrl, GET, requestHeaders, 10000, 20000);
                Log.d(TAG, "下载文件Url: " + mUrl);

                // 请求返回内容
                int responseCode = connection.getResponseCode();
                long contentLength = connection.getContentLength();
                Log.d(TAG, "请求结果: " + responseCode + ", 剩余文件大小: " + contentLength);

                if (responseCode == HttpURLConnection.HTTP_OK
                        || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    writeStreamToFile(mDownloadFile, connection, alreadyDownloadFileSize + contentLength);
                } else if (responseCode == 416) {
                    sendDownloadFailedMessage("超出文件范围 " + contentLength);
                } else {
                    sendDownloadFailedMessage("HTTP请求错误 " + responseCode);
                }
            } catch (IOException e) {
                sendDownloadFailedMessage(e.getMessage());
                e.printStackTrace();
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        /**
         * 创建HTTP请求
         *
         * @param urlString      请求的URL
         * @param requestMethod  请求的方式
         * @param headers        请求头部信息
         * @param connectTimeout 连接超时时间
         * @param readTimeout    读取超时时间
         * @return HttpURLConnection
         * @throws IOException 请求的异常
         */
        private HttpURLConnection createRequest(String urlString, String requestMethod,
                                                Map<String, String> headers, int connectTimeout,
                                                int readTimeout) throws IOException {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // 设置字符编码
            connection.setRequestMethod(requestMethod);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setInstanceFollowRedirects(true);

            if (connection.getRequestMethod().equals(POST)) {
                // 设置是否向从HttpURLConnection输出, Post请求中,
                // 参数要放在http正文内, 因此需要设置为true, 默认情况下是false
                connection.setDoOutput(true);
                // Post请求不能使用缓存
                connection.setUseCaches(false);
            }

            // 设置是否向从HttpURLConnection读入, 默认情况下是true
            connection.setDoInput(true);

            // 设置开始下载的位置, 单位为字节
            // Range: bytes=startOffset-targetOffset/sum  [表示从startOffset读取，一直读取到targetOffset位置，读取总数为sum直接]
            // Range: bytes=startOffset-targetOffset  [字节总数也可以去掉]
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            connection.connect();
            return connection;
        }

        /**
         * 保存文件
         *
         * @param file       下载的文件
         * @param connection 获取下载文件流
         * @param totalSize  总下载文件大小
         */
        private void writeStreamToFile(File file, HttpURLConnection connection, long totalSize) {
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream fileOutputStream = new FileOutputStream(file, true)) {
                long fileSize = file.length();
                int length;
                byte[] buffer = new byte[1024];
                while (isDownloadRunning && (length = inputStream.read(buffer)) != -1) {
                    if (!isDownloadRunning) break;
                    fileOutputStream.write(buffer, 0, length);
                    fileSize = fileSize + length;

                    Message message = new Message();
                    message.what = DownloadListener.DOWNLOAD_PROGRESS;
                    message.obj = new long[]{fileSize, totalSize};
                    mDownloadCallbackHandler.sendMessage(message);
                }
                if (totalSize == fileSize) {
                    mDownloadCallbackHandler.sendEmptyMessage(DownloadListener.DOWNLOAD_SUCCESS);
                } else {
                    sendDownloadFailedMessage("文件大小与服务器文件大小不一致");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @SuppressLint("HandlerLeak")
        private Handler mDownloadCallbackHandler = new Handler() {
            private long mCurrentPercent = 0;

            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case DownloadListener.DOWNLOAD_PROGRESS:
                        // 下载进度回调
                        isDownloadRunning = true;
                        long[] fileSizes = (long[]) msg.obj;
                        long alreadyDownloadFileSize = fileSizes[0], totalSize = fileSizes[1];
                        // 计算下载的百分比
                        long percent = alreadyDownloadFileSize * 100 / totalSize;
                        if (percent != mCurrentPercent) {
                            mCurrentPercent = percent;
                            Log.d(TAG, "下载进度: " + percent);
                            if (mDownloadListener != null) {
                                mDownloadListener.onProgress(totalSize, alreadyDownloadFileSize);
                            }
                        }
                        break;
                    case DownloadListener.DOWNLOAD_SUCCESS:
                        // 下载成功回调
                        isDownloadRunning = false;
                        Log.d(TAG, "下载完成: " + mDownloadFile.getAbsolutePath());
                        if (mDownloadListener != null) {
                            mDownloadListener.onSuccess(mDownloadFile.getAbsolutePath());
                        }
                        break;
                    case DownloadListener.DOWNLOAD_FAILED:
                        // 下载失败回调
                        isDownloadRunning = false;
                        String errorMsg = (String) msg.obj;
                        Log.e(TAG, "下载失败: " + errorMsg);
                        if (mDownloadListener != null) {
                            mDownloadListener.onError(errorMsg);
                        }
                        break;
                    default:
                        break;
                }
            }
        };

        private void sendDownloadFailedMessage(@Nullable String errorMsg) {
            Message message = new Message();
            message.what = DownloadListener.DOWNLOAD_FAILED;
            message.obj = errorMsg;
            mDownloadCallbackHandler.sendMessage(message);
        }
    }
}