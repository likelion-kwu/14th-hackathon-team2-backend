#!/usr/bin/env bash

set -Eeuo pipefail

if (( $# != 6 )); then
  echo "Usage: deploy-gabia.sh <image-archive> <compose-source> <deploy-path> <health-url> <release-id> <sha256>" >&2
  exit 64
fi

image_archive="$1"
compose_source="$2"
deploy_path="$3"
health_url="$4"
release_id="$5"
expected_sha256="$6"

[[ "${image_archive}" =~ ^/tmp/godsaeng-lion-[0-9a-f]{40}-[0-9]+\.image\.tar\.gz$ ]] || {
  echo "Invalid image archive path" >&2
  exit 64
}
[[ "${compose_source}" =~ ^/tmp/godsaeng-lion-[0-9a-f]{40}-[0-9]+\.compose\.yaml$ ]] || {
  echo "Invalid Compose source path" >&2
  exit 64
}
if [[ ! "${deploy_path}" =~ ^/(opt|srv|home)/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*$ ]] \
  || [[ "/${deploy_path}/" == *"/../"* ]] \
  || [[ "/${deploy_path}/" == *"/./"* ]]; then
  echo "Deployment path must be a safe application directory under /opt, /srv, or /home" >&2
  exit 64
fi
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
[[ -f "${image_archive}" && -f "${compose_source}" ]] || {
  echo "Uploaded deployment files do not exist" >&2
  exit 66
}

cleanup_uploads() {
  rm -f -- "${image_archive}" "${compose_source}"
}
trap cleanup_uploads EXIT

actual_sha256="$(sha256sum "${image_archive}" | awk '{print $1}')"
if [[ "${actual_sha256}" != "${expected_sha256}" ]]; then
  echo "Uploaded image checksum mismatch" >&2
  exit 65
fi

command -v docker >/dev/null || {
  echo "Docker is not installed" >&2
  exit 69
}
docker compose version >/dev/null
gzip -t "${image_archive}"

mkdir -p -- "${deploy_path}" "${deploy_path}/data/avatars"
env_file="${deploy_path}/.env"
compose_file="${deploy_path}/compose.yaml"
[[ -f "${env_file}" ]] || {
  echo "Runtime environment file is missing: ${env_file}" >&2
  exit 78
}

install -m 0644 -- "${compose_source}" "${compose_file}"
gzip -dc -- "${image_archive}" | docker load

new_image="godsaeng-lion-backend:${release_id}"
docker image inspect "${new_image}" >/dev/null
docker run --rm \
  --user 0:0 \
  --entrypoint /bin/sh \
  --volume "${deploy_path}/data/avatars:/data" \
  "${new_image}" \
  -c 'chown 10001:10001 /data && chmod 0700 /data'

previous_container="$(
  docker ps -aq \
    --filter 'label=com.docker.compose.project=godsaeng-lion' \
    --filter 'label=com.docker.compose.service=backend' \
    | head -n 1
)"
previous_image=""
if [[ -n "${previous_container}" ]]; then
  previous_image="$(docker inspect --format '{{.Config.Image}}' "${previous_container}")"
fi

compose_command=(
  docker compose
  --project-directory "${deploy_path}"
  --env-file "${env_file}"
  --file "${compose_file}"
)

start_image() {
  local image_ref="$1"
  env BACKEND_IMAGE="${image_ref}" "${compose_command[@]}" up -d --no-build --remove-orphans
}

rollback() {
  if [[ -n "${previous_image}" && "${previous_image}" != "${new_image}" ]]; then
    echo "Restoring previous container image: ${previous_image}" >&2
    start_image "${previous_image}" || true
  else
    echo "No previous container image is available for rollback" >&2
    env BACKEND_IMAGE="${new_image}" "${compose_command[@]}" stop backend >/dev/null 2>&1 || true
  fi
}

if ! start_image "${new_image}"; then
  echo "Docker Compose failed to start the release" >&2
  rollback
  exit 1
fi

healthy=0
for attempt in {1..45}; do
  if curl --fail --silent --show-error --max-time 3 "${health_url}" >/dev/null; then
    healthy=1
    break
  fi
  echo "Health check ${attempt}/45 failed; retrying..." >&2
  sleep 2
done

if (( healthy == 0 )); then
  echo "New container release did not become healthy" >&2
  "${compose_command[@]}" logs --no-color --tail 150 backend postgres >&2 || true
  rollback
  exit 1
fi

printf '%s\n' "${new_image}" > "${deploy_path}/.current-image"
echo "Docker deployment succeeded: ${new_image}"
