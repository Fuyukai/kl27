#!/usr/bin/env python3
"""
The KL27 source code compiler.
Unlike the reference implementation, this is implemented in Python 3.6.

This compiler does several things:

 - Parses the KLT file line by line
 - Generates a label table
 - Parses and creates each instruction
 - Inserts label addresses
"""
import argparse
import pprint
import struct
import sys

# the jump resolver
from collections import OrderedDict


class LabelPlaceholder:
    def __init__(self, label_name: str):
        self.label_name = label_name

    def resolve(self, table: dict):
        """
        Resolves the jump address for this label.
        """
        return table[self.label_name][0].to_bytes(2, byteorder="big")


# function definitions
# all functions take one arg, the line
# and returns an iterable of bytestrings or similar
def compile_nop(line: str):
    # fmt: `nop`
    return [b"\x00\x00\x00\x00"]


def compile_jmpl(line: str):
    # fmt: `jmpl <label>`
    # pass the line directly
    pl = LabelPlaceholder(line)
    return [b"\x00\x01", pl]


def kl27_compile(args: argparse.Namespace):
    print("compiling", args.infile)

    with open(args.infile) as f:
        data = f.read()

    # current offset
    current_pointer = 0
    # label to address mapping
    label_table = {}
    # machine code memory
    code = []

    # current label
    current_label = None

    for lineno, line in enumerate(data.splitlines()):
        # clean up shit whitespace
        line = line.lstrip().rstrip()
        # ignore whitespace
        if not line:
            continue

        # strip comments
        if line.startswith("//"):
            continue

        # check if it's a label
        if line.endswith(":"):
            # save the address of this label
            current_label = line[:-1]
            if current_label in label_table:
                print(f"warning: redefined label {current_label}, old code is unreachable")

            label_table[current_label] = (len(label_table), current_pointer)
            print(f"\ncompiling label {current_label} at address {current_pointer}")
            # don't increment the pointer, labels don't have pointers
            continue

        if current_label is None:
            if args.no_automatic_main:
                print(f"error: line {lineno}: no label specified.")
                return 1
            print("warning: no label specified, assuming main")
            print("(pass --no-automatic-main to disable this)")
            current_label = "main"
            label_table[current_label] = (len(label_table), current_pointer)

        instruction = line.split(" ")[0]

        # extract the function to compile the instruction
        glob = globals()
        func = f"compile_{instruction}"
        if func not in glob:
            print(f"error: line {lineno}: unknown instruction `{instruction}`.")
            return 1
        print(f"compiling instruction {instruction} at address {current_pointer} inside {current_label}")

        f = glob[func]
        # call with the rest of the line to parse and construct
        instructions = f(" ".join(line.split(" ")[1:]))
        code.extend(instructions)

        # increment the pointer by 4
        current_pointer += 4

    print("\nlabel table:")
    pprint.pprint(label_table)

    if args.entry_point not in label_table:
        print("error: could not find entry point label")
        return 1

    entry: int = label_table[args.entry_point][1]
    print(f"`{args.entry_point}` entry point address:", entry)

    # generate the header

    print("\ngenerating label table...")
    final_label_table = [
        len(label_table).to_bytes(length=4, byteorder="big")
    ]

    for id, addr in label_table.values():
        # pack the (id, addr)
        final_label_table.append(
            struct.pack(">IH", id, addr)
        )

    final_label_table = b"".join(final_label_table)

    print("generated", len(label_table), "labels")

    def fix_jumps():
        loop_code = code.copy()

        for n, i in enumerate(loop_code):
            if isinstance(i, LabelPlaceholder):
                print("resolving jump for", i.label_name, "to", label_table[i.label_name])
                # replace it with the resolved
                code[n] = i.resolve(label_table)

    print("\nfixing jumps...")
    fix_jumps()
    final_code = b"".join(code)

    print("instructions parsed (est.):", len(final_code) // 4)

    print("\ngenerating header...")
    corrected_entry_point = (16 + len(final_label_table) + entry)
    print("corrected entry point:", corrected_entry_point)
    header = []
    # 1: magic number
    header += [b"KL27"]
    # 2: K_VERSION, which is 1
    header += [b"\x01"]
    # 3: K_COMPRESS
    header += [b"\x00"]
    # 4: K_BODY, the main entry point
    header += [(16 + len(final_label_table) + entry).to_bytes(4, byteorder="big")]
    # 5: K_STACKSIZE
    header += [(255).to_bytes(2, byteorder="big")]
    # 6: K_CHECKSUM, ignore this for now
    header += [b"\x00\x00\x00\x00"]
    header = b"".join(header)

    with open(args.outfile, 'wb') as out:
        out.write(header)
        out.write(final_label_table)
        out.write(final_code)

    print("compiled file successfully!")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="KL27 basic compiler")
    parser.add_argument("-i", "--infile", help="The input file to parse.")
    parser.add_argument("-o", "--outfile", help="The output file to produce.")

    parser.add_argument_group("Compiler options")
    parser.add_argument("--entry-point", default="main", help="The entry point to use")
    parser.add_argument("--no-automatic-main", action="store_true")

    args = parser.parse_args()

    sys.exit(kl27_compile(args))
