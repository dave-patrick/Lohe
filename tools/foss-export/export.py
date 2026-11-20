#!/usr/bin/env python3
"""Build the open-source (FOSS) edition of Lohe from the private Play Edition.

Usage: python3 -I tools/foss-export/export.py --play PATH/TO/PLAY-CHECKOUT --out PATH/TO/PUBLIC-CHECKOUT

The Play Edition (built in Google AI Studio) is the main codebase. This script:
  1. Replaces the public checkout's app code with the Play Edition's, keeping the
     public-only files listed in KEEP (CI, docs, wrapper, this tool, ...).
  2. Leaves out Pro code, debug-only owner tools, AI Studio/Firebase config.
  3. Patches the build so it has no Google Play Billing, Google sign-in or
     Firebase, with Edition.HAS_PRO = false and its own application ID.
  4. Fails loudly if anything it expects has moved, or if anything private leaks.

Every patch must match exactly once. When AI Studio restructures a file and a
patch stops matching, the export stops instead of publishing something wrong.
"""
import argparse
import os
import re
import shutil
import sys
from pathlib import Path

FOSS_APPLICATION_ID = "com.lohe.app.foss"
# Name shown on the launcher and in Android's app list, so the editions are easy to tell apart.
FOSS_LAUNCHER_LABEL = "Lohe (FOSS)"

# Paths owned by the public repository; the export never deletes or overwrites them.
KEEP = {
    ".git", ".github", ".gitattributes", "docs", "tools", "README.md", "LICENSE",
    "AGENTS.md", ".herald", "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar",
}

# Paths in the Play Edition that never go public.
EXCLUDE = {
    ".git", "app/src/main/java/com/example/pro", "app/src/debug",
    "app/src/testDebug", "firebase-applet-config.json", "metadata.json", ".env", ".env.example",
    "public", "local.properties", "google-services.json", "app/google-services.json",
    "my-upload-key.jks", "debug.keystore", ".idea", "build", "app/build", ".gradle", ".kotlin",
}
EXCLUDE_SUFFIXES = (".jks", ".keystore", ".p12")

# Text that must never appear in the public output.
LEAK_PATTERNS = [
    (r"com\.example\.pro\.(?!ProFeaturesImpl\b)", "a reference into the Pro package"),
    (r"com\.android\.billingclient", "Google Play Billing"),
    (r"com\.google\.android\.gms(?!\.car\.)", "Google Play services"),
    (r"googleusercontent\.com", "an OAuth client ID"),
    (r"AIza[0-9A-Za-z_\-]{30,}", "a Google API key"),
    (r"GEMINI_API_KEY", "a Gemini key reference"),
    (r"libs\.android\.billing|libs\.play\.services\.(?!location)", "a Google dependency"),
    (r"com\.example\.dev\.|DebugDevTools|LocalDebugApiServer|DiagnosticsReportManager|PocketCastsJsonImporter",
     "owner-only debug tools (these belong in app/src/testDebug, not app/src/test)"),
]


def fail(msg):
    sys.exit(f"FOSS export failed: {msg}")


def rel_matches(rel, names):
    return any(rel == n or rel.startswith(n + "/") for n in names)


def clear_output(out):
    for path in sorted(out.rglob("*"), key=lambda p: len(p.parts), reverse=True):
        rel = path.relative_to(out).as_posix()
        if rel_matches(rel, KEEP) or any(k.startswith(rel + "/") for k in KEEP):
            continue
        if path.is_dir() and not path.is_symlink():
            if not any(path.iterdir()):
                path.rmdir()
        else:
            path.unlink()


def copy_play(play, out):
    for root, dirs, files in os.walk(play):
        root_rel = Path(root).relative_to(play).as_posix()
        root_rel = "" if root_rel == "." else root_rel
        dirs[:] = [d for d in dirs if not rel_matches(f"{root_rel}/{d}".lstrip("/"), EXCLUDE)]
        for name in files:
            rel = f"{root_rel}/{name}".lstrip("/")
            if rel_matches(rel, EXCLUDE) or name.endswith(EXCLUDE_SUFFIXES):
                continue
            if rel_matches(rel, KEEP):
                continue  # public copy wins
            src = Path(root) / name
            if src.is_symlink():
                fail(f"symbolic link in Play Edition: {rel}")
            dst = out / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)


