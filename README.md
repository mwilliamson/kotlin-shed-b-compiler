# Shed programming language

# State

State should be an effect over a heap.
For instance:

```
fun increment[H: Heap](x: Ref[H]) ! State[H] -> Unit {
    x.set(x.get() + 1);
}
```

If a function uses state internally but the heap is not present in any arguments or the return type,
the effect can be dropped in the signature:

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

* Check number of static arguments
