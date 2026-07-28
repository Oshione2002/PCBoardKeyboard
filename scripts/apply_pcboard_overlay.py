#!/usr/bin/env python3
"""Apply the PCBoard product and main-layout overlay to pinned LeanType source.

LeanType remains the keyboard engine and feature set. PCBoard changes the package,
branding and primary keyboard geometry only.
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
    replace_required(
        build,
        'applicationId = "com.leanbitlab.leantype"',
        'applicationId = "com.treasure.pcboard"',
    )
    regex_required(build, r"versionCode\s*=\s*\d+", "versionCode = 3001")
    regex_required(
        build,
        r'versionName\s*=\s*"[^"]+"',
        'versionName = "3.0.1-layout"',
    )
    replace_required(build, "$number-LeanType_", "$number-PCBoard_")

    # Keep LeanType's dictionaries, prediction, correction, toolbar, clipboard,
    # themes, downloads and AI behaviour. Replace only the primary QWERTY and
    # functional-key geometry approved for PCBoard.
    shutil.copyfile(
        ROOT / "overlays/qwerty.txt",
        upstream / "app/src/main/assets/layouts/main/qwerty.txt",
    )
    for target in ("functional_keys.json", "functional_keys_tablet.json"):
        shutil.copyfile(
            ROOT / "overlays/functional_keys.json",
            upstream / f"app/src/main/assets/layouts/functional/{target}",
        )

    # Rebrand visible product strings while retaining LeanType's source packages.
    for strings in (upstream / "app/src/main/res").glob("values*/strings.xml"):
        text = strings.read_text(encoding="utf-8")
        text = text.replace("LeanType", "PCBoard")
        text = text.replace("Leantype", "PCBoard")
        strings.write_text(text, encoding="utf-8")

    # PCBoard adaptive launcher mark. Legacy launcher assets remain available for
    # old Android versions.
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

    (upstream / "PCBOARD_FORK_NOTICE.md").write_text(
        "# PCBoard fork notice\n\n"
        "PCBoard Keyboard is built from LeanType, HeliBoard, OpenBoard and AOSP "
        "LatinIME under their respective licences. PCBoard modifications are "
        "distributed under GPL-3.0. The pinned upstream revision is recorded in "
        "the PCBoard build-overlay repository.\n",
        encoding="utf-8",
    )

    print("PCBoard layout overlay applied successfully")


if __name__ == "__main__":
    main()
