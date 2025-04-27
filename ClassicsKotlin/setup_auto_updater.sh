#!/bin/bash

# Setup script for APK Auto-Updater

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}======================================================${NC}"
echo -e "${GREEN}     Jasmine TV Auto-Updater Configuration Script     ${NC}"
echo -e "${GREEN}======================================================${NC}"
echo ""

# Check for required utilities
echo -e "${YELLOW}Checking for required utilities...${NC}"
if ! command -v jq &> /dev/null; then
    echo -e "${RED}Error: jq is not installed. Please install jq to continue.${NC}"
    echo "On Ubuntu/Debian: sudo apt install jq"
    echo "On macOS with Homebrew: brew install jq"
    exit 1
fi

if ! command -v curl &> /dev/null; then
    echo -e "${RED}Error: curl is not installed. Please install curl to continue.${NC}"
    echo "On Ubuntu/Debian: sudo apt install curl"
    echo "On macOS with Homebrew: brew install curl"
    exit 1
fi

echo -e "${GREEN}All required utilities are installed.${NC}"
echo ""

# Get Drive file details
echo -e "${YELLOW}Please provide the Google Drive APK file ID:${NC}"
echo "(This is the long string after /d/ in your Google Drive share link)"
read -p "APK File ID: " APK_FILE_ID

echo -e "${YELLOW}Please provide the current version information:${NC}"
read -p "Version Name (e.g., 1.2.0): " VERSION_NAME
read -p "Version Code (e.g., 120): " VERSION_CODE

echo -e "${YELLOW}Do you want to force users to update? (y/n):${NC}"
read -p "Force Update: " FORCE_UPDATE_INPUT
if [[ $FORCE_UPDATE_INPUT == "y" || $FORCE_UPDATE_INPUT == "Y" ]]; then
    FORCE_UPDATE=true
else
    FORCE_UPDATE=false
fi

echo -e "${YELLOW}Enter minimum supported version code (earlier versions will be forced to update):${NC}"
read -p "Min Version Code: " MIN_VERSION

echo -e "${YELLOW}Enter release notes (use \\n for line breaks):${NC}"
read -p "Release Notes: " RELEASE_NOTES

# Create update info JSON
echo -e "${YELLOW}Creating update JSON file...${NC}"
DOWNLOAD_URL="https://drive.google.com/uc?export=download&id=${APK_FILE_ID}"
UPDATE_DATE=$(date +"%Y-%m-%d")

# Create JSON with jq
JSON_CONTENT=$(jq -n \
    --arg vn "$VERSION_NAME" \
    --argjson vc "$VERSION_CODE" \
    --arg du "$DOWNLOAD_URL" \
    --arg rn "$RELEASE_NOTES" \
    --arg ud "$UPDATE_DATE" \
    --argjson fu "$FORCE_UPDATE" \
    --argjson mv "$MIN_VERSION" \
    '{
        versionName: $vn,
        versionCode: $vc,
        downloadUrl: $du,
        releaseNotes: $rn,
        updateDate: $ud,
        forceUpdate: $fu,
        minSupportedVersion: $mv
    }')

echo "$JSON_CONTENT" > update-info.json
echo -e "${GREEN}Created update-info.json file.${NC}"

echo ""
echo -e "${YELLOW}Do you want to upload the update-info.json to Google Drive? (y/n):${NC}"
read -p "Upload: " UPLOAD_INPUT

if [[ $UPLOAD_INPUT == "y" || $UPLOAD_INPUT == "Y" ]]; then
    echo -e "${RED}To upload to Google Drive, you'll need to:${NC}"
    echo "1. Go to drive.google.com"
    echo "2. Upload the 'update-info.json' file manually"
    echo "3. Make sure the file is publicly accessible (Anyone with the link can view)"
    echo "4. Copy the file ID from the sharing link"
    echo ""
    echo -e "${GREEN}The update-info.json file has been created in the current directory.${NC}"
else
    echo -e "${GREEN}The update-info.json file has been created in the current directory.${NC}"
fi

echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Upload the update-info.json file to Google Drive or your server"
echo "2. Make the file publicly accessible"
echo "3. Copy the file ID from the sharing link"
echo "4. Update the UPDATE_INFO_URL constant in AppUpdateManager.kt with your JSON file's URL"
echo "   (line 47: private const val UPDATE_INFO_URL = \"https://drive.google.com/uc?export=download&id=YOUR_JSON_FILE_ID\")"
echo ""
echo -e "${GREEN}Setup complete!${NC}"