# Hosting miniciv

How miniciv is hosted: one DigitalOcean Droplet you can SSH into, on a domain bought at Porkbun, with master redeployed every day at 04:07 UTC, or at once on a manual trigger, and only when every test passes.

This runbook is the whole spec. Follow it top to bottom and you shouldn't need to open anything else; each section ends with a **Why** line naming the ticket where its decisions were made, for when you want the reasoning. The tickets belong to [Host miniciv on a DigitalOcean VPS with scheduled auto-deploys][map].

Parts 2 and 3 can be built before the domain is bought. Parts 4 and 5 wait for it.

1. [Overview](#1-overview)
2. [App changes](#2-app-changes)
3. [Repo files](#3-repo-files)
4. [Provisioning checklist](#4-provisioning-checklist)
5. [Verification](#5-verification)
6. [Operating](#6-operating)
7. [Implementation slices](#7-implementation-slices)
8. [Settled while writing this spec](#8-settled-while-writing-this-spec)

## 1. Overview

```
Players ──HTTPS / WSS──▶ Reserved IPv4 ──▶ Cloud Firewall (22, 80, 443)
                                                │
          ┌─────────────────────── Droplet: s-1vcpu-1gb, LON1, Ubuntu LTS ───────────────────────┐
          │  Caddy :80/:443   automatic Let's Encrypt, HTTP→HTTPS, www→apex, HSTS, compression   │
          │     │ reverse_proxy (HTTP, /api, /ws)                                                │
          │     ▼                                                                                │
          │  miniciv.service  java -jar /opt/miniciv/current  on 127.0.0.1:8080, user miniciv    │
          │                   current → releases/<sha>.jar     REVISION = <sha> that is live     │
          └──────────────────────────────────────────────────────────────────────────────────────┘
                                                ▲
GitHub Actions ──SSH as deploy──────────────────┘   deploy.yml: 04:07 UTC daily, or by hand
```

- **The app** is the one Spring Boot jar (Java 21). It serves the React frontend, `/api` and `/ws` (STOMP over WebSocket). It listens only on localhost, so Caddy is the only way in.
- **Caddy** terminates HTTPS, gets and renews certificates itself, redirects HTTP to HTTPS and `www` to the apex, and proxies everything, WebSockets included.
- **A deploy** happens at 04:07 UTC each day if master has changed since the last deploy, or at once when started by hand. GitHub Actions builds master's tip and runs every test; if any fails, nothing is deployed. Otherwise it copies the jar to the Droplet, points `current` at it, restarts the service and waits for `/api/health`. If the new jar doesn't come up within 90 seconds, it rolls back to the previous jar and the workflow fails, which GitHub emails you about.
- **What a deploy does to Players:** all state is in memory, so every restart ends every Room and Game, without warning. That is a deliberate trade-off, recorded in [ADR 0003: Deploys wipe running Games](adr/0003-deploys-wipe-running-games.md). A security-update reboot at 04:20 UTC does the same.
- **Cost:** $6 a month for the Droplet, plus the domain. No paid backups.

**Out of scope:** saving Games or Rooms across restarts; staging and preview deploys; monitoring and alerting beyond systemd restarting a crashed process; IPv6; defending against distributed floods (DDoS).

**Why:** [Which Droplet size and region suit miniciv?][97], [How can GitHub Actions deploy to a Droplet on a schedule and on demand?][98], [How should HTTPS and WebSockets be fronted on the Droplet?][99], [What form does the hosting spec take, and where does it live?][107]

## 2. App changes

### 2.1 The `prod` profile

Add `backend/src/main/resources/application-prod.properties`. The Droplet's systemd unit turns it on with `SPRING_PROFILES_ACTIVE=prod`; local runs never use it, and only `ProdProfileTest` does among the tests.

```properties
# Only Caddy, on the same Droplet, can reach the app.
server.address=127.0.0.1
server.port=8080
# Trust Caddy's X-Forwarded-For and X-Forwarded-Proto. Spring only does this by itself on cloud platforms it
# detects, and a plain Droplet isn't one. Tomcat already trusts proxies on localhost.
server.forward-headers-strategy=native
```

- The client IP the abuse limits key on comes from `X-Forwarded-For`. Caddy replaces any incoming `X-Forwarded-For` by default, and the jar only listens on localhost, so a Player can't fake it.
- There is no environment file, because there are no secrets. Add one only if a secret ever appears.

### 2.2 `/api/health`

Already there: `HealthController` answers `GET /api/health` with `200 {"status":"UP"}`, and `HealthEndpointTest` covers it. The deploy polls it on localhost after each restart. Don't add Spring Boot Actuator.

### 2.3 Abuse limits

The threat is one person or one IP looping requests: bored people and careless scripts. What the code already bounds stays as it is: an unjoined or unconnected Room is removed after 60 s, a Room has at most 4 Players, a Ruler's name at most 20 characters, a Message at most 280 characters, and a STOMP frame at most 64 KB (Spring's default).

All new limits live in the app. Each is a config key under `miniciv.limits.*`, so tests can set them low and the Droplet can override them without a rebuild ([6.4](#64-changing-limits)). Write the defaults into `application.properties`:

```properties
# Abuse limits; see docs/hosting.md.
miniciv.limits.rooms=100
miniciv.limits.room-creations-per-minute=5
miniciv.limits.joins-per-minute=30
miniciv.limits.sessions=200
miniciv.limits.sessions-per-ip=20
miniciv.limits.frames-per-second=20
miniciv.limits.messages-per-game=500
miniciv.limits.nation-name-length=20
```

| Limit | Key | Default | Where | When hit |
|---|---|---|---|---|
| Live Rooms, overall | `rooms` | 100 | `Rooms.create()` | refused with 503, "miniciv is full right now, try again shortly" |
| Room creation per client IP | `room-creations-per-minute` | 5 / min | in-memory token bucket on `POST /api/rooms` | 429 with a friendly message |
| Joins per client IP | `joins-per-minute` | 30 / min | the same bucket mechanism on `POST /api/rooms/{code}/players` | 429; this also stops anyone guessing Room codes by brute force |
| `/ws` sessions, overall | `sessions` | 200 | a WebSocket handshake interceptor | the upgrade is refused |
| `/ws` sessions per client IP | `sessions-per-ip` | 20 | the same interceptor | the upgrade is refused |
| Inbound STOMP frames per session | `frames-per-second` | 20 / s | a client-inbound channel interceptor, next to `PlayerIdentification` | extra frames are **dropped** silently |
| Messages per Player per Game | `messages-per-game` | 500 | `Room.message` | rejected with a reason, like any other refused Message |
| Nation name length | `nation-name-length` | 20 chars | `Room.join` | a new `JoinRefused.Reason.NATION_NAME_TOO_LONG`, answered with 400 |

Notes for building them:

- Answer 429 and 503 as a `ProblemDetail` whose `detail` is the text for the Player, as `RoomController` already does for `JoinRefused`.
- Forget a client IP's token bucket once it has been full and unused for a few minutes, so the buckets themselves can't grow without limit.
- The `/ws` counts go down when a session ends (`SessionDisconnectEvent`). With heartbeats on ([2.4](#24-stomp-heartbeats)), a session whose client vanished ends within about 30 s and frees its slot.
- The frontend shows the server's text for 429 and 503 the way it shows a `JoinRefused` today. `createRoom` in `frontend/src/api.ts` needs to start reading `detail` like `joinRoom` does. A refused `/ws` upgrade is different: browsers don't let a page read a failed WebSocket handshake, so the frontend can only show a fixed message, such as "Can't connect to miniciv right now".
- Add these two lines to `GLOSSARY.md` when the limits are built:
  - **Message**: a Player may send at most 500 Messages in a Game.
  - **Nation**: a Nation's name is at most 20 characters.

Caddy adds one more limit, a 64 KB cap on `/api` request bodies (see [3.4](#34-caddyfile)). Caddy has no rate limiter in its standard build, and the `xcaddy` rate-limit plugin would mean leaving the apt package and its automatic updates, so it isn't used.

**Why:** [What light abuse protection does a widely shared miniciv need, and where does it live?][105]

### 2.4 STOMP heartbeats

Today the simple broker sends no heartbeats, so a connection that died without a close frame (a laptop lid shut, a mobile network dropped) lingers. That holds a slot under the `/ws` caps, and the Game only Pauses once a disconnect is noticed. Turn heartbeats on at 10 s each way.

**Backend.** Add the intervals to `application.properties`, next to the limits:

```properties
# STOMP heartbeats, in ms; the frontend's values in useRoom.ts must match.
miniciv.heartbeat.server-ms=10000
miniciv.heartbeat.client-ms=10000
```

In `WebSocketConfig.configureMessageBroker`:

```java
registry.enableSimpleBroker("/topic", "/queue")
	.setHeartbeatValue(new long[] { heartbeat.serverMs(), heartbeat.clientMs() })
	.setTaskScheduler(heartbeatScheduler);
```

`heartbeatScheduler` is a `ThreadPoolTaskScheduler` with one thread named `stomp-heartbeat-`, created and initialised inside `WebSocketConfig` and shut down with it. Don't register it as a bean: a `TaskScheduler` bean changes which scheduler Spring runs `@Scheduled` methods on, and the heartbeats must not share a thread with the three 1 s jobs in `Rooms`, where slow Turn work could delay them.

**Effect.** The simple broker closes a session that sends nothing for three times the agreed interval, about 30 s. That fires the existing `SessionDisconnectEvent` → `Rooms.disconnected`: the Game Pauses, the Player's 5-minute clock starts (counting from when the disconnect is noticed, so the Player isn't charged for those 30 s), and the session's `/ws` slot is freed.

**Frontend.** In `frontend/src/useRoom.ts`, write the values down so a change in stompjs's defaults can't silently break them, and tick heartbeats from a Web Worker so a hidden tab keeps sending them despite browsers throttling its timers:

```ts
import { Client, TickerStrategy } from '@stomp/stompjs'

const stomp = new Client({
  brokerURL: `${protocol}://${window.location.host}/ws`,
  connectHeaders: { token },
  reconnectDelay: 1000,
  // Must match miniciv.heartbeat.* on the server. The Worker ticker keeps a hidden tab's heartbeats
  // going, so switching tabs never Pauses the Game.
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
  heartbeatStrategy: TickerStrategy.Worker,
})
```

A Player looking at another tab is not disconnected. Caddy needs no change: it never closes idle sockets, and heartbeats keep them busy anyway.

**Why:** [Should STOMP heartbeats be turned on, and how often?][106]

## 3. Repo files

New files:

```
.github/actions/build/action.yml   the build both workflows share
.github/workflows/ci.yml            build and test on every push and pull request
.github/workflows/deploy.yml        the daily and manual deploy
deploy/cloud-init.yaml              sets up a new Droplet; the only copy of the Caddyfile and the systemd unit
```

### 3.1 Every test blocks a deploy: vitest in Maven

Today `./mvnw package` runs the backend tests and the frontend's `tsc -b && vite build`, but not the vitest files. Add a fourth execution to `frontend-maven-plugin` in `backend/pom.xml`, after `npm-build`:

```xml
<execution>
	<id>npm-test</id>
	<goals>
		<goal>npm</goal>
	</goals>
	<phase>test</phase>
	<configuration>
		<arguments>run test</arguments>
	</configuration>
</execution>
```

- One command, locally and in CI, now runs the backend tests, vitest and the type-check.
- `-DskipTests` skips vitest too, as does `-Dskip.npm`.
- Lint (`oxlint`) doesn't block a build or a deploy.
- Update the README's Test section to say `./mvnw test` also runs the frontend tests.

### 3.2 The shared build: `.github/actions/build/action.yml`

```yaml
name: Build
description: Builds the jar with the frontend inside it, running the backend tests, vitest and the type-check.

runs:
  using: composite
  steps:
    - uses: actions/setup-java@v5
      with:
        distribution: temurin
        java-version: '21'
        cache: maven

    - name: Read the Node version from the pom
      id: node
      shell: bash
      run: echo "version=$(sed -n 's:.*<node.version>\(.*\)</node.version>.*:\1:p' backend/pom.xml)" >> "$GITHUB_OUTPUT"

    # frontend-maven-plugin installs Node into backend/target; keep it, and npm's download cache, between runs.
    - uses: actions/cache@v4
      with:
        path: |
          ~/.npm
          backend/target/node
        key: frontend-${{ runner.os }}-${{ steps.node.outputs.version }}-${{ hashFiles('frontend/package-lock.json') }}

    # No clean: the runner starts from a fresh checkout, and clean would delete the cached Node.
    - name: Build and test
      shell: bash
      working-directory: backend
      run: ./mvnw -B package
```

Each workflow checks the repo out before using this action, because GitHub can only find a local action once the repo is checked out. Use the current major versions of the `actions/*` actions when building this.

### 3.3 The workflows

`.github/workflows/ci.yml` only builds and tests:

```yaml
name: CI

on:
  push:
  pull_request:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: ./.github/actions/build
```

`.github/workflows/deploy.yml` rebuilds and retests master's tip itself rather than reusing a CI run's jar, so nothing passes between workflows:

```yaml
name: Deploy

on:
  schedule:
    - cron: '7 4 * * *' # 04:07 UTC; GitHub delays schedules most at the top of the hour
  workflow_dispatch:

permissions:
  contents: read

# A manual run started during the nightly one waits for it rather than racing it.
concurrency:
  group: deploy
  cancel-in-progress: false

jobs:
  deploy:
    # The branch picker on a manual run can't put another branch live.
    if: github.ref == 'refs/heads/master'
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - name: Set up SSH to the Droplet
        env:
          DEPLOY_SSH_KEY: ${{ secrets.DEPLOY_SSH_KEY }}
          DEPLOY_KNOWN_HOSTS: ${{ secrets.DEPLOY_KNOWN_HOSTS }}
          DEPLOY_HOST: ${{ secrets.DEPLOY_HOST }}
        run: |
          install -d -m 700 ~/.ssh
          printf '%s\n' "$DEPLOY_SSH_KEY" > ~/.ssh/deploy
          chmod 600 ~/.ssh/deploy
          printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > ~/.ssh/known_hosts
          printf 'Host droplet\n  HostName %s\n  User deploy\n  IdentityFile ~/.ssh/deploy\n  IdentitiesOnly yes\n  BatchMode yes\n' \
            "$DEPLOY_HOST" > ~/.ssh/config

      # Every restart wipes every Game, so a night with nothing new does nothing. A manual run always deploys.
      - name: Skip if this commit is already live
        id: live
        if: github.event_name == 'schedule'
        run: |
          live=$(ssh droplet 'cat /opt/miniciv/REVISION 2>/dev/null || true')
          if [ "$live" = "$GITHUB_SHA" ]; then
            echo "skip=true" >> "$GITHUB_OUTPUT"
            echo "\`$GITHUB_SHA\` is already live; nothing to deploy." >> "$GITHUB_STEP_SUMMARY"
          fi

      - uses: actions/checkout@v5
        if: steps.live.outputs.skip != 'true'

      - uses: ./.github/actions/build
        if: steps.live.outputs.skip != 'true'

      - name: Copy the jar
        if: steps.live.outputs.skip != 'true'
        run: scp backend/target/miniciv-*.jar "droplet:/opt/miniciv/releases/$GITHUB_SHA.jar.tmp"

      - name: Switch to it, restart and check health
        if: steps.live.outputs.skip != 'true'
        run: |
          set +e
          ssh droplet bash -s -- "$GITHUB_SHA" > outcome.txt <<'EOF'
          set -euo pipefail
          sha=$1
          cd /opt/miniciv

          # Points current at a release, swapping the link atomically.
          point_at() { ln -sfn "releases/$1.jar" current.tmp && mv -T current.tmp current; }

          # Waits up to 90 s for the jar to answer on localhost.
          healthy() {
            local deadline=$((SECONDS + 90))
            while [ "$SECONDS" -lt "$deadline" ]; do
              curl -fs -o /dev/null --max-time 2 http://127.0.0.1:8080/api/health && return 0
              sleep 2
            done
            return 1
          }

          mv "releases/$sha.jar.tmp" "releases/$sha.jar"
          previous=$(cat REVISION 2>/dev/null || true)
          point_at "$sha"
          sudo systemctl restart miniciv

          if healthy; then
            # Written last, so after any failure REVISION still names the jar that is live.
            echo "$sha" > REVISION.tmp && mv REVISION.tmp REVISION
            live=$(readlink current)
            ls -1t releases/*.jar | tail -n +6 | while read -r jar; do
              [ "$jar" = "$live" ] || rm -- "$jar"
            done
            echo "Deployed \`$sha\`."
            exit 0
          fi

          if [ -z "$previous" ]; then
            echo "Health check failed for \`$sha\`; no previous revision to roll back to."
            exit 1
          fi
          point_at "$previous"
          sudo systemctl restart miniciv
          if healthy; then
            echo "Health check failed for \`$sha\`; rolled back to \`$previous\`."
          else
            echo "Health check failed for \`$sha\`; rollback to \`$previous\` also unhealthy."
          fi
          exit 1
          EOF
          status=$?
          cat outcome.txt
          tail -n 1 outcome.txt >> "$GITHUB_STEP_SUMMARY"
          exit $status
```

How it behaves:

- **Tests gate the deploy.** The build step fails on any failing test, and nothing after it runs. If master's tip fails, every nightly run retries it and fails, and GitHub emails each failure, until master is fixed.
- **Skipping.** A scheduled run reads `/opt/miniciv/REVISION` first and stops if it already names master's tip. The Droplet is the source of truth, so this stays right after a hand-made rollback or a rebuilt Droplet.
- **On the server**, with no script stored there: the jar arrives as `releases/<sha>.jar.tmp` and is renamed, so a half-copied jar is never live; `current` is swapped atomically; the service restarts; `/api/health` is polled on localhost every 2 s for up to 90 s.
- **Healthy** means `/api/health` answers 200. There's no public-URL check, since a deploy doesn't touch Caddy or DNS, and no SHA check: the old process is gone and `current` already points at the new jar, so whatever answers is the new jar. "Starts but broken" is left to the tests.
- **On success**, `REVISION` is written, and all but the newest 5 jars are deleted, never the one `current` points to.
- **On failure**, it makes one rollback attempt to the jar `REVISION` names, then fails the workflow either way. The step summary has one line: rolled back, rollback also unhealthy, or no previous revision (the first deploy). `REVISION` isn't written, so it still names the live jar, and the next night tries the broken tip again, failing with an email each time until master is fixed.
- **Notification** is GitHub's failed-workflow email, which links to the summary. Nothing more.
- **Minutes:** about 6–8 for a real deploy and about 1 for a skip, so at most about 240 a month, roughly a tenth of a free account's 2,000 for a private repo. Without a payment method, running out blocks runs rather than billing.
- **Secrets** are plain repository secrets, which work on any GitHub plan: `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS` and `DEPLOY_HOST`.

**Why:** [What is the deploy pipeline, end to end?][102], [How can GitHub Actions deploy to a Droplet on a schedule and on demand?][98], [How is a bad deploy detected and rolled back?][104]

### 3.4 Caddyfile

Written to `/etc/caddy/Caddyfile` by the cloud-init file ([3.6](#36-cloud-init-deploycloud-inityaml)), which holds the only copy in the repo:

```caddyfile
{
	email EMAIL
}

DOMAIN {
	encode zstd gzip
	header Strict-Transport-Security "max-age=31536000"
	request_body /api/* {
		max_size 64KB
	}
	reverse_proxy 127.0.0.1:8080 {
		stream_close_delay 4h
	}
}

www.DOMAIN {
	redir https://DOMAIN{uri} permanent
}
```

- **Certificates:** Caddy gets and renews them from Let's Encrypt by itself, falling back to ZeroSSL. It needs DNS pointing at the Droplet and ports 80 and 443 open. `EMAIL` is where expiry warnings go.
- **HTTP to HTTPS:** Caddy's automatic redirect.
- **Apex is canonical.** `www` redirects to it permanently. Serving both without a redirect would split Players across two origins, since each Room membership is kept in the browser's per-origin `localStorage`.
- **HSTS:** one year, without `includeSubDomains` or preload.
- **WebSockets** need no config: Caddy upgrades and tunnels them, keeps `Host` unchanged (Spring's same-origin check on `/ws` relies on that) and never times out an idle stream. `stream_close_delay 4h` stops a `systemctl reload caddy` from cutting off live Games for up to 4 hours.
- **Body cap:** `/api` request bodies over 64 KB are refused with 413.

**Why:** [Which domain, DNS and HTTPS setup?][103], [How should HTTPS and WebSockets be fronted on the Droplet?][99], [What light abuse protection does a widely shared miniciv need, and where does it live?][105]

### 3.5 systemd unit

Written to `/etc/systemd/system/miniciv.service` by the cloud-init file, which holds the only copy in the repo:

```ini
[Unit]
Description=miniciv
After=network.target
# Until the first deploy there is no jar to run.
ConditionPathExists=/opt/miniciv/current

[Service]
User=miniciv
Group=miniciv
Environment=SPRING_PROFILES_ACTIVE=prod
ExecStart=/usr/bin/java -XX:+UseSerialGC -XX:MaxRAMPercentage=50 -XX:+ExitOnOutOfMemoryError -jar /opt/miniciv/current
# The JVM exits with 143 when systemd stops it.
SuccessExitStatus=143
Restart=always
RestartSec=5
ProtectSystem=strict
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

- **Runtime:** Ubuntu's `openjdk-21-jre-headless`, patched by the normal OS updates. It stays on Java 21 to match CI; upgrade both together, on purpose.
- **User:** `miniciv` is a system user that can't log in and can only read `/opt/miniciv`. `ProtectSystem=strict` makes the rest of the filesystem read-only to it; `PrivateTmp=true` gives Tomcat a private, writable `/tmp`.
- **JVM:** Serial GC suits a small app on one vCPU, and stays chosen if the Droplet is resized. A heap of 50% of RAM is about 490 MB on 1 GB, leaving room for the OS, Caddy and the JVM's non-heap memory. Don't go above about 60% on 1 GB. On running out of memory the JVM exits and systemd starts it again (Games are lost either way). Measured locally, the jar uses about 128 MB committed and about 206 MB resident after startup.
- **Restarts:** `Restart=always` brings the app back after a crash; the unit is enabled, so it starts at boot.
- **Logs** go to journald, capped at 200 MB (set by the cloud-init file).

**Why:** [How does the app run and stay up on the Droplet?][100], [Which Droplet size and region suit miniciv?][97]

### 3.6 cloud-init: `deploy/cloud-init.yaml`

Pasted as user data when the Droplet is created ([4.5](#45-create-the-droplet)). It sets up the users and their keys, SSH, the deploy user's one sudo rule, packages, automatic security updates, journald, the `/opt/miniciv` layout, the systemd unit, Caddy and the Caddyfile. Five placeholders are filled in before use ([4.3](#43-fill-in-the-cloud-init-file)): `DOMAIN`, `EMAIL`, `ADMIN_USER`, `ADMIN_SSH_KEY` and `DEPLOY_SSH_KEY`.

```yaml
#cloud-config
# Sets up the miniciv Droplet; see docs/hosting.md. This is the only copy of the
# Caddyfile and the systemd unit. Replace DOMAIN, EMAIL, ADMIN_USER,
# ADMIN_SSH_KEY and DEPLOY_SSH_KEY before creating a Droplet.

groups:
  - miniciv

users:
  # You. The account has no password, so sudo can't ask for one; the
  # passphrase on your key is what guards it.
  - name: ADMIN_USER
    groups: [sudo]
    shell: /bin/bash
    lock_passwd: true
    sudo: ALL=(ALL) NOPASSWD:ALL
    ssh_authorized_keys:
      - ADMIN_SSH_KEY
  # GitHub Actions. May only copy jars in and restart the service.
  - name: deploy
    shell: /bin/bash
    lock_passwd: true
    ssh_authorized_keys:
      - restrict DEPLOY_SSH_KEY
  # Runs the app; can't log in.
  - name: miniciv
    system: true
    primary_group: miniciv
    no_create_home: true
    shell: /usr/sbin/nologin

ssh_pwauth: false
disable_root: true

package_update: true
package_upgrade: true
packages:
  - openjdk-21-jre-headless
  - curl
  - gpg
  - unattended-upgrades

write_files:
  - path: /etc/ssh/sshd_config.d/10-miniciv.conf
    content: |
      PermitRootLogin no
      PasswordAuthentication no
      KbdInteractiveAuthentication no

  - path: /etc/sudoers.d/miniciv-deploy
    permissions: '0440'
    content: |
      deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart miniciv

  - path: /etc/apt/apt.conf.d/20auto-upgrades
    content: |
      APT::Periodic::Update-Package-Lists "1";
      APT::Periodic::Unattended-Upgrade "1";

  # Also patch Caddy from its own repo, and reboot when a patch needs it, just
  # after the 04:07 UTC deploy. The Droplet's clock is UTC.
  - path: /etc/apt/apt.conf.d/52miniciv-unattended-upgrades
    content: |
      Unattended-Upgrade::Origins-Pattern {
        "site=dl.cloudsmith.io";
      };
      Unattended-Upgrade::Automatic-Reboot "true";
      Unattended-Upgrade::Automatic-Reboot-Time "04:20";

  - path: /etc/systemd/journald.conf.d/miniciv.conf
    content: |
      [Journal]
      SystemMaxUse=200M

  - path: /etc/systemd/system/miniciv.service
    content: |
      [Unit]
      Description=miniciv
      After=network.target
      # Until the first deploy there is no jar to run.
      ConditionPathExists=/opt/miniciv/current

      [Service]
      User=miniciv
      Group=miniciv
      Environment=SPRING_PROFILES_ACTIVE=prod
      ExecStart=/usr/bin/java -XX:+UseSerialGC -XX:MaxRAMPercentage=50 -XX:+ExitOnOutOfMemoryError -jar /opt/miniciv/current
      # The JVM exits with 143 when systemd stops it.
      SuccessExitStatus=143
      Restart=always
      RestartSec=5
      ProtectSystem=strict
      NoNewPrivileges=true
      PrivateTmp=true

      [Install]
      WantedBy=multi-user.target

  - path: /etc/caddy/Caddyfile
    content: |
      {
        email EMAIL
      }

      DOMAIN {
        encode zstd gzip
        header Strict-Transport-Security "max-age=31536000"
        request_body /api/* {
          max_size 64KB
        }
        reverse_proxy 127.0.0.1:8080 {
          stream_close_delay 4h
        }
      }

      www.DOMAIN {
        redir https://DOMAIN{uri} permanent
      }

runcmd:
  # deploy owns the releases, current and REVISION; the setgid bit gives every
  # new file the miniciv group, which can read but not write.
  - install -d -o deploy -g miniciv -m 2750 /opt/miniciv /opt/miniciv/releases
  # Caddy from its official apt repo, as its install docs give it. Keep the
  # Caddyfile written above rather than the package's example.
  - curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  - curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt -o /etc/apt/sources.list.d/caddy-stable.list
  - chmod o+r /usr/share/keyrings/caddy-stable-archive-keyring.gpg /etc/apt/sources.list.d/caddy-stable.list
  - apt-get update
  - DEBIAN_FRONTEND=noninteractive apt-get install -y -o Dpkg::Options::=--force-confold caddy
  - systemctl restart systemd-journald ssh
  - systemctl daemon-reload
  - systemctl enable miniciv
  - systemctl restart caddy
```

What it sets up:

- **SSH** is key-only: no root login, no passwords. Port 22 stays, with no fail2ban.
- **Three accounts.** You, with sudo and a passphrase-protected `ed25519` key whose private half never leaves your Mac. `deploy`, used only by GitHub Actions, with its own key pair whose private half is only ever an Actions secret. `miniciv`, the service user, which can't log in.
- **What `deploy` can do.** It owns `/opt/miniciv/releases`, `current` and `REVISION`, which the `miniciv` group can read. Its one sudo rule is `systemctl restart miniciv`. Its key carries `restrict` in `authorized_keys`, which turns off forwarding and terminals but still allows the `scp` and commands the deploy runs.
- **Automatic security updates** with `unattended-upgrades`, including Caddy's repo, rebooting at 04:20 UTC when a patch needs it, just after the deploy. A patched JRE takes effect at the next restart; on a night the deploy is skipped, that waits for the next deploy or reboot, which is fine.

**Why:** [How is the Droplet accessed and secured?][101], [Which domain, DNS and HTTPS setup?][103], [How does the app run and stay up on the Droplet?][100]

## 4. Provisioning checklist

Done by hand, once, after slices 3–5 are on master ([7](#7-implementation-slices)). Prices as of 2026-10-13.

### 4.1 Accounts

- [ ] Turn on two-factor authentication for DigitalOcean and for GitHub.

### 4.2 Buy the domain and make the keys

- [ ] Buy the domain at **Porkbun**. Any TLD works; check the renewal price as well as the first year's. Turn on Porkbun's free WHOIS privacy.
- [ ] Make your own key, with a passphrase:
  ```sh
  ssh-keygen -t ed25519 -f ~/.ssh/miniciv_admin -C "miniciv admin"
  ```
- [ ] Make the deploy key, without a passphrase (Actions can't type one), somewhere temporary:
  ```sh
  ssh-keygen -t ed25519 -N '' -f /tmp/miniciv_deploy -C "miniciv deploy"
  ```
  Its private half goes into a GitHub secret in [4.8](#48-add-the-actions-secrets) and is then deleted.

### 4.3 Fill in the cloud-init file

- [ ] In `deploy/cloud-init.yaml`, replace:
  - `DOMAIN` with the bought name, such as `example.com` (it appears in several places);
  - `EMAIL` with the address for certificate warnings;
  - `ADMIN_USER` with your username on the Droplet;
  - `ADMIN_SSH_KEY` with the contents of `~/.ssh/miniciv_admin.pub`;
  - `DEPLOY_SSH_KEY` with the contents of `/tmp/miniciv_deploy.pub`.
- [ ] Commit it. None of these values is secret, and the file in the repo should be what the Droplet runs.

### 4.4 Reserve the IP and set up the firewall

- [ ] **Networking → Reserved IPs:** reserve an IPv4 address in **LON1**. DNS points at this address, so recreating the Droplet never changes DNS. If the control panel only offers to reserve an IP for an existing Droplet, do this straight after [4.5](#45-create-the-droplet) instead.
- [ ] **Networking → Firewalls:** create a Cloud Firewall named `miniciv`:
  - inbound TCP 22, 80 and 443, and UDP 443 (HTTP/3), from all IPv4 and IPv6 sources;
  - outbound left at the default, everything allowed;
  - applied to the tag `miniciv`.

  There's no `ufw`. SSH can't be limited to GitHub's runners, whose addresses change every week, so it relies on key-only login.

### 4.5 Create the Droplet

- [ ] **Create → Droplets:**
  - Region: **LON1**. AMS3 or FRA1 if London is ever out of capacity; same price.
  - Image: the newest **Ubuntu LTS** offered.
  - Size: **Basic, Regular, `s-1vcpu-1gb`** (1 GB, 1 vCPU, 25 GB SSD, 1,000 GB transfer), **$6 a month**. Not the $4 512 MB size: too little room beside the OS.
  - Authentication: SSH key, choosing your `miniciv_admin` key. DigitalOcean puts it on `root`, which can't log in anyway.
  - Backups: **off**. Game state is only ever in memory, so a disk backup can't restore a Game, and this file rebuilds everything else.
  - IPv6: off.
  - Advanced options → user data: paste the filled-in `deploy/cloud-init.yaml`.
  - Hostname `miniciv`, tag `miniciv`.
- [ ] Assign the Reserved IP to the Droplet.

### 4.6 Check the host key and log in

The first SSH connection trusts whatever key the server presents, so check it against one you've seen through DigitalOcean.

- [ ] Open the Droplet's web console in the control panel and run:
  ```sh
  ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
  ```
- [ ] On your Mac, fetch the key over the network and compare its fingerprint with the one above. Stop if they differ.
  ```sh
  ssh-keyscan -t ed25519 <reserved-ip> > /tmp/miniciv_known_hosts
  ssh-keygen -lf /tmp/miniciv_known_hosts
  cat /tmp/miniciv_known_hosts >> ~/.ssh/known_hosts
  ```
- [ ] Log in and check cloud-init finished cleanly:
  ```sh
  ssh -i ~/.ssh/miniciv_admin <ADMIN_USER>@<reserved-ip>
  cloud-init status --wait          # should end in "status: done"
  sudo grep -iE 'error|warn' /var/log/cloud-init-output.log
  ```

Redo this section whenever the Droplet is rebuilt: a new Droplet has a new host key.

### 4.7 Point DNS at the Reserved IP

- [ ] In Porkbun's DNS for the domain, delete the parking records Porkbun added, then add:

  | Host | Type | Answer | TTL |
  |---|---|---|---|
  | *(blank, the apex)* | A | the Reserved IP | 600 |
  | `www` | A | the Reserved IP | 600 |

  DNS stays at Porkbun, not DigitalOcean DNS. No AAAA records (IPv6 is off) and no CAA record.
- [ ] Once `dig +short DOMAIN` and `dig +short www.DOMAIN` both give the Reserved IP, run `sudo systemctl reload caddy` on the Droplet so Caddy asks for its certificates now rather than at its next retry.

### 4.8 Add the Actions secrets

- [ ] In the GitHub repo, **Settings → Secrets and variables → Actions → New repository secret**:

  | Secret | Value |
  |---|---|
  | `DEPLOY_SSH_KEY` | the whole of `/tmp/miniciv_deploy` (the private half) |
  | `DEPLOY_KNOWN_HOSTS` | the whole of `/tmp/miniciv_known_hosts`, the line you checked in [4.6](#46-check-the-host-key-and-log-in) |
  | `DEPLOY_HOST` | the Reserved IP |

- [ ] Delete the local copies: `rm /tmp/miniciv_deploy /tmp/miniciv_deploy.pub /tmp/miniciv_known_hosts`.

### 4.9 First deploy

- [ ] **Actions → Deploy → Run workflow**, on master. With no `REVISION` yet, a failed health check has nothing to roll back to and just fails the workflow.
- [ ] Open `https://DOMAIN`.
- [ ] Optional: take one manual snapshot of the Droplet as a rebuild shortcut (about $0.06 per GB a month), and delete it once you trust the cloud-init file.

**Why:** [How is the Droplet accessed and secured?][101], [Which domain, DNS and HTTPS setup?][103], [Which Droplet size and region suit miniciv?][97], [How can GitHub Actions deploy to a Droplet on a schedule and on demand?][98]

## 5. Verification

Run these once after the first deploy.

**HTTPS and redirects**

- [ ] `curl -sI http://DOMAIN` redirects to `https://DOMAIN/`.
- [ ] `curl -sI https://www.DOMAIN` gives 301 to `https://DOMAIN/`.
- [ ] `curl -sI https://DOMAIN` gives 200 with `strict-transport-security: max-age=31536000`.
- [ ] `curl -m 5 http://<reserved-ip>:8080/` fails: the jar isn't reachable from outside.

**`/ws` through Caddy, and heartbeats**

- [ ] In two browsers, create a Room, join it, and play a Turn.
- [ ] In the browser's dev tools, the `/ws` request shows `101 Switching Protocols`, and heartbeat frames pass each way about every 10 s.
- [ ] Switch to another tab for two minutes: the Game doesn't Pause.
- [ ] Cut one browser's network (or shut the laptop): the Game Pauses for the other Player within about 30 s.
- [ ] `sudo systemctl reload caddy` during a Game: neither Player is disconnected.

**Limits**

- [ ] Run `curl -s -o /dev/null -w '%{http_code}\n' -X POST https://DOMAIN/api/rooms` six times within a minute: the sixth gives 429.
- [ ] A 70 KB body to `/api` is refused with 413:
  ```sh
  head -c 70000 /dev/zero | tr '\0' a | curl -s -o /dev/null -w '%{http_code}\n' -X POST \
    -H 'Content-Type: application/json' --data-binary @- https://DOMAIN/api/rooms/ABCD/players
  ```

**A failed deploy rolls back**

- [ ] At a quiet time, push a commit to master that passes the tests but can't answer the health check: set `server.port=8081` in `application-prod.properties` (`ProdProfileTest` uses the `prod` profile but picks a random port, so it still passes). Run Deploy by hand.
- [ ] The workflow fails, its summary says "rolled back to `<previous sha>`", GitHub emails you, the site still works, and `/opt/miniciv/REVISION` is unchanged.
- [ ] Revert the commit, run Deploy by hand again, and check it succeeds.

**The schedule and the server**

- [ ] The next morning, the Actions tab shows a Deploy run started by `schedule` shortly after 04:07 UTC. With nothing new on master its summary says the commit is already live; otherwise `REVISION` now holds master's tip.
- [ ] `sudo unattended-upgrade --dry-run --debug 2>&1 | grep -i cloudsmith` shows Caddy's repo is among those patched.
- [ ] `sudo reboot`: once it's back up, so is the site, without a deploy.

**Why:** [What form does the hosting spec take, and where does it live?][107]

## 6. Operating

### 6.1 Deploy now

**Actions → Deploy → Run workflow**, on master. It always deploys, even an unchanged commit, which also helps recover a stuck server. It ends every running Game ([ADR 0003](adr/0003-deploys-wipe-running-games.md)).

### 6.2 Roll back by hand

The normal route is `git revert` on master, then deploy by hand. In an emergency, SSH in as yourself and point `current` back at an earlier jar:

```sh
ls -lt /opt/miniciv/releases      # the newest 5 jars, named by commit
cat /opt/miniciv/REVISION         # what the last successful deploy put live

sudo -u deploy ln -sfn releases/<sha>.jar /opt/miniciv/current.tmp && sudo -u deploy mv -T /opt/miniciv/current.tmp /opt/miniciv/current
sudo systemctl restart miniciv
```

Leave `REVISION` alone, so the nightly skip check still compares against master. The next deploy replaces the hand-picked jar. There is no rollback workflow in Actions, on purpose: writing `REVISION` would make the next night redeploy the bad tip, and not writing it would leave `REVISION` describing the wrong jar.

### 6.3 Read logs

```sh
journalctl -u miniciv -f                 # the app, live
journalctl -u miniciv --since today
journalctl -u miniciv -b -1              # the app before the last reboot
journalctl -u caddy --since today        # certificates, proxy errors
systemctl status miniciv                 # running? memory?
```

A failed deploy's reason is in its Actions run summary.

### 6.4 Changing limits

To override a limit or heartbeat interval on the Droplet without a rebuild, add an environment variable to the unit. Spring maps `miniciv.limits.room-creations-per-minute` to `MINICIV_LIMITS_ROOMCREATIONSPERMINUTE`: uppercase, dots to underscores, dashes dropped.

```sh
sudo systemctl edit miniciv
#   [Service]
#   Environment=MINICIV_LIMITS_ROOMS=150
sudo systemctl restart miniciv           # ends every running Game
```

To change a default for good, change `application.properties` and let it deploy. Changing a heartbeat interval needs the frontend's values in `useRoom.ts` changed too.

### 6.5 Changing Caddy or the unit

The cloud-init file is only read when a Droplet is created, so change both the live file and `deploy/cloud-init.yaml`:

- **Caddyfile:** edit `/etc/caddy/Caddyfile`, run `caddy validate --config /etc/caddy/Caddyfile`, then `sudo systemctl reload caddy`. Live sockets survive the reload for up to 4 hours.
- **systemd unit:** edit `/etc/systemd/system/miniciv.service`, then `sudo systemctl daemon-reload && sudo systemctl restart miniciv`.

### 6.6 Recreating the Droplet

1. Create a new Droplet as in [4.5](#45-create-the-droplet), from the committed cloud-init file and with the `miniciv` tag, so the firewall applies.
2. Move the Reserved IP to it. DNS doesn't change.
3. Check its host key and replace `DEPLOY_KNOWN_HOSTS`, as in [4.6](#46-check-the-host-key-and-log-in) and [4.8](#48-add-the-actions-secrets). Remove the old line for the IP from your own `~/.ssh/known_hosts` first.
4. Run Deploy by hand, then destroy the old Droplet. Only destroying stops billing; a powered-off Droplet is still charged.

### 6.7 When memory gets tight

Signs: the app restarting in `journalctl -u miniciv` with out-of-memory errors, or `free -m` showing little left. Power the Droplet off and resize it to `s-1vcpu-2gb` ($12 a month), choosing to resize CPU and RAM only so it can be shrunk again. The JVM flags need no change.

### 6.8 Rotating the deploy key

Make a new key pair as in [4.2](#42-buy-the-domain-and-make-the-keys), replace the key line in `/home/deploy/.ssh/authorized_keys` (keeping the `restrict ` prefix) and in `deploy/cloud-init.yaml`, update `DEPLOY_SSH_KEY`, and run Deploy by hand to check it.

**Why:** [How is a bad deploy detected and rolled back?][104], [How is the Droplet accessed and secured?][101], [What light abuse protection does a widely shared miniciv need, and where does it live?][105], [Which Droplet size and region suit miniciv?][97]

## 7. Implementation slices

Carrying out this spec is its own effort, which creates these as issues. Slices 1–5 can be built and merged before the domain is bought.

1. **Abuse limits.** Everything in [2.3](#23-abuse-limits), with tests that set the limits low, the frontend showing the server's text, and the two glossary lines.
2. **STOMP heartbeats.** [2.4](#24-stomp-heartbeats), backend and frontend.
3. **The `prod` profile.** [2.1](#21-the-prod-profile). `/api/health` already exists ([2.2](#22-apihealth)).
4. **CI.** vitest in Maven ([3.1](#31-every-test-blocks-a-deploy-vitest-in-maven)), the composite build action ([3.2](#32-the-shared-build-githubactionsbuildactionyml)) and `ci.yml` ([3.3](#33-the-workflows)).
5. **Deploy files.** `deploy.yml` with the health check and rollback ([3.3](#33-the-workflows)), the Caddyfile ([3.4](#34-caddyfile)), the systemd unit ([3.5](#35-systemd-unit)) and the cloud-init file ([3.6](#36-cloud-init-deploycloud-inityaml)). Blocked by slice 4, which brings the composite action. Merging `deploy.yml` before the Droplet exists means the nightly run fails each morning, so either merge it last or provision soon after.
6. **Provisioning and first deploy.** [4](#4-provisioning-checklist) and [5](#5-verification). Human in the loop. Blocked by slices 3, 4 and 5, and by buying the domain.

**Why:** [What form does the hosting spec take, and where does it live?][107]

## 8. Settled while writing this spec

The tickets left these details open, or got them slightly wrong; this spec settles them as follows.

- **Checkout sits in each workflow**, not in the composite action: GitHub can only find a local action after the repo is checked out.
- **CI runs `./mvnw -B package`, not `clean package`.** The runner starts from a fresh checkout, so `clean` would only delete the cached Node in `backend/target`.
- **Caddy's repo is added to unattended-upgrades by `Origins-Pattern` with `site=dl.cloudsmith.io`**, rather than an `Allowed-Origins` line, which needs the repo's origin and suite names. Verification checks it works.
- **Your account has passwordless sudo.** Passwords are turned off, so sudo has nothing to ask for; the passphrase on your SSH key is the guard.
- **The unit has `ConditionPathExists=/opt/miniciv/current`**, so before the first deploy it is skipped instead of failing every 5 seconds.
- **A refused `/ws` upgrade can't show the server's text**: browsers hide a failed WebSocket handshake from the page, so the frontend shows a fixed message.
- **The heartbeat scheduler isn't a Spring bean**, so it can't change which scheduler `@Scheduled` methods run on.

[map]: https://github.com/finnjwohner/miniciv/issues/96
[97]: https://github.com/finnjwohner/miniciv/issues/97
[98]: https://github.com/finnjwohner/miniciv/issues/98
[99]: https://github.com/finnjwohner/miniciv/issues/99
[100]: https://github.com/finnjwohner/miniciv/issues/100
[101]: https://github.com/finnjwohner/miniciv/issues/101
[102]: https://github.com/finnjwohner/miniciv/issues/102
[103]: https://github.com/finnjwohner/miniciv/issues/103
[104]: https://github.com/finnjwohner/miniciv/issues/104
[105]: https://github.com/finnjwohner/miniciv/issues/105
[106]: https://github.com/finnjwohner/miniciv/issues/106
[107]: https://github.com/finnjwohner/miniciv/issues/107
