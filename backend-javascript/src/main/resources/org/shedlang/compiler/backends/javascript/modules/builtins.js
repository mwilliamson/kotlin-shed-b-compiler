function declareShape(name, tagValue, fields) {
    function shape(fieldValues = {}) {
        return {...fieldValues, ...constantFieldValues, $tagValue: tagValue};
    }

    shape.fields = {};

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
                return handler(error.args);
            }
        }
        throw error;
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
    handle: handle,

    varargs: varargs,
};
