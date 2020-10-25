package com.dzenm.download_manager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class DownloadHelper {

    private static final String TAG = DownloadHelper.class.getSimpleName();

    /**
     * 在下载过程中通知栏会一直显示该下载的Notification。在下载完毕后该Notification会继续显示。
     * 直到用户点击该Notification或者消除该Notification。这是默认的參数值。
     */
    public static final int NOTIFICATION_VISIBLE = 0;

    /**
     * 在下载进行的过程中。通知栏中会一直显示该下载的Notification，当下载完毕时，该Notification
     * 会被移除。
     */
    public static final int NOTIFICATION_VISIBLE_NOTIFY_COMPLETED = 1;

    /**
     * 不显示该下载请求的Notification。假设要使用这个參数，须要在应用的清单文件里加上
     * DOWNLOAD_WITHOUT_NOTIFICATION权限。
     */
    public static final int NOTIFICATION_HIDDEN = 2;

    /**
     * 仅仅有在下载完毕后该Notification才会被显示。
     */
    public static final int NOTIFICATION_VISIBLE_NOTIFY_ONLY_COMPLETION = 3;

    private static final long DOWNLOAD_DEFAULT_ID = 0L;
    private static final long DOWNLOAD_ERROR_ID = -1L;

    private static final int DOWNLOAD_PROGRESS = 1001;
    private static final int DOWNLOAD_FAILED = 1002;

    /**
     * 安装包类型
     */
    private static final String MIME_TYPE = "application/vnd.android.package-archive";

    /**
     * 保存Download ID的shared_prefs文件名称
     */
    private static final String DOWNLOAD_PREF = "download_pref";
    private static final String FILE_PATH = "file_path";

    private Context mContext;

    /**
     * 下载监听广播
     */
    private DownloadReceiver mDownloadReceiver;

    /**
     * 下载管理器
     */
    private DownloadManager mDownloadManager;

    /**
     * 下载的任务ID
     */
    private long mDownloadId;

    /**
     * 下载监听回调事件 {@link #setOnDownloadListener(OnDownloadListener)}
     * 回调的方法说明参考{@link OnDownloadListener}
     */
    private OnDownloadListener mOnDownloadListener;

    /**
     * 下载apk文件的url {@link #setUrl(String)}
     */
    private String mUrl;

    /**
     * 文件下载存储的路径 {@link #setFilePath(String)}
     * 默认存储的路径为 storage/emulated/0/{app名称}/apk/{版本号}
     */
    private String mFilePath;

    /**
     * 判断是否正在下载状态 {@link #isRunningDownload()}
     */
    private boolean isRunningDownload = false;

    @IntDef({NOTIFICATION_VISIBLE, NOTIFICATION_VISIBLE_NOTIFY_COMPLETED,
            NOTIFICATION_HIDDEN, NOTIFICATION_VISIBLE_NOTIFY_ONLY_COMPLETION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface NotificationType {
    }

    private @NotificationType
    int mNotificationType = NOTIFICATION_VISIBLE_NOTIFY_COMPLETED;

    public static DownloadHelper newInstance(Context context) {
        return new DownloadHelper(context);
    }

    public DownloadHelper(Context context) {
        mContext = context;
        mDownloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        mDownloadReceiver = new DownloadReceiver();
        mFilePath = mContext.getFilesDir().getAbsolutePath();
    }

    /**
     * @param url 下载的url {@link #mUrl}
     * @return this
     */
    public DownloadHelper setUrl(String url) {
        mUrl = url;
        return this;
    }

    /**
     * @param filePath 下载文件的路径  {@link #mFilePath}
     * @return this
     */
    public DownloadHelper setFilePath(String filePath) {
        mFilePath = filePath;
        return this;
    }

    /**
     * @param notificationType 通知栏的显示的方式  {@link #mNotificationType}
     * @return this
     */
    public DownloadHelper setNotificationType(@NotificationType int notificationType) {
        mNotificationType = notificationType;
        return this;
    }

    /**
     * @param onDownloadListener 下载监听回调  {@link #mOnDownloadListener}
     * @return this
     */
    public DownloadHelper setOnDownloadListener(OnDownloadListener onDownloadListener) {
        mOnDownloadListener = onDownloadListener;
        return this;
    }

    /**
     * @return 是否正在下载
     */
    public boolean isRunningDownload() {
        return isRunningDownload;
    }

    /**
     * 开始下载
     */
    public void start() {
        try {
            SharedPreferences sp = mContext.getSharedPreferences(DOWNLOAD_PREF, Context.MODE_PRIVATE);
            String filePath = sp.getString(FILE_PATH, "");
            File file = new File(filePath);
            if (TextUtils.isEmpty(filePath) || !file.exists()) {
                startDownloadTask();
            } else {
                Log.d(TAG, "文件已下载, 文件路径" + filePath);
                downloadApkFileSuccessCallback(mContext, FileUtil.getUri(mContext, file));
            }
        } catch (Exception e) {
            stop();
            e.printStackTrace();
        }
    }

    /**
     * 取消下载
     */
    public void stop() {
        removeDownloadTask();
    }

    /**
     * 注册下载监听广播
     */
    private void registerDownloadBroadcast() {
        if (!isRunningDownload) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            intentFilter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED);
            mContext.registerReceiver(mDownloadReceiver, intentFilter);
            isRunningDownload = true;
        }
    }

    /**
     * 取消注册下载监听广播
     */
    private void unregisterDownloadBroadcast() {
        if (isRunningDownload) {
            mContext.unregisterReceiver(mDownloadReceiver);
            isRunningDownload = false;
        }
    }

    /**
     * 判断当前手机是否可以使用 DownloadManager 下载更新
     *
     * @return DownloadManager是否可用
     */
    private boolean isDownloadManager() {
        String packageName = "com.android.providers.downloads";
        int state = mContext.getPackageManager().getApplicationEnabledSetting(packageName);
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + packageName));
                mContext.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                mContext.startActivity(intent);
            }
            return false;
        }
        return true;
    }

    /**
     * 下载文件
     */
    private void startDownloadTask() {
        registerDownloadBroadcast();
        if (isDownloadManager()) {
            // 先清空之前的下载
            if (mDownloadId != DOWNLOAD_DEFAULT_ID) removeTask(mDownloadId);
            mHandler.post(mRunnable);
            // 获取下载任务ID
            mDownloadId = mDownloadManager.enqueue(getRequest(mUrl));
            Log.i(TAG, "已注册下载监听广播, 开始下载..." + ", 下载任务Download ID: " + mDownloadId);
        } else {
            callBrowserToDownload();
        }
    }

    /**
     * 如果DownloadManager不可用, 调用浏览器下载
     */
    private void callBrowserToDownload() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(mUrl));
        mContext.startActivity(intent);
        Log.i(TAG, "已注册下载监听广播, 调用浏览器下载...");
    }

    /**
     * @param downloadId 移除任务的任务ID
     */
    private void removeTask(Long downloadId) {
        try {
            mDownloadManager.remove(downloadId);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    /**
     * 移除下载任务, 移除进度查询， 取消广播的注册
     */
    private void removeDownloadTask() {
        // 移除查询下载进度执行事件
        mHandler.removeCallbacks(mRunnable);
        unregisterDownloadBroadcast();
        Log.i(TAG, "移除下载任务, 移除进度查询, 取消注册下载监听广播");
    }

    /**
     * @param url 下载文件的url
     * @return Request下载设置
     */
    private DownloadManager.Request getRequest(String url) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        Log.d(TAG, "下载文件的url:" + url);

        // 自定义文件路径
        String fileName = mUrl.substring(mUrl.lastIndexOf("/") + 1);
        if (!mFilePath.substring(mFilePath.lastIndexOf("/") + 1).contains(".")) {
            mFilePath = mFilePath + File.separator + fileName;
        }
        File file = new File(mFilePath);
        Uri uri = Uri.fromFile(file);
        // 如果使用content// 开头的Uri指定下载目标路径, 下载失败: Not a file URI: content://
        request.setDestinationUri(uri);

        Log.d(TAG, "下载文件存储目录: " + mFilePath);

        // 设置允许使用的网络类型，这里是移动网络和wifi都可以
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        // 默认为下载剩余时间
//        request.setDescription("");                                                 // 通知栏描述信息
        request.setTitle(fileName)                                                  // 通知栏标题, 默认值为APP名称
                .setVisibleInDownloadsUi(true)                                      // 设置可以在下载UI界面显示
                .setNotificationVisibility(mNotificationType)                       // 显示通知栏的样式
                .setAllowedOverRoaming(true)
                .setMimeType(MIME_TYPE)                                             // 设置类型为安装包
                .allowScanningByMediaScanner();                                     // 设置为可被媒体扫描器找到
        if (mOnDownloadListener != null) mOnDownloadListener.onPrepared(request);
        return request;
    }

    /**
     * 接收传递的进度
     */
    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == DOWNLOAD_PROGRESS) {
                long[] fileSize = (long[]) msg.obj;
                if (mOnDownloadListener != null)
                    mOnDownloadListener.onProgress(fileSize[0], fileSize[1]);
            } else if (msg.what == DOWNLOAD_FAILED) {
                setDownloadFailed((String) msg.obj);
            }
        }
    };

    /**
     * 执行查询下载任务
     */
    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            getDownloadManagerQueryStatus();
            mHandler.post(mRunnable);
        }
    };

    /**
     * 查询下载状态
     */
    private void getDownloadManagerQueryStatus() {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(mDownloadId);
        // 通过ID向下载管理查询下载情况，返回一个cursor
        Cursor cursor = mDownloadManager.query(query);
        if (cursor == null) return;
        if (!cursor.moveToFirst()) return;

        String msg = "下载失败:";
        int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        if (status == DownloadManager.STATUS_PENDING) {
            Log.i(TAG, "等待下载");

        } else if (status == DownloadManager.STATUS_RUNNING) {      // 查询下载进度
            // 以下是从游标中进行信息提取
            long downloadSoFar = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long downloadTotalSize = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

            if (downloadTotalSize == -1) return;

            Log.i(TAG, "总文件大小: " + downloadTotalSize + ", 正在下载进度: " + downloadSoFar);
            Message message = Message.obtain();
            message.what = DOWNLOAD_PROGRESS;
            message.obj = new long[]{downloadSoFar, downloadTotalSize};

            mHandler.sendMessage(message);
        } else if (status == DownloadManager.STATUS_PAUSED) {         // 查看下载暂停的原因
            // 以下是从游标中进行信息提取
            String title = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE));
            int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
            if (reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI) {
                msg = title + ": 等待连接Wi-Fi网络";
            } else if (reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK) {
                msg = title + ": 等待连接网络";
            } else if (reason == DownloadManager.PAUSED_WAITING_TO_RETRY) {
                msg = title + ": 等待重试...";
            }
            Log.e(TAG, "下载暂停: " + msg);
        } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
            Log.i(TAG, "下载成功");
        } else if (status == DownloadManager.STATUS_FAILED) {      // 查看下载错误的原因
            int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
            if (reason == DownloadManager.ERROR_FILE_ERROR) {
                msg = "文件错误";
            } else if (reason == DownloadManager.ERROR_UNHANDLED_HTTP_CODE) {
                msg = "未处理的HTTP错误码";
            } else if (reason == DownloadManager.ERROR_HTTP_DATA_ERROR) {
                msg = "数据接收或处理错误";
            } else if (reason == DownloadManager.ERROR_TOO_MANY_REDIRECTS) {
                msg = "重定向错误";
            } else if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) {
                msg = "存储空间不足";
            } else if (reason == DownloadManager.ERROR_DEVICE_NOT_FOUND) {
                msg = "设备未找到";
            } else if (reason == DownloadManager.ERROR_CANNOT_RESUME) {
                msg = "恢复下载失败";
            } else if (reason == DownloadManager.ERROR_FILE_ALREADY_EXISTS) {
                msg = "文件已存在";
            } else if (reason == DownloadManager.ERROR_UNKNOWN) {
                msg = "未知错误";
            }
            Log.e(TAG, "下载失败: " + msg);
            Message message = Message.obtain();
            message.what = DOWNLOAD_FAILED;
            message.obj = msg;
            mHandler.sendMessage(message);
        }
        if (!cursor.isClosed()) cursor.close();
    }

    /**
     * @param msg 设置失败回调
     */
    private void setDownloadFailed(String msg) {
        if (mOnDownloadListener == null) {
            Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
        } else {
            mOnDownloadListener.onFailed(msg);
        }
        stop();
    }

    /**
     * 下载完成通知广播
     */
    private class DownloadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ERROR_ID);
                if (id == mDownloadId) {
                    verifyDownloadFile(context, id);
                }
            } else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(intent.getAction())) {
                // 进入下载详情
                Intent intentView = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                intentView.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentView);
            }
        }
    }

    /**
     * 校验下载文件
     */
    private void verifyDownloadFile(Context context, long id) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        if (downloadManager == null) return;
        Uri uri = downloadManager.getUriForDownloadedFile(id);                  // 下载文件的uri
        Log.d(TAG, "接收下载文件的ID: " + id + ", 接收下载文件uri: " + uri);

        String type = downloadManager.getMimeTypeForDownloadedFile(id);    // 下载文件的ID
        // 保存文件的ID和存储路径
        SharedPreferences sp = context.getSharedPreferences(DOWNLOAD_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(FILE_PATH, mFilePath);
        editor.apply();

        // 当下载文件类型为安装版类型时, 进入安装APK界面
        if (MIME_TYPE.equals(type)) {
            downloadApkFileSuccessCallback(context, uri);
        } else {
            downloadFileSuccessCallback(uri);
        }

        // 先回调， 再移除任务，否则会出错
        mDownloadId = DOWNLOAD_DEFAULT_ID;
        stop();
    }

    /**
     * APK文件下载成功的回调
     *
     * @param context Context, 安装APK
     * @param uri     下载文件的uri
     */
    private void downloadApkFileSuccessCallback(Context context, Uri uri) {
        if (!install((Activity) context, uri)) {
            Log.d(TAG, "安装失败");
            setDownloadFailed("安装失败");
        } else {
            Log.d(TAG, "进入安装APK");
        }
        downloadFileSuccessCallback(uri);
    }

    /**
     * @param activity 当前Activity
     * @param uri      APK文件的路径
     */
    private boolean install(Activity activity, Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, MIME_TYPE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 表示对目标应用临时授权该Uri所代表的文件}
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivity(intent);
            return true;
        }
        return false;
    }

    /**
     * 文件下载成功的回调
     *
     * @param uri 下载文件的uri
     */
    private void downloadFileSuccessCallback(Uri uri) {
        if (mOnDownloadListener != null) {
            mOnDownloadListener.onSuccess(uri, MIME_TYPE);
        }
    }

    public interface OnDownloadListener {

        /**
         * 下载前的准备
         *
         * @param request 设置request
         */
        void onPrepared(DownloadManager.Request request);

        /**
         * 正在下载
         *
         * @param soFar         已经下载的大小
         * @param totalFileSize 文件大小
         */
        void onProgress(long soFar, long totalFileSize);

        /**
         * 下载成功
         *
         * @param uri      下载完成的文件uri
         * @param mimeType 文件类型
         */
        void onSuccess(Uri uri, String mimeType);

        /**
         * 下载失败
         *
         * @param msg 下载失败的错误信息
         */
        void onFailed(String msg);
    }
}
