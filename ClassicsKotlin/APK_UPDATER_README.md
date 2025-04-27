# APK Auto-Updater Implementation Guide

This document explains how to use the custom APK updater system in the Jasmine TV application.

## Overview

The APK Auto-Updater allows the app to check for, download, and install updates directly from Google Drive (or any other direct download URL), bypassing the need for Google Play Store. This is particularly useful for:

1. Android TV devices without Google Play Store
2. Testing new versions before public release
3. Rolling out updates to specific users
4. Having more control over the update process

## Features

- Checks for updates on application start
- Shows update dialog with version information and release notes
- Downloads updates in the background
- Provides progress indication during download
- Automatically initiates installation after download
- Supports forced updates that cannot be skipped
- Maintains minimum version compatibility

## How It Works

1. The app checks a JSON file hosted on Google Drive for update information
2. If a newer version is available, a dialog is shown to the user
3. When the user confirms, the APK is downloaded using Android's DownloadManager
4. After download completes, the app triggers the installation process

## Setup Instructions

### 1. Prepare the Update JSON File

Create a JSON file with the following structure:

```json
{
  "versionName": "1.2.0",
  "versionCode": 120,
  "downloadUrl": "https://drive.google.com/uc?export=download&id=1w9oaJqmmKr7P3uDM68d5e_FhSrhspWt5",
  "releaseNotes": "- Improved UI performance\n- Fixed playback issues with certain channels\n- Added new login options\n- Updated libraries for better stability",
  "updateDate": "2025-04-26",
  "forceUpdate": false,
  "minSupportedVersion": 100
}
```

### 2. Upload to Google Drive

1. Upload your JSON file to Google Drive
2. Make the file publicly accessible (Anyone with the link can view)
3. Get the file ID from the sharing link (the long string after `/d/` in the URL)

### 3. Upload Your APK File

1. Build and sign your APK
2. Upload the APK to Google Drive
3. Make the file publicly accessible (Anyone with the link can view)
4. Get the file ID from the sharing link

### 4. Update Constants in AppUpdateManager

In the `AppUpdateManager.kt` file, update the `UPDATE_INFO_URL` constant with your JSON file's Google Drive URL:

```kotlin
private const val UPDATE_INFO_URL = "https://drive.google.com/uc?export=download&id=YOUR_JSON_FILE_ID"
```

## Using Direct Download URLs

Instead of Google Drive, you can also use any direct download URL that provides the APK file. Simply modify the `downloadUrl` field in your JSON file to point to the direct URL.

## Customization Options

### Force Updates

Set `forceUpdate` to `true` in your JSON file to prevent users from skipping the update. This is useful for critical updates.

### Minimum Supported Version

Set `minSupportedVersion` to specify the minimum version code that is still supported. If the user's app version is below this value, they will be shown a force update dialog.

### Release Notes

Format your release notes using `\n` for new lines. The update dialog will display these notes to users before they decide to update.

## Security Considerations

- Always sign your APK with the same certificate to ensure smooth updates
- Host your JSON file and APK in secure locations
- Consider adding a hash verification step to validate the downloaded APK

## Troubleshooting

If users are having trouble with the auto-update:

1. Check if the download URL is accessible
2. Verify that the APK is properly signed with the same certificate
3. Make sure the device has enough storage space
4. Check for any permission issues with the download location

## Manual Update Alternative

For users who are unable to use the auto-update feature, provide an alternative direct download link on your website or support channel.