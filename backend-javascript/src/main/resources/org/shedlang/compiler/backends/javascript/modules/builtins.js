function declareShape(name, tagValue, fields) {
    function shape(fieldValues = {}) {
        return {...fieldValues, ...constantFieldValues, $tagValue: tagValue};
    }

    shape.$typeTagValue = tagValue;
    shape.fields = {};
    Object.defineProperty(shape, "name", {value: name});

    const constantFieldValues = {};
    fields.forEach(field => {
        if (field.isConstant) {
            constantFieldValues[field.jsName] = field.value;
        }

        shape.fields[field.jsName] = field;
    })

    return shape;
}

function defineEffect(operationNames) {
    const effect = {};

    for (const operationName of operationNames) {
        effect[operationName] = (...args) => {
            const error = new Error();
            error.args = args;
            error.operationName = operationName;
            throw error;
        };
    }

    return effect;
}

function handle(func, handlers) {
    try {
        return func();
    } catch (error) {
        for ([operationName, handler] of handlers) {
            if (error.operationName === operationName) {
                return handler(...error.args);
            }
        }
        throw error;
    }
}

async function handleAsync(func, handlers) {
    try {
        return await func();
    } catch (error) {
        // TODO: check effect
        for ([operationName, handler] of handlers) {
            if (error.operationName === operationName) {
                return handler.async(...error.args);
            }
        }
        throw error;
    }
}

handle.async = handleAsync;

function partial(receiver, positionalArguments, namedArguments, moreNamedArguments) {
    if (moreNamedArguments) {
        const func = (...args) => {
            args[args.length - 1] = Object.assign({}, args[args.length - 1], namedArguments);

            return receiver(...positionalArguments, ...args);
        };

        func.async = (...args) => {
            args[args.length - 1] = Object.assign({}, args[args.length - 1], namedArguments);

            return receiver.async(...positionalArguments, ...args);
        };

        return func;
    } else {
        const func = (...args) => {
            return receiver(...positionalArguments, ...args, namedArguments);
        };

        func.async = (...args) => {
            return receiver.async(...positionalArguments, ...args, namedArguments);
        };

        return func;
    }
}

function varargs(cons, nil) {
    return (...args) => {
        let result = nil;
        for (let index = args.length; index --> 0;) {
            result = cons(args[index], result);
        }
        return result;
    };
}

module.exports = {
    declareShape: declareShape,
    defineEffect: defineEffect,
    empty: () => {},
    handle: handle,
    partial: partial,
    varargs: varargs,
};
