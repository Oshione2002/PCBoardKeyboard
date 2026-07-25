# PCBoard Keyboard

PCBoard is an original Android input method focused on modern everyday typing and desktop-style productivity. It keeps a familiar mobile layout while adding dedicated **Ctrl** and **Tab** keys, editing tools and task-specific profiles.

## Version 1.3 features

### Typing foundation
- QWERTY, number, phone, email, URL and symbols layouts
- Dedicated Ctrl and Tab
- Shift, double-tap Caps Lock and visible modifier states
- Automatic capitalisation
- Double-space full stop
- Adaptive Enter action
- Long-press accented characters and number shortcuts
- Key preview popups
- Hold Backspace to repeat
- Swipe Backspace left to delete a word
- Swipe across Space to move the cursor
- Swipe down to hide the keyboard

### Smart typing
- 1,600+ word offline starter lexicon
- Prefix suggestions
- Ranked autocorrection with Damerau-Levenshtein distance
- Transposed-letter correction
- Nearby-key correction boost
- Next-word suggestions
- Personal word and phrase learning stored locally
- British, Canadian, US and Nigerian English variants
- Nigerian Pidgin starter vocabulary
- Emoji suggestions for common words
- Undo the latest autocorrection with Backspace

### Productivity
- Clipboard history captured only when the clipboard panel is opened
- One-hour automatic expiry for unpinned clipboard items
- Cut, copy, paste, select all, undo and redo tools
- Arrow keys, forward delete and Escape
- Terminal profile
- Coding profile
- Spreadsheet profile
- Automatic profile detection by foreground app package
- One-handed left and right modes
- Compact mode
- Adjustable height, number row, themes, vibration and sound

### Privacy and accessibility
- No internet permission
- No typed-text upload
- No learning in password fields
- Respects `IME_FLAG_NO_PERSONALIZED_LEARNING`
- Incognito mode
- Clear learned data and clipboard history
- Individual key focus, content descriptions and 48dp minimum touch targets

## Build the APK

Open **Actions**, select **Build APK**, run the workflow, then download the `PCBoard-debug-apk` artifact.

## Scope notes

This release does not claim feature parity with Gboard, SwiftKey or Samsung Keyboard. It does not include cloud AI, cloud sync, full glide typing, voice dictation, translation, handwriting, GIF services or true free-floating IME windows. Compact mode narrows the docked keyboard but does not create an unrestricted overlay window.
