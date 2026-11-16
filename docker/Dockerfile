# The one environment for building, testing and the scripts: locally (compose.yaml), in VS Code (.devcontainer/)
# and in CI (.github/workflows/). Tool versions are not set here: Java, Node and Python come from .tool-versions
# (installed by mise), Gradle from its wrapper.
FROM ubuntu:26.04

# mise installs the tools listed in .tool-versions.
ARG MISE_VERSION=v2026.11.9
# The container user gets the host user's IDs, so files it writes into the checkout belong to you.
ARG USER_UID=1000
ARG USER_GID=1000

ENV DEBIAN_FRONTEND=noninteractive \
	LANG=C.UTF-8 \
	LC_ALL=C.UTF-8

# Basics, plus a virtual screen (Xvfb) and software OpenGL (Mesa) for the Minecraft client tests.
RUN apt-get update \
	&& apt-get install -y --no-install-recommends \
		ca-certificates curl git unzip zip xz-utils procps sudo \
		xvfb xauth libgl1 libgl1-mesa-dri libglx-mesa0 libegl1 \
		libx11-6 libxext6 libxrandr2 libxi6 libxcursor1 libxxf86vm1 libxtst6 libxrender1 libasound2t64 \
	&& rm -rf /var/lib/apt/lists/*

# Ubuntu's image already has a user with ID 1000; reuse that ID for ours.
RUN if getent passwd "$USER_UID" >/dev/null; then userdel -r "$(getent passwd "$USER_UID" | cut -d: -f1)"; fi \
	&& if getent group "$USER_GID" >/dev/null; then groupdel "$(getent group "$USER_GID" | cut -d: -f1)"; fi \
	&& groupadd --gid "$USER_GID" dev \
	&& useradd --uid "$USER_UID" --gid "$USER_GID" --create-home --shell /bin/bash dev \
	&& echo 'dev ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/dev \
	&& mkdir -p /workspace \
	&& chown dev:dev /workspace

USER dev
ENV HOME=/home/dev \
	MISE_YES=1 \
	MISE_DATA_DIR=/home/dev/.local/share/mise \
	PATH=/home/dev/.local/share/mise/shims:/home/dev/.local/bin:$PATH \
	MOD_DEV_CONTAINER=1

# The cache folder the compose volume mounts over; created here so the volume belongs to the dev user.
RUN mkdir -p /home/dev/.gradle /home/dev/.cache

RUN curl -fsSL https://mise.run | MISE_VERSION="$MISE_VERSION" MISE_INSTALL_PATH=/home/dev/.local/bin/mise sh

# The versions of .tool-versions as the user's global config, so the shims use them anywhere, also outside /workspace.
COPY --chown=dev:dev .tool-versions /tmp/tool-versions
RUN mise use -g $(awk '{print $1 "@" $2}' /tmp/tool-versions) && mise reshim \
	&& java -version && python --version \
	&& ln -s "$(mise where java)" /home/dev/.local/java
# For tools that look for a JDK there (Gradle's toolchains, VS Code's Java extension).
ENV JAVA_HOME=/home/dev/.local/java

WORKDIR /workspace
CMD ["bash"]
