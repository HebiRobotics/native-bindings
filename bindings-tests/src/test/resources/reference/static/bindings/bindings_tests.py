from __future__ import annotations
from ctypes import *
from dataclasses import dataclass
from enum import IntEnum
import os
import platform
from typing import Sequence, Tuple, Any, Callable

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
            url = f"https://example.com/download/bindings_tests/snapshot/42/{dl_name}"

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

# Asynchronous callbacks are held for the process lifetime. ctypes does not keep the
# trampoline alive by itself, and such a callback may fire long after the call returns.
_callback_wrappers = {}
def _wrap_callback(prototype, func):
    wrapper = _callback_wrappers.get((prototype, func))
    if wrapper is None:
        wrapper = prototype(func)
        _callback_wrappers[(prototype, func)] = wrapper
    return wrapper

# API definitions
class _ErrorInfo(Structure):
    # Filled by every method that can throw. The strings stay valid only until
    # this thread's next library call, so they get copied into the exception.
    _fields_ = [("message", c_char_p), ("id", c_char_p), ("code", c_int32)]

class Error(RuntimeError):
    # Failure reported by the library. The id names the kind of failure, as in
    # "mylib:invalidArgument", and is empty when the library did not set one.
    def __init__(self, message, id=""):
        super().__init__(f"[{id}] {message}" if id else message)
        self.id = id

def _checked(error_, result_):
    # Wraps the call so that the check reads as one expression. Python evaluates
    # the arguments first, so the struct is filled by the time this runs.
    if error_.code != 0:
        message = error_.message.decode("utf-8") if error_.message else "unknown error"
        raise Error(message, error_.id.decode("utf-8") if error_.id else "")
    return result_

class _ByteSpan(Structure):
    # Layout of the by-value struct in the C ABI, mirroring ByteSpan
    _fields_ = [("data", POINTER(c_uint8)), ("length", c_size_t)]

class _DoubleSpan(Structure):
    # Layout of the by-value struct in the C ABI, mirroring DoubleSpan
    _fields_ = [("data", POINTER(c_double)), ("length", c_size_t)]

class _FieldTypes(Structure):
    # Layout of the by-value struct in the C ABI, mirroring FieldTypes
    _fields_ = [("unit", c_uint16), ("shorts", POINTER(c_int16)), ("longs", POINTER(c_int64)), ("context", c_void_p)]

class _FloatSpan(Structure):
    # Layout of the by-value struct in the C ABI, mirroring FloatSpan
    _fields_ = [("data", POINTER(c_float)), ("length", c_size_t)]

class _IntSpan(Structure):
    # Layout of the by-value struct in the C ABI, mirroring IntSpan
    _fields_ = [("data", POINTER(c_int32)), ("length", c_size_t)]

class _StatusInfo(Structure):
    # Layout of the by-value struct in the C ABI, mirroring StatusInfo
    _fields_ = [("voltage", c_double), ("state", c_int32), ("enabled", c_int32)]

class struct_Counted_(Structure):
    pass
CountedPtr = POINTER(struct_Counted_)

class struct_Counter_(Structure):
    pass
CounterPtr = POINTER(struct_Counter_)

class struct_Naming_(Structure):
    pass
NamingPtr = POINTER(struct_Naming_)

class struct_Notified_(Structure):
    pass
NotifiedPtr = POINTER(struct_Notified_)

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

class struct_Indices_(Structure):
    pass
IndicesPtr = POINTER(struct_Indices_)

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

class struct_Structs_(Structure):
    pass
StructsPtr = POINTER(struct_Structs_)

IndexCallbackFunction = CFUNCTYPE(None, c_void_p, c_int)
NotifyCallbackFunction = CFUNCTYPE(None, c_void_p)
ScaleCallbackFunction = CFUNCTYPE(c_double, c_void_p, c_double)

# ==== Counted ====
libbindings_tests.bindings_tests_Counted_create.argtypes = [POINTER(_ErrorInfo), IndexCallbackFunction, c_void_p, c_int]
libbindings_tests.bindings_tests_Counted_create.restype = CountedPtr
libbindings_tests.bindings_tests_Counted_getClosedCount.argtypes = []
libbindings_tests.bindings_tests_Counted_getClosedCount.restype = c_int
libbindings_tests.bindings_tests_Counted_release.argtypes = [CountedPtr]
libbindings_tests.bindings_tests_Counted_release.restype = None

# ==== Counter ====
libbindings_tests.bindings_tests_Counter_create.argtypes = [POINTER(_ErrorInfo), c_int]
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

# ==== Naming ====
libbindings_tests.custom_prefix_Naming_create.argtypes = [POINTER(_ErrorInfo), c_int]
libbindings_tests.custom_prefix_Naming_create.restype = NamingPtr
libbindings_tests.custom_prefix_Naming_get_start_value.argtypes = [NamingPtr]
libbindings_tests.custom_prefix_Naming_get_start_value.restype = c_int
libbindings_tests.custom_prefix_Naming_add_two_numbers.argtypes = [c_int, c_int]
libbindings_tests.custom_prefix_Naming_add_two_numbers.restype = c_int
libbindings_tests.custom_prefix_Naming_release.argtypes = [NamingPtr]
libbindings_tests.custom_prefix_Naming_release.restype = None

