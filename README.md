# Shed programming language

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

## TODO

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

* Consider how to handle functions that have multiple non local return effects
  * Forbid?
  * Allow if different, non-overlapping types? How to distinguish them in type system and implementation?

* Decide whether to allow Type[_] for non-shape types e.g. unions.
  At the moment, allowed in the type system, but not implemented in the backends.

* In LLVM backend, operation handlers have anonymous names -- should be able to give them a better name

* Forbid returning from operation handlers

* Allow no return when there's an expression statement calling a function that returns never
