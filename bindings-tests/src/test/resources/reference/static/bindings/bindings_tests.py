from __future__ import annotations
from ctypes import *
from enum import IntEnum
import os
import platform
from typing import Sequence, Tuple, Any

# Load the shared library relative to this file
def load_library():
    lib_name = 'bindings_tests'
    version = '1.2.3'
    build_number = '42'

    architecture = platform.architecture()[0]  # gets '64bit' or '32bit'
    os_name = platform.system()  # gets 'Windows', 'Linux', 'Darwin'

    # Determine the current platform
    if os_name == 'Windows' and architecture == '64bit':
        lib_platform = 'win_amd64'
        lib_file = lib_name + '.dll'
        cache_home = os.getenv('LOCALAPPDATA')
    elif os_name == 'Linux' and architecture == '64bit':
        lib_platform = 'linux_amd64'
        lib_file = 'lib' + lib_name + '.so'
        cache_home = os.getenv('XDG_CACHE_HOME')
        if not cache_home:
            cache_home = os.path.join(os.getenv('HOME'), '.cache')
    elif os_name == 'Darwin' and architecture == '64bit':  # Darwin is the underlying system for macOS.
        if platform.machine() in ['arm64', 'aarch64']:  # Apple Silicon
            lib_platform = 'osx_arm64'
        else:  # Intel Mac
            lib_platform = 'osx_amd64'
        lib_file = 'lib' + lib_name + '.dylib'
        cache_home = os.path.join(os.getenv('HOME'), 'Library', 'Caches')
    else:
        raise Exception('Unsupported system: ' + os_name + ' ' + platform.machine())

    # Prioritize locally bundled lib binaries
    this_file_dir = os.path.dirname(os.path.realpath(__file__))
    lib_path = os.path.join(this_file_dir, 'lib', lib_platform, lib_file)

    # Fall back to shared downloaded version in local cache
    if not os.path.exists(lib_path):
        download_dir = os.path.join(cache_home, lib_name, f'{version}-build{build_number}')
        lib_path = os.path.join(download_dir, lib_name, 'lib', lib_platform, lib_file)

        # If necessary, start a new download it into a shared dir. All files are
        # kept so we can share a single download for all languages.
        if not os.path.exists(lib_path):
            import urllib.request
            import zipfile

            # Build download url
            dl_name = f"{lib_name}-{version}-{lib_platform}.zip"
            url = f"https://files.hebi.us/download/{lib_name}/snapshot/{build_number}/{dl_name}"

            # Download zip file
            print(f"Downloading {url}")
            os.makedirs(download_dir, exist_ok=True)
            zip_path = os.path.join(download_dir, dl_name)

            # urllib may fail on some platforms due to missing SSL certificates (e.g. macOS brew),
            # so we provide a curl fallback that should be available on most systems.
            try:
                urllib.request.urlretrieve(
                    url,
                    zip_path,
                    lambda count, block_size, total_size: print(
                        f"\rDownloading... {int(count * block_size * 100 / total_size)}% ", end="")
                )
                print('')
            except Exception as e:
                print(f"\nurllib download failed ({e}), retrying with curl...")
                import subprocess
                subprocess.run(["curl", "-L", "-f", "-o", zip_path, url], check=True)

            # Extract the entire archive
            print(f'Extracting to {download_dir}')
            with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                zip_ref.extractall(download_dir)

            print(f'Removing temporary files')
            os.remove(zip_path)

    # Sanity check due to loadlibrary errors being harder to parse
    if not os.path.exists(lib_path):
        raise Exception(f'Required binary was not found: {lib_path}')

    # Load the library
    return cdll.LoadLibrary(lib_path)

libbindings_tests = load_library()

# Optional lazy-loaded numpy dependency for bulk methods
_numpy = None
def _require_numpy():
    global _numpy
    if _numpy is None:
        try:
            import numpy as np
            _numpy = np
        except ImportError:
            raise ImportError("This method requires 'numpy'. Please run: pip install numpy")
    return _numpy

# API definitions
class struct_Counter_(Structure):
    pass
CounterPtr = POINTER(struct_Counter_)

class struct_Shape_(Structure):
    pass
ShapePtr = POINTER(struct_Shape_)

class struct_Circle_(Structure):
    pass
CirclePtr = POINTER(struct_Circle_)

class struct_Square_(Structure):
    pass
SquarePtr = POINTER(struct_Square_)

class struct_Arrays_(Structure):
    pass
ArraysPtr = POINTER(struct_Arrays_)

class struct_Callbacks_(Structure):
    pass
CallbacksPtr = POINTER(struct_Callbacks_)

class struct_Errors_(Structure):
    pass
ErrorsPtr = POINTER(struct_Errors_)

class struct_Isolated_(Structure):
    pass
IsolatedPtr = POINTER(struct_Isolated_)

class struct_Math_(Structure):
    pass
MathPtr = POINTER(struct_Math_)

class struct_Overloads_(Structure):
    pass
OverloadsPtr = POINTER(struct_Overloads_)

class struct_Palette_(Structure):
    pass
