"""Run: python3 -m unittest discover -s scripts -p 'test_*.py' -v"""
import hashlib
import io
from pathlib import Path
import stat
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import setup


class SetupTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def zip_archive(self, entries):
        archive = self.root / "test.zip"
        with zipfile.ZipFile(archive, "w") as output:
            for name, data in entries:
                output.writestr(name, data)
        return archive

    def test_regular_zip_files_are_preserved(self):
        archive = self.zip_archive([("Framework.xcframework/ios-arm64/lib.a", b"static-lib")])
        output = self.root / "output"
        setup.safe_extract(archive, output)
        self.assertEqual((output / "Framework.xcframework/ios-arm64/lib.a").read_bytes(), b"static-lib")

    def test_zip_traversal_rejected_before_any_write(self):
        for name in ("../escape", "/escape", "C:/escape", "folder\\escape"):
            with self.subTest(name=name):
                archive = self.zip_archive([("good.txt", b"fine"), (name, b"evil")])
                output = self.root / "output"
                with self.assertRaises(ValueError):
                    setup.safe_extract(archive, output)
                self.assertFalse(output.exists())

    def test_zip_symlink_rejected(self):
        archive = self.root / "link.zip"
        link = zipfile.ZipInfo("link")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        with zipfile.ZipFile(archive, "w") as output:
            output.writestr(link, "../outside")
        with self.assertRaises(ValueError):
            setup.safe_extract(archive, self.root / "output")

    def test_tar_links_rejected(self):
        for kind in (tarfile.SYMTYPE, tarfile.LNKTYPE):
            with self.subTest(kind=kind):
                archive = self.root / "link.tar.bz2"
                with tarfile.open(archive, "w:bz2") as output:
                    link = tarfile.TarInfo("link")
                    link.type = kind
                    link.linkname = "../../outside"
                    output.addfile(link)
                with self.assertRaises(ValueError):
                    setup.safe_extract(archive, self.root / "output")

    def test_tar_traversal_rejected(self):
        archive = self.root / "bad.tar.bz2"
        with tarfile.open(archive, "w:bz2") as output:
            member = tarfile.TarInfo("../outside")
            member.size = 1
            output.addfile(member, io.BytesIO(b"x"))
        with self.assertRaises(ValueError):
            setup.safe_extract(archive, self.root / "output")

    def test_regular_tar_payload(self):
        archive = self.root / "model.tar.bz2"
        with tarfile.open(archive, "w:bz2") as output:
            member = tarfile.TarInfo("kitten/model.onnx")
            member.size = 5
            output.addfile(member, io.BytesIO(b"model"))
        setup.safe_extract(archive, self.root / "output")
        self.assertEqual((self.root / "output/kitten/model.onnx").read_bytes(), b"model")

    def test_archive_expansion_cap(self):
        archive = self.zip_archive([("payload", b"large")])
        with patch.object(setup, "MAX_EXTRACT_BYTES", 4):
            with self.assertRaises(ValueError):
                setup.safe_extract(archive, self.root / "output")

    def test_file_used_as_parent_rejected(self):
        archive = self.zip_archive([("folder", b"file"), ("folder/nested", b"bad")])
        with self.assertRaises(ValueError):
            setup.safe_extract(archive, self.root / "output")
        self.assertFalse((self.root / "output").exists())

    def artifact(self):
        return {"name": "model.bin", "sha256": hashlib.sha256(b"expected").hexdigest(),
                "bytes": 8, "url": "https://example.invalid/model.bin"}

    def test_unverified_download_never_enters_cache(self):
        cache = self.root / "cache"
        with patch.object(setup.urllib.request, "urlopen", return_value=io.BytesIO(b"modified")):
            with self.assertRaises(ValueError):
                setup.fetch(self.artifact(), cache)
        self.assertEqual(list(cache.iterdir()), [])

    def test_corrupt_cache_is_redownloaded_and_then_reused(self):
        cache = self.root / "cache"
        cache.mkdir()
        artifact = self.artifact()
        cached = cache / (artifact["sha256"] + "-" + artifact["name"])
        cached.write_bytes(b"tampered")
        with patch.object(setup.urllib.request, "urlopen", return_value=io.BytesIO(b"expected")) as request:
            self.assertEqual(setup.fetch(artifact, cache), cached)
            request.assert_called_once()
        with patch.object(setup.urllib.request, "urlopen") as request:
            self.assertEqual(setup.fetch(artifact, cache), cached)
            request.assert_not_called()
        self.assertEqual(cached.read_bytes(), b"expected")


if __name__ == "__main__":
    unittest.main()
