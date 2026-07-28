# PCBoard Keyboard

PCBoard is a fully offline Android keyboard focused on comfortable mobile typing and desktop-style productivity. Version 3.0 replaces the earlier experimental keyboard engine with a reproducible fork overlay based on LeanType, HeliBoard, OpenBoard and AOSP LatinIME.

## Version 3.0 foundation

The foundation build inherits the mature upstream implementation of:

- offline dictionary suggestions and autocorrection
- next-word prediction and personal dictionaries
- gesture typing with a local fallback engine
- clipboard history, search, pinning and text expansion
- emoji search and suggestion support
- text-editing and touchpad modes
- one-handed, split and floating layouts
- Material You themes and custom colours
- backup and restore
- password-field and incognito protections

PCBoard adds these defaults and modifications:

- package name `com.treasure.pcboard`
- PCBoard branding and adaptive launcher icon
- offline-lite build with no `INTERNET` permission
- Ctrl and Tab integrated into the main QWERTY layout
- long-press numbers, symbols and accented characters
- number row enabled by default
- split toolbar and suggestions enabled by default
- autocorrection enabled by default
- taller portrait and landscape keyboard defaults
- height control extended to 60%–190%
- increased bottom safety padding for Android navigation and keyboard controls
- network-dependent download controls disabled

## Main layout

```text
q  w  e  r  t  y  u  i  o  p

⇥  a  s  d  f  g  h  j  k  l

Ctrl  z  x  c  v  b  n  m
```

LeanType's layout engine adds Shift, Backspace, symbols, comma, space, period and the adaptive Enter key around these rows.

## Reproducible upstream build

The repository stores a pinned LeanType commit and a small reviewed PCBoard overlay rather than copying hundreds of megabytes of generated and upstream files.

The GitHub workflow:

1. fetches the exact commit in `upstream/LEANTYPE_COMMIT`
2. applies `scripts/apply_pcboard_overlay.py`
3. runs LeanType's unit tests
4. runs Android lint
5. builds the `offlineliteDebug` APK
6. rejects an APK that requests `android.permission.INTERNET`
7. uploads the APK and SHA-256 checksum

## Build locally

```bash
git clone https://github.com/Oshione2002/PCBoardKeyboard.git
cd PCBoardKeyboard

UPSTREAM_COMMIT=$(cat upstream/LEANTYPE_COMMIT)
git clone https://github.com/LeanBitLab/LeanType.git leantype
git -C leantype checkout "$UPSTREAM_COMMIT"
python3 scripts/apply_pcboard_overlay.py leantype
cd leantype
./gradlew :app:testOfflineliteDebugUnitTest :app:lintOfflineliteDebug :app:assembleOfflineliteDebug
```

## Licence and attribution

PCBoard's fork modifications are distributed under GPL-3.0. LeanType, HeliBoard and OpenBoard licence notices must remain available with redistributed source. AOSP-derived portions retain their applicable Apache 2.0 notices.

## Current stage

This branch is the PCBoard 3.0 foundation. The build must pass CI and be tested on the Tecno Camon 30 before it replaces the current main release. Later stages will refine the toolbar, productivity profiles, Nigerian English and Pidgin dictionaries, touch adaptation and optional specialised offline prediction.
