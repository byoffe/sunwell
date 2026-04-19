FROM eclipse-temurin:21-jdk

ARG ASYNC_PROFILER_VERSION=4.3
ARG ASYNC_PROFILER_SHA256=69a16462c34c06ff55618f41653cffad1f8946822d30842512a3e0e774841c06

RUN apt-get update && apt-get install -y openssh-server curl && rm -rf /var/lib/apt/lists/*

RUN curl -fL "https://github.com/async-profiler/async-profiler/releases/download/v${ASYNC_PROFILER_VERSION}/async-profiler-${ASYNC_PROFILER_VERSION}-linux-x64.tar.gz" \
      -o /tmp/async-profiler.tar.gz && \
    echo "${ASYNC_PROFILER_SHA256}  /tmp/async-profiler.tar.gz" | sha256sum -c - && \
    tar xz -C /opt -f /tmp/async-profiler.tar.gz && \
    mv /opt/async-profiler-${ASYNC_PROFILER_VERSION}-linux-x64 /opt/async-profiler && \
    rm /tmp/async-profiler.tar.gz

# Create the sunwell user
RUN useradd -m -s /bin/bash sunwell && \
    echo 'sunwell:sunwell' | chpasswd

# Install the dev public key for password-free scripted access
COPY sunwell_dev_key.pub /tmp/sunwell_dev_key.pub
RUN mkdir -p /home/sunwell/.ssh && \
    cat /tmp/sunwell_dev_key.pub >> /home/sunwell/.ssh/authorized_keys && \
    chmod 700 /home/sunwell/.ssh && \
    chmod 600 /home/sunwell/.ssh/authorized_keys && \
    chown -R sunwell:sunwell /home/sunwell/.ssh && \
    rm /tmp/sunwell_dev_key.pub

# Make Java and async-profiler available in non-interactive SSH sessions
RUN echo 'JAVA_HOME=/opt/java/openjdk' >> /etc/environment && \
    echo 'PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin' >> /etc/environment && \
    echo 'ASYNC_PROFILER_HOME=/opt/async-profiler' >> /etc/environment

# SSH daemon configuration
RUN mkdir -p /var/run/sshd && \
    echo 'PermitRootLogin no' >> /etc/ssh/sshd_config

EXPOSE 22

CMD ["/usr/sbin/sshd", "-D"]
