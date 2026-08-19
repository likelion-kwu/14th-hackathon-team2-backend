#!/usr/bin/env bash

set -Eeuo pipefail

if (( $# != 6 )); then
  echo "Usage: deploy-gabia.sh <artifact> <deploy-path> <service> <health-url> <release-id> <sha256>" >&2
  exit 64
fi

artifact_path="$1"
deploy_path="$2"
service_name="$3"
health_url="$4"
release_id="$5"
expected_sha256="$6"

[[ "${artifact_path}" =~ ^/tmp/godsaeng-lion-[0-9a-f]{40}-[0-9]+\.jar$ ]] || {
  echo "Invalid artifact path" >&2
  exit 64
}

cleanup() {
  rm -f -- "${artifact_path}"
}
trap cleanup EXIT

if [[ ! "${deploy_path}" =~ ^/(opt|srv|home)/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*$ ]] \
  || [[ "/${deploy_path}/" == *"/../"* ]] \
  || [[ "/${deploy_path}/" == *"/./"* ]]; then
  echo "Deployment path must be a safe application directory under /opt, /srv, or /home" >&2
  exit 64
fi
[[ "${service_name}" =~ ^[A-Za-z0-9@_.-]+\.service$ ]] || {
  echo "Service name must end in .service" >&2
  exit 64
}
[[ "${health_url}" =~ ^https?://(127\.0\.0\.1|localhost)(:[0-9]{1,5})?(/[A-Za-z0-9._~/-]*)?$ ]] || {
  echo "Health URL must be an HTTP(S) loopback URL without query parameters" >&2
  exit 64
}
[[ "${release_id}" =~ ^[0-9a-f]{40}-[0-9]+$ ]] || {
  echo "Invalid release ID" >&2
  exit 64
}
[[ "${expected_sha256}" =~ ^[0-9a-f]{64}$ ]] || {
  echo "Invalid SHA-256 digest" >&2
  exit 64
}
[[ -f "${artifact_path}" ]] || {
  echo "Uploaded artifact does not exist" >&2
  exit 66
}

actual_sha256="$(sha256sum "${artifact_path}" | awk '{print $1}')"
if [[ "${actual_sha256}" != "${expected_sha256}" ]]; then
  echo "Uploaded artifact checksum mismatch" >&2
  exit 65
fi

systemctl_bin="$(command -v systemctl)"
releases_path="${deploy_path}/releases"
release_path="${releases_path}/${release_id}.jar"
current_link="${deploy_path}/current.jar"
next_link="${deploy_path}/.current.jar.next"
previous_target="$(readlink "${current_link}" 2>/dev/null || true)"

mkdir -p -- "${releases_path}"
install -m 0644 -- "${artifact_path}" "${release_path}"
ln -sfn -- "${release_path}" "${next_link}"
mv -Tf -- "${next_link}" "${current_link}"

rollback() {
  if [[ -n "${previous_target}" && -f "${previous_target}" ]]; then
    echo "Restoring previous release: ${previous_target}" >&2
    ln -sfn -- "${previous_target}" "${next_link}"
    mv -Tf -- "${next_link}" "${current_link}"
    sudo -n "${systemctl_bin}" restart "${service_name}" || true
  else
    echo "No previous release is available for rollback" >&2
  fi
}

if ! sudo -n "${systemctl_bin}" restart "${service_name}"; then
  echo "Service restart failed" >&2
  rollback
  exit 1
fi

healthy=0
for attempt in {1..30}; do
  if curl --fail --silent --show-error --max-time 3 "${health_url}" >/dev/null; then
    healthy=1
    break
  fi
  echo "Health check ${attempt}/30 failed; retrying..." >&2
  sleep 2
done

if (( healthy == 0 )); then
  echo "New release did not become healthy" >&2
  rollback
  exit 1
fi

mapfile -t release_files < <(
  find "${releases_path}" -maxdepth 1 -type f -name '*.jar' -printf '%T@ %p\n' \
    | sort -rn \
    | cut -d' ' -f2-
)
for (( index = 5; index < ${#release_files[@]}; index++ )); do
  if [[ "${release_files[index]}" != "$(readlink "${current_link}")" ]]; then
    rm -f -- "${release_files[index]}"
  fi
done

echo "Deployment succeeded: ${release_id}"
