# Setup tools

`setup.py` (Python 3.10+, standard library) downloads exactly the runtimes and model files pinned in `dependencies.json`, verifies SHA-256 hashes and extracts archives safely. Use `--platform android`, `--platform ios`, or `--platform all`. `--models-only` and `--runtimes-only` support focused setup. Generated files and the `.cache` directory are ignored by Git.

`prepare_ios.py` compiles the small audio bridge for arm64 device and Apple Silicon simulator. Run it after iOS runtime setup, and re-run it whenever `speech_audio.mm` or its header changes. Full Xcode is required.

Run `python3 -m unittest discover -s scripts -p 'test_*.py' -v` from the root. See [development](../docs/development.md) for the full verification recipe.

## Recent Changes

Added verified artifact setup, extraction regression tests and the iOS audio bridge build.
