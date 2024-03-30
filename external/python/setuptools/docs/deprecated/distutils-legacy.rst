Porting from Distutils
======================

Setuptools and the PyPA have a `stated goal <https://github.com/pypa/packaging-problems/issues/127>`_ to make Setuptools the reference API for distutils.

Since the 60.0.0 release, Setuptools includes a local, vendored copy of distutils (from late copies of CPython) that is enabled by default. To disable the use of this copy of distutils when invoking setuptools, set the enviroment variable:

    SETUPTOOLS_USE_DISTUTILS=stdlib


Prefer Setuptools
-----------------

As Distutils is deprecated, any usage of functions or objects from distutils is similarly discouraged, and Setuptools aims to replace or deprecate all such uses. This section describes the recommended replacements.

``distutils.core.setup`` → ``setuptools.setup``

``distutils.cmd.Command`` → ``setuptools.Command``

``distutils.command.{build_clib,build_ext,build_py,sdist}`` → ``setuptools.command.*``

``distutils.log`` → :mod:`logging` (standard library)

``distutils.version.*`` → :doc:`packaging.version.* <packaging:version>`

``distutils.errors.*`` → ``setuptools.errors.*`` [#errors]_


Migration advice is also provided by :pep:`PEP 632 <632#migration-advice>`.

If a project relies on uses of ``distutils`` that do not have a suitable replacement above, please search the `Setuptools issue tracker <https://github.com/pypa/setuptools/issues/>`_ and file a request, describing the use-case so that Setuptools' maintainers can investigate. Please provide enough detail to help the maintainers understand how distutils is used, what value it provides, and why that behavior should be supported.


.. [#errors] Please notice errors related to the command line usage of
   ``setup.py``, such as ``DistutilsArgError``, are intentionally not exposed
   by setuptools, since this is considered a deprecated practice.
