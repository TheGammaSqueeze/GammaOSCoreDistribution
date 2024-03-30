"""
Command line tool to process minidump files and print what was logged by GfxApiLogger.

For more details see:

design: go/bstar-gfx-logging
g3doc:  http://g3doc/play/g3doc/games/battlestar/kiwivm/graphics-tips.md#gfx-logs
C++:    http://source/play-internal/battlestar/aosp/device/generic/vulkan-cereal/base/GfxApiLogger.h

Usage:

python3 print_gfx_logs.py <path to minidump file>
"""

import argparse
import ctypes
import sys
from datetime import datetime
import mmap
import textwrap
import command_printer
from typing import NamedTuple, Optional, List
import traceback


class Header(ctypes.Structure):
    """The C struct that we use to represent the data in memory
    Keep in sync with GfxApiLogger.h
    """
    _fields_ = [('signature', ctypes.c_char * 10),
                ('version', ctypes.c_uint16),
                ('thread_id', ctypes.c_uint32),
                ('last_written_time', ctypes.c_uint64),
                ('write_index', ctypes.c_uint32),
                ('committed_index', ctypes.c_uint32),
                ('capture_id', ctypes.c_uint64),
                ('data_size', ctypes.c_uint32)]


class Command(NamedTuple):
    """A single command in the stream"""
    opcode: int
    original_size: int
    data: bytes


class Stream(NamedTuple):
    """Stream of commands received from the guest"""
    pos_in_file: int  # Location of this stream in the minidump file, useful for debugging
    timestamp: int  # Unix timestamp of last command received, in milliseconds
    thread_id: int
    capture_id: int
    commands: List[Command]
    error_message: Optional[str]  # `None` if there were no errors parsing this stream


def read_uint32(buf: bytes, pos: int) -> int:
    """Reads a single uint32 from buf at a given position"""
    assert pos + 4 <= len(buf)
    return int.from_bytes(buf[pos:pos + 4], byteorder='little', signed=False)


def process_command(buf: bytes) -> Command:
    opcode = read_uint32(buf, 0)
    size = read_uint32(buf, 4)
    return Command(opcode, size, bytes(buf[8:]))


def process_stream(file_bytes: mmap, file_pos: int) -> Stream:
    # Read the header
    file_bytes.seek(file_pos)
    header = Header()
    header_bytes = file_bytes.read(ctypes.sizeof(header))
    ctypes.memmove(ctypes.addressof(header), header_bytes, ctypes.sizeof(header))

    if header.signature != b'GFXAPILOG':
        return Stream(file_pos, error_message="Signature doesn't match")

    if header.version != 2:
        return Stream(file_pos, error_message=("This script can only process version 2 of the graphics API logs, " +
                                               "but the dump file uses version {} ").format(data.version))

    # Convert Windows' GetSystemTimeAsFileTime to Unix timestamp
    # https://stackoverflow.com/questions/1695288/getting-the-current-time-in-milliseconds-from-the-system-clock-in-windows
    timestamp_ms = int(header.last_written_time / 10000 - 11644473600000)
    if timestamp_ms <= 0: timestamp_ms = 0

    # Sanity check the size
    if header.data_size > 5_000_000:
        return Stream(file_pos,
                      error_message="data size is larger than 5MB. This likely indicates garbage/corrupted data")

    if header.committed_index >= header.data_size:
        return Stream(file_pos,
                      error_message="index is larger than buffer size. Likely indicates garbage/corrupted data")

    file_bytes.seek(file_pos + ctypes.sizeof(header))
    data = file_bytes.read(header.data_size)

    # Reorder the buffer so that we can read it in a single pass from back to front
    buf = data[header.committed_index:] + data[:header.committed_index]

    commands = []
    i = len(buf)
    while i >= 4:
        i -= 4
        size = read_uint32(buf, i)
        if size == 0 or size > i:
            # We reached the end of the stream
            break
        cmd = process_command(buf[i - size:i])

        commands.append(cmd)
        i -= size

    commands.reverse()  # so that they're sorted from oldest to most recent
    return Stream(file_pos, timestamp_ms, header.thread_id, header.capture_id, commands, None)


def process_minidump(dump_file: str) -> List[Stream]:
    """
    Extracts a list of commands streams from a minidump file
    """
    streams = []
    with open(dump_file, "r+b") as f:
        mm = mmap.mmap(f.fileno(), 0)
        pos = 0
        while True:
            pos = mm.find(b'GFXAPILOG', pos + 1)
            if pos == -1:
                break
            streams.append(process_stream(mm, pos))

    return streams


def main():
    parser = argparse.ArgumentParser(description="""Command line tool to process crash reports and print out the 
    commands logged by GfxApiLogger""")
    parser.add_argument('dump_file', help="Path to  minidump file")

    args = parser.parse_args()
    streams = process_minidump(args.dump_file)

    streams.sort(key=lambda s: s.timestamp)

    for stream_idx, stream in enumerate(streams):
        print(textwrap.dedent("""
                  ======================================================= 
                  GfxApiLog command stream #{} at offset {} in dump
                    - Timestamp: {}
                    - Thread id: {}
                    - Capture id: {}""".format(stream_idx, stream.pos_in_file,
                                               datetime.fromtimestamp(stream.timestamp / 1000.0),
                                               stream.thread_id,
                                               stream.capture_id)))
        if stream.error_message:
            print("Could not decode stream. Error: ", stream.error_message)
            continue

        subdecode_size = 0
        for cmd_idx, cmd in enumerate(stream.commands):
            cmd_printer = command_printer.CommandPrinter(cmd.opcode, cmd.original_size, cmd.data, stream_idx, cmd_idx)

            try:
                cmd_printer.print_cmd()
            except:
                # Print stack trace and continue
                traceback.print_exc(file=sys.stdout)

            if subdecode_size > 0:
                subdecode_size -= cmd.original_size
                assert subdecode_size >= 0
                if subdecode_size == 0:
                    print("\n--- end of subdecode ---")

            if cmd_printer.cmd_name() == "OP_vkQueueFlushCommandsGOOGLE":
                assert subdecode_size == 0
                subdecode_size = cmd.original_size - 36
                print("\n--- start of subdecode, size = {} bytes ---".format(subdecode_size))


if __name__ == '__main__':
    main()
