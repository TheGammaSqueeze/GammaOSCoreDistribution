"""develop tests
"""

import os
import sys
import subprocess
import platform
import pathlib

from setuptools.command import test

import pytest
import pip_run.launch

from setuptools.command.develop import develop
from setuptools.dist import Distribution
from . import contexts
from . import namespaces

SETUP_PY = """\
from setuptools import setup

setup(name='foo',
    packages=['foo'],
)
"""

INIT_PY = """print "foo"
"""


@pytest.fixture
def temp_user(monkeypatch):
    with contexts.tempdir() as user_base:
        with contexts.tempdir() as user_site:
            monkeypatch.setattr('site.USER_BASE', user_base)
            monkeypatch.setattr('site.USER_SITE', user_site)
            yield


@pytest.fixture
def test_env(tmpdir, temp_user):
    target = tmpdir
    foo = target.mkdir('foo')
    setup = target / 'setup.py'
    if setup.isfile():
        raise ValueError(dir(target))
    with setup.open('w') as f:
        f.write(SETUP_PY)
    init = foo / '__init__.py'
    with init.open('w') as f:
        f.write(INIT_PY)
    with target.as_cwd():
        yield target


class TestDevelop:
    in_virtualenv = hasattr(sys, 'real_prefix')
    in_venv = hasattr(sys, 'base_prefix') and sys.base_prefix != sys.prefix

    def test_console_scripts(self, tmpdir):
        """
        Test that console scripts are installed and that they reference
        only the project by name and not the current version.
        """
        pytest.skip(
            "TODO: needs a fixture to cause 'develop' "
            "to be invoked without mutating environment."
        )
        settings = dict(
            name='foo',
            packages=['foo'],
            version='0.0',
            entry_points={
                'console_scripts': [
                    'foocmd = foo:foo',
                ],
            },
        )
        dist = Distribution(settings)
        dist.script_name = 'setup.py'
        cmd = develop(dist)
        cmd.ensure_finalized()
        cmd.install_dir = tmpdir
        cmd.run()
        # assert '0.0' not in foocmd_text


class TestResolver:
    """
    TODO: These tests were written with a minimal understanding
    of what _resolve_setup_path is intending to do. Come up with
    more meaningful cases that look like real-world scenarios.
    """

    def test_resolve_setup_path_cwd(self):
        assert develop._resolve_setup_path('.', '.', '.') == '.'

    def test_resolve_setup_path_one_dir(self):
        assert develop._resolve_setup_path('pkgs', '.', 'pkgs') == '../'

    def test_resolve_setup_path_one_dir_trailing_slash(self):
        assert develop._resolve_setup_path('pkgs/', '.', 'pkgs') == '../'


class TestNamespaces:
    @staticmethod
    def install_develop(src_dir, target):

        develop_cmd = [
            sys.executable,
            'setup.py',
            'develop',
            '--install-dir',
            str(target),
        ]
        with src_dir.as_cwd():
            with test.test.paths_on_pythonpath([str(target)]):
                subprocess.check_call(develop_cmd)

    @pytest.mark.skipif(
        bool(os.environ.get("APPVEYOR")),
        reason="https://github.com/pypa/setuptools/issues/851",
    )
    @pytest.mark.skipif(
        platform.python_implementation() == 'PyPy',
        reason="https://github.com/pypa/setuptools/issues/1202",
    )
    def test_namespace_package_importable(self, tmpdir):
        """
        Installing two packages sharing the same namespace, one installed
        naturally using pip or `--single-version-externally-managed`
        and the other installed using `develop` should leave the namespace
        in tact and both packages reachable by import.
        """
        pkg_A = namespaces.build_namespace_package(tmpdir, 'myns.pkgA')
        pkg_B = namespaces.build_namespace_package(tmpdir, 'myns.pkgB')
        target = tmpdir / 'packages'
        # use pip to install to the target directory
        install_cmd = [
            sys.executable,
            '-m',
            'pip',
            'install',
            str(pkg_A),
            '-t',
            str(target),
        ]
        subprocess.check_call(install_cmd)
        self.install_develop(pkg_B, target)
        namespaces.make_site_dir(target)
        try_import = [
            sys.executable,
            '-c',
            'import myns.pkgA; import myns.pkgB',
        ]
        with test.test.paths_on_pythonpath([str(target)]):
            subprocess.check_call(try_import)

        # additionally ensure that pkg_resources import works
        pkg_resources_imp = [
            sys.executable,
            '-c',
            'import pkg_resources',
        ]
        with test.test.paths_on_pythonpath([str(target)]):
            subprocess.check_call(pkg_resources_imp)

    @pytest.mark.xfail(
        platform.python_implementation() == 'PyPy',
        reason="Workaround fails on PyPy (why?)",
    )
    def test_editable_prefix(self, tmp_path, sample_project):
        """
        Editable install to a prefix should be discoverable.
        """
        prefix = tmp_path / 'prefix'

        # figure out where pip will likely install the package
        site_packages = prefix / next(
            pathlib.Path(path).relative_to(sys.prefix)
            for path in sys.path
            if 'site-packages' in path and path.startswith(sys.prefix)
        )
        site_packages.mkdir(parents=True)

        # install workaround
        pip_run.launch.inject_sitecustomize(str(site_packages))

        env = dict(os.environ, PYTHONPATH=str(site_packages))
        cmd = [
            sys.executable,
            '-m',
            'pip',
            'install',
            '--editable',
            str(sample_project),
            '--prefix',
            str(prefix),
            '--no-build-isolation',
        ]
        subprocess.check_call(cmd, env=env)

        # now run 'sample' with the prefix on the PYTHONPATH
        bin = 'Scripts' if platform.system() == 'Windows' else 'bin'
        exe = prefix / bin / 'sample'
        if sys.version_info < (3, 8) and platform.system() == 'Windows':
            exe = str(exe)
        subprocess.check_call([exe], env=env)