PalettePtr = POINTER(struct_Palette_)

class struct_Strings_(Structure):
    pass
StringsPtr = POINTER(struct_Strings_)

IndexCallbackFunction = CFUNCTYPE(None, c_void_p, c_int)

# ==== Counter ====
libbindings_tests.bindings_tests_Counter_create.argtypes = [c_int]
libbindings_tests.bindings_tests_Counter_create.restype = CounterPtr
libbindings_tests.bindings_tests_Counter_getValue.argtypes = [CounterPtr]
libbindings_tests.bindings_tests_Counter_getValue.restype = c_int
libbindings_tests.bindings_tests_Counter_setValue.argtypes = [CounterPtr, c_int]
libbindings_tests.bindings_tests_Counter_setValue.restype = None
libbindings_tests.bindings_tests_Counter_increment.argtypes = [CounterPtr]
libbindings_tests.bindings_tests_Counter_increment.restype = None
libbindings_tests.bindings_tests_Counter_add.argtypes = [CounterPtr, c_int]
libbindings_tests.bindings_tests_Counter_add.restype = None
libbindings_tests.bindings_tests_Counter_isClosed.argtypes = [CounterPtr]
libbindings_tests.bindings_tests_Counter_isClosed.restype = c_int
libbindings_tests.bindings_tests_Counter_getClosedCount.argtypes = []
libbindings_tests.bindings_tests_Counter_getClosedCount.restype = c_int
libbindings_tests.bindings_tests_Counter_resetClosedCount.argtypes = []
libbindings_tests.bindings_tests_Counter_resetClosedCount.restype = None
libbindings_tests.bindings_tests_Counter_release.argtypes = [CounterPtr]
libbindings_tests.bindings_tests_Counter_release.restype = None

# ==== Shape ====
libbindings_tests.bindings_tests_Shape_getName.argtypes = [ShapePtr]
libbindings_tests.bindings_tests_Shape_getName.restype = c_char_p
libbindings_tests.bindings_tests_Shape_setName.argtypes = [ShapePtr, c_char_p]
libbindings_tests.bindings_tests_Shape_setName.restype = None
libbindings_tests.bindings_tests_Shape_area.argtypes = [ShapePtr]
libbindings_tests.bindings_tests_Shape_area.restype = c_double
libbindings_tests.bindings_tests_Shape_sideCount.argtypes = [ShapePtr]
libbindings_tests.bindings_tests_Shape_sideCount.restype = c_int
libbindings_tests.bindings_tests_Shape_release.argtypes = [ShapePtr]
libbindings_tests.bindings_tests_Shape_release.restype = None

# ==== Circle ====
libbindings_tests.bindings_tests_Circle_create.argtypes = [c_double]
libbindings_tests.bindings_tests_Circle_create.restype = CirclePtr
libbindings_tests.bindings_tests_Circle_getRadius.argtypes = [CirclePtr]
libbindings_tests.bindings_tests_Circle_getRadius.restype = c_double
libbindings_tests.bindings_tests_Circle_area.argtypes = [CirclePtr]
libbindings_tests.bindings_tests_Circle_area.restype = c_double
libbindings_tests.bindings_tests_Circle_sideCount.argtypes = [CirclePtr]
libbindings_tests.bindings_tests_Circle_sideCount.restype = c_int
libbindings_tests.bindings_tests_Circle_to_Shape.argtypes = [CirclePtr]
libbindings_tests.bindings_tests_Circle_to_Shape.restype = ShapePtr

# ==== Square ====
libbindings_tests.bindings_tests_Square_create.argtypes = [c_double]
libbindings_tests.bindings_tests_Square_create.restype = SquarePtr
libbindings_tests.bindings_tests_Square_getSide.argtypes = [SquarePtr]
libbindings_tests.bindings_tests_Square_getSide.restype = c_double
libbindings_tests.bindings_tests_Square_area.argtypes = [SquarePtr]
libbindings_tests.bindings_tests_Square_area.restype = c_double
libbindings_tests.bindings_tests_Square_sideCount.argtypes = [SquarePtr]
libbindings_tests.bindings_tests_Square_sideCount.restype = c_int
libbindings_tests.bindings_tests_Square_to_Shape.argtypes = [SquarePtr]
libbindings_tests.bindings_tests_Square_to_Shape.restype = ShapePtr

# ==== Arrays ====
libbindings_tests.bindings_tests_Arrays_sumFloat64.argtypes = [POINTER(c_double), c_size_t]
libbindings_tests.bindings_tests_Arrays_sumFloat64.restype = c_double
libbindings_tests.bindings_tests_Arrays_sumInt32.argtypes = [POINTER(c_int), c_size_t]
libbindings_tests.bindings_tests_Arrays_sumInt32.restype = c_int
libbindings_tests.bindings_tests_Arrays_fillFloat64.argtypes = [POINTER(c_double), c_size_t, c_double]
libbindings_tests.bindings_tests_Arrays_fillFloat64.restype = None
libbindings_tests.bindings_tests_Arrays_cumulativeSum.argtypes = [POINTER(c_double), POINTER(c_double), c_size_t]
libbindings_tests.bindings_tests_Arrays_cumulativeSum.restype = None
libbindings_tests.bindings_tests_Arrays_reverseFloat64.argtypes = [POINTER(c_double), c_size_t]
libbindings_tests.bindings_tests_Arrays_reverseFloat64.restype = None

