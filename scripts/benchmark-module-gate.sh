#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <git-repository-url> <base-ref> <head-ref>" >&2
  echo "Example: $0 https://github.com/OpenFeign/feign.git HEAD~1 HEAD" >&2
  exit 2
fi

REPO_URL="$1"
BASE="$2"
HEAD="$3"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${CODECRITIC_JAR:-$ROOT/target/codecritic.jar}"
TEST_TIMEOUT="${CODECRITIC_BENCHMARK_TIMEOUT:-1200}"

if [ ! -f "$JAR" ]; then
  echo "Missing CodeCritic jar: $JAR" >&2
  echo "Run: mvn -B package -DskipTests" >&2
  exit 2
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
REPO="$WORK/repo"

git clone --quiet "$REPO_URL" "$REPO"
git -C "$REPO" fetch --quiet origin "$BASE" "$HEAD" || true
git -C "$REPO" checkout --quiet "$HEAD"
BASE_SHA="$(git -C "$REPO" rev-parse "$BASE")"
HEAD_SHA="$(git -C "$REPO" rev-parse "$HEAD")"

run_scope() {
  local scope="$1"
  local report="$WORK/$scope.json"
  local start end status
  start="$(date +%s%3N)"
  set +e
  CODECRITIC_SANDBOX_NETWORK="${CODECRITIC_SANDBOX_NETWORK:-bridge}" \
    java -jar "$JAR" \
      --repo "$REPO" \
      --base "$BASE_SHA" \
      --head "$HEAD_SHA" \
      --run-tests \
      --select "$scope" \
      --fail-on never \
      --format json \
      --test-timeout "$TEST_TIMEOUT" \
      --out "$report"
  status=$?
  set -e
  end="$(date +%s%3N)"
  echo "$status" > "$WORK/$scope.status"
  echo "$((end - start))" > "$WORK/$scope.ms"
  echo "$report" > "$WORK/$scope.report"
}

run_scope all
run_scope modules

python3 - "$WORK" "$REPO_URL" "$BASE_SHA" "$HEAD_SHA" <<'PY'
import json
import pathlib
import sys

work = pathlib.Path(sys.argv[1])
repo = sys.argv[2]
base = sys.argv[3]
head = sys.argv[4]

def read_json(path):
    try:
        return json.loads(path.read_text())
    except Exception as exc:
        return {"_error": str(exc)}

full = read_json(pathlib.Path((work / "all.report").read_text().strip()))
selected = read_json(pathlib.Path((work / "modules.report").read_text().strip()))
full_ms = int((work / "all.ms").read_text().strip())
selected_ms = int((work / "modules.ms").read_text().strip())
full_status = int((work / "all.status").read_text().strip())
selected_status = int((work / "modules.status").read_text().strip())

def baseline(report):
    return report.get("baseline") or {}

def scope(report):
    selection = baseline(report).get("selection") or {}
    mode = selection.get("mode", "ALL")
    modules = selection.get("modules") or []
    return mode, modules

full_mode, _ = scope(full)
selected_mode, selected_modules = scope(selected)
saved = 0 if full_ms == 0 else round((full_ms - selected_ms) * 100 / full_ms, 1)
scope_label = "all" if selected_mode == "ALL" else ",".join(selected_modules)
setup_error = baseline(selected).get("setupError")
note = ""
if setup_error:
    note = "setup error: " + str(setup_error).replace("|", "/")
elif full_mode == "ALL" and selected_mode == "ALL":
    note = "module fallback; no benchmark signal"

print("| Repository | Base | Head | Selected scope | Full ms | Module ms | Saved % | Exit full/module | Note |")
print("|---|---|---|---|---:|---:|---:|---|---|")
print(f"| {repo} | {base[:12]} | {head[:12]} | {scope_label} | {full_ms} | {selected_ms} | {saved} | {full_status}/{selected_status} | {note} |")
PY