# ==== Notified ====
libbindings_tests.bindings_tests_Notified_create.argtypes = [POINTER(_ErrorInfo), NotifyCallbackFunction, c_void_p]
libbindings_tests.bindings_tests_Notified_create.restype = NotifiedPtr
libbindings_tests.bindings_tests_Notified_release.argtypes = [NotifiedPtr]
libbindings_tests.bindings_tests_Notified_release.restype = None

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
libbindings_tests.bindings_tests_Circle_create.argtypes = [POINTER(_ErrorInfo), c_double]
libbindings_tests.bindings_tests_Circle_create.restype = CirclePtr
libbindings_tests.bindings_tests_Circle_getRadius.argtypes = [CirclePtr]
libbindings_tests.bindings_tests_Circle_getRadius.restype = c_double
libbindings_tests.bindings_tests_Circle_getColor.argtypes = [CirclePtr]
libbindings_tests.bindings_tests_Circle_getColor.restype = c_int32
libbindings_tests.bindings_tests_Circle_area.argtypes = [CirclePtr]
libbindings_tests.bindings_tests_Circle_area.restype = c_double
libbindings_tests.bindings_tests_Circle_sideCount.argtypes = [CirclePtr]
libbindings_tests.bindings_tests_Circle_sideCount.restype = c_int
libbindings_tests.bindings_tests_Circle_to_Shape.argtypes = [CirclePtr]
libbindings_tests.bindings_tests_Circle_to_Shape.restype = ShapePtr

# ==== Square ====
libbindings_tests.bindings_tests_Square_create.argtypes = [POINTER(_ErrorInfo), c_double]
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
libbindings_tests.bindings_tests_Arrays_dotProduct.argtypes = [POINTER(_ErrorInfo), _DoubleSpan, _DoubleSpan]
libbindings_tests.bindings_tests_Arrays_dotProduct.restype = c_double
libbindings_tests.bindings_tests_Arrays_addOffset.argtypes = [_DoubleSpan, c_double]
libbindings_tests.bindings_tests_Arrays_addOffset.restype = _DoubleSpan
libbindings_tests.bindings_tests_Arrays_addOffsetInt32.argtypes = [_IntSpan, c_int]
libbindings_tests.bindings_tests_Arrays_addOffsetInt32.restype = _IntSpan
libbindings_tests.bindings_tests_Arrays_addOffsetFloat32.argtypes = [_FloatSpan, c_float]
libbindings_tests.bindings_tests_Arrays_addOffsetFloat32.restype = _FloatSpan
libbindings_tests.bindings_tests_Arrays_sumUint8.argtypes = [POINTER(c_uint8), c_size_t]
libbindings_tests.bindings_tests_Arrays_sumUint8.restype = c_int
libbindings_tests.bindings_tests_Arrays_addOffsetUint8.argtypes = [_ByteSpan, c_byte]
libbindings_tests.bindings_tests_Arrays_addOffsetUint8.restype = _ByteSpan
libbindings_tests.bindings_tests_Arrays_getSequence.argtypes = [c_size_t]
libbindings_tests.bindings_tests_Arrays_getSequence.restype = _DoubleSpan

# ==== Callbacks ====
libbindings_tests.bindings_tests_Callbacks_invokeTimes.argtypes = [IndexCallbackFunction, c_void_p, c_size_t]
libbindings_tests.bindings_tests_Callbacks_invokeTimes.restype = None
libbindings_tests.bindings_tests_Callbacks_invokeOnce.argtypes = [IndexCallbackFunction, c_void_p, c_int]
libbindings_tests.bindings_tests_Callbacks_invokeOnce.restype = None
libbindings_tests.bindings_tests_Callbacks_invokeChecked.argtypes = [POINTER(_ErrorInfo), IndexCallbackFunction, c_void_p, c_int]
libbindings_tests.bindings_tests_Callbacks_invokeChecked.restype = None
libbindings_tests.bindings_tests_Callbacks_invokeOnWorkerThread.argtypes = [IndexCallbackFunction, c_void_p, c_int]
libbindings_tests.bindings_tests_Callbacks_invokeOnWorkerThread.restype = None
libbindings_tests.bindings_tests_Callbacks_notifyOnce.argtypes = [NotifyCallbackFunction, c_void_p]
libbindings_tests.bindings_tests_Callbacks_notifyOnce.restype = None
libbindings_tests.bindings_tests_Callbacks_applyScaled.argtypes = [ScaleCallbackFunction, c_void_p, c_double]
libbindings_tests.bindings_tests_Callbacks_applyScaled.restype = c_double