# ==== Callbacks ====
libbindings_tests.bindings_tests_Callbacks_invokeTimes.argtypes = [IndexCallbackFunction, c_void_p, c_size_t]
libbindings_tests.bindings_tests_Callbacks_invokeTimes.restype = None
libbindings_tests.bindings_tests_Callbacks_invokeOnce.argtypes = [IndexCallbackFunction, c_void_p, c_int]
libbindings_tests.bindings_tests_Callbacks_invokeOnce.restype = None

# ==== Errors ====
libbindings_tests.bindings_tests_Errors_requireNonNegative.argtypes = [c_int]
libbindings_tests.bindings_tests_Errors_requireNonNegative.restype = c_int
libbindings_tests.bindings_tests_Errors_divideInt32.argtypes = [c_int, c_int]
libbindings_tests.bindings_tests_Errors_divideInt32.restype = c_int
libbindings_tests.bindings_tests_Errors_failingFloat64.argtypes = [c_int]
libbindings_tests.bindings_tests_Errors_failingFloat64.restype = c_double
libbindings_tests.bindings_tests_Errors_failingBool.argtypes = [c_int]
libbindings_tests.bindings_tests_Errors_failingBool.restype = c_int
libbindings_tests.bindings_tests_Errors_failingString.argtypes = [c_int]
libbindings_tests.bindings_tests_Errors_failingString.restype = c_char_p
libbindings_tests.bindings_tests_Errors_neverThrows.argtypes = []
libbindings_tests.bindings_tests_Errors_neverThrows.restype = None
libbindings_tests.bindings_tests_Errors_clearLastError.argtypes = []
libbindings_tests.bindings_tests_Errors_clearLastError.restype = None
libbindings_tests.bindings_tests_Errors_getLastError.argtypes = []
libbindings_tests.bindings_tests_Errors_getLastError.restype = c_char_p

# ==== Isolated ====
libbindings_tests.bindings_tests_Isolated_increment.argtypes = []
libbindings_tests.bindings_tests_Isolated_increment.restype = None
libbindings_tests.bindings_tests_Isolated_getCounter.argtypes = []
libbindings_tests.bindings_tests_Isolated_getCounter.restype = c_int
libbindings_tests.bindings_tests_Isolated_reset.argtypes = []
libbindings_tests.bindings_tests_Isolated_reset.restype = None
libbindings_tests.bindings_tests_Isolated_alwaysFails.argtypes = []
libbindings_tests.bindings_tests_Isolated_alwaysFails.restype = c_int
libbindings_tests.bindings_tests_Isolated_getLastError.argtypes = []
libbindings_tests.bindings_tests_Isolated_getLastError.restype = c_char_p

# ==== Math ====
libbindings_tests.bindings_tests_Math_negate.argtypes = [c_int]
libbindings_tests.bindings_tests_Math_negate.restype = c_int
libbindings_tests.bindings_tests_Math_nextChar.argtypes = [c_short]
libbindings_tests.bindings_tests_Math_nextChar.restype = c_short
libbindings_tests.bindings_tests_Math_addInt8.argtypes = [c_byte, c_byte]
libbindings_tests.bindings_tests_Math_addInt8.restype = c_byte
libbindings_tests.bindings_tests_Math_addInt16.argtypes = [c_short, c_short]
libbindings_tests.bindings_tests_Math_addInt16.restype = c_short
libbindings_tests.bindings_tests_Math_addInt32.argtypes = [c_int, c_int]
libbindings_tests.bindings_tests_Math_addInt32.restype = c_int
libbindings_tests.bindings_tests_Math_addInt64.argtypes = [c_longlong, c_longlong]
libbindings_tests.bindings_tests_Math_addInt64.restype = c_longlong
libbindings_tests.bindings_tests_Math_addFloat32.argtypes = [c_float, c_float]
libbindings_tests.bindings_tests_Math_addFloat32.restype = c_float
libbindings_tests.bindings_tests_Math_addFloat64.argtypes = [c_double, c_double]
libbindings_tests.bindings_tests_Math_addFloat64.restype = c_double
libbindings_tests.bindings_tests_Math_addSize.argtypes = [c_size_t, c_size_t]
libbindings_tests.bindings_tests_Math_addSize.restype = c_size_t
libbindings_tests.bindings_tests_Math_sizeToInt32.argtypes = [c_size_t]
libbindings_tests.bindings_tests_Math_sizeToInt32.restype = c_int
libbindings_tests.bindings_tests_Math_maxInt32.argtypes = []
libbindings_tests.bindings_tests_Math_maxInt32.restype = c_int
libbindings_tests.bindings_tests_Math_maxSize.argtypes = []
libbindings_tests.bindings_tests_Math_maxSize.restype = c_size_t