def sub_once(text, pattern, repl, label, flags=0):
    new, n = re.subn(pattern, repl, text, count=0, flags=flags)
    if n != 1:
        fail(f"patch '{label}' matched {n} times (expected 1); the Play Edition changed shape")
    return new


def remove_block(text, header, label):
    """Remove `header { ... }` (brace-matched) plus its trailing newline."""
    m = list(re.finditer(header + r"\s*\{", text))
    if len(m) != 1:
        fail(f"patch '{label}' found {len(m)} blocks (expected 1)")
    start, i, depth = m[0].start(), m[0].end(), 1
    while depth:
        if i >= len(text):
            fail(f"patch '{label}': unbalanced braces")
        depth += {"{": 1, "}": -1}.get(text[i], 0)
        i += 1
    line_start = text.rfind("\n", 0, start) + 1
    end = text.find("\n", i)
    end = len(text) if end == -1 else end + 1
    return text[:line_start] + text[end:]


def patch(path, fn):
    if not path.exists():
        fail(f"missing {path}")
    path.write_text(fn(path.read_text()))


def patch_app_gradle(t):
    t = sub_once(t, r"^import com\.google\.gms\..*\n", "", "google-services import", re.M)
    t = sub_once(t, r"^\s*alias\(libs\.plugins\.secrets\)\n", "", "secrets plugin", re.M)
    t = sub_once(t, r"^\s*alias\(libs\.plugins\.google\.services\)\n", "", "google-services plugin", re.M)
    t = sub_once(t, r'applicationId = "[^"]+"', f'applicationId = "{FOSS_APPLICATION_ID}"', "applicationId")
    t = sub_once(
        t,
        r"\n\s*val appletConfigFile = .*?buildConfigField\(\"String\", \"OAUTH_CLIENT_ID\", \"\\\"\$oauthClientId\\\"\"\)",
        '\n    buildConfigField("String", "OAUTH_CLIENT_ID", "\\"\\"")',
        "OAuth client ID",
        re.S,
    )
    t = sub_once(t, r"(?ms)^\s*//[^\n]*\n\s*//[^\n]*\n\s*signingConfigs \{", "  signingConfigs {", "signing comment")
    t = remove_block(t, r"(?m)^\s*signingConfigs", "signing configs")
    t = sub_once(t, r"(?m)^\s*//[^\n]*\n\s*//[^\n]*AI Studio\.\n\s*signingConfig = signingConfigs\.getByName\(\"release\"\)\n",
                 "", "release signing assignment")
    t = sub_once(t, r"(?m)^\s*//[^\n]*\n\s*//[^\n]*AI Studio\.\n\s*signingConfig = signingConfigs\.getByName\(\"debugConfig\"\)\n",
                 "", "debug signing assignment")
    t = sub_once(t, r"includeInBundle = true", "includeInBundle = false", "dependency metadata")
    t = sub_once(t, r"(?m)^// Configure the Secrets Gradle Plugin.*\n// to match.*\n", "", "secrets comment")
    t = remove_block(t, r"(?m)^secrets", "secrets block")
    t = sub_once(t, r"(?m)^googleServices \{.*\}\n", "", "googleServices block")
    t = sub_once(t, r"(?m)^\s*implementation\(libs\.android\.billing\.ktx\)\n", "", "billing dependency")
    t = sub_once(t, r"(?m)^\s*implementation\(libs\.play\.services\.auth\)\n", "", "play-services-auth dependency")
    # Optional local release signing for people building their own FOSS APKs.
    t = sub_once(
        t, r"(?m)^([ \t]*)buildTypes \{",
        "\\1// Release signing is optional: set LOHE_KEYSTORE, LOHE_KEYSTORE_PASSWORD,\n"
        "\\1// LOHE_KEY_ALIAS and LOHE_KEY_PASSWORD to sign; otherwise the release APK is unsigned.\n"
        "\\1val foss = System.getenv(\"LOHE_KEYSTORE\")\n"
        "\\1if (foss != null) {\n"
        "\\1  signingConfigs.create(\"release\") {\n"
        "\\1    storeFile = file(foss)\n"
        "\\1    storePassword = System.getenv(\"LOHE_KEYSTORE_PASSWORD\")\n"
        "\\1    keyAlias = System.getenv(\"LOHE_KEY_ALIAS\")\n"
        "\\1    keyPassword = System.getenv(\"LOHE_KEY_PASSWORD\")\n"
        "\\1  }\n"
        "\\1}\n\n"
        "\\1buildTypes {",
        "buildTypes header",
    )
    t = sub_once(t, r"(?m)^([ \t]*)release \{\n",
                 "\\1release {\n\\1  if (foss != null) signingConfig = signingConfigs.getByName(\"release\")\n",
                 "release signing hook")
    return t


