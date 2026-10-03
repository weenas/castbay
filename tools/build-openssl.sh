#!/usr/bin/env bash
# Builds OpenSSL's libcrypto from source (the third_party/openssl submodule, an OpenSSL 3 LTS
# release: Apache 2.0, which the GPL-3.0 allows; 1.1.1's licence didn't) as a static library for
# each Android ABI, into third_party/openssl-android/<abi>/{include,lib/libcrypto.a}, which
# airplay/src/main/cpp/CMakeLists.txt links. An ABI already built from the same OpenSSL commit,
# NDK and API level is skipped, so this is quick after the first time (a few minutes per ABI).
#
#   tools/build-openssl.sh [ABI...]      (default: armeabi-v7a arm64-v8a)
#
# The NDK comes from ANDROID_NDK_ROOT, else the version airplay/build.gradle.kts pins, under
# ANDROID_HOME (or ANDROID_SDK_ROOT, or ~/Library/Android/sdk). Gradle runs this before the
# native build. Needs perl and make.
set -euo pipefail
cd "$(dirname "$0")/.."

SRC=third_party/openssl
OUT=third_party/openssl-android
API=23

if [[ ! -f "$SRC/Configure" ]]; then
  echo "OpenSSL sources missing: run git submodule update --init" >&2
  exit 1
fi

NDK="${ANDROID_NDK_ROOT:-}"
if [[ -z "$NDK" ]]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  version="$(sed -n 's/.*ndkVersion = "\(.*\)".*/\1/p' airplay/build.gradle.kts)"
  NDK="$sdk/ndk/$version"
fi
case "$(uname -s)" in
  Darwin) host=darwin-x86_64 ;;
  Linux) host=linux-x86_64 ;;
  *) echo "Unsupported build host $(uname -s)" >&2; exit 1 ;;
esac
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$host/bin"
if [[ ! -x "$TOOLCHAIN/clang" ]]; then
  echo "No NDK toolchain at $TOOLCHAIN (set ANDROID_NDK_ROOT)" >&2
  exit 1
fi

# Only what libcrypto needs for AirPlay (EVP, big numbers, Ed25519/X25519, AES, SHA-2): no
# command-line tools, tests, docs or loadable modules, and none of the algorithms and protocols
# UxPlay doesn't use, which otherwise all link in through the default provider (+1.5 MB).
OPTIONS=(no-shared no-module no-dso no-engine no-tests no-apps no-docs no-ui-console
  no-legacy no-comp no-quic no-srp no-cms no-ts no-ocsp no-ct no-srtp no-psk
  no-ec2m no-sm2 no-sm3 no-sm4 no-ml-dsa no-ml-kem no-slh-dsa
  no-whirlpool no-idea no-camellia no-seed no-aria no-rc2 no-rc4 no-rc5 no-md4 no-mdc2 no-bf no-cast)
CFLAGS_EXTRA="-ffunction-sections -fdata-sections"

# Which OpenSSL: its commit, or in a source archive without git, its version file.
version="$(git -C "$SRC" rev-parse HEAD 2>/dev/null || tr '\n' ' ' < "$SRC/VERSION.dat")"
stamp="openssl $version, ndk $(basename "$NDK"), api $API, ${OPTIONS[*]}"
jobs="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)"

abis=("$@")
[[ ${#abis[@]} -gt 0 ]] || abis=(armeabi-v7a arm64-v8a)

for abi in "${abis[@]}"; do
  case "$abi" in
    armeabi-v7a) target=android-arm ;;
    arm64-v8a) target=android-arm64 ;;
    x86_64) target=android-x86_64 ;;
    x86) target=android-x86 ;;
    *) echo "Unknown ABI $abi" >&2; exit 1 ;;
  esac
  dest="$OUT/$abi"
  if [[ -f "$dest/stamp" && "$(cat "$dest/stamp")" == "$stamp" ]]; then
    echo "OpenSSL for $abi is up to date"
    continue
  fi
  echo "Building OpenSSL libcrypto for $abi ($stamp)"
  build="$OUT/build-$abi"
  rm -rf "$build" "$dest"
  mkdir -p "$build" "$dest/lib" "$dest/include/openssl"
  (
    cd "$build"
    export ANDROID_NDK_ROOT="$NDK" PATH="$TOOLCHAIN:$PATH"
    perl "../../openssl/Configure" "$target" -D__ANDROID_API__=$API "${OPTIONS[@]}" $CFLAGS_EXTRA \
      > configure.log 2>&1 || { tail -30 configure.log >&2; exit 1; }
    { make -j"$jobs" build_generated && make -j"$jobs" libcrypto.a; } > make.log 2>&1 || { tail -30 make.log >&2; exit 1; }
  )
  cp "$build/libcrypto.a" "$dest/lib/"
  # The public headers, then the ones the build generates (opensslv.h, configuration.h, …).
  cp "$SRC"/include/openssl/*.h "$dest/include/openssl/"
  cp "$build"/include/openssl/*.h "$dest/include/openssl/"
  rm -rf "$build"
  echo "$stamp" > "$dest/stamp"
done