# ==== Overloads ====
libbindings_tests.bindings_tests_Overloads_scale.argtypes = [c_int, c_int]
libbindings_tests.bindings_tests_Overloads_scale.restype = c_int
libbindings_tests.bindings_tests_Overloads_greet.argtypes = [c_char_p, c_char_p]
libbindings_tests.bindings_tests_Overloads_greet.restype = c_char_p
libbindings_tests.bindings_tests_Overloads_total.argtypes = [c_int, c_int]
libbindings_tests.bindings_tests_Overloads_total.restype = c_int
libbindings_tests.bindings_tests_Overloads_answer.argtypes = [c_int]
libbindings_tests.bindings_tests_Overloads_answer.restype = c_int
libbindings_tests.bindings_tests_Overloads_renamed.argtypes = []
libbindings_tests.bindings_tests_Overloads_renamed.restype = c_int

# ==== Palette ====
libbindings_tests.bindings_tests_Palette_next.argtypes = [c_int32]
libbindings_tests.bindings_tests_Palette_next.restype = c_int32
libbindings_tests.bindings_tests_Palette_nameOf.argtypes = [c_int32]
libbindings_tests.bindings_tests_Palette_nameOf.restype = c_char_p
libbindings_tests.bindings_tests_Palette_ordinalOf.argtypes = [c_int32]
libbindings_tests.bindings_tests_Palette_ordinalOf.restype = c_int
libbindings_tests.bindings_tests_Palette_fromOrdinal.argtypes = [c_int]
libbindings_tests.bindings_tests_Palette_fromOrdinal.restype = c_int32

# ==== Strings ====
libbindings_tests.bindings_tests_Strings_echo.argtypes = [c_char_p]
libbindings_tests.bindings_tests_Strings_echo.restype = c_char_p
libbindings_tests.bindings_tests_Strings_concat.argtypes = [c_char_p, c_char_p]
libbindings_tests.bindings_tests_Strings_concat.restype = c_char_p
libbindings_tests.bindings_tests_Strings_utf8Length.argtypes = [c_char_p]
libbindings_tests.bindings_tests_Strings_utf8Length.restype = c_size_t
libbindings_tests.bindings_tests_Strings_utf16Length.argtypes = [c_char_p]
libbindings_tests.bindings_tests_Strings_utf16Length.restype = c_size_t
libbindings_tests.bindings_tests_Strings_empty.argtypes = []
libbindings_tests.bindings_tests_Strings_empty.restype = c_char_p
libbindings_tests.bindings_tests_Strings_nullString.argtypes = []
libbindings_tests.bindings_tests_Strings_nullString.restype = c_char_p
libbindings_tests.bindings_tests_Strings_join.argtypes = [POINTER(c_char_p), c_size_t, c_char_p]
libbindings_tests.bindings_tests_Strings_join.restype = c_char_p
libbindings_tests.bindings_tests_Strings_repeat.argtypes = [c_char_p, c_size_t]
libbindings_tests.bindings_tests_Strings_repeat.restype = c_char_p

# ==== Enum Wrappers ====
class Color(IntEnum):
    RED = 0
    GREEN = 1
    BLUE = 2


# ==== Class Wrappers ====
class Counter:
    """A mutable counter, used to check handle creation and release."""

    __slots__ = ('ptr',)

    def __init__(self, start: int = 0, *, value: int = None) -> None:
        """Creates a counter starting at the given value, or at zero by default.
        
        Args:
            start: 
        
        Raises:
            Exception: on internal errors
        """
        ptr_ = libbindings_tests.bindings_tests_Counter_create(start)
        if not ptr_:
            raise RuntimeError(f'Failed to create Counter in Counter::__init__ ({libbindings_tests.bindings_tests_Errors_getLastError().decode("utf-8")})')
        self.ptr = ptr_
        if value is not None: self.value = value

    def __del__(self):
        if self.ptr is not None:
            libbindings_tests.bindings_tests_Counter_release(self.ptr)
        self.ptr = None

    def __repr__(self):
        return f"{self.__class__.__name__}(value={self.value!r})"

    @property
    def value(self) -> int:
        return libbindings_tests.bindings_tests_Counter_getValue(self.ptr)

    @value.setter
    def value(self, value: int):
        libbindings_tests.bindings_tests_Counter_setValue(self.ptr, value)

    def increment(self) -> None:
        libbindings_tests.bindings_tests_Counter_increment(self.ptr)

    def add(self, amount: int) -> None:
        libbindings_tests.bindings_tests_Counter_add(self.ptr, amount)

    def is_closed(self) -> bool:
        """Returns true once close() has run. Only observable before the handle is released."""
        return libbindings_tests.bindings_tests_Counter_isClosed(self.ptr) != 0

    @classmethod
    def get_closed_count(cls) -> int:
        """Total number of counters that have been released so far in this isolate.
        Increments exactly once per released handle, so tests can verify that the
        generated destructor ran.
        """
        return libbindings_tests.bindings_tests_Counter_getClosedCount()

    @classmethod
    def reset_closed_count(cls) -> None:
        """Resets the released counter, so tests do not depend on execution order."""
        libbindings_tests.bindings_tests_Counter_resetClosedCount()


