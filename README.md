# Shed programming language

## Specification

### Values

#### `Bool`

`Bool` has two possible values, `true` and `false`.

#### `Int`

Values of type `Int` are arbitary-precision integers.

#### `String`

Values of type `String` are strings of unicode scalars.

#### `UnicodeScalar`

A `UnicodeScalar` is a single Unicode scalar.

#### `Unit`

`Unit` has one possible value, `unit`.

#### Tuples

Tuples are fixed-length lists of values.
For instance, the type `#(Int, Bool)` is a tuple where the first element is an `Int`, and the second element is a `Bool`.

### Expressions

#### Boolean literal

    <expr> ::= "true" | "false"

Boolean literals are of type `Bool`.

    ⊢ b: Bool

#### Integer literal

    <expr> ::= /-?[0-9]+/

Integer literals are of type `Int`.

    ⊢ i: Int

#### String literal

    <expr> ::= '"' <Unicode scalar> * '"'

String literals are of type `String`.

    ⊢ s: String

#### Unicode scalar literal

    <expr> ::= "'" <Unicode scalar> "'"

Unicode scalar literals are of type `UnicodeScalar`.

    ⊢ c: UnicodeScalar

#### Unit literal

    <expr> ::= "unit"

Unit literals are of type `Unit`.

    ⊢ u: Unit

#### Tuple literal

    <expr> ::= "#(" <tuple-contents> ? ")"
    <tuple-contents> ::= <expr> ("," <expr>) * "," ?

Tuple types correspond to their element types:

    𝚪 ⊢ e_1: τ_1    𝚪 ⊢ e_2: τ_2    ...    𝚪 ⊢ e_n: τ_n
    ________________________________________________
    𝚪 ⊢ #(e_1, e_2, ..., e_n): #(τ_1, τ_2, ..., τ_n)

## State

State should be an effect over a heap.
For instance:

```
fun increment[H: Heap](x: Ref[H]) ! State[H] -> Unit {
    x.set(x.get() + 1);
}
```

If a function uses state internally but the heap is not present in any arguments or the return type,
we can use a local heap:

```
fun fibonacci(n: Int) -> Int {
    heap H;
    val x = ref[H](0);
    val y = ref[H](1);
    repeat(n, fun () => {
        val tmp = y.get();
        y.set(x.get() + y.get());
        x.set(tmp);
    });
    x.get()
}
```

Is there a way to define this without having to add heaps to the syntax? e.g.

fun fibonacci(n: Int) -> Int {
    withHeap(fun[H: Heap]() ! State[H] -> Int {
        ...
    });
}

The type system probably isn't expressive enough to describe withHeap without a specialised type.
Automatically inferring effects might help, something like:

fun fibonacci(n: Int) -> Int {
    withHeap(fun[H: Heap]() !* -> Int {
        ...
    });
}

but is automatic inference of effects desirable?

Could add some extra syntax to make gets nicer:

```
fun fib3(n: Int) -> Int {
    heap h;
    val x = ref[h](0);
    val y = ref[h](1);
    repeat(n, fun () => {
        val tmp = *y;
        y.set(*x + *y);
        x.set(tmp);
    });
    *x
}
```

Syntax for assignments could be similar e.g. `*x = tmp`,
although parsing is harder.
Alternative would be to use `:=` i.e. `x := tmp`.

## Alternative syntax for tags/shapes

```
shape OptionShape {
    tag: Symbol;
}

shape Some[+T] <: OptionShape {
    OptionShape.tag = @Some;
    value: T;
}

shape None <: OptionShape {
    OptionShape.tag = @None;
}

type Option[+T] = Some[T] | None;
```

More explicit, but more verbose.
`is` checks become more work for the compiler (have to find the relevant field that acts as a discriminator).
If we add in private fields, we can hide the tag (the compiler still has enough information to discriminate types).

## TODO

* Probably remove symbols unless a compelling use-case presents itself.
  Handle open unions more directly/explicitly.

* Use ranges for sources

* Add source to types

* Produce better explanations of mismatched types

* Change modules to UpperCamelCase

* Optimise directly recursive tail calls
  * Make opt-in/explicit? Implement using while where possible, fallback to trampolines.

* Check number of static arguments

* Rename Any type to something else (Top?) to avoid conflation with any in (for instance) TypeScript

* Don't allow references to compile-time only values
  * Or don't have a notion of compile-time only values?

* Check usages of defer() in type-checker
  * Function bodies can be deferred, but should be checked on first use

* Use canonical representation for Unicode comparisons?
