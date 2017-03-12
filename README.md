# KL27

The **KL27** is a 16-bit virtual CPU. It reads in K27 compiled assembly files to run programs.

## Implementation

This repository contains the reference implementation of the K27 compiler and VM. 

### Compiler

The **compiler** (really an assembler) is written in **Python 3.6**.  
Usage:

```
usage: compiler.py [-h] [-i INFILE] [-o OUTFILE] [--entry-point ENTRY_POINT]
                   [--no-automatic-main]

KL27 basic compiler

optional arguments:
  -h, --help            show this help message and exit
  -i INFILE, --infile INFILE
                        The input file to parse.
  -o OUTFILE, --outfile OUTFILE
                        The output file to produce.
  --entry-point ENTRY_POINT
                        The entry point to use
  --no-automatic-main

```

### VM

The **virtual machine** used to execute code is written in Kotlin 1.1.0, on top of the Java 8 JVM.  
This VM uses `libgdx` to display the fake screen.

To run the VM, use `./gradlew desktop:run`. The path is currently hardcoded in, TODO: fix this.