class Shape:
    """Base class for the shape hierarchy."""

    __slots__ = ('ptr',)

    def __init__(self, ptr: ShapePtr):
        if not isinstance(ptr, ShapePtr):
            raise TypeError(f'Expected ShapePtr, got {type(ptr).__name__}')
        self.ptr = ptr

    def __del__(self):
        if self.ptr is not None:
            libbindings_tests.bindings_tests_Shape_release(self.ptr)
        self.ptr = None

    def __repr__(self):
        return f"{self.__class__.__name__}(name={self.name!r})"

    @property
    def name(self) -> str:
        ptr_ = libbindings_tests.bindings_tests_Shape_getName(self.ptr)
        return ptr_.decode("utf-8") if ptr_ is not None else ""

    @name.setter
    def name(self, name: str):
        if name is not None and not isinstance(name, c_char_p):
            name = c_char_p(name.encode('utf-8') if isinstance(name, str) else name)
        libbindings_tests.bindings_tests_Shape_setName(self.ptr, name)

    def area(self) -> float:
        """Area of the shape. Dispatches to the concrete subclass."""
        return libbindings_tests.bindings_tests_Shape_area(self.ptr)

    def side_count(self) -> int:
        """Number of sides. Zero for shapes without straight edges."""
        return libbindings_tests.bindings_tests_Shape_sideCount(self.ptr)


class Circle(Shape):
    """A circle. Inherits name and area from Shape."""

    __slots__ = ('ref_Circle',)

    def __init__(self, radius: float, *, name: str = None) -> None:
        ptr_ = libbindings_tests.bindings_tests_Circle_create(radius)
        if not ptr_:
            raise RuntimeError(f'Failed to create Circle in Circle::__init__ ({libbindings_tests.bindings_tests_Errors_getLastError().decode("utf-8")})')
        self.ref_Circle = ptr_
        if name is not None: self.name = name


        super().__init__(libbindings_tests.bindings_tests_Circle_to_Shape(ref_Circle))    def __del__(self):
        super().__del__()
        self.ref_Circle = None

    def __repr__(self):
        return f"{self.__class__.__name__}(radius={self.radius!r}, name={self.name!r})"

    @property
    def radius(self) -> float:
        return libbindings_tests.bindings_tests_Circle_getRadius(self.ref_Circle)

    def area(self) -> float:
        return libbindings_tests.bindings_tests_Circle_area(self.ref_Circle)

    def side_count(self) -> int:
        return libbindings_tests.bindings_tests_Circle_sideCount(self.ref_Circle)


class Square(Shape):
    """A square. Inherits name and area from Shape."""

    __slots__ = ('ref_Square',)

    def __init__(self, side: float, *, name: str = None) -> None:
        ptr_ = libbindings_tests.bindings_tests_Square_create(side)
        if not ptr_:
            raise RuntimeError(f'Failed to create Square in Square::__init__ ({libbindings_tests.bindings_tests_Errors_getLastError().decode("utf-8")})')
        self.ref_Square = ptr_
        if name is not None: self.name = name


        super().__init__(libbindings_tests.bindings_tests_Square_to_Shape(ref_Square))    def __del__(self):
        super().__del__()
        self.ref_Square = None

    def __repr__(self):
        return f"{self.__class__.__name__}(side={self.side!r}, name={self.name!r})"

    @property
    def side(self) -> float:
        return libbindings_tests.bindings_tests_Square_getSide(self.ref_Square)

    def area(self) -> float:
        return libbindings_tests.bindings_tests_Square_area(self.ref_Square)

    def side_count(self) -> int:
        return libbindings_tests.bindings_tests_Square_sideCount(self.ref_Square)


class Arrays:
    """Bulk array operations over caller-owned buffers."""

    def __new__(cls, *args, **kwargs):
        raise TypeError('Class is not instantiable')

    @classmethod
    def sum_float64(cls, values: Sequence[float], count: int) -> float:
        np = _require_numpy()
        if values is not None and not isinstance(values, POINTER(c_double)):
            values = np.ascontiguousarray(values, dtype=np.float64).ctypes.data_as(POINTER(c_double))
        return libbindings_tests.bindings_tests_Arrays_sumFloat64(values, count)

    @classmethod
    def sum_int32(cls, values: Sequence[int], count: int) -> int:
        np = _require_numpy()
        if values is not None and not isinstance(values, POINTER(c_int)):
            values = np.ascontiguousarray(values, dtype=np.int32).ctypes.data_as(POINTER(c_int))
        return libbindings_tests.bindings_tests_Arrays_sumInt32(values, count)

    @classmethod
    def fill_float64(cls, target: Sequence[float], count: int, value: float) -> None:
        """Writes value into every element of target. Checks output buffer handling.
        
        Args:
            target: 
            count: 
            value:
        """
        np = _require_numpy()
        if target is not None and not isinstance(target, POINTER(c_double)):
            target = np.ascontiguousarray(target, dtype=np.float64).ctypes.data_as(POINTER(c_double))
        libbindings_tests.bindings_tests_Arrays_fillFloat64(target, count, value)

    @classmethod
    def cumulative_sum(cls, source: Sequence[float], target: Sequence[float], count: int) -> None:
        """Writes the running total of source into target. Both buffers must hold at
        least count elements. Passing the same buffer for both is supported.
        
        Args:
            source: 
            target: 
            count:
        """
        np = _require_numpy()
        if source is not None and not isinstance(source, POINTER(c_double)):
            source = np.ascontiguousarray(source, dtype=np.float64).ctypes.data_as(POINTER(c_double))
        if target is not None and not isinstance(target, POINTER(c_double)):
            target = np.ascontiguousarray(target, dtype=np.float64).ctypes.data_as(POINTER(c_double))
        libbindings_tests.bindings_tests_Arrays_cumulativeSum(source, target, count)

    @classmethod
    def reverse_float64(cls, values: Sequence[float], count: int) -> None:
        """Reverses the buffer in place.
        
        Args:
            values: 
            count:
        """
        np = _require_numpy()
        if values is not None and not isinstance(values, POINTER(c_double)):
            values = np.ascontiguousarray(values, dtype=np.float64).ctypes.data_as(POINTER(c_double))
        libbindings_tests.bindings_tests_Arrays_reverseFloat64(values, count)


