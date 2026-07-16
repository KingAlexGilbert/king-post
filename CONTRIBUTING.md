# Contributing to King Post

Thank you for your interest in improving King Post.

## Before You Begin

King Post currently follows a lightweight, local-first design. Contributions involving platform APIs, accounts, cloud services, or server components are welcome for discussion, but they should be proposed in a feature request before development begins because they may significantly change the project's architecture, privacy model, and maintenance requirements.

For major changes, open a feature request before beginning substantial work.

## Development Setup

1. Fork or clone the repository.
2. Open the project in Android Studio.
3. Allow Gradle to sync.
4. Build the debug APK using `Build → Generate App Bundles or APKs → Build APK(s)`.
5. Find the generated APK at `app/build/outputs/apk/debug/app-debug.apk`.

## Testing

Before submitting a pull request:

- confirm that the app builds successfully
- test video selection and preview
- test caption copying and clearing
- test sharing to any affected platform apps
- confirm that tapping outside the caption field clears focus without blocking button presses
- confirm that temporary video preparation still works
- verify that existing behavior has not changed unintentionally

Platform behavior can vary by device, Android version, and installed app version. Include those details in your testing notes.

## Pull Requests

Keep pull requests focused and describe:

- what changed
- why the change is useful
- how it was tested
- which devices, Android versions, and platform-app versions were used

Do not include unrelated formatting changes or generated build output.

## Do Not Commit

Never commit:

- APK or AAB build files
- signing keys or keystores
- passwords, tokens, or credentials
- `local.properties`
- personal videos, screenshots, or account information
- generated `build/` folders
- private service configuration files

## Code and UI Guidelines

- Preserve the existing local-first workflow unless a proposed architectural change has been discussed and approved.
- Keep the interface simple and lightweight.
- Avoid adding permissions unless they are genuinely required.
- Do not add tracking, analytics, advertisements, or telemetry.
- Keep user-facing text clear and accurate.
- Avoid changing unrelated functionality in narrowly scoped fixes.

## License

By contributing, you agree that your contribution may be distributed under the GNU General Public License v3.0.