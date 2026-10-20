package com.hmp.data.database

import platform.Foundation.NSFileManager

internal actual fun databaseFileExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)
