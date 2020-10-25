package com.dzenm.download_manager;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

class FileUtil {

    /**
     * 获取uri, 适配Android N
     * 必须在Manifest中添加
     * <provider
     * android:name="android.support.v4.content.FileProvider"
     * android:authorities="${applicationId}.provider"
     * android:exported="false"
     * android:grantUriPermissions="true">
     * <meta-data
     * android:name="android.support.FILE_PROVIDER_PATHS"
     * android:resource="@xml/file_provider_path" />
     * </provider>
     *
     * @param file 需要获取uri的文件
     * @return uri
     */
    static Uri getUri(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0 的文件访问权限问题, FileProvider在xml新建的文件权限表示的含义
            // 例: <external-path name="external" path="." /> 转化后的结果 content://com.dzenm.helper.provider/external/did
            // name 表示名字转化后的根目录路径名称, path表示可以访问的路径, 也就是如下getUriForFile()方法的参数File, 可以访问的路径
            // 其中 . 表示所有文件夹, 也可以具体设置文件夹(必须存在), 访问该文件夹下的文件的路径一定是path下的文件
            return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        } else {
            return Uri.fromFile(file);
        }
    }

    /**
     * @param uri 当前图片的Uri
     * @return 解析后的Uri对应的String
     */
    static String getRealFilePath(Context context, Uri uri) {
        // 1. DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // 1.1 ExternalStorageProvider, Whether the Uri authority is ExternalStorageProvider
            if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            }
            // 1.2 DownloadsProvider, Whether the Uri authority is DownloadsProvider
            else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            }
            // 1.3 MediaProvider, Whether the Uri authority is MediaProvider.
            else if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // 2. MediaStore (and general)
        else if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
            // 判断是否是Google相册的图片，类似于content://com.google.android.apps.photos.content/...
            if ("com.google.android.apps.photos.content".equals(uri.getAuthority())) {
                return uri.getLastPathSegment();
            }
            // 判断是否是Google相册的图片，类似于content://com.google.android.apps.photos.contentprovider/0/1/mediakey:/local%3A821abd2f-9f8c-4931-bbe9-a975d1f5fabc/ORIGINAL/NONE/1075342619
            else if ("com.google.android.apps.photos.contentprovider".equals(uri.getAuthority())) {
                if (uri.getAuthority() != null) {
                    try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        return writeToTempImageAndGetPathUri(context, bmp).toString();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            } else {                                // 其他类似于media这样的图片，和android4.4以下获取图片path方法类似
                return getDataColumn(context, uri, null, null);
            }
        }
        // 3. 判断是否是文件形式 File
        else if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return uri.getPath();
    }

    /**
     * 获取图片真实路径, 由于文件uri会存在scheme会content的情况, 但是通过Cursor方法找不到
     * 只能通过Uri的getPath方法获取文件真实路径
     *
     * @param uri           通过Cursor查找文件路径(文件的uri)
     * @param selection     选择哪些列{@link androidx.core.content.FileProvider}
     * @param selectionArgs 通过mimeType去选择
     * @return 文件uri
     */
    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        String filePath = null;
        String[] projection = {MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME};
        try (Cursor cursor = context.getContentResolver().query(uri, projection, selection,
                selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                filePath = cursor.getString(cursor.getColumnIndexOrThrow(projection[0]));
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return filePath;
    }

    /**
     * 将图片流读取出来保存到手机本地相册中
     **/
    private static Uri writeToTempImageAndGetPathUri(Context context, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(context.getContentResolver(),
                inImage, "title", null);
        return Uri.parse(path);
    }
}