# ==== Errors ====
libbindings_tests.bindings_tests_Errors_requireNonNegative.argtypes = [POINTER(_ErrorInfo), c_int]
libbindings_tests.bindings_tests_Errors_requireNonNegative.restype = None
libbindings_tests.bindings_tests_Errors_divideInt32.argtypes = [POINTER(_ErrorInfo), c_int, c_int]
libbindings_tests.bindings_tests_Errors_divideInt32.restype = c_int
libbindings_tests.bindings_tests_Errors_failingFloat64.argtypes = [POINTER(_ErrorInfo), c_int]
libbindings_tests.bindings_tests_Errors_failingFloat64.restype = c_double
libbindings_tests.bindings_tests_Errors_failingBool.argtypes = [POINTER(_ErrorInfo), c_int]
libbindings_tests.bindings_tests_Errors_failingBool.restype = c_int
libbindings_tests.bindings_tests_Errors_failingString.argtypes = [POINTER(_ErrorInfo), c_int]
libbindings_tests.bindings_tests_Errors_failingString.restype = c_char_p
libbindings_tests.bindings_tests_Errors_failWithoutMessage.argtypes = [POINTER(_ErrorInfo)]
libbindings_tests.bindings_tests_Errors_failWithoutMessage.restype = c_int
libbindings_tests.bindings_tests_Errors_neverThrows.argtypes = []
libbindings_tests.bindings_tests_Errors_neverThrows.restype = None

# ==== Indices ====
libbindings_tests.bindings_tests_Indices_echo.argtypes = [c_int]
libbindings_tests.bindings_tests_Indices_echo.restype = c_int
libbindings_tests.bindings_tests_Indices_countUpTo.argtypes = [c_int]
libbindings_tests.bindings_tests_Indices_countUpTo.restype = c_int
libbindings_tests.bindings_tests_Indices_lastIndexOf.argtypes = [c_int]
libbindings_tests.bindings_tests_Indices_lastIndexOf.restype = c_int
libbindings_tests.bindings_tests_Indices_offsetFrom.argtypes = [c_int, c_int]
libbindings_tests.bindings_tests_Indices_offsetFrom.restype = c_int
libbindings_tests.bindings_tests_Indices_fromRow.argtypes = [c_int]
libbindings_tests.bindings_tests_Indices_fromRow.restype = c_int
libbindings_tests.bindings_tests_Indices_echoSize.argtypes = [c_size_t]
libbindings_tests.bindings_tests_Indices_echoSize.restype = c_size_t

# ==== Isolated ====
libbindings_tests.bindings_tests_Isolated_increment.argtypes = []
libbindings_tests.bindings_tests_Isolated_increment.restype = None
libbindings_tests.bindings_tests_Isolated_getCounter.argtypes = []
libbindings_tests.bindings_tests_Isolated_getCounter.restype = c_int
libbindings_tests.bindings_tests_Isolated_reset.argtypes = []
libbindings_tests.bindings_tests_Isolated_reset.restype = None
libbindings_tests.bindings_tests_Isolated_alwaysFails.argtypes = [POINTER(_ErrorInfo)]
libbindings_tests.bindings_tests_Isolated_alwaysFails.restype = None

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
libbindings_tests.bindings_tests_Overloads_gain.argtypes = [c_int, c_double]
libbindings_tests.bindings_tests_Overloads_gain.restype = c_double
libbindings_tests.bindings_tests_Overloads_echoIndex.argtypes = [c_int]
libbindings_tests.bindings_tests_Overloads_echoIndex.restype = c_int
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
libbindings_tests.bindings_tests_Palette_describe.argtypes = [c_int32, c_int32]
libbindings_tests.bindings_tests_Palette_describe.restype = c_char_p
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

# ==== Structs ====
libbindings_tests.bindings_tests_Structs_fillFieldTypes.argtypes = [c_short, POINTER(c_short), POINTER(c_longlong), c_void_p]
libbindings_tests.bindings_tests_Structs_fillFieldTypes.restype = _FieldTypes
libbindings_tests.bindings_tests_Structs_totalPower.argtypes = [_StatusInfo, c_double]
libbindings_tests.bindings_tests_Structs_totalPower.restype = c_double
libbindings_tests.bindings_tests_Structs_checkedVoltage.argtypes = [POINTER(_ErrorInfo), _StatusInfo]
libbindings_tests.bindings_tests_Structs_checkedVoltage.restype = c_double
libbindings_tests.bindings_tests_Structs_getStatus.argtypes = [c_int]
libbindings_tests.bindings_tests_Structs_getStatus.restype = _StatusInfo
libbindings_tests.bindings_tests_Structs_getCheckedStatus.argtypes = [POINTER(_ErrorInfo), c_int]
libbindings_tests.bindings_tests_Structs_getCheckedStatus.restype = _StatusInfo

# ==== Structs ====

@dataclass
class FieldTypes:
    unit: int

@dataclass
class StatusInfo:
    voltage: float
    state: int
    enabled: bool


# ==== Enum Wrappers ====
class CircleColor(IntEnum):
    RED = 0
    GREEN = 1
    BLUE = 2

class Color(IntEnum):
    RED = 0
    GREEN = 1
    BLUE = 2

class Marker(IntEnum):
    DOT = 0
    CROSS = 1


