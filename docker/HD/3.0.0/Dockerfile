FROM ubuntu:22.04

USER root

RUN ln -s /usr/share/zoneinfo/America/Los_Angeles /etc/localtime && echo "America/Los_Angeles" > /etc/timezone

RUN useradd -ms /bin/bash -G sudo scaleview

WORKDIR /home/scaleview

# this will be replaced by git clone
ADD --chown=scaleview:scaleview . /home/scaleview/scaleview-core

WORKDIR /home/scaleview/scaleview-core

RUN bash provision.sh

WORKDIR /home/scaleview/scaleview-core/docker/HD/3.0.0

RUN bash setup-extras.sh

WORKDIR /home/scaleview/scaleview-core

RUN chown -R scaleview:scaleview /home/scaleview

USER scaleview