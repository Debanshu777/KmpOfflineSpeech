#!/usr/bin/env python3
"""Download pinned speech runtimes/models; Python 3.10+, no third-party packages."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import stat
import tarfile
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MAX_EXTRACT_BYTES = 3 * 1024**3


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify(path: Path, expected_sha256: str, expected_bytes: int | None = None) -> None:
    if expected_bytes is not None and path.stat().st_size != expected_bytes:
        raise ValueError(f"Size mismatch: {path.name}")
    if sha256(path) != expected_sha256:
        raise ValueError(f"SHA-256 mismatch: {path.name}")


def fetch(artifact: dict, cache: Path) -> Path:
    """A partial or unverified response is never published to the download cache."""
    cache.mkdir(parents=True, exist_ok=True)
    output = cache / (artifact["sha256"] + "-" + artifact["name"])
    if output.exists():
        try:
            verify(output, artifact["sha256"], artifact.get("bytes"))
            print(f"Verified cached {artifact['name']}", flush=True)
            return output
        except ValueError:
            output.unlink()
    print(f"Downloading {artifact['name']} …", flush=True)
    request = urllib.request.Request(artifact["url"], headers={"User-Agent": "kmp-offline-speech-setup/1"})
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=cache, suffix=".part", delete=False) as target:
            temporary = Path(target.name)
            with urllib.request.urlopen(request, timeout=120) as response:
                shutil.copyfileobj(response, target, length=1024 * 1024)
        verify(temporary, artifact["sha256"], artifact.get("bytes"))
        temporary.replace(output)
        return output
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def member_path(name: str) -> PurePosixPath:
    # Backslashes and drive letters must not acquire platform-dependent meanings.
    raw = name.rstrip("/")
    path = PurePosixPath(raw)
    if not raw or "\\" in name or ":" in name or path.is_absolute() or ".." in path.parts:
        raise ValueError(f"Unsafe archive path: {name!r}")
    if path == PurePosixPath("."):
        raise ValueError(f"Unsafe archive path: {name!r}")
    return path


def safe_extract(archive: Path, destination: Path) -> None:
    """Extract only regular files/directories after validating the entire archive.

    Links, devices, duplicate paths, traversal and archives expanding past 3 GiB
    are rejected. Destination must be new/empty and contain no symlinks.
    """
    if destination.is_symlink() or (destination.exists() and any(destination.iterdir())):
        raise ValueError("Extraction destination must be a new or empty directory")
    entries = []
    seen = set()
    total = 0
    with (zipfile.ZipFile(archive) if zipfile.is_zipfile(archive) else tarfile.open(archive, "r:*")) as source:
        is_zip = isinstance(source, zipfile.ZipFile)
        members = source.infolist() if is_zip else source.getmembers()
        for member in members:
            name = member.filename if is_zip else member.name
            path = member_path(name)
            if path in seen:
                raise ValueError(f"Duplicate archive path: {name}")
            seen.add(path)
            if is_zip:
                mode = member.external_attr >> 16
                file_type = stat.S_IFMT(mode)
                directory = member.is_dir()
                if file_type not in (0, stat.S_IFREG, stat.S_IFDIR):
                    raise ValueError(f"Archive links/special files are unsupported: {name}")
                size = member.file_size
            else:
                directory = member.isdir()
                if not directory and not member.isfile():
                    raise ValueError(f"Archive links/special files are unsupported: {name}")
                size = member.size
            total += size
            if size < 0 or total > MAX_EXTRACT_BYTES:
                raise ValueError("Archive exceeds the extraction size limit")
            entries.append((member, path, directory))
        # Reject a regular file also used as a parent before any file is written.
        files = {path for _, path, directory in entries if not directory}
        if any(parent in files for _, path, _ in entries for parent in path.parents):
            raise ValueError("Archive file/directory path conflict")
        destination.mkdir(parents=True, exist_ok=True)
        for member, path, directory in entries:
            target = destination.joinpath(*path.parts)
            if directory:
                target.mkdir(parents=True, exist_ok=True)
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            stream = source.open(member) if is_zip else source.extractfile(member)
            if stream is None:
                raise ValueError(f"Missing archive payload: {path}")
            with stream, target.open("xb") as output:
                shutil.copyfileobj(stream, output, length=1024 * 1024)


def copy_file(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def replace_tree(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.is_symlink():
        raise ValueError(f"Refusing to replace symlink: {destination}")
    if destination.exists():
        shutil.rmtree(destination)
    shutil.copytree(source, destination)


def install_framework(artifact: dict, cache: Path) -> None:
    archive = fetch(artifact, cache)
    with tempfile.TemporaryDirectory(prefix="speech-xcframework-") as temporary:
        extracted = Path(temporary) / "unpacked"
        safe_extract(archive, extracted)
        candidates = list(extracted.rglob("*.xcframework"))
        if len(candidates) != 1 or not candidates[0].is_dir():
            raise ValueError(f"Expected one XCFramework in {artifact['name']}")
        replace_tree(candidates[0], ROOT / artifact["destination"])


def install_models(lock: dict, cache: Path) -> None:
    for artifact in lock["models"]["files"]:
        copy_file(fetch(artifact, cache), ROOT / artifact["destination"])
    artifact = lock["models"]["kitten_archive"]
    archive = fetch(artifact, cache)
    with tempfile.TemporaryDirectory(prefix="speech-kitten-") as temporary:
        extracted = Path(temporary) / "unpacked"
        safe_extract(archive, extracted)
        for item in artifact["files"]:
            candidates = list(extracted.rglob(item["name"]))
            if len(candidates) != 1 or not candidates[0].is_file():
                raise ValueError(f"Expected one {item['name']} in Kitten archive")
            verify(candidates[0], item["sha256"], item.get("bytes"))
            copy_file(candidates[0], ROOT / "models/kitten" / item["name"])
        # Preserve any bundled model notices alongside the weights.
        for candidate in extracted.rglob("*"):
            if candidate.is_file() and candidate.name.lower().startswith(("license", "copying", "notice")):
                copy_file(candidate, ROOT / "models/kitten" / candidate.name)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", choices=("android", "ios", "all"), default="all")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--models-only", action="store_true")
    group.add_argument("--runtimes-only", action="store_true")
    parser.add_argument("--cache-dir", type=Path, default=ROOT / ".cache/downloads")
    args = parser.parse_args()
    lock = json.loads(Path(__file__).with_name("dependencies.json").read_text())
    if not args.models_only:
        if args.platform in ("android", "all"):
            artifact = lock["runtimes"]["android"]
            copy_file(fetch(artifact, args.cache_dir), ROOT / artifact["destination"])
        if args.platform in ("ios", "all"):
            for artifact in lock["runtimes"]["ios"]:
                install_framework(artifact, args.cache_dir)
    if not args.runtimes_only:
        install_models(lock, args.cache_dir)
        if args.platform in ("android", "all"):
            replace_tree(ROOT / "models", ROOT / "composeApp/src/androidMain/assets/models")
    print("Setup complete. iOS: run python3 scripts/prepare_ios.py next.")


if __name__ == "__main__":
    main()
