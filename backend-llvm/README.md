# LLVM backend

All values are 64-bit integers,
which are either direct encodings or pointers to other data.

* Ints are represented as 64-bit integers,
  but should really be pointers to BigInts.

* Bools: `false` and `true` are `0` and `1` respectively.

* Unicode scalars are directly represented as 64-bit integers.

* Strings are pointers to variable-length structs:
  * u64: the number of bytes required to UTF-8 encode the string
  * u8[]: the UTF-8 encoding of the string

* Functions are pointers to variable-length structs:
  * u64: pointer to the low-level function
  * i64[]: the function environment i.e. the variables the function closes over

  A Shed function is invokved by calling the low-level function
  with the pointer to the environment as the first argument,
  and the Shed arguments as the remaining arguments.

Memory is managed by the Boehm–Demers–Weiser garbage collector.
