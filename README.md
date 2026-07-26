# PCBoard Keyboard

PCBoard is an original Android input method focused on modern everyday typing and desktop-style productivity. It keeps a familiar mobile layout while adding dedicated **Ctrl** and **Tab** keys, editing tools and task-specific profiles.

## Version 2.0

### Typing and layout
- QWERTY, number, phone, email, URL and symbols layouts
- Dedicated Ctrl and Tab
- Shift, double-tap Caps Lock and visible modifier states
- Automatic capitalisation and double-space full stop
- Adaptive Enter action
- Long-press symbols on every letter and number key
- Accented-character alternatives and visible symbol hints
- Key preview popups
- Hold Backspace to repeat and swipe left to delete a word
- Swipe across Space to move the cursor
- Swipe down to hide the keyboard
- Adjustable keyboard height from 90% to 180%, with a taller 118% default
- Configurable bottom safety gap to avoid Android navigation and keyboard controls
- Number row, themes, vibration and sound controls
- One-handed left, right and compact modes
- Editable toolbar order, bottom modifier and punctuation keys

### Smart typing
- Offline glide typing with ranked word candidates
- 1,600+ word offline starter lexicon
- Prefix and next-word suggestions
- Ranked autocorrection with Damerau-Levenshtein distance
- Transposed-letter and nearby-key correction
- Local personal word and phrase learning
- British, Canadian, US and Nigerian English variants
- Nigerian Pidgin starter vocabulary
- Emoji suggestions and autocorrection undo

### Productivity and rich input
- Clipboard history captured only when its panel is opened
- One-hour expiry for unpinned clipboard items
- Cut, copy, paste, select all, undo and redo
- Arrow keys, forward delete and Escape
- Terminal, coding and spreadsheet profiles
- Automatic profile detection by foreground app package
- Quick-text snippets editable by the user
- Emoji and kaomoji panel
- Clipboard image and rich-content insertion in compatible applications
- Android system voice typing, with partial results when supported
- Android 12+ system translation for selected text or the current sentence when available
- Locally trainable handwriting gestures for letters, words, symbols and shortcuts

### Privacy and accessibility
- No PCBoard internet permission and no PCBoard cloud account
- Microphone permission is requested only for voice typing
- No typed-text upload by PCBoard
- No learning in password fields
- Respects `IME_FLAG_NO_PERSONALIZED_LEARNING`
- Incognito mode
- Clear learned data, clipboard history and handwriting training
- Individual key focus, content descriptions and 48dp minimum touch targets

## Intentionally excluded
Version 2.0 does not add multiple language packs, encrypted cloud synchronisation, AI proofreading or rewriting, or a downloadable model manager.

## Build the APK
Open **Actions**, select **Build APK**, run the workflow, then download the `PCBoard-debug-apk` artifact.

## Capability notes
- Glide typing is an offline lexicon-based decoder, not a proprietary neural model.
- Voice typing depends on the speech-recognition service installed on the device. That system service may use on-device or network processing.
- Translation requires Android 12 or later and an available device translation service.
- Handwriting recognises gestures trained locally by the user. It is not general handwriting OCR.
- Image or rich-content insertion works only when the receiving application advertises a compatible MIME type.
- PCBoard does not claim feature parity with Gboard, SwiftKey or Samsung Keyboard.
