#!/bin/sh
# Check, fetch and export the host build toolchain (kept in private/toolchain, git-ignored).
# usage: tools/toolchain.sh check   one "ok <name> <path>" line per tool; exit 1 if any is missing
#        tools/toolchain.sh fetch   download the missing Maven Central jars (sha1 + pinned sha256)
#        tools/toolchain.sh env     print export lines for ANDROID_JAR BUILD_TOOLS JUNIT_CP JSON_CP MOCK_SH;
#                                   use: eval "$(tools/toolchain.sh env)"
# environment: TOOLCHAIN_DIR (default <repo>/private/toolchain), ANDROID_SDK (default ~/Android/sdk)

set -e
TC_ROOT=$(cd "$(dirname "$0")/.." && pwd)
TOOLCHAIN_DIR=${TOOLCHAIN_DIR:-$TC_ROOT/private/toolchain}
ANDROID_SDK=${ANDROID_SDK:-$HOME/Android/sdk}
TC_BUILD_TOOLS=$ANDROID_SDK/build-tools/30.0.3
TC_MAVEN=https://repo1.maven.org/maven2

# Maven Central jars: <folder under TOOLCHAIN_DIR> <path under Maven Central> <pinned sha256>
tc_jars() {
    cat <<LIST
junit junit/junit/4.13.2/junit-4.13.2.jar 8e495b634469d64fb8acfa3495a065cbacc8a0fff55ce1e31007be4c16dc57d3
junit org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar 66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9
json org/json/json/20231013/json-20231013.jar 0f18192df289114e17aa1a0d0a7f8372cc9f5c7e4f7e39adcf8906fe714fa7d3
LIST
}

tc_cp() { # tc_cp <folder> <jar>...: a class path of those jars
    _cp=
    _d=$1; shift
    for _j in "$@"; do _cp=$_cp${_cp:+:}$TOOLCHAIN_DIR/$_d/$_j.jar; done
    echo "$_cp"
}

tc_env() {
    ANDROID_JAR=$ANDROID_SDK/platforms/android-19/android.jar
    BUILD_TOOLS=$TC_BUILD_TOOLS
    JUNIT_CP=$(tc_cp junit junit-4.13.2 hamcrest-core-1.3)
    JSON_CP=$(tc_cp json json-20231013)
    if command -v mksh > /dev/null 2>&1; then MOCK_SH=mksh; else MOCK_SH="bash --posix"; fi
    export ANDROID_JAR BUILD_TOOLS JUNIT_CP JSON_CP MOCK_SH
}

tc_print_env() {
    for _v in ANDROID_JAR BUILD_TOOLS JUNIT_CP JSON_CP MOCK_SH; do
        eval "_val=\$$_v"
        echo "export $_v='$_val'"
    done
}

tc_check() {
    tc_env
    TC_FAILS=0
    ok() { echo "ok $1 $2"; }
    missing() { echo "missing $1${2:+ ($2)}"; TC_FAILS=$((TC_FAILS + 1)); }

    _jv=$(javac -version 2>&1 || true)
    _jv=${_jv#javac }
    _jm=${_jv%%.*}
    case $_jm in
        ''|*[!0-9]*) missing javac "no JDK found" ;;
        *) if [ "$_jm" -ge 11 ]; then ok javac "$(command -v javac) $_jv"; else missing javac "JDK $_jv, need 11 or newer"; fi ;;
    esac

    if [ -f "$ANDROID_JAR" ]; then ok android.jar "$ANDROID_JAR"; else missing android.jar "$ANDROID_JAR"; fi
    for _t in d8 aapt zipalign apksigner dexdump; do
        if [ -x "$TC_BUILD_TOOLS/$_t" ]; then ok "$_t" "$TC_BUILD_TOOLS/$_t"; else missing "$_t" "$TC_BUILD_TOOLS/$_t"; fi
    done

    if [ -f "$TOOLCHAIN_DIR/junit/junit-4.13.2.jar" ] && [ -f "$TOOLCHAIN_DIR/junit/hamcrest-core-1.3.jar" ]; then ok junit "$TOOLCHAIN_DIR/junit"; else missing junit "$TOOLCHAIN_DIR/junit; run tools/toolchain.sh fetch"; fi
    if [ -f "$TOOLCHAIN_DIR/json/json-20231013.jar" ]; then ok json "$TOOLCHAIN_DIR/json"; else missing json "$TOOLCHAIN_DIR/json; run tools/toolchain.sh fetch"; fi

    _p=$(command -v mksh || true)
    if [ -n "$_p" ]; then ok mksh "$_p"; else echo "optional mksh missing (tests use: $MOCK_SH)"; fi

    [ "$TC_FAILS" -eq 0 ]
}
# sha256 of a file
tc_sha256() { sha256sum "$1" | cut -d' ' -f1; }

tc_fetch() {
    # refuse before downloading anything if a jar already here is not the pinned one
    _bad=0
    while read -r _dir _path _sha; do
        _dest=$TOOLCHAIN_DIR/$_dir/${_path##*/}
        [ -f "$_dest" ] || continue
        if [ "$(tc_sha256 "$_dest")" != "$_sha" ]; then
            echo "sha256 mismatch: $_dest (delete it and fetch again)"
            _bad=1
        fi
    done <<LIST
$(tc_jars)
LIST
    [ "$_bad" -eq 0 ] || return 1

    _tmp=$(mktemp -d)
    trap 'rm -rf "$_tmp"' EXIT
    while read -r _dir _path _sha; do
        _name=${_path##*/}
        _dest=$TOOLCHAIN_DIR/$_dir/$_name
        [ -f "$_dest" ] && continue
        echo "fetching $_name"
        curl -fsS -o "$_tmp/$_name" "$TC_MAVEN/$_path"
        curl -fsS -o "$_tmp/$_name.sha1" "$TC_MAVEN/$_path.sha1"
        _want=$(cut -c1-40 "$_tmp/$_name.sha1")
        _have=$(sha1sum "$_tmp/$_name" | cut -d' ' -f1)
        if [ "$_have" != "$_want" ]; then echo "sha1 mismatch: $_name"; return 1; fi
        if [ "$(tc_sha256 "$_tmp/$_name")" != "$_sha" ]; then echo "sha256 mismatch: $_name"; return 1; fi
        mkdir -p "$TOOLCHAIN_DIR/$_dir"
        mv "$_tmp/$_name" "$_dest"
    done <<LIST
$(tc_jars)
LIST
}

case ${1:-} in
    check) tc_check ;;
    fetch) tc_fetch ;;
    env) tc_env; tc_print_env ;;
    *) echo "usage: tools/toolchain.sh check|fetch|env" >&2; exit 2 ;;
esac
