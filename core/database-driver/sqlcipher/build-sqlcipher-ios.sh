#!/bin/bash
# Builds the SQLCipher amalgamation as static libraries for iOS device and Apple Silicon simulator.
# Usage: build-sqlcipher-ios.sh <amalgamation source dir> <output dir> <min iOS version>
# Flags follow the SQLCipher 4.19.0 build documentation: CommonCrypto provider, raw-key codec, MEMORY temp store.
set -euo pipefail

SRC="$1"
OUT="$2"
MIN_IOS="$3"

FLAGS=(
  -DSQLITE_HAS_CODEC
  -DSQLCIPHER_CRYPTO_CC
  -DSQLITE_TEMP_STORE=3
  -DSQLITE_EXTRA_INIT=sqlcipher_extra_init
  -DSQLITE_EXTRA_SHUTDOWN=sqlcipher_extra_shutdown
  -DNDEBUG
  -O2
)

rm -rf "$OUT"
mkdir -p "$OUT/device" "$OUT/simulator"

xcrun --sdk iphoneos clang -arch arm64 -miphoneos-version-min="$MIN_IOS" \
  "${FLAGS[@]}" -I"$SRC" -c "$SRC/sqlite3.c" -o "$OUT/device/sqlite3.o"
xcrun --sdk iphoneos ar rcs "$OUT/device/libsqlcipher.a" "$OUT/device/sqlite3.o"

xcrun --sdk iphonesimulator clang -target "arm64-apple-ios${MIN_IOS}-simulator" \
  "${FLAGS[@]}" -I"$SRC" -c "$SRC/sqlite3.c" -o "$OUT/simulator/sqlite3.o"
xcrun --sdk iphonesimulator ar rcs "$OUT/simulator/libsqlcipher.a" "$OUT/simulator/sqlite3.o"

# The library must export the SQLCipher codec and must not depend on a system libsqlite3.
for lib in "$OUT/device/libsqlcipher.a" "$OUT/simulator/libsqlcipher.a"; do
  nm -g "$lib" | grep -q " T _sqlite3_key_v2$"
  nm -g "$lib" | grep -q " T _sqlcipher_extra_init$"
done
