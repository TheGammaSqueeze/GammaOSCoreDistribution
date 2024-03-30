.. _stubs:

**********
Type Stubs
**********

Introduction
============

*type stubs*, also called *stub files*, provide type information for untyped
Python packages and modules. Type stubs serve multiple purposes:

* They are the only way to add type information to extension modules.
* They can provide type information for packages that do not wish to
  add them inline.
* They can be distributed separately from the implementation.
  This allows stubs to be developed at a different pace or by different
  authors, which is especially useful when adding type annotations to
  existing packages.
* They can act as documentation, succinctly explaining the external
  API of a package, without including the implementation or private
  members.

This document aims to give guidance to both authors of type stubs and developers
of type checkers and other tools. It describes the constructs that can be used safely in type stubs,
suggests a style guide for them, and lists constructs that type
checkers are expected to support.

Type stubs that only use constructs described in this document should work with
all type checkers that also follow this document.
Type stub authors can elect to use additional constructs, but
must be prepared that some type checkers will not parse them as expected.

A type checker that conforms to this document will parse a type stub that only uses
constructs described here without error and will not interpret any
construct in a contradictory manner. However, type checkers are not
required to implement checks for all these constructs, and
can elect to ignore unsupported ones. Additionally type checkers
can support constructs not described in this document and tool authors are
encouraged to experiment with additional features.

Syntax
======

Type stubs are syntactically valid Python 3.7 files with a ``.pyi`` suffix.
The Python syntax used for type stubs is independent from the Python
versions supported by the implementation, and from the Python version the type
checker runs under (if any). Therefore, type stub authors should use the
latest available syntax features in stubs (up to Python 3.7), even if the
implementation supports older, pre-3.7 Python versions.
Type checker authors are encouraged to support syntax features from
post-3.7 Python versions, although type stub authors should not use such
features if they wish to maintain compatibility with all type checkers.

