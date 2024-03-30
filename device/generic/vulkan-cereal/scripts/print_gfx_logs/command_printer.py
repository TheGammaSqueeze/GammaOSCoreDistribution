import io
import textwrap
from typing import Dict
import vulkan_printer


class CommandPrinter:
    """This class is responsible for printing the commands found in the minidump file to the terminal."""

    def __init__(self, opcode: int, original_size: int, data: bytes, stream_idx: int, cmd_idx: int):
        self.opcode = opcode
        self.original_size = original_size
        self.data = io.BytesIO(data)
        self.stream_idx = stream_idx
        self.cmd_idx = cmd_idx

    def print_cmd(self):
        """
        Tries to decode and pretty print the command to the terminal.
        Falls back to printing hex data if the command doesn't have a printer.
        """

        # Print out the command name
        print("\n{}.{} - {}: ({} bytes)".format(self.stream_idx, self.cmd_idx, self.cmd_name(), self.original_size - 8))

        pretty_printer = getattr(vulkan_printer, self.cmd_name(), None)
        if not pretty_printer:
            self.print_raw()
            return

        try:
            pretty_printer(self, indent=4)
            # Check that we processed all the bytes, otherwise there's probably a bug in the pretty printing logic
            if self.data.tell() != len(self.data.getbuffer()):
                raise BufferError(
                    "Not all data was decoded. Decoded {} bytes but expected {}".format(
                        self.data.tell(), len(self.data.getbuffer())))
        except Exception as ex:
            print("Error while processing {}: {}".format(self.cmd_name(), repr(ex)))
            print("Command raw data:")
            self.print_raw()
            raise ex

    def cmd_name(self) -> str:
        """Returns the command name (e.g.: "OP_vkBeginCommandBuffer", or the opcode as a string if unknown"""
        return vulkan_printer.opcodes.get(self.opcode, str(self.opcode))

    def print_raw(self):
        """Prints the command data as a hex bytes, as a fallback if we don't know how to decode it"""
        truncated = self.original_size > len(self.data.getbuffer()) + 8
        indent = 8
        hex = ' '.join(["{:02x}".format(x) for x in self.data.getbuffer()])
        if truncated:
            hex += " [...]"
        lines = textwrap.wrap(hex, width=16 * 3 + indent, initial_indent=' ' * indent, subsequent_indent=' ' * indent)
        for l in lines:
            print(l)

    def read_int(self, num_bytes: int, signed: bool = False) -> int:
        assert num_bytes == 4 or num_bytes == 8
        buf = self.data.read(num_bytes)
        if len(buf) != num_bytes:
            raise EOFError("Unexpectly reached the end of the buffer")
        return int.from_bytes(buf, byteorder='little', signed=signed)

    def write(self, msg: str, indent: int):
        """Prints a string at a given indentation level"""
        assert type(msg) == str
        assert type(indent) == int and indent >= 0
        print("  " * indent + msg, end='')

    def write_int(self,
                  field_name: str,
                  num_bytes: int,
                  indent: int,
                  signed: bool = False,
                  value: int = None):
        """Reads the next 32 or 64 bytes integer from the data stream and prints it"""
        if value is None:
            value = self.read_int(num_bytes, signed)
        self.write("{}: {}\n".format(field_name, value), indent)

    def write_enum(self, field_name: str, enum: Dict[int, str], indent: int, value: int = None):
        """Reads the next 32-byte int from the data stream and prints it as an enum"""
        if value is None:
            value = self.read_int(4)
        self.write("{}: {} ({})\n".format(field_name, enum.get(value, ""), value), indent)

    def write_stype_and_pnext(self, expected_stype: str, indent: int):
        """Reads and prints the sType and pNext fields found in many Vulkan structs, while also sanity checking them"""
        stype = self.read_int(4)
        self.write_enum("sType", vulkan_printer.VkStructureType, indent, value=stype)
        if vulkan_printer.VkStructureType.get(stype) != expected_stype:
            raise ValueError("Wrong sType while decoding data. Expected: " + expected_stype)

        pnext_size = self.read_int(4)
        self.write_int("pNextSize", 4, indent, value=pnext_size)
        if pnext_size != 0:
            raise NotImplementedError("Decoding structs with pNextSize > 0 not supported")

    def write_struct(self, field_name: str, struct_fn, indent: int):
        """Reads and prints a struct, calling `struct_fn` to pretty-print it"""
        self.write("{}:\n".format(field_name), indent)
        struct_fn(self, indent + 1)

    def write_repeated(self,
                       count_name: str,
                       field_name: str,
                       struct_fn,
                       indent: int,
                       pointer_name: str = None):
        """
        Reads and prints repeated structs, with a 32-byte count field followed by the struct data.
        If pointer_name is not None, reads an additional 64-bit pointer within the repeated block
        before reading repeated data.
        """
        count = self.read_int(4)
        if pointer_name is not None:
            self.write_int(pointer_name, 8, indent)
        assert count < 1000, "count too large: {}".format(count)  # Sanity check that we haven't read garbage data
        self.write_int(count_name, 4, indent, value=count)
        for i in range(0, count):
            self.write_struct("{} #{}".format(field_name, i), struct_fn, indent)
