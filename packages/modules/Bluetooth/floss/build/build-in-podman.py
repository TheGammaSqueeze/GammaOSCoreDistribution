#!/usr/bin/env python3

import argparse
import os
import subprocess
import sys
import time

SRC_MOUNT = "/root/src"
STAGING_MOUNT = "/root/.floss"


class FlossPodmanRunner:
    """Runs Floss build inside podman container."""

    # Commands to run for build
    BUILD_COMMANDS = [
        # First run bootstrap to get latest code + create symlinks
        [f'{SRC_MOUNT}/build.py', '--run-bootstrap'],

        # Clean up any previous artifacts inside the volume
        [f'{SRC_MOUNT}/build.py', '--target', 'clean'],

        # Run normal code builder
        [f'{SRC_MOUNT}/build.py', '--target', 'all'],

        # Run tests
        [f'{SRC_MOUNT}/build.py', '--target', 'test'],
    ]

    def __init__(self, workdir, rootdir, image_tag, volume_name, container_name, staging_dir):
        """ Constructor.

        Args:
            workdir: Current working directory (should be the script path).
            rootdir: Root directory for Bluetooth.
            image_tag: Tag for podman image used for building.
            volume_name: Volume name used for storing artifacts.
            container_name: Name for running container instance.
            staging_dir: Directory to mount for artifacts instead of using volume.
        """
        self.workdir = workdir
        self.rootdir = rootdir
        self.image_tag = image_tag
        self.env = os.environ.copy()

        # Name of running container
        self.container_name = container_name

        # Name of volume to write output.
        self.volume_name = volume_name
        # Staging dir where we send output instead of the volume.
        self.staging_dir = staging_dir

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

    def _create_volume_if_needed(self):
        # Check if the volume exists. Otherwise create it.
        try:
            subprocess.check_output(['podman', 'volume', 'inspect', self.volume_name])
        except:
            self.run_command('podman volume create', ['podman', 'volume', 'create', self.volume_name])

    def start_container(self):
        """Starts the podman container with correct mounts."""
        # Stop any previously started container.
        self.stop_container(ignore_error=True)

        # Create volume and create mount string
        if self.staging_dir:
            mount_output_volume = 'type=bind,src={},dst={}'.format(self.staging_dir, STAGING_MOUNT)
        else:
            # If not using staging dir, use the volume instead
            self._create_volume_if_needed()
            mount_output_volume = 'type=volume,src={},dst={}'.format(self.volume_name, STAGING_MOUNT)

        # Mount the source directory
        mount_src_dir = 'type=bind,src={},dst={}'.format(self.rootdir, SRC_MOUNT)

        # Run the podman image. It will run `tail` indefinitely so the container
        # doesn't close and we can run `podman exec` on it.
        self.run_command('podman run', [
            'podman', 'run', '--name', self.container_name, '--mount', mount_output_volume, '--mount', mount_src_dir,
            '-d', self.image_tag, 'tail', '-f', '/dev/null'
        ])

    def stop_container(self, ignore_error=False):
        """Stops the podman container for build."""
        self.run_command('podman stop', ['podman', 'stop', '-t', '1', self.container_name], ignore_rc=ignore_error)
        self.run_command('podman rm', ['podman', 'rm', self.container_name], ignore_rc=ignore_error)

    def do_build(self):
        """Runs the basic build commands."""
        # Start container before building
        self.start_container()

        try:
            # Run all commands
            for i, cmd in enumerate(self.BUILD_COMMANDS):
                self.run_command('podman exec #{}'.format(i), ['podman', 'exec', '-it', self.container_name] + cmd)
        finally:
            # Always stop container before exiting
            self.stop_container()

    def print_do_build(self):
        """Prints the commands for building."""
        podman_exec = ['podman', 'exec', '-it', self.container_name]
        print('Normally, build would run the following commands: \n')
        for cmd in self.BUILD_COMMANDS:
            print(' '.join(podman_exec + cmd))

    def check_podman_runnable(self):
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


if __name__ == "__main__":
    parser = argparse.ArgumentParser('Builder Floss inside podman image.')
    parser.add_argument(
        '--only-start',
        action='store_true',
        default=False,
        help='Only start the container. Prints the commands it would have ran.')
    parser.add_argument('--only-stop', action='store_true', default=False, help='Only stop the container and exit.')
    parser.add_argument('--image-tag', default='floss:latest', help='Podman image to use to build.')
    parser.add_argument(
        '--volume-tag',
        default='floss-out',
        help='Name of volume to use. This is where build artifacts will be stored by default.')
    parser.add_argument(
        '--staging-dir',
        default=None,
        help='Staging directory to use instead of volume. Build artifacts will be written here.')
    parser.add_argument('--container-name', default='floss-podman-runner', help='What to name the started container')
    args = parser.parse_args()

    # cwd should be set to same directory as this script (that's where Podmanfile
    # is kept).
    workdir = os.path.dirname(os.path.abspath(sys.argv[0]))
    rootdir = os.path.abspath(os.path.join(workdir, '../..'))

    # Determine staging directory absolute path
    staging = os.path.abspath(args.staging_dir) if args.staging_dir else None

    fdr = FlossPodmanRunner(workdir, rootdir, args.image_tag, args.volume_tag, args.container_name, staging)

    # Make sure podman is runnable before continuing
    if fdr.check_podman_runnable():
        # Handle some flags
        if args.only_start:
            fdr.start_container()
            fdr.print_do_build()
        elif args.only_stop:
            fdr.stop_container()
        else:
            fdr.do_build()