For example, Python 3.7 added the ``async`` keyword (see PEP 492 [#pep492]_).
Stub authors should use it to mark coroutines, even if the implementation
still uses the ``@coroutine`` decorator. On the other hand, type stubs should
not use the positional-only syntax from PEP 570 [#pep570]_, introduced in
Python 3.8, although type checker authors are encouraged to support it.

Stubs are treated as if ``from __future__ import annotations`` is enabled.
In particular, built-in generics, pipe union syntax (``X | Y``), and forward
references can be used.

Starting with Python 3.8, the :py:mod:`ast` module from the standard library supports
all syntax features required by this PEP. Older Python versions can use the
`typed_ast <https://pypi.org/project/typed-ast/>`_ package from the
Python Package Index, which also supports Python 3.7 syntax and ``# type``
comments.

Distribution
============

Type stubs can be distributed with or separately from the implementation;
see PEP 561 [#pep561]_ for more information. The
`typeshed <https://github.com/python/typeshed>`_ project
includes stubs for Python's standard library and several third-party
packages. The stubs for the standard library are usually distributed with type checkers and do not
require separate installation. Stubs for third-party libraries are
available on the `Python Package Index <https://pypi.org>`_. A stub package for
a library called ``widget`` will be called ``types-widget``.

Supported Constructs
====================

This sections lists constructs that type checkers will accept in type stubs.
Type stub authors can safely use these constructs. If a
construct is marked as "unspecified", type checkers may handle it
as they best see fit or report an error. Linters should usually
flag those constructs. Type stub authors should avoid using them to
ensure compatibility across type checkers.

Unless otherwise mentioned, type stubs support all features from the
``typing`` module of the latest released Python version. If a stub uses
typing features from a later Python version than what the implementation
supports, these features can be imported from ``typing_extensions`` instead
of ``typing``.

For example, a stub could use ``Literal``, introduced in Python 3.8,
for a library supporting Python 3.7+::

    from typing_extensions import Literal

    def foo(x: Literal[""]) -> int: ...

Comments
--------

Standard Python comments are accepted everywhere Python syntax allows them.

Two kinds of structured comments are accepted:

* A ``# type: X`` comment at the end of a line that defines a variable,
  declaring that the variable has type ``X``. However, PEP 526-style [#pep526]_
  variable annotations are preferred over type comments.
* A ``# type: ignore`` comment at the end of any line, which suppresses all type
  errors in that line. The type checker mypy supports suppressing certain
  type errors by using ``# type: ignore[error-type]``. This is not supported
  by other type checkers and should not be used in stubs.

Imports
-------

Type stubs distinguish between imports that are re-exported and those
that are only used internally. Imports are re-exported if they use one of these
forms:[#pep484]_

* ``import X as X``
* ``from Y import X as X``
* ``from Y import *``

Here are some examples of imports that make names available for internal use in
a stub but do not re-export them::

    import X
    from Y import X
    from Y import X as OtherX

Type aliases can be used to re-export an import under a different name::

    from foo import bar as _bar
    new_bar = _bar  # "bar" gets re-exported with the name "new_bar"

Sub-modules are always exported when they are imported in a module.
For example, consider the following file structure::

    foo/
        __init__.pyi
        bar.pyi

Then ``foo`` will export ``bar`` when one of the following constructs is used in
``__init__.pyi``::

    from . import bar
    from .bar import Bar

Stubs support customizing star import semantics by defining a module-level
variable called ``__all__``. In stubs, this must be a string list literal.
Other types are not supported. Neither is the dynamic creation of this
variable (for example by concatenation).

By default, ``from foo import *`` imports all names in ``foo`` that
do not begin with an underscore. When ``__all__`` is defined, only those names
specified in ``__all__`` are imported::

    __all__ = ['public_attr', '_private_looking_public_attr']

    public_attr: int
    _private_looking_public_attr: int
    private_attr: int

Type checkers support cyclic imports in stub files.

Built-in Generics
-----------------

PEP 585 [#pep585]_ built-in generics are supported and should be used instead
of the corresponding types from ``typing``::

    from collections import defaultdict

    def foo(t: type[MyClass]) -> list[int]: ...
    x: defaultdict[int]

Using imports from ``collections.abc`` instead of ``typing`` is
generally possible and recommended::

    from collections.abc import Iterable

    def foo(iter: Iterable[int]) -> None: ...

Unions
------

Declaring unions with ``Union`` and ``Optional`` is supported by all
type checkers. With a few exceptions [#ts-4819]_, the shorthand syntax
is also supported::

    def foo(x: int | str) -> int | None: ...  # recommended
    def foo(x: Union[int, str]) -> Optional[int]: ...  # ok

Module Level Attributes
-----------------------

Module level variables and constants can be annotated using either
type comments or variable annotation syntax::

    x: int  # recommended
    x: int = 0
    x = 0  # type: int
    x = ...  # type: int

The type of a variable is unspecified when the variable is unannotated or
when the annotation
and the assigned value disagree. As an exception, the ellipsis literal can
stand in for any type::

    x = 0  # type is unspecified
    x = ...  # type is unspecified
    x: int = ""  # type is unspecified
    x: int = ...  # type is int

Classes
-------

Class definition syntax follows general Python syntax, but type checkers
are only expected to understand the following constructs in class bodies:

* The ellipsis literal ``...`` is ignored and used for empty
  class bodies. Using ``pass`` in class bodies is undefined.
* Instance attributes follow the same rules as module level attributes
  (see above).
* Method definitions (see below) and properties.
* Method aliases.
* Inner class definitions.

More complex statements don't need to be supported::

    class Simple: ...

    class Complex(Base):
        read_write: int
        @property
        def read_only(self) -> int: ...
        def do_stuff(self, y: str) -> None: ...
        doStuff = do_stuff

The type of generic classes can be narrowed by annotating the ``self``
argument of the ``__init__`` method::

    class Foo(Generic[_T]):
        @overload
        def __init__(self: Foo[str], type: Literal["s"]) -> None: ...
        @overload
        def __init__(self: Foo[int], type: Literal["i"]) -> None: ...
        @overload
        def __init__(self, type: str) -> None: ...

The class must match the class in which it is declared. Using other classes,
including sub or super classes, will not work. In addition, the ``self``
annotation cannot contain type variables.

.. _supported-functions:

Functions and Methods
---------------------

Function and method definition syntax follows general Python syntax.
Unless an argument name is prefixed with two underscores (but not suffixed
with two underscores), it can be used as a keyword argument [#pep484]_::

    # x is positional-only
    # y can be used positionally or as keyword argument
    # z is keyword-only
    def foo(__x, y, *, z): ...

PEP 570 [#pep570]_ style positional-only parameters are currently not
supported.

If an argument or return type is unannotated, per PEP 484 [#pep484]_ its
type is assumed to be ``Any``. It is preferred to leave unknown
types unannotated rather than explicitly marking them as ``Any``, as some
type checkers can optionally warn about unannotated arguments.

If an argument has a literal or constant default value, it must match the implementation
and the type of the argument (if specified) must match the default value.
Alternatively, ``...`` can be used in place of any default value::

    # The following arguments all have type Any.
    def unannotated(a, b=42, c=...): ...
    # The following arguments all have type int.
    def annotated(a: int, b: int = 42, c: int = ...): ...
    # The following default values are invalid and the types are unspecified.
    def invalid(a: int = "", b: Foo = Foo()): ...

For a class ``C``, the type of the first argument to a classmethod is
assumed to be ``type[C]``, if unannotated. For other non-static methods,
its type is assumed to be ``C``::

    class Foo:
        def do_things(self): ...  # self has type Foo
        @classmethod
        def create_it(cls): ...  # cls has type Type[Foo]
        @staticmethod
        def utility(x): ...  # x has type Any

But::

    _T = TypeVar("_T")

    class Foo:
        def do_things(self: _T) -> _T: ...  # self has type _T
        @classmethod
        def create_it(cls: _T) -> _T: ...  # cls has type _T

PEP 612 [#pep612]_ parameter specification variables (``ParamSpec``)
are supported in argument and return types::

    _P = ParamSpec("_P")
    _R = TypeVar("_R")

    def foo(cb: Callable[_P, _R], *args: _P.args, **kwargs: _P.kwargs) -> _R: ...

However, ``Concatenate`` from PEP 612 is not yet supported; nor is using
a ``ParamSpec`` to parameterize a generic class.

PEP 647 [#pep647]_ type guards are supported.

Using a function or method body other than the ellipsis literal is currently
unspecified. Stub authors may experiment with other bodies, but it is up to
individual type checkers how to interpret them::

    def foo(): ...  # compatible
    def bar(): pass  # behavior undefined

All variants of overloaded functions and methods must have an ``@overload``
decorator::

    @overload
    def foo(x: str) -> str: ...
    @overload
    def foo(x: float) -> int: ...

The following (which would be used in the implementation) is wrong in
type stubs::

    @overload
    def foo(x: str) -> str: ...
    @overload
    def foo(x: float) -> int: ...
    def foo(x: str | float) -> Any: ...

Aliases and NewType
-------------------

Type checkers should accept module-level type aliases, optionally using
``TypeAlias`` (PEP 613 [#pep613]_), e.g.::

  _IntList = list[int]
  _StrList: TypeAlias = list[str]

Type checkers should also accept regular module-level or class-level aliases,
e.g.::

  def a() -> None: ...
  b = a

  class C:
      def f(self) -> int: ...
      g = f

A type alias may contain type variables. As per PEP 484 [#pep484]_,
all type variables must be substituted when the alias is used::

  _K = TypeVar("_K")
  _V = TypeVar("_V")
  _MyMap: TypeAlias = dict[str, dict[_K, _V]]

  # either concrete types or other type variables can be substituted
  def f(x: _MyMap[str, _V]) -> _V: ...
  # explicitly substitute in Any rather than using a bare alias
  def g(x: _MyMap[Any, Any]) -> Any: ...

Otherwise, type variables in aliases follow the same rules as type variables in
generic class definitions.

``typing.NewType`` is also supported in stubs.

Decorators
----------

Type stubs may only use decorators defined in the ``typing`` module, plus a
fixed set of additional ones:

* ``classmethod``
* ``staticmethod``
* ``property`` (including ``.setter``)
* ``abc.abstractmethod``
* ``dataclasses.dataclass``
* ``asyncio.coroutine`` (although ``async`` should be used instead)

The behavior of other decorators should instead be incorporated into the types.
For example, for the following function::

  import contextlib
  @contextlib.contextmanager
  def f():
      yield 42

the stub definition should be::

  from contextlib import AbstractContextManager
  def f() -> AbstractContextManager[int]: ...

Version and Platform Checks
---------------------------

Type stubs for libraries that support multiple Python versions can use version
checks to supply version-specific type hints. Type stubs for different Python
versions should still conform to the most recent supported Python version's
syntax, as explain in the Syntax_ section above.

Version checks are if-statements that use ``sys.version_info`` to determine the
current Python version. Version checks should only check against the ``major`` and
``minor`` parts of ``sys.version_info``. Type checkers are only required to
support the tuple-based version check syntax::

    if sys.version_info >= (3,):
        # Python 3-specific type hints. This tuple-based syntax is recommended.
    else:
        # Python 2-specific type hints.

    if sys.version_info >= (3, 5):
        # Specific minor version features can be easily checked with tuples.

    if sys.version_info < (3,):
        # This is only necessary when a feature has no Python 3 equivalent.

Type stubs should avoid checking against ``sys.version_info.major``
directly and should not use comparison operators other than ``<`` and ``>=``.

No::

    if sys.version_info.major >= 3:
        # Semantically the same as the first tuple check.

    if sys.version_info[0] >= 3:
        # This is also the same.

    if sys.version_info <= (2, 7):
        # This does not work because e.g. (2, 7, 1) > (2, 7).

Some type stubs also may need to specify type hints for different platforms.
Platform checks must be equality comparisons between ``sys.platform`` and the name
of a platform as a string literal:

Yes::

    if sys.platform == 'win32':
        # Windows-specific type hints.
    else:
        # Posix-specific type hints.

No::

    if sys.platform.startswith('linux'):
        # Not necessary since Python 3.3.

    if sys.platform in ['linux', 'cygwin', 'darwin']:
        # Only '==' or '!=' should be used in platform checks.

Version and platform comparisons can be chained using the ``and`` and ``or``
operators::

    if sys.platform == 'linux' and (sys.version_info < (3,) or sys,version_info >= (3, 7)): ...

Enums
-----

Enum classes are supported in stubs, regardless of the Python version targeted by
the stubs.

Enum members may be specified just like other forms of assignments, for example as
``x: int``, ``x = 0``, or ``x = ...``.  The first syntax is preferred because it
allows type checkers to correctly type the ``.value`` attribute of enum members,
without providing unnecessary information like the runtime value of the enum member.

Additional properties on enum members should be specified with ``@property``, so they
do not get interpreted by type checkers as enum members.

Yes::

    from enum import Enum
    
    class Color(Enum):
        RED: int
        BLUE: int
        @property
        def rgb_value(self) -> int: ...

    class Color(Enum):
        # discouraged; type checkers will not understand that Color.RED.value is an int
        RED = ...
        BLUE = ...
        @property
        def rgb_value(self) -> int: ...

No::

    from enum import Enum
    
    class Color(Enum):
        RED: int
        BLUE: int
        rgb_value: int  # no way for type checkers to know that this is not an enum member

Unsupported Features
--------------------

Currently, the following features are not supported by all type checkers
and should not be used in stubs:

* Positional-only argument syntax (PEP 570 [#pep570]_). Instead, use
  the syntax described in the section :ref:`supported-functions`.
  [#ts-4972]_

Type Stub Content
=================

This section documents best practices on what elements to include or
leave out of type stubs.

Modules excluded fom stubs
--------------------------

Not all modules should be included into stubs.

It is recommended to exclude:

1. Implementation details, with `multiprocessing/popen_spawn_win32.py <https://github.com/python/cpython/blob/main/Lib/multiprocessing/popen_spawn_win32.py>`_ as a notable example
2. Modules that are not supposed to be imported, such as ``__main__.py``
3. Protected modules that start with a single ``_`` char. However, when needed protected modules can still be added (see :ref:`undocumented-objects` section below)

Public Interface
----------------

Stubs should include the complete public interface (classes, functions,
constants, etc.) of the module they cover, but it is not always
clear exactly what is part of the interface.

The following should always be included:

* All objects listed in the module's documentation.
* All objects included in ``__all__`` (if present).

Other objects may be included if they are not prefixed with an underscore
or if they are being used in practice. (See the next section.)

.. _undocumented-objects:

Undocumented Objects
--------------------

Undocumented objects may be included as long as they are marked with a comment
of the form ``# undocumented``.

Example::

    def list2cmdline(seq: Sequence[str]) -> str: ...  # undocumented

Such undocumented objects are allowed because omitting objects can confuse
users. Users who see an error like "module X has no attribute Y" will
not know whether the error appeared because their code had a bug or
because the stub is wrong. Although it may also be helpful for a type
checker to point out usage of private objects, false negatives (no errors for
wrong code) are preferable over false positives (type errors
for correct code). In addition, even for private objects a type checker
can be helpful in pointing out that an incorrect type was used.

``__all__``
------------

A type stub should contain an ``__all__`` variable if and only if it also
present at runtime. In that case, the contents of ``__all__`` should be
identical in the stub and at runtime. If the runtime dynamically adds
or removes elements (for example if certain functions are only available on
some platforms), include all possible elements in the stubs.

Stub-Only Objects
-----------------

Definitions that do not exist at runtime may be included in stubs to aid in
expressing types. Sometimes, it is desirable to make a stub-only class available
to a stub's users - for example, to allow them to type the return value of a
public method for which a library does not provided a usable runtime type::

  from typing import Protocol

  class _Readable(Protocol):
      def read(self) -> str: ...

  def get_reader() -> _Readable: ...

Structural Types
----------------

As seen in the example with ``_Readable`` in the previous section, a common use
of stub-only objects is to model types that are best described by their
structure. These objects are called protocols [#pep544]_, and it is encouraged
to use them freely to describe simple structural types.

It is `recommended <#private-definitions>`_ to prefix stubs-only object names with ``_``.

Incomplete Stubs
----------------

Partial stubs can be useful, especially for larger packages, but they should
follow the following guidelines:

* Included functions and methods should list all arguments, but the arguments
  can be left unannotated.
* Do not use ``Any`` to mark unannotated arguments or return values.
* Partial classes should include a ``__getattr__()`` method marked with an
  ``# incomplete`` comment (see example below).
* Partial modules (i.e. modules that are missing some or all classes,
  functions, or attributes) should include a top-level ``__getattr__()``
  function marked with an ``# incomplete`` comment (see example below).
* Partial packages (i.e. packages that are missing one or more sub-modules)
  should have a ``__init__.pyi`` stub that is marked as incomplete (see above).
  A better alternative is to create empty stubs for all sub-modules and
  mark them as incomplete individually.

Example of a partial module with a partial class ``Foo`` and a partially
annotated function ``bar()``::

    def __getattr__(name: str) -> Any: ...  # incomplete

    class Foo:
        def __getattr__(self, name: str) -> Any:  # incomplete
        x: int
        y: str

    def bar(x: str, y, *, z=...): ...

The ``# incomplete`` comment is mainly intended as a reminder for stub
authors, but can be used by tools to flag such items.

Attribute Access
----------------

Python has several methods for customizing attribute access: ``__getattr__``,
``__getattribute__``, ``__setattr__``, and ``__delattr__``. Of these,
``__getattr__`` and ``__setattr___`` should sometimes be included in stubs.

In addition to marking incomplete definitions, ``__getattr__`` should be
included when a class or module allows any name to be accessed. For example, consider
the following class::

  class Foo:
      def __getattribute__(self, name):
          return self.__dict__.setdefault(name)

An appropriate stub definition is::

  from typing import Any
  class Foo:
      def __getattr__(self, name: str) -> Any | None: ...

Note that only ``__getattr__``, not ``__getattribute__``, is guaranteed to be
supported in stubs.

On the other hand, ``__getattr__`` should be omitted even if the source code
includes it, if only limited names are allowed. For example, consider this class::

  class ComplexNumber:
      def __init__(self, n):
          self._n = n
      def __getattr__(self, name):
          if name in ("real", "imag"):
              return getattr(self._n, name)
          raise AttributeError(name)

In this case, the stub should list the attributes individually::

  class ComplexNumber:
      @property
      def real(self) -> float: ...
      @property
      def imag(self) -> float: ...
      def __init__(self, n: complex) -> None: ...

``__setattr___`` should be included when a class allows any name to be set and
restricts the type. For example::

  class IntHolder:
      def __setattr__(self, name, value):
          if isinstance(value, int):
              return super().__setattr__(name, value)
          raise ValueError(value)

A good stub definition would be::

  class IntHolder:
      def __setattr__(self, name: str, value: int) -> None: ...

``__delattr__`` should not be included in stubs.

Finally, even in the presence of ``__getattr__`` and ``__setattr__``, it is
still recommended to separately define known attributes.

Constants
---------

When the value of a constant is important, annotate it using ``Literal``
instead of its type.

Yes::

    TEL_LANDLINE: Literal["landline"]
    TEL_MOBILE: Literal["mobile"]
    DAY_FLAG: Literal[0x01]
    NIGHT_FLAG: Literal[0x02]

No::

    TEL_LANDLINE: str
    TEL_MOBILE: str
    DAY_FLAG: int
    NIGHT_FLAG: int

Documentation or Implementation
-------------------------------

Sometimes a library's documented types will differ from the actual types in the
code. In such cases, type stub authors should use their best judgment. Consider
these two examples::

  def print_elements(x):
      """Print every element of list x."""
      for y in x:
          print(y)

  def maybe_raise(x):
      """Raise an error if x (a boolean) is true."""
      if x:
          raise ValueError()

The implementation of ``print_elements`` takes any iterable, despite the
documented type of ``list``. In this case, annotate the argument as
``Iterable[Any]``, to follow this PEP's style recommendation of preferring
abstract types.

For ``maybe_raise``, on the other hand, it is better to annotate the argument as
``bool`` even though the implementation accepts any object. This guards against
common mistakes like unintentionally passing in ``None``.

If in doubt, consider asking the library maintainers about their intent.

Style Guide
===========

The recommendations in this section are aimed at type stub authors
who wish to provide a consistent style for type stubs. Type checkers
should not reject stubs that do not follow these recommendations, but
linters can warn about them.

Stub files should generally follow the Style Guide for Python Code (PEP 8)
[#pep8]_. There are a few exceptions, outlined below, that take the
different structure of stub files into account and are aimed to create
more concise files.

Maximum Line Length
-------------------

Type stubs should be limited to 130 characters per line.

Blank Lines
-----------

Do not use empty lines between functions, methods, and fields, except to
group them with one empty line. Use one empty line around classes, but do not
use empty lines between body-less classes, except for grouping.

Yes::

    def time_func() -> None: ...
    def date_func() -> None: ...

    def ip_func() -> None: ...

    class Foo:
        x: int
        y: int
        def __init__(self) -> None: ...

    class MyError(Exception): ...
    class AnotherError(Exception): ...

No::

    def time_func() -> None: ...

    def date_func() -> None: ...  # do no leave unnecessary empty lines

    def ip_func() -> None: ...


    class Foo:  # leave only one empty line above
        x: int
    class MyError(Exception): ...  # leave an empty line between the classes

Shorthand Syntax
----------------

Where possible, use shorthand syntax for unions instead of
``Union`` or ``Optional``. ``None`` should be the last
element of an union.

Yes::

    def foo(x: str | int) -> None: ...
    def bar(x: str | None) -> int | None: ...

No::

    def foo(x: Union[str, int]) -> None: ...
    def bar(x: Optional[str]) -> Optional[int]: ...
    def baz(x: None | str) -> None: ...

Module Level Attributes
-----------------------

Do not use an assignment for module-level attributes.

Yes::

    CONST: Literal["const"]
    x: int

No::

    CONST = "const"
    x: int = 0
    y: float = ...
    z = 0  # type: int
    a = ...  # type: int

Type Aliases
------------

Use ``TypeAlias`` for type aliases (but not for regular aliases).

Yes::

    _IntList: TypeAlias = list[int]
    g = os.stat
    Path = pathlib.Path
    ERROR = errno.EEXIST

No::

    _IntList = list[int]
    g: TypeAlias = os.stat
    Path: TypeAlias = pathlib.Path
    ERROR: TypeAlias = errno.EEXIST

Classes
-------

Classes without bodies should use the ellipsis literal ``...`` in place
of the body on the same line as the class definition.

Yes::

    class MyError(Exception): ...

No::

    class MyError(Exception):
        ...
    class AnotherError(Exception): pass

Instance attributes and class variables follow the same recommendations as
module level attributes:

Yes::

    class Foo:
        c: ClassVar[str]
        x: int

No::

    class Foo:
        c: ClassVar[str] = ""
        d: ClassVar[int] = ...
        x = 4
        y: int = ...

Functions and Methods
---------------------

Use the same argument names as in the implementation, because
otherwise using keyword arguments will fail. Of course, this
does not apply to positional-only arguments, which are marked with a double
underscore.

Use the ellipsis literal ``...`` in place of actual default argument
values. Use an explicit ``X | None`` annotation instead of
a ``None`` default.

Yes::

    def foo(x: int = ...) -> None: ...
    def bar(y: str | None = ...) -> None: ...

No::

    def foo(x: int = 0) -> None: ...
    def bar(y: str = None) -> None: ...
    def baz(z: str | None = None) -> None: ...

Do not annotate ``self`` and ``cls`` in method definitions, except when
referencing a type variable.

Yes::

    _T = TypeVar("_T")
    class Foo:
        def bar(self) -> None: ...
        @classmethod
        def create(cls: type[_T]) -> _T: ...

No::

    class Foo:
        def bar(self: Foo) -> None: ...
        @classmethod
        def baz(cls: type[Foo]) -> int: ...

The bodies of functions and methods should consist of only the ellipsis
literal ``...`` on the same line as the closing parenthesis and colon.

Yes::

    def to_int1(x: str) -> int: ...
    def to_int2(
        x: str,
    ) -> int: ...

No::

    def to_int1(x: str) -> int:
        return int(x)
    def to_int2(x: str) -> int:
        ...
    def to_int3(x: str) -> int: pass

.. _private-definitions:

Private Definitions
-------------------

Type variables, type aliases, and other definitions that should not
be used outside the stub should be marked as private by prefixing them
with an underscore.

Yes::

    _T = TypeVar("_T")
    _DictList = Dict[str, List[Optional[int]]

No::

    T = TypeVar("T")
    DictList = Dict[str, List[Optional[int]]]

Language Features
-----------------

Use the latest language features available as outlined
in the Syntax_ section, even for stubs targeting older Python versions.
Do not use quotes around forward references and do not use ``__future__``
imports.

Yes::

    class Py35Class:
        x: int
        forward_reference: OtherClass
    class OtherClass: ...

No::

    class Py35Class:
        x = 0  # type: int
        forward_reference: 'OtherClass'
    class OtherClass: ...

Types
-----

Generally, use ``Any`` when a type cannot be expressed appropriately
with the current type system or using the correct type is unergonomic.

Use ``float`` instead of ``int | float``.
Use ``None`` instead of ``Literal[None]``.
For argument types,
use ``bytes`` instead of ``bytes | memoryview | bytearray``.

Use ``Text`` in stubs that support Python 2 when something accepts both
``str`` and ``unicode``. Avoid using ``Text`` in stubs or branches for
Python 3 only.

Yes::

    if sys.version_info < (3,):
        def foo(s: Text) -> None: ...
    else:
        def foo(s: str, *, i: int) -> None: ...
    def bar(s: Text) -> None: ...

No::

    if sys.version_info < (3,):
        def foo(s: unicode) -> None: ...
    else:
        def foo(s: Text, *, i: int) -> None: ...

For arguments, prefer protocols and abstract types (``Mapping``,
``Sequence``, ``Iterable``, etc.). If an argument accepts literally any value,
use ``object`` instead of ``Any``.

For return values, prefer concrete types (``list``, ``dict``, etc.) for
concrete implementations. The return values of protocols
and abstract base classes must be judged on a case-by-case basis.

Yes::

    def map_it(input: Iterable[str]) -> list[int]: ...
    def create_map() -> dict[str, int]: ...
    def to_string(o: object) -> str: ...  # accepts any object

No::

    def map_it(input: list[str]) -> list[int]: ...
    def create_map() -> MutableMapping[str, int]: ...
    def to_string(o: Any) -> str: ...

Maybe::

    class MyProto(Protocol):
        def foo(self) -> list[int]: ...
        def bar(self) -> Mapping[str]: ...

Avoid union return types, since they require ``isinstance()`` checks.
Use ``Any`` or ``X | Any`` if necessary.

Use built-in generics instead of the aliases from ``typing``,
where possible. See the section `Built-in Generics`_ for cases,
where it's not possible to use them.

Yes::

    from collections.abc import Iterable

    def foo(x: type[MyClass]) -> list[str]: ...
    def bar(x: Iterable[str]) -> None: ...

No::

    from typing import Iterable, List, Type

    def foo(x: Type[MyClass]) -> List[str]: ...
    def bar(x: Iterable[str]) -> None: ...

NamedTuple and TypedDict
------------------------

Use the class-based syntax for ``typing.NamedTuple`` and
``typing.TypedDict``, following the Classes section of this style guide.

Yes::

    from typing import NamedTuple, TypedDict
    class Point(NamedTuple):
        x: float
        y: float

    class Thing(TypedDict):
        stuff: str
        index: int

No::

    from typing import NamedTuple, TypedDict
    Point = NamedTuple("Point", [('x', float), ('y', float)])
    Thing = TypedDict("Thing", {'stuff': str, 'index': int})

References
==========

PEPs
----

.. [#pep8] PEP 8 -- Style Guide for Python Code, van Rossum et al. (https://www.python.org/dev/peps/pep-0008/)
.. [#pep484] PEP 484 -- Type Hints, van Rossum et al. (https://www.python.org/dev/peps/pep-0484)
.. [#pep492] PEP 492 -- Coroutines with async and await syntax, Selivanov (https://www.python.org/dev/peps/pep-0492/)
.. [#pep526] PEP 526 -- Syntax for Variable Annotations, Gonzalez et al. (https://www.python.org/dev/peps/pep-0526)
.. [#pep544] PEP 544 -- Protocols: Structural Subtyping, Levkivskyi et al. (https://www.python.org/dev/peps/pep-0544)
.. [#pep561] PEP 561 -- Distributing and Packaging Type Information, Smith (https://www.python.org/dev/peps/pep-0561)
.. [#pep570] PEP 570 -- Python Positional-Only Parameters, Hastings et al. (https://www.python.org/dev/peps/pep-0570)
.. [#pep585] PEP 585 -- Type Hinting Generics In Standard Collections, Langa (https://www.python.org/dev/peps/pep-0585)
.. [#pep604] PEP 604 -- Allow writing union types as X | Y, Prados and Moss (https://www.python.org/dev/peps/pep-0604)
.. [#pep612] PEP 612 -- Parameter Specification Variables, Mendoza (https://www.python.org/dev/peps/pep-0612)
.. [#pep613] PEP 613 -- Explicit Type Aliases, Zhu (https://www.python.org/dev/peps/pep-0613)
.. [#pep647] PEP 647 -- User-Defined Type Guards, Traut (https://www.python.org/dev/peps/pep-0647)
.. [#pep3107] PEP 3107 -- Function Annotations, Winter and Lownds (https://www.python.org/dev/peps/pep-3107)

Bugs
----

.. [#ts-4819] typeshed issue #4819 -- PEP 604 tracker (https://github.com/python/typeshed/issues/4819)
.. [#ts-4972] typeshed issue #4972 -- PEP 570 tracker (https://github.com/python/typeshed/issues/4972)

Copyright
=========

This document is placed in the public domain or under the CC0-1.0-Universal license, whichever is more permissive.
