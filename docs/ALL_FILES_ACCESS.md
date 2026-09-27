# Fossick storage access and Play declaration

## Core functionality

Fossick's primary function is on-device file and memory search. On Android 11 and newer, `MANAGE_EXTERNAL_STORAGE` is declared only to discover supported files across broad shared device storage, including documents and downloads that are not reliably represented by media collections. Fossick does not use this access for unrelated features.

The app checks `Environment.isExternalStorageManager()` and opens `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` with Fossick's package URI. The user must deliberately enable access in Android Special app access. This is not an ordinary runtime permission. Fossick verifies the result after returning from Settings and continues in limited mode when access is declined.

Android 8-10 retain the legacy `READ_EXTERNAL_STORAGE` runtime permission model. MediaStore and user-selected Storage Access Framework folders remain supported on every applicable API level.

## Discovery and identity

MediaStore remains the preferred source for images, screenshots, camera photos, videos, and media metadata. Verified broad access additionally enables bounded filesystem discovery for supported PDF, TXT, CSV, JSON, XML, DOC/DOCX, PPT/PPTX, XLS/XLSX, image, video, and download files. Fossick excludes its own app-private storage and Android app-private data/obb directories.

Discovery is local-only. MediaStore, filesystem, and SAF results use normalized shared-storage paths or provider URIs for stable identity and are deduplicated before synchronization. Fossick does not bypass Android sandbox protections; content is searchable only when it is actually accessible through the selected source.

## Privacy and Play declaration text

Fossick does not upload, transmit, sell, remotely analyze, or export files, extracted text, embeddings, search queries, or the local index. Processing, OCR, and search remain on-device. The accurate Play declaration is: "All Files Access is required for Fossick's core functionality: broad on-device search of supported files and folders across shared device storage."

## Network permissions

- `INTERNET`: absent from the merged application manifest.
- `ACCESS_NETWORK_STATE`: absent from the merged application manifest. No dependency is intentionally included for network access.
- `MANAGE_EXTERNAL_STORAGE`: present for the core on-device file-search function described above.
