Language specification
======================

Values
------

``Bool``
~~~~~~~~

``Bool`` has two possible values, ``true`` and ``false``.

``Int``
~~~~~~~

Values of type ``Int`` are arbitary-precision integers.

``String``
~~~~~~~~~~

Values of type ``String`` are strings of unicode scalars.

``UnicodeScalar``
~~~~~~~~~~~~~~~~~

A ``UnicodeScalar`` is a single Unicode scalar.

``Unit``
~~~~~~~~

``Unit`` has one possible value, ``unit``.

Tuples
~~~~~~

Tuples are fixed-length lists of values.
For instance, the type ``#(Int, Bool)`` is a tuple where the first element is an ``Int``, and the second element is a ``Bool``.

Expressions
-----------

.. math::
    :nowrap:

    \begin{align*}
    e
        \produces & bool \\
        \choice & int \\
        \choice & string \\
        \choice & unicode\_scalar \\
        \choice & unit \\
        \choice & tuple \\
        \choice & unary\_operation \\
        \choice & binary\_operation \\
    \end{align*}

Boolean literal
~~~~~~~~~~~~~~~

.. .. productionlist::
     expr: "true" | "false"

.. math::

    bool \produces \literal{true} \choice \literal{false}

Boolean literals are of type ``Bool``.

.. math::

    \turnstile bool: Bool

Integer literal
~~~~~~~~~~~~~~~

.. math::

    int \produces \regex{-?[0-9]+}

Integer literals are of type ``Int``.

.. math::

    \turnstile int: Int

String literal
~~~~~~~~~~~~~~

.. math::

    string \produces \literal{"} \seq unicode\_scalar\_symbol* \seq \literal{"}

String literals are of type ``String``.

.. math ::

    \turnstile string: String

Unicode scalar literal
~~~~~~~~~~~~~~~~~~~~~~

.. math::

    unicode\_scalar \produces \literal{'} \seq unicode\_scalar\_symbol \seq \literal{'}

Unicode scalar literals are of type ``UnicodeScalar``.

.. math::

    \turnstile unicode\_scalar: UnicodeScalar

Unit literal
~~~~~~~~~~~~

.. math::

    unit \produces \literal{unit}

Unit literals are of type ``Unit``.

.. math::

    \turnstile unit: Unit

Tuple literal
~~~~~~~~~~~~~

.. math::
    :nowrap:

    \begin{align*}
    tuple \produces & \literal{\#(} \seq \optional{tuple\_contents} \seq \literal{)} \\
    tuple\_contents \produces & e \seq (\literal{,} \seq e) * \seq \literal{,} ?
    \end{align*}

Tuple types correspond to their element types:

.. math::

    \frac{
    \Gamma \turnstile e_1: \uptau_1 \qquad
    \Gamma \turnstile e_2: \uptau_2 \qquad
    ... \qquad
    \Gamma \turnstile e_n: \uptau_n
    }{
    \Gamma \turnstile \literal{\#(}e_1\literal{,} e_2\literal{,} ...\literal{,} e_n\literal{)}: \#(\uptau_1, \uptau_2, ..., \uptau_n)
    }

Sub-expressions are evaluated from left to right.

Unary operations
~~~~~~~~~~~~~~~~

.. math::
    :nowrap:

    \begin{align*}
    unary\_operation \produces & unary\_operator \seq e \\
    unary\_operator \produces & \literal{-} \choice \literal{not}
    \end{align*}

The ``-`` operator transforms an ``Int`` to its negation.

.. math::

    \frac{
        \Gamma \turnstile e: Int
    } {
        \Gamma \turnstile \literal{-}e: Int
    }

The ``not`` operator transforms a ``Bool`` to its negation.

.. math::

    \frac{
        \Gamma \turnstile e: Bool
    } {
        \Gamma \turnstile \literal{not} \> e: Bool
    }

Binary operations
~~~~~~~~~~~~~~~~~

.. math::
    :nowrap:

    \begin{align*}
    binary\_operation \produces & e \seq binary\_operator \seq e \\
    binary\_operator
        \produces & \literal{==} \\
        \choice & \literal{!=} \\
        \choice & \literal{<} \\
        \choice & \literal{<=} \\
        \choice & \literal{>} \\
        \choice & \literal{>=} \\
        \choice & \literal{\&\&} \\
        \choice & \literal{||} \\
        \choice & \literal{+} \\
        \choice & \literal{-} \\
        \choice & \literal{*} \\
    \end{align*}

The left operand is evaluated before the right operand.

The ``==`` operator operates on two scalar operands of the same type.
It evaluates to true if the operands are equal, false otherwise.
TODO: define string equality (normalised? scalar value equality? byte equality?)

.. math::

    \frac{
        \Gamma \turnstile e_1: \uptau \qquad
        \Gamma \turnstile e_2: \uptau
    } {
        \Gamma \turnstile e_1 \> \literal{==} \> e_2: Bool
    }

    \\
    \textrm{where} \quad \uptau \in {Bool, Int, String, UnicodeScalar}

The ``!=`` operator is the negation of the ``==`` operator.

.. math::

    \frac{
        \Gamma \turnstile e_1: \uptau \qquad
        \Gamma \turnstile e_2: \uptau
    } {
        \Gamma \turnstile e_1 \> \literal{!=} \> e_2: Bool
    }

    \\
    \textrm{where} \quad \uptau \in {Bool, Int, String, UnicodeScalar}
