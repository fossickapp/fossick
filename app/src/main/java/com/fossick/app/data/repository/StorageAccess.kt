package com.fossick.app.data.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

object StorageAccess {
    enum class Level {
        BROAD_SHARED_STORAGE,
        MEDIA_ONLY,
        FOLDER_ONLY,
        NONE
    }

    fun hasAllFilesAccess(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        Environment.isExternalStorageManager()

    fun hasMediaAccess(context: Context): Boolean {
        val permissions = mediaPermissions()
        return permissions.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    fun hasLegacySharedStorageAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun canScanSharedFiles(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> hasAllFilesAccess()
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> hasLegacySharedStorageAccess(context)
        else -> false
    }

    fun canQuerySharedDocuments(context: Context): Boolean = hasAllFilesAccess() ||
        (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 && hasLegacySharedStorageAccess(context))

    fun level(context: Context): Level {
        if (hasAllFilesAccess()) return Level.BROAD_SHARED_STORAGE
        if (hasMediaAccess(context) || hasLegacySharedStorageAccess(context)) return Level.MEDIA_ONLY
        if (context.contentResolver.persistedUriPermissions.any { it.isReadPermission }) return Level.FOLDER_ONLY
        return Level.NONE
    }

    fun mediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun allFilesSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
}