# Privacy

MinkLauncher Open does not operate a server, include analytics or advertising SDKs, create an account, or upload personal data. The application does not request Internet access.

The app stores its settings, to-dos, selected document-folder references, widget configuration, daily well-being preferences, and five most recent plain-text queries locally on the device. Android cloud backup is disabled for the application.

Optional permissions are used as follows:

- Contacts: searches contact names and phone numbers locally for `@` messaging and `#` calling commands.
- Phone: places a call only after the user selects a contact and confirms the call in MinkLauncher Open.
- SMS: sends a carrier SMS only after the user selects a recipient and submits a message. Direct SMS is available only while MinkLauncher Open is the active Android assistant handler. The alternative messaging-app action leaves the final send inside the selected provider.
- Photos, videos, audio, and older shared-storage access: searches media filenames locally and displays local thumbnails.
- All files access: optionally expands document filename search beyond user-selected folders. It is disabled unless the user explicitly enables the special access in Android settings.
- Notification shade: permits the launcher’s swipe-down gesture to expand Android’s notification panel.
- Notification access: reads active conversation notifications in memory so messages can be grouped and replied to through the originating app. Non-message notifications are ignored, and conversation history is not stored by MinkLauncher Open.
- Usage access: reads Android app-activity and screen-time events while MinkLauncher Open is visible to calculate the current Mink’s Day state and local observations. The app stores the user’s goal and category choices, but does not create a separate usage-history database.
- Accessibility: the optional **MinkLauncher Open - Double Tap to Lock Screen** service performs only Android's Lock screen global action after the user double-taps empty Home space. It does not subscribe to accessibility events, retrieve window content, perform gestures, or collect data.

Document search can use folders selected through Android’s Storage Access Framework or the optional Android All files special access. Folder references and filenames are processed locally.

When selected as the Android assistant, MinkLauncher Open opens its keyboard-first Magic Box over the current app. It does not request microphone access, inspect assist context, read the underlying screen, or collect information from the app underneath it.

When the user launches another app, composes a message, creates a note or event, opens a file, or delegates a web query, Android hands the requested content to the app selected by the user. That destination app’s privacy practices then apply.

Privacy questions can be sent to contact@katoaapps.com.
