FROM openjdk:17-slim as Builder

RUN mkdir -p /tmp && mv ${JAVA_HOME} /tmp/jdk

FROM node:18.17.1-slim

ENV JAVA_HOME=/usr/local/jdk
ENV ECSTASY_HOME=/xqiz.it
ENV PATH $ECSTASY_HOME/bin:$JAVA_HOME/bin:$PATH

COPY --from=builder /tmp/jdk ${JAVA_HOME}
COPY build/xdk/ $ECSTASY_HOME/libexec/

RUN mkdir -p $ECSTASY_HOME/bin \
    && ln -s $ECSTASY_HOME/libexec/bin/linux_launcher $ECSTASY_HOME/bin/xec \
    && ln -s $ECSTASY_HOME/libexec/bin/linux_launcher $ECSTASY_HOME/bin/xtc \
    && ln -s $ECSTASY_HOME/libexec/bin/linux_launcher $ECSTASY_HOME/bin/xam