def patch_root_gradle(t):
    t = sub_once(t, r"(?m)^\s*alias\(libs\.plugins\.secrets\) apply false\n", "", "root secrets plugin")
    t = sub_once(t, r"(?m)^\s*alias\(libs\.plugins\.google\.services\) apply false\n", "", "root google-services plugin")
    return t


def patch_manifest(t):
    t = sub_once(t, r"(?m)^\s*<uses-permission android:name=\"android\.permission\.SYSTEM_ALERT_WINDOW\" />\n",
                 "", "overlay permission")
    t = sub_once(t, r"(?s)\n\s*<service\s+android:name=\"\.pro\.[^\"]+\"[^>]*?/>\n", "\n", "Pro overlay service")
    if ".pro." in t:
        fail("manifest still references a Pro component")
    t, n = re.subn(r'android:label="@string/app_name"', 'android:label="@string/foss_launcher_label"', t)
    if n == 0:
        fail("patch 'launcher label' found no android:label=\"@string/app_name\"")
    return t


def write_foss_resources(out):
    values = out / "app/src/main/res/values/foss_edition.xml"
    if values.exists():
        fail(f"{values.relative_to(out)} already exists in the Play Edition")
    label = FOSS_LAUNCHER_LABEL.replace("&", "&amp;").replace("<", "&lt;")
    values.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generated by tools/foss-export. -->\n"
        f'<resources>\n    <string name="foss_launcher_label">{label}</string>\n</resources>\n'
    )


def check_leaks(out):
    problems = []
    for path in out.rglob("*"):
        rel = path.relative_to(out).as_posix()
        if path.is_dir() or rel_matches(rel, KEEP):  # only check what came from the Play Edition
            continue
        if path.suffix not in {".kt", ".kts", ".xml", ".toml", ".properties", ".json", ".pro", ".txt", ".md", ".yml", ".yaml"}:
            continue
        text = path.read_text(errors="ignore")
        if rel == "gradle/libs.versions.toml":
            continue  # version catalog may list unused libraries
        for pattern, what in LEAK_PATTERNS:
            if re.search(pattern, text):
                problems.append(f"{rel}: {what}")
    if (out / "app/src/main/java/com/example/pro").exists():
        problems.append("Pro package was copied")
    if problems:
        fail("private material found:\n  " + "\n  ".join(sorted(set(problems))))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--play", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    a = ap.parse_args()
    play, out = a.play.resolve(), a.out.resolve()
    if not (play / "app/src/main/java/com/example/edition/Edition.kt").exists():
        fail("Play Edition checkout not found or has no Edition.kt")
    if not (out / ".git").exists():
        fail("--out must be the public repository checkout")
    clear_output(out)
    copy_play(play, out)
    patch(out / "app/src/main/java/com/example/edition/Edition.kt",
          lambda t: sub_once(t, r"const val HAS_PRO = true", "const val HAS_PRO = false", "Edition.HAS_PRO"))
    patch(out / "app/build.gradle.kts", patch_app_gradle)
    patch(out / "build.gradle.kts", patch_root_gradle)
    patch(out / "app/src/main/AndroidManifest.xml", patch_manifest)
    write_foss_resources(out)
    check_leaks(out)
    print("FOSS export complete.")


if __name__ == "__main__":
    main()
