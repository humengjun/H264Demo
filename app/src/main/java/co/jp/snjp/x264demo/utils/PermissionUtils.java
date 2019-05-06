package co.jp.snjp.x264demo.utils;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

/**
 * Created by Administrator on 2018/9/1 0001.
 */

public class PermissionUtils {


    public static boolean checkPermission(Activity activity, String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(activity, permission) ==
                PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
            return false;
        }
    }

    public static boolean checkPermissions(Activity activity, String[] permissions, int[] requestCodes) {
        boolean isSuccess = true;
        for (int i = 0; i < permissions.length; i++) {
            if (ContextCompat.checkSelfPermission(activity, permissions[i]) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{permissions[i]}, requestCodes[i]);
                isSuccess = false;
            }
        }
        return isSuccess;
    }
}
