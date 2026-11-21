#!/usr/bin/env python3
"""Compile the audio-only bridge; inference uses the downloaded sherpa C API directly."""
import argparse
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]


def run(*args):
    subprocess.run([str(a) for a in args], check=True)


def prepare(target):
    simulator = target == "iosSimulatorArm64"
    sdk = "iphonesimulator" if simulator else "iphoneos"
    sdk_path = subprocess.check_output(["xcrun", "--sdk", sdk, "--show-sdk-path"], text=True).strip()
    source = ROOT / "composeApp/src/iosMain/cinterop"
    framework = ROOT / "libs/ios/SherpaOnnxC.xcframework"
    slice_name = "ios-arm64_x86_64-simulator" if simulator else "ios-arm64"
    headers = framework / slice_name / "SherpaOnnxC.framework/Headers"
    if not headers.is_dir():
        raise SystemExit("Missing sherpa XCFramework; run python3 scripts/setup.py --platform ios first")
    output = ROOT / "libs/ios/audio" / target
    output.mkdir(parents=True, exist_ok=True)
    object_file = output / "speech_audio.o"
    triple = "arm64-apple-ios18.5" + ("-simulator" if simulator else "")
    run("xcrun", "--sdk", sdk, "clang++", "-std=c++17", "-fobjc-arc", "-O2",
        "-target", triple, "-isysroot", sdk_path, "-I", source, "-I", headers,
        "-c", source / "speech_audio.mm", "-o", object_file)
    run("xcrun", "libtool", "-static", "-o", output / "libspeech_audio.a", object_file)
    print(f"Prepared {target} audio bridge")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=["iosArm64", "iosSimulatorArm64", "all"], default="all")
    arguments = parser.parse_args()
    for selected in (["iosArm64", "iosSimulatorArm64"] if arguments.target == "all" else [arguments.target]):
        prepare(selected)
