# PCBoard Keyboard

PCBoard is a customised LeanType keyboard for Android. LeanType remains the typing engine, settings application and feature set. PCBoard changes the product identity and the primary keyboard geometry.

## Version 3.0.1, LeanType edition

The build retains LeanType's existing implementation of:

- dictionary suggestions, autocorrection and next-word prediction
- personal dictionaries and learned words
- gesture typing
- clipboard history, search, pinning and text expansion
- emoji search and suggestions through the toolbar
- text-editing and touchpad modes
- one-handed, split and floating layouts
- Material You themes and custom colours
- backup and restore
- downloads, plugins and network features
- Gemini-powered AI features available in LeanType's `standardfull` flavour
- handwriting support included by the `standardfull` flavour

AI features require the relevant LeanType configuration and a supported API key or service. Network features operate only when the device has an internet connection.

## PCBoard main layout

```text
Optional LeanType number row

q  w  e  r  t  y  u  i  o  p

Tab  a  s  d  f  g  h  j  k  l

Shift  z  x  c  v  b  n  m  Backspace

Ctrl  ?123  ,       Space       .  Enter
```

Layout rules:

- Tab is a small key to the left of A.
- A shifts slightly to the right to accommodate Tab.
- Shift appears once, at the left of Z.
- Backspace is at the right of M.
- The permanent `!` and `?` keys are removed from the alphabet view.
- Ctrl is the first key on the bottom row.
- `?123` follows Ctrl.
- Comma follows `?123`.
- The spacebar is reduced to provide room for Ctrl.
- Period remains before Enter.
- Enter is a standard-sized adaptive action key at the lower right.
- The permanent emoji key is removed from the bottom row. Emoji remains available through LeanType's toolbar.

All other LeanType defaults, settings and features are left unchanged.

## Internet and AI build

PCBoard uses LeanType's `standardfullDebug` build variant. The APK includes `android.permission.INTERNET` for:

- dictionary and resource downloads
- plugin-related network functions
- supported AI features
- other network functions already implemented by LeanType

The keyboard does not add a separate PCBoard server.

## Reproducible build

The repository stores a pinned LeanType commit and a small PCBoard overlay rather than duplicating the full upstream source tree.

The GitHub workflow:

1. fetches the exact LeanType commit recorded in `upstream/LEANTYPE_COMMIT`
2. applies `scripts/apply_pcboard_overlay.py`
3. verifies that LeanType defaults remain unchanged
4. verifies the approved PCBoard layout assets
5. compiles LeanType's test sources
6. builds `standardfullDebug`
7. verifies the PCBoard package, version and internet permission
8. uploads the APK, reports and SHA-256 checksum

## Build locally

```bash
git clone https://github.com/Oshione2002/PCBoardKeyboard.git
cd PCBoardKeyboard

UPSTREAM_COMMIT=$(cat upstream/LEANTYPE_COMMIT)
git clone https://github.com/LeanBitLab/LeanType.git leantype
git -C leantype checkout "$UPSTREAM_COMMIT"
python3 scripts/apply_pcboard_overlay.py leantype
cd leantype
./gradlew :app:assembleStandardfullDebug
```

## Licence and attribution

PCBoard modifications are distributed under GPL-3.0. LeanType, HeliBoard, OpenBoard and AOSP notices must remain available under their applicable licences.
