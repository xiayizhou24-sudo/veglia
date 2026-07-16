// Veglia · holds MediaProjection consent across Activity/Service boundary.
package dev.veglia.companion;

import android.content.Intent;

public class ProjectionHolder {
    private static int resultCode;
    private static Intent resultData;

    public static void store(int code, Intent data) {
        resultCode = code;
        resultData = data;
    }

    public static int getResultCode() {
        return resultCode;
    }

    public static Intent getResultData() {
        return resultData;
    }

    public static void clear() {
        resultCode = 0;
        resultData = null;
    }
}
