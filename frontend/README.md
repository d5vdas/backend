# Frontend (Android, Jetpack Compose)

This is a minimal Android frontend connected to backend APIs.

## Features implemented
- Register
- Login
- Persist JWT token (DataStore)
- Fetch profile (`/users/me`)
- Fetch my activities (`/activities/me`)
- Logout

## Backend URL used
- Emulator base URL: `http://10.0.2.2:8083`
- Change in: `app/src/main/java/com/runrunrun/frontend/data/ApiClient.kt`

## Run in Android Studio
1. Open Android Studio
2. Open folder: `frontend`
3. Let Gradle sync complete
4. Start backend on port 8083
5. Run app on emulator

## Notes
- Ensure backend is running before login/register.
- If using physical device, replace base URL with your machine LAN IP.
