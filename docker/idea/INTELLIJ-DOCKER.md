# Running IntelliJ IDEA in Docker

This setup allows you to run IntelliJ IDEA Community Edition 2025.4 in a Docker container with your XVM project pre-configured to use IntelliJ IDEA build mode.

## Prerequisites

### macOS
1. Install Docker Desktop: `brew install --cask docker`
2. Install XQuartz (X11 server): `brew install --cask xquartz`
3. **Log out and log back in** after installing XQuartz
4. Start XQuartz and allow network connections:
   ```bash
   open -a XQuartz
   # In XQuartz preferences: Security tab → Enable "Allow connections from network clients"
   ```

### Linux
1. Install Docker
2. X11 is usually pre-installed

## Usage

### Quick Start

```bash
./run-intellij-docker.sh
```

### Manual Steps

If you prefer to run manually:

#### macOS:
```bash
# 1. Start XQuartz and allow connections
open -a XQuartz
xhost + 127.0.0.1

# 2. Build the image
docker build -f Dockerfile.intellij -t xvm-intellij:latest .

# 3. Run the container
docker run -it --rm \
    -e DISPLAY=host.docker.internal:0 \
    -v /tmp/.X11-unix:/tmp/.X11-unix:rw \
    -v "${HOME}/.gradle:/home/developer/.gradle" \
    -v "$(pwd)/.idea:/home/developer/xvm/.idea" \
    xvm-intellij:latest \
    /home/developer/xvm
```

#### Linux:
```bash
# 1. Allow Docker to connect to X server
xhost +local:docker

# 2. Build the image
docker build -f Dockerfile.intellij -t xvm-intellij:latest .

# 3. Run the container
docker run -it --rm \
    -e DISPLAY="${DISPLAY}" \
    -v /tmp/.X11-unix:/tmp/.X11-unix:rw \
    -v "${HOME}/.gradle:/home/developer/.gradle" \
    -v "$(pwd)/.idea:/home/developer/xvm/.idea" \
    xvm-intellij:latest \
    /home/developer/xvm
```

## What This Does

1. **Installs IntelliJ IDEA 2025.4** Community Edition
2. **Pre-configures Gradle settings** to use IntelliJ IDEA mode for builds and tests:
   - `delegatedBuild = false` (use IntelliJ compiler)
   - `testRunner = PLATFORM` (use IntelliJ test runner)
3. **Copies your XVM source tree** into the container
4. **Mounts volumes** for:
   - Gradle cache (speeds up builds)
   - `.idea` directory (preserves IntelliJ settings)
5. **Sets up X11 forwarding** so the GUI displays on your host

## Troubleshooting

### IntelliJ window doesn't appear

**macOS:**
- Ensure XQuartz is running: `pgrep Xquartz`
- Check X11 permissions: `xhost + 127.0.0.1`
- Verify in XQuartz Preferences → Security: "Allow connections from network clients" is checked
- Try restarting XQuartz

**Linux:**
- Check DISPLAY variable: `echo $DISPLAY`
- Allow Docker access: `xhost +local:docker`

### "Can't connect to X11 window server"

**macOS:**
```bash
# Restart XQuartz
killall Xquartz
open -a XQuartz
xhost + 127.0.0.1
```

**Linux:**
```bash
xhost +local:docker
```

### Slow performance

The container uses volume mounts for `.gradle` cache. First-time builds will be slower as Gradle downloads dependencies.

### Checking if X11 forwarding works

Test with a simple X11 app:
```bash
docker run -it --rm \
    -e DISPLAY=host.docker.internal:0 \
    -v /tmp/.X11-unix:/tmp/.X11-unix:rw \
    xvm-intellij:latest \
    xclock
```

You should see a clock window appear.

## Verifying the Configuration

Once IntelliJ opens:

1. Wait for project indexing to complete
2. Go to **Preferences → Build, Execution, Deployment → Build Tools → Gradle**
3. Verify:
   - **Build and run using:** is set to `IntelliJ IDEA`
   - **Run tests using:** is set to `IntelliJ IDEA`
4. Check for the `.gradle.kts` compilation errors you were seeing

## Cleaning Up

```bash
# Stop the container (if running in background)
docker stop xvm-intellij

# Remove the image
docker rmi xvm-intellij:latest

# Revoke X11 access (macOS)
xhost - 127.0.0.1
```
