package github.ponyhuang.gimi

import android.os.Build

/** 首次安装时只申请一次通知权限，拒绝后交由权限设置页处理。 */
internal fun shouldRequestNotificationPermission(
    sdkInt: Int,
    granted: Boolean,
    previouslyRequested: Boolean,
): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU && !granted && !previouslyRequested
