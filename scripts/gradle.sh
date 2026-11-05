#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -n "${MARKET_JAVA_HOME:-}" ]]; then
 export JAVA_HOME="$MARKET_JAVA_HOME"
elif [[ -x /workspace/android/toolchain/jdk17/bin/javac ]]; then
 export JAVA_HOME=/workspace/android/toolchain/jdk17
fi
market_args=()
if [[ -f /etc/ssl/certs/java/cacerts ]]; then market_args+=(-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts); fi
if [[ -n "${HTTPS_PROXY:-}" ]]; then
 read -r market_proxy_host market_proxy_port < <(python3 - <<'PY'
import os,urllib.parse
u=urllib.parse.urlparse(os.environ['HTTPS_PROXY'])
print(u.hostname or '',u.port or 80)
PY
 )
 if [[ -n "$market_proxy_host" ]]; then market_args+=("-Dhttps.proxyHost=$market_proxy_host" "-Dhttps.proxyPort=$market_proxy_port" "-Dhttp.proxyHost=$market_proxy_host" "-Dhttp.proxyPort=$market_proxy_port"); fi
fi
exec ./gradlew "${market_args[@]}" "$@"
