FROM eclipse-temurin:21-jdk

RUN apt-get update && apt-get install -y openssh-server && rm -rf /var/lib/apt/lists/*

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

# Make Java available in non-interactive SSH sessions
RUN echo 'JAVA_HOME=/opt/java/openjdk' >> /etc/environment && \
    echo 'PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin' >> /etc/environment

# SSH daemon configuration
RUN mkdir -p /var/run/sshd && \
    echo 'PermitRootLogin no' >> /etc/ssh/sshd_config

EXPOSE 22

CMD ["/usr/sbin/sshd", "-D"]
