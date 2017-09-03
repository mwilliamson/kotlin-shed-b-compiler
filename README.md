# Shed programming language

## TODO

Ensure that unions have distinct members.
For instance, the following would not be allowed:

    union T = Int | Any

since the expression

    4: T
    
is ambiguous.

However, the following is fine:

    union T = Int | String

When a value could be coerced to either member type,
the compiler requires the value to be explicitly coerced to a member type.
For instance, to coerce `Bottom` to `T`, it must first be coerced to either `Int` or `String`.

Some work is needed to support this properly in the backends,
since generics are erased.
