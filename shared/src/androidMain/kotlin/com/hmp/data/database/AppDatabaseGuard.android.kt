package com.hmp.data.database

import java.io.File

internal actual fun databaseFileExists(path: String): Boolean = File(path).exists()
