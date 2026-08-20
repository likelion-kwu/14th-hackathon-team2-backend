# Gabia GitHub Actions deployment setup

The workflow at `.github/workflows/deploy-gabia.yml` tests and packages every pull request to `main`. A push to `main`, or a manual run with `main` selected, deploys the executable JAR to the Gabia VM.

The deployment keeps versioned JARs on the VM, atomically points `current.jar` at the new version, restarts systemd, and calls `/api/v1/health`. If restart or health verification fails and an older JAR exists, the script restores that JAR and restarts the service. The five newest releases are retained.

## 1. Prepare the Gabia VM once

The examples below assume Ubuntu, Java 17, deployment user `godsaeng`, application directory `/opt/godsaeng-lion/backend`, and avatar storage directory `/var/lib/godsaeng-lion/avatars`. Adjust the example unit and GitHub variables together if the server uses different values.

Install Java, curl, OpenSSH, and PostgreSQL client/server components appropriate for the server. Then create the non-root service account and directories:

```bash
sudo useradd --system --create-home --shell /bin/bash godsaeng
sudo install -d -o godsaeng -g godsaeng -m 0750 /opt/godsaeng-lion/backend/releases
sudo install -d -o godsaeng -g godsaeng -m 0750 /var/lib/godsaeng-lion/avatars
sudo install -d -o root -g godsaeng -m 0750 /etc/godsaeng-lion
```

Create `/etc/godsaeng-lion/backend.env` on the VM. Do not commit this file:

```dotenv
DB_URL=jdbc:postgresql://127.0.0.1:5432/godsaeng_lion
DB_USERNAME=replace_me
DB_PASSWORD=replace_me
OPENAI_API_KEY=replace_me
AVATAR_STORAGE_ROOT=/var/lib/godsaeng-lion/avatars
```

Protect the file and install the reviewed systemd unit:

```bash
sudo chown root:godsaeng /etc/godsaeng-lion/backend.env
sudo chmod 0640 /etc/godsaeng-lion/backend.env
sudo cp deploy/godsaeng-lion-backend.service /etc/systemd/system/godsaeng-lion-backend.service
sudo systemctl daemon-reload
sudo systemctl enable godsaeng-lion-backend.service
```

The deploy user needs permission to restart only this service. First run `command -v systemctl`, then use the returned absolute path in `/etc/sudoers.d/godsaeng-lion-deploy`. On Ubuntu it is normally:

```sudoers
godsaeng ALL=(root) NOPASSWD: /usr/bin/systemctl restart godsaeng-lion-backend.service
```

Validate the sudoers file before closing the administrator session:

```bash
sudo chmod 0440 /etc/sudoers.d/godsaeng-lion-deploy
sudo visudo --check
sudo -u godsaeng sudo -n -l /usr/bin/systemctl restart godsaeng-lion-backend.service
```

The last command checks authorization without starting the service. It must not ask for a password or report that the command is disallowed.

## 2. Configure SSH access

Generate a dedicated deployment key pair on an administrator workstation. Use an empty passphrase because GitHub Actions cannot answer an interactive prompt:

```bash
ssh-keygen -t ed25519 -C github-actions-gabia-deploy -f ./gabia_deploy_key
```

Append `gabia_deploy_key.pub` to `/home/godsaeng/.ssh/authorized_keys` on the VM and set standard SSH permissions:

```bash
sudo install -d -o godsaeng -g godsaeng -m 0700 /home/godsaeng/.ssh
sudo sh -c 'cat /path/to/gabia_deploy_key.pub >> /home/godsaeng/.ssh/authorized_keys'
sudo chown godsaeng:godsaeng /home/godsaeng/.ssh/authorized_keys
sudo chmod 0600 /home/godsaeng/.ssh/authorized_keys
```

From a trusted network, record the VM host key and compare its fingerprint with the key shown on the VM before saving it in GitHub:

```bash
ssh-keyscan -p 22 your-gabia-host.example.com
sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

Delete the private key from the workstation after it has been saved in GitHub and access has been tested.

## 3. Configure the GitHub production environment

As a repository administrator, open **Settings → Environments → New environment** and create `production`. Restrict deployment branches to `main`; optionally add required reviewers.

Add these environment variables:

| Variable | Example | Purpose |
| --- | --- | --- |
| `GABIA_HOST` | `api.example.com` | Gabia VM DNS name or IPv4 address |
| `GABIA_SSH_PORT` | `22` | SSH port; defaults to `22` in the workflow |
| `GABIA_SSH_USER` | `godsaeng` | Dedicated deploy/service account |
| `GABIA_DEPLOY_PATH` | `/opt/godsaeng-lion/backend` | Directory owned by the deploy user |
| `GABIA_SERVICE_NAME` | `godsaeng-lion-backend.service` | Exact systemd unit allowed by sudoers |
| `GABIA_HEALTH_URL` | `http://127.0.0.1:8080/api/v1/health` | Loopback-only health endpoint on the VM |

Add these environment secrets:

| Secret | Value |
| --- | --- |
| `GABIA_SSH_PRIVATE_KEY` | Complete contents of `gabia_deploy_key`, including header and footer |
| `GABIA_SSH_KNOWN_HOSTS` | Verified `ssh-keyscan` output for the configured host and port |

Production database credentials and `OPENAI_API_KEY` belong only in the VM environment file. They are not GitHub Actions secrets because the build does not need them and the deploy job never copies application secrets.

Under **Settings → Actions → General**, keep the default workflow token permission read-only. If the organization restricts third-party actions, allow the pinned `gradle/actions/setup-gradle` action in addition to GitHub's `actions/*` actions used by the workflow.

After the workflow has run once, add `Test and package` as a required status check in the `main` branch ruleset. This prevents code that fails tests or packaging from being merged while leaving the production deployment job gated by the `production` environment.

## 4. First deployment and operations

Merge the workflow into `main`, then watch **Actions → Build and deploy to Gabia**. A successful run creates a deployment record under the `production` environment.

Useful VM checks:

```bash
sudo systemctl status godsaeng-lion-backend.service
sudo journalctl -u godsaeng-lion-backend.service -n 200 --no-pager
readlink /opt/godsaeng-lion/backend/current.jar
curl --fail http://127.0.0.1:8080/api/v1/health
```

Flyway runs when Spring Boot starts. The JAR rollback cannot reverse a database migration, so production migrations must remain backward compatible with the immediately previous application release.

To stop automatic production deployments without editing the workflow, add a required reviewer to the `production` environment or disable the workflow in the Actions page.