# ==== Class Wrappers ====
class Counted:
    """A handle whose construction invokes a callback once per index."""

    __slots__ = ('ptr',)

    def __init__(self, func: Callable[[Any, int], None], user_data: Any | bytes | None, times: int) -> None:
        """Creates the handle, invoking the callback with indices 0 to times-1 before returning it.

        Args:
            func: Any callable is accepted, and a plain one is wrapped into a fresh
                IndexCallbackFunction per call. Build the prototype once for a hot loop.
            user_data:
            times:

        Returns:
            A handle whose construction invokes a callback once per index.

        Raises:
            Exception: on internal errors
        """
        if func is not None and not isinstance(func, IndexCallbackFunction):
            func = IndexCallbackFunction(func)
        error_ = _ErrorInfo()
        ptr_ = _checked(error_, libbindings_tests.bindings_tests_Counted_create(byref(error_), func, user_data, times))
        if not ptr_:
            raise RuntimeError('Failed to create Counted in Counted::__init__')
        self.ptr = ptr_

    def __del__(self):
        if self.ptr is not None:
            libbindings_tests.bindings_tests_Counted_release(self.ptr)
        self.ptr = None

    def __repr__(self):
        return f"{self.__class__.__name__}()"

    @classmethod
    def get_closed_count(cls) -> int:
        """Total number of handles of this type that have been released so far in this
        isolate. Increments exactly once per released handle.
        """
        return libbindings_tests.bindings_tests_Counted_getClosedCount()


class Counter:
    """A mutable counter, used to check handle creation and release."""

    __slots__ = ('ptr',)

    def __init__(self, start: int = 0, *, value: int = None) -> None:
        """Creates a counter starting at the given value, or at zero by default.

        Args:
            start:

        Returns:
            A mutable counter, used to check handle creation and release.

        Raises:
            Exception: on internal errors
        """
        error_ = _ErrorInfo()
        ptr_ = _checked(error_, libbindings_tests.bindings_tests_Counter_create(byref(error_), start))
        if not ptr_:
            raise RuntimeError('Failed to create Counter in Counter::__init__')
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


class Naming:
    """A counter exported under a custom prefix in snake case."""

    __slots__ = ('ptr',)

    def __init__(self, start_value: int = 0) -> None:
        """Creates an instance holding the given value, or zero by default.

        Args:
            start_value:

        Returns:
            A counter exported under a custom prefix in snake case.

        Raises:
            Exception: on internal errors
        """
        error_ = _ErrorInfo()
        ptr_ = _checked(error_, libbindings_tests.custom_prefix_Naming_create(byref(error_), start_value))
        if not ptr_:
            raise RuntimeError('Failed to create Naming in Naming::__init__')
        self.ptr = ptr_

    def __del__(self):
        if self.ptr is not None:
            libbindings_tests.custom_prefix_Naming_release(self.ptr)
        self.ptr = None

    def __repr__(self):
        return f"{self.__class__.__name__}()"

    def get_start_value(self) -> int:
        """An instance method whose name needs more than one word to show the conversion."""
        return libbindings_tests.custom_prefix_Naming_get_start_value(self.ptr)

    @classmethod
    def add_two_numbers(cls, first: int, second: int) -> int:
        """A static method, so the conversion is pinned for both kinds of call.

        Args:
            first:
            second:
        """
        return libbindings_tests.custom_prefix_Naming_add_two_numbers(first, second)


class Notified:
    """A handle whose construction invokes a callback."""

    __slots__ = ('ptr',)

    def __init__(self, func: Callable[[Any], None], user_data: Any | bytes | None) -> None:
        """Creates the handle and invokes the callback once before returning it.

        Args:
            func:
            user_data:

        Returns:
            A handle whose construction invokes a callback.

        Raises:
            Exception: on internal errors
        """
        if func is not None and not isinstance(func, NotifyCallbackFunction):
            func = _wrap_callback(NotifyCallbackFunction, func)
        error_ = _ErrorInfo()
        ptr_ = _checked(error_, libbindings_tests.bindings_tests_Notified_create(byref(error_), func, user_data))
        if not ptr_:
            raise RuntimeError('Failed to create Notified in Notified::__init__')
        self.ptr = ptr_

    def __del__(self):
        if self.ptr is not None:
            libbindings_tests.bindings_tests_Notified_release(self.ptr)
        self.ptr = None

    def __repr__(self):
        return f"{self.__class__.__name__}()"


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
        error_ = _ErrorInfo()
        ptr_ = _checked(error_, libbindings_tests.bindings_tests_Circle_create(byref(error_), radius))
        if not ptr_:
            raise RuntimeError('Failed to create Circle in Circle::__init__')
        self.ref_Circle = ptr_
        super().__init__(libbindings_tests.bindings_tests_Circle_to_Shape(ptr_))
        if name is not None: self.name = name

    def __del__(self):
        super().__del__()
        self.ref_Circle = None

    def __repr__(self):
        return f"{self.__class__.__name__}(radius={self.radius!r}, name={self.name!r})"

    @property
    def radius(self) -> float:
        return libbindings_tests.bindings_tests_Circle_getRadius(self.ref_Circle)

    def get_color(self) -> CircleColor:
        """Returns the fill color, which is always Blue until setters exist.

        Returns:
            Fill color of a circle.
        """
        return CircleColor(libbindings_tests.bindings_tests_Circle_getColor(self.ref_Circle))

    def area(self) -> float:
        return libbindings_tests.bindings_tests_Circle_area(self.ref_Circle)

    def side_count(self) -> int:
        return libbindings_tests.bindings_tests_Circle_sideCount(self.ref_Circle)


