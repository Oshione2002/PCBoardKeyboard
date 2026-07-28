#!/usr/bin/env python3
"""Apply the PCBoard product overlay to a pinned LeanType checkout.

The upstream source remains GPL-3.0. This script keeps the fork reproducible while
avoiding an unreviewable vendored copy in this repository.
"""
from __future__ import annotations

import re
import shutil
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise SystemExit(message)


def replace_required(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        fail(f"Expected text not found in {path}: {old!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def regex_required(path: Path, pattern: str, replacement: str, flags: int = 0) -> None:
    text = path.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, text, flags=flags)
    if count == 0:
        fail(f"Expected pattern not found in {path}: {pattern}")
    path.write_text(updated, encoding="utf-8")


def main() -> None:
    if len(sys.argv) != 2:
        fail("Usage: apply_pcboard_overlay.py <LeanType checkout>")

    upstream = Path(sys.argv[1]).resolve()
    if not (upstream / "app" / "build.gradle.kts").exists():
        fail(f"Not a LeanType checkout: {upstream}")

    build = upstream / "app" / "build.gradle.kts"
    replace_required(build, 'applicationId = "com.leanbitlab.leantype"', 'applicationId = "com.treasure.pcboard"')
    regex_required(build, r"versionCode\s*=\s*\d+", "versionCode = 3000")
    regex_required(build, r'versionName\s*=\s*"[^"]+"', 'versionName = "3.0.0-foundation"')
    regex_required(
        build,
        r'(create\("offlinelite"\)\s*\{.*?dimension\s*=\s*"privacy"\s*)applicationIdSuffix\s*=\s*"\.offlinelite"',
        r"\1// PCBoard uses the base application id for its offline-only release\n            minSdk = 26",
        flags=re.S,
    )
    replace_required(build, "$number-LeanType_", "$number-PCBoard_")

    defaults = upstream / "app/src/main/java/helium314/keyboard/latin/settings/Defaults.kt"
    replacements = {
        "const val PREF_SPLIT_TOOLBAR = false": "const val PREF_SPLIT_TOOLBAR = true",
        "const val PREF_SHOW_DOWNLOAD_BUTTON_IN_TOOLBAR = true": "const val PREF_SHOW_DOWNLOAD_BUTTON_IN_TOOLBAR = false",
        "const val PREF_AUTO_CORRECTION = false": "const val PREF_AUTO_CORRECTION = true",
        "val PREF_KEYBOARD_HEIGHT_SCALE = Array(2) { 0.77f }": "val PREF_KEYBOARD_HEIGHT_SCALE = Array(2) { 1.12f }",
        "val PREF_BOTTOM_PADDING_SCALE = arrayOf(1.05f, 0f)": "val PREF_BOTTOM_PADDING_SCALE = arrayOf(1.20f, 0.15f)",
        "const val PREF_FONT_SCALE = 0.85f": "const val PREF_FONT_SCALE = 0.95f",
        "const val PREF_SHOW_NUMBER_ROW = false": "const val PREF_SHOW_NUMBER_ROW = true",
        "const val PREF_SHOW_NUMBER_ROW_HINTS = false": "const val PREF_SHOW_NUMBER_ROW_HINTS = true",
        "const val PREF_SHOW_POPUP_HINTS = false": "const val PREF_SHOW_POPUP_HINTS = true",
        "const val PREF_DISABLE_NETWORK = false": "const val PREF_DISABLE_NETWORK = true",
        "const val PREF_DONT_SHOW_SPONSOR_DIALOG = false": "const val PREF_DONT_SHOW_SPONSOR_DIALOG = true",
    }
    for old, new in replacements.items():
        replace_required(defaults, old, new)

    appearance = upstream / "app/src/main/java/helium314/keyboard/settings/screens/AppearanceScreen.kt"
    replace_required(appearance, "range = 0.3f..1.5f,", "range = 0.6f..1.9f,")

    # LeanType injects Shift, Backspace, symbols, comma, space, period and the
    # adaptive action key around these character rows.
    shutil.copyfile(
        ROOT / "overlays/qwerty.txt",
        upstream / "app/src/main/assets/layouts/main/qwerty.txt",
    )

    # Rebrand user-visible strings while preserving upstream package namespaces.
    for strings in (upstream / "app/src/main/res").glob("values*/strings.xml"):
        text = strings.read_text(encoding="utf-8")
        text = text.replace("LeanType", "PCBoard")
        text = text.replace("Leantype", "PCBoard")
        strings.write_text(text, encoding="utf-8")

    manifest = upstream / "app/src/main/AndroidManifest.xml"
    manifest_text = manifest.read_text(encoding="utf-8")
    manifest_text = manifest_text.replace('android:usesCleartextTraffic="true"', 'android:usesCleartextTraffic="false"')
    manifest.write_text(manifest_text, encoding="utf-8")

    # Modern PCBoard adaptive icon for Android 8+. Legacy raster assets remain
    # only as a compatibility fallback for older Android versions.
    drawable = upstream / "app/src/main/res/drawable"
    (drawable / "ic_pcboard_mark.xml").write_text(
        '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF"
        android:pathData="M17,29h74a7,7 0,0 1,7 7v39a7,7 0,0 1,-7 7h-74a7,7 0,0 1,-7 -7v-39a7,7 0,0 1,7 -7zM22,39h10v9h-10zM36,39h10v9h-10zM50,39h10v9h-10zM64,39h10v9h-10zM78,39h8v9h-8zM22,52h12v9h-12zM38,52h12v9h-12zM54,52h12v9h-12zM70,52h16v9h-16zM22,65h12v8h-12zM38,65h35v8h-35zM77,65h9v8h-9z"/>
    <path android:fillColor="#FF4F46E5"
        android:pathData="M46,34h19v25h-6v-7h-7v7h-6zM52,40v6h7v-6z"/>
</vector>
''',
        encoding="utf-8",
    )
    (drawable / "ic_launcher_background.xml").write_text(
        '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#FF4F46E5" />
</shape>
''',
        encoding="utf-8",
    )
    (drawable / "ic_launcher_foreground_scaled.xml").write_text(
        '''<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:drawable="@drawable/ic_pcboard_mark"
    android:insetLeft="7dp" android:insetRight="7dp"
    android:insetTop="7dp" android:insetBottom="7dp" />
''',
        encoding="utf-8",
    )
    (drawable / "ic_launcher_monochrome_vector.xml").write_text(
        (drawable / "ic_pcboard_mark.xml").read_text(encoding="utf-8"),
        encoding="utf-8",
    )

    notice = upstream / "PCBOARD_FORK_NOTICE.md"
    notice.write_text(
        "# PCBoard fork notice\n\n"
        "PCBoard Keyboard is built from LeanType, HeliBoard, OpenBoard and AOSP "
        "LatinIME under their respective licences. PCBoard modifications are "
        "distributed under GPL-3.0. The pinned upstream revision is recorded in "
        "the PCBoard build-overlay repository.\n",
        encoding="utf-8",
    )

    print("PCBoard overlay applied successfully")


if __name__ == "__main__":
    main()
