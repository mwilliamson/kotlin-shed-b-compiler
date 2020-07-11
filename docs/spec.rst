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

Typing rules
------------

Typing judgements are in the form:

.. math::

    \Gamma \turnstile e \effect \varepsilon : \uptau

meaning that in the context :math:`\Gamma`, the expression :math:`e` has effect :math:`\varepsilon` and type :math:`\uptau`.

An expression with an effect can be treated as having a super-effect.

.. math::

    \frac{
        \Gamma \turnstile e \effect \varepsilon_1 : \uptau \qquad
        \varepsilon_1 \leq \varepsilon_2
    } {
        \Gamma \turnstile e \effect \varepsilon_2 : \uptau
    }

An expression of a type can be treated as having a supertype.

.. math::

    \frac{
        \Gamma \turnstile e \effect \varepsilon : \uptau_1 \qquad
        \uptau_1 <: \uptau_2
    } {
        \Gamma \turnstile e \effect \varepsilon : \uptau_2
    }

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

    \turnstile bool: \type{Bool}

Integer literal
~~~~~~~~~~~~~~~

.. math::

    int \produces \regex{-?[0-9]+}

Integer literals are of type ``Int``.

.. math::

    \turnstile int: \type{Int}

String literal
~~~~~~~~~~~~~~

.. math::

    string \produces \literal{"} \seq unicode\_scalar\_symbol* \seq \literal{"}

String literals are of type ``String``.

.. math ::

    \turnstile string: \type{String}

Unicode scalar literal
~~~~~~~~~~~~~~~~~~~~~~

.. math::

    unicode\_scalar \produces \literal{'} \seq unicode\_scalar\_symbol \seq \literal{'}

Unicode scalar literals are of type ``UnicodeScalar``.

.. math::

    \turnstile unicode\_scalar: \type{UnicodeScalar}

Unit literal
~~~~~~~~~~~~

.. math::

    unit \produces \literal{unit}

Unit literals are of type ``Unit``.