class Square(Shape):
    """A square. Inherits name and area from Shape."""

    __slots__ = ('ref_Square',)

    def __init__(self, side: float, *, name: str = None) -> None:
        error_ = _ErrorInfo()
        ptr_ = _checked(error_, libbindings_tests.bindings_tests_Square_create(byref(error_), side))
        if not ptr_:
            raise RuntimeError('Failed to create Square in Square::__init__')
        self.ref_Square = ptr_
        super().__init__(libbindings_tests.bindings_tests_Square_to_Shape(ptr_))
        if name is not None: self.name = name

    def __del__(self):
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
        """A const input buffer. Besides the const in the C header, this is what makes the
        C++ generator add a std::vector<double> overload next to the raw pointer one.

        Args:
            values:
            count:
        """
        np = _require_numpy()
        if values is not None and not isinstance(values, POINTER(c_double)):
            values = np.ascontiguousarray(values, dtype=np.float64).ctypes.data_as(POINTER(c_double))
        return libbindings_tests.bindings_tests_Arrays_sumFloat64(values, count)

    @classmethod
    def sum_int32(cls, values: Sequence[int], count: int) -> int:
        """Const on a non-double pointer, which gets the const but no vector overload.

        Args:
            values:
            count:
        """
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
        least count elements. Passing the same buffer for both is supported. One const
        and one mutable pointer in the same signature, so only the source picks up the
        vector overload.

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

    @classmethod
    def dot_product(cls, x: Sequence[float], y: Sequence[float]) -> float:
        """Dot product of two spans. The lengths ride along in the spans, so a mismatch is checked once here rather than in every language wrapper.

        Args:
            x:
            y:

        Raises:
            Exception: mismatched lengths
        """
        np = _require_numpy()
        if x is None:
            x = _DoubleSpan(None, 0)
        elif not isinstance(x, _DoubleSpan):
            x_arr = np.ascontiguousarray(x, dtype=np.float64)
            x = _DoubleSpan(x_arr.ctypes.data_as(POINTER(c_double)), len(x_arr))
        if y is None:
            y = _DoubleSpan(None, 0)
        elif not isinstance(y, _DoubleSpan):
            y_arr = np.ascontiguousarray(y, dtype=np.float64)
            y = _DoubleSpan(y_arr.ctypes.data_as(POINTER(c_double)), len(y_arr))
        error_ = _ErrorInfo()
        return _checked(error_, libbindings_tests.bindings_tests_Arrays_dotProduct(byref(error_), x, y))

    @classmethod
    def add_offset(cls, values: Sequence[float], offset: float) -> Sequence[float]:
        """Returns a new array with the offset added to every element. Checks a span in and an array out.

        Args:
            values:
            offset:
        """
        np = _require_numpy()
        if values is None:
            values = _DoubleSpan(None, 0)
        elif not isinstance(values, _DoubleSpan):
            values_arr = np.ascontiguousarray(values, dtype=np.float64)
            values = _DoubleSpan(values_arr.ctypes.data_as(POINTER(c_double)), len(values_arr))
        struct_ = libbindings_tests.bindings_tests_Arrays_addOffset(values, offset)
        if struct_.length == 0 or not struct_.data:
            return np.empty(0, dtype=np.float64)
        return np.ctypeslib.as_array(struct_.data, shape=(struct_.length,)).copy()

    @classmethod
    def add_offset_int32(cls, values: Sequence[int], offset: int) -> Sequence[int]:
        """Returns a new array with the offset added to every element. Checks an int32 span in and an array out.

        Args:
            values:
            offset:
        """
        np = _require_numpy()
        if values is None:
            values = _IntSpan(None, 0)
        elif not isinstance(values, _IntSpan):
            values_arr = np.ascontiguousarray(values, dtype=np.int32)
            values = _IntSpan(values_arr.ctypes.data_as(POINTER(c_int32)), len(values_arr))
        struct_ = libbindings_tests.bindings_tests_Arrays_addOffsetInt32(values, offset)
        if struct_.length == 0 or not struct_.data:
            return np.empty(0, dtype=np.int32)
        return np.ctypeslib.as_array(struct_.data, shape=(struct_.length,)).copy()

    @classmethod
    def add_offset_float32(cls, values: Sequence[float], offset: float) -> Sequence[float]:
        """Returns a new array with the offset added to every element. Checks a float span in and an array out.

        Args:
            values:
            offset:
        """
        np = _require_numpy()
        if values is None:
            values = _FloatSpan(None, 0)
        elif not isinstance(values, _FloatSpan):
            values_arr = np.ascontiguousarray(values, dtype=np.float32)
            values = _FloatSpan(values_arr.ctypes.data_as(POINTER(c_float)), len(values_arr))
        struct_ = libbindings_tests.bindings_tests_Arrays_addOffsetFloat32(values, offset)
        if struct_.length == 0 or not struct_.data:
            return np.empty(0, dtype=np.float32)
        return np.ctypeslib.as_array(struct_.data, shape=(struct_.length,)).copy()

    @classmethod
    def sum_uint8(cls, values: Sequence[int], count: int) -> int:
        """Sum of the unsigned byte values. Checks the raw uint8 pointer with an explicit count, the shape an image row uses.

        Args:
            values:
            count:
        """
        np = _require_numpy()
        if values is not None and not isinstance(values, POINTER(c_uint8)):
            values = np.ascontiguousarray(values, dtype=np.uint8).ctypes.data_as(POINTER(c_uint8))
        return libbindings_tests.bindings_tests_Arrays_sumUint8(values, count)

    @classmethod
    def add_offset_uint8(cls, values: Sequence[int], offset: int) -> Sequence[int]:
        """Returns a new array with the offset added to every element. Checks a byte span in and an array out, including embedded zeros that would truncate a string.

        Args:
            values:
            offset:
        """
        np = _require_numpy()
        if values is None:
            values = _ByteSpan(None, 0)
        elif not isinstance(values, _ByteSpan):
            values_arr = np.ascontiguousarray(values, dtype=np.uint8)
            values = _ByteSpan(values_arr.ctypes.data_as(POINTER(c_uint8)), len(values_arr))
        struct_ = libbindings_tests.bindings_tests_Arrays_addOffsetUint8(values, offset)
        if struct_.length == 0 or not struct_.data:
            return np.empty(0, dtype=np.uint8)
        return np.ctypeslib.as_array(struct_.data, shape=(struct_.length,)).copy()

    @classmethod
    def get_sequence(cls, count: int) -> Sequence[float]:
        """Returns 0, 1, .. count-1. A count of zero returns an empty array, and a count
        over 128 forces the per-thread scratch buffer to grow, mirroring Strings.repeat.

        Args:
            count:
        """
        np = _require_numpy()
        struct_ = libbindings_tests.bindings_tests_Arrays_getSequence(count)
        if struct_.length == 0 or not struct_.data:
            return np.empty(0, dtype=np.float64)
        return np.ctypeslib.as_array(struct_.data, shape=(struct_.length,)).copy()


