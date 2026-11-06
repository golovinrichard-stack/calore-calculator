# Releasing Calore

Calore is shared as an APK on GitHub. GitHub builds it for you:

- **Every pull request** runs the checks (unit tests, lint, debug build). The debug APK is attached to the run as `calore-debug-apk` (Actions tab → the run → Artifacts).
- **Every version tag** such as `v0.2.0` builds the release APK, signs it with your key and publishes it as a GitHub release.

## Once: create your signing key

Android only installs an update if it is signed with the same key as the installed app. So you create one key and keep it forever.

1. Create the keystore (you need a JDK; `keytool` comes with it). Choose a strong password and remember it:

   ```bash
   keytool -genkeypair -v -keystore calore-release.jks -alias calore -keyalg RSA -keysize 4096 -validity 10000
   ```

2. **Keep `calore-release.jks` and the password safe**, for example in a password manager, plus one offline copy. If you lose them, nobody can install updates over the old app anymore. Never commit the keystore (`.gitignore` already blocks `*.jks`).

3. Turn the keystore into text so GitHub can store it:

   ```bash
   base64 -w0 calore-release.jks > keystore.txt
   ```

   On macOS use `base64 -i calore-release.jks -o keystore.txt`. On Windows (PowerShell):
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("calore-release.jks")) > keystore.txt`

4. On GitHub open the repository → **Settings → Secrets and variables → Actions → New repository secret**, and add four secrets:

   | Name | Value |
   |---|---|
   | `CALORE_KEYSTORE_BASE64` | the whole content of `keystore.txt` |
   | `CALORE_KEYSTORE_PASSWORD` | the keystore password |
   | `CALORE_KEY_ALIAS` | `calore` (the `-alias` from step 1) |
   | `CALORE_KEY_PASSWORD` | the key password (the same as the keystore password, unless you chose another one) |

5. Delete `keystore.txt`.

## Every release

1. In `app/build.gradle.kts`, raise `versionCode` by one and set `versionName`, for example `versionCode = 3` and `versionName = "0.3.0"`. Merge that into `main`.
2. Tag the commit on `main` with the same version and push the tag:

   ```bash
   git tag v0.3.0
   ```

   ```bash
   git push origin v0.3.0
   ```

3. Wait for the **Release** workflow (Actions tab, about 10 minutes). It stops with a clear message if a secret is missing or if the tag doesn't match `versionName`.
4. The new release is under **Releases** with the file `calore-v0.3.0.apk`.

## Installing on a phone

Open the release page on the phone, download the APK and open it. The first time, Android asks to allow installs from the browser or the Files app. Installing a newer release keeps all data.

The first signed release can't be installed over a debug build or a build signed with another key: uninstall that first (make a backup in Me → Settings → Backup before).