.. math::

    \turnstile unit: \type{Unit}

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
        \Gamma \turnstile e_1 \effect \varepsilon : \uptau_1 \qquad
        \Gamma \turnstile e_2 \effect \varepsilon : \uptau_2 \qquad
        \dots \qquad
        \Gamma \turnstile e_n \effect \varepsilon : \uptau_n
    }{
        \Gamma \turnstile \literal{\#(}e_1\literal{,} e_2\literal{,} \dots\literal{,} e_n\literal{)} \effect \varepsilon  : \#(\uptau_1, \uptau_2, \dots, \uptau_n)
    }

Sub-expressions are evaluated from left to right.

.. math::

    \frac{
        e_i \rightarrow e_i'
    }{
        \literal{\#(}v_1\literal{,} v_2\literal{,} \dots \literal{,} v_{i - 1} \literal{,} e_i \literal{,} \dots \literal{,} e_n\literal{)} \rightarrow
        \literal{\#(}v_1\literal{,} v_2\literal{,} \dots \literal{,} v_{i - 1} \literal{,} e_i' \literal{,} \dots \literal{,} e_n\literal{)}
    }

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
        \Gamma \turnstile e \effect \varepsilon : \type{Int}
    } {
        \Gamma \turnstile \literal{-}e \effect \varepsilon : \type{Int}
    }

The ``not`` operator transforms a ``Bool`` to its negation.

.. math::

    \frac{
        \Gamma \turnstile e \effect \varepsilon : \type{Bool}
    } {
        \Gamma \turnstile \literal{not} \> e \effect \varepsilon : \type{Bool}
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
Depending on the value of the left operand,
:math:`\literal{\&\&}` and :math:`\literal{||}` operations may not evaluate the right operand.
All other operations will evaluate both operands.

The ``==`` operator operates on two scalar operands of the same type.
It evaluates to true if the operands are equal, false otherwise.
TODO: define string equality (normalised? scalar value equality? byte equality?)

.. math::

    \frac{
        \uptau \in \{\type{Bool}, \type{Int}, \type{String}, \type{UnicodeScalar}\} \qquad
        \Gamma \turnstile e_1 \effect \varepsilon : \uptau \qquad
        \Gamma \turnstile e_2 \effect \varepsilon : \uptau
    } {
        \Gamma \turnstile e_1 \> \literal{==} \> e_2 \effect \varepsilon : \type{Bool}
    }

The ``!=`` operator is the negation of the ``==`` operator.

.. math::

    \frac{
        \uptau \in \{\type{Bool}, \type{Int}, \type{String}, \type{UnicodeScalar}\} \qquad
        \Gamma \turnstile e_1 \effect \varepsilon : \uptau \qquad
        \Gamma \turnstile e_2 \effect \varepsilon : \uptau
    } {
        \Gamma \turnstile e_1 \> \literal{!=} \> e_2 \effect \varepsilon : \type{Bool}
    }

.. math::

    \frac{
        \uptau \in \{\type{Int}, \type{UnicodeScalar}\} \qquad
        \Gamma \turnstile e_1 \effect \varepsilon : \uptau \qquad
        \Gamma \turnstile e_2 \effect \varepsilon : \uptau
    } {
        \Gamma \turnstile e_1 \> \literal{<} \> e_2 \effect \varepsilon : \type{Bool}
    }

.. math::

    \frac{
        \uptau \in \{\type{Int}, \type{UnicodeScalar}\} \qquad
        \Gamma \turnstile e_1 \effect \varepsilon : \uptau \qquad
        \Gamma \turnstile e_2 \effect \varepsilon : \uptau
    } {
        \Gamma \turnstile e_1 \> \literal{<=} \> e_2 \effect \varepsilon : \type{Bool}
    }

.. math::

    \frac{
        \uptau \in \{\type{Int}, \type{UnicodeScalar}\} \qquad
        \Gamma \turnstile e_1 \effect \varepsilon : \uptau \qquad
        \Gamma \turnstile e_2 \effect \varepsilon : \uptau
    } {
        \Gamma \turnstile e_1 \> \literal{>} \> e_2 \effect \varepsilon : \type{Bool}
    }

.. math::

    \frac{
        \uptau \in \{\type{Int}, \type{UnicodeScalar}\} \qquad
        \Gamma \turnstile e_1 \effect \varepsilon : \uptau \qquad
        \Gamma \turnstile e_2 \effect \varepsilon : \uptau
    } {
        \Gamma \turnstile e_1 \> \literal{>=} \> e_2 \effect \varepsilon : \type{Bool}
    }

.. math::

    \frac{
        \Gamma \turnstile e_1 \effect \varepsilon : \type{Bool} \qquad
        \Gamma \turnstile e_2 \effect \varepsilon : \type{Bool}
    } {
        \Gamma \turnstile e_1 \> \literal{\&\&} \> e_2 \effect \varepsilon : \type{Bool}
    }

.. math::

    \frac{
        \Gamma \turnstile e_1 \effect \varepsilon : \type{Bool} \qquad
        \Gamma \turnstile e_2 \effect \varepsilon : \type{Bool}
    } {
        \Gamma \turnstile e_1 \> \literal{||} \> e_2 \effect \varepsilon : \type{Bool}
    }

.. math::

    \frac{
        \Gamma \turnstile e_1 \effect \varepsilon : \type{Int} \qquad
        \Gamma \turnstile e_2 \effect \varepsilon : \type{Int}
    } {
        \Gamma \turnstile e_1 \> \literal{+} \> e_2 \effect \varepsilon : \type{Int}
    }

.. math::

    \frac{
        \Gamma \turnstile e_1 \effect \varepsilon : \type{Int} \qquad
        \Gamma \turnstile e_2 \effect \varepsilon : \type{Int}
    } {
        \Gamma \turnstile e_1 \> \literal{-} \> e_2 \effect \varepsilon : \type{Int}
    }

.. math::

    \frac{
        \Gamma \turnstile e_1 \effect \varepsilon : \type{Int} \qquad
        \Gamma \turnstile e_2 \effect \varepsilon : \type{Int}
    } {
        \Gamma \turnstile e_1 \> \literal{*} \> e_2 \effect \varepsilon : \type{Int}
    }