class Callbacks:
    """Synchronous callbacks into caller-provided function pointers."""

    def __new__(cls, *args, **kwargs):
        raise TypeError('Class is not instantiable')

    @classmethod
    def invoke_times(cls, func: Callable[[Any, int], None], user_data: Any | bytes | None, times: int) -> None:
        """Invokes the callback exactly 'times' times with indices 0 to times-1, then
        returns. A null function pointer is ignored, and a non-positive count does
        nothing.

        Args:
            func: Any callable is accepted, and a plain one is wrapped into a fresh
                IndexCallbackFunction per call. Build the prototype once for a hot loop.
            user_data:
            times:
        """
        if func is not None and not isinstance(func, IndexCallbackFunction):
            func = IndexCallbackFunction(func)
        libbindings_tests.bindings_tests_Callbacks_invokeTimes(func, user_data, times)

    @classmethod
    def invoke_once(cls, func: Callable[[Any, int], None], user_data: Any | bytes | None, index: int) -> None:
        """Invokes the callback once with the given index. Returns after it completes.

        Args:
            func: Any callable is accepted, and a plain one is wrapped into a fresh
                IndexCallbackFunction per call. Build the prototype once for a hot loop.
            user_data:
            index:
        """
        if func is not None and not isinstance(func, IndexCallbackFunction):
            func = IndexCallbackFunction(func)
        libbindings_tests.bindings_tests_Callbacks_invokeOnce(func, user_data, index)

    @classmethod
    def invoke_checked(cls, func: Callable[[Any, int], None], user_data: Any | bytes | None, index: int) -> None:
        """Invokes the callback once with the given index, then throws if the index is
        negative. The invocation happens before the validation on purpose, so that a
        caller can observe both failures in one call.

        Args:
            func: Any callable is accepted, and a plain one is wrapped into a fresh
                IndexCallbackFunction per call. Build the prototype once for a hot loop.
            user_data:
            index:

        Raises:
            Exception: index is negative
        """
        if func is not None and not isinstance(func, IndexCallbackFunction):
            func = IndexCallbackFunction(func)
        error_ = _ErrorInfo()
        _checked(error_, libbindings_tests.bindings_tests_Callbacks_invokeChecked(byref(error_), func, user_data, index))

    @classmethod
    def invoke_on_worker_thread(cls, func: Callable[[Any, int], None], user_data: Any | bytes | None, index: int) -> None:
        """Invokes the callback once with the given index on a worker thread, and joins that
        thread before returning. The invocation therefore completes within the call but not
        on the calling thread, which is the shape a solver has when it fans an objective
        out to workers. The callback must be thread safe.

        Args:
            func: Any callable is accepted, and a plain one is wrapped into a fresh
                IndexCallbackFunction per call. Build the prototype once for a hot loop.
            user_data:
            index:
        """
        if func is not None and not isinstance(func, IndexCallbackFunction):
            func = IndexCallbackFunction(func)
        libbindings_tests.bindings_tests_Callbacks_invokeOnWorkerThread(func, user_data, index)

    @classmethod
    def notify_once(cls, func: Callable[[Any], None], user_data: Any | bytes | None) -> None:
        """Invokes the callback once. Returns after it completes.

        Args:
            func:
            user_data:
        """
        if func is not None and not isinstance(func, NotifyCallbackFunction):
            func = _wrap_callback(NotifyCallbackFunction, func)
        libbindings_tests.bindings_tests_Callbacks_notifyOnce(func, user_data)

    @classmethod
    def apply_scaled(cls, func: Callable[[Any, float], float], user_data: Any | bytes | None, value: float) -> float:
        """Passes the value to the callback and returns what the callback produced. A null
        function pointer leaves the value unchanged.

        Args:
            func: Any callable is accepted, and a plain one is wrapped into a fresh
                ScaleCallbackFunction per call. Build the prototype once for a hot loop.
            user_data:
            value:
        """
        if func is not None and not isinstance(func, ScaleCallbackFunction):
            func = ScaleCallbackFunction(func)
        return libbindings_tests.bindings_tests_Callbacks_applyScaled(func, user_data, value)


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
        error_ = _ErrorInfo()
        _checked(error_, libbindings_tests.bindings_tests_Errors_requireNonNegative(byref(error_), value))

    @classmethod
    def divide_int32(cls, a: int, b: int) -> int:
        """Integer division. Returns 0 through the ABI when it throws.

        Args:
            a:
            b:

        Raises:
            Exception: divisor is zero
        """
        error_ = _ErrorInfo()
        return _checked(error_, libbindings_tests.bindings_tests_Errors_divideInt32(byref(error_), a, b))

    @classmethod
    def failing_float64(cls, should_fail: bool) -> float:
        """Returns 0.0 through the ABI when it throws.

        Args:
            should_fail:

        Raises:
            Exception: shouldFail is true
        """
        error_ = _ErrorInfo()
        return _checked(error_, libbindings_tests.bindings_tests_Errors_failingFloat64(byref(error_), should_fail))

    @classmethod
    def failing_bool(cls, should_fail: bool) -> bool:
        """Returns false through the ABI when it throws.

        Args:
            should_fail:

        Raises:
            Exception: shouldFail is true
        """
        error_ = _ErrorInfo()
        return _checked(error_, libbindings_tests.bindings_tests_Errors_failingBool(byref(error_), should_fail)) != 0

    @classmethod
    def failing_string(cls, should_fail: bool) -> str:
        """Returns a null pointer through the ABI when it throws.

        Args:
            should_fail:

        Raises:
            Exception: shouldFail is true
        """
        error_ = _ErrorInfo()
        ptr_ = _checked(error_, libbindings_tests.bindings_tests_Errors_failingString(byref(error_), should_fail))
        return ptr_.decode("utf-8") if ptr_ is not None else ""

    @classmethod
    def fail_without_message(cls) -> int:
        """Throws an exception that carries no message.

        Raises:
            Exception: always
        """
        error_ = _ErrorInfo()
        return _checked(error_, libbindings_tests.bindings_tests_Errors_failWithoutMessage(byref(error_)))

    @classmethod
    def never_throws(cls) -> None:
        """Marked noexcept, so no status code is generated for it."""
        libbindings_tests.bindings_tests_Errors_neverThrows()


