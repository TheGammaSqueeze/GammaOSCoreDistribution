#!/bin/sh
# Copyright (c) 2018-2020 Petr Vorel <pvorel@suse.cz>
set -ex

# workaround for missing oldstable-updates repository
# W: Failed to fetch http://deb.debian.org/debian/dists/oldstable-updates/main/binary-amd64/Packages
grep -v oldstable-updates /etc/apt/sources.list > /tmp/sources.list && mv /tmp/sources.list /etc/apt/sources.list

apt update

apt="apt install -y --no-install-recommends"

$apt \
	acl-dev \
	asciidoc \
	asciidoctor \
	autoconf \
	automake \
	build-essential \
	debhelper \
	devscripts \
	clang \
	gcc \
	git \
	libacl1 \
	libacl1-dev \
	libaio-dev \
	libaio1 \
	libcap-dev \
	libcap2 \
	libc6 \
	libc6-dev \
	libjson-perl \
	libkeyutils-dev \
	libkeyutils1 \
	libmm-dev \
	libmnl-dev \
	libnuma-dev \
	libnuma1 \
	libselinux1-dev \
	libsepol1-dev \
	libssl-dev \
	libtirpc-dev \
	linux-libc-dev \
	lsb-release \
	pkg-config

$apt ruby-asciidoctor-pdf || true

df -hT
