FROM budtmo/docker-android:emulator_14.0

USER root

RUN echo 'root:x:0:0:root:/root:/bin/bash' >> /etc/passwd

USER androidusr

COPY run.sh /run.sh

ENTRYPOINT ["/bin/bash", "-c"]
CMD ["/run.sh"]