class Indices:
    """Index conversion for languages that count from one."""

    def __new__(cls, *args, **kwargs):
        raise TypeError('Class is not instantiable')

    @classmethod
    def echo(cls, index: int) -> int:
        """Returns the index unchanged. Both directions are marked, so a one-based caller
        passes a value and gets the same value back.

        Args:
            index:
        """
        return libbindings_tests.bindings_tests_Indices_echo(index)

    @classmethod
    def count_up_to(cls, index: int) -> int:
        """Index in, plain number out, so only the input converts.

        Args:
            index:
        """
        return libbindings_tests.bindings_tests_Indices_countUpTo(index)

    @classmethod
    def last_index_of(cls, count: int) -> int:
        """Plain number in, index out, so only the output converts.

        Args:
            count:
        """
        return libbindings_tests.bindings_tests_Indices_lastIndexOf(count)

    @classmethod
    def offset_from(cls, row: int = 0, col: int = 0) -> int:
        """Two indices that default to the first element. The default is written zero based
        here and each one-based wrapper shifts it, so an omitted argument converts the
        same way a supplied one does.

        Args:
            row:
            col:
        """
        return libbindings_tests.bindings_tests_Indices_offsetFrom(row, col)

    @classmethod
    def from_row(cls, row: int = 5) -> int:
        """An index whose default is not zero, which is what catches a default that gets
        converted twice. Omitting the argument has to reach Java as 5 in every language.

        Args:
            row:
        """
        return libbindings_tests.bindings_tests_Indices_fromRow(row)

    @classmethod
    def echo_size(cls, index: int) -> int:
        """A size_t index, so the size bridge and the index conversion have to compose.

        Args:
            index:
        """
        return libbindings_tests.bindings_tests_Indices_echoSize(index)


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
        error_ = _ErrorInfo()
        _checked(error_, libbindings_tests.bindings_tests_Isolated_alwaysFails(byref(error_)))


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
        total = sum(values)
        return libbindings_tests.bindings_tests_Overloads_total(total, factor)

    @classmethod
    def gain(cls, invert: bool = False, scale: float = 1e-3) -> float:
        """A boolean default and a double default in scientific notation. Each wrapper has
        to render both in its own syntax, which is where they tend to go wrong.

        Args:
            invert:
            scale:
        """
        return libbindings_tests.bindings_tests_Overloads_gain(invert, scale)

    @classmethod
    def echo_index(cls, index: int) -> int:
        """Returns the index unchanged. MATLAB converts to zero based indexing on the way
        in and back to one based on the way out, so it sees the value it passed in.
        Python clamps negatives instead, which matches C for every valid index.

        Args:
            index:
        """
        index = max(index, 0)
        return libbindings_tests.bindings_tests_Overloads_echoIndex(index)

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

        Returns:
            A named color. Values are ordinals, starting at zero.
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
    def describe(cls, color: Color, marker: Marker) -> str:
        """Takes both enums at once, so the two generated types have to coexist in one signature.

        Args:
            color:
            marker:
        """
        ptr_ = libbindings_tests.bindings_tests_Palette_describe(color, marker)
        return ptr_.decode("utf-8") if ptr_ is not None else ""

    @classmethod
    def from_ordinal(cls, ordinal: int) -> Color:
        """Returns the enum with the given ordinal, or Red if it is out of range.

        Args:
            ordinal:

        Returns:
            A named color. Values are ordinals, starting at zero.
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
        """Joins a char* array with a separator. Checks const char** parameter handling.

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


