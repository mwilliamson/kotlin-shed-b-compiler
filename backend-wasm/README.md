# WASM backend

All values are i32.

* Ints are i32s, but should really be pointers to BigInts
* Bools:
  * `0` -> `false`
  * `1` -> `true`
* Strings are pointers to structs:
  * First 4 bytes are a u32 containing the length of the string contents
  * Remaining bytes are the UTF-8 encoded string contents

