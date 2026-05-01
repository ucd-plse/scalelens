FROM ubuntu:22.04

USER root

RUN ln -s /usr/share/zoneinfo/America/Los_Angeles /etc/localtime && echo "America/Los_Angeles" > /etc/timezone

RUN useradd -ms /bin/bash -G sudo scaleview

WORKDIR /home/scaleview

ADD --chown=scaleview:scaleview . /home/scaleview/scaleview-core

WORKDIR /home/scaleview/scaleview-core

RUN chown -R scaleview:scaleview /home/scaleview

RUN bash provision.sh

USER scaleview