class Structs:
    """Reads fields out of a caller-owned struct."""

    def __new__(cls, *args, **kwargs):
        raise TypeError('Class is not instantiable')

    @classmethod
    def fill_field_types(cls, unit: Any, shorts: Any, longs: Sequence[int], context: Any | bytes | None) -> FieldTypes:
        """Fills the out parameter from the arguments. Pins the field types StatusInfo
        leaves out: the char code unit crosses as uint16, the pointers pass through
        untouched so the caller can verify them against what it handed in.

        Args:
            unit:
            shorts:
            longs:
            context:
        """
        np = _require_numpy()
        if shorts is not None and not isinstance(shorts, POINTER(c_short)):
            shorts = np.ascontiguousarray(shorts, dtype=np.int16).ctypes.data_as(POINTER(c_short))
        if longs is not None and not isinstance(longs, POINTER(c_longlong)):
            longs = np.ascontiguousarray(longs, dtype=np.int64).ctypes.data_as(POINTER(c_longlong))
        struct_ = libbindings_tests.bindings_tests_Structs_fillFieldTypes(unit, shorts, longs, context)
        return FieldTypes(struct_.unit)

    @classmethod
    def total_power(cls, status: StatusInfo, current_amps: float) -> float:
        """Power draw in watts for the given current. Returns zero unless the actuator is
        enabled and in the running state (2), so all three fields decide the result.

        Args:
            status:
            current_amps:
        """
        if not isinstance(status, _StatusInfo):
            status = _StatusInfo(status.voltage, status.state, int(status.enabled))
        return libbindings_tests.bindings_tests_Structs_totalPower(status, current_amps)

    @classmethod
    def checked_voltage(cls, status: StatusInfo) -> float:
        """Bus voltage in volts. Returns 0.0 through the ABI when it throws.

        Args:
            status:

        Raises:
            Exception: the actuator is disabled
        """
        if not isinstance(status, _StatusInfo):
            status = _StatusInfo(status.voltage, status.state, int(status.enabled))
        error_ = _ErrorInfo()
        return _checked(error_, libbindings_tests.bindings_tests_Structs_checkedVoltage(byref(error_), status))

    @classmethod
    def get_status(cls, index: int) -> StatusInfo:
        """Fills the out parameter with a canned reading for the given index: index 0
        reads (48.0, running, enabled), every other index reads (12.5, 1, disabled).

        Args:
            index:

        Returns:
            Status reading for the given index.
        """
        struct_ = libbindings_tests.bindings_tests_Structs_getStatus(index)
        return StatusInfo(struct_.voltage, struct_.state, bool(struct_.enabled))

    @classmethod
    def get_checked_status(cls, index: int) -> StatusInfo:
        """Same readings as getStatus. Every field of the out parameter reads as zero through the ABI when it throws.

        Args:
            index:

        Raises:
            Exception: the index is negative
        """
        error_ = _ErrorInfo()
        struct_ = _checked(error_, libbindings_tests.bindings_tests_Structs_getCheckedStatus(byref(error_), index))
        return StatusInfo(struct_.voltage, struct_.state, bool(struct_.enabled))