class Callbacks:
    """Synchronous callbacks into caller-provided function pointers."""

    def __new__(cls, *args, **kwargs):
        raise TypeError('Class is not instantiable')

    @classmethod
    def invoke_times(cls, func: IndexCallbackFunction, user_data: Any | bytes | None, times: int) -> None:
        """Invokes the callback exactly 'times' times with indices 0 to times-1, then
        returns. A null function pointer is ignored, and a non-positive count does
        nothing.
        
        Args:
            func: 
            user_data: 
            times:
        """
        libbindings_tests.bindings_tests_Callbacks_invokeTimes(func, user_data, times)

    @classmethod
    def invoke_once(cls, func: IndexCallbackFunction, user_data: Any | bytes | None, index: int) -> None:
        """Invokes the callback once with the given index. Returns after it completes.
        
        Args:
            func: 
            user_data: 
            index:
        """
        libbindings_tests.bindings_tests_Callbacks_invokeOnce(func, user_data, index)


class Errors:
    """Deliberate failures, used to check error propagation."""

    def __new__(cls, *args, **kwargs):
        raise TypeError('Class is not instantiable')

    @classmethod
    def require_non_negative(cls, value: int) -> None:
        """Throws when value is negative, otherwise does nothing.
        
        Args:
            value: 
        
        Raises:
            Exception: value is negative
        """
        status_ = libbindings_tests.bindings_tests_Errors_requireNonNegative(value)
        if status_ != 0:
            raise RuntimeError(f'Encountered error in Errors::require_non_negative ({libbindings_tests.bindings_tests_Errors_getLastError().decode("utf-8")})')

    @classmethod
    def divide_int32(cls, a: int, b: int) -> int:
        """Integer division. Returns 0 through the ABI when it throws.
        
        Args:
            a: 
            b: 
        
        Raises:
            Exception: divisor is zero
        """
        return libbindings_tests.bindings_tests_Errors_divideInt32(a, b)

    @classmethod
    def failing_float64(cls, should_fail: bool) -> float:
        """Returns 0.0 through the ABI when it throws.
        
        Args:
            should_fail: 
        
        Raises:
            Exception: shouldFail is true
        """
        return libbindings_tests.bindings_tests_Errors_failingFloat64(should_fail)

    @classmethod
    def failing_bool(cls, should_fail: bool) -> bool:
        """Returns false through the ABI when it throws.
        
        Args:
            should_fail: 
        
        Raises:
            Exception: shouldFail is true
        """
        return libbindings_tests.bindings_tests_Errors_failingBool(should_fail) != 0

    @classmethod
    def failing_string(cls, should_fail: bool) -> str:
        """Returns a null pointer through the ABI when it throws.
        
        Args:
            should_fail: 
        
        Raises:
            Exception: shouldFail is true
        """
        ptr_ = libbindings_tests.bindings_tests_Errors_failingString(should_fail)
        return ptr_.decode("utf-8") if ptr_ is not None else ""

    @classmethod
    def never_throws(cls) -> None:
        """Marked noexcept, so no status code is generated for it."""
        libbindings_tests.bindings_tests_Errors_neverThrows()

    @classmethod
    def clear_last_error(cls) -> None:
        """Clears the last error for the calling thread."""
        libbindings_tests.bindings_tests_Errors_clearLastError()

    @classmethod
    def get_last_error(cls) -> str:
        """Message of the last exception thrown on the calling thread, or an empty
        string if there has not been one. The language wrappers call this to turn
        a failed call into a native exception.
        """
        ptr_ = libbindings_tests.bindings_tests_Errors_getLastError()
        return ptr_.decode("utf-8") if ptr_ is not None else ""


