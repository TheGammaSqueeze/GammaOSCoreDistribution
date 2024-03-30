import os

import pytest

from distutils.errors import DistutilsOptionError
from setuptools.config import expand
from setuptools.discovery import find_package_path


def write_files(files, root_dir):
    for file, content in files.items():
        path = root_dir / file
        path.parent.mkdir(exist_ok=True, parents=True)
        path.write_text(content)


def test_glob_relative(tmp_path, monkeypatch):
    files = {
        "dir1/dir2/dir3/file1.txt",
        "dir1/dir2/file2.txt",
        "dir1/file3.txt",
        "a.ini",
        "b.ini",
        "dir1/c.ini",
        "dir1/dir2/a.ini",
    }

    write_files({k: "" for k in files}, tmp_path)
    patterns = ["**/*.txt", "[ab].*", "**/[ac].ini"]
    monkeypatch.chdir(tmp_path)
    assert set(expand.glob_relative(patterns)) == files
    # Make sure the same APIs work outside cwd
    assert set(expand.glob_relative(patterns, tmp_path)) == files


def test_read_files(tmp_path, monkeypatch):

    dir_ = tmp_path / "dir_"
    (tmp_path / "_dir").mkdir(exist_ok=True)
    (tmp_path / "a.txt").touch()
    files = {
        "a.txt": "a",
        "dir1/b.txt": "b",
        "dir1/dir2/c.txt": "c"
    }
    write_files(files, dir_)

    with monkeypatch.context() as m:
        m.chdir(dir_)
        assert expand.read_files(list(files)) == "a\nb\nc"

        cannot_access_msg = r"Cannot access '.*\.\..a\.txt'"
        with pytest.raises(DistutilsOptionError, match=cannot_access_msg):
            expand.read_files(["../a.txt"])

    # Make sure the same APIs work outside cwd
    assert expand.read_files(list(files), dir_) == "a\nb\nc"
    with pytest.raises(DistutilsOptionError, match=cannot_access_msg):
        expand.read_files(["../a.txt"], dir_)


class TestReadAttr:
    def test_read_attr(self, tmp_path, monkeypatch):
        files = {
            "pkg/__init__.py": "",
            "pkg/sub/__init__.py": "VERSION = '0.1.1'",
            "pkg/sub/mod.py": (
                "VALUES = {'a': 0, 'b': {42}, 'c': (0, 1, 1)}\n"
                "raise SystemExit(1)"
            ),
        }
        write_files(files, tmp_path)

        with monkeypatch.context() as m:
            m.chdir(tmp_path)
            # Make sure it can read the attr statically without evaluating the module
            assert expand.read_attr('pkg.sub.VERSION') == '0.1.1'
            values = expand.read_attr('lib.mod.VALUES', {'lib': 'pkg/sub'})

        assert values['a'] == 0
        assert values['b'] == {42}

        # Make sure the same APIs work outside cwd
        assert expand.read_attr('pkg.sub.VERSION', root_dir=tmp_path) == '0.1.1'
        values = expand.read_attr('lib.mod.VALUES', {'lib': 'pkg/sub'}, tmp_path)
        assert values['c'] == (0, 1, 1)

    def test_import_order(self, tmp_path):
        """
        Sometimes the import machinery will import the parent package of a nested
        module, which triggers side-effects and might create problems (see issue #3176)

        ``read_attr`` should bypass these limitations by resolving modules statically
        (via ast.literal_eval).
        """
        files = {
            "src/pkg/__init__.py": "from .main import func\nfrom .about import version",
            "src/pkg/main.py": "import super_complicated_dep\ndef func(): return 42",
            "src/pkg/about.py": "version = '42'",
        }
        write_files(files, tmp_path)
        attr_desc = "pkg.about.version"
        package_dir = {"": "src"}
        # `import super_complicated_dep` should not run, otherwise the build fails
        assert expand.read_attr(attr_desc, package_dir, tmp_path) == "42"


@pytest.mark.parametrize(
    'package_dir, file, module, return_value',
    [
        ({"": "src"}, "src/pkg/main.py", "pkg.main", 42),
        ({"pkg": "lib"}, "lib/main.py", "pkg.main", 13),
        ({}, "single_module.py", "single_module", 70),
        ({}, "flat_layout/pkg.py", "flat_layout.pkg", 836),
    ]
)
def test_resolve_class(tmp_path, package_dir, file, module, return_value):
    files = {file: f"class Custom:\n    def testing(self): return {return_value}"}
    write_files(files, tmp_path)
    cls = expand.resolve_class(f"{module}.Custom", package_dir, tmp_path)
    assert cls().testing() == return_value


@pytest.mark.parametrize(
    'args, pkgs',
    [
        ({"where": ["."], "namespaces": False}, {"pkg", "other"}),
        ({"where": [".", "dir1"], "namespaces": False}, {"pkg", "other", "dir2"}),
        ({"namespaces": True}, {"pkg", "other", "dir1", "dir1.dir2"}),
        ({}, {"pkg", "other", "dir1", "dir1.dir2"}),  # default value for `namespaces`
    ]
)
def test_find_packages(tmp_path, monkeypatch, args, pkgs):
    files = {
        "pkg/__init__.py",
        "other/__init__.py",
        "dir1/dir2/__init__.py",
    }
    write_files({k: "" for k in files}, tmp_path)

    package_dir = {}
    kwargs = {"root_dir": tmp_path, "fill_package_dir": package_dir, **args}
    where = kwargs.get("where", ["."])
    assert set(expand.find_packages(**kwargs)) == pkgs
    for pkg in pkgs:
        pkg_path = find_package_path(pkg, package_dir, tmp_path)
        assert os.path.exists(pkg_path)

    # Make sure the same APIs work outside cwd
    where = [
        str((tmp_path / p).resolve()).replace(os.sep, "/")  # ensure posix-style paths
        for p in args.pop("where", ["."])
    ]

    assert set(expand.find_packages(where=where, **args)) == pkgs
