package com.vdigital.volumestream.platform.orientation

import platform.Foundation.NSNotificationCenter

object OrientationManager {
    const val LOCK_CHANGED_NOTIFICATION = "VSOrientationLockChanged"

    var forceLandscape: Boolean = false
        set(value) {
            field = value
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = LOCK_CHANGED_NOTIFICATION,
                `object` = null
            )
        }
}