class Isolated:
    """State confined to a second isolate."""

    def __new__(cls, *args, **kwargs):
        raise TypeError('Class is not instantiable')

    @classmethod
    def increment(cls) -> None:
        libbindings_tests.bindings_tests_Isolated_increment()

    @classmethod
    def get_counter(cls) -> int:
        return libbindings_tests.bindings_tests_Isolated_getCounter()

    @classmethod
    def reset(cls) -> None:
        libbindings_tests.bindings_tests_Isolated_reset()

    @classmethod
    def always_fails(cls) -> None:
        """Always throws, so the secondary isolate's error path is exercised too.
        
        Raises:
            Exception: always
        """
        status_ = libbindings_tests.bindings_tests_Isolated_alwaysFails()
        if status_ != 0:
            raise RuntimeError(f'Encountered error in Isolated::always_fails ({libbindings_tests.bindings_tests_Isolated_getLastError().decode("utf-8")})')

    @classmethod
    def get_last_error(cls) -> str:
        """Last error for the calling thread within the secondary isolate."""
        ptr_ = libbindings_tests.bindings_tests_Isolated_getLastError()
        return ptr_.decode("utf-8") if ptr_ is not None else ""


class Math:
    """Stateless arithmetic used to check primitive type mapping."""

    def __new__(cls, *args, **kwargs):
        raise TypeError('Class is not instantiable')

    @classmethod
    def negate(cls, value: bool) -> bool:
        return libbindings_tests.bindings_tests_Math_negate(value) != 0

    @classmethod
    def next_char(cls, value: Any) -> Any:
        """Returns the next UTF-16 code unit. 'A' becomes 'B'.
        
        Args:
            value:
        """
        return libbindings_tests.bindings_tests_Math_nextChar(value)

    @classmethod
    def add_int8(cls, a: int, b: int) -> int:
        return libbindings_tests.bindings_tests_Math_addInt8(a, b)

    @classmethod
    def add_int16(cls, a: int, b: int) -> int:
        return libbindings_tests.bindings_tests_Math_addInt16(a, b)

    @classmethod
    def add_int32(cls, a: int, b: int) -> int:
        return libbindings_tests.bindings_tests_Math_addInt32(a, b)

    @classmethod
    def add_int64(cls, a: int, b: int) -> int:
        """Uses values above 2^32 to catch platforms that truncate long to 32 bit.
        
        Args:
            a: 
            b:
        """
        return libbindings_tests.bindings_tests_Math_addInt64(a, b)

    @classmethod
    def add_float32(cls, a: float, b: float) -> float:
        return libbindings_tests.bindings_tests_Math_addFloat32(a, b)

    @classmethod
    def add_float64(cls, a: float, b: float) -> float:
        return libbindings_tests.bindings_tests_Math_addFloat64(a, b)

    @classmethod
    def add_size(cls, a: int, b: int) -> int:
        """size_t in and size_t out. Bridged through int64 on the Java side.
        
        Args:
            a: 
            b:
        """
        return libbindings_tests.bindings_tests_Math_addSize(a, b)

    @classmethod
    def size_to_int32(cls, value: int) -> int:
        """Takes a size_t that is capped to a 32 bit Java int on the way in.
        
        Args:
            value:
        """
        return libbindings_tests.bindings_tests_Math_sizeToInt32(value)

    @classmethod
    def max_int32(cls) -> int:
        """Returns Integer.MAX_VALUE, so callers can check the 31 bit cap."""
        return libbindings_tests.bindings_tests_Math_maxInt32()

    @classmethod
    def max_size(cls) -> int:
        """Returns Long.MAX_VALUE as a size_t, so callers can check the 63 bit cap."""
        return libbindings_tests.bindings_tests_Math_maxSize()


class Overloads:
    """Default arguments and language specific convenience overloads."""

    def __new__(cls, *args, **kwargs):
        raise TypeError('Class is not instantiable')

    @classmethod
    def scale(cls, value: int = 1, factor: int = 10) -> int:
        """Both parameters have defaults, so wrappers may call this with no arguments.
        
        Args:
            value: 
            factor:
        """
        return libbindings_tests.bindings_tests_Overloads_scale(value, factor)

    @classmethod
    def greet(cls, name: str, greeting: str = "Hello") -> str:
        """A trailing default only, which is the common case.
        
        Args:
            name: 
            greeting:
        """
        if name is not None and not isinstance(name, c_char_p):
            name = c_char_p(name.encode('utf-8') if isinstance(name, str) else name)
        if greeting is not None and not isinstance(greeting, c_char_p):
            greeting = c_char_p(greeting.encode('utf-8') if isinstance(greeting, str) else greeting)
        ptr_ = libbindings_tests.bindings_tests_Overloads_greet(name, greeting)
        return ptr_.decode("utf-8") if ptr_ is not None else ""

    @classmethod
    def total(cls, values, factor = 1) -> int:
        """Takes a pre-summed total. Each language gets a wrapper that sums a collection for you.
        
        Args:
            sum: 
            factor:
        """
        total = sum(values)return libbindings_tests.bindings_tests_Overloads_total(total, factor)

    @classmethod
    def answer(cls, multiplier: int) -> int:
        """The C++ wrapper body is hand written and never calls into the library, so
        this is the one method where the C++ result may legitimately differ from
        the others. Kept deliberately obvious.
        
        Args:
            multiplier:
        """
        return libbindings_tests.bindings_tests_Overloads_answer(multiplier)

    @classmethod
    def renamed(cls) -> int:
        """Declared as originalName in Java but exported as renamed."""
        return libbindings_tests.bindings_tests_Overloads_renamed()


