package com.dzenm.download;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface DownloadListener {

    int DOWNLOAD_PROGRESS = 1;
    int DOWNLOAD_SUCCESS = 2;
    int DOWNLOAD_FAILED = 3;

    /**
     * 下载文件进度
     *
     * @param totalValue   文件总大小
     * @param currentValue 当前下载的文件大小
     */
    void onProgress(long totalValue, long currentValue);

    /**
     * 下载出错
     *
     * @param errorMsg 错误信息
     */
    void onError(@Nullable String errorMsg);

    /**
     * 下载文件成功
     */
    void onSuccess(@NonNull String filePath);
}
