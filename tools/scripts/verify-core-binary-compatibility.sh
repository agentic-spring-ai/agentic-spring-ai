#!/usr/bin/env bash
#
# Copyright 2024-2026 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -euo pipefail

readonly BASE_COMMIT="${1:-c128f02584fc976ee641572074db2e556466f6a2}"
readonly JAPICMP_VERSION="${JAPICMP_VERSION:-0.23.1}"
readonly REVISION="${REVISION:-2.1.0-dev}"

readonly REPO_ROOT="$(git rev-parse --show-toplevel)"
readonly TMP_PARENT="${TMPDIR:-/tmp}"
readonly COMPAT_TMP="$(mktemp -d "${TMP_PARENT%/}/agentic-core-binary-compat.XXXXXX")"
readonly BASELINE_WORKTREE="${COMPAT_TMP}/baseline"
readonly REPORT_DIR="${REPO_ROOT}/target/binary-compatibility"
readonly ISOLATED_MAVEN_REPO="${BINARY_COMPAT_MAVEN_REPO:-${REPORT_DIR}/m2}"

cleanup() {
	if git -C "${REPO_ROOT}" worktree list --porcelain | grep -Fqx "worktree ${BASELINE_WORKTREE}"; then
		git -C "${REPO_ROOT}" worktree remove --force "${BASELINE_WORKTREE}" >/dev/null 2>&1 || true
	fi
	if [[ "${COMPAT_TMP}" == "${TMP_PARENT%/}"/agentic-core-binary-compat.* && -d "${COMPAT_TMP}" ]]; then
		rm -rf "${COMPAT_TMP}"
	fi
	git -C "${REPO_ROOT}" worktree prune >/dev/null 2>&1 || true
}
trap cleanup EXIT

run_maven() {
	local workdir="$1"
	shift
	(
		cd "${workdir}"
		if [[ -n "${JAVA_HOME:-}" ]]; then
			JAVA_HOME="${JAVA_HOME}" ./mvnw -B -Dmaven.repo.local="${ISOLATED_MAVEN_REPO}" "$@"
		else
			./mvnw -B -Dmaven.repo.local="${ISOLATED_MAVEN_REPO}" "$@"
		fi
	)
}

run_maven_with_retry() {
	local workdir="$1"
	shift
	local attempt=1
	local max_attempts=3

	until run_maven "${workdir}" "$@"; do
		if ((attempt >= max_attempts)); then
			return 1
		fi
		echo "Maven command failed; retrying (${attempt}/${max_attempts})"
		attempt=$((attempt + 1))
	done
}

echo "Preparing baseline worktree at ${BASE_COMMIT}"
git -C "${REPO_ROOT}" worktree add --detach "${BASELINE_WORKTREE}" "${BASE_COMMIT}" >/dev/null

echo "Building baseline Core graph/builtin artifacts in isolated Maven repo"
run_maven_with_retry "${BASELINE_WORKTREE}" -U -DskipTests -pl :agentic-spring-ai-graph-core,:agentic-spring-ai-starter-builtin-nodes -am package

echo "Building candidate Core graph/builtin artifacts in isolated Maven repo"
run_maven_with_retry "${REPO_ROOT}" -U -DskipTests -pl :agentic-spring-ai-graph-core,:agentic-spring-ai-starter-builtin-nodes -am package

echo "Resolving japicmp ${JAPICMP_VERSION}"
run_maven_with_retry "${REPO_ROOT}" dependency:get \
	-Dartifact="com.github.siom79.japicmp:japicmp:${JAPICMP_VERSION}:jar:jar-with-dependencies"

readonly JAPICMP_JAR="${ISOLATED_MAVEN_REPO}/com/github/siom79/japicmp/japicmp/${JAPICMP_VERSION}/japicmp-${JAPICMP_VERSION}-jar-with-dependencies.jar"

mkdir -p "${REPORT_DIR}"

compare_module() {
	local label="$1"
	local baseline_jar="$2"
	local candidate_jar="$3"
	local report_file="${REPORT_DIR}/${label}-japicmp.md"

	echo "Comparing ${label}"
	rm -f "${report_file}"
	java -jar "${JAPICMP_JAR}" \
		--old "${baseline_jar}" \
		--new "${candidate_jar}" \
		-a protected \
		--only-incompatible \
		--error-on-binary-incompatibility \
		--ignore-missing-classes \
		--markdown \
		> "${report_file}"
	echo "Binary compatible: ${label} (${report_file})"
}

compare_module "agentic-spring-ai-graph-core" \
	"${BASELINE_WORKTREE}/agentic-spring-ai-graph-core/target/agentic-spring-ai-graph-core-${REVISION}.jar" \
	"${REPO_ROOT}/agentic-spring-ai-graph-core/target/agentic-spring-ai-graph-core-${REVISION}.jar"

compare_module "agentic-spring-ai-starter-builtin-nodes" \
	"${BASELINE_WORKTREE}/spring-boot-starters/agentic-spring-ai-starter-builtin-nodes/target/agentic-spring-ai-starter-builtin-nodes-${REVISION}.jar" \
	"${REPO_ROOT}/spring-boot-starters/agentic-spring-ai-starter-builtin-nodes/target/agentic-spring-ai-starter-builtin-nodes-${REVISION}.jar"

echo "Core binary compatibility gate passed"