class Palette:
    """Enum round trips."""

    def __new__(cls, *args, **kwargs):
        raise TypeError('Class is not instantiable')

    @classmethod
    def next(cls, color: Color) -> Color:
        """Cycles Red to Green to Blue and back to Red.
        
        Args:
            color:
        """
        return Color(libbindings_tests.bindings_tests_Palette_next(color))

    @classmethod
    def name_of(cls, color: Color) -> str:
        ptr_ = libbindings_tests.bindings_tests_Palette_nameOf(color)
        return ptr_.decode("utf-8") if ptr_ is not None else ""

    @classmethod
    def ordinal_of(cls, color: Color) -> int:
        return libbindings_tests.bindings_tests_Palette_ordinalOf(color)

    @classmethod
    def from_ordinal(cls, ordinal: int) -> Color:
        """Returns the enum with the given ordinal, or Red if it is out of range.
        
        Args:
            ordinal:
        """
        return Color(libbindings_tests.bindings_tests_Palette_fromOrdinal(ordinal))


class Strings:
    """String conversion, including non-ASCII round trips."""

    def __new__(cls, *args, **kwargs):
        raise TypeError('Class is not instantiable')

    @classmethod
    def echo(cls, value: str) -> str:
        """Returns the input unchanged. Round trips UTF-8 through Java's UTF-16.
        
        Args:
            value:
        """
        if value is not None and not isinstance(value, c_char_p):
            value = c_char_p(value.encode('utf-8') if isinstance(value, str) else value)
        ptr_ = libbindings_tests.bindings_tests_Strings_echo(value)
        return ptr_.decode("utf-8") if ptr_ is not None else ""

    @classmethod
    def concat(cls, a: str, b: str) -> str:
        if a is not None and not isinstance(a, c_char_p):
            a = c_char_p(a.encode('utf-8') if isinstance(a, str) else a)
        if b is not None and not isinstance(b, c_char_p):
            b = c_char_p(b.encode('utf-8') if isinstance(b, str) else b)
        ptr_ = libbindings_tests.bindings_tests_Strings_concat(a, b)
        return ptr_.decode("utf-8") if ptr_ is not None else ""

    @classmethod
    def utf8_length(cls, value: str) -> int:
        """Length in UTF-8 bytes, which differs from the Java String length for non-ASCII input.
        
        Args:
            value:
        """
        if value is not None and not isinstance(value, c_char_p):
            value = c_char_p(value.encode('utf-8') if isinstance(value, str) else value)
        return libbindings_tests.bindings_tests_Strings_utf8Length(value)

    @classmethod
    def utf16_length(cls, value: str) -> int:
        """Number of UTF-16 code units, i.e. the Java String length.
        
        Args:
            value:
        """
        if value is not None and not isinstance(value, c_char_p):
            value = c_char_p(value.encode('utf-8') if isinstance(value, str) else value)
        return libbindings_tests.bindings_tests_Strings_utf16Length(value)

    @classmethod
    def empty(cls) -> str:
        """Returns an empty string, which must not be confused with a null pointer."""
        ptr_ = libbindings_tests.bindings_tests_Strings_empty()
        return ptr_.decode("utf-8") if ptr_ is not None else ""

    @classmethod
    def null_string(cls) -> str:
        """Returns a null pointer rather than an empty string."""
        ptr_ = libbindings_tests.bindings_tests_Strings_nullString()
        return ptr_.decode("utf-8") if ptr_ is not None else ""

    @classmethod
    def join(cls, values: Sequence[str], count: int, separator: str) -> str:
        """Joins a char* array with a separator. Checks char** parameter handling.
        
        Args:
            values: 
            count: number of entries in values
            separator:
        """
        if values is not None and not isinstance(values, POINTER(c_char_p)):
            encoded_list = [s.encode('utf-8') if isinstance(s, str) else s for s in values]
            values = (c_char_p * len(encoded_list))(*encoded_list)
        if separator is not None and not isinstance(separator, c_char_p):
            separator = c_char_p(separator.encode('utf-8') if isinstance(separator, str) else separator)
        ptr_ = libbindings_tests.bindings_tests_Strings_join(values, count, separator)
        return ptr_.decode("utf-8") if ptr_ is not None else ""

    @classmethod
    def repeat(cls, value: str, times: int) -> str:
        """Returns a string longer than the initial scratch buffer, forcing it to grow.
        
        Args:
            value: 
            times:
        """
        if value is not None and not isinstance(value, c_char_p):
            value = c_char_p(value.encode('utf-8') if isinstance(value, str) else value)
        ptr_ = libbindings_tests.bindings_tests_Strings_repeat(value, times)
        return ptr_.decode("utf-8") if ptr_ is not None else ""
