#!/usr/bin/env python3

import argparse
import os
import sys
import subprocess

SRC_MOUNT = "/root/src"


class PodmanImageBuilder:
    """Builds the podman image for Floss build environment."""

    def __init__(self, workdir, rootdir, tag):
        """ Constructor.

        Args:
            workdir: Working directory for this script. Dockerfile should exist here.
            rootdir: Root directory for Bluetooth.
            tag: Label in format |name:version|.
        """
        self.workdir = workdir
        self.rootdir = rootdir
        (self.name, self.version) = tag.split(':')
        self.build_tag = '{}:{}'.format(self.name, 'buildtemp')
        self.container_name = 'floss-buildtemp'
        self.final_tag = tag
        self.env = os.environ.copy()

        # Mark dpkg builders for podman
        self.env['LIBCHROME_DOCKER'] = '1'
        self.env['MODP_DOCKER'] = '1'

    def run_command(self, target, args, cwd=None, env=None, ignore_rc=False):
        """ Run command and stream the output.
        """
        # Set some defaults
        if not cwd:
            cwd = self.workdir
        if not env:
            env = self.env

        rc = 0
        process = subprocess.Popen(args, cwd=cwd, env=env, stdout=subprocess.PIPE)
        while True:
            line = process.stdout.readline()
            print(line.decode('utf-8'), end="")
            if not line:
                rc = process.poll()
                if rc is not None:
                    break

                time.sleep(0.1)

        if rc != 0 and not ignore_rc:
            raise Exception("{} failed. Return code is {}".format(target, rc))

    def _podman_build(self):
        self.run_command('podman build', ['podman', 'build', '-t', self.build_tag, '.'])

    def _build_dpkg_and_commit(self):
        # Try to remove any previous instance of the container that may be
        # running if this script didn't complete cleanly last time.
        self.run_command('podman stop', ['podman', 'stop', '-t', '1', self.container_name], ignore_rc=True)
        self.run_command('podman rm', ['podman', 'rm', self.container_name], ignore_rc=True)

        # Runs never terminating application on the newly built image in detached mode
        mount_str = 'type=bind,src={},dst={},readonly'.format(self.rootdir, SRC_MOUNT)
        self.run_command('podman run', [
            'podman', 'run', '--name', self.container_name, '--mount', mount_str, '-d', self.build_tag, 'tail', '-f',
            '/dev/null'
        ])

        commands = [
            # Create the output directories
            ['mkdir', '-p', '/tmp/libchrome', '/tmp/modpb64'],

            # Run the dpkg builder for modp_b64
            [f'{SRC_MOUNT}/system/build/dpkg/modp_b64/gen-src-pkg.sh', '/tmp/modpb64'],

            # Install modp_b64 since libchrome depends on it
            ['find', '/tmp/modpb64', '-name', 'modp*.deb', '-exec', 'dpkg', '-i', '{}', '+'],

            # Run the dpkg builder for libchrome
            [f'{SRC_MOUNT}/system/build/dpkg/libchrome/gen-src-pkg.sh', '/tmp/libchrome'],

            # Install libchrome.
            ['find', '/tmp/libchrome', '-name', 'libchrome_*.deb', '-exec', 'dpkg', '-i', '{}', '+'],

            # Delete intermediate files
            ['rm', '-rf', '/tmp/libchrome', '/tmp/modpb64'],
        ]

        try:
            # Run commands in container first to install everything.
            for i, cmd in enumerate(commands):
                self.run_command('podman exec #{}'.format(i), ['podman', 'exec', '-it', self.container_name] + cmd)

            # Commit changes into the final tag name
            self.run_command('podman commit', ['podman', 'commit', self.container_name, self.final_tag])
        finally:
            # Stop running the container and remove it
            self.run_command('podman stop', ['podman', 'stop', '-t', '1', self.container_name])
            self.run_command('podman rm', ['podman', 'rm', self.container_name])

    def _check_podman_runnable(self):
        try:
            subprocess.check_output(['podman', 'ps'], stderr=subprocess.STDOUT)
        except subprocess.CalledProcessError as err:
            if 'denied' in err.output.decode('utf-8'):
                print('Run script as sudo')
            else:
                print('Unexpected error: {}'.format(err.output.decode('utf-8')))

            return False

        # No exception means podman is ok
        return True

    def build(self):
        if not self._check_podman_runnable():
            return

        # First build the podman image
        self._podman_build()

        # Then build libchrome and modp-b64 inside the podman image and install
        # them. Commit those changes to the final label.
        self._build_dpkg_and_commit()


def main():
    parser = argparse.ArgumentParser(description='Build podman image for Floss build environment.')
    parser.add_argument('--tag', required=True, help='Tag for podman image. i.e. floss:latest')
    args = parser.parse_args()

    # cwd should be set to same directory as this script (that's where Dockerfile
    # is kept).
    workdir = os.path.dirname(os.path.abspath(sys.argv[0]))
    rootdir = os.path.abspath(os.path.join(workdir, '../..'))

    # Build the podman image
    pib = PodmanImageBuilder(workdir, rootdir, args.tag)
    pib.build()


if __name__ == '__main__':
    